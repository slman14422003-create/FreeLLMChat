package com.freellm.chat.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.data.VoiceException
import com.freellm.chat.data.GroqVoice
import com.freellm.chat.data.VoiceFail
import com.freellm.chat.data.VoiceStep
import com.freellm.chat.domain.CloseReason
import com.freellm.chat.domain.GhostFilter
import com.freellm.chat.domain.SessionPolicy
import com.freellm.chat.domain.SpeechLang
import com.freellm.chat.domain.Utterance
import com.freellm.chat.domain.Wav
import com.freellm.chat.domain.SpeechSplitter
import com.freellm.chat.domain.SpeechText
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class VoicePhase { LISTENING, THINKING, SPEAKING }

private const val ERR_UNAVAILABLE = -1

private class Heard(val text: String?, val error: Int?)

private class HearResult(val text: String?, val failed: Boolean)

private fun quietError(error: Int?): Boolean =
    error == null || error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

private suspend fun listenDevice(
    ctx: Context,
    tag: String,
    offline: Boolean,
    onReady: () -> Unit = {},
    extras: (Intent) -> Unit = {}
): Heard =
    suspendCancellableCoroutine { c ->
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            c.resume(Heard(null, ERR_UNAVAILABLE))
            return@suspendCancellableCoroutine
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
        fun done(h: Heard) {
            if (c.isActive) c.resume(h)
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onReady()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                done(Heard(null, error))
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                done(Heard(t, null))
            }
        })
        c.invokeOnCancellation {
            runCatching { rec.cancel() }
            runCatching { rec.destroy() }
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        extras(i)
        rec.startListening(i)
    }

@android.annotation.SuppressLint("NewApi")
private suspend fun listenDeviceAudio(ctx: Context, wav: ByteArray, tag: String): Heard? {
    if (Build.VERSION.SDK_INT < 33) return null
    val info = Wav.parse(wav) ?: return null
    val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: return null
    val writer = Thread {
        runCatching {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(wav, info.dataOffset, info.dataLength) }
        }
    }
    writer.start()
    val h = withTimeoutOrNull(20_000) {
        listenDevice(ctx, tag, false, {}) { i ->
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, info.channels)
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, info.sampleRate)
        }
    }
    runCatching { pipe[0].close() }
    return h
}

fun voiceErrorText(ctx: Context, kind: VoiceFail, code: Int): String = when (kind) {
    VoiceFail.NO_KEY -> ctx.getString(R.string.voice_err_key)
    VoiceFail.AUTH -> ctx.getString(R.string.voice_err_auth)
    VoiceFail.RATE -> ctx.getString(R.string.voice_err_rate)
    VoiceFail.TERMS -> ctx.getString(R.string.voice_err_terms)
    VoiceFail.NETWORK -> ctx.getString(R.string.voice_err_net)
    VoiceFail.EMPTY -> ctx.getString(R.string.voice_err_empty)
    VoiceFail.OTHER -> if (code > 0) ctx.getString(R.string.voice_err_http, code) else ctx.getString(R.string.voice_err_other)
}

fun voiceErrorText(ctx: Context, e: VoiceException): String = voiceErrorText(ctx, e.kind, e.httpCode)

fun voiceStepText(ctx: Context, s: VoiceStep): String {
    val name = ctx.getString(if (s.step == "stt") R.string.voice_test_stt else R.string.voice_test_tts)
    val res = if (s.ok) ctx.getString(R.string.voice_test_ok) else voiceErrorText(ctx, s.kind ?: VoiceFail.OTHER, s.code)
    return "$name: $res"
}

