package com.freellm.chat.data

import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    @Volatile private var file: File? = null

    val guard = CoroutineExceptionHandler { _, e -> note(e) }

    fun init(dir: File) {
        file = File(dir, "last_crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write("FATAL", e)
            if (previous != null) previous.uncaughtException(t, e)
        }
    }

    fun note(e: Throwable) {
        write("HANDLED", e)
    }

    private fun write(kind: String, e: Throwable) {
        val f = file ?: return
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val entry = kind + " " + stamp + "\n" + e.stackTraceToString().take(3500) + "\n\n"
            val old = if (f.exists()) f.readText() else ""
            val all = entry + old
            f.writeText(all.take(MAX_CHARS))
        }
    }

    fun read(): String? {
        val f = file ?: return null
        return runCatching { if (f.exists()) f.readText().ifBlank { null } else null }.getOrNull()
    }

    fun clear() {
        runCatching { file?.delete() }
    }

    private const val MAX_CHARS = 12_000
}
