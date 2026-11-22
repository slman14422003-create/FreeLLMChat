package com.freellm.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

class NetDnsTest {
    private val v4 = InetAddress.getByAddress(byteArrayOf(1, 2, 3, 4))
    private val v6 = InetAddress.getByAddress(ByteArray(16) { if (it == 15) 1 else 0 })
    private var now = 0L

    @Test fun cacheHitWithinTtl() {
        var sys = 0
        val dns = NetDns(null, clock = { now }, system = { sys++; listOf(v4) }, doh = { throw UnknownHostException(it) })
        dns.lookup("example.com")
        now = 299_999
        dns.lookup("example.com")
        assertEquals(1, sys)
        now = 300_001
        dns.lookup("example.com")
        assertEquals(2, sys)
    }

    @Test fun clearDropsCache() {
        var sys = 0
        val dns = NetDns(null, clock = { now }, system = { sys++; listOf(v4) }, doh = { throw UnknownHostException(it) })
        dns.lookup("example.com")
        dns.clear()
        dns.lookup("example.com")
        assertEquals(2, sys)
    }

    @Test fun failuresAreNotCached() {
        var sys = 0
        var dohCalls = 0
        val dns = NetDns(null, clock = { now }, system = { sys++; throw UnknownHostException(it) }, doh = { dohCalls++; throw UnknownHostException(it) })
        repeat(2) {
            try {
                dns.lookup("example.com")
                fail()
            } catch (e: UnknownHostException) {
            }
        }
        assertEquals(2, sys)
        assertEquals(2, dohCalls)
    }

    @Test fun privateHostsNeverUseDoh() {
        var dohCalls = 0
        val dns = NetDns(null, clock = { now }, system = { throw UnknownHostException(it) }, doh = { dohCalls++; listOf(v4) })
        for (h in listOf("nas.local", "box.lan", "router.home.arpa", "localhost", "intranet", "192.168.1.5")) {
            try {
                dns.lookup(h)
                fail(h)
            } catch (e: UnknownHostException) {
            }
        }
        assertEquals(0, dohCalls)
    }

    @Test fun publicHostFallsBackToDohWhenSystemFails() {
        var dohCalls = 0
        val dns = NetDns(null, clock = { now }, system = { throw UnknownHostException(it) }, doh = { dohCalls++; listOf(v4) })
        assertEquals(listOf(v4), dns.lookup("api.example.com"))
        assertEquals(1, dohCalls)
        dns.lookup("api.example.com")
        assertEquals(1, dohCalls)
    }

    @Test fun ipv4ComesFirst() {
        val dns = NetDns(null, clock = { now }, system = { listOf(v6, v4) }, doh = { throw UnknownHostException(it) })
        assertTrue(dns.lookup("example.com").first() is Inet4Address)
    }
}
