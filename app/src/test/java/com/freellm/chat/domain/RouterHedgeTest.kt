package com.freellm.chat.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RouterHedgeTest {
    private fun tgt(provider: String, priority: Int, reasoning: Boolean = false) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = "m-$provider", provider = provider, displayName = provider, priority = priority, reasoning = reasoning)
    )

    private fun req(vararg t: Target) = RouteRequest(
        messages = listOf(ChatMsg("user", "hi")),
        mode = ChatMode.AUTO,
        pinnedKey = null,
        candidates = t.toList()
    )

    private fun text(events: List<RouteEvent>) = events.filterIsInstance<RouteEvent.Delta>().joinToString("") { it.text }

    private val cfg = RouterConfig(hedgeMs = 1000, firstTokenTimeoutMs = 20_000)

    @Test fun slowPrimaryIsRacedByNextProvider() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { delay(60_000); emit("late") } else flow<String> { delay(500); emit("fast") }
        }
        val events = Router(tr, cfg, clock = { 0L }).stream(req(a, b)).toList()
        assertEquals("fast", text(events))
        assertEquals(b.key, (events.last() as RouteEvent.Done).target.key)
        assertTrue(currentTime < 20_000)
    }

    @Test fun fastPrimaryNeverHedges() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { delay(200); emit("ok") } }
        val events = Router(tr, cfg, clock = { 0L }).stream(req(a, b)).toList()
        assertEquals("ok", text(events))
        assertEquals(1, tr.calls.size)
    }

    @Test fun primaryStillWinsWhenItAnswersDuringRace() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { delay(1500); emit("primary") } else flow<String> { delay(5000); emit("second") }
        }
        val events = Router(tr, cfg, clock = { 0L }).stream(req(a, b)).toList()
        assertEquals("primary", text(events))
        assertEquals(a.key, (events.last() as RouteEvent.Done).target.key)
    }

    @Test fun bothHangingEndsInFailure() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { delay(120_000); emit("never") } }
        val events = Router(tr, cfg, clock = { 0L }).stream(req(a, b)).toList()
        assertTrue(events.last() is RouteEvent.Failed)
        assertTrue(currentTime <= 21_000)
    }

    @Test fun failingPrimaryFallsToHedgeLane() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { delay(1500); throw LlmException(ErrorKind.SERVER, 500) } else flow<String> { delay(1800); emit("backup") }
        }
        val events = Router(tr, cfg, clock = { 0L }).stream(req(a, b)).toList()
        assertEquals("backup", text(events))
    }

    @Test fun disabledHedgeKeepsSequentialBehaviour() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { delay(5000); emit("slow") } }
        val events = Router(tr, RouterConfig(firstTokenTimeoutMs = 20_000), clock = { 0L }).stream(req(a, b)).toList()
        assertEquals("slow", text(events))
        assertEquals(1, tr.calls.size)
    }

    @Test fun reasoningModelsGetLongerFirstTokenWindow() = runTest {
        val a = tgt("a", 1, reasoning = true)
        val tr = FakeTransport { _, _, _ -> flow<String> { delay(30_000); emit("thought") } }
        val events = Router(tr, RouterConfig(firstTokenTimeoutMs = 20_000), clock = { 0L }).stream(req(a)).toList()
        assertEquals("thought", text(events))
    }

    @Test fun networkBlipRetriesOnce() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { _, _, n ->
            if (n <= 2) flow<String> { throw java.io.IOException("offline") } else flow<String> { emit("ok") }
        }
        val events = Router(tr, clock = { 0L }).stream(req(a, b)).toList()
        assertEquals("ok", text(events))
        assertEquals(3, tr.calls.size)
    }

    @Test fun persistentNetworkFailureStopsAfterOneRetry() = runTest {
        val a = tgt("a", 1)
        val tr = FakeTransport { _, _, _ -> flow<String> { throw java.io.IOException("offline") } }
        val events = Router(tr, clock = { 0L }).stream(req(a)).toList()
        assertTrue(events.last() is RouteEvent.Failed)
        assertEquals(2, tr.calls.size)
    }

    @Test fun clearTransientReopensCooledModels() {
        val h = Health(RouterConfig())
        val t = tgt("a", 1)
        repeat(3) { h.onFailure(t, LlmException(ErrorKind.SERVER, 500), 0L) }
        assertTrue(h.blockedUntil(t, 1L) != null)
        assertTrue(h.clearTransient())
        assertTrue(h.blockedUntil(t, 1L) == null)
    }

    @Test fun timeoutsCountDoubleTowardsCooldown() {
        val h = Health(RouterConfig())
        val t = tgt("a", 1)
        h.onFailure(t, LlmException(ErrorKind.TIMEOUT), 0L)
        h.onFailure(t, LlmException(ErrorKind.TIMEOUT), 0L)
        assertTrue(h.blockedUntil(t, 1L) != null)
    }
}
