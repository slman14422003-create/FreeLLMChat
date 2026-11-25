package com.freellm.chat.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzureTtsTest {
    @Test fun ssmlEscapesAndSetsLang() {
        val s = AzureTts.buildSsml("a & b < c", "ar-SY-AmanyNeural")
        assertTrue(s.contains("a &amp; b &lt; c"))
        assertTrue(s.contains("xml:lang='ar-SY'"))
        assertTrue(s.contains("<voice name='ar-SY-AmanyNeural'>"))
    }

    @Test fun escapeHandlesQuotes() {
        assertEquals("&quot;x&quot; &apos;y&apos; &gt;", AzureTts.escape("\"x\" 'y' >"))
    }
}
