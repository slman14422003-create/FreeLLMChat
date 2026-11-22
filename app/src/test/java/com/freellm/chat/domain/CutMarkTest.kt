package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutMarkTest {
    @Test fun addAndDetect() {
        val n = CutMark.add(listOf("note"), "cut", true).joinToString("\n")
        assertTrue(CutMark.has(n))
        assertFalse(CutMark.has("note"))
        assertFalse(CutMark.has(null))
    }

    @Test fun addDoesNothingWhenNotCut() {
        assertEquals(listOf("a"), CutMark.add(listOf("a"), "cut", false))
    }

    @Test fun stripRemovesMarkOnly() {
        val n = "note\n" + CutMark.PREFIX + " cut"
        assertEquals("note", CutMark.strip(n))
        assertNull(CutMark.strip(CutMark.PREFIX + " cut"))
        assertNull(CutMark.strip(null))
    }
}
