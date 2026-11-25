package com.freellm.chat.ui.live

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.speech.SpeechRecognizer
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.data.UiLang
import com.freellm.chat.data.dictation.DictationEngine
import com.freellm.chat.data.dictation.DictationManager
import com.freellm.chat.data.dictation.DictationPolicy
import com.freellm.chat.data.dictation.SystemDictation
import com.freellm.chat.data.dictation.VoiceInputMode
import com.freellm.chat.data.dictation.WhisperDictation
import com.freellm.chat.domain.CodeFiles
import com.freellm.chat.domain.LiveSentences
import com.freellm.chat.domain.SilenceDetector
import com.freellm.chat.domain.TextChunker
import com.freellm.chat.domain.ThinkSplit
import com.freellm.chat.ui.Speaker
import com.freellm.chat.ui.StreamUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

enum class LiveState { Idle, Listening, Thinking, Speaking }

class LiveSession(
    private val app: FreeLlmApp,
    private val scope: CoroutineScope,
    private val speaker: Speaker,
    private val stream: StateFlow<StreamUi?>,
    private val send: (String) -> Boolean,
    private val stopGeneration: () -> Unit,
    private val dismissError: () -> Unit,
    private val onFailed: () -> Unit
) {
    private val stateFlow = MutableStateFlow(LiveState.Idle)
    private val levelFlow = MutableStateFlow(0f)
    private val captionFlow = MutableStateFlow("")
    private val recentFlow = MutableStateFlow<List<String>>(emptyList())
    private val mutedFlow = MutableStateFlow(false)

    val state: StateFlow<LiveState> = stateFlow.asStateFlow()
    val micLevel: StateFlow<Float> = levelFlow.asStateFlow()
    val caption: StateFlow<String> = captionFlow.asStateFlow()
    val recentLines: StateFlow<List<String>> = recentFlow.asStateFlow()
    val muted: StateFlow<Boolean> = mutedFlow.asStateFlow()

    private val system = SystemDictation(app)
    private var whisperName = "BALANCED_Q"
    private var inputMode = "system"
    private val offline = WhisperDictation { app.whisper.resolve(whisperName) }
    private var active: DictationEngine = system
    private var engineOn = false
    private val silence = SilenceDetector()
    private var bargeSince = -1L
    private var started = false
    private var failures = 0
    private var replyFinished = false
    private var replySaw = false
    private var replyText = ""
    private var spoken = 0
    private var replyJob: Job? = null
    private var restartJob: Job? = null
    private var watchdog: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch { app.settings.whisperModel.collect { whisperName = it } }
        scope.launch { app.settings.voiceInput.collect { inputMode = it } }
        for (e in listOf<DictationEngine>(system, offline)) {
            scope.launch { e.partial.collect { if (e === active) onPartial(it) } }
            scope.launch { e.final.collect { if (e === active) onFinal(it) } }
            scope.launch { e.error.collect { if (e === active) onError(it) } }
            scope.launch { e.level.collect { if (e === active) onLevel(it) } }
        }
        speaker.onQueuedStart = { s ->
            captionFlow.value = s
            recentFlow.value = (recentFlow.value + s).takeLast(3)
        }
        speaker.onQueueIdle = { onSpeechIdle() }
        beginListening()
    }

    fun toggleMute() {
        val m = !mutedFlow.value
        mutedFlow.value = m
        if (m) {
            stopEngine()
        } else if (stateFlow.value == LiveState.Listening || stateFlow.value == LiveState.Speaking) {
            startEngine()
        }
    }

    fun close() {
        started = false
        val generating = replyJob?.isActive == true
        replyJob?.cancel()
        restartJob?.cancel()
        watchdog?.cancel()
        stopEngine()
        speaker.onQueueIdle = null
        speaker.onQueuedStart = null
        speaker.stop()
        if (generating) stopGeneration()
        stateFlow.value = LiveState.Idle
        captionFlow.value = ""
    }

    private fun langTag(): String? {
        val stored = app.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("lang", null)
        val ui = UiLang.resolve(stored, UiLang.device())
        return DictationPolicy.languageTag(if (ui == "system") UiLang.device() else ui)
    }

    private fun pickEngine(): DictationEngine {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val manager = DictationManager({ system }, if (app.whisper.anyInstalled()) ({ offline }) else null)
        return manager.engine(
            VoiceInputMode.parse(inputMode),
            app.netState.network != null,
            cm?.isActiveNetworkMetered == true
        )
    }

    private fun startEngine() {
        if (mutedFlow.value || engineOn || !started) return
        active = pickEngine()
        engineOn = true
        silence.reset()
        active.start(langTag())
    }

    private fun stopEngine() {
        system.cancel()
        offline.cancel()
        engineOn = false
        levelFlow.value = 0f
    }

    private fun restartSoon() {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(250)
            val s = stateFlow.value
            if (started && (s == LiveState.Listening || s == LiveState.Speaking)) startEngine()
        }
    }

    private fun beginListening() {
        replyFinished = false
        bargeSince = -1L
        stateFlow.value = LiveState.Listening
        captionFlow.value = ""
        silence.reset()
        startEngine()
    }

    private fun onPartial(text: String) {
        failures = 0
        if (stateFlow.value == LiveState.Listening) captionFlow.value = text
    }

    private fun onFinal(text: String) {
        engineOn = false
        levelFlow.value = 0f
        when (stateFlow.value) {
            LiveState.Listening -> if (text.isBlank()) restartSoon() else think(text)
            LiveState.Speaking -> restartSoon()
            else -> Unit
        }
    }

    private fun onError(code: Int) {
        engineOn = false
        levelFlow.value = 0f
        val s = stateFlow.value
        if (s != LiveState.Listening && s != LiveState.Speaking) return
        if (code != SpeechRecognizer.ERROR_NO_MATCH && code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            failures++
            if (failures >= 5) {
                onFailed()
                return
            }
        }
        restartSoon()
    }

    private fun onLevel(level: Float) {
        val now = SystemClock.elapsedRealtime()
        levelFlow.value = level
        when (stateFlow.value) {
            LiveState.Listening -> if (silence.update(level, now)) active.stop()
            LiveState.Speaking -> checkBarge(level, now)
            else -> Unit
        }
    }

    private fun checkBarge(level: Float, now: Long) {
        if (level <= 0.35f) {
            bargeSince = -1L
            return
        }
        if (bargeSince < 0) {
            bargeSince = now
        } else if (now - bargeSince >= 300) {
            bargeIn()
        }
    }

    private fun bargeIn() {
        speaker.stop()
        replyJob?.cancel()
        stopGeneration()
        beginListening()
    }

    private fun think(text: String) {
        stopEngine()
        captionFlow.value = ""
        stateFlow.value = LiveState.Thinking
        dismissError()
        replySaw = false
        replyText = ""
        spoken = 0
        replyFinished = false
        if (!send(text)) {
            beginListening()
            return
        }
        replyJob?.cancel()
        replyJob = scope.launch { stream.takeWhile { step(it) }.collect() }
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(25_000)
            if (stateFlow.value == LiveState.Thinking && !replySaw) {
                replyJob?.cancel()
                stopGeneration()
                beginListening()
            }
        }
    }

    private fun clean(raw: String): String = CodeFiles.stripCommand(ThinkSplit.strip(raw))

    private fun step(s: StreamUi?): Boolean {
        if (s != null && s.active) replySaw = true
        val failed = s?.error != null
        if (!replySaw && !failed) return true
        if (s != null && s.text.isNotEmpty()) replyText = clean(s.text)
        val finished = failed || s == null || !s.active
        val r = LiveSentences.take(replyText, spoken, finished)
        spoken = r.next
        for (sentence in r.sentences) speakSentence(sentence)
        if (finished) {
            replyFinished = true
            if (!speaker.isSpeaking) afterSpeech()
            return false
        }
        return true
    }

    private fun speakSentence(sentence: String) {
        if (stateFlow.value == LiveState.Thinking) {
            stateFlow.value = LiveState.Speaking
            bargeSince = -1L
            startEngine()
        }
        for (c in TextChunker.chunks(sentence, 220)) speaker.speakQueued(c)
    }

    private fun onSpeechIdle() {
        if (replyFinished && stateFlow.value == LiveState.Speaking) afterSpeech()
    }

    private fun afterSpeech() {
        watchdog?.cancel()
        beginListening()
    }
}
