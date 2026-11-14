package com.freellm.chat.data

import com.freellm.chat.domain.CodeFile
import com.freellm.chat.domain.CodeFiles
import java.io.File
import java.io.IOException

data class WsFile(val path: String, val size: Long)

class Workspace(private val root: File) {
    private fun dir(cid: Long): File = File(root, cid.toString())

    private fun resolve(cid: Long, path: String): File? {
        val safe = CodeFiles.safePath(path) ?: return null
        val base = dir(cid).canonicalFile
        val f = File(base, safe).canonicalFile
        return if (f.path.startsWith(base.path + File.separator)) f else null
    }

    private fun filesOf(cid: Long): List<File> {
        val d = dir(cid)
        if (!d.isDirectory) return emptyList()
        return d.walkTopDown().filter { it.isFile }.toList()
    }

    fun write(cid: Long, path: String, content: String): String? {
        val f = resolve(cid, path) ?: return "Invalid path. Use a relative path with a file extension, like src/main.py."
        if (content.length > MAX_FILE_CHARS) return "File too large (limit " + MAX_FILE_CHARS + " characters)."
        val existing = filesOf(cid)
        val isNew = !f.exists()
        if (isNew && existing.size >= CodeFiles.MAX_FILES) return "Too many files (limit " + CodeFiles.MAX_FILES + ")."
        val total = existing.sumOf { it.length() } - (if (isNew) 0L else f.length())
        if (total + content.length > MAX_TOTAL_BYTES) return "Project is too large."
        return try {
            f.parentFile?.mkdirs()
            f.writeText(content, Charsets.UTF_8)
            null
        } catch (e: IOException) {
            "Could not write file: " + (e.message ?: "io error").take(80)
        }
    }

    fun read(cid: Long, path: String): String? {
        val f = resolve(cid, path) ?: return null
        return if (f.isFile) try { f.readText(Charsets.UTF_8) } catch (e: IOException) { null } else null
    }

    fun delete(cid: Long, path: String): Boolean {
        val f = resolve(cid, path) ?: return false
        return f.isFile && f.delete()
    }

    fun list(cid: Long): List<WsFile> {
        val base = dir(cid)
        return filesOf(cid).map { WsFile(it.relativeTo(base).invariantSeparatorsPath, it.length()) }.sortedBy { it.path }
    }

    fun files(cid: Long): List<CodeFile> {
        val base = dir(cid)
        return filesOf(cid).sortedBy { it.relativeTo(base).invariantSeparatorsPath }.mapNotNull { f ->
            try {
                CodeFile(f.relativeTo(base).invariantSeparatorsPath, f.readText(Charsets.UTF_8))
            } catch (e: IOException) {
                null
            }
        }
    }

    fun count(cid: Long): Int = filesOf(cid).size

    fun clear(cid: Long) {
        dir(cid).deleteRecursively()
    }

    fun clearAll() {
        root.deleteRecursively()
    }

    fun totalBytes(): Long = if (root.isDirectory) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L

    fun totalFiles(): Int = if (root.isDirectory) root.walkTopDown().count { it.isFile } else 0

    companion object {
        const val MAX_FILE_CHARS = 300_000
        const val MAX_TOTAL_BYTES = 8_000_000L
    }
}
