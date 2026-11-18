package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffTest {
    private fun tgt(p: String, pr: Int) = Target(
        providerId = p, providerName = p, baseUrl = "https://example.com", headers = emptyMap(), apiKey = "k",
        model = ModelDef(id = "m-$p", provider = p, displayName = p, priority = pr, contextWindow = 100_000)
    )

    private fun req(vararg t: Target) = RouteRequest(
        messages = listOf(ChatMsg("system", "sys"), ChatMsg("user", "what is 2+2")),
        mode = ChatMode.AUTO,
        pinnedKey = null,
        candidates = t.toList()
    )

    private val head = "<think>The sum needs the calculator.</think> Checking. "
    private val block = "<tool_call>{\"name\":\"calculator\",\"arguments\":{\"expression\":\"2+2\"}}</tool_call>"

    @Test fun brokenToolBlockIsNotHandedOver() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> {
                emit(head + "<tool_call>{\"name\":\"calc")
                throw LlmException(ErrorKind.RATE_LIMIT, 429)
            } else flow<String> { emit(block) }
        }
        val events = Router(tr, clock = { 0L }).stream(req(a, b)).toList()
        val second = tr.calls[1].second
        assertFalse(second[second.size - 2].content.contains("<tool_call"))
        assertTrue(second.last().content.contains("tool block"))
        assertTrue(events.last() is RouteEvent.Done)
    }

    @Test fun toolRoundSurvivesAModelSwitch() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, msgs, n ->
            when {
                t.providerId == "a" -> flow<String> {
                    emit(head + "<tool_call>{\"name\":\"calc")
                    throw LlmException(ErrorKind.RATE_LIMIT, 429)
                }
                n == 2 -> flow<String> { emit(block) }
                else -> flow<String> {
                    assertTrue(msgs.last().content.contains("<tool_result name=\"calculator\""))
                    assertTrue(msgs.last().content.contains("another model"))
                    emit("The answer is 4")
                }
            }
        }
        val exec = object : ToolExecutor {
            override suspend fun execute(call: ToolCall, ctx: ToolContext) = ToolResult("4")
        }
        val steps = ArrayList<ToolStep>()
        val events = ToolRunner(Router(tr, clock = { 0L }), exec).stream(req(a, b), ToolContext(1), {}, { steps.add(it) }).toList()
        val text = events.filterIsInstance<RouteEvent.Delta>().joinToString("") { it.text }
        assertEquals(1, Regex("<think>").findAll(text).count())
        assertTrue(text.endsWith("The answer is 4"))
        assertEquals(1, steps.size)
        assertEquals(3, tr.calls.size)
        assertTrue(events.last() is RouteEvent.Done)
    }

    @Test fun trimmerKeepsTheQuestionAndShortensOldResults() {
        val big = "<tool_result name=\"web_search\" ok=\"true\">\n" + "x".repeat(6000) + "\n</tool_result>"
        val msgs = listOf(
            ChatMsg("system", "sys"),
            ChatMsg("user", "original question"),
            ChatMsg("assistant", "call1"),
            ChatMsg("user", big),
            ChatMsg("assistant", "call2"),
            ChatMsg("user", big)
        )
        val out = ContextTrimmer.trim(msgs, window = 1500, reserve = 300, keepLast = 1)
        assertTrue(out.any { it.content == "original question" })
        assertEquals(msgs.last(), out.last())
    }

    @Test fun cutRemovesOnlyUnfinishedToolBlocks() {
        assertEquals("hello ", cutDanglingTool("hello <tool_call>{\"na"))
        assertEquals("hello ", cutDanglingTool("hello <tool_c"))
        val done = "hello <tool_call>{}</tool_call>"
        assertEquals(done, cutDanglingTool(done))
        assertEquals("plain text", cutDanglingTool("plain text"))
    }

    @Test fun quotaOnForbiddenIsSwitchableNotAuth() {
        assertEquals(ErrorKind.RATE_LIMIT, ProviderErrors.kind(403, "{\"error\":{\"message\":\"You exceeded your current quota\"}}"))
        assertEquals(ErrorKind.RATE_LIMIT, ProviderErrors.kind(400, "RESOURCE_EXHAUSTED"))
        assertEquals(ErrorKind.AUTH, ProviderErrors.kind(403, "invalid api key"))
        assertEquals(ErrorKind.RATE_LIMIT, ProviderErrors.streamKind("insufficient_quota"))
    }
}

