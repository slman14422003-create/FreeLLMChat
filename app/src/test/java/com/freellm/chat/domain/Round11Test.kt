package com.freellm.chat.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Round11Test {
    private fun tgt(provider: String, priority: Int, rpm: Int = 0, id: String = "m-$provider") = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = id, provider = provider.substringBefore('#'), displayName = provider, priority = priority, rpm = rpm)
    )

    private fun req(vararg t: Target) = RouteRequest(
        messages = listOf(ChatMsg("user", "hi")),
        mode = ChatMode.AUTO,
        pinnedKey = null,
        candidates = t.toList()
    )

    private fun text(events: List<RouteEvent>) = events.filterIsInstance<RouteEvent.Delta>().joinToString("") { it.text }

    private val day = 24L * 3_600_000

    @Test fun statsSurviveRestartWithDecay() {
        val a = ModelStats()
        repeat(10) {
            a.onFirstToken("p/m", 900)
            a.onOk("p/m")
        }
        repeat(2) { a.onFail("p/m") }
        val raw = a.export(0L)
        val b = ModelStats()
        b.import(raw, 3 * day)
        val row = b.rows().first { it.key == "p/m" }
        assertEquals(5, row.ok)
        assertEquals(1, row.fail)
        assertEquals(5, row.samples)
        assertEquals(900L, b.latencyMs("p/m"))
    }

    @Test fun staleStatsAreDropped() {
        val a = ModelStats()
        a.onFirstToken("p/m", 500)
        a.onOk("p/m")
        val b = ModelStats()
        b.import(a.export(0L), 15 * day)
        assertTrue(b.rows().isEmpty())
    }

    @Test fun seedOnlyFillsMissingKeys() {
        val s = ModelStats()
        s.onFirstToken("a/m", 300)
        s.seed(mapOf("a/m" to 9000L, "b/m" to 700L))
        assertEquals(300L, s.latencyMs("a/m"))
        assertEquals(700L, s.latencyMs("b/m"))
        assertEquals(0, s.samples("b/m"))
        assertTrue(s.hasHistory())
    }

    @Test fun healthSeedParse() {
        val m = HealthSeed.parse("""{"runs":[],"latency":{"x/y":1699,"z/w":644}}""")
        assertEquals(1699L, m["x/y"])
        assertEquals(644L, m["z/w"])
        assertTrue(HealthSeed.parse("not json").isEmpty())
    }

    @Test fun healthStateRoundTrips() {
        val h = Health(RouterConfig())
        val t = tgt("a", 1)
        repeat(3) { h.onFailure(t, LlmException(ErrorKind.SERVER, 500), 1000L) }
        val raw = h.export(1001L)
        val h2 = Health(RouterConfig())
        h2.import(raw, 1002L)
        assertNotNull(h2.blockedUntil(t, 1002L))
        assertNull(h2.blockedUntil(t, 1_000_000L))
    }

    @Test fun fewSamplesDoNotOutrankPriority() {
        val low = tgt("a", 1)
        val high = tgt("b", 200)
        val r = Router(FakeTransport { _, _, _ -> flow<String> { emit("x") } }, clock = { 0L })
        r.stats.onFirstToken(high.key, 100)
        val order = r.order(req(low, high).copy(adaptive = true, rating = { if (it == high.key) 3 else 0 }))
        assertEquals(low.key, order.first().key)
    }

    @Test fun enoughSamplesLetLatencyReorder() {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val r = Router(FakeTransport { _, _, _ -> flow<String> { emit("x") } }, clock = { 0L })
        repeat(6) {
            r.stats.onFirstToken(a.key, 7000)
            r.stats.onFirstToken(b.key, 200)
        }
        assertEquals(b.key, r.order(req(a, b).copy(adaptive = true)).first().key)
    }

    @Test fun fastRequestsDemoteReasoningModels() {
        val reasoning = Target("a", "a", "https://example.com", emptyMap(), "k", ModelDef("m", "a", "a", 1, reasoning = true))
        val plain = tgt("b", 5)
        val r = Router(FakeTransport { _, _, _ -> flow<String> { emit("x") } }, clock = { 0L })
        assertEquals(plain.key, r.order(req(reasoning, plain).copy(fast = true)).first().key)
        assertEquals(reasoning.key, r.order(req(reasoning, plain)).first().key)
    }

    @Test fun p95DrivesTimeoutsInsideBounds() {
        val s = ModelStats()
        assertNull(s.p95("a/m"))
        listOf(500L, 600L, 700L, 800L, 9000L).forEach { s.onFirstToken("a/m", it) }
        assertEquals(9000L, s.p95("a/m"))
    }

    @Test fun hedgeLoserIsRefunded() = runTest {
        val a = tgt("a", 1, rpm = 10)
        val b = tgt("b", 2, rpm = 10)
        val tr = FakeTransport { t, _, _ ->
            if (t.providerId == "a") flow<String> { delay(60_000); emit("late") } else flow<String> { delay(500); emit("fast and complete answer") }
        }
        val lim = UsageLimiter()
        val r = Router(tr, RouterConfig(hedgeMs = 1000, firstTokenTimeoutMs = 20_000), clock = { 0L }, limiter = lim)
        val events = r.stream(req(a, b).copy(fast = true)).toList()
        assertEquals(b.key, (events.last() as RouteEvent.Done).target.key)
        assertEquals(0, lim.snapshot(a, 0L).minUsed)
        assertEquals(1, lim.snapshot(b, 0L).minUsed)
    }

    @Test fun restrictedPrimaryOnlyHedgesAcrossProviders() = runTest {
        val a = tgt("a", 1, rpm = 10, id = "m1")
        val a2 = tgt("a", 2, rpm = 10, id = "m2")
        val tr = FakeTransport { _, _, _ -> flow<String> { delay(60_000); emit("never") } }
        val r = Router(tr, RouterConfig(hedgeMs = 1000, firstTokenTimeoutMs = 20_000), clock = { 0L })
        r.stream(req(a, a2).copy(fast = true)).toList()
        assertEquals(listOf(a.key, a2.key), tr.calls.map { it.first }.distinct())
        assertTrue(tr.calls.size >= 2)
    }

    @Test fun poorReplyIsCheckedBeforeAnythingIsShown() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { t, _, _ -> if (t.providerId == "a") flow<String> { emit(" .") } else flow<String> { emit("A proper full answer.") } }
        val events = Router(tr, clock = { 0L }).stream(req(a, b)).toList()
        assertFalse(events.any { it is RouteEvent.Restart })
        assertEquals("A proper full answer.", text(events))
        assertEquals(b.key, (events.last() as RouteEvent.Done).target.key)
    }

    @Test fun languageMismatchRetriesBeforeStreaming() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val ar = "\u0645\u0631\u062d\u0628\u0627 \u0628\u0643 \u0643\u064a\u0641 \u064a\u0645\u0643\u0646\u0646\u064a \u0645\u0633\u0627\u0639\u062f\u062a\u0643"
        val tr = FakeTransport { t, _, _ -> if (t.providerId == "a") flow<String> { emit("Hello there, how can I help you?") } else flow<String> { emit(ar) } }
        val events = Router(tr, clock = { 0L }).stream(req(a, b).copy(lang = "ar")).toList()
        assertEquals(ar, text(events))
        assertFalse(events.any { it is RouteEvent.Restart })
    }

    @Test fun languageMismatchIsAcceptedOnSecondModel() = runTest {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("Hello there, how can I help you?") } }
        val events = Router(tr, clock = { 0L }).stream(req(a, b).copy(lang = "ar")).toList()
        assertEquals("Hello there, how can I help you?", text(events))
        assertTrue(events.last() is RouteEvent.Done)
    }

    @Test fun langGuardRules() {
        val ar = "\u0645\u0631\u062d\u0628\u0627 \u0628\u0643 \u0643\u064a\u0641 \u062d\u0627\u0644\u0643 \u0627\u0644\u064a\u0648\u0645"
        assertEquals("ar", LangGuard.detect(ar))
        assertEquals("en", LangGuard.detect("what is the capital of France"))
        assertTrue(LangGuard.mismatch("ar", "This reply is plainly English"))
        assertFalse(LangGuard.mismatch("ar", ar))
        assertTrue(LangGuard.mismatch("en", ar))
        assertFalse(LangGuard.mismatch(null, ar))
        assertTrue(LangGuard.isGreeting("hello"))
        assertFalse(LangGuard.isGreeting("explain how recursion works in detail please"))
        assertEquals("Reply in Arabic.", LangGuard.instruction("ar"))
    }

    @Test fun neverSucceededModelGetsShortFirstCooldown() {
        val h = Health(RouterConfig())
        val t = tgt("a", 1)
        h.onFailure(t, LlmException(ErrorKind.TIMEOUT), 0L)
        val until = h.blockedUntil(t, 1L)
        assertNotNull(until)
        assertTrue(until!! <= 20_000L)
    }

    @Test fun knownModelKeepsNormalCooldown() {
        val h = Health(RouterConfig())
        val t = tgt("a", 1)
        h.onSuccess(t, 0L)
        h.onFailure(t, LlmException(ErrorKind.TIMEOUT), 700_000L)
        assertTrue(h.blockedUntil(t, 700_001L)!! > 700_000L + 20_000L)
    }

    @Test fun networkChangeClearsTransientAndCapsBackoff() {
        val h = Health(RouterConfig())
        val t = tgt("a", 1)
        h.onSuccess(t, 0L)
        repeat(4) { i -> h.onFailure(t, LlmException(ErrorKind.INTERRUPTED), 1000L * i) }
        assertNotNull(h.blockedUntil(t, 5000L))
        h.onNetworkChange()
        assertNull(h.blockedUntil(t, 5000L))
    }

    @Test fun keyLevelCooldownDoesNotBlockSiblingKey() {
        val h = Health(RouterConfig())
        val k1 = tgt("p#1", 1)
        val k2 = tgt("p#2", 1)
        h.onFailure(k1, LlmException(ErrorKind.RATE_LIMIT, 429, 30), 0L)
        assertNotNull(h.blockedUntil(k1, 1L))
        assertNull(h.blockedUntil(k2, 1L))
    }

    @Test fun routerSnapshotRestores() = runTest {
        val a = tgt("a", 1)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("a perfectly fine answer") } }
        val r = Router(tr, clock = { 0L })
        r.stream(req(a)).toList()
        val raw = r.snapshot(0L)
        val r2 = Router(tr, clock = { 0L })
        r2.restore(raw, 0L)
        assertTrue(r2.stats.samples(a.key) >= 1)
    }

    @Test fun arabicEstimateUsesSeparateFactor() {
        val latin = "a".repeat(220)
        val arabic = "\u0627".repeat(220)
        assertTrue(ContextTrimmer.estimate(arabic) > ContextTrimmer.estimate(latin))
        assertEquals(4 + 63, ContextTrimmer.estimate(latin))
        assertTrue(ContextTrimmer.estimate(arabic) in 103..105)
    }

    @Test fun ghostFilterDependsOnDurationAndRatio() {
        assertTrue(GhostFilter.isGhost("Thank you.", 600, 0.9))
        assertTrue(GhostFilter.isGhost("thank you", 3000, 0.2))
        assertFalse(GhostFilter.isGhost("Thank you", 3000, 0.8))
        assertTrue(GhostFilter.isGhost("\u0634\u0643\u0631\u0627", 800, 0.9))
        assertFalse(GhostFilter.isGhost("\u0634\u0643\u0631\u0627", 2500, 0.9))
        assertTrue(GhostFilter.isGhost("Thanks for watching!", 5000, 0.9))
        assertFalse(GhostFilter.isGhost("what time is it", 600, 0.2))
    }

    @Test fun sessionClosingReasons() {
        assertEquals(CloseReason.NONE, SessionPolicy.closeReason(2, 2))
        assertEquals(CloseReason.FAILURES, SessionPolicy.closeReason(3, 0))
        assertEquals(CloseReason.SILENCE, SessionPolicy.closeReason(0, 3))
        assertEquals(CloseReason.FAILURES, SessionPolicy.closeReason(3, 3))
    }

    @Test fun firstSpeechChunkSplitsEarlyAtClause() {
        val s = SpeechSplitter(190, 40)
        val out = s.push("Well, that is a really good question to ask, and the answer is longer")
        assertEquals(1, out.size)
        assertTrue(out[0].length in 24..80)
        val none = SpeechSplitter(190, 0).push("Well, that is a really good question to ask, and the answer is longer")
        assertTrue(none.isEmpty())
    }

    @Test fun adaptiveVadThresholdFollowsNoise() {
        val quiet = VoiceVad()
        quiet.calibrate(30.0, false)
        val noisy = VoiceVad()
        noisy.calibrate(500.0, false)
        assertTrue(noisy.currentThreshold > quiet.currentThreshold)
        val events = (1..40).map { noisy.feed(900.0, 30) }
        assertTrue(events.all { it == VadEvent.NONE })
        assertEquals(VadEvent.START, (1..5).map { quiet.feed(900.0, 30) }.first { it != VadEvent.NONE })
    }

    @Test fun vadReportsSpeechRatio() {
        val v = VoiceVad(silenceMs = 900)
        repeat(20) { v.feed(3000.0, 30) }
        repeat(40) { v.feed(5.0, 30) }
        assertTrue(v.speechRatio > 0.9)
        assertTrue(v.lastTotalMs in 500..700)
    }

    @Test fun testWavIsParsable() {
        val info = Wav.parse(VoiceTestAudio.wav())!!
        assertEquals(16_000, info.sampleRate)
        assertTrue(info.dataLength > 10_000)
    }

    @Test fun whisperLanguageChoice() {
        assertEquals("fr", SpeechLang.whisper("fr", "ar"))
        assertEquals("ar", SpeechLang.whisper("auto", "ar"))
        assertEquals("auto", SpeechLang.whisper("auto", null))
        assertEquals("ar-SA", SpeechLang.deviceTag("auto", "ar", "en-GB"))
        assertEquals("en-GB", SpeechLang.deviceTag("auto", null, "en-GB"))
        assertEquals("en", SpeechLang.isoOf("English"))
    }

    @Test fun timingLogKeepsLastFifty() {
        val log = TimingLog()
        repeat(60) { log.add(TimingEntry(it.toLong(), 1, 2, 3, 4, 0, "m", 10, true)) }
        assertEquals(50, log.all().size)
        assertEquals(59L, log.all().first().at)
        assertEquals(TimingEntry(1, 2, 3, 4, 5, 6, "m", 7, true), TimingEntry.decode(TimingEntry(1, 2, 3, 4, 5, 6, "m", 7, true).encode()))
    }

    @Test fun ambiguityResolvedLocally() {
        assertEquals(QType.CODE, AmbiguityResolver.heuristic("why does this throw an error in my build"))
        assertEquals(QType.SHORT, AmbiguityResolver.heuristic("is the sky blue today?"))
    }

    @Test fun assistSearchNeedsQuotaHeadroom() {
        assertTrue(SearchPolicy.shouldAssist(true, true, false, "who is Ada Lovelace", false, false, 80, 100))
        assertFalse(SearchPolicy.shouldAssist(true, true, false, "who is Ada Lovelace", false, false, 40, 100))
        assertFalse(SearchPolicy.shouldAssist(true, true, true, "who is Ada Lovelace", false, false, 80, 100))
    }

    @Test fun toolIntentIsDetected() {
        assertFalse(ToolPolicy.hasIntent("tell me a story about a fox", false))
        assertTrue(ToolPolicy.hasIntent("calculate 12 * 7", false))
    }

    @Test fun sidePickerReservesTopModels() {
        val list = (1..6).map { tgt("p$it", it) }
        val r = Router(FakeTransport { _, _, _ -> flow<String> { emit("x") } }, clock = { 0L })
        val picked = SidePicker.pick(list, r.health, r.limiter, 0L)
        assertFalse(picked.any { it.key == list[0].key || it.key == list[1].key })
        assertTrue(picked.isNotEmpty())
    }
}
