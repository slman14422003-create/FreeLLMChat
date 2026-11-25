package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastResortTest {
    private fun tgt(model: String, priority: Int) = Target(
        providerId = "a",
        providerName = "a",
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = model, provider = "a", displayName = model, priority = priority, contextWindow = 100000)
    )

    @Test
    fun zeroSuccessOverFiveAttemptsIsLastResort() {
        val s = ModelStats()
        repeat(4) { s.onFail("k") }
        assertFalse(s.lastResort("k"))
        s.onFail("k")
        assertTrue(s.lastResort("k"))
        s.onOk("k")
        assertFalse(s.lastResort("k"))
    }

    @Test
    fun lastResortModelIsTriedAfterOthersButNotRemoved() = runTest {
        val bad = tgt("bad", 1)
        val good = tgt("good", 2)
        val tr = FakeTransport { _, _, _ -> flow<String> { emit("ok") } }
        val router = Router(tr, clock = { 0L })
        repeat(5) { router.stats.onFail(bad.key) }
        val req = RouteRequest(messages = listOf(ChatMsg("user", "hi")), mode = ChatMode.AUTO, pinnedKey = null, candidates = listOf(bad, good), adaptive = true)
        assertEquals(listOf(good.key, bad.key), router.order(req).map { it.key })
        router.stream(req).toList()
        assertEquals(listOf(good.key), tr.calls.map { it.first })
    }
}