class ExtraToolsTest {
    private fun call(name: String, vararg a: Pair<String, String>) = ToolCall(name, mapOf(*a))

    private fun run(c: ToolCall): ToolResult = ExtraTools.run(c)!!

    @Test fun sha256IsExact() {
        val r = run(call("encode", "op" to "sha256", "text" to "abc"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", r.text)
    }

    @Test fun base64RoundTrip() {
        val enc = run(call("encode", "op" to "base64", "text" to "hello")).text
        assertEquals("aGVsbG8=", enc)
        assertEquals("hello", run(call("encode", "op" to "base64d", "text" to enc)).text)
    }

    @Test fun invalidBase64Fails() {
        assertFalse(run(call("encode", "op" to "base64d", "text" to "***")).ok)
    }

    @Test fun randomIntStaysInRange() {
        repeat(50) {
            val v = run(call("random", "op" to "int", "min" to "3", "max" to "5")).text.toInt()
            assertTrue(v in 3..5)
        }
    }

    @Test fun regexListsMatchesAndGroups() {
        val r = run(call("regex_test", "pattern" to "(\\d+)-(\\d+)", "text" to "a 12-34 b"))
        assertTrue(r.text.contains("12-34"))
        assertTrue(r.text.contains("12|34"))
    }

    @Test fun invalidRegexFails() {
        assertFalse(run(call("regex_test", "pattern" to "(", "text" to "x")).ok)
    }

    @Test fun jsonQueryWalksAPath() {
        val r = run(call("json_query", "json" to "{\"a\":{\"b\":[1,{\"c\":\"hi\"}]}}", "path" to "a.b[1].c"))
        assertEquals("hi", r.text)
    }

    @Test fun jsonQueryDescribesTheRoot() {
        assertTrue(run(call("json_query", "json" to "{\"a\":1,\"b\":2}")).text.contains("2 keys"))
    }

    @Test fun jsonMissingPathFails() {
        assertFalse(run(call("json_query", "json" to "{\"a\":1}", "path" to "z")).ok)
    }

    @Test fun textStatsCountsWords() {
        assertTrue(run(call("text_stats", "text" to "one two three. four!")).text.contains("words: 4"))
    }

    @Test fun unknownNameIsNull() {
        assertNull(ExtraTools.run(call("nope")))
    }

    @Test fun everyExtraToolIsInTheCatalog() {
        assertTrue(ExtraTools.ids.all { ToolCatalog.spec(it) != null })
        assertTrue(ToolCatalog.spec("weather") != null)
    }

    @Test fun trivialChatSkipsToolsButSignalsKeepThem() {
        assertTrue(ToolPolicy.trivial("hello"))
        assertFalse(ToolPolicy.trivial("weather in Riyadh"))
        assertFalse(ToolPolicy.trivial("2+2"))
        assertFalse(ToolPolicy.trivial("random number"))
    }

    @Test fun alwaysModeKeepsProjectToolsOutOfPlainChat() {
        val all = ToolCatalog.ids().toSet()
        val plain = ToolPolicy.select("tell me a story about a fox", all, ToolPolicy.MODE_ALWAYS, false, false).map { it.id }
        assertFalse("project_write" in plain)
        assertTrue("weather" in plain)
        val build = ToolPolicy.select("create a website project for my shop", all, ToolPolicy.MODE_ALWAYS, false, false).map { it.id }
        assertTrue("project_write" in build)
    }
}
