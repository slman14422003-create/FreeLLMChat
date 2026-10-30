package com.freellm.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StableSplitTest {
    @Test fun splitsAtTheLastBlankLine() {
        val s = "one\n\ntwo\n\nthree"
        assertEquals("one\n\ntwo\n\n", s.substring(0, stableSplit(s)))
    }

    @Test fun neverCutsInsideAFence() {
        assertEquals(0, stableSplit("intro\n\n```kotlin\nval a = 1\n\nval b = 2"))
    }

    @Test fun keepsAFileTitleWithItsCode() {
        assertEquals(3, stableSplit("a\n\n**main.kt**\n\n```kt\nx\n```"))
    }

    @Test fun plainTextWithoutBlankLinesStaysInTheTail() {
        assertEquals(0, stableSplit("just one paragraph"))
    }
}
