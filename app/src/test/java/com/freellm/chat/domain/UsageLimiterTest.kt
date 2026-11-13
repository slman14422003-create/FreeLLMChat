package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class UsageLimiterTest {

    private fun tgt(provider: String, model: String, rpm: Int = 0, rph: Int = 0, rpd: Int = 0, rpmo: Int = 0, perModel: Boolean = false, shared: Boolean = false) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = model, provider = provider, displayName = model, priority = 1, rpm = rpm, rph = rph, rpd = rpd, rpmo = rpmo),
        perModelLimits = perModel,
        sharedLimits = shared
    )

    private class MemStore : UsageStore {
        val map = HashMap<String, String>()
        override fun read(key: String): String? = map[key]
        override fun write(key: String, value: String) {
            map[key] = value
        }
    }

    @Test
    fun noLimitsNeverBlocks() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val t = tgt("p", "m")
        repeat(100) { l.record(t, 1000L + it) }
        assertNull(l.blockedUntil(t, 2000L))
    }

    @Test
    fun minuteWindowBlocksThenReleases() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val t = tgt("p", "m", rpm = 2)
        l.record(t, 10_000L)
        l.record(t, 20_000L)
        assertEquals(70_000L, l.blockedUntil(t, 30_000L))
        assertNull(l.blockedUntil(t, 70_001L))
    }

    @Test
    fun hourWindowBlocks() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val t = tgt("p", "m", rph = 3)
        l.record(t, 0L)
        l.record(t, 1_000L)
        l.record(t, 2_000L)
        assertEquals(3_600_000L, l.blockedUntil(t, 5_000L))
    }

    @Test
    fun dayCounterBlocksUntilMidnight() {
        val store = MemStore()
        val l = UsageLimiter(store, ZoneOffset.UTC)
        val t = tgt("p", "m", rpd = 2)
        val noon = 12 * 3_600_000L
        l.record(t, noon)
        assertNull(l.blockedUntil(t, noon))
        l.record(t, noon + 1)
        assertEquals(24 * 3_600_000L, l.blockedUntil(t, noon + 2))
        assertNull(l.blockedUntil(t, 24 * 3_600_000L + 1))
    }

    @Test
    fun countersSurviveRestart() {
        val store = MemStore()
        val t = tgt("p", "m", rpmo = 2)
        val first = UsageLimiter(store, ZoneOffset.UTC)
        first.record(t, 1_000L)
        first.record(t, 2_000L)
        val second = UsageLimiter(store, ZoneOffset.UTC)
        assertNotNull(second.blockedUntil(t, 3_000L))
    }

    @Test
    fun perModelScopeIsolatesModels() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val a = tgt("p", "a", rpm = 1, perModel = true)
        val b = tgt("p", "b", rpm = 1, perModel = true)
        l.record(a, 1_000L)
        assertNotNull(l.blockedUntil(a, 2_000L))
        assertNull(l.blockedUntil(b, 2_000L))
    }

    @Test
    fun sharedLimitsOverridePerModelScope() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val a = tgt("p", "a", rpm = 1, perModel = true, shared = true)
        val b = tgt("p", "b", rpm = 1, perModel = true, shared = true)
        l.record(a, 1_000L)
        assertNotNull(l.blockedUntil(b, 2_000L))
    }
}