class VoiceSession(
    private val ctx: Context,
    private val app: FreeLlmApp,
    private val vm: ChatViewModel,
    private val speaker: Speaker,
    private val scope: CoroutineScope
) {
    val phase = MutableStateFlow(VoicePhase.LISTENING)
    val heard = MutableStateFlow("")
    val reply = MutableStateFlow("")
    val status = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val termsHint = MutableStateFlow(false)
    val muted = MutableStateFlow(false)
    private val route = AudioRoute(ctx)
    private val capture = MicCapture(900, route)
    val micLevel = capture.level
    private val player = PcmPlayer()
    private var job: Job? = null
    private var statusJob: Job? = null
    @Volatile private var speakJob: Job? = null
    @Volatile private var groqTtsOk = true
    @Volatile private var bargedIn = false
    @Volatile private var langHint: String? = SpeechLang.ui(Locale.getDefault().language)
    @Volatile private var ttsFails = 0

    fun start(onEnd: () -> Unit) {
        job = scope.launch {
            try {
                loop()
            } finally {
                vm.voiceMode = false
                route.release()
            }
            onEnd()
        }
    }

    fun stop() {
        job?.cancel()
        speakJob?.cancel()
        speaker.stop()
        if (vm.generating) vm.stop()
        vm.voiceMode = false
        route.release()
    }

    fun skip() {
        if (phase.value == VoicePhase.SPEAKING) {
            speakJob?.cancel()
            if (vm.generating) vm.stop()
        }
    }

    private fun setStatus(text: String?) {
        statusJob?.cancel()
        status.value = text
        if (text != null) {
            statusJob = scope.launch {
                delay(STATUS_MS)
                status.value = null
            }
        }
    }

    private fun clearStatus() {
        statusJob?.cancel()
        status.value = null
    }

    private fun setNotice(text: String?, terms: Boolean = false) {
        notice.value = text
        termsHint.value = terms && text != null
    }

    private suspend fun healthy(): Boolean {
        for (i in 0..1) {
            try {
                app.groqVoice.ping()
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: VoiceException) {
                if (e.kind == VoiceFail.RATE) return true
                if (e.kind == VoiceFail.AUTH || e.kind == VoiceFail.NO_KEY) {
                    setStatus(voiceErrorText(ctx, e))
                    return false
                }
            }
            if (i == 0) delay(400)
        }
        return false
    }

    private fun deviceTag(setting: String): String =
        SpeechLang.deviceTag(setting, langHint, Locale.getDefault().toLanguageTag())

    private suspend fun hearDevice(): Heard {
        val setting = app.settings.voiceLang.first()
        var tag = deviceTag(setting)
        var offline = false
        var last = Heard(null, null)
        for (attempt in 0..2) {
            val h = listenDevice(ctx, tag, offline, { clearStatus() })
            last = h
            val e = h.error
            if (quietError(e) || e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || e == ERR_UNAVAILABLE) return h
            when (e) {
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    setStatus(ctx.getString(R.string.voice_dev_net))
                    offline = true
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    setStatus(ctx.getString(R.string.voice_dev_busy))
                    delay(700)
                }
                12, 13 -> {
                    setStatus(ctx.getString(R.string.voice_dev_lang))
                    tag = Locale.getDefault().toLanguageTag()
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    setStatus(ctx.getString(R.string.voice_dev_client))
                    delay(400)
                }
                else -> return h
            }
        }
        return last
    }

    private suspend fun deviceFallback(u: Utterance): HearResult {
        setNotice(ctx.getString(R.string.voice_dev_fallback))
        val setting = app.settings.voiceLang.first()
        val fromAudio = listenDeviceAudio(ctx, u.wav, deviceTag(setting))
        val t = fromAudio?.text?.trim()
        if (!t.isNullOrBlank()) return HearResult(t, false)
        val h = hearDevice()
        val live = h.text?.trim()
        return if (live.isNullOrBlank()) HearResult(null, !quietError(h.error)) else HearResult(live, false)
    }

    private suspend fun hearWhisper(u: Utterance): HearResult {
        phase.value = VoicePhase.THINKING
        val model = app.settings.voiceSttModel.first()
        val setting = app.settings.voiceLang.first()
        var attempt = 0
        while (true) {
            try {
                val lang = SpeechLang.whisper(setting, langHint)
                val t = app.groqVoice.transcribeDetailed(u.wav, model, lang, SpeechLang.WHISPER_PROMPT)
                if (lang == "auto" && t.language != null) langHint = t.language
                if (GhostFilter.isGhost(t.text, u.durationMs, u.speechRatio)) {
                    if (setting == "auto") langHint = null
                    return HearResult(null, false)
                }
                return HearResult(t.text, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VoiceException) {
                if (e.kind == VoiceFail.EMPTY) {
                    if (setting == "auto") langHint = null
                    return HearResult(null, false)
                }
                if (e.transient && attempt == 0) {
                    attempt++
                    delay(300)
                    continue
                }
                setStatus(voiceErrorText(ctx, e))
                if (e.kind == VoiceFail.TERMS) setNotice(ctx.getString(R.string.voice_err_terms), true)
                if (e.transient) return deviceFallback(u).let { if (it.text == null) HearResult(null, true) else it }
                return HearResult(null, true)
            }
        }
    }

    private suspend fun closeWith(reason: CloseReason) {
        setStatus(
            ctx.getString(
                if (reason == CloseReason.FAILURES) R.string.voice_closed_failures else R.string.voice_closed_silence
            )
        )
        delay(CLOSE_MS)
    }

    private suspend fun loop() {
        val s = app.settings
        var whisper = app.groqVoice.hasKey() && s.voiceStt.first() != "device"
        var groqTts = app.groqVoice.hasKey() && s.voiceTts.first() != "device"
        capture.silenceMs = s.voiceSilenceMs.first()
        val bargeSetting = s.voiceBargeIn.first()
        vm.voiceMode = true
        route.acquire()
        if (whisper || groqTts) {
            phase.value = VoicePhase.THINKING
            if (!healthy()) {
                whisper = false
                groqTts = false
                setNotice(ctx.getString(R.string.voice_health_fail))
            }
        }
        val barge = whisper && bargeSetting
        if (whisper) capture.calibrate()
        var failMisses = 0
        var idleMisses = 0
        var carried: Utterance? = null
        while (currentCoroutineContext().isActive) {
            phase.value = VoicePhase.LISTENING
            if (muted.value) {
                delay(250)
                continue
            }
            var said: String? = null
            var failed = false
            if (whisper) {
                val u = carried ?: capture.nextUtterance(IDLE_MS, false, { clearStatus() })
                carried = null
                if (u == null) {
                    idleMisses += SessionPolicy.IDLE_TIMEOUT_WEIGHT - 1
                } else {
                    val r = hearWhisper(u)
                    said = r.text
                    failed = r.failed
                }
            } else {
                val h = hearDevice()
                if (h.error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    setStatus(ctx.getString(R.string.voice_denied))
                    delay(CLOSE_MS)
                    return
                }
                if (h.error == ERR_UNAVAILABLE) {
                    setStatus(ctx.getString(R.string.voice_unavailable))
                    delay(CLOSE_MS)
                    return
                }
                said = h.text?.trim()
                failed = !quietError(h.error)
            }
            if (said.isNullOrBlank()) {
                if (failed) failMisses++ else idleMisses++
                val reason = SessionPolicy.closeReason(failMisses, idleMisses)
                if (reason != CloseReason.NONE) {
                    closeWith(reason)
                    return
                }
                delay(if (failed) 1200L else 250L)
                continue
            }
            failMisses = 0
            idleMisses = 0
            clearStatus()
            heard.value = said
            reply.value = ""
            phase.value = VoicePhase.THINKING
            val before = vm.messages.value.count { it.role == "assistant" }
            vm.send(said)
            var waited = 0
            while (!vm.generating && waited < 1500) {
                delay(50)
                waited += 50
            }
            if (!vm.generating && vm.messages.value.count { it.role == "assistant" } == before) continue
            bargedIn = false
            val sj = scope.launch { speakStream(groqTts, before) }
            speakJob = sj
            var watch: Deferred<Utterance?>? = null
            if (barge) {
                watch = scope.async {
                    capture.nextUtterance(WATCH_MS, true, {}) {
                        bargedIn = true
                        sj.cancel()
                        if (vm.generating) vm.stop()
                    }
                }
            }
            sj.join()
            if (watch != null) {
                if (bargedIn) carried = watch.await() else watch.cancel()
            }
            speakJob = null
        }
    }

    private fun groqCan(text: String): Boolean {
        if (SpeechText.isArabic(text)) return true
        val letters = text.count { it.isLetter() }
        return letters > 0 && text.count { it.isLetter() && it.code < 128 } >= letters * 0.9
    }

    private suspend fun synth(text: String): ByteArray? {
        val s = app.settings
        return try {
            val ar = SpeechText.isArabic(text)
            val out = app.groqVoice.speak(text, ar, if (ar) s.voiceNameAr.first() else s.voiceNameEn.first())
            ttsFails = 0
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoiceException) {
            ttsFails++
            if (e.kind != VoiceFail.NETWORK && e.kind != VoiceFail.OTHER || ttsFails >= 2) groqTtsOk = false
            setNotice(ctx.getString(R.string.voice_tts_fallback, voiceErrorText(ctx, e)), e.kind == VoiceFail.TERMS)
            null
        }
    }

    private suspend fun speakStream(useGroq: Boolean, assistantBefore: Int) = coroutineScope {
        groqTtsOk = useGroq
        ttsFails = 0
        val splitter = SpeechSplitter(if (useGroq) 190 else 600, FIRST_CHUNK_CHARS)
        val gate = Semaphore(TTS_PARALLEL)
        val queue = Channel<Pair<String, Deferred<ByteArray?>?>>(Channel.UNLIMITED)
        val consumer = launch {
            for ((text, audio) in queue) {
                var played = false
                val bytes = audio?.await()
                if (bytes != null) {
                    phase.value = VoicePhase.SPEAKING
                    played = player.play(bytes)
                }
                if (!played) {
                    phase.value = VoicePhase.SPEAKING
                    speaker.say(text)
                }
            }
        }
        fun enqueue(parts: List<String>) {
            for (t in parts) {
                val d = if (groqTtsOk && groqCan(t)) async(Dispatchers.IO) {
                    gate.withPermit { if (groqTtsOk) synth(t) else null }
                } else null
                queue.trySend(t to d)
            }
        }
        var consumed = ""
        while (true) {
            val cur = com.freellm.chat.domain.ThinkSplit.strip(vm.stream.value?.text.orEmpty())
            if (cur.length > consumed.length && cur.startsWith(consumed)) {
                enqueue(splitter.push(cur.substring(consumed.length)))
                consumed = cur
            }
            reply.value = cur.takeLast(240)
            if (!vm.generating) break
            delay(60)
        }
        var waited = 0
        var final = ""
        while (waited <= 900) {
            val list = vm.messages.value.filter { it.role == "assistant" }
            if (list.size > assistantBefore) {
                final = com.freellm.chat.domain.ThinkSplit.strip(list.last().content)
                break
            }
            delay(60)
            waited += 60
        }
        if (final.startsWith(consumed) && final.length > consumed.length) {
            enqueue(splitter.push(final.substring(consumed.length)))
        } else if (consumed.isEmpty() && final.isNotEmpty()) {
            enqueue(splitter.push(final))
        }
        enqueue(splitter.flush())
        queue.close()
        consumer.join()
    }

    companion object {
        const val IDLE_MS = 45_000L
        const val WATCH_MS = 600_000L
        const val STATUS_MS = 3_000L
        const val CLOSE_MS = 2_500L
        const val FIRST_CHUNK_CHARS = 40
        const val TTS_PARALLEL = 2
    }
}

