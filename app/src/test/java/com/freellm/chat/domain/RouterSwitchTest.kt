package com.freellm.chat.domain

import com.freellm.chat.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterSwitchTest {
    private fun tgt(provider: String, priority: Int) = Target(
        providerId = provider, providerName = provider, baseUrl = "https://example.com", headers = emptyMap(), apiKey = "k",
        model = ModelDef(id = "m-$provider", provider = provider, displayName = provider, priority = priority, contextWindow = 100_000)
    )

    private fun req(vararg t: Target) = RouteRequest(
        messages = listOf(ChatMsg("user", "hi")), mode = ChatMode.AUTO, pinnedKey = null, candidates = t.toList()
    )

    private fun textOf(events: List<RouteEvent>) = events.filterIsInstance<RouteEvent.Delta>().joinToString("") { it.text }

    private val partial = "The quick brown fox jumps over the lazy dog and keeps running far away"

    @Test fun overlappingContinuationIsNotDuplicated() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { emit(partial); throw LlmException(ErrorKind.INTERRUPTED) }
            else flow<String> { emit("keeps running far away and then it finally stops here, ok") }
        }
        val events = Router(tr, clock = { 0L }).stream(req(a, b)).toList()
        assertEquals(partial + " and then it finally stops here, ok", textOf(events))
        assertTrue(events.any { it is RouteEvent.Switched })
        assertFalse(events.any { it is RouteEvent.Restart })
        assertEquals(b.key, (events.last() as RouteEvent.Done).target.key)
    }

    @Test fun stalledStreamHandsTheReplyToTheNextModel() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> {
                emit(partial)
                delay(Long.MAX_VALUE)
            } else flow<String> { emit(" and then the second model finishes the sentence properly now") }
        }
        val router = Router(tr, RouterConfig(idleTimeoutMs = 1000, firstTokenTimeoutMs = 1000), clock = { 0L })
        val events = router.stream(req(a, b)).toList()
        assertEquals(partial + " and then the second model finishes the sentence properly now", textOf(events))
        assertTrue(events.any { it is RouteEvent.Switched && it.target.key == b.key })
        assertEquals(2, tr.calls.size)
        val second = tr.calls[1].second
        assertEquals("assistant", second[second.size - 2].role)
        assertEquals(partial, second[second.size - 2].content)
    }

    @Test fun veryShortPartialRestartsInsteadOfContinuing() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { emit("Hi there"); throw LlmException(ErrorKind.INTERRUPTED) }
            else flow<String> { emit("A complete answer from the second model.") }
        }
        val events = Router(tr, clock = { 0L }).stream(req(a, b)).toList()
        val restartAt = events.indexOfFirst { it is RouteEvent.Restart }
        assertTrue(restartAt >= 0)
        val after = events.drop(restartAt).filterIsInstance<RouteEvent.Delta>().joinToString("") { it.text }
        assertEquals("A complete answer from the second model.", after)
        assertEquals("user", tr.calls[1].second.last().role)
        assertFalse(tr.calls[1].second.last().content.contains("أكمل"))
    }

    @Test fun failureEventCarriesStructuredReasons() = runTest {
        val a = tgt("a", 1)
        val tr = FakeTransport { _, _, _ -> flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429, 90) } }
        val failed = Router(tr, clock = { 0L }).stream(req(a)).toList().last() as RouteEvent.Failed
        assertEquals(FailKind.ALL_FAILED, failed.kind)
        assertEquals(listOf(FailReason("a", ReasonKind.ERROR, ErrorKind.RATE_LIMIT)), failed.reasons)
        assertEquals(90L, failed.retryInSec)
    }

    @Test fun noProviderAndPinnedMissingAreStructured() = runTest {
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("x") } }
        val none = Router(tr, clock = { 0L }).stream(req()).toList().last() as RouteEvent.Failed
        assertEquals(FailKind.NO_PROVIDER, none.kind)
        val pinned = Router(tr, clock = { 0L }).stream(
            RouteRequest(listOf(ChatMsg("user", "hi")), ChatMode.PINNED, "zzz", listOf(tgt("a", 1)))
        ).toList().last() as RouteEvent.Failed
        assertEquals(FailKind.PINNED_MISSING, pinned.kind)
        assertTrue(pinned.pinned)
    }

    @Test fun errorLabelsResolveThroughResourcesAndKeepOldEntries() {
        assertEquals("x", labelOf("RATE_LIMIT") { "x" })
        assertEquals("old arabic text", labelOf("old arabic text") { "x" })
        assertEquals(ErrorKind.entries.size, ErrorKind.entries.map { it.labelRes() }.toSet().size)
        assertEquals(
            R.string.err_msg_auth_401,
            LlmException(ErrorKind.AUTH, 401).messageRes()
        )
        assertEquals("msg:boom", LlmException(ErrorKind.SERVER, 500, null, "boom").localized { _, d -> "msg:$d" })
    }
}
