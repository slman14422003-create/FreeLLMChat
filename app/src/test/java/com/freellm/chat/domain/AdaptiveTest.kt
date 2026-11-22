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
    fun overlapIgnoresMarkdownAndLongRestarts() {
        val tail = "- CSF analysis: finds oligoclonal bands in the spinal fluid"
        val next = "- **CSF analysis:** finds oligoclonal bands in the spinal fluid and raised IgG."
        assertEquals(" and raised IgG.", Overlap.strip(tail, next))
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

    @Test
    fun latencyBucketThresholds() {
        val s = ModelStats()
        s.onFirstToken("a/m", 699)
        s.onFirstToken("b/m", 700)
        s.onFirstToken("c/m", 1499)
        s.onFirstToken("d/m", 1500)
        s.onFirstToken("e/m", 2999)
        s.onFirstToken("f/m", 3000)
        s.onFirstToken("g/m", 5999)
        s.onFirstToken("h/m", 6000)
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4), listOf("a/m", "b/m", "c/m", "d/m", "e/m", "f/m", "g/m", "h/m").map { s.latencyBucket(it) })
    }

    @Test
    fun latencyBucketWithoutSampleIsTwo() {
        assertEquals(2, ModelStats().latencyBucket("x/m"))
    }
}
