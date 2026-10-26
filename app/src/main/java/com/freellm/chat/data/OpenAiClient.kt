package com.freellm.chat.data

import com.freellm.chat.domain.ChatMsg
import com.freellm.chat.domain.ChatTransport
import com.freellm.chat.domain.ErrorKind
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

class OpenAiClient(private val http: OkHttpClient) : ChatTransport {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    private class Parsed(val content: String?, val finished: Boolean, val error: String?)

    private fun endpoint(base: String): String = base.trim().trimEnd('/') + "/chat/completions"

    private fun body(t: Target, msgs: List<ChatMsg>, stream: Boolean, maxTokens: Int?): String =
        buildJsonObject {
            put("model", t.model.id)
            put("stream", stream)
            if (maxTokens != null) put("max_tokens", maxTokens)
            putJsonArray("messages") {
                msgs.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            }
        }.toString()

    private fun request(t: Target, base: String, bodyJson: String): Request {
        val b = Request.Builder()
            .url(endpoint(base))
            .header("Accept", "application/json")
        b.header("Authorization", "Bearer ${t.apiKey}")
        if (t.providerId == "gemini") b.header("x-goog-api-key", t.apiKey)
        t.headers.forEach { (k, v) -> b.header(k, v) }
        return b.post(bodyJson.toRequestBody(mediaJson)).build()
    }

    private fun redact(s: String, key: String): String =
        if (key.isBlank()) s else s.replace(key, "***")

    private fun httpError(code: Int, retryAfter: String?, bodyText: String, key: String): LlmException {
        val snippet = redact(bodyText.replace("\n", " ").take(200), key)
        val lower = bodyText.lowercase()
        val modelMissing = lower.contains("model") && (
            lower.contains("not found") || lower.contains("does not exist") ||
                lower.contains("not_found") || lower.contains("no endpoints found") ||
                lower.contains("decommissioned")
            )
        val kind = when {
            code == 429 -> ErrorKind.RATE_LIMIT
            code == 402 -> ErrorKind.PAYMENT
            code == 401 || code == 403 -> ErrorKind.AUTH
            code == 404 || modelMissing -> ErrorKind.NOT_FOUND
            code == 408 || code >= 500 -> ErrorKind.SERVER
            else -> ErrorKind.BAD_REQUEST
        }
        return LlmException(kind, code, retryAfter?.trim()?.toLongOrNull(), snippet)
    }

    private fun hostOf(base: String): String = base.substringAfter("://").substringBefore("/")

    private fun mapIo(e: IOException, base: String = ""): LlmException {
        val where = if (base.isBlank()) "" else hostOf(base) + " "
        return if (e is SocketTimeoutException) {
            LlmException(ErrorKind.TIMEOUT, message = where + e.message.orEmpty().take(90))
        } else {
            LlmException(ErrorKind.NETWORK, message = where + e.javaClass.simpleName + ": " + e.message.orEmpty().take(80))
        }
    }

    private fun parseChunk(data: String): Parsed? = try {
        val obj = json.parseToJsonElement(data) as JsonObject
        val err = obj["error"]
        if (err != null && err !is JsonNull) {
            Parsed(null, false, err.toString())
        } else {
            val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            val delta = choice?.get("delta") as? JsonObject
            val prim = delta?.get("content") as? JsonPrimitive
            val content = if (prim == null || prim is JsonNull) null else prim.content
            val fin = choice?.get("finish_reason")?.let { it !is JsonNull } ?: false
            Parsed(content, fin, null)
        }
    } catch (e: Exception) {
        null
    }

    private fun bases(t: Target): List<String> =
        listOf(t.baseUrl) + listOfNotNull(t.altBaseUrl.takeIf { it.isNotBlank() && it != t.baseUrl })

