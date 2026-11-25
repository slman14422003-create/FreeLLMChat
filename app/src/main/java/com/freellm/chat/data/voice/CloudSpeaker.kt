package com.freellm.chat.data.voice

import com.freellm.chat.FreeLlmApp
import com.freellm.chat.domain.TextChunker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class CloudSpeaker(private val app: FreeLlmApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)

    private class Session(
        val chunks: List<String>,
        val engine: CloudTts,
        val window: Int,
        val onFirstChunk: () -> Unit,
        val onDone: () -> Unit,
        val onError: (TtsException, Int) -> Unit
    ) {
        val jobs = ConcurrentHashMap<Int, Deferred<File>>()
        val errored = AtomicBoolean(false)
        @Volatile var failure: Pair<TtsException, Int>? = null
        @Volatile var current = 0
    }

    @Volatile private var session: Session? = null

    private val player = ChunkPlayer(
        onChunk = { i -> chunkStarted(i) },
        onDone = { finished() },
        onError = { msg -> playerFailed(msg) }
    )

    fun speak(
        text: String,
        engine: CloudTts,
        window: Int,
        onFirstChunk: () -> Unit,
        onDone: () -> Unit,
        onError: (TtsException, Int) -> Unit
    ) {
        stop()
        val chunks = TextChunker.chunks(text, engine.maxChars)
        if (chunks.isEmpty()) {
            onDone()
            return
        }
        val s = Session(chunks, engine, window.coerceAtLeast(1), onFirstChunk, onDone, onError)
        session = s
        for (i in 0..minOf(s.window, chunks.lastIndex)) ensure(s, i)
        player.start({ i -> await(s, i) }, chunks.size)
    }

    fun stop() {
        val s = session
        session = null
        player.stop()
        s?.jobs?.values?.forEach { it.cancel() }
        s?.jobs?.clear()
    }

    private fun ensure(s: Session, i: Int): Deferred<File> {
        s.jobs[i]?.let { return it }
        synchronized(s) {
            s.jobs[i]?.let { return it }
            val d = scope.async {
                gate.withPermit {
                    val chunk = s.chunks[i]
                    val voice = s.engine.voiceFor(chunk)
                    val key = app.voiceCache.key(s.engine.id, voice, chunk)
                    (listOf(s.engine.ext) + s.engine.altExts).firstNotNullOfOrNull { app.voiceCache.get(key, it) }
                        ?: s.engine.synthExt(chunk).let { (bytes, e) -> app.voiceCache.put(key, e, bytes) }
                }
            }
            s.jobs[i] = d
            return d
        }
    }

    private suspend fun await(s: Session, i: Int): File {
        try {
            return ensure(s, i).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val t = e as? TtsException ?: TtsException(0, e.message.orEmpty())
            if (s.failure == null) s.failure = t to i
            throw t
        }
    }

    private fun chunkStarted(i: Int) {
        val s = session ?: return
        s.current = i
        if (i == 0) s.onFirstChunk()
        for (j in (i + 1)..minOf(i + s.window, s.chunks.lastIndex)) ensure(s, j)
    }

    private fun finished() {
        val s = session ?: return
        session = null
        s.jobs.clear()
        s.onDone()
    }

    private fun playerFailed(message: String) {
        val s = session ?: return
        session = null
        s.jobs.values.forEach { it.cancel() }
        s.jobs.clear()
        if (!s.errored.compareAndSet(false, true)) return
        val f = s.failure ?: (TtsException(0, message) to s.current)
        s.onError(f.first, f.second)
    }
}
