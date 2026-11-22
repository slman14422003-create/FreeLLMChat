package com.freellm.chat.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Round12Test {
    private fun tgt(provider: String, priority: Int) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = "m-$provider", provider = provider, displayName = "m-$provider", priority = priority)
    )

    @Test fun attemptCallbackReportsEachModelTried() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429, 30) }
            else flow<String> { emit("ok") }
        }
        val seen = ArrayList<Pair<String, Int>>()
        val req = RouteRequest(
            messages = listOf(ChatMsg("user", "hi")),
            mode = ChatMode.AUTO,
            pinnedKey = null,
            candidates = listOf(a, b),
            onAttempt = { t, n, _ -> seen.add(t.providerId to n) }
        )
        val events = Router(tr, clock = { 0L }).stream(req).toList()
        assertTrue(events.last() is RouteEvent.Done)
        assertEquals(listOf("a" to 1, "b" to 2), seen)
    }

    @Test fun slowToolIsCutOffAndReportedAsFailed() = runTest {
        val t = tgt("p", 1)
        val tr = FakeTransport { _, _, n ->
            if (n == 1) flow<String> { emit("<tool_call>{\"name\":\"calculator\",\"arguments\":{\"expression\":\"2+2\"}}</tool_call>") }
            else flow<String> { emit("done") }
        }
        val exec = object : ToolExecutor {
            override suspend fun execute(call: ToolCall, ctx: ToolContext): ToolResult {
                delay(10 * 60_000L)
                return ToolResult("never")
            }
        }
        val req = RouteRequest(
            messages = listOf(ChatMsg("user", "what is 2+2")),
            mode = ChatMode.AUTO,
            pinnedKey = null,
            candidates = listOf(t)
        )
        val steps = ArrayList<ToolStep>()
        val events = ToolRunner(Router(tr, clock = { 0L }), exec).stream(req, ToolContext(1), {}, { steps.add(it) }).toList()
        assertEquals(1, steps.size)
        assertFalse(steps[0].ok)
        assertTrue(steps[0].summary.contains("timed out"))
        assertTrue(events.last() is RouteEvent.Done)
    }

    @Test fun toolTimeoutsAreOrdered() {
        assertTrue(ToolRunner.TOOL_TIMEOUT_MS < ToolRunner.FETCH_TIMEOUT_MS)
        assertTrue(ToolRunner.RUN_TIMEOUT_MS >= ToolRunner.TOOL_TIMEOUT_MS)
    }
}
