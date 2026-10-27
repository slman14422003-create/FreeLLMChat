package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBudgetTest {
    private fun tgt(window: Int) = Target(
        providerId = "p", providerName = "p", baseUrl = "https://example.com", headers = emptyMap(), apiKey = "k",
        model = ModelDef(id = "m$window", provider = "p", displayName = "m", priority = 1, contextWindow = window)
    )

    @Test fun quickIsSmallerThanCode() {
        val q = MaxTokensPlanner.plan(TaskKind.QUICK, "hi", "default", false)
        val c = MaxTokensPlanner.plan(TaskKind.CODE, "write a function", "default", false)
        assertTrue(q < c)
    }

    @Test fun conciseStyleShrinksAndDetailedGrows() {
        val base = MaxTokensPlanner.plan(TaskKind.GENERAL, "tell me about whales and their habits please", "default", false)
        assertTrue(MaxTokensPlanner.plan(TaskKind.GENERAL, "tell me about whales and their habits please", "concise", false) < base)
        assertTrue(MaxTokensPlanner.plan(TaskKind.GENERAL, "tell me about whales and their habits please", "detailed", false) > base)
    }

    @Test fun explicitLongRequestRaisesLimit() {
        assertTrue(MaxTokensPlanner.plan(TaskKind.QUICK, "اشرح لي بالتفصيل", "default", false) >= 3500)
    }

    @Test fun planIsAlwaysWithinBounds() {
        TaskKind.values().forEach { k ->
            listOf("default", "concise", "detailed", "teacher").forEach { s ->
                val v = MaxTokensPlanner.plan(k, "x", s, true)
                assertTrue(v in MaxTokensPlanner.MIN..MaxTokensPlanner.MAX)
            }
        }
    }

    @Test fun fitsRespectsMargin() {
        assertTrue(RequestFitter.fits(tgt(10_000), 5_000))
        assertFalse(RequestFitter.fits(tgt(4_000), 5_000))
        assertFalse(RequestFitter.fits(tgt(5_100), 5_000))
    }

    @Test fun neededAddsReplyReserve() {
        val msgs = listOf(ChatMsg("user", "a".repeat(350)))
        assertEquals(TokenEstimator.messages(msgs) + 500, RequestFitter.needed(msgs, 500))
        assertEquals(TokenEstimator.messages(msgs) + RequestFitter.DEFAULT_REPLY, RequestFitter.needed(msgs, null))
    }

    @Test fun clampKeepsTheQuestionAtTheEnd() {
        val big = "x".repeat(40_000) + "\n\nWHAT IS THE ANSWER?"
        val out = RequestFitter.clampLast(listOf(ChatMsg("system", "s"), ChatMsg("user", big)), 3_000)
        val text = out.last().content
        assertTrue(text.endsWith("WHAT IS THE ANSWER?"))
        assertTrue(text.length < big.length)
        assertTrue(ContextTrimmer.cost(out.last()) <= 3_000)
    }

    @Test fun clampLeavesSmallRequestsUntouched() {
        val msgs = listOf(ChatMsg("user", "hello"))
        assertEquals(msgs, RequestFitter.clampLast(msgs, 3_000))
    }

    @Test fun arabicQuestionsGetMoreRoomThanEnglish() {
        val ar = MaxTokensPlanner.plan(TaskKind.GENERAL, "ما هي أسباب التصلب اللويحي", "default", false)
        val en = MaxTokensPlanner.plan(TaskKind.GENERAL, "what causes multiple sclerosis", "default", false)
        assertTrue(ar > en)
    }

    @Test fun referenceRequestsGetLongReplies() {
        assertTrue(MaxTokensPlanner.plan(TaskKind.QUICK, "ابحث عن مراجع في التصلب اللويحي", "default", false) >= 3500)
    }

    @Test fun sourcesRaiseTheFloor() {
        assertTrue(MaxTokensPlanner.plan(TaskKind.QUICK, "hi", "concise", false, true) >= 2500)
    }
}
