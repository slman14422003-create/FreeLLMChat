package com.freellm.chat.ui

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import com.freellm.chat.domain.VadEvent
import com.freellm.chat.domain.VoiceVad
import com.freellm.chat.domain.Wav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MicCapture(var silenceMs: Int) {
    val level = MutableStateFlow(0f)

    private fun write(out: ByteArrayOutputStream, pcm: ShortArray, n: Int) {
        val b = ByteArray(n * 2)
        for (i in 0 until n) {
            b[i * 2] = pcm[i].toInt().toByte()
            b[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte()
        }
        out.write(b, 0, b.size)
    }

    @SuppressLint("MissingPermission")
    suspend fun nextUtterance(idleMs: Long, bargeIn: Boolean = false, onStart: () -> Unit = {}): ByteArray? =
        withContext(Dispatchers.IO) {
            val frame = FRAME
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return@withContext null
            val source = if (bargeIn) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
            val rec = AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame * 8))
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return@withContext null
            }
            var aec: AcousticEchoCanceler? = null
            if (bargeIn && AcousticEchoCanceler.isAvailable()) {
                aec = runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true } }.getOrNull()
            }
            val vad = VoiceVad(silenceMs = silenceMs, minRms = if (bargeIn) 900.0 else 320.0)
            val pcm = ShortArray(frame)
            val roll = ArrayList<ShortArray>()
            val out = ByteArrayOutputStream()
            val began = System.currentTimeMillis()
            try {
                rec.startRecording()
                while (isActive) {
                    val n = rec.read(pcm, 0, frame)
                    if (n < 0) break
                    if (n == 0) {
                        delay(10)
                        continue
                    }
                    val rms = VoiceVad.rms(pcm, n)
                    level.value = (rms / 6000.0).coerceIn(0.0, 1.0).toFloat()
                    val was = vad.isSpeaking
                    val ev = vad.feed(rms, FRAME_MS)
                    if (ev == VadEvent.START) {
                        roll.forEach { write(out, it, it.size) }
                        roll.clear()
                        write(out, pcm, n)
                        onStart()
                    } else if (was) {
                        write(out, pcm, n)
                    } else {
                        roll.add(pcm.copyOf(n))
                        if (roll.size > ROLL_FRAMES) roll.removeAt(0)
                    }
                    if (ev == VadEvent.END) return@withContext Wav.encode(out.toByteArray(), RATE)
                    if (ev == VadEvent.TOO_SHORT) out.reset()
                    if (!vad.isSpeaking && System.currentTimeMillis() - began > idleMs) return@withContext null
                }
                null
            } finally {
                runCatching { rec.stop() }
                runCatching { aec?.release() }
                rec.release()
                level.value = 0f
            }
        }

    companion object {
        const val RATE = 16_000
        const val FRAME = 480
        const val FRAME_MS = 30
        const val ROLL_FRAMES = 10
    }
}

class PcmPlayer {
    @SuppressLint("NewApi")
    suspend fun play(wav: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val info = Wav.parse(wav) ?: return@withContext false
        val mask = if (info.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val min = AudioTrack.getMinBufferSize(info.sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return@withContext false
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(info.sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(mask)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            var off = info.dataOffset
            val end = info.dataOffset + info.dataLength
            while (off < end && isActive) {
                val n = track.write(wav, off, minOf(4096, end - off))
                if (n <= 0) break
                off += n
            }
            val frames = info.dataLength / (2 * info.channels)
            val limit = System.currentTimeMillis() + frames * 1000L / info.sampleRate + 1500
            while (isActive && track.playbackHeadPosition < frames && System.currentTimeMillis() < limit) delay(30)
            true
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            track.release()
        }
    }
}
