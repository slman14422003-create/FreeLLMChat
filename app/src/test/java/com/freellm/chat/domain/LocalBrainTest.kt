package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBrainTest {
    @Test fun learnsAndFindsSameQuestion() {
        val b = LocalBrain()
        b.learn("ما هي عاصمة فرنسا؟", "عاصمة فرنسا هي باريس.")
        val m = b.best("ما هي عاصمة فرنسا", 0.8)
        assertNotNull(m)
        assertEquals("عاصمة فرنسا هي باريس.", m!!.entry.answer)
    }

    @Test fun unrelatedQuestionDoesNotMatch() {
        val b = LocalBrain()
        b.learn("How do I reverse a string in Python?", "Use s[::-1].")
        assertNull(b.best("اكتب لي قصة عن البحر", 0.4))
    }

    @Test fun duplicateQuestionUpdatesInsteadOfGrowing() {
        val b = LocalBrain()
        b.learn("What is Kotlin?", "A language.")
        b.learn("what is kotlin", "A JVM language.")
        assertEquals(1, b.size())
        assertEquals("A JVM language.", b.all().first().answer)
    }

    @Test fun badFeedbackRemovesEntry() {
        val b = LocalBrain()
        val e = b.learn("What is Kotlin?", "A language.")!!
        b.feedback(e.id, false)
        assertEquals(0, b.size())
    }

    @Test fun timeSensitiveQuestionsAreNotLearned() {
        val b = LocalBrain()
        assertNull(b.learn("ما هو سعر الذهب اليوم", "كذا"))
        assertTrue(b.size() == 0)
    }

    @Test fun capacityEvictsLowestScore() {
        val b = LocalBrain(capacity = 3)
        val keep = b.learn("question number alpha", "a")!!
        b.feedback(keep.id, true)
        b.learn("question number beta", "b")
        b.learn("question number gamma", "c")
        b.learn("question number delta", "d")
        assertEquals(3, b.size())
        assertTrue(b.all().any { it.id == keep.id })
    }
}
