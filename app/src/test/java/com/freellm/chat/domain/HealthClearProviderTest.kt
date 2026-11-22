package com.freellm.chat.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HealthClearProviderTest {
    private fun t(provider: String, model: String) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://example.com",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = model, provider = provider.substringBefore('#'), displayName = model, priority = 1)
    )

    @Test fun clearProviderRemovesModelCooldownsAndDeadMarkersForItsKeysOnly() {
        val h = Health(RouterConfig())
        val a1 = t("a", "m1")
        val a2 = t("a#2", "m2")
        val ab = t("ab", "m1")
        val b = t("b", "m1")
        for (x in listOf(a1, ab, b)) repeat(3) { h.onFailure(x, LlmException(ErrorKind.SERVER, 500), 0L) }
        h.onFailure(a2, LlmException(ErrorKind.NOT_FOUND, 404), 0L)
        assertNotNull(h.blockedUntil(a1, 1L))
        assertNotNull(h.blockedUntil(a2, 1L))
        h.clearProvider("a")
        assertNull(h.blockedUntil(a1, 1L))
        assertNull(h.blockedUntil(a2, 1L))
        assertNotNull(h.blockedUntil(ab, 1L))
        assertNotNull(h.blockedUntil(b, 1L))
    }

    @Test fun regionFailureNeverPenalizesTheModel() {
        val h = Health(RouterConfig())
        val x = t("a", "m")
        repeat(10) { h.onFailure(x, LlmException(ErrorKind.REGION, 403), 0L) }
        assertNull(h.blockedUntil(x, 1L))
    }
}
