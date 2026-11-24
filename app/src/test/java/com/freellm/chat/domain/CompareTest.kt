package com.freellm.chat.domain

import com.freellm.chat.data.ModelRatings
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareTest {
    private class Mem : UsageStore {
        val m = HashMap<String, String>()
        override fun read(key: String): String? = m[key]
        override fun write(key: String, value: String) {
            m[key] = value
        }
    }

    private fun tgt(provider: String, priority: Int, modes: List<String> = emptyList(), reasoning: Boolean = false) = Target(
        providerId = provider, providerName = provider, baseUrl = "https://example.com", headers = emptyMap(), apiKey = "k",
        model = ModelDef(
            id = "m-$provider", provider = provider, displayName = provider, priority = priority,
            contextWindow = 100_000, modes = modes, reasoning = reasoning
        )
    )

    private fun done(t: Target, text: String) = CompareSide(t, SideState.DONE, text)

    @Test fun bothSidesFinishInParallel() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ -> flow<String> { emit("from-" + t.providerId) } }
        val last = Comparer(Router(tr, clock = { 0L })).run(CompareRequest("q", a, b)).last()
        assertTrue(last.finished)
        assertEquals(SideState.DONE, last.a.state)
        assertEquals(SideState.DONE, last.b.state)
        assertEquals("from-a", last.a.text)
        assertEquals("from-b", last.b.text)
        assertEquals(2, last.doneCount)
    }

    @Test fun oneSideFailingKeepsTheOtherResult() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { throw LlmException(ErrorKind.SERVER, 500) }
            else flow<String> { emit("fine") }
        }
        val last = Comparer(Router(tr, clock = { 0L })).run(CompareRequest("q", a, b)).last()
        assertTrue(last.finished)
        assertEquals(SideState.FAILED, last.a.state)
        assertNotNull(last.a.error)
        assertEquals(SideState.DONE, last.b.state)
        assertEquals("fine", last.b.text)
        assertEquals(1, last.doneCount)
    }

    @Test fun bothFailingIsReportedPerSide() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { throw LlmException(ErrorKind.RATE_LIMIT, 429, 30) } }
        val last = Comparer(Router(tr, clock = { 0L })).run(CompareRequest("q", a, b)).last()
        assertEquals(SideState.FAILED, last.a.state)
        assertEquals(SideState.FAILED, last.b.state)
        assertEquals(0, last.doneCount)
    }

    @Test fun eachSideIsPinnedToItsOwnModel() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("x") } }
        Comparer(Router(tr, clock = { 0L })).run(CompareRequest("q", a, b)).last()
        assertEquals(setOf(a.key, b.key), tr.calls.map { it.first }.toSet())
        assertEquals(2, tr.calls.size)
    }

    @Test fun choosingChangesRatings() {
        val r = ModelRatings(Mem())
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val res = CompareResult(done(a, "one"), done(b, "two"))
        CompareChoice.apply(res, true, false, r::rate, r::rateArabic)
        assertEquals(1, r.score(a.key))
        assertEquals(-1, r.score(b.key))
        assertEquals(0, r.arabicScore(a.key))
        CompareChoice.apply(res, false, true, r::rate, r::rateArabic)
        assertEquals(0, r.score(a.key))
        assertEquals(0, r.score(b.key))
        assertEquals(-1, r.arabicScore(a.key))
        assertEquals(1, r.arabicScore(b.key))
    }

    @Test fun failedLoserIsNotPenalizedAndFailedWinnerIsIgnored() {
        val r = ModelRatings(Mem())
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val res = CompareResult(done(a, "one"), CompareSide(b, SideState.FAILED, "", "x"))
        CompareChoice.apply(res, true, false, r::rate, r::rateArabic)
        assertEquals(1, r.score(a.key))
        assertEquals(0, r.score(b.key))
        CompareChoice.apply(res, false, false, r::rate, r::rateArabic)
        assertEquals(1, r.score(a.key))
        assertEquals(0, r.score(b.key))
    }

    @Test fun mergeNeedsBothAnswers() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("merged") } }
        val router = Router(tr, clock = { 0L })
        val side = SideTasks(tr, router, clock = { 0L })
        val res = CompareResult(done(a, "one"), CompareSide(b, SideState.FAILED))
        assertFalse(Merger.canMerge(res))
        assertNull(Merger.run(side, listOf(a, b), res, "q"))
        assertTrue(tr.calls.isEmpty())
    }

    @Test fun mergePassesBothAnswersToTheStrongestModel() = runTest {
        val light = tgt("light", 1, modes = listOf("fast"))
        val strong = tgt("strong", 5, modes = listOf("smart"), reasoning = true)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("merged answer") } }
        val router = Router(tr, clock = { 0L })
        val side = SideTasks(tr, router, clock = { 0L })
        val res = CompareResult(done(light, "ALPHA text"), done(strong, "BETA text"))
        val out = Merger.run(side, listOf(light, strong), res, "what is x")
        assertEquals("merged answer", out)
        assertEquals(1, tr.calls.size)
        assertEquals(strong.key, tr.calls[0].first)
        val msgs = tr.calls[0].second
        assertEquals("system", msgs[0].role)
        assertEquals(Merger.SYSTEM, msgs[0].content)
        val user = msgs[1].content
        assertTrue(user.contains("ALPHA text"))
        assertTrue(user.contains("BETA text"))
        assertTrue(user.contains("what is x"))
    }

    @Test fun strongPickerPrefersSmartThenReasoning() {
        val router = Router(FakeTransport { _, _, _ -> flow<String> { emit("x") } }, clock = { 0L })
        val plain = tgt("plain", 1)
        val smart = tgt("smart", 3, modes = listOf("smart"))
        val deep = tgt("deep", 4, modes = listOf("smart"), reasoning = true)
        val order = StrongPicker.pick(listOf(plain, smart, deep), router.health, router.limiter, 0L)
        assertEquals(listOf("deep", "smart", "plain"), order.map { it.providerId })
    }

    @Test fun mergePromptWrapsEachPart() {
        val p = Merger.prompt("q", "a1", "a2")
        assertTrue(p.contains("<answer_1>\na1\n</answer_1>"))
        assertTrue(p.contains("<answer_2>\na2\n</answer_2>"))
    }
}
