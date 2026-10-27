package com.freellm.chat.data

import com.freellm.chat.domain.ErrorKind
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.SearchKind
import com.freellm.chat.domain.SearchParsers
import com.freellm.chat.domain.SearchProvider
import com.freellm.chat.domain.SearchResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

abstract class HttpSearch(private val http: OkHttpClient, private val proxy: String = "") : SearchProvider {
    internal fun endpoint(id: String, host: String, path: String): String {
        val px = proxy.trim().trimEnd('/')
        return if (px.isEmpty()) host + path else px + "/" + id + path
    }

    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    protected fun jsonBody(query: String, count: Int, queryKey: String, countKey: String, extra: Map<String, String> = emptyMap()) =
        buildJsonObject {
            put(queryKey, query)
            put(countKey, count)
            extra.forEach { (k, v) -> put(k, v) }
        }.toString().toRequestBody(mediaJson)

    protected suspend fun run(req: Request): String {
        val resp = try {
            http.newCall(req).await()
        } catch (e: SocketTimeoutException) {
            throw LlmException(ErrorKind.TIMEOUT, message = "search timeout")
        } catch (e: IOException) {
            throw LlmException(ErrorKind.NETWORK, message = e.javaClass.simpleName)
        }
        return resp.use { r ->
            val text = try {
                r.peekBody(MAX_BODY).string()
            } catch (e: IOException) {
                throw LlmException(ErrorKind.NETWORK, message = e.javaClass.simpleName)
            }
            if (!r.isSuccessful) throw httpError(r.code, r.header("Retry-After"), text)
            text
        }
    }

    internal fun httpError(code: Int, retryAfter: String?, body: String): LlmException {
        val lower = body.lowercase()
        val kind = when {
            code == 429 -> ErrorKind.RATE_LIMIT
            code == 402 || code == 432 || code == 433 -> ErrorKind.PAYMENT
            code == 400 && (lower.contains("credit") || lower.contains("quota")) -> ErrorKind.PAYMENT
            code == 403 && (lower.contains("ratelimit") || lower.contains("dailylimit") || lower.contains("quota")) -> ErrorKind.RATE_LIMIT
            code == 401 || code == 403 -> ErrorKind.AUTH
            code == 404 -> ErrorKind.NOT_FOUND
            code == 408 || code >= 500 -> ErrorKind.SERVER
            else -> ErrorKind.BAD_REQUEST
        }
        return LlmException(kind, code, retryAfter?.trim()?.toLongOrNull(), body.replace("\n", " ").take(160))
    }

    private companion object {
        const val MAX_BODY = 2_000_000L
    }
}

class GoogleSearch(http: OkHttpClient, private val key: String, private val cx: String, proxy: String = "") : HttpSearch(http, proxy) {
    override val kind = SearchKind.GOOGLE

    override suspend fun search(query: String, count: Int): List<SearchResult> {
        val url = endpoint("google", "https://www.googleapis.com", "/customsearch/v1").toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("cx", cx)
            .addQueryParameter("q", query)
            .addQueryParameter("num", count.coerceIn(1, 10).toString())
            .build()
        return SearchParsers.google(run(Request.Builder().url(url).header("Accept", "application/json").build()))
    }
}

class BraveSearch(http: OkHttpClient, private val key: String, proxy: String = "") : HttpSearch(http, proxy) {
    override val kind = SearchKind.BRAVE

    override suspend fun search(query: String, count: Int): List<SearchResult> {
        val url = endpoint("brave", "https://api.search.brave.com", "/res/v1/web/search").toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", count.coerceIn(1, 20).toString())
            .build()
        val req = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", key)
            .build()
        return SearchParsers.brave(run(req))
    }
}

class TavilySearch(http: OkHttpClient, private val key: String, proxy: String = "") : HttpSearch(http, proxy) {
    override val kind = SearchKind.TAVILY

    override suspend fun search(query: String, count: Int): List<SearchResult> {
        val req = Request.Builder().url(endpoint("tavily", "https://api.tavily.com", "/search"))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $key")
            .post(jsonBody(query, count.coerceIn(1, 10), "query", "max_results", mapOf("search_depth" to "basic")))
            .build()
        return SearchParsers.tavily(run(req))
    }
}

class SerperSearch(http: OkHttpClient, private val key: String, proxy: String = "") : HttpSearch(http, proxy) {
    override val kind = SearchKind.SERPER

    override suspend fun search(query: String, count: Int): List<SearchResult> {
        val req = Request.Builder().url(endpoint("serper", "https://google.serper.dev", "/search"))
            .header("Accept", "application/json")
            .header("X-API-KEY", key)
            .post(jsonBody(query, count.coerceIn(1, 10), "q", "num"))
            .build()
        return SearchParsers.serper(run(req))
    }
}

object SearchFactory {
    fun keyId(kind: SearchKind): String = "search_" + kind.id

    fun configured(kind: SearchKind, secrets: SecretStore): Boolean {
        val key = secrets.apiKey(keyId(kind))?.trim().orEmpty()
        if (key.isEmpty()) return false
        return kind != SearchKind.GOOGLE || !secrets.accountId(keyId(kind))?.trim().isNullOrEmpty()
    }

    fun build(kind: SearchKind, http: OkHttpClient, key: String, cx: String, proxy: String = ""): SearchProvider? {
        val k = key.trim()
        if (k.isEmpty()) return null
        return when (kind) {
            SearchKind.GOOGLE -> if (cx.trim().isEmpty()) null else GoogleSearch(http, k, cx.trim(), proxy)
            SearchKind.BRAVE -> BraveSearch(http, k, proxy)
            SearchKind.TAVILY -> TavilySearch(http, k, proxy)
            SearchKind.SERPER -> SerperSearch(http, k, proxy)
        }
    }

    fun create(kind: SearchKind, http: OkHttpClient, secrets: SecretStore, proxy: String = ""): SearchProvider? {
        if (!configured(kind, secrets)) return null
        val key = secrets.apiKey(keyId(kind))!!.trim()
        return when (kind) {
            SearchKind.GOOGLE -> GoogleSearch(http, key, secrets.accountId(keyId(kind))!!.trim(), proxy)
            SearchKind.BRAVE -> BraveSearch(http, key, proxy)
            SearchKind.TAVILY -> TavilySearch(http, key, proxy)
            SearchKind.SERPER -> SerperSearch(http, key, proxy)
        }
    }

    fun resolve(selected: SearchKind?, http: OkHttpClient, secrets: SecretStore, proxy: String = ""): SearchProvider? {
        val kind = selected?.takeIf { configured(it, secrets) } ?: SearchKind.entries.firstOrNull { configured(it, secrets) } ?: return null
        return create(kind, http, secrets, proxy)
    }

    fun anyConfigured(secrets: SecretStore): Boolean = SearchKind.entries.any { configured(it, secrets) }
}
