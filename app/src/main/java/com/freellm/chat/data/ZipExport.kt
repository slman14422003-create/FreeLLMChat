package com.freellm.chat.data

import com.freellm.chat.domain.CodeFile
import com.freellm.chat.domain.CodeFiles
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipExport {
    fun write(files: List<CodeFile>, out: OutputStream, resolve: (String) -> File? = { null }): Int {
        var n = 0
        ZipOutputStream(out.buffered(), Charsets.UTF_8).use { zip ->
            val seen = HashSet<String>()
            for (f in files) {
                val safe = CodeFiles.safePath(f.path) ?: continue
                val bin = f.attachId?.let(resolve)?.takeIf { it.isFile }
                if (f.attachId != null && bin == null) continue
                val name = CodeFiles.uniquePath(safe, seen)
                seen.add(name)
                zip.putNextEntry(ZipEntry(name))
                if (bin != null) bin.inputStream().use { it.copyTo(zip) } else zip.write(f.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                n++
            }
        }
        return n
    }
}
