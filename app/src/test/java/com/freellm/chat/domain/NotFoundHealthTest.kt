package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotFoundHealthTest {
    private fun tgt(model: String) = Target(
        providerId = "gemini",
        providerName = "gemini",
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = model, provider = "gemini", displayName = model, priority = 1)
    )

    @Test
    fun notFoundMarksOnlyThatModel() {
        val h = Health(RouterConfig())
        val missing = tgt("gone")
        val other = tgt("alive")
        h.onFailure(missing, LlmException(ErrorKind.NOT_FOUND, 404, message = "model not found"), 1_000L)
        assertTrue(h.state(missing, 1_001L) is ModelHealth.Dead)
        assertNull(h.blockedUntil(other, 1_001L))
        assertTrue(h.state(other, 1_001L) is ModelHealth.Ready)
    }

    @Test
    fun notFoundNeverCoolsTheProviderKey() {
        val h = Health(RouterConfig())
        h.onFailure(tgt("gone"), LlmException(ErrorKind.NOT_FOUND, 404), 1_000L)
        assertEquals(0, h.forbiddenCount("gemini"))
        assertNull(h.blockedUntil(tgt("alive"), 1_001L))
    }
}
