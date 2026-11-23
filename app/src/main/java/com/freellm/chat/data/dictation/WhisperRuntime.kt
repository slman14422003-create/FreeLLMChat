package com.freellm.chat.data.dictation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object WhisperRuntime {
    private const val IDLE_MS = 90_000L
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var handle = 0L
    private var loadedPath: String? = null
    private var idleJob: Job? = null

    fun threads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    fun decode(bytes: ByteArray): String = String(bytes, Charsets.UTF_8).replace("\uFFFD", "").trim()

    suspend fun transcribe(model: File, samples: FloatArray, lang: String): String? = lock.withLock {
        idleJob?.cancel()
        if (!WhisperNative.available() || !model.isFile) return@withLock null
        if (loadedPath != model.absolutePath || handle == 0L) {
            releaseLocked()
            handle = WhisperNative.initContext(model.absolutePath)
            loadedPath = if (handle != 0L) model.absolutePath else null
        }
        if (handle == 0L) return@withLock null
        val out = decode(WhisperNative.transcribe(handle, samples, lang, threads()))
        idleJob = scope.launch {
            delay(IDLE_MS)
            lock.withLock { releaseLocked() }
        }
        out
    }

    suspend fun release() = lock.withLock { releaseLocked() }

    private fun releaseLocked() {
        if (handle != 0L) WhisperNative.freeContext(handle)
        handle = 0L
        loadedPath = null
    }
}
