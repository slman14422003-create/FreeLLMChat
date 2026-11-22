package com.freellm.chat.data

import com.freellm.chat.domain.HtmlText
import com.freellm.chat.domain.UrlPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class PageFetcher(base: OkHttpClient, private val proxy: suspend () -> String = { "" }) {
    private val client: OkHttpClient = base.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val found = base.dns.lookup(hostname)
                if (found.isEmpty() || found.any { UrlPolicy.blockedAddress(it) }) throw UnknownHostException(hostname)
                return found
            }
        })
        .addNetworkInterceptor(Interceptor { chain ->
            val u = chain.request().url
            if (!UrlPolicy.allowed(u.scheme, u.host)) throw IOException("blocked address")
            chain.proceed(chain.request())
        })
        .build()

    private sealed class Result {
        class Text(val text: String) : Result()
        data object Rejected : Result()
        data object Unreachable : Result()
    }

    suspend fun fetch(url: String): String? {
        if (!UrlPolicy.allowedUrl(url)) return null
        val direct = try {
            Request.Builder().url(url)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9")
                .header("User-Agent", USER_AGENT)
                .build()
        } catch (e: IllegalArgumentException) {
            return null
        }
        when (val first = get(direct)) {
            is Result.Text -> return first.text
            Result.Rejected -> return null
            Result.Unreachable -> {}
        }
        val px = try {
            proxy().trim().trimEnd('/')
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ""
        }
        if (px.isEmpty()) return null
        val via = try {
            Request.Builder().url(px + "/fetch?url=" + URLEncoder.encode(url, "UTF-8"))
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9")
                .build()
        } catch (e: IllegalArgumentException) {
            return null
        }
        return (get(via) as? Result.Text)?.text
    }

    private suspend fun get(req: Request): Result = try {
        client.newCall(req).await().use { r ->
            if (r.code == 403 || r.code == 451) return@use Result.Unreachable
            if (!r.isSuccessful) return@use Result.Rejected
            val type = r.header("Content-Type").orEmpty().lowercase()
            val html = type.isEmpty() || type.contains("html") || type.contains("xml")
            if (!html && !type.startsWith("text/plain")) return@use Result.Rejected
            val body = r.body ?: return@use Result.Rejected
            val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val bytes = withContext(Dispatchers.IO) {
                val src = body.source()
                src.request(MAX_BYTES)
                src.buffer.readByteArray(minOf(src.buffer.size, MAX_BYTES))
            }
            val raw = String(bytes, charset)
            val text = if (html) HtmlText.extract(raw) else raw.trim()
            if (text.length >= MIN_CHARS) Result.Text(text) else Result.Rejected
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        if (e.message == "blocked address") Result.Rejected else Result.Unreachable
    } catch (e: Throwable) {
        Result.Rejected
    }

    private companion object {
        const val MAX_BYTES = 700_000L
        const val MIN_CHARS = 200
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) FreeLLMChat/1.0"
    }
}