@Composable
fun VoiceChat(vm: ChatViewModel, speaker: Speaker, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as FreeLlmApp
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val session = remember { VoiceSession(ctx, app, vm, speaker, scope) }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        session.start { onClose() }
        onDispose {
            view.keepScreenOn = false
            session.stop()
        }
    }
    val phase by session.phase.collectAsStateWithLifecycle()
    val heard by session.heard.collectAsStateWithLifecycle()
    val reply by session.reply.collectAsStateWithLifecycle()
    val status by session.status.collectAsStateWithLifecycle()
    val notice by session.notice.collectAsStateWithLifecycle()
    val termsHint by session.termsHint.collectAsStateWithLifecycle()
    val muted by session.muted.collectAsStateWithLifecycle()
    val level by session.micLevel.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(if (phase == VoicePhase.SPEAKING) 520 else 900), RepeatMode.Reverse),
        label = "scale"
    )
    val lv = animateFloatAsState(level, tween(80), label = "level")
    val orb = when (phase) {
        VoicePhase.LISTENING -> 1f + lv.value * 0.45f
        else -> pulse.value
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(cs.background)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.voice_end))
            }
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    Modifier
                        .size(168.dp)
                        .scale(orb)
                        .clip(CircleShape)
                        .background(if (phase == VoicePhase.SPEAKING) cs.tertiary else cs.primary)
                        .clickable { session.skip() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Mic, null, Modifier.size(56.dp), tint = cs.onPrimary)
                }
                Text(
                    stringResource(
                        when (phase) {
                            VoicePhase.LISTENING -> if (muted) R.string.voice_muted else R.string.voice_listening
                            VoicePhase.THINKING -> R.string.voice_thinking
                            VoicePhase.SPEAKING -> R.string.voice_speaking
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                if (heard.isNotEmpty()) {
                    Text(heard, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 3)
                }
                if (reply.isNotEmpty()) {
                    Text(reply, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, maxLines = 5)
                }
                status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = cs.error, textAlign = TextAlign.Center) }
                notice?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center) }
                if (termsHint) {
                    val uri = androidx.compose.ui.platform.LocalUriHandler.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.TextButton(onClick = { uri.openUri(GroqVoice.termsUrl(false)) }) {
                            Text(stringResource(R.string.voice_terms_en))
                        }
                        androidx.compose.material3.TextButton(onClick = { uri.openUri(GroqVoice.termsUrl(true)) }) {
                            Text(stringResource(R.string.voice_terms_ar))
                        }
                    }
                }
                if (phase == VoicePhase.SPEAKING) {
                    Text(stringResource(R.string.voice_tap_skip), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { session.muted.value = !muted }) {
                        Icon(if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, stringResource(R.string.voice_mute))
                    }
                }
            }
        }
    }
}

@Composable
fun WhisperDictation(onText: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as FreeLlmApp
    val capture = remember { MicCapture(1100) }
    val level by capture.level.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val u = capture.nextUtterance(12_000)
        if (u == null) {
            onClose()
            return@LaunchedEffect
        }
        try {
            val t = app.groqVoice.transcribe(
                u.wav, app.settings.voiceSttModel.first(), app.settings.voiceLang.first(), SpeechLang.WHISPER_PROMPT
            )
            if (!GhostFilter.isGhost(t, u.durationMs, u.speechRatio)) onText(t)
            onClose()
        } catch (e: VoiceException) {
            error = voiceErrorText(ctx, e)
            delay(2200)
            onClose()
        }
    }
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.size(96.dp).scale(1f + level * 0.5f).clip(CircleShape).background(cs.primary).clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Mic, null, Modifier.size(36.dp), tint = cs.onPrimary)
            }
            Text(error ?: stringResource(R.string.voice_listening), style = MaterialTheme.typography.titleMedium)
        }
    }
}
