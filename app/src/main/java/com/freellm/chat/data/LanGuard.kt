package com.freellm.chat.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Allows plain http only for loopback, private LAN ranges and local hostnames. */
object LanGuard {
    fun isPrivateHost(hostRaw: String): Boolean {
        val host = hostRaw.trim().trim('[', ']').lowercase()
        if (host.isEmpty()) return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true
        if (host.contains(':')) {
            return host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80")
        }
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } }) {
            val n = parts.map { it.toInt() }
            if (n.any { it > 255 }) return false
            return when {
                n[0] == 10 -> true
                n[0] == 127 -> true
                n[0] == 192 && n[1] == 168 -> true
                n[0] == 172 && n[1] in 16..31 -> true
                n[0] == 169 && n[1] == 254 -> true
                else -> false
            }
        }
        return !host.contains('.')
    }

    fun allows(scheme: String, host: String): Boolean = scheme == "https" || isPrivateHost(host)

    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!allows(url.scheme, url.host)) throw IOException("cleartext http is limited to private LAN addresses")
        chain.proceed(chain.request())
    }
}
