package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheAndMemoryTest {
    @Test fun normalizeIgnoresCaseSpacesAndTrailingPunctuation() {
        assertEquals(CacheKey.hash("What is  Kotlin?"), CacheKey.hash("what is kotlin"))
    }
    @Test fun personalToolAndLongPromptsAreNotCacheable() {
        assertTrue(CacheKey.cacheable("What is Kotlin?", false, false))
        assertFalse(CacheKey.cacheable("What is my balance", false, false))
        assertFalse(CacheKey.cacheable("What is Kotlin", true, false))
        assertFalse(CacheKey.cacheable("What is Kotlin", false, true))
        assertFalse(CacheKey.cacheable("x".repeat(201), false, false))
    }
    @Test fun ttlIs24Hours() {
        assertTrue(CacheKey.fresh(0, CacheKey.TTL_MS - 1))
        assertFalse(CacheKey.fresh(0, CacheKey.TTL_MS))
    }
    @Test fun memoryBlockRespectsToggleAndCap() {
        assertEquals("", MemoryFacts.block(listOf("a"), false))
        assertEquals("", MemoryFacts.block(emptyList(), true))
        val b = MemoryFacts.block(List(50) { "fact number $it is here" }, true)
        assertTrue(b.length <= MemoryFacts.MAX_CHARS)
        assertTrue(b.contains("- fact number 0"))
    }
}
