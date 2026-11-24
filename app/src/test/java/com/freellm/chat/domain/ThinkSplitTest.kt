package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkSplitTest {
    @Test
    fun plainTextPassesThrough() {
        val p = ThinkSplit.split("hello")
        assertEquals("hello", p.answer)
        assertEquals("", p.thoughts)
        assertFalse(p.thinking)
    }

    @Test
    fun closedBlockIsSeparated() {
        val p = ThinkSplit.split("<think>step one\n\nstep two</think>\nanswer")
        assertEquals("answer", p.answer)
        assertEquals(2, ThinkSplit.steps(p.thoughts).size)
        assertFalse(p.thinking)
    }

    @Test
    fun openBlockMeansThinking() {
        val p = ThinkSplit.split("<think>still going")
        assertTrue(p.thinking)
        assertEquals("", p.answer)
        assertEquals("still going", p.thoughts)
    }

    @Test
    fun partialTagIsHidden() {
        assertEquals("abc", ThinkSplit.split("abc<thi").answer)
    }

    @Test
    fun zipCommandIsParsedAndStripped() {
        val text = "File: a.txt\n```\nx\n```\n[[EXPORT_ZIP:my-app.zip]]"
        assertEquals("myapp.zip", CodeFiles.zipCommand("[[EXPORT_ZIP:myapp]]"))
        assertEquals("project.zip", CodeFiles.zipCommand("[[EXPORT_ZIP]]"))
        assertNull(CodeFiles.zipCommand("no command"))
        assertFalse(CodeFiles.stripCommand(text).contains("EXPORT_ZIP"))
        assertTrue(CodeFiles.stripCommand("done [[EXPORT_Z").endsWith("done"))
    }

    @Test
    fun wantsZipDetectsRequests() {
        assertTrue(CodeFiles.wantsZip("send it as a zip"))
        assertFalse(CodeFiles.wantsZip("explain recursion"))
    }

    @Test
    fun extractIgnoresThinking() {
        val files = CodeFiles.extract("<think>File: x.kt\n```\nfake\n```</think>\nFile: a.kt\n```\nreal\n```")
        assertEquals(listOf("a.kt"), files.map { it.path })
    }
}
