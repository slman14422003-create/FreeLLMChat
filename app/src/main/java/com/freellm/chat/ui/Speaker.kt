package com.freellm.chat.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class Speaker(ctx: Context) {
    private var ready = false
    private var speaking = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun toggle(text: String): Boolean {
        if (speaking) {
            stop()
            return false
        }
        if (!ready) return false
        val plain = text.replace(Regex("[*#`_>|~]"), " ").replace(Regex("\\s+"), " ").trim().take(3900)
        if (plain.isEmpty()) return false
        val arabic = plain.any { it in '\u0600'..'\u06FF' }
        tts.language = if (arabic) Locale("ar") else Locale.getDefault()
        speaking = true
        tts.speak(plain, TextToSpeech.QUEUE_FLUSH, null, "msg")
        return true
    }

    fun stop() {
        speaking = false
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
