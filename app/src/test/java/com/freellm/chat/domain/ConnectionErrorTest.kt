package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionErrorTest {
    @Test fun workerOwnRateLimitIsNotAProviderRateLimit() {
        assertEquals(ErrorKind.SERVER, ProviderErrors.kind(503, "{\"error\":{\"message\":\"worker rate limit exceeded\",\"type\":\"worker_error\"}}"))
    }

    @Test fun providerRealRateLimitStillCoolsDown() {
        assertEquals(ErrorKind.RATE_LIMIT, ProviderErrors.kind(429, "too many requests"))
    }
}
