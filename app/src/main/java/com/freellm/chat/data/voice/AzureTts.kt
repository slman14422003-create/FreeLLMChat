package com.freellm.chat.data.voice

import com.freellm.chat.domain.SpeechText
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AzureTts(
    private val client: OkHttpClient,
    private val key: () -> String,
    private val region: () -> String,
    private val voice: () -> String
) : CloudTts {
    override val id = "azure"
    override val ext = "mp3"
    override val maxChars = 420

    override fun voiceFor(text: String): String = if (SpeechText.isArabic(text)) voice() else "en-US-AriaNeural"

    override suspend fun synth(text: String): ByteArray {
        val k = key().trim()
        if (k.isEmpty()) throw TtsException(401, "401 missing key")
        val request = Request.Builder()
            .url("https://" + region().trim() + ".tts.speech.microsoft.com/cognitiveservices/v1")
            .header("Ocp-Apim-Subscription-Key", k)
            .header("Content-Type", "application/ssml+xml")
            .header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
            .header("User-Agent", "FreeLLMChat")
            .post(buildSsml(text, voiceFor(text)).toRequestBody("application/ssml+xml".toMediaType()))
            .build()
        return HttpRetry.post(client, request)
    }

    companion object {
        internal fun escape(s: String): String = buildString {
            for (c in s) when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }

        internal fun buildSsml(text: String, voice: String): String {
            val lang = voice.split("-").take(2).joinToString("-")
            return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" + lang + "'><voice name='" + voice + "'>" + escape(text) + "</voice></speak>"
        }
    }
}
