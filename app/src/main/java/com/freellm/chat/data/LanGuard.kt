package com.freellm.chat.data

import okhttp3.Interceptor
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
                n[0] == 100 && n[1] in 64..127 -> true
                else -> false
            }
        }
        return !host.contains('.')
    }

    private val builtin = setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
    private val lanSuffixes = listOf(".local", ".lan", ".home.arpa")

    @Volatile
    private var userHosts: Set<String> = emptySet()

    fun hostOfUrl(url: String): String? {
        val u = url.trim()
        val rest = when {
            u.startsWith("http://", true) -> u.substring(7)
            u.startsWith("https://", true) -> u.substring(8)
            else -> return null
        }
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@')
        val h = if (authority.startsWith("[")) authority.substringBefore(']').trimStart('[') else authority.substringBefore(':')
        return h.lowercase().ifEmpty { null }
    }

    fun registerUrl(url: String?) {
        val h = url?.let { hostOfUrl(it) } ?: return
        if (isPrivateHost(h) && h !in builtin) userHosts = userHosts + h
    }

    fun replaceUserHosts(urls: Collection<String>) {
        userHosts = urls.mapNotNull { hostOfUrl(it) }.filter { isPrivateHost(it) && it !in builtin }.toSet()
    }

    fun cleartextPermitted(hostRaw: String): Boolean {
        val h = hostRaw.trim().trim('[', ']').lowercase()
        if (!isPrivateHost(h)) return false
        return h in builtin || h in userHosts || lanSuffixes.any { h.endsWith(it) }
    }

    fun allows(scheme: String, host: String): Boolean = scheme == "https" || isPrivateHost(host)

    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!allows(url.scheme, url.host) || (url.scheme != "https" && !cleartextPermitted(url.host))) throw IOException("cleartext http is limited to private LAN addresses")
        chain.proceed(chain.request())
    }
}