    private fun retryable(e: LlmException): Boolean = when (e.kind) {
        ErrorKind.TIMEOUT, ErrorKind.NETWORK -> true
        ErrorKind.AUTH -> e.httpCode == 403
        ErrorKind.SERVER -> e.httpCode in listOf(502, 503, 504) || e.httpCode in 520..530
        else -> false
    }

    override fun stream(target: Target, messages: List<ChatMsg>, maxTokens: Int?): Flow<String> = flow {
        val all = bases(target)
        for ((i, base) in all.withIndex()) {
            var emitted = false
            try {
                streamOnce(target, base, messages, maxTokens).collect {
                    emitted = true
                    emit(it)
                }
                return@flow
            } catch (e: LlmException) {
                if (emitted || i == all.lastIndex || !retryable(e)) throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamOnce(target: Target, base: String, messages: List<ChatMsg>, maxTokens: Int?): Flow<String> = flow {
        val req = try {
            request(target, base, body(target, messages, true, maxTokens))
        } catch (e: IllegalArgumentException) {
            throw LlmException(ErrorKind.BAD_REQUEST, message = "invalid url or key format")
        }
        val call = http.newCall(req)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val resp = try {
                call.execute()
            } catch (e: IOException) {
                if (call.isCanceled()) throw CancellationException("cancelled")
                throw mapIo(e, base)
            }
            resp.use { r ->
                if (!r.isSuccessful) {
                    val txt = try {
                        r.body?.string().orEmpty()
                    } catch (e: IOException) {
                        ""
                    }
                    throw httpError(r.code, r.header("Retry-After"), txt, target.apiKey)
                }
                val source = r.body?.source() ?: throw LlmException(ErrorKind.EMPTY)
                var finished = false
                while (true) {
                    val line = (try {
                        source.readUtf8Line()
                    } catch (e: IOException) {
                        if (call.isCanceled()) throw CancellationException("cancelled")
                        throw LlmException(ErrorKind.INTERRUPTED, message = "stream cut")
                    }) ?: break
                    if (line.isEmpty() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        finished = true
                        break
                    }
                    val p = parseChunk(data) ?: continue
                    if (p.error != null) {
                        val msg = redact(p.error, target.apiKey).take(200)
                        val l = msg.lowercase()
                        val kind = if (l.contains("429") || l.contains("rate") || l.contains("quota"))
                            ErrorKind.RATE_LIMIT else ErrorKind.SERVER
                        throw LlmException(kind, 0, null, msg)
                    }
                    if (!p.content.isNullOrEmpty()) emit(p.content)
                    if (p.finished) finished = true
                }
                if (!finished) throw LlmException(ErrorKind.INTERRUPTED, message = "no terminator")
            }
        } finally {
            handle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun test(target: Target): LlmException? {
        val all = bases(target)
        var last: LlmException? = null
        val trail = ArrayList<String>()
        for ((i, base) in all.withIndex()) {
            val err = testOnce(target, base) ?: return null
            last = err
            val tag = if (err.httpCode > 0) err.httpCode.toString() else err.kind.name
            trail.add(hostOf(base) + " -> " + tag + ": " + err.message.orEmpty().take(120))
            if (i == all.lastIndex || !retryable(err)) break
        }
        val l = last ?: return null
        return LlmException(l.kind, l.httpCode, l.retryAfterSec, trail.joinToString(" | "))
    }


    private suspend fun testOnce(target: Target, base: String): LlmException? = withContext(Dispatchers.IO) {
        val req = try {
            request(
                target,
                base,
                body(target, listOf(ChatMsg("user", "hi")), false, 5)
            )
        } catch (e: IllegalArgumentException) {
            return@withContext LlmException(ErrorKind.BAD_REQUEST, message = "invalid url or key format")
        }
        try {
            http.newCall(req).execute().use { r ->
                if (r.isSuccessful) {
                    null
                } else {
                    httpError(r.code, r.header("Retry-After"), r.body?.string().orEmpty(), target.apiKey)
                }
            }
        } catch (e: IOException) {
            mapIo(e, base)
        }
    }
}
