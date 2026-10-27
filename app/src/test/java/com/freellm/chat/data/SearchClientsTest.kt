package com.freellm.chat.data

import com.freellm.chat.domain.ErrorKind
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchClientsTest {
    private val p = TavilySearch(OkHttpClient(), "k")

    @Test fun httpStatusMapsToErrorKinds() {
        assertEquals(ErrorKind.RATE_LIMIT, p.httpError(429, "12", "").kind)
        assertEquals(12L, p.httpError(429, "12", "").retryAfterSec)
        assertEquals(ErrorKind.PAYMENT, p.httpError(432, null, "").kind)
        assertEquals(ErrorKind.PAYMENT, p.httpError(400, null, "Not enough credits").kind)
        assertEquals(ErrorKind.AUTH, p.httpError(401, null, "").kind)
        assertEquals(ErrorKind.AUTH, p.httpError(403, null, "forbidden").kind)
        assertEquals(ErrorKind.RATE_LIMIT, p.httpError(403, null, "dailyLimitExceeded").kind)
        assertEquals(ErrorKind.SERVER, p.httpError(503, null, "").kind)
        assertEquals(ErrorKind.BAD_REQUEST, p.httpError(400, null, "bad").kind)
    }

    @Test fun errorBodyIsTruncated() {
        assertTrue(p.httpError(500, null, "x".repeat(1000)).message.orEmpty().length <= 160)
        assertFalse(p.httpError(500, null, "a\nb").message.orEmpty().contains("\n"))
    }
}
