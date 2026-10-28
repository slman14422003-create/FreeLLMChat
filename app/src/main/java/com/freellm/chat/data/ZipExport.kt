package com.freellm.chat.data

import com.freellm.chat.domain.CodeFile
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipExport {
    fun write(files: List<CodeFile>, out: OutputStream): Int {
        var n = 0
        ZipOutputStream(out.buffered(), Charsets.UTF_8).use { zip ->
            val seen = HashSet<String>()
            for (f in files) {
                if (!seen.add(f.path)) continue
                zip.putNextEntry(ZipEntry(f.path))
                zip.write(f.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                n++
            }
        }
        return n
    }
}
