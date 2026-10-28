package com.freellm.chat.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.freellm.chat.domain.SpeechText
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class Speaker(ctx: Context) {
    @Volatile private var ready = false
    @Volatile private var speaking = false
    @Volatile private var finish: (() -> Unit)? = null
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = ended()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = ended()
                override fun onStop(utteranceId: String?, interrupted: Boolean) = ended()
            })
        }
    }

    private fun ended() {
        speaking = false
        val f = finish
        finish = null
        f?.invoke()
    }

    private fun begin(plain: String, id: String) {
        tts.language = if (SpeechText.isArabic(plain)) Locale("ar") else Locale.getDefault()
        speaking = true
        tts.speak(plain, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun toggle(text: String): Boolean {
        if (speaking) {
            stop()
            return false
        }
        if (!ready) return false
        val plain = SpeechText.plain(text)
        if (plain.isEmpty()) return false
        begin(plain, "msg")
        return true
    }

    suspend fun say(text: String) {
        val plain = SpeechText.plain(text)
        if (!ready || plain.isEmpty()) return
        suspendCancellableCoroutine { c ->
            finish = { if (c.isActive) c.resume(Unit) }
            c.invokeOnCancellation {
                finish = null
                tts.stop()
                speaking = false
            }
            begin(plain, "voice")
        }
    }

    fun stop() {
        speaking = false
        tts.stop()
        ended()
    }

    fun shutdown() {
        finish = null
        tts.stop()
        tts.shutdown()
    }
}
