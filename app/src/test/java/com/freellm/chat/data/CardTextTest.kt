package com.freellm.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextTest {
    @Test fun shortTextIsKept() {
        assertEquals("hello world", CardText.fit("hello world"))
    }

    @Test fun markdownIsStripped() {
        val t = CardText.clean("## Title\n\n**bold** text\n```kotlin\nval a = 1\n```")
        assertFalse(t.contains("#"))
        assertFalse(t.contains("**"))
        assertFalse(t.contains("```"))
        assertTrue(t.contains("bold text"))
    }

    @Test fun longTextCutsAtSentenceBoundary() {
        val s = "First sentence is here. " + "word ".repeat(40) + "end. " + "tail ".repeat(100)
        val out = CardText.fit(s, 300)
        assertTrue(out.length <= 301)
        assertTrue(out.endsWith("…"))
        assertTrue(out.startsWith("First sentence is here."))
    }

    @Test fun longTextWithoutPunctuationCutsAtWord() {
        val out = CardText.fit("abc ".repeat(200), 100)
        assertTrue(out.endsWith("…"))
        assertFalse(out.dropLast(1).endsWith(" "))
        assertTrue(out.length <= 101)
    }

    @Test fun hostStripsSchemeAndWww() {
        assertEquals("example.com", CardText.host("https://www.example.com/a/b?c=1"))
    }
}
