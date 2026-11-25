package com.freellm.chat.data.dictation

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class WhisperDictation(private val model: () -> File?) : DictationEngine {
    private val p = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val f = MutableSharedFlow<String>(extraBufferCapacity = 2)
    private val e = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    private val l = MutableSharedFlow<Float>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val partial: SharedFlow<String> = p
    override val final: SharedFlow<String> = f
    override val error: SharedFlow<Int> = e
    override val level: SharedFlow<Float> = l

    private var scope: CoroutineScope? = null
    private var recorder: Job? = null
    @Volatile private var stopRequested = false

    @SuppressLint("MissingPermission")
    override fun start(languageTag: String?) {
        cancel()
        val file = model()
        if (file == null || !file.isFile || !WhisperNative.available()) {
            e.tryEmit(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        stopRequested = false
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = sc
        val segments = Channel<FloatArray>(Channel.UNLIMITED)
        val lang = languageTag ?: "auto"

        val transcriber = sc.launch {
            val acc = StringBuilder()
            for (seg in segments) {
                val text = WhisperRuntime.transcribe(file, seg, lang)
                if (text == null) {
                    e.tryEmit(SpeechRecognizer.ERROR_CLIENT)
                    return@launch
                }
                if (text.isNotBlank()) {
                    if (acc.isNotEmpty()) acc.append(' ')
                    acc.append(text)
                    p.tryEmit(acc.toString())
                }
            }
            if (acc.isEmpty()) e.tryEmit(SpeechRecognizer.ERROR_NO_MATCH) else f.tryEmit(acc.toString())
        }

        recorder = sc.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val frame = RATE / 10
            val rec = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, frame * 4)
                )
            }.getOrNull()
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                rec?.release()
                e.tryEmit(SpeechRecognizer.ERROR_AUDIO)
                segments.close()
                transcriber.cancel()
                return@launch
            }
            val vad = Vad()
            val shorts = ShortArray(frame)
            var silentMs = 0
            var spokeMs = 0
            var heard = false
            var waitedMs = 0
            try {
                rec.startRecording()
                while (isActive && !stopRequested) {
                    val n = rec.read(shorts, 0, frame)
                    if (n <= 0) continue
                    val fl = FloatArray(n) { shorts[it] / 32768f }
                    val r = vad.rms(fl)
                    l.tryEmit((r * 12f).coerceIn(0f, 1f))
                    val ms = n * 1000 / RATE
                    if (r >= THRESHOLD) { silentMs = 0; spokeMs += ms; heard = true } else silentMs += ms
                    if (!heard) {
                        waitedMs += ms
                        if (waitedMs >= NO_SPEECH_MS) {
                            e.tryEmit(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                            segments.close()
                            transcriber.cancel()
                            return@launch
                        }
                    }
                    vad.push(fl)?.let { segments.trySend(it) }
                    if (heard && spokeMs >= MIN_SPEECH_MS && silentMs >= END_SILENCE_MS) break
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
            vad.flush()?.let { segments.trySend(it) }
            segments.close()
        }
    }

    override fun stop() {
        stopRequested = true
    }

    override fun cancel() {
        stopRequested = true
        scope?.cancel()
        scope = null
        recorder = null
    }

    private companion object {
        const val RATE = 16000
        const val THRESHOLD = 0.015f
        const val MIN_SPEECH_MS = 300
        const val END_SILENCE_MS = 2500
        const val NO_SPEECH_MS = 8000
    }
}
