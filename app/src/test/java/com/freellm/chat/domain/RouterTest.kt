package com.freellm.chat.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTransport(
    private val script: (Target, List<ChatMsg>, Int) -> Flow<String>
) : ChatTransport {
    val calls = mutableListOf<Pair<String, List<ChatMsg>>>()
    override fun stream(target: Target, messages: List<ChatMsg>, maxTokens: Int?): Flow<String> {
        calls.add(target.key to messages)
        return script(target, messages, calls.size)
    }
}

class RouterTest {

    private fun tgt(provider: String, model: String, priority: Int, window: Int = 100000) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = model, provider = provider, displayName = model, priority = priority, contextWindow = window)
    )

    private fun req(vararg targets: Target, mode: ChatMode = ChatMode.AUTO, pinned: String? = null) = RouteRequest(
        messages = listOf(ChatMsg("user", "hi")),
        mode = mode,
        pinnedKey = pinned,
        candidates = targets.toList()
    )

    private fun textOf(events: List<RouteEvent>) =
        events.filterIsInstance<RouteEvent.Delta>().joinToString("") { it.text }

    @Test
    fun rateLimitThenSuccess() = runTest {
        val a = tgt("a", "m1", 1)
        val b = tgt("b", "m2", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429, 30) }
            else flow<String> { emit("hel"); emit("lo") }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, b)).toList()
        assertEquals("hello", textOf(events))
        val done = events.last() as RouteEvent.Done
        assertEquals(b.key, done.target.key)

        tr.calls.clear()
        router.stream(req(a, b)).toList()
        assertEquals(listOf(b.key), tr.calls.map { it.first })
    }

    @Test
    fun streamInterruptedContinuesOnNextModel() = runTest {
        val a = tgt("a", "m1", 1)
        val b = tgt("b", "m2", 2)
        val partial = "This is a long enough partial answer"
        val tr = FakeTransport { _, _, n ->
            if (n == 1) flow<String> { emit(partial); throw LlmException(ErrorKind.INTERRUPTED) }
            else flow<String> { emit(" and the rest") }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, b)).toList()
        assertEquals(partial + " and the rest", textOf(events))
        assertTrue(events.any { it is RouteEvent.Switched })
        val second = tr.calls[1].second
        assertTrue(second.last().content.contains("أكمل"))
        assertEquals("assistant", second[second.size - 2].role)
        assertEquals(partial, second[second.size - 2].content)
        assertTrue(events.last() is RouteEvent.Done)
    }

    @Test
    fun contextIsTrimmedKeepingSystemAndLast() {
        val msgs = listOf(ChatMsg("system", "sys")) +
            (1..20).map { ChatMsg(if (it % 2 == 1) "user" else "assistant", "x".repeat(350)) }
        val out = ContextTrimmer.trim(msgs, window = 1000, reserve = 100)
        assertEquals("sys", out.first().content)
        assertEquals(msgs.last(), out.last())
        assertTrue(out.size < msgs.size)
        assertEquals("user", out[1].role)
    }

    @Test
    fun pinnedModeDoesNotSwitch() = runTest {
        val a = tgt("a", "m1", 1)
        val b = tgt("b", "m2", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { throw LlmException(ErrorKind.SERVER, 500) } }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, b, mode = ChatMode.PINNED, pinned = a.key)).toList()
        val failed = events.last() as RouteEvent.Failed
        assertTrue(failed.pinned)
        assertEquals(1, tr.calls.size)
    }

    @Test
    fun authFailureDisablesProviderAndMovesOn() = runTest {
        val a = tgt("a", "m1", 1)
        val b = tgt("b", "m2", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.AUTH, 401) }
            else flow<String> { emit("ok") }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, b)).toList()
        assertTrue(events.any { it is RouteEvent.AuthFailed && it.providerId == "a" })
        assertEquals("ok", textOf(events))
        assertTrue(router.health.state(a, 0L) is ModelHealth.ProviderDown)
    }

    @Test
    fun allFailGivesArabicSummary() = runTest {
        val a = tgt("a", "m1", 1)
        val tr = FakeTransport { _, _, _ -> flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429, 30) } }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a)).toList()
        val failed = events.last() as RouteEvent.Failed
        assertTrue(failed.message.contains("a"))
        assertTrue(failed.message.contains("30"))
    }

    @Test
    fun deadModelIsSkippedOnNotFound() = runTest {
        val a = tgt("a", "m1", 1)
        val b = tgt("a", "m2", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.model.id == "m1") flow<String> { throw LlmException(ErrorKind.NOT_FOUND, 404) }
            else flow<String> { emit("fine") }
        }
        val router = Router(tr, clock = { 0L })
        router.stream(req(a, b)).toList()
        tr.calls.clear()
        val events = router.stream(req(a, b)).toList()
        assertEquals("fine", textOf(events))
        assertEquals(listOf(b.key), tr.calls.map { it.first })
    }

    @Test
    fun preferredModelGoesFirstButFallsBack() = runTest {
        val a = tgt("a", "m1", 1)
        val b = tgt("b", "m2", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "b") flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429, 30) }
            else flow<String> { emit("from-a") }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, b).copy(preferredKey = b.key)).toList()
        assertEquals(listOf(b.key, a.key), tr.calls.map { it.first })
        assertEquals("from-a", textOf(events))
    }

    @Test
    fun poorOutputRetriesOnNextModel() = runTest {
        val a = tgt("a", "m1", 1)
        val b = tgt("b", "m2", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { emit(" .") } else flow<String> { emit("A proper full answer.") }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, b)).toList()
        assertEquals(listOf(a.key, b.key), tr.calls.map { it.first })
        assertTrue(events.any { it is RouteEvent.Restart })
        assertEquals((events.last() as RouteEvent.Done).target.key, b.key)
    }

    @Test
    fun poorOutputWithNoFallbackIsKept() = runTest {
        val a = tgt("a", "m1", 1)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("ok,") } }
        val events = Router(tr, clock = { 0L }).stream(req(a)).toList()
        assertTrue(events.last() is RouteEvent.Done)
    }

    @Test
    fun ratingsRankModelsWithinSamePriorityTier() = runTest {
        val a = tgt("a", "m1", 5)
        val b = tgt("b", "m2", 5)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("fine answer") } }
        val router = Router(tr, clock = { 0L })
        router.stream(req(a, b).copy(rating = { if (it == b.key) 2 else 0 })).toList()
        assertEquals(listOf(b.key), tr.calls.map { it.first })
    }

    @Test
    fun outputQualityRules() {
        assertTrue(OutputQuality.isPoor(""))
        assertTrue(OutputQuality.isPoor("x"))
        assertTrue(OutputQuality.isPoor("Sorry, I can't"))
        assertTrue(!OutputQuality.isPoor("Paris is the capital of France."))
    }
}

