package com.freellm.chat.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChainSearchTest {
    private class P(
        override val kind: SearchKind,
        private val result: List<SearchResult>? = null,
        private val error: Throwable? = null
    ) : SearchProvider {
        var calls = 0
        override suspend fun search(query: String, count: Int): List<SearchResult> {
            calls++
            if (error != null) throw error
            return result.orEmpty()
        }
    }

    private val r = listOf(SearchResult("t", "https://a.com", "s"))

    @Test fun firstWinsAndSecondIsNotCalled() = runTest {
        val a = P(SearchKind.TAVILY, r)
        val b = P(SearchKind.LOCAL, r)
        assertEquals(r, ChainSearch(a, b).search("q", 3))
        assertEquals(0, b.calls)
    }

    @Test fun failureFallsBackToSecond() = runTest {
        val a = P(SearchKind.TAVILY, error = LlmException(ErrorKind.PAYMENT))
        val b = P(SearchKind.LOCAL, r)
        assertEquals(r, ChainSearch(a, b).search("q", 3))
    }

    @Test fun emptyFirstFallsBackToSecond() = runTest {
        val a = P(SearchKind.TAVILY, emptyList())
        val b = P(SearchKind.LOCAL, r)
        assertEquals(r, ChainSearch(a, b).search("q", 3))
    }

    @Test fun bothFailingThrowsFirstError() = runTest {
        val a = P(SearchKind.TAVILY, error = LlmException(ErrorKind.AUTH))
        val b = P(SearchKind.LOCAL, error = LlmException(ErrorKind.TIMEOUT))
        try {
            ChainSearch(a, b).search("q", 3)
            fail()
        } catch (e: LlmException) {
            assertEquals(ErrorKind.AUTH, e.kind)
        }
    }

    @Test fun failureThenEmptyReturnsEmptyNotError() = runTest {
        val a = P(SearchKind.TAVILY, error = LlmException(ErrorKind.SERVER))
        val b = P(SearchKind.LOCAL, emptyList())
        assertTrue(ChainSearch(a, b).search("q", 3).isEmpty())
    }

    @Test fun kindFollowsFirst() {
        assertEquals(SearchKind.TAVILY, ChainSearch(P(SearchKind.TAVILY), null).kind)
    }
}
