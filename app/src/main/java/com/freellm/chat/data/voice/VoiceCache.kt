package com.freellm.chat.data.voice

import java.io.File
import java.security.MessageDigest

class VoiceCache(
    private val dir: File,
    private val capBytes: Long = 300L * 1024 * 1024,
    private val trimToBytes: Long = 225L * 1024 * 1024
) {
    private val lock = Any()

    init {
        dir.mkdirs()
    }

    fun key(engine: String, voice: String, text: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("$engine|$voice|$text".toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    fun get(key: String, ext: String): File? {
        val f = File(dir, "$key.$ext")
        if (!f.isFile) return null
        f.setLastModified(System.currentTimeMillis())
        return f
    }

    fun put(key: String, ext: String, bytes: ByteArray): File = synchronized(lock) {
        dir.mkdirs()
        val part = File(dir, "$key.$ext.part")
        val target = File(dir, "$key.$ext")
        part.writeBytes(bytes)
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            target.writeBytes(bytes)
            part.delete()
        }
        trim(target)
        target
    }

    private fun trim(keep: File) {
        if (size() <= capBytes) return
        val files = (dir.listFiles() ?: emptyArray()).filter { it.isFile && !it.name.endsWith(".part") }.sortedBy { it.lastModified() }
        var total = size()
        for (f in files) {
            if (total <= trimToBytes) break
            if (f.absolutePath == keep.absolutePath) continue
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    fun size(): Long = (dir.listFiles() ?: emptyArray()).filter { it.isFile }.sumOf { it.length() }

    fun clear() {
        synchronized(lock) {
            (dir.listFiles() ?: emptyArray()).forEach { it.delete() }
            dir.mkdirs()
        }
    }
}
