package com.freellm.chat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

enum class VoicePhase { LISTENING, THINKING, SPEAKING }

private class Heard(val text: String?, val fatal: Boolean)

private suspend fun listenDevice(ctx: Context): Heard =
    suspendCancellableCoroutine { c ->
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            c.resume(Heard(null, true))
            return@suspendCancellableCoroutine
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
        fun done(h: Heard) {
            if (c.isActive) c.resume(h)
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                val fatal = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                done(Heard(null, fatal && !quiet))
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                done(Heard(t, false))
            }
        })
        c.invokeOnCancellation {
            runCatching { rec.cancel() }
            runCatching { rec.destroy() }
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        rec.startListening(i)
    }

fun voiceErrorRes(e: VoiceException): Int = when (e.kind) {
    VoiceFail.NO_KEY -> R.string.voice_err_key
    VoiceFail.AUTH -> R.string.voice_err_auth
    VoiceFail.RATE -> R.string.voice_err_rate
    VoiceFail.TERMS -> R.string.voice_err_terms
    VoiceFail.NETWORK -> R.string.voice_err_net
    VoiceFail.EMPTY -> R.string.voice_err_empty
    VoiceFail.OTHER -> R.string.voice_err_other
}

private val ghosts = setOf(
    "thank you", "thank you.", "thanks for watching", "thanks for watching!", "you", "bye", "bye.",
    "شكرا", "شكرًا", "شكرا لكم", "شكراً للمشاهدة", "شكرا للمشاهدة", "اشتركوا في القناة", "ترجمة نانسي قنقر"
)

fun isGhostText(t: String): Boolean {
    val s = t.trim().trim('.', '!', '؟', '?', ' ').lowercase()
    return s.length < 2 || t.trim().lowercase() in ghosts || s in ghosts
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
    val status = MutableStateFlow<Int?>(null)
    val muted = MutableStateFlow(false)
    private val capture = MicCapture(900)
    val micLevel = capture.level
    private val player = PcmPlayer()
    private var job: Job? = null
    @Volatile private var speakJob: Job? = null
    @Volatile private var groqTtsOk = true
    @Volatile private var bargedIn = false

    fun start(onEnd: () -> Unit) {
        job = scope.launch {
            try {
                loop()
            } finally {
                vm.voiceMode = false
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
    }

    fun skip() {
        if (phase.value == VoicePhase.SPEAKING) {
            speakJob?.cancel()
            if (vm.generating) vm.stop()
        }
    }

    private suspend fun hearWhisper(wav: ByteArray): String? {
        phase.value = VoicePhase.THINKING
        return try {
            val model = app.settings.voiceSttModel.first()
            val lang = app.settings.voiceLang.first()
            val t = app.groqVoice.transcribe(wav, model, lang)
            if (isGhostText(t)) null else t
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoiceException) {
            status.value = voiceErrorRes(e)
            null
        }
    }

    private suspend fun loop() {
        val s = app.settings
        val whisper = app.groqVoice.hasKey() && s.voiceStt.first() != "device"
        val groqTts = app.groqVoice.hasKey() && s.voiceTts.first() != "device"
        val silence = s.voiceSilenceMs.first()
        val barge = whisper && s.voiceBargeIn.first()
        capture.silenceMs = silence
        vm.voiceMode = true
        var misses = 0
        var carried: ByteArray? = null
        while (currentCoroutineContext().isActive) {
            phase.value = VoicePhase.LISTENING
            val said: String?
            if (whisper) {
                val wav = carried ?: capture.nextUtterance(IDLE_MS)
                carried = null
                said = when {
                    wav == null -> null
                    muted.value -> null
                    else -> hearWhisper(wav)
                }
                if (wav == null && misses >= 1) return
            } else {
                val h = listenDevice(ctx)
                if (h.fatal) return
                said = h.text?.trim()
            }
            if (said.isNullOrBlank()) {
                misses++
                if (misses >= MAX_MISSES) return
                delay(250)
                continue
            }
            misses = 0
            status.value = null
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
            var watch: Deferred<ByteArray?>? = null
            if (barge) {
                watch = scope.async {
                    capture.nextUtterance(WATCH_MS, true) {
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
            app.groqVoice.speak(text, ar, if (ar) s.voiceNameAr.first() else s.voiceNameEn.first())
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoiceException) {
            groqTtsOk = false
            status.value = voiceErrorRes(e)
            null
        }
    }

    private suspend fun speakStream(useGroq: Boolean, assistantBefore: Int) = coroutineScope {
        groqTtsOk = useGroq
        val splitter = SpeechSplitter(if (useGroq) 190 else 600)
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
                val d = if (groqTtsOk && groqCan(t)) async(Dispatchers.IO) { synth(t) } else null
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
        const val MAX_MISSES = 3
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
                status?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = cs.error, textAlign = TextAlign.Center) }
                if (status == R.string.voice_err_terms) {
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
    var error by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val wav = capture.nextUtterance(12_000)
        if (wav == null) {
            onClose()
            return@LaunchedEffect
        }
        try {
            val t = app.groqVoice.transcribe(wav, app.settings.voiceSttModel.first(), app.settings.voiceLang.first())
            if (!isGhostText(t)) onText(t)
            onClose()
        } catch (e: VoiceException) {
            error = voiceErrorRes(e)
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
            Text(stringResource(error ?: R.string.voice_listening), style = MaterialTheme.typography.titleMedium)
        }
    }
}