class RouterRound10Test {
    private fun tgt(provider: String, model: String, priority: Int, rpm: Int = 0, rpd: Int = 0) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = model, provider = provider, displayName = model, priority = priority, rpm = rpm, rpd = rpd)
    )

    @Test
    fun forbiddenCoolsProviderForTenMinutesWithoutDisabling() {
        val h = Health(RouterConfig())
        val t = tgt("a", "m", 1)
        h.onFailure(t, LlmException(ErrorKind.AUTH, 403), 0L)
        h.onFailure(t, LlmException(ErrorKind.AUTH, 403), 1L)
        assertEquals(2, h.forbiddenCount("a"))
        assertTrue(h.state(t, 1000L) is ModelHealth.Cooling)
        assertTrue(h.state(t, 600_001L) is ModelHealth.Ready)
    }

    @Test
    fun unauthorizedStillDisablesProvider() {
        val h = Health(RouterConfig())
        val t = tgt("a", "m", 1)
        h.onFailure(t, LlmException(ErrorKind.AUTH, 401), 0L)
        assertTrue(h.state(t, 1L) is ModelHealth.ProviderDown)
    }

    @Test
    fun timeoutCountsOnceAfterRecentSuccess() {
        val h = Health(RouterConfig())
        val t = tgt("a", "m", 1)
        h.onSuccess(t, 1000L)
        h.onFailure(t, LlmException(ErrorKind.TIMEOUT), 2000L)
        h.onFailure(t, LlmException(ErrorKind.TIMEOUT), 2000L)
        org.junit.Assert.assertNull(h.blockedUntil(t, 2001L))
        h.onFailure(t, LlmException(ErrorKind.TIMEOUT), 2000L)
        org.junit.Assert.assertNotNull(h.blockedUntil(t, 2001L))
    }

    @Test
    fun timeoutCountsTripleWithoutRecentSuccess() {
        val h = Health(RouterConfig())
        val t = tgt("a", "m", 1)
        h.onSuccess(t, 0L)
        h.onFailure(t, LlmException(ErrorKind.TIMEOUT), 700_000L)
        org.junit.Assert.assertNotNull(h.blockedUntil(t, 700_001L))
    }

    @Test
    fun lowLimitProvidersDoNotHedgeByDefault() {
        val r = Router(FakeTransport { _, _, _ -> flow<String> { emit("x") } })
        assertTrue(!r.hedgeAllowed(tgt("a", "m", 1, rpm = 30)))
        assertTrue(!r.hedgeAllowed(tgt("a", "m", 1, rpd = 1000)))
        assertTrue(r.hedgeAllowed(tgt("a", "m", 1, rpm = 60, rpd = 5000)))
        assertTrue(r.hedgeAllowed(tgt("a", "m", 1)))
    }

    @Test
    fun networkFailureBeforeResponseIsNotCounted() = runTest {
        val a = tgt("a", "m1", 1, rpd = 10)
        val tr = FakeTransport { _, _, _ -> flow<String> { throw LlmException(ErrorKind.NETWORK) } }
        val router = Router(tr, clock = { 0L })
        router.stream(RouteRequest(messages = listOf(ChatMsg("user", "hi")), mode = ChatMode.AUTO, pinnedKey = null, candidates = listOf(a))).toList()
        assertEquals(0, router.limiter.snapshot(a, 0L).dayUsed)
    }

    @Test
    fun successfulRequestIsCounted() = runTest {
        val a = tgt("a", "m1", 1, rpd = 10)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("ok") } }
        val router = Router(tr, clock = { 0L })
        router.stream(RouteRequest(messages = listOf(ChatMsg("user", "hi")), mode = ChatMode.AUTO, pinnedKey = null, candidates = listOf(a))).toList()
        assertEquals(1, router.limiter.snapshot(a, 0L).dayUsed)
    }
}
