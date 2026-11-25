package com.freellm.chat.data.voice

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

class GeminiTts(
    private val client: OkHttpClient,
    private val key: () -> String,
    private val model: () -> String,
    private val voice: () -> String
) : CloudTts {
    override val id = "gemini"
    override val ext = "wav"
    override val maxChars = 280
    override val altExts = listOf("mp3")

    override fun voiceFor(text: String): String = voice()

    override suspend fun synth(text: String): ByteArray = synthExt(text).first

    override suspend fun synthExt(text: String): Pair<ByteArray, String> {
        val k = sanitizeKey(key())
        if (k.isEmpty()) throw TtsException(401, "401 missing key")
        pace()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/" + model().trim() + ":generateContent")
            .header("x-goog-api-key", k)
            .header("Content-Type", "application/json")
            .post(buildBody(text, voice()).toRequestBody("application/json".toMediaType()))
            .build()
        val bytes = HttpRetry.post(client, request)
        return parseAudio(String(bytes, Charsets.UTF_8))
    }

    private suspend fun pace() {
        gate.withLock {
            val wait = lastCallAt + MIN_GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastCallAt = System.currentTimeMillis()
        }
    }

    companion object {
        private const val MIN_GAP_MS = 3000L

        fun sanitizeKey(raw: String): String =
            raw.split(Regex("[\\n\\r,\\s]+")).firstOrNull { it.isNotBlank() }.orEmpty().trim()

        private val gate = Mutex()
        @Volatile private var lastCallAt = 0L

        internal fun buildBody(text: String, voice: String): String = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", text)
                            putJsonObject("speech_metadata") { put("style", "clear and natural") }
                        })
                    }
                })
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(JsonPrimitive("AUDIO")) }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") {
                        putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) }
                    }
                }
            }
        }.toString()

        internal fun parseAudio(json: String): Pair<ByteArray, String> {
            val root = try {
                Json.parseToJsonElement(json).jsonObject
            } catch (e: Exception) {
                throw TtsException(0, "bad response")
            }
            val part = ((root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("content").let { it as? JsonObject }
                ?.get("parts").let { it as? JsonArray }
                ?.firstOrNull().let { it as? JsonObject }
            val inline = (part?.get("inlineData") ?: part?.get("inline_data")) as? JsonObject
                ?: throw TtsException(0, "no audio in response")
            val mime = ((inline["mimeType"] ?: inline["mime_type"]) as? JsonPrimitive)?.contentOrNull.orEmpty()
            val data = (inline["data"] as? JsonPrimitive)?.contentOrNull
                ?: throw TtsException(0, "no audio data")
            val bytes = try {
                Base64.getMimeDecoder().decode(data)
            } catch (e: IllegalArgumentException) {
                throw TtsException(0, "bad audio data")
            }
            val m = mime.lowercase()
            return when {
                m.startsWith("audio/l16") || m.startsWith("audio/pcm") -> {
                    val rate = Regex("rate=(\\d+)").find(m)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                    WavWriter.pcm16Mono(bytes, rate) to "wav"
                }
                m == "audio/wav" || m == "audio/x-wav" -> bytes to "wav"
                m == "audio/mpeg" -> bytes to "mp3"
                else -> throw TtsException(0, "unsupported audio $mime")
            }
        }
    }
}
