package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SideTasksRouteTest {
    private fun tgt(provider: String, priority: Int, worker: Boolean = false) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://direct.example/$provider",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = "m-$provider", provider = provider, displayName = provider, priority = priority, modes = listOf("fast")),
        altBaseUrl = if (worker) "https://worker.example/$provider" else ""
    )

    @Test fun failingModelStopsBeingPicked() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.SERVER, 500) } else flow { emit("ok") }
        }
        val router = Router(tr, clock = { 0L })
        val side = SideTasks(tr, router, clock = { 0L })
        repeat(3) { assertEquals("ok", side.run(listOf(a, b), "s", "u")) }
        assertNotNull(router.health.blockedUntil(a, 1L))
        val before = tr.calls.count { it.first == a.key }
        assertEquals("ok", side.run(listOf(a, b), "s", "u"))
        assertEquals(before, tr.calls.count { it.first == a.key })
    }

    @Test fun workerRetryHappensOnlyWhenProxyExists() = runTest {
        val withWorker = tgt("a", 1, worker = true)
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            if (t.via == NetRoute.DIRECT) flow<String> { throw LlmException(ErrorKind.NETWORK, message = "x", connect = true) } else flow { emit("ok") }
        }
        val router = Router(tr, clock = { 0L })
        val side = SideTasks(tr, router, clock = { 0L })
        assertEquals("ok", side.run(listOf(withWorker), "s", "u"))
        assertEquals(listOf(NetRoute.DIRECT, NetRoute.WORKER), seen.map { it.via })
        assertEquals(NetRoute.WORKER, router.routes.route(withWorker))
        assertNull(router.health.blockedUntil(withWorker, 1L))

        seen.clear()
        val noWorker = tgt("b", 1)
        val tr2 = FakeTransport { t, _, _ ->
            seen.add(t)
            flow<String> { throw LlmException(ErrorKind.NETWORK, message = "x", connect = true) }
        }
        val side2 = SideTasks(tr2, Router(tr2, clock = { 0L }), clock = { 0L })
        assertNull(side2.run(listOf(noWorker), "s", "u"))
        assertEquals(listOf(NetRoute.DIRECT), seen.map { it.via })
    }

    @Test fun routedTargetIsUsedWhenWorkerRouteIsActive() = runTest {
        val a = tgt("a", 1, worker = true)
        val seen = mutableListOf<Target>()
        val tr = FakeTransport { t, _, _ ->
            seen.add(t)
            flow { emit("ok") }
        }
        val router = Router(tr, clock = { 0L })
        router.routes.onDirectFailure("a", ErrorKind.REGION)
        router.routes.onWorkerSuccess("a")
        val side = SideTasks(tr, router, clock = { 0L })
        assertEquals("ok", side.run(listOf(a), "s", "u"))
        assertTrue(seen.single().via == NetRoute.WORKER)
    }
}
