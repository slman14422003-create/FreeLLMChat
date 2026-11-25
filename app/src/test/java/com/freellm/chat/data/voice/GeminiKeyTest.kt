package com.freellm.chat.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiKeyTest {
    @Test
    fun singleKeyIsKept() {
        assertEquals("AIzaKey1", GeminiTts.sanitizeKey("AIzaKey1"))
    }

    @Test
    fun firstOfTwoKeysOnNewline() {
        assertEquals("AIzaKey1", GeminiTts.sanitizeKey("AIzaKey1\nAIzaKey2"))
    }

    @Test
    fun keyWithSpacesIsTrimmed() {
        assertEquals("AIzaKey1", GeminiTts.sanitizeKey("  \n AIzaKey1  AIzaKey2 "))
        assertEquals("AIzaKey1", GeminiTts.sanitizeKey("AIzaKey1,AIzaKey2"))
        assertEquals("", GeminiTts.sanitizeKey("  \n "))
    }
}
