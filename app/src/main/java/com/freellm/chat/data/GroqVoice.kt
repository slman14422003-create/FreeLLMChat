package com.freellm.chat.data

import com.freellm.chat.FreeLlmApp
import com.freellm.chat.domain.SpeechLang
import com.freellm.chat.domain.VoiceTestAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class VoiceFail { NO_KEY, AUTH, RATE, TERMS, NETWORK, EMPTY, OTHER }

class VoiceException(
    val kind: VoiceFail,
    message: String = "",
    val model: String = "",
    val httpCode: Int = 0,
    val snippet: String = ""
) : Exception(message) {
    val transient: Boolean get() = kind == VoiceFail.NETWORK || kind == VoiceFail.OTHER
}

class Transcript(val text: String, val language: String?)

data class VoiceStep(val step: String, val ok: Boolean, val code: Int, val kind: VoiceFail?)

object VoiceErrors {
    private val json = Json { ignoreUnknownKeys = true }
    private val secret = Regex("gsk_[A-Za-z0-9_\\-]{6,}|Bearer\\s+\\S+")

    fun redact(s: String, limit: Int = 100): String = secret.replace(s.replace('\n', ' ').replace('\r', ' '), "***").take(limit)

    fun termsRequired(body: String): Boolean {
        val err = try {
            (json.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return false
        val code = (err["code"] as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
        val msg = (err["message"] as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
        if (code == "model_terms_required") return true
        return msg.contains("terms") && msg.contains("accept")
    }

    fun map(code: Int, body: String): VoiceException {
        val kind = when {
            termsRequired(body) -> VoiceFail.TERMS
            code == 401 -> VoiceFail.AUTH
            code == 429 -> VoiceFail.RATE
            else -> VoiceFail.OTHER
        }
        val snip = redact(body)
        return VoiceException(kind, "HTTP $code $snip", "", code, snip)
    }
}

object VoiceFallback {
    suspend fun <T> run(keys: List<String>, bases: List<String>, call: suspend (base: String, key: String) -> T): T {
        if (keys.isEmpty()) throw VoiceException(VoiceFail.NO_KEY)
        var last: VoiceException? = null
        for (k in keys) {
            for (b in bases) {
                try {
                    return call(b, k)
                } catch (e: VoiceException) {
                    last = e
                    if (e.kind == VoiceFail.TERMS || e.kind == VoiceFail.EMPTY) throw e
                    if (e.kind == VoiceFail.AUTH || e.kind == VoiceFail.RATE) break
                }
            }
        }
        throw last ?: VoiceException(VoiceFail.OTHER)
    }
}

class GroqVoice(private val app: FreeLlmApp) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient by lazy {
        app.http.newBuilder().readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    }

    fun hasKey(): Boolean = keys().isNotEmpty()

    private fun keys(): List<String> =
        app.secrets.apiKey("groq").orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private suspend fun bases(): List<String> {
        val custom = app.secrets.baseUrl("groq").orEmpty().trim().trimEnd('/')
        if (custom.isNotEmpty()) return listOf(custom)
        val px = app.settings.proxyUrl.first().trim().trimEnd('/')
        val direct = "https://api.groq.com/openai/v1"
        return if (px.isEmpty()) listOf(direct) else listOf("$px/groq/openai/v1", direct)
    }

    private suspend fun <T> withKeys(call: suspend (base: String, key: String) -> T): T =
        VoiceFallback.run(keys(), bases(), call)

    private suspend fun send(req: Request): ByteArray = try {
        client.newCall(req).await().use { r ->
            val bytes = withContext(Dispatchers.IO) { r.body?.bytes() ?: ByteArray(0) }
            if (!r.isSuccessful) throw VoiceErrors.map(r.code, String(bytes, Charsets.UTF_8))
            bytes
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: VoiceException) {
        throw e
    } catch (e: IOException) {
        throw VoiceException(VoiceFail.NETWORK, e.javaClass.simpleName + ": " + e.message.orEmpty().take(80))
    }

    suspend fun ping() {
        withKeys { base, key ->
            send(Request.Builder().url("$base/models").header("Authorization", "Bearer $key").get().build())
        }
    }

    suspend fun transcribe(wav: ByteArray, model: String, lang: String, prompt: String = ""): String =
        transcribeDetailed(wav, model, lang, prompt).text

    suspend fun transcribeDetailed(wav: ByteArray, model: String, lang: String, prompt: String = ""): Transcript = withKeys { base, key ->
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
        if (lang.isNotBlank() && lang != "auto") form.addFormDataPart("language", lang)
        if (prompt.isNotBlank()) form.addFormDataPart("prompt", prompt)
        val req = Request.Builder().url("$base/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(form.build())
            .build()
        val bytes = send(req)
        val obj = try {
            json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
        } catch (e: Exception) {
            null
        }
        val text = (obj?.get("text") as? JsonPrimitive)?.contentOrNull?.trim()
        if (text == null) throw VoiceException(VoiceFail.EMPTY)
        Transcript(text, SpeechLang.isoOf((obj?.get("language") as? JsonPrimitive)?.contentOrNull))
    }

    suspend fun speak(text: String, arabic: Boolean, voice: String): ByteArray = withKeys { base, key ->
        val payloadModel = if (arabic) AR_MODEL else EN_MODEL
        val payload = buildJsonObject {
            put("model", payloadModel)
            put("input", text)
            put("voice", voice)
            put("response_format", "wav")
        }.toString()
        val req = Request.Builder().url("$base/audio/speech")
            .header("Authorization", "Bearer $key")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            send(req)
        } catch (e: VoiceException) {
            throw VoiceException(e.kind, e.message.orEmpty(), payloadModel, e.httpCode, e.snippet)
        }
    }

    suspend fun checkTts(arabic: Boolean): VoiceFail? = try {
        speak(if (arabic) "\u0645\u0631\u062d\u0628\u0627" else "Hello", arabic, if (arabic) AR_VOICES.first() else EN_VOICES.first())
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: VoiceException) {
        e.kind
    }

    suspend fun selfTest(sttModel: String): List<VoiceStep> {
        val out = ArrayList<VoiceStep>()
        out.add(
            try {
                transcribe(VoiceTestAudio.wav(), sttModel, "en")
                VoiceStep("stt", true, 0, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VoiceException) {
                if (e.kind == VoiceFail.EMPTY) VoiceStep("stt", true, 0, null) else VoiceStep("stt", false, e.httpCode, e.kind)
            }
        )
        out.add(
            try {
                speak("Test", false, EN_VOICES.first())
                VoiceStep("tts", true, 0, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VoiceException) {
                VoiceStep("tts", false, e.httpCode, e.kind)
            }
        )
        return out
    }

    companion object {
        fun termsUrl(arabic: Boolean): String =
            "https://console.groq.com/playground?model=" + if (arabic) AR_MODEL else EN_MODEL

        const val EN_MODEL = "canopylabs/orpheus-v1-english"
        const val AR_MODEL = "canopylabs/orpheus-arabic-saudi"
        val EN_VOICES = listOf("autumn", "diana", "hannah", "austin", "daniel", "troy")
        val AR_VOICES = listOf("noura", "lulwa", "fahad", "sultan")
        val STT_MODELS = listOf("whisper-large-v3-turbo", "whisper-large-v3")
    }
}
