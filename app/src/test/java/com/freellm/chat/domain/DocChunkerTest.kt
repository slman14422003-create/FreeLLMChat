package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocChunkerTest {
    private fun doc(): String {
        val filler = (0 until 30).joinToString("\n\n") { i -> "Section $i " + "lorem ipsum dolor sit amet ".repeat(30) }
        return filler + "\n\nThe warranty period for the battery is exactly 24 months from purchase.\n\n" + filler
    }

    @Test fun smallTextIsReturnedWhole() {
        assertEquals("short text", DocChunker.select("short text", "anything", 500))
    }

    @Test fun relevantChunkIsSelected() {
        val out = DocChunker.select(doc(), "what is the battery warranty period", 600)
        assertTrue(out.contains("24 months"))
    }

    @Test fun outputRespectsBudgetApproximately() {
        val out = DocChunker.select(doc(), "battery warranty", 600)
        assertTrue(ContextTrimmer.estimate(out) <= 600 + 60)
    }

    @Test fun genericQuestionFallsBackToDocumentStart() {
        val out = DocChunker.select(doc(), "حلل الملف", 600)
        assertTrue(out.startsWith("Section 0"))
    }

    @Test fun arabicKeywordsMatch() {
        val text = (0 until 40).joinToString("\n\n") { "فقرة عادية رقم $it " + "نص تعبئة ".repeat(40) } +
            "\n\nمدة الضمان للبطارية هي سنتان كاملتان.\n\n" +
            (0 until 40).joinToString("\n\n") { "فقرة أخرى رقم $it " + "نص تعبئة ".repeat(40) }
        val out = DocChunker.select(text, "كم مدة الضمان", 500)
        assertTrue(out.contains("سنتان"))
    }

    @Test fun chunkSplitsLongParagraphs() {
        val pieces = DocChunker.chunk("a".repeat(5000), 1200)
        assertTrue(pieces.size >= 4)
        assertTrue(pieces.all { it.text.length <= 1200 })
    }

    @Test fun gapsAreMarked() {
        val out = DocChunker.select(doc(), "battery warranty", 600)
        assertTrue(out.contains("[…]"))
        assertFalse(out.isBlank())
    }
}
