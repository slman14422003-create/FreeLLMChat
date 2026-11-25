package com.freellm.chat.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

object TextImport {
    private const val ENTRY_LIMIT = 12L * 1024 * 1024
    private const val MAX_SLIDES = 200

    private val extensions = setOf(
        "txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "css", "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
        "c", "cpp", "cc", "h", "hpp", "cs", "go", "rs", "rb", "php", "swift", "sh", "bat", "sql", "yml", "yaml", "toml",
        "ini", "cfg", "conf", "properties", "gradle", "log", "tex", "srt", "svg"
    )

    fun isTextType(name: String, mime: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in extensions) return true
        return ext.isEmpty() && mime.startsWith("text/") || (ext.isNotEmpty() && mime.startsWith("text/") && ext !in blocked)
    }

    private val blocked = setOf("xlsx", "pptx", "zip", "doc", "docx", "xls", "ppt", "rar", "7z", "apk", "exe")

    fun decode(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        for (name in listOf("UTF-8", "windows-1256")) {
            try {
                return Charset.forName(name).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body)).toString()
            } catch (e: CharacterCodingException) {
            } catch (e: IllegalArgumentException) {
            }
        }
        return String(body, Charsets.ISO_8859_1)
    }

    private val numeric = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")

    fun decodeEntities(s: String): String {
        val n = numeric.replace(s) { m ->
            val g = m.groupValues[1]
            val code = if (g.startsWith("x") || g.startsWith("X")) g.substring(1).toIntOrNull(16) else g.toIntOrNull()
            if (code == null || code <= 0 || code > 0x10FFFF || (code in 0xD800..0xDFFF)) "" else String(Character.toChars(code))
        }
        return n.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    }

    private fun readEntry(z: ZipInputStream): String? {
        val bo = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val r = z.read(buf)
            if (r < 0) break
            total += r
            if (total > ENTRY_LIMIT) return null
            bo.write(buf, 0, r)
        }
        return bo.toString("UTF-8")
    }

    private val tText = Regex("<t(?:\\s[^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val aText = Regex("<a:t(?:\\s[^>]*)?>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL)
    private val si = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    private val slideName = Regex("ppt/slides/slide(\\d+)\\.xml")

    fun xlsxText(input: InputStream): String {
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "xl/sharedStrings.xml") {
                    val xml = readEntry(z) ?: return ""
                    return si.findAll(xml).joinToString("\n") { m ->
                        tText.findAll(m.groupValues[1]).joinToString("") { decodeEntities(it.groupValues[1]) }
                    }.trim()
                }
            }
        }
        return ""
    }

    fun pptxText(input: InputStream): String {
        val slides = java.util.TreeMap<Int, String>()
        ZipInputStream(input).use { z ->
            while (slides.size < MAX_SLIDES) {
                val e = z.nextEntry ?: break
                val m = slideName.matchEntire(e.name) ?: continue
                val xml = readEntry(z) ?: continue
                val n = m.groupValues[1].toIntOrNull() ?: continue
                slides[n] = aText.findAll(xml).joinToString("\n") { decodeEntities(it.groupValues[1]) }
            }
        }
        return slides.entries.joinToString("\n\n") { "Slide " + it.key + "\n" + it.value }.trim()
    }

    fun docxText(xml: String): String =
        decodeEntities(
            xml.replace("</w:p>", "\n").replace("<w:tab/>", "\t").replace("<w:br/>", "\n").replace(Regex("<[^>]+>"), "")
        )
}
