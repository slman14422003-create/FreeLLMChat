package com.freellm.chat.data

import com.freellm.chat.domain.CodeFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ZipExportTest {
    @Test fun roundTripKeepsPathsAndContent() {
        val out = ByteArrayOutputStream()
        val n = ZipExport.write(listOf(CodeFile("a/b.txt", "héllo\n"), CodeFile("c.kt", "x\n"), CodeFile("a/b.txt", "dup")), out)
        assertEquals(3, n)
        val got = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                got[e.name] = String(z.readBytes(), Charsets.UTF_8)
            }
        }
        assertEquals(mapOf("a/b.txt" to "héllo\n", "c.kt" to "x\n", "a/b-2.txt" to "dup"), got)
    }

    @Test fun unsafePathsSkippedAndAttachmentsCopied() {
        val f = java.io.File.createTempFile("att", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3)); deleteOnExit() }
        val out = ByteArrayOutputStream()
        val n = ZipExport.write(
            listOf(CodeFile("../x.txt", "no"), CodeFile("img/a.png", "", "id1"), CodeFile("gone.png", "", "missing")),
            out
        ) { id -> if (id == "id1") f else null }
        assertEquals(1, n)
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            val e = z.nextEntry!!
            assertEquals("img/a.png", e.name)
            assertEquals(listOf<Byte>(1, 2, 3), z.readBytes().toList())
        }
    }
}
