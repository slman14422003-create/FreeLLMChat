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
        val keep = b.learn("question number alpha", "answer alpha")!!
        b.feedback(keep.id, true)
        b.learn("question number beta", "answer beta")
        b.learn("question number gamma", "answer gamma")
        b.learn("question number delta", "answer delta")
        assertEquals(3, b.size())
        assertTrue(b.all().any { it.id == keep.id })
    }
}

class LocalBrainEditTest {
    @org.junit.Test
    fun updateAndRemove() {
        val b = LocalBrain()
        val e = b.learn("what is the capital of France", "Paris")!!
        org.junit.Assert.assertTrue(b.update(e.id, "capital of France?", "Paris, France"))
        org.junit.Assert.assertEquals("Paris, France", b.all().first().answer)
        org.junit.Assert.assertTrue(b.remove(e.id))
        org.junit.Assert.assertEquals(0, b.size())
    }
}

class LocalBrainRound10Test {
    @Test fun expiredEntriesAreSkipped() {
        val b = LocalBrain()
        b.learn("what is the capital of France", "Paris", now = 0L)
        org.junit.Assert.assertEquals(1, b.search("what is the capital of France", now = 1000L).size)
        org.junit.Assert.assertTrue(b.search("what is the capital of France", now = LocalBrain.DEFAULT_TTL_MS + 1).isEmpty())
    }

    @Test fun contextDependentQuestionsAreNotLearned() {
        val b = LocalBrain()
        org.junit.Assert.assertNull(b.learn("and what about that one", "some answer"))
        org.junit.Assert.assertNull(b.learn("explain it again please", "some answer"))
        org.junit.Assert.assertEquals(0, b.size())
    }

    @Test fun strictMatchNeedsSameNumbers() {
        val b = LocalBrain()
        b.learn("how many days in a 30 day month plan", "a long enough answer")
        org.junit.Assert.assertNull(b.bestStrict("how many days in a 31 day month plan", 0.1))
        org.junit.Assert.assertNotNull(b.bestStrict("how many days in a 30 day month plan", 0.1))
    }

    @Test fun strictMatchNeedsSameQuotedTokens() {
        org.junit.Assert.assertTrue(LocalBrain.coversTokens("meaning of \"carpe diem\"", "meaning of carpe diem phrase"))
        org.junit.Assert.assertFalse(LocalBrain.coversTokens("meaning of \"memento mori\"", "meaning of carpe diem phrase"))
    }

    @Test fun yearsFromCurrentOnAreTimeSensitive() {
        org.junit.Assert.assertTrue(LocalBrain.isTimeSensitive("best phones of 2031", 2026))
        org.junit.Assert.assertTrue(LocalBrain.isTimeSensitive("what is new in 2026", 2026))
        org.junit.Assert.assertFalse(LocalBrain.isTimeSensitive("history of rome in 2019", 2026))
        org.junit.Assert.assertTrue(LocalBrain.isTimeSensitive("latest kotlin release"))
    }

    @Test fun followUpsAreNotLearnedOrServed() {
        org.junit.Assert.assertTrue(BrainPolicy.skipMemory("and why?", 3))
        org.junit.Assert.assertTrue(BrainPolicy.skipMemory("please explain that in more detail with a full example for beginners", 3))
        org.junit.Assert.assertFalse(BrainPolicy.skipMemory("explain how photosynthesis works in green plants during the day", 3))
        org.junit.Assert.assertFalse(BrainPolicy.skipMemory("and why?", 1))
    }

    @Test fun poorOrFreshAnswersAreNotLearned() {
        org.junit.Assert.assertFalse(BrainPolicy.canLearn("what is a monad in haskell", "x", 1, false))
        org.junit.Assert.assertFalse(BrainPolicy.canLearn("what is a monad in haskell", "A monad is a pattern.", 1, true))
        org.junit.Assert.assertTrue(BrainPolicy.canLearn("what is a monad in haskell", "A monad is a pattern.", 1, false))
    }
}
