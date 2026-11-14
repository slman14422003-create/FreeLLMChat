package com.freellm.chat.data

import com.freellm.chat.FreeLlmApp
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

class VoiceException(val kind: VoiceFail, message: String = "", val model: String = "") : Exception(message)

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

    private fun mapHttp(code: Int, body: String): VoiceException {
        val low = body.lowercase()
        val kind = when {
            low.contains("terms") || low.contains("model_terms_required") -> VoiceFail.TERMS
            code == 401 || code == 403 -> VoiceFail.AUTH
            code == 429 -> VoiceFail.RATE
            else -> VoiceFail.OTHER
        }
        return VoiceException(kind, "HTTP $code " + body.replace('\n', ' ').take(140))
    }

    private suspend fun <T> withKeys(call: suspend (base: String, key: String) -> T): T {
        val ks = keys()
        if (ks.isEmpty()) throw VoiceException(VoiceFail.NO_KEY)
        val bs = bases()
        var last: VoiceException? = null
        for (k in ks) {
            for (b in bs) {
                try {
                    return call(b, k)
                } catch (e: VoiceException) {
                    last = e
                    if (e.kind != VoiceFail.NETWORK) break
                }
            }
            if (last?.kind == VoiceFail.TERMS) break
        }
        throw last ?: VoiceException(VoiceFail.OTHER)
    }

    private suspend fun send(req: Request): ByteArray = try {
        client.newCall(req).await().use { r ->
            val bytes = withContext(Dispatchers.IO) { r.body?.bytes() ?: ByteArray(0) }
            if (!r.isSuccessful) throw mapHttp(r.code, String(bytes, Charsets.UTF_8))
            bytes
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: VoiceException) {
        throw e
    } catch (e: IOException) {
        throw VoiceException(VoiceFail.NETWORK, e.javaClass.simpleName + ": " + e.message.orEmpty().take(80))
    }

    suspend fun transcribe(wav: ByteArray, model: String, lang: String): String = withKeys { base, key ->
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
        if (lang.isNotBlank() && lang != "auto") form.addFormDataPart("language", lang)
        val req = Request.Builder().url("$base/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(form.build())
            .build()
        val bytes = send(req)
        val text = try {
            ((json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull
        } catch (e: Exception) {
            null
        }
        text?.trim() ?: throw VoiceException(VoiceFail.EMPTY)
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
            throw VoiceException(e.kind, e.message.orEmpty(), payloadModel)
        }
    }

    suspend fun checkTts(arabic: Boolean): VoiceFail? = try {
        speak(if (arabic) "مرحبا" else "Hello", arabic, if (arabic) AR_VOICES.first() else EN_VOICES.first())
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: VoiceException) {
        e.kind
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
