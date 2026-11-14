package com.freellm.chat.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerTest {
    private fun cls(type: QType = QType.GENERAL, fresh: Boolean = false, diff: Difficulty = Difficulty.NORMAL) =
        Classification(type, diff, false, fresh, false)

    private fun bundle(vararg urls: String, text: String = "plain english text") =
        SearchBundle(listOf("q"), urls.map { SourceItem("t", it, text, true) })

    private fun tgt(provider: String, reasoning: Boolean = false) = Target(
        providerId = provider, providerName = provider, baseUrl = "https://x.com", headers = emptyMap(), apiKey = "k",
        model = ModelDef(id = "m-$provider", provider = provider, displayName = provider, priority = 1, reasoning = reasoning)
    )

    private class TempTransport : ChatTransport {
        val seen = ArrayList<Pair<String, Double?>>()
        override fun stream(target: Target, messages: List<ChatMsg>, maxTokens: Int?): Flow<String> =
            stream(target, messages, maxTokens, null)

        override fun stream(target: Target, messages: List<ChatMsg>, maxTokens: Int?, temperature: Double?): Flow<String> {
            seen.add(target.key to temperature)
            return flow { emit("ok") }
        }
    }

    @Test fun noSourcesMeansNoExtraInstructions() {
        assertEquals("", AnswerPolicy.build(AnswerPolicy.spec("hello", "default", null)))
        assertEquals("", AnswerPolicy.build(AnswerPolicy.spec("hello", "default", SearchBundle(emptyList(), emptyList()))))
    }

    @Test fun sourcesAddCitationAndNotFoundRules() {
        val s = AnswerPolicy.build(AnswerPolicy.spec("what is x", "default", bundle("https://a.com", "https://b.com")))
        assertTrue(s.contains(SourcesPrompt.SYSTEM))
        assertTrue(s.contains("[1]"))
        assertTrue(s.contains("لم أجد"))
        assertTrue(s.contains("disagree"))
        assertTrue(s.contains("Sources:"))
        assertFalse(s.contains("official"))
    }

    @Test fun officialSourcesChangeConflictRule() {
        val s = AnswerPolicy.build(AnswerPolicy.spec("q", "default", bundle("https://a.com", "https://health.gov.sa/x")))
        assertTrue(s.contains("official"))
    }

    @Test fun singleSourceSkipsConflictRule() {
        val s = AnswerPolicy.build(AnswerPolicy.spec("q", "default", bundle("https://a.com")))
        assertFalse(s.contains("disagree"))
    }

    @Test fun templateFollowsStyle() {
        val concise = AnswerPolicy.template("concise")
        val detailed = AnswerPolicy.template("detailed")
        val normal = AnswerPolicy.template("default")
        assertTrue(concise.contains("one to three sentences"))
        assertTrue(detailed.contains("structured details"))
        assertTrue(normal.contains("one or two sentences"))
        assertEquals(3, AnswerPolicy.maxSources("concise"))
        assertEquals(4, AnswerPolicy.maxSources("default"))
        assertEquals(5, AnswerPolicy.maxSources("detailed"))
        assertEquals(5, AnswerPolicy.maxSources("teacher"))
    }

    @Test fun languageDirectiveOnlyWhenLanguagesDiffer() {
        val en = bundle("https://a.com", text = "This is plain english text about something")
        val ar = bundle("https://a.com", text = "هذا نص عربي واضح عن موضوع معين وتفاصيله")
        assertTrue(AnswerPolicy.build(AnswerPolicy.spec("ما هو سعر الذهب", "default", en)).contains("Answer in Arabic"))
        assertTrue(AnswerPolicy.build(AnswerPolicy.spec("gold price", "default", ar)).contains("not Arabic"))
        assertFalse(AnswerPolicy.build(AnswerPolicy.spec("gold price", "default", en)).contains("Answer in"))
        assertFalse(AnswerPolicy.build(AnswerPolicy.spec("ما هو سعر الذهب", "default", ar)).contains("Answer in"))
        assertEquals("", AnswerPolicy.language(true, null))
    }

    @Test fun temperatureByKindAndSources() {
        assertEquals(0.9, TemperaturePolicy.pick(TaskKind.WRITE, cls(), false), 0.0)
        assertEquals(0.2, TemperaturePolicy.pick(TaskKind.CODE, cls(QType.CODE), false), 0.0)
        assertEquals(0.2, TemperaturePolicy.pick(TaskKind.GENERAL, cls(), true), 0.0)
        assertEquals(0.2, TemperaturePolicy.pick(TaskKind.GENERAL, cls(fresh = true), false), 0.0)
        assertEquals(0.2, TemperaturePolicy.pick(TaskKind.REASON, cls(QType.COMPLEX), false), 0.0)
        assertEquals(0.3, TemperaturePolicy.pick(TaskKind.GENERAL, cls(), false), 0.0)
        assertEquals(0.3, TemperaturePolicy.pick(TaskKind.QUICK, cls(QType.SHORT), false), 0.0)
        assertEquals(0.9, TemperaturePolicy.pick(TaskKind.WRITE, cls(fresh = true), true), 0.0)
    }

    @Test fun routerPassesTemperatureExceptToReasoningModels() = runTest {
        val tr = TempTransport()
        val router = Router(tr, clock = { 0L })
        val plain = tgt("plain")
        val deep = tgt("deep", reasoning = true)
        fun req(t: Target) = RouteRequest(
            messages = listOf(ChatMsg("user", "hi")), mode = ChatMode.AUTO, pinnedKey = null,
            candidates = listOf(t), temperature = 0.25
        )
        router.stream(req(plain)).toList()
        router.stream(req(deep)).toList()
        assertEquals(listOf(plain.key to 0.25, deep.key to null), tr.seen)
    }

    @Test fun routerDefaultsToNoTemperature() = runTest {
        val tr = TempTransport()
        val t = tgt("a")
        Router(tr, clock = { 0L }).stream(
            RouteRequest(listOf(ChatMsg("user", "hi")), ChatMode.AUTO, null, listOf(t))
        ).toList()
        assertEquals(listOf(t.key to null), tr.seen)
    }

    @Test fun verifierParsesReviewerReplies() {
        assertEquals(emptyList<String>(), Verifier.parse("OK")!!.unsupported)
        assertEquals(emptyList<String>(), Verifier.parse("ok\n")!!.unsupported)
        val r = Verifier.parse("- The price rose 5%\n* Released in 2019\n1. Third claim\nrandom chatter")
        assertEquals(listOf("The price rose 5%", "Released in 2019", "Third claim"), r!!.unsupported)
        assertNull(Verifier.parse(null))
        assertNull(Verifier.parse("   "))
        assertEquals(emptyList<String>(), Verifier.parse("looks fine to me")!!.unsupported)
        assertEquals(Verifier.MAX_CLAIMS, Verifier.parse((1..9).joinToString("\n") { "- claim $it" })!!.unsupported.size)
    }

    @Test fun verifierRunsOnlyWhenWorthIt() {
        assertTrue(Verifier.shouldVerify(true, true, Difficulty.NORMAL, true, true))
        assertTrue(Verifier.shouldVerify(true, false, Difficulty.HARD, true, true))
        assertFalse(Verifier.shouldVerify(false, true, Difficulty.HARD, true, true))
        assertFalse(Verifier.shouldVerify(true, true, Difficulty.HARD, false, true))
        assertFalse(Verifier.shouldVerify(true, false, Difficulty.NORMAL, true, true))
        assertFalse(Verifier.shouldVerify(true, true, Difficulty.HARD, true, false))
    }

    @Test fun reviewerIsADifferentModelAndGetsSourcesAndAnswer() = runTest {
        val a = tgt("a")
        val b = tgt("b")
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("- unsupported claim") } }
        val side = SideTasks(tr, Router(tr, clock = { 0L }), clock = { 0L })
        val res = Verifier.run(side, listOf(a, b), a.key, "q?", "<sources>S</sources>", "the answer")
        assertEquals(listOf("unsupported claim"), res!!.unsupported)
        assertEquals(1, tr.calls.size)
        assertEquals(b.key, tr.calls[0].first)
        val user = tr.calls[0].second[1].content
        assertTrue(user.contains("<sources>S</sources>"))
        assertTrue(user.contains("the answer"))
        assertNull(Verifier.run(side, listOf(a), a.key, "q", "s", "a"))
        assertTrue(Verifier.warning(listOf("x", "y")).startsWith("⚠ x"))
    }

    @Test fun officialSourcesRankFirst() {
        val results = listOf(
            SearchResult("Blog", "https://someblog.com/post", "s"),
            SearchResult("Pinned", "https://www.pinterest.com/pin", "s"),
            SearchResult("Gov", "https://www.health.gov.sa/info", "s"),
            SearchResult("Edu", "https://cs.stanford.edu/page", "s"),
            SearchResult("Docs", "https://docs.example.com/a", "s")
        )
        val ranked = SourceRank.rank(results, 5)
        assertEquals(listOf("Gov", "Edu", "Docs"), ranked.take(3).map { it.title })
        assertEquals("Pinned", ranked.last().title)
        assertTrue(SourceRank.isOfficial("https://nih.gov/x"))
        assertTrue(SourceRank.isOfficial("https://ox.ac.uk/x"))
        assertFalse(SourceRank.isOfficial("https://govtech.com/x"))
    }

    @Test fun rankDedupesDropsPrivateAndCaps() {
        val results = listOf(
            SearchResult("A", "https://a.com/x", "s"),
            SearchResult("A2", "https://www.a.com/x/", "s"),
            SearchResult("Lan", "https://192.168.0.1/x", "s"),
            SearchResult("B", "http://b.com/y", "s")
        )
        val r = SourceRank.rank(results, 5)
        assertEquals(listOf("A", "B"), r.map { it.title })
        assertEquals(1, SourceRank.rank(results, 1).size)
    }

    @Test fun recentYearGivesSmallBoost() {
        val results = listOf(
            SearchResult("Old", "https://one.com/a", "published long ago"),
            SearchResult("New", "https://two.com/b", "updated in 2026")
        )
        assertEquals("New", SourceRank.rank(results, 2, 2026).first().title)
        assertEquals("Old", SourceRank.rank(results, 2, null).first().title)
        assertNotNull(SourceRank.rank(results, 2, 2026))
    }
}
