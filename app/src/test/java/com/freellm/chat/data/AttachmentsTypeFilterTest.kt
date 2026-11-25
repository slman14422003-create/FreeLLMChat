package com.freellm.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AttachmentsTypeFilterTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            entries.forEach { (n, c) ->
                z.putNextEntry(ZipEntry(n))
                z.write(c.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return ByteArrayInputStream(bo.toByteArray())
    }

    @Test fun knownTextTypesAreAccepted() {
        listOf("a.txt", "notes.md", "data.csv", "x.json", "p.xml", "i.html", "A.kt", "B.java", "c.py", "d.js", "e.ts", "f.c", "g.cpp", "h.h", "q.sql", "c.yml", "run.log")
            .forEach { assertTrue(it, TextImport.isTextType(it, "application/octet-stream")) }
        assertTrue(TextImport.isTextType("readme", "text/plain"))
    }

    @Test fun binaryAndOfficeTypesAreRejected() {
        listOf("a.xlsx", "b.pptx", "c.zip", "d.doc", "e.docx", "f.exe", "g.bin", "h.apk")
            .forEach { assertFalse(it, TextImport.isTextType(it, "application/octet-stream")) }
        assertFalse(TextImport.isTextType("a.zip", "text/plain"))
    }

    @Test fun utf8IsDecodedStrictly() {
        assertEquals("h\u00e9llo", TextImport.decode("h\u00e9llo".toByteArray(Charsets.UTF_8)))
    }

    @Test fun windows1256IsUsedWhenUtf8Fails() {
        val s = "\u0645\u0631\u062d\u0628\u0627"
        assertEquals(s, TextImport.decode(s.toByteArray(java.nio.charset.Charset.forName("windows-1256"))))
    }

    @Test fun latin1FallbackKeepsBytes() {
        assertEquals("caf\u00e9", TextImport.decode(byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte())))
    }

    @Test fun bomIsDropped() {
        assertEquals("hi", TextImport.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x68, 0x69)))
    }

    @Test fun numericEntitiesAreDecoded() {
        assertEquals("a A B \u0645 & <", TextImport.decodeEntities("a &#65; &#x42; &#1605; &amp; &lt;"))
        assertEquals("", TextImport.decodeEntities("&#0;&#xD800;"))
    }

    @Test fun docxXmlBecomesText() {
        val xml = "<w:p><w:r><w:t>One &#38; two</w:t></w:r></w:p><w:p><w:r><w:t>Three</w:t></w:r></w:p>"
        assertEquals("One & two\nThree\n", TextImport.docxText(xml))
    }

    @Test fun xlsxSharedStringsAreExtracted() {
        val z = zip("xl/sharedStrings.xml" to "<sst><si><t>Hello</t></si><si><t xml:space=\"preserve\">W&#246;rld</t></si></sst>")
        assertEquals("Hello\nW\u00f6rld", TextImport.xlsxText(z))
    }

    @Test fun pptxSlidesAreExtractedInOrder() {
        val z = zip(
            "ppt/slides/slide2.xml" to "<p><a:t>B</a:t></p>",
            "ppt/slides/slide1.xml" to "<p><a:t>A</a:t></p>",
            "ppt/slideLayouts/slideLayout1.xml" to "<a:t>ignored</a:t>"
        )
        assertEquals("Slide 1\nA\n\nSlide 2\nB", TextImport.pptxText(z))
    }

    @Test fun missingEntriesGiveEmptyText() {
        assertEquals("", TextImport.xlsxText(zip("other.xml" to "x")))
        assertEquals("", TextImport.pptxText(zip("other.xml" to "x")))
    }
}
