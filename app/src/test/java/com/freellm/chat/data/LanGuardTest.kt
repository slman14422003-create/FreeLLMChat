package com.freellm.chat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanGuardTest {
    @Test
    fun privateRangesAllowed() {
        listOf("192.168.1.10", "10.0.0.5", "172.16.0.1", "172.31.255.1", "127.0.0.1", "localhost", "pc.local", "169.254.1.1", "[::1]", "mypc")
            .forEach { assertTrue(it, LanGuard.isPrivateHost(it)) }
    }

    @Test
    fun publicRejected() {
        listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.1.1", "example.com", "300.1.1.1", "")
            .forEach { assertFalse(it, LanGuard.isPrivateHost(it)) }
    }

    @Test
    fun httpsAlwaysAllowed() {
        assertTrue(LanGuard.allows("https", "example.com"))
        assertFalse(LanGuard.allows("http", "example.com"))
        assertTrue(LanGuard.allows("http", "192.168.0.2"))
    }
}

class AutoBackupPruneTest {
    @Test
    fun keepsNewestFive() {
        val names = (1..8).map { "freellm-auto-2026-10-0$it.bak" }
        val drop = AutoBackup.toDelete(names, 5)
        org.junit.Assert.assertEquals(listOf("freellm-auto-2026-10-03.bak", "freellm-auto-2026-10-02.bak", "freellm-auto-2026-10-01.bak"), drop)
    }
}
