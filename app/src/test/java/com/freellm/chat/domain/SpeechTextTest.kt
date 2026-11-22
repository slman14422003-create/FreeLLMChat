package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test fun codeBlocksAreSkipped() {
        val t = "Intro\n```kotlin\nval x = 1\n```\nOutro"
        assertEquals("Intro Outro", SpeechText.plain(t))
    }

    @Test fun unclosedCodeBlockIsDropped() {
        assertEquals("Hi", SpeechText.plain("Hi\n```py\nprint(1)"))
    }

    @Test fun linksKeepTheirLabelAndBareUrlsGo() {
        assertEquals("see docs and more", SpeechText.plain("see [docs](https://a.com/x) and https://b.com more"))
    }

    @Test fun markdownMarksAreRemoved() {
        assertEquals("Title bold item", SpeechText.plain("# Title\n**bold** - item".replace("- ", "")))
    }

    @Test fun lengthIsCapped() {
        assertEquals(SpeechText.MAX, SpeechText.plain("a ".repeat(5000).replace(" ", "ab")).length)
    }

    @Test fun arabicIsDetected() {
        assertTrue(SpeechText.isArabic("مرحبا"))
        assertFalse(SpeechText.isArabic("hello"))
    }
}
