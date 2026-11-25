package com.freellm.chat.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.data.voice.CloudSpeaker
import com.freellm.chat.data.voice.AzureTts
import com.freellm.chat.data.voice.CloudTts
import com.freellm.chat.data.voice.GeminiTts
import com.freellm.chat.data.voice.TtsException
import com.freellm.chat.domain.SpeechText
import com.freellm.chat.domain.TextChunker
import kotlinx.coroutines.launch
import java.util.Locale

class Speaker(ctx: Context) {
    private val app = ctx.applicationContext as FreeLlmApp
    private val main = Handler(Looper.getMainLooper())
    private val cloud = CloudSpeaker(app)

    @Volatile private var ready = false
    @Volatile private var speaking = false
    @Volatile private var engineName = "system"
    @Volatile private var fallback = true
    @Volatile private var preload = "auto"
    @Volatile private var geminiVoice = "Kore"
    @Volatile private var geminiModel = "gemini-3.8-flash-lite-tts"
    @Volatile private var azureRegion = "westeurope"
    @Volatile private var azureVoice = "ar-SY-AmanyNeural"

    private val queue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val queuedPending = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var queuedBusy = false
    @Volatile var onQueueIdle: (() -> Unit)? = null
    @Volatile var onQueuedStart: ((String) -> Unit)? = null

    val isSpeaking: Boolean get() = speaking || queuedPending.get() > 0

    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = utteranceEnded(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = utteranceEnded(utteranceId)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = utteranceEnded(utteranceId)
            })
        }
    }

    init {
        app.appScope.launch { app.settings.voiceEngine.collect { engineName = it } }
        app.appScope.launch { app.settings.voiceFallback.collect { fallback = it } }
        app.appScope.launch { app.settings.voicePreload.collect { preload = it } }
        app.appScope.launch { app.settings.voiceGeminiVoice.collect { geminiVoice = it } }
        app.appScope.launch { app.settings.voiceGeminiModel.collect { geminiModel = it } }
        app.appScope.launch { app.settings.voiceAzureRegion.collect { azureRegion = it } }
        app.appScope.launch { app.settings.voiceAzureVoice.collect { azureVoice = it } }
    }

    private fun ended() {
        speaking = false
    }

    private fun utteranceEnded(id: String?) {
        if (id == QUEUED_ID) finishQueued() else ended()
    }

    fun speakQueued(text: String) {
        val plain = SpeechText.plain(text)
        if (plain.isEmpty()) return
        queuedPending.incrementAndGet()
        queue.add(plain)
        main.post { pump() }
    }

    private fun pump() {
        if (queuedBusy) return
        val next = queue.poll() ?: return
        queuedBusy = true
        onQueuedStart?.invoke(next)
        val name = engineName
        val engine = if (keyMissing(name)) null else engineFor(name)
        if (engine == null) {
            speakSystemQueued(next)
            return
        }
        cloud.speak(
            next,
            engine,
            window(engine),
            onFirstChunk = {},
            onDone = { finishQueued() },
            onError = { e, _ ->
                main.post { Toast.makeText(app, errorText(engine, e), Toast.LENGTH_LONG).show() }
                if (fallback && ready) main.post { speakSystemQueued(next) } else finishQueued()
            }
        )
    }

    private fun speakSystemQueued(plain: String) {
        if (!ready) {
            finishQueued()
            return
        }
        tts.language = if (SpeechText.isArabic(plain)) Locale("ar") else Locale.getDefault()
        tts.speak(plain, TextToSpeech.QUEUE_FLUSH, null, QUEUED_ID)
    }

    private fun finishQueued() {
        if (!queuedBusy) return
        queuedBusy = false
        val left = queuedPending.decrementAndGet()
        if (left <= 0) {
            queuedPending.set(0)
            main.post { onQueueIdle?.invoke() }
        } else {
            main.post { pump() }
        }
    }

    private fun clearQueued() {
        queuedBusy = false
        queue.clear()
        queuedPending.set(0)
    }

    private fun begin(plain: String, id: String) {
        tts.language = if (SpeechText.isArabic(plain)) Locale("ar") else Locale.getDefault()
        speaking = true
        tts.speak(plain, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun geminiKey(): String =
        GeminiTts.sanitizeKey(app.secrets.extra("voice_gemini_key").orEmpty())
            .ifBlank { GeminiTts.sanitizeKey(app.secrets.apiKey("gemini").orEmpty()) }

    private fun azureKey(): String = app.secrets.extra("voice_azure_key").orEmpty()

    private fun engineFor(name: String): CloudTts? = when (name) {
        "gemini" -> GeminiTts(app.http, { geminiKey() }, { geminiModel }, { geminiVoice })
        "azure" -> AzureTts(app.http, { azureKey() }, { azureRegion }, { azureVoice })
        else -> null
    }

    private fun keyMissing(name: String): Boolean = when (name) {
        "gemini" -> geminiKey().isBlank()
        "azure" -> azureKey().isBlank()
        else -> false
    }

    private fun window(engine: CloudTts): Int = when (preload) {
        "3", "6", "10" -> preload.toInt()
        else -> if (engine.id == "gemini") 3 else 6
    }

    private fun engineLabel(id: String): String = app.getString(
        when (id) {
            "edge" -> R.string.voice_engine_edge
            "gemini" -> R.string.voice_engine_gemini
            "azure" -> R.string.voice_engine_azure
            else -> R.string.voice_engine_system
        }
    )

    private fun errorText(engine: CloudTts, e: TtsException): String {
        val name = engineLabel(engine.id)
        return when {
            e.code <= 0 -> app.getString(R.string.voice_err_network, name)
            e.code == 429 -> app.getString(R.string.voice_err_quota, name)
            e.code == 401 || e.code == 403 -> app.getString(R.string.voice_err_key, name)
            else -> app.getString(R.string.voice_err_unknown, name)
        }
    }

    private fun cloudFailed(plain: String, engine: CloudTts, e: TtsException, index: Int) {
        main.post { Toast.makeText(app, errorText(engine, e), Toast.LENGTH_LONG).show() }
        if (fallback && ready) {
            val rest = TextChunker.chunks(plain, engine.maxChars).drop(index).joinToString(" ")
            if (rest.isNotEmpty()) {
                main.post { begin(rest, "msg") }
                return
            }
        }
        speaking = false
    }

    fun toggle(text: String): Boolean {
        if (speaking) {
            stop()
            return false
        }
        val name = engineName
        val engine = if (keyMissing(name)) null else engineFor(name)
        if (engine == null) {
            if (keyMissing(name)) {
                val label = engineLabel(name)
                main.post { Toast.makeText(app, app.getString(R.string.voice_err_key, label), Toast.LENGTH_LONG).show() }
            }
            if (!ready) return false
            val plain = SpeechText.plain(text)
            if (plain.isEmpty()) return false
            begin(plain, "msg")
            return true
        }
        val plain = SpeechText.plain(text)
        if (plain.isEmpty()) return false
        speaking = true
        cloud.speak(
            plain,
            engine,
            window(engine),
            onFirstChunk = {},
            onDone = { speaking = false },
            onError = { e, i -> cloudFailed(plain, engine, e, i) }
        )
        return true
    }

    fun stop() {
        speaking = false
        clearQueued()
        cloud.stop()
        tts.stop()
        ended()
    }

    fun shutdown() {
        cloud.stop()
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val QUEUED_ID = "q"
    }
}
