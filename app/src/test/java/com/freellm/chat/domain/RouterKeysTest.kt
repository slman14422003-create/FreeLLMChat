package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterKeysTest {
    private fun tgt(providerId: String, model: String, priority: Int, key: String) = Target(
        providerId = providerId,
        providerName = providerId.substringBefore('#'),
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = key,
        model = ModelDef(id = model, provider = providerId.substringBefore('#'), displayName = model, priority = priority)
    )

    private fun req(mode: ChatMode, pinned: String?, vararg t: Target) = RouteRequest(
        messages = listOf(ChatMsg("user", "hi")),
        mode = mode,
        pinnedKey = pinned,
        candidates = t.toList()
    )

    @Test fun keysOfSameProviderStayTogetherInOrder() {
        val a1 = tgt("a", "m", 1, "k1")
        val b1 = tgt("b", "m", 1, "k1")
        val a2 = tgt("a#2", "m", 1, "k2")
        val grouped = groupKeys(listOf(a1, b1, a2))
        assertEquals(listOf(a1.providerId, a2.providerId, b1.providerId), grouped.map { it.providerId })
    }

    @Test fun limitedKeyFallsToNextKeyBeforeAnotherProvider() = runTest {
        val a1 = tgt("a", "m", 1, "k1")
        val a2 = tgt("a#2", "m", 1, "k2")
        val b1 = tgt("b", "m", 1, "k1")
        val tr = FakeTransport { t, _, _ ->
            if (t.apiKey == "k1" && t.providerId == "a") flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429) }
            else flow<String> { emit("from " + t.providerId) }
        }
        val events = Router(tr, clock = { 0L }).stream(req(ChatMode.AUTO, null, a1, b1, a2)).toList()
        assertEquals("a#2", (events.last() as RouteEvent.Done).target.providerId)
    }

    @Test fun pinnedModelUsesNextKeyWhenLimited() = runTest {
        val a1 = tgt("a", "m", 1, "k1")
        val a2 = tgt("a#2", "m", 1, "k2")
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429) }
            else flow<String> { emit("ok") }
        }
        val events = Router(tr, clock = { 0L }).stream(req(ChatMode.PINNED, "a/m", a1, a2)).toList()
        assertEquals("a#2", (events.last() as RouteEvent.Done).target.providerId)
    }

    @Test fun pinnedModelFailsWhenEveryKeyIsLimited() = runTest {
        val a1 = tgt("a", "m", 1, "k1")
        val a2 = tgt("a#2", "m", 1, "k2")
        val tr = FakeTransport { _, _, _ -> flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429) } }
        val events = Router(tr, clock = { 0L }).stream(req(ChatMode.PINNED, "a/m", a1, a2)).toList()
        assertTrue(events.last() is RouteEvent.Failed)
        assertEquals(2, tr.calls.size)
    }
}
