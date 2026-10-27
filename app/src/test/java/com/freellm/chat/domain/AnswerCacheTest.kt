package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCacheTest {
    @Test fun sameQuestionDifferentFormattingSameKey() {
        assertEquals(CacheKey.of("What is Kotlin?", ""), CacheKey.of("what is   kotlin", ""))
    }

    @Test fun arabicVariantsShareKey() {
        assertEquals(CacheKey.of("أين تقع مصر؟", ""), CacheKey.of("اين تقع مصر", ""))
    }

    @Test fun contextChangesKey() {
        assertNotEquals(CacheKey.of("explain more", "about whales"), CacheKey.of("explain more", "about cars"))
    }

    @Test fun keyIsHex64() {
        assertTrue(CacheKey.of("x question", "").matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun timeSensitiveAndTinyQuestionsAreNotCacheable() {
        assertFalse(CacheKey.cacheable("what is the weather today"))
        assertFalse(CacheKey.cacheable("ok"))
        assertTrue(CacheKey.cacheable("What is a binary tree?"))
    }

    @Test fun contextUsesLastTwoMessages() {
        val c = CacheKey.contextOf(listOf("a", "b", "c"))
        assertFalse(c.contains("a"))
        assertTrue(c.contains("b") && c.contains("c"))
    }

    @Test fun freshnessFollowsTtl() {
        val ttl = CachePolicy.ttlMs(2)
        assertTrue(CachePolicy.fresh(1000, 1000 + ttl, ttl))
        assertFalse(CachePolicy.fresh(1000, 1001 + ttl, ttl))
        assertFalse(CachePolicy.fresh(5000, 1000, ttl))
    }

    @Test fun ttlIsClamped() {
        assertEquals(CachePolicy.HOUR_MS, CachePolicy.ttlMs(0))
        assertEquals(720 * CachePolicy.HOUR_MS, CachePolicy.ttlMs(99999))
    }
}
