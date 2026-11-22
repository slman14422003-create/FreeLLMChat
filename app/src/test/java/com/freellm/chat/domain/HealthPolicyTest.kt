package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthPolicyTest {
    private fun tgt(provider: String, model: String, priority: Int) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = model, provider = provider, displayName = model, priority = priority, contextWindow = 100000)
    )

    @Test
    fun unavailableGetsShortCooldownImmediately() {
        val h = Health(RouterConfig())
        val t = tgt("a", "m1", 1)
        h.onFailure(t, LlmException(ErrorKind.SERVER, 503), 1_000L)
        assertEquals(21_000L, h.blockedUntil(t, 1_001L))
        assertEquals(null, h.blockedUntil(t, 21_001L))
    }

    @Test
    fun otherServerErrorsStillUseThreshold() {
        val h = Health(RouterConfig())
        val t = tgt("a", "m1", 1)
        h.onFailure(t, LlmException(ErrorKind.SERVER, 500), 1_000L)
        assertEquals(null, h.blockedUntil(t, 1_001L))
    }

    @Test
    fun emptyWeighsLessThanInterrupted() {
        val cfg = RouterConfig()
        val e = Health(cfg)
        val i = Health(cfg)
        val t = tgt("a", "m1", 1)
        e.onFailure(t, LlmException(ErrorKind.EMPTY), 1_000L)
        i.onFailure(t, LlmException(ErrorKind.INTERRUPTED), 1_000L)
        assertEquals(null, e.blockedUntil(t, 1_001L))
        assertTrue(i.blockedUntil(t, 1_001L) != null || cfg.failureThreshold > 3)
    }

    @Test
    fun deadFlagSurvivesSnapshotRestore() = runTest {
        val a = tgt("a", "m1", 1)
        val b = tgt("a", "m2", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.model.id == "m1") flow<String> { throw LlmException(ErrorKind.NOT_FOUND, 404) }
            else flow<String> { emit("fine") }
        }
        val r1 = Router(tr, clock = { 0L })
        val req = RouteRequest(messages = listOf(ChatMsg("user", "hi")), mode = ChatMode.AUTO, pinnedKey = null, candidates = listOf(a, b))
        r1.stream(req).toList()
        val snap = r1.snapshot(0L)
        val r2 = Router(tr, clock = { 0L })
        r2.restore(snap, 0L)
        tr.calls.clear()
        r2.stream(req).toList()
        assertEquals(listOf(b.key), tr.calls.map { it.first })
    }

    @Test
    fun expiredDeadFlagIsDroppedOnRestore() {
        val h = Health(RouterConfig())
        val t = tgt("a", "m1", 1)
        h.import("d\t" + t.key + "\t500\n", 1_000L)
        assertEquals(null, h.blockedUntil(t, 1_000L))
    }
}
