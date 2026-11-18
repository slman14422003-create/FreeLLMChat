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

    suspend fun fetch(url: String): String? {
        if (!UrlPolicy.allowedUrl(url)) return null
        val px = try {
            proxy().trim().trimEnd('/')
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ""
        }
        if (px.isNotEmpty()) {
            val via = try {
                Request.Builder().url(px + "/fetch?url=" + URLEncoder.encode(url, "UTF-8"))
                    .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9")
                    .build()
            } catch (e: IllegalArgumentException) {
                null
            }
            if (via != null) get(via)?.let { return it }
        }
        val req = Request.Builder().url(url)
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9")
            .header("User-Agent", USER_AGENT)
            .build()
        return get(req)
    }

    private suspend fun get(req: Request): String? = try {
        client.newCall(req).await().use { r ->
            if (!r.isSuccessful) return@use null
            val type = r.header("Content-Type").orEmpty().lowercase()
            val html = type.isEmpty() || type.contains("html") || type.contains("xml")
            if (!html && !type.startsWith("text/plain")) return@use null
            val body = r.body ?: return@use null
            val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val bytes = withContext(Dispatchers.IO) {
                val src = body.source()
                src.request(MAX_BYTES)
                src.buffer.readByteArray(minOf(src.buffer.size, MAX_BYTES))
            }
            val raw = String(bytes, charset)
            val text = if (html) HtmlText.extract(raw) else raw.trim()
            text.takeIf { it.length >= MIN_CHARS }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private companion object {
        const val MAX_BYTES = 700_000L
        const val MIN_CHARS = 200
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) FreeLLMChat/1.0"
    }
}
