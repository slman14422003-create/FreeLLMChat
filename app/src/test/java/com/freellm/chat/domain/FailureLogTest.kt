package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FailureLogTest {
    private class Mem : UsageStore {
        val m = HashMap<String, String>()
        override fun read(key: String) = m[key]
        override fun write(key: String, value: String) { m[key] = value }
    }

    @Test
    fun keepsNewestFirstAndCapsSize() {
        val store = Mem()
        val log = FailureLog(store, max = 3)
        (1..5).forEach { log.add(it.toLong(), "p$it", "r|x") }
        assertEquals(listOf("p5", "p4", "p3"), log.all().map { it.provider })
        assertEquals("r x", log.all().first().reason)
        assertEquals(3, FailureLog(store, max = 3).all().size)
    }

    @Test
    fun snapshotCountsRequests() {
        val lim = UsageLimiter()
        val t = Target("a", "A", "https://x", emptyMap(), "k",
            ModelDef(id = "m", provider = "a", displayName = "m", priority = 1, rpm = 5, rpd = 10))
        lim.record(t, 1000L); lim.record(t, 2000L)
        val s = lim.snapshot(t, 3000L)
        assertEquals(2, s.minUsed); assertEquals(2, s.dayUsed); assertEquals(5, s.minLimit)
    }
}
