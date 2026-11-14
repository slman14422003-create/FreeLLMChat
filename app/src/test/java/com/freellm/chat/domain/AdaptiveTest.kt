package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {
    @Test
    fun classifiesCode() {
        assertEquals(TaskKind.CODE, TaskClassifier.classify("اكتب لي دالة بايثون لترتيب قائمة"))
        assertEquals(TaskKind.CODE, TaskClassifier.classify("fix this bug:\n```kotlin\nval x = 1\n```"))
    }

    @Test
    fun classifiesReasoningWritingAndQuick() {
        assertEquals(TaskKind.REASON, TaskClassifier.classify("احسب 12 * 7 خطوة بخطوة"))
        assertEquals(TaskKind.WRITE, TaskClassifier.classify("لخص هذا النص لي"))
        assertEquals(TaskKind.QUICK, TaskClassifier.classify("مرحبا كيف الحال"))
    }

    @Test
    fun overlapRemovesRepeatedTail() {
        assertEquals(" and the rest", Overlap.strip("this is the answer so far", "answer so far and the rest"))
        assertEquals("brand new text", Overlap.strip("something else", "brand new text"))
    }

    @Test
    fun tokenSaverShrinksOldMessagesOnly() {
        val big = "x".repeat(2000)
        val msgs = (1..12).map { ChatMsg(if (it % 2 == 1) "user" else "assistant", big) }
        val c = TokenSaver.compact(msgs, TaskKind.QUICK)
        assertTrue(c.savedTokens > 0)
        assertEquals(big, c.messages.last().content)
        assertTrue(c.messages.first().content.length < big.length)
    }

    @Test
    fun statsPenalizeFailingModel() {
        val s = ModelStats()
        repeat(3) { s.onFail("a/m") }
        assertEquals(1, s.failTier("a/m"))
        assertEquals(0, s.failTier("b/m"))
    }
}
