package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderErrorsTest {
    @Test fun dataPolicyIsAProviderConfigIssue() {
        val body = """{"error":{"message":"No endpoints found matching your data policy (Free model publication). Configure: https://openrouter.ai/settings/privacy","code":404}}"""
        assertEquals(ErrorKind.POLICY, ProviderErrors.kind(404, body))
    }

    @Test fun noEndpointsIsTransientNotDead() {
        assertEquals(ErrorKind.SERVER, ProviderErrors.kind(404, """{"error":{"message":"No endpoints found for x/y:free."}}"""))
    }

    @Test fun plainNotFoundStillMarksModelMissing() {
        assertEquals(ErrorKind.NOT_FOUND, ProviderErrors.kind(404, "Not found"))
        assertEquals(ErrorKind.NOT_FOUND, ProviderErrors.kind(400, """{"error":"The model does not exist"}"""))
    }

    @Test fun statusCodesMapAsBefore() {
        assertEquals(ErrorKind.RATE_LIMIT, ProviderErrors.kind(429, ""))
        assertEquals(ErrorKind.PAYMENT, ProviderErrors.kind(402, ""))
        assertEquals(ErrorKind.AUTH, ProviderErrors.kind(401, ""))
        assertEquals(ErrorKind.SERVER, ProviderErrors.kind(503, ""))
        assertEquals(ErrorKind.BAD_REQUEST, ProviderErrors.kind(400, "bad"))
    }

    @Test fun dailyLimitWaitsUntilUtcMidnight() {
        val noon = 12 * 3600 * 1000L
        val wait = ProviderErrors.retryAfter(429, "Rate limit exceeded: free-models-per-day", 5L, noon)
        assertEquals(12 * 3600L, wait)
    }

    @Test fun shortLimitKeepsHeaderValue() {
        assertEquals(7L, ProviderErrors.retryAfter(429, "slow down", 7L, 0L))
        assertNull(ProviderErrors.retryAfter(429, "slow down", null, 0L))
    }

    @Test fun streamErrorsAreClassified() {
        assertEquals(ErrorKind.POLICY, ProviderErrors.streamKind("No endpoints found matching your data policy"))
        assertEquals(ErrorKind.RATE_LIMIT, ProviderErrors.streamKind("""{"code":429,"message":"Provider returned error"}"""))
        assertEquals(ErrorKind.SERVER, ProviderErrors.streamKind("upstream exploded"))
        assertTrue(ProviderErrors.streamRetryAfter("free-models-per-day", 0L)!! >= 60L)
    }
}
