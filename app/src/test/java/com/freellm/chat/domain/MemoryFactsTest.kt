package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryFactsTest {
    @Test fun memoryBlockRespectsToggleAndCap() {
        assertEquals("", MemoryFacts.block(listOf("a"), false))
        assertEquals("", MemoryFacts.block(emptyList(), true))
        val b = MemoryFacts.block(List(50) { "fact number $it is here" }, true)
        assertTrue(b.length <= MemoryFacts.MAX_CHARS)
        assertTrue(b.contains("- fact number 0"))
    }
}
