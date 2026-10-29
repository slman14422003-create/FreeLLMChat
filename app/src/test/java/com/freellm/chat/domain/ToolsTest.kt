package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolProtocolTest {
    @Test fun parsesJsonCall() {
        val raw = "Let me check.\n<tool_call>{\"name\":\"calculator\",\"arguments\":{\"expression\":\"2+2\"}}</tool_call>"
        val p = ToolProtocol.parse(raw)
        assertEquals(1, p.calls.size)
        assertEquals("calculator", p.calls[0].name)
        assertEquals("2+2", p.calls[0].str("expression"))
        assertEquals("Let me check.", p.visible)
    }

    @Test fun parsesBodyCall() {
        val raw = "<tool_call name=\"project_write\" path=\"src/a.py\">\nprint(1)\n</tool_call>"
        val p = ToolProtocol.parse(raw)
        assertEquals("project_write", p.calls[0].name)
        assertEquals("src/a.py", p.calls[0].str("path"))
        assertEquals("print(1)", p.calls[0].args["content"])
    }

    @Test fun parsesSeveralCallsAndCutsTrailingText() {
        val raw = "<tool_call>{\"name\":\"a\",\"arguments\":{}}</tool_call><tool_call>{\"name\":\"b\",\"arguments\":{\"x\":1}}</tool_call>invented result"
        val p = ToolProtocol.parse(raw)
        assertEquals(listOf("a", "b"), p.calls.map { it.name })
        assertEquals("1", p.calls[1].str("x"))
        assertFalse(p.cleanRaw.contains("invented"))
    }

    @Test fun unreadableCallIsCounted() {
        val p = ToolProtocol.parse("<tool_call>not json</tool_call>")
        assertTrue(p.calls.isEmpty())
        assertEquals(1, p.bad)
    }

    @Test fun noCallMeansNoParse() {
        val p = ToolProtocol.parse("plain answer")
        assertTrue(p.calls.isEmpty())
        assertEquals(0, p.bad)
    }

    @Test fun partialTagIsWithheld() {
        assertEquals(6, ToolProtocol.visibleLength("Hello <tool_c", false))
        assertEquals(13, ToolProtocol.visibleLength("Hello <tool_c", true))
    }

    @Test fun danglingFenceBeforeCallIsDropped() {
        val raw = "Text\n```json\n<tool_call>{\"name\":\"x\"}</tool_call>"
        assertEquals("Text", ToolProtocol.visible(raw))
    }

    @Test fun longResultIsTruncated() {
        val r = ToolProtocol.result("t", ToolResult("a".repeat(ToolProtocol.MAX_RESULT_CHARS + 500)))
        assertTrue(r.contains("[truncated]"))
        assertTrue(r.startsWith("<tool_result name=\"t\" ok=\"true\">"))
    }
}

class ToolPolicyTest {
    private val all = ToolCatalog.ids().toSet()

    private fun pick(text: String, mode: String = ToolPolicy.MODE_SMART, runner: Boolean = false, ws: Boolean = false) =
        ToolPolicy.select(text, all, mode, runner, ws).map { it.id }

    @Test fun mathOffersCalculator() {
        assertTrue("calculator" in pick("احسب 15 * 3"))
        assertTrue("calculator" in pick("what is 12 + 30"))
    }

    @Test fun smallTalkOffersNothing() {
        assertTrue(pick("hello how are you").isEmpty())
    }

    @Test fun projectRequestOffersProjectTools() {
        val p = pick("create a website project for my shop")
        assertTrue("project_write" in p)
        assertTrue("project_template" in p)
    }

    @Test fun runnerOnlyWhenConfigured() {
        assertFalse("run_code" in pick("write code", ToolPolicy.MODE_ALWAYS, runner = false))
        assertTrue("run_code" in pick("write code", ToolPolicy.MODE_ALWAYS, runner = true))
    }

    @Test fun disabledToolsAreNeverOffered() {
        val r = ToolPolicy.select("احسب 2+2", all - "calculator", ToolPolicy.MODE_SMART, false, false)
        assertFalse(r.any { it.id == "calculator" })
    }

    @Test fun dateQuestionOffersDatetime() {
        assertTrue("datetime" in pick("كم باقي على رمضان"))
    }
}

class ProjectTemplatesTest {
    @Test fun everyTemplateHasSafeUniquePaths() {
        for (t in ProjectTemplates.types) {
            val files = ProjectTemplates.files(t, "Demo App")
            assertNotNull(t, files)
            assertTrue(t, files!!.isNotEmpty())
            files.forEach { assertNotNull(t + ":" + it.path, CodeFiles.safePath(it.path)) }
            assertEquals(t, files.size, files.map { it.path }.toSet().size)
            assertFalse(t, files.any { it.content.contains("{{name}}") })
        }
    }

    @Test fun unknownTypeIsNull() {
        assertNull(ProjectTemplates.files("cobol", "x"))
    }

    @Test fun androidTemplateHasActivityAndManifest() {
        val paths = ProjectTemplates.files("android-compose", "Demo")!!.map { it.path }
        assertTrue(paths.any { it.endsWith("MainActivity.kt") })
        assertTrue("app/src/main/AndroidManifest.xml" in paths)
    }

    @Test fun nameIsSanitized() {
        assertEquals("MyApp", ProjectTemplates.safeName("***"))
    }
}

class ToolRunnerTest {
    private fun tgt() = Target(
        providerId = "p", providerName = "p", baseUrl = "https://example.com", headers = emptyMap(), apiKey = "k",
        model = ModelDef(id = "m", provider = "p", displayName = "m", priority = 1)
    )

    private fun req() = RouteRequest(
        messages = listOf(ChatMsg("user", "what is 2+2")),
        mode = ChatMode.AUTO,
        pinnedKey = null,
        candidates = listOf(tgt())
    )

    @Test fun runsToolThenAnswers() = runTest {
        val tr = FakeTransport { _, msgs, n ->
            if (n == 1) flow<String> { emit("Checking. <tool_call>{\"name\":\"calculator\","); emit("\"arguments\":{\"expression\":\"2+2\"}}</tool_call>") }
            else flow<String> {
                assertTrue(msgs.last().content.contains("<tool_result name=\"calculator\""))
                emit("The answer is 4")
            }
        }
        val exec = object : ToolExecutor {
            override suspend fun execute(call: ToolCall, ctx: ToolContext) = ToolResult("4")
        }
        val steps = ArrayList<ToolStep>()
        val events = ToolRunner(Router(tr, clock = { 0L }), exec).stream(req(), ToolContext(1), {}, { steps.add(it) }).toList()
        val text = events.filterIsInstance<RouteEvent.Delta>().joinToString("") { it.text }
        assertEquals("Checking.\n\nThe answer is 4", text.replace("Checking. ", "Checking."))
        assertEquals(1, steps.size)
        assertEquals("calculator", steps[0].name)
        assertTrue(events.last() is RouteEvent.Done)
        assertEquals(2, tr.calls.size)
    }

    @Test fun answerWithoutToolPassesThrough() = runTest {
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("Just "); emit("text") } }
        val exec = object : ToolExecutor {
            override suspend fun execute(call: ToolCall, ctx: ToolContext) = ToolResult("x")
        }
        val events = ToolRunner(Router(tr, clock = { 0L }), exec).stream(req(), ToolContext(1), {}, {}).toList()
        assertEquals("Just text", events.filterIsInstance<RouteEvent.Delta>().joinToString("") { it.text })
        assertEquals(1, tr.calls.size)
    }
}
