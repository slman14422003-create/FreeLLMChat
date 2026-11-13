package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPlannerTest {
    private fun convo(n: Int): List<ChatMsg> =
        (0 until n).map { ChatMsg(if (it % 2 == 0) "user" else "assistant", "m$it") }

    @Test fun shortConversationIsNotSplit() {
        val (older, recent) = ContextPlanner.split(convo(6), 8) { it.role }
        assertTrue(older.isEmpty())
        assertEquals(6, recent.size)
    }

    @Test fun splitKeepsRecentStartingWithUser() {
        val list = convo(13)
        val (older, recent) = ContextPlanner.split(list, 8) { it.role }
        assertEquals("user", recent.first().role)
        assertEquals(list.size, older.size + recent.size)
        assertTrue(recent.size <= 8)
        assertEquals(list.last(), recent.last())
    }

    @Test fun splitNeverReturnsEmptyRecent() {
        val list = List(12) { ChatMsg("assistant", "a$it") }
        val (_, recent) = ContextPlanner.split(list, 8) { it.role }
        assertTrue(recent.isNotEmpty())
    }

    @Test fun updateTriggersOnCountOrSize() {
        assertFalse(ContextPlanner.shouldUpdate(0, 5000))
        assertFalse(ContextPlanner.shouldUpdate(2, 100))
        assertTrue(ContextPlanner.shouldUpdate(4, 100))
        assertTrue(ContextPlanner.shouldUpdate(1, 2000))
    }

    @Test fun digestStaysWithinLimitAndKeepsNewest() {
        val old = (0 until 40).map { ChatMsg("user", "question number $it about something") }
        val d = ContextPlanner.digest(old, 300)
        assertTrue(d.length <= 300)
        assertTrue(d.contains("39"))
    }

    @Test fun summaryPromptIncludesPreviousAndNew() {
        val p = ContextPlanner.summaryPrompt("old facts", listOf(ChatMsg("user", "hello there")))
        assertTrue(p.contains("old facts"))
        assertTrue(p.contains("User: hello there"))
    }

    @Test fun cleanCapsLength() {
        assertEquals(ContextPlanner.MAX_SUMMARY_CHARS, ContextPlanner.clean("x".repeat(5000)).length)
    }
}
