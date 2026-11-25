package com.freellm.chat.domain

import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Test

class RouterSlowDemotionTest {
    private fun tgt(provider: String, priority: Int) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = "m-$provider", provider = provider, displayName = provider, priority = priority)
    )

    private fun router(): Router = Router(FakeTransport { _, _, _ -> flow<String> { emit("x") } }, RouterConfig(), clock = { 0L })

    private fun req(mode: ChatMode, pinned: String?, vararg t: Target) = RouteRequest(
        messages = listOf(ChatMsg("user", "hi")),
        mode = mode,
        pinnedKey = pinned,
        candidates = t.toList(),
        adaptive = true
    )

    @Test
    fun slowModelDropsBehindFasterOne() {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val r = router()
        repeat(6) { r.stats.onFirstToken(a.key, 9_000L) }
        repeat(6) { r.stats.onFirstToken(b.key, 900L) }
        assertEquals(listOf(b.key, a.key), r.order(req(ChatMode.AUTO, null, a, b)).map { it.key })
    }

    @Test
    fun pinnedModeIgnoresSlowness() {
        val a = tgt("a", 1)
        val b = tgt("b", 2)
        val r = router()
        repeat(6) { r.stats.onFirstToken(a.key, 9_000L) }
        assertEquals(listOf(a.key), r.order(req(ChatMode.PINNED, a.key, a, b)).map { it.key })
    }
}
