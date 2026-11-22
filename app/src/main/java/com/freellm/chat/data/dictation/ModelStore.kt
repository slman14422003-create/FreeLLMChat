package com.freellm.chat.data.dictation

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

enum class ModelLevel(val fileName: String, val approxBytes: Long, val minRamMb: Int) {
    FAST("ggml-tiny.bin", 75L shl 20, 0),
    BALANCED("ggml-base.bin", 140L shl 20, 0),
    ACCURATE("ggml-small.bin", 460L shl 20, 0),
    MOST_ACCURATE("ggml-medium.bin", 1500L shl 20, 6144)
}

// Pure logic for resumable downloads and verification. Network and checksum values live elsewhere.
object ModelStore {
    const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
    private const val SPACE_MARGIN = 50L shl 20

    fun url(level: ModelLevel): String = BASE_URL + level.fileName

    fun resumeOffset(partial: File): Long = if (partial.isFile) partial.length() else 0L

    fun rangeHeader(offset: Long): String? = if (offset > 0) "bytes=$offset-" else null

    // 206 appends, 200 restarts from zero, anything else is an error.
    fun responseAction(code: Int): String? = when (code) {
        206 -> "append"
        200 -> "restart"
        else -> null
    }

    fun hasSpace(freeBytes: Long, totalBytes: Long, alreadyHave: Long): Boolean =
        freeBytes >= (totalBytes - alreadyHave) + SPACE_MARGIN

    fun needsRamWarning(level: ModelLevel, deviceRamMb: Int): Boolean = deviceRamMb < level.minRamMb

    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val b = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(b)
            if (n < 0) break
            md.update(b, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedHex: String): Boolean =
        file.isFile && file.inputStream().use { sha256(it) }.equals(expectedHex.trim(), ignoreCase = true)
}
