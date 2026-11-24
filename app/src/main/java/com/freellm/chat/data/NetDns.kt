package com.freellm.chat.data

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetDns(
    private val net: NetState?,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val system: (String) -> List<InetAddress> = { host ->
        val n = net?.network
        if (n != null) n.getAllByName(host).toList() else Dns.SYSTEM.lookup(host)
    },
    private val doh: (String) -> List<InetAddress> = { host -> DohResolver.lookup(host) }
) : Dns {
    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    fun clear() {
        cache.clear()
    }

    private fun sorted(list: List<InetAddress>): List<InetAddress> =
        list.sortedBy { if (it is Inet4Address) 0 else 1 }

    override fun lookup(hostname: String): List<InetAddress> {
        val now = clock()
        val hit = cache[hostname]
        if (hit != null && now - hit.second < TTL_MS) return hit.first
        val found = try {
            sorted(resolveSystem(hostname))
        } catch (e: UnknownHostException) {
            if (!hostname.contains('.') || LanGuard.isPrivateHost(hostname)) throw e
            val viaDoh = try {
                sorted(doh(hostname))
            } catch (x: Exception) {
                emptyList()
            }
            if (viaDoh.isEmpty()) throw e
            viaDoh
        }
        if (found.isEmpty()) throw UnknownHostException(hostname)
        cache[hostname] = found to now
        return found
    }

    private fun resolveSystem(host: String): List<InetAddress> = try {
        system(host)
    } catch (e: UnknownHostException) {
        throw e
    } catch (e: SecurityException) {
        Dns.SYSTEM.lookup(host)
    }

    private companion object {
        const val TTL_MS = 300_000L
    }
}

internal object DohResolver {
    private val plain: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val servers: List<DnsOverHttps> by lazy {
        listOf(
            DnsOverHttps.Builder().client(plain)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1")))
                .build(),
            DnsOverHttps.Builder().client(plain)
                .url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4")))
                .build()
        )
    }

    fun lookup(host: String): List<InetAddress> {
        for (s in servers) {
            val r = try {
                s.lookup(host)
            } catch (e: Exception) {
                emptyList()
            }
            if (r.isNotEmpty()) return r
        }
        throw UnknownHostException(host)
    }
}
