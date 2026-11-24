package com.freellm.chat.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterDirectFirstTest {
    private val answer = "A proper full answer."

    private fun tgt(provider: String, model: String = "m", priority: Int = 1, worker: Boolean = true) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://direct.example/$provider",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = model, provider = provider.substringBefore('#'), displayName = model, priority = priority),
        altBaseUrl = if (worker) "https://worker.example/$provider" else ""
    )

    private fun req(vararg t: Target, mode: ChatMode = ChatMode.AUTO, pinned: String? = null) = RouteRequest(
        messages = listOf(ChatMsg("user", "hi")),
        mode = mode,
        pinnedKey = pinned,
        candidates = t.toList()
    )

    private fun connectFail() = LlmException(ErrorKind.NETWORK, message = "unreachable", connect = true)

    @Test fun directSuccessNeverTouchesWorker() = runTest {
        val a = tgt("a")
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            flow { emit(answer) }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a)).toList()
        assertTrue(events.last() is RouteEvent.Done)
        assertTrue(seen.all { it.via == NetRoute.DIRECT })
        assertEquals(1, seen.size)
    }

    @Test fun connectFailuresFallToWorkerWithoutPenaltyAndRouteSticks() = runTest {
        val a = tgt("a", priority = 1)
        val b = tgt("b", priority = 2)
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            if (t.via == NetRoute.DIRECT) flow<String> { throw connectFail() } else flow { emit(answer) }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, b)).toList()
        val done = events.last() as RouteEvent.Done
        assertEquals(NetRoute.WORKER, done.target.via)
        assertEquals(a.key, done.target.key)
        assertNull(router.health.blockedUntil(a, 1L))
        assertNull(router.health.blockedUntil(b, 1L))
        assertEquals(NetRoute.WORKER, router.routes.route(a))
        seen.clear()
        val second = router.stream(req(a, b)).toList()
        assertTrue(second.last() is RouteEvent.Done)
        assertEquals(listOf(NetRoute.WORKER), seen.map { it.via })
    }

    @Test fun regionBlockWithoutWorkerSkipsProviderAndNextAnswers() = runTest {
        val a1 = tgt("a", "m1", 1, worker = false)
        val a2 = tgt("a", "m2", 2, worker = false)
        val b = tgt("b", "m1", 3, worker = false)
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.REGION, 403) } else flow { emit(answer) }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a1, a2, b)).toList()
        assertEquals(listOf("a", "b"), seen.map { it.providerId })
        assertEquals(b.key, (events.last() as RouteEvent.Done).target.key)
        assertNull(router.health.blockedUntil(a1, 1L))
        assertTrue(!router.routes.directAllowed(a2))
    }

    @Test fun rateLimitIsNeverDeferredToWorker() = runTest {
        val a = tgt("a", priority = 1)
        val b = tgt("b", priority = 2, worker = false)
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429, 30) } else flow { emit(answer) }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, b)).toList()
        assertTrue(seen.none { it.via == NetRoute.WORKER })
        assertEquals(b.key, (events.last() as RouteEvent.Done).target.key)
        assertNotNull(router.health.blockedUntil(a, 1L))
        assertTrue(router.routes.directAllowed(a))
    }

    @Test fun routerLevelFirstTokenTimeoutIsNeverDeferred() = runTest {
        val a = tgt("a")
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            flow<String> {
                delay(60_000)
                emit(answer)
            }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a)).toList()
        assertTrue(events.last() is RouteEvent.Failed)
        assertEquals(1, seen.size)
        assertEquals(NetRoute.DIRECT, seen.single().via)
        assertTrue(router.routes.directAllowed(a))
    }

    @Test fun pinnedModeDefersToWorkerOnceThenFails() = runTest {
        val a = tgt("a")
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            flow<String> { throw connectFail() }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(a, mode = ChatMode.PINNED, pinned = a.key)).toList()
        val failed = events.last() as RouteEvent.Failed
        assertTrue(failed.pinned)
        assertEquals(listOf(NetRoute.DIRECT, NetRoute.WORKER), seen.map { it.via })
    }

    @Test fun workerFailureSkipsRemainingModelsAndEmitsFailure() = runTest {
        val m1 = tgt("a", "m1", 1)
        val m2 = tgt("a", "m2", 2)
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            flow<String> { throw connectFail() }
        }
        val router = Router(tr, clock = { 0L })
        val events = router.stream(req(m1, m2)).toList()
        val failed = events.last() as RouteEvent.Failed
        assertTrue(seen.none { it.model.id == "m2" })
        assertTrue(seen.any { it.via == NetRoute.WORKER })
        assertTrue(failed.reasons.isNotEmpty())
        assertEquals(ErrorKind.NETWORK, failed.reasons.first().error)
    }
}
