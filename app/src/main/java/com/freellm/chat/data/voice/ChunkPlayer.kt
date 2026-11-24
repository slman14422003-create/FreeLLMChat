package com.freellm.chat.data.voice

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class ChunkPlayer(
    private val onChunk: (Int) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var gen = 0
    private var cur: MediaPlayer? = null
    private var curIndex = -1
    private var curFinished = false
    private var next: MediaPlayer? = null
    private var nextIndex = -1
    private var nextReady = false
    private var provider: (suspend (Int) -> File)? = null
    private var total = 0
    private var fetchJob: Job? = null
    private var pendingError: String? = null

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun start(files: suspend (Int) -> File, count: Int) {
        onMain {
            clear()
            val g = ++gen
            provider = files
            total = count
            if (count <= 0) {
                onDone()
                return@onMain
            }
            fetch(0, g)
        }
    }

    fun stop() {
        onMain {
            gen++
            clear()
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun clear() {
        fetchJob?.cancel()
        fetchJob = null
        val a = cur
        val b = next
        cur = null
        next = null
        curIndex = -1
        nextIndex = -1
        curFinished = false
        nextReady = false
        pendingError = null
        provider = null
        total = 0
        dispose(a)
        dispose(b)
    }

    private fun dispose(mp: MediaPlayer?) {
        if (mp == null) return
        mp.setOnPreparedListener(null)
        mp.setOnCompletionListener(null)
        mp.setOnErrorListener(null)
        main.post {
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
    }

    private fun fetch(index: Int, g: Int) {
        val p = provider ?: return
        fetchJob = scope.launch {
            val file = try {
                p(index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = e.message.orEmpty()
                main.post { if (g == gen) fetchFailed(msg) }
                return@launch
            }
            main.post { if (g == gen) onFile(index, file, g) }
        }
    }

    private fun fetchFailed(message: String) {
        if (cur != null && !curFinished) {
            pendingError = message
            return
        }
        failNow(message)
    }

    private fun failNow(message: String) {
        gen++
        clear()
        onError(message)
    }

    private fun build(file: File, index: Int, g: Int): MediaPlayer? {
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
        } catch (e: Throwable) {
            runCatching { mp.release() }
            failNow(e.message.orEmpty())
            return null
        }
        mp.setOnErrorListener { _, what, extra ->
            if (g == gen) failNow("media $what $extra")
            true
        }
        mp.setOnCompletionListener {
            if (g == gen && index == curIndex) completed(g)
        }
        return mp
    }

    private fun onFile(index: Int, file: File, g: Int) {
        val mp = build(file, index, g) ?: return
        if (index == 0 && cur == null) {
            cur = mp
            curIndex = 0
            curFinished = false
            mp.setOnPreparedListener {
                if (g == gen && cur === mp) {
                    mp.start()
                    started(0, g)
                }
            }
            mp.prepareAsync()
            return
        }
        next = mp
        nextIndex = index
        nextReady = false
        mp.setOnPreparedListener {
            if (g == gen && next === mp) {
                nextReady = true
                if (curFinished) advance(g)
            }
        }
        mp.prepareAsync()
    }

    private fun started(index: Int, g: Int) {
        onChunk(index)
        if (g == gen && index + 1 < total) fetch(index + 1, g)
    }

    private fun completed(g: Int) {
        curFinished = true
        val err = pendingError
        if (err != null) {
            failNow(err)
            return
        }
        if (nextReady && next != null) {
            advance(g)
            return
        }
        if (curIndex + 1 >= total) {
            gen++
            clear()
            onDone()
        }
    }

    private fun advance(g: Int) {
        val old = cur
        val mp = next ?: return
        cur = mp
        curIndex = nextIndex
        next = null
        nextIndex = -1
        nextReady = false
        curFinished = false
        dispose(old)
        mp.start()
        started(curIndex, g)
    }
}
