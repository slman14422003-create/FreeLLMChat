package com.freellm.chat.data

import android.content.Context
import com.freellm.chat.domain.BrainEntry
import com.freellm.chat.domain.LocalBrain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class BrainFile(val version: Int = 1, val entries: List<StoredEntry> = emptyList())

@Serializable
private data class StoredEntry(
    val id: Long,
    val q: String,
    val a: String,
    val t: Long,
    val s: Int = 1,
    val h: Int = 0
)

/** Persists the learned Q&A pairs in a private JSON file (nothing leaves the device). */
class BrainStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "local_brain.json")
    private val json = Json { ignoreUnknownKeys = true }
    val brain: LocalBrain by lazy { LocalBrain(load()) }

    private fun load(): List<BrainEntry> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<BrainFile>(file.readText()).entries
            .map { BrainEntry(it.id, it.q, it.a, it.t, it.s, it.h) }
    }.getOrDefault(emptyList())

    private val saveLock = Mutex()

    suspend fun save() = saveLock.withLock { saveNow() }

    private suspend fun saveNow() = withContext(Dispatchers.IO) {
        runCatching {
            val data = BrainFile(entries = brain.all().map { StoredEntry(it.id, it.question, it.answer, it.createdAt, it.score, it.hits) })
            val tmp = File(file.parentFile, "local_brain.tmp")
            tmp.writeText(json.encodeToString(data))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }
}
