package com.freellm.chat.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

object WorkerAuth {
    const val HEADER = "X-App-Secret"

    class Parsed(val host: String, val port: Int, val secret: String)

    fun parse(proxy: String): Parsed? {
        val u = proxy.trim().toHttpUrlOrNull() ?: return null
        val seg = u.pathSegments.firstOrNull { it.isNotEmpty() } ?: return null
        return Parsed(u.host, u.port, seg)
    }

    fun split(url: HttpUrl, parsed: Parsed?): Pair<HttpUrl, String?> {
        if (parsed == null || url.host != parsed.host || url.port != parsed.port) return url to null
        val first = url.pathSegments.firstOrNull()
        if (first != parsed.secret) return url to null
        return url.newBuilder().removePathSegment(0).build() to parsed.secret
    }

    fun maskUrl(url: String): String {
        val u = url.trim().toHttpUrlOrNull() ?: return url
        if (u.pathSegments.none { it.isNotEmpty() }) return url
        val rest = u.pathSegments.drop(1).filter { it.isNotEmpty() }
        val tail = if (rest.isEmpty()) "" else "/" + rest.joinToString("/")
        return u.scheme + "://" + u.host + (if (u.port != HttpUrl.defaultPort(u.scheme)) ":" + u.port else "") + "/***" + tail
    }
}

class WorkerAuthInterceptor : Interceptor {
    @Volatile var proxy: String = ""
        set(v) {
            field = v
            parsed = WorkerAuth.parse(v)
        }
    @Volatile private var parsed: WorkerAuth.Parsed? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val (url, secret) = WorkerAuth.split(req.url, parsed)
        if (secret == null) return chain.proceed(req)
        return chain.proceed(req.newBuilder().url(url).header(WorkerAuth.HEADER, secret).build())
    }
}

object Redact {
    private val patterns = listOf(
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}") to "$1***",
        Regex("(?i)((?:api[_-]?key|key|token|secret|x-app-secret|authorization)[=:]\\s*)[^&\\s\"',]+") to "$1***",
        Regex("AIza[0-9A-Za-z_-]{20,}") to "***",
        Regex("\\b(?:gsk|sk|ghp|github_pat|tvly|xai)[_-][A-Za-z0-9_-]{12,}") to "***"
    )
    private val urlSecret = Regex("(https?://[^/\\s]*workers\\.dev)/[^/\\s?#]+")

    fun text(s: String, extra: Collection<String> = emptyList()): String {
        var out = s
        for (w in extra) if (w.length >= 6) out = out.replace(w, "***")
        for ((re, rep) in patterns) out = re.replace(out, rep)
        return urlSecret.replace(out, "$1/***")
    }
}
