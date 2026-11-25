package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    @Test fun shortSentencesStayWithinLimitAndKeepContent() {
        val text = "Hello there. ".repeat(80).trim().take(1000)
        val chunks = TextChunker.chunks(text, 280)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= 280 })
        assertEquals(text.filter { !it.isWhitespace() }, chunks.joinToString("").filter { !it.isWhitespace() })
    }

    @Test fun longSentenceWithoutSpacesIsHardCut() {
        val text = "a".repeat(700)
        val chunks = TextChunker.chunks(text, 280)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.length <= 280 })
        assertEquals(text, chunks.joinToString(""))
    }

    @Test fun emptyTextGivesEmptyList() {
        assertTrue(TextChunker.chunks("", 280).isEmpty())
        assertTrue(TextChunker.chunks("   ", 280).isEmpty())
    }
}
