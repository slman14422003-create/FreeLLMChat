package com.freellm.chat.data

import com.freellm.chat.domain.ChatMsg
import com.freellm.chat.domain.ChatTransport
import com.freellm.chat.domain.ErrorKind
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.ProviderErrors
import com.freellm.chat.domain.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class OpenAiClient(private val http: OkHttpClient) : ChatTransport {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    internal class Parsed(val content: String?, val finished: Boolean, val error: String?, val length: Boolean = false, val reasoning: String? = null)

    private fun endpoint(base: String): String = base.trim().trimEnd('/') + "/chat/completions"

    private fun body(t: Target, msgs: List<ChatMsg>, stream: Boolean, maxTokens: Int?, temperature: Double? = null): String =
        buildJsonObject {
            put("model", t.model.id)
            put("stream", stream)
            if (maxTokens != null) put("max_tokens", if (t.model.reasoning) maxOf(maxTokens, REASONING_MIN_TOKENS) else maxTokens)
            if (temperature != null) put("temperature", temperature)
            putJsonArray("messages") {
                msgs.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        if (m.images.isEmpty()) {
                            put("content", m.content)
                        } else {
                            putJsonArray("content") {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", m.content.ifBlank { " " })
                                })
                                m.images.forEach { u ->
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") { put("url", u) }
                                    })
                                }
                            }
                        }
                    })
                }
            }
        }.toString()

    private fun request(t: Target, base: String, bodyJson: String): Request {
        val b = Request.Builder()
            .url(endpoint(base))
            .header("Accept", "application/json")
        if (t.apiKey.isNotBlank()) b.header("Authorization", "Bearer ${t.apiKey}")
        if (t.providerId.substringBefore('#') == "gemini") b.header("x-goog-api-key", t.apiKey)
        t.headers.forEach { (k, v) -> b.header(k, v) }
        return b.post(bodyJson.toRequestBody(mediaJson)).build()
    }

    private fun redact(s: String, key: String): String =
        if (key.isBlank()) s else s.replace(key, "***")

    private fun httpError(code: Int, retryAfter: String?, bodyText: String, key: String): LlmException {
        val snippet = redact(bodyText.replace("\n", " ").take(200), key)
        val kind = ProviderErrors.kind(code, bodyText)
        val wait = ProviderErrors.retryAfter(code, bodyText, retryAfter?.trim()?.toLongOrNull(), System.currentTimeMillis())
        return LlmException(kind, code, wait, snippet)
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

    internal fun parseChunk(data: String): Parsed? = try {
        val obj = json.parseToJsonElement(data) as JsonObject
        val err = obj["error"]
        if (err != null && err !is JsonNull) {
            Parsed(null, false, err.toString())
        } else {
            val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            val delta = choice?.get("delta") as? JsonObject
            val prim = delta?.get("content") as? JsonPrimitive
            val content = if (prim == null || prim is JsonNull) null else prim.content
            val rp = (delta?.get("reasoning_content") ?: delta?.get("reasoning")) as? JsonPrimitive
            val thought = if (rp == null || rp is JsonNull) null else rp.content
            val reason = choice?.get("finish_reason") as? JsonPrimitive
            val fin = reason != null && reason !is JsonNull
            Parsed(content, fin, null, fin && reason?.content == "length", thought)
        }
    } catch (e: Exception) {
        null
    }

    private val badBase = ConcurrentHashMap<String, Long>()

    private fun bases(t: Target): List<String> =
        listOf(t.baseUrl) + listOfNotNull(t.altBaseUrl.takeIf { it.isNotBlank() && it != t.baseUrl })

    internal fun orderedBases(t: Target, now: Long = System.currentTimeMillis()): List<String> {
        val all = bases(t)
        if (all.size < 2) return all
        val first = now - (badBase[all[0]] ?: 0L) < BAD_BASE_MS
        val second = now - (badBase[all[1]] ?: 0L) < BAD_BASE_MS
        return if (first && !second) listOf(all[1], all[0]) + all.drop(2) else all
    }

    private fun retryable(e: LlmException): Boolean = when (e.kind) {
        ErrorKind.TIMEOUT, ErrorKind.NETWORK -> true
        ErrorKind.AUTH -> e.httpCode == 403
        ErrorKind.NOT_FOUND -> e.httpCode == 404 && e.message.orEmpty().trim().equals("Not found", ignoreCase = true)
        ErrorKind.SERVER -> e.httpCode in listOf(502, 503, 504) || e.httpCode in 520..530
        else -> false
    }

    override fun stream(target: Target, messages: List<ChatMsg>, maxTokens: Int?): Flow<String> =
        stream(target, messages, maxTokens, null)

    override fun stream(target: Target, messages: List<ChatMsg>, maxTokens: Int?, temperature: Double?): Flow<String> = flow {
        val all = orderedBases(target)
        for ((i, base) in all.withIndex()) {
            var emitted = false
            val ttfb = if (i < all.lastIndex) (if (target.model.reasoning) TTFB_REASONING_MS else TTFB_MS) else 0L
            try {
                val acc = StringBuilder()
                var msgs = messages
                var round = 0
                while (true) {
                    var cut = false
                    try {
                        streamOnce(target, base, msgs, maxTokens, temperature, ttfb) { cut = true }.collect {
                            if (!emitted) badBase.remove(base)
                            emitted = true
                            acc.append(it)
                            emit(it)
                        }
                    } catch (e: LlmException) {
                        if (round == 0) throw e
                        break
                    }
                    if (!cut || round >= MAX_AUTO_CONTINUE || acc.isEmpty()) break
                    round++
                    msgs = messages + ChatMsg("assistant", acc.toString()) + ChatMsg("user", AUTO_CONTINUE)
                }
                return@flow
            } catch (e: LlmException) {
                if (!emitted && (e.kind == ErrorKind.TIMEOUT || e.kind == ErrorKind.NETWORK)) badBase[base] = System.currentTimeMillis()
                if (emitted || i == all.lastIndex || !retryable(e)) throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamOnce(
        target: Target,
        base: String,
        messages: List<ChatMsg>,
        maxTokens: Int?,
        temperature: Double?,
        ttfbMs: Long = 0L,
        onLength: () -> Unit = {}
    ): Flow<String> = flow {
        val req = try {
            request(target, base, body(target, messages, true, maxTokens, temperature))
        } catch (e: IllegalArgumentException) {
            throw LlmException(ErrorKind.BAD_REQUEST, message = "invalid url or key format")
        }
        val call = http.newCall(req)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        val timedOut = AtomicBoolean(false)
        val gotHeaders = AtomicBoolean(false)
        val dog = if (ttfbMs > 0) CoroutineScope(Dispatchers.Default).launch {
            delay(ttfbMs)
            if (!gotHeaders.get()) {
                timedOut.set(true)
                call.cancel()
            }
        } else null
        try {
            val resp = try {
                call.execute()
            } catch (e: IOException) {
                if (timedOut.get()) throw LlmException(ErrorKind.TIMEOUT, message = hostOf(base) + " no response in " + ttfbMs / 1000 + "s")
                if (call.isCanceled()) throw CancellationException("cancelled")
                throw mapIo(e, base)
            }
            gotHeaders.set(true)
            dog?.cancel()
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
                var thinking = false
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
                        throw LlmException(
                            ProviderErrors.streamKind(msg), 0,
                            ProviderErrors.streamRetryAfter(msg, System.currentTimeMillis()), msg
                        )
                    }
                    if (!p.reasoning.isNullOrEmpty()) {
                        if (!thinking) {
                            thinking = true
                            emit("<think>")
                        }
                        emit(p.reasoning)
                    }
                    if (!p.content.isNullOrEmpty()) {
                        if (thinking) {
                            thinking = false
                            emit("</think>\n")
                        }
                        emit(p.content)
                    }
                    if (p.length) onLength()
                    if (p.finished) finished = true
                }
                if (thinking) emit("</think>\n")
                if (!finished) throw LlmException(ErrorKind.INTERRUPTED, message = "no terminator")
            }
        } finally {
            dog?.cancel()
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

    /** Lists model ids from an OpenAI-style /models endpoint, falling back to Ollama's /api/tags. */
    suspend fun listModels(base: String, key: String): Pair<List<String>, LlmException?> = withContext(Dispatchers.IO) {
        val root = base.trim().trimEnd('/')
        if (root.isEmpty()) return@withContext emptyList<String>() to LlmException(ErrorKind.BAD_REQUEST, message = "empty url")
        val ollamaRoot = root.removeSuffix("/v1")
        var last: LlmException? = null
        val urls = listOf(root + "/models" to "data", ollamaRoot + "/api/tags" to "models")
        for ((url, field) in urls) {
            try {
                val b = Request.Builder().url(url).header("Accept", "application/json")
                if (key.isNotBlank()) b.header("Authorization", "Bearer $key")
                http.newCall(b.get().build()).execute().use { r ->
                    val txt = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        last = httpError(r.code, null, txt, key)
                    } else {
                        val arr = (json.parseToJsonElement(txt) as? JsonObject)?.get(field) as? JsonArray
                        val ids = arr.orEmpty().mapNotNull { e ->
                            val o = e as? JsonObject ?: return@mapNotNull null
                            ((o["id"] ?: o["name"]) as? JsonPrimitive)?.content
                        }.distinct()
                        if (ids.isNotEmpty()) return@withContext ids to null
                        last = LlmException(ErrorKind.EMPTY, message = "no models")
                    }
                }
            } catch (e: IOException) {
                last = mapIo(e, url)
            } catch (e: IllegalArgumentException) {
                last = LlmException(ErrorKind.BAD_REQUEST, message = "invalid url")
            } catch (e: Exception) {
                last = LlmException(ErrorKind.SERVER, message = e.javaClass.simpleName)
            }
        }
        emptyList<String>() to last
    }
}

private const val MAX_AUTO_CONTINUE = 2
private const val AUTO_CONTINUE =
    "Continue exactly where your previous message stopped. Do not repeat anything already written and do not add any preface."

private const val REASONING_MIN_TOKENS = 3000
private const val TTFB_MS = 7_000L
private const val TTFB_REASONING_MS = 30_000L
private const val BAD_BASE_MS = 120_000L
