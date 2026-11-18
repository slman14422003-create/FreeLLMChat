package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class BudgetRoutingTest {
    private fun tgt(
        provider: String,
        priority: Int,
        window: Int = 100_000,
        rpd: Int = 0,
        modes: List<String> = emptyList(),
        reasoning: Boolean = false
    ) = Target(
        providerId = provider, providerName = provider, baseUrl = "https://example.com", headers = emptyMap(), apiKey = "k",
        model = ModelDef(
            id = "m-$provider", provider = provider, displayName = provider, priority = priority,
            contextWindow = window, rpd = rpd, modes = modes, reasoning = reasoning
        )
    )

    private fun req(vararg t: Target, proactive: Boolean = false, est: Int = 0) = RouteRequest(
        messages = listOf(ChatMsg("user", "hi")),
        mode = ChatMode.AUTO,
        pinnedKey = null,
        candidates = t.toList(),
        proactive = proactive,
        estimatedTokens = est
    )

    private fun router(limiter: UsageLimiter = UsageLimiter(zone = ZoneOffset.UTC)) =
        Router(FakeTransport { _, _, _ -> flow { emit("ok") } }, clock = { 0L }, limiter = limiter)

    @Test fun pressureReflectsDailyUse() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val a = tgt("a", 1, rpd = 10)
        repeat(9) { l.record(a, 0L) }
        assertEquals(0.9, l.pressure(a, 0L), 1e-9)
        assertEquals(0.9, l.budgetPressure(a, 0L), 1e-9)
    }

    @Test fun pressureIsZeroWithoutLimits() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val a = tgt("a", 1)
        repeat(5) { l.record(a, 0L) }
        assertEquals(0.0, l.pressure(a, 0L), 0.0)
    }

    @Test fun proactiveModeMovesNearLimitModelBack() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val a = tgt("a", 1, rpd = 10)
        val b = tgt("b", 2)
        repeat(9) { l.record(a, 0L) }
        val r = router(l)
        assertEquals("a", r.order(req(a, b, proactive = false)).first().providerId)
        assertEquals("b", r.order(req(a, b, proactive = true)).first().providerId)
    }

    @Test fun nearLimitEventIsEmittedOnceAtEightyPercent() = runTest {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val a = tgt("a", 1, rpd = 10)
        repeat(8) { l.record(a, 0L) }
        val r = router(l)
        val first = r.stream(req(a, proactive = true)).toList()
        val hit = first.filterIsInstance<RouteEvent.NearLimit>()
        assertEquals(1, hit.size)
        assertEquals(80, hit.first().percent)
        val second = r.stream(req(a, proactive = true)).toList()
        assertTrue(second.none { it is RouteEvent.NearLimit })
    }

    @Test fun noNearLimitEventWhenDisabled() = runTest {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val a = tgt("a", 1, rpd = 10)
        repeat(9) { l.record(a, 0L) }
        val events = router(l).stream(req(a, proactive = false)).toList()
        assertTrue(events.none { it is RouteEvent.NearLimit })
    }

    @Test fun largeRequestPrefersBiggerContext() {
        val small = tgt("small", 1, window = 4_000)
        val big = tgt("big", 2, window = 100_000)
        val r = router()
        assertEquals("small", r.order(req(small, big, est = 0)).first().providerId)
        assertEquals("big", r.order(req(small, big, est = 5_000)).first().providerId)
    }

    @Test fun lightPickerPrefersFastNonReasoning() {
        val smart = tgt("smart", 1, modes = listOf("smart"), reasoning = true)
        val thinkFast = tgt("thinkfast", 1, modes = listOf("fast"), reasoning = true)
        val fast = tgt("fast", 5, modes = listOf("fast"))
        val r = router()
        val picks = LightPicker.pick(listOf(smart, thinkFast, fast), r.health, r.limiter, 0L)
        assertEquals(listOf("fast", "thinkfast", "smart"), picks.map { it.providerId })
    }

    @Test fun lightPickerSkipsNearlyExhaustedProviders() {
        val l = UsageLimiter(zone = ZoneOffset.UTC)
        val a = tgt("a", 1, rpd = 10, modes = listOf("fast"))
        val b = tgt("b", 2, modes = listOf("fast"))
        repeat(9) { l.record(a, 0L) }
        val r = router(l)
        assertEquals(listOf("b"), LightPicker.pick(listOf(a, b), r.health, r.limiter, 0L).map { it.providerId })
    }

    @Test fun sideTaskUsesLightModelAndReturnsText() = runTest {
        val smart = tgt("smart", 1, modes = listOf("smart"), reasoning = true)
        val fast = tgt("fast", 3, modes = listOf("fast"))
        val tr = FakeTransport { _, _, _ -> flow { emit("sum"); emit("mary") } }
        val side = SideTasks(tr, Router(tr, clock = { 0L }), clock = { 0L })
        assertEquals("summary", side.run(listOf(smart, fast), "sys", "user"))
        assertEquals("fast/m-fast", tr.calls.first().first)
    }

    @Test fun sideTaskFallsBackToNextAndReturnsNullOnTotalFailure() = runTest {
        val a = tgt("a", 1, modes = listOf("fast"))
        val b = tgt("b", 2, modes = listOf("fast"))
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.SERVER, 500) } else flow { emit("ok") }
        }
        val side = SideTasks(tr, Router(tr, clock = { 0L }), clock = { 0L })
        assertEquals("ok", side.run(listOf(a, b), "s", "u"))

        val bad = FakeTransport { _, _, _ -> flow<String> { throw LlmException(ErrorKind.SERVER, 500) } }
        val side2 = SideTasks(bad, Router(bad, clock = { 0L }), clock = { 0L })
        assertEquals(null, side2.run(listOf(a, b), "s", "u"))
    }
}
