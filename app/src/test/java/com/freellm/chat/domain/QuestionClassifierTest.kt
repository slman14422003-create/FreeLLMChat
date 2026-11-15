package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionClassifierTest {
    @Test fun codeQuestionIsCode() {
        val c = QuestionClassifier.classify("\u0627\u0643\u062A\u0628 \u062F\u0627\u0644\u0629 \u0628\u0627\u064A\u062B\u0648\u0646 \u062A\u0639\u0643\u0633 \u0627\u0644\u0646\u0635")
        assertEquals(QType.CODE, c.type)
        assertTrue("code" in c.caps)
        assertTrue(c.arabic)
    }

    @Test fun shortGreetingIsEasy() {
        val c = QuestionClassifier.classify("\u0645\u0631\u062D\u0628\u0627 \u0643\u064A\u0641 \u062D\u0627\u0644\u0643")
        assertEquals(QType.SHORT, c.type)
        assertEquals(Difficulty.EASY, c.difficulty)
        assertTrue("fast" in c.caps)
    }

    @Test fun reasoningQuestionIsHard() {
        val c = QuestionClassifier.classify("Prove step by step that the square root of 2 is irrational")
        assertEquals(QType.COMPLEX, c.type)
        assertEquals(Difficulty.HARD, c.difficulty)
        assertTrue("reasoning" in c.caps)
        assertFalse(c.arabic)
    }

    @Test fun freshnessIsDetected() {
        assertTrue(QuestionClassifier.classify("what is the latest Kotlin version").needsFresh)
        assertTrue(QuestionClassifier.classify("\u0645\u0627 \u0647\u0648 \u0633\u0639\u0631 \u0627\u0644\u0630\u0647\u0628 \u0627\u0644\u064A\u0648\u0645").needsFresh)
        assertFalse(QuestionClassifier.classify("what is a monad").needsFresh)
    }

    @Test fun longMediumTextIsAmbiguous() {
        val t = "I have been thinking about how people organise their weekly schedule and wonder whether a different approach could help me"
        assertTrue(QuestionClassifier.classify(t).ambiguous)
    }

    @Test fun arabicRatioHandlesMixedAndEmpty() {
        assertEquals(0.0, QuestionClassifier.arabicRatio("123 !!"), 0.0)
        assertTrue(QuestionClassifier.arabicRatio("hello \u0645\u0631\u062D\u0628\u0627") in 0.3..0.7)
    }

    @Test fun ambiguityReplyParsing() {
        assertEquals(QType.CODE, AmbiguityResolver.parse(" Code."))
        assertEquals(QType.COMPLEX, AmbiguityResolver.parse("reasoning"))
        assertNull(AmbiguityResolver.parse("banana"))
    }

    @Test fun capsFallBackToModesWhenCatalogHasNone() {
        val m = ModelDef(id = "x", provider = "p", displayName = "x", priority = 1, modes = listOf("fast", "code"), reasoning = true, contextWindow = 300_000)
        assertEquals(setOf("code", "fast", "reasoning", "long_context"), ModelCaps.of(m).toSet())
        assertEquals(listOf("arabic"), ModelCaps.of(m.copy(caps = listOf("arabic"))))
    }

    @Test fun routerPrefersModelMatchingCapsOverPriority() {
        fun t(id: String, p: Int, caps: List<String>) = Target(
            "p$id", "p$id", "https://example.com", emptyMap(), "k",
            ModelDef(id = id, provider = "p$id", displayName = id, priority = p, caps = caps)
        )
        val a = t("a", 1, listOf("fast"))
        val b = t("b", 2, listOf("arabic", "reasoning"))
        val r = Router(FakeTransport { _, _, _ -> flow { emit("ok") } }, clock = { 0L })
        fun req(want: List<String>) = RouteRequest(listOf(ChatMsg("user", "x")), ChatMode.AUTO, null, listOf(a, b), wantCaps = want)
        assertEquals("a", r.order(req(emptyList())).first().model.id)
        assertEquals("b", r.order(req(listOf("arabic", "reasoning"))).first().model.id)
    }

    @Test fun resolverUsesLocalHeuristicsOnly() {
        assertEquals(QType.CODE, AmbiguityResolver.heuristic("fix this {broken} build"))
        assertEquals(QType.GENERAL, AmbiguityResolver.heuristic("tell me something about your day and how things are going lately in general terms"))
    }
}
