package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSentencesTest {
    @Test
    fun emitsOnlyCompletedSentences() {
        val r = LiveSentences.take("Hello there. How are", 0, false)
        assertEquals(listOf("Hello there."), r.sentences)
        assertEquals(12, r.next)
    }

    @Test
    fun trailingTerminatorWaitsUntilFinished() {
        val a = LiveSentences.take("One. Two.", 0, false)
        assertEquals(listOf("One."), a.sentences)
        val b = LiveSentences.take("One. Two.", a.next, true)
        assertEquals(listOf("Two."), b.sentences)
        assertEquals(9, b.next)
    }

    @Test
    fun decimalsDoNotSplit() {
        val r = LiveSentences.take("Pi is 3.14 roughly. Next", 0, false)
        assertEquals(listOf("Pi is 3.14 roughly."), r.sentences)
    }

    @Test
    fun arabicQuestionMark() {
        val r = LiveSentences.take("كيف حالك؟ أنا بخير", 0, true)
        assertEquals(listOf("كيف حالك؟", "أنا بخير"), r.sentences)
    }

    @Test
    fun finishedFlushesTail() {
        val r = LiveSentences.take("no punctuation here", 0, true)
        assertEquals(listOf("no punctuation here"), r.sentences)
        assertEquals(19, r.next)
    }
}
