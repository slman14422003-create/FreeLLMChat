package com.freellm.chat.data.dictation

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

enum class DownloadResult { OK, NO_SPACE, FAILED, BAD_CHECKSUM }

data class DownloadProgress(val level: ModelLevel, val done: Long, val total: Long)

class WhisperModels(private val ctx: Context, http: OkHttpClient) {
    private val dl = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private var job: Job? = null

    val progress = MutableStateFlow<DownloadProgress?>(null)
    val result = MutableStateFlow<DownloadResult?>(null)

    fun startDownload(scope: CoroutineScope, level: ModelLevel) {
        if (job?.isActive == true) return
        job = scope.launch {
            progress.value = DownloadProgress(level, partialBytes(level), level.approxBytes)
            try {
                val r = download(level) { d, t -> progress.value = DownloadProgress(level, d, t) }
                result.value = r
            } finally {
                progress.value = null
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    private data class Meta(val sha: String?, val size: Long?)

    val dir: File get() = File(ctx.filesDir, "whisper").also { it.mkdirs() }

    fun file(level: ModelLevel) = File(dir, level.fileName)

    fun customFile() = File(dir, "custom.bin")

    fun installed(level: ModelLevel) = file(level).let { it.isFile && it.length() > 0 }

    fun hasCustom() = customFile().let { it.isFile && it.length() > 0 }

    fun partialBytes(level: ModelLevel): Long = ModelStore.resumeOffset(File(dir, level.fileName + ".part"))

    fun installedNames(): List<String> =
        ModelLevel.values().filter { installed(it) }.map { it.name } + if (hasCustom()) listOf(ModelStore.CUSTOM) else emptyList()

    fun anyInstalled(): Boolean = installedNames().isNotEmpty()

    fun resolve(selected: String): File? {
        val name = ModelStore.pick(selected, installedNames()) ?: return null
        return if (name == ModelStore.CUSTOM) customFile() else ModelStore.levelOf(name)?.let { file(it) }
    }

    fun delete(level: ModelLevel) {
        file(level).delete()
        File(dir, level.fileName + ".part").delete()
    }

    fun deleteCustom() {
        customFile().delete()
    }

    suspend fun importCustom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(dir, "custom.part")
        val ok = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            } ?: return@runCatching false
            val head = ByteArray(4)
            val n = tmp.inputStream().use { it.read(head) }
            n == 4 && ModelStore.isGgml(head)
        }.getOrDefault(false)
        if (ok) {
            customFile().delete()
            tmp.renameTo(customFile())
        } else tmp.delete()
        ok
    }

    suspend fun download(level: ModelLevel, onProgress: (Long, Long) -> Unit): DownloadResult = withContext(Dispatchers.IO) {
        var call: okhttp3.Call? = null
        val job = coroutineContext[Job]
        val reg = job?.invokeOnCompletion { if (it != null) call?.cancel() }
        try {
            fetch(level, onProgress, { c -> call = c }, { job?.isActive != false })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DownloadResult.FAILED
        } finally {
            reg?.dispose()
        }
    }

    private fun meta(url: String): Meta? = runCatching {
        val c = dl.newBuilder().followRedirects(false).followSslRedirects(false).build()
        c.newCall(Request.Builder().url(url).head().build()).execute().use { r ->
            Meta(ModelStore.parseLinkedEtag(r.header("X-Linked-Etag")), r.header("X-Linked-Size")?.toLongOrNull())
        }
    }.getOrNull()

    private fun fetch(
        level: ModelLevel,
        onProgress: (Long, Long) -> Unit,
        register: (okhttp3.Call) -> Unit,
        alive: () -> Boolean
    ): DownloadResult {
        val url = ModelStore.url(level)
        val part = File(dir, level.fileName + ".part")
        val target = file(level)
        val meta = meta(url)
        val expected = meta?.size ?: level.approxBytes
        var offset = ModelStore.resumeOffset(part)
        if (!ModelStore.hasSpace(dir.usableSpace, expected, offset)) return DownloadResult.NO_SPACE
        if (meta?.size != null && offset == meta.size) return finish(part, target, meta)

        val rb = Request.Builder().url(url)
        ModelStore.rangeHeader(offset)?.let { rb.header("Range", it) }
        val call = dl.newCall(rb.build())
        register(call)
        call.execute().use { resp ->
            if (resp.code == 416) {
                part.delete()
                return DownloadResult.FAILED
            }
            val action = ModelStore.responseAction(resp.code) ?: return DownloadResult.FAILED
            if (action == "restart") offset = 0
            val body = resp.body ?: return DownloadResult.FAILED
            val len = body.contentLength()
            val total = if (len > 0) offset + len else expected
            var done = offset
            var last = 0L
            FileOutputStream(part, action == "append").use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (!alive()) return DownloadResult.FAILED
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 250) {
                            onProgress(done, total)
                            last = now
                        }
                    }
                }
            }
            onProgress(done, total)
            if (len > 0 && done != total) return DownloadResult.FAILED
        }
        return finish(part, target, meta)
    }

    private fun finish(part: File, target: File, meta: Meta?): DownloadResult {
        if (meta?.size != null && part.length() != meta.size) {
            part.delete()
            return DownloadResult.FAILED
        }
        if (meta?.sha != null && !ModelStore.verify(part, meta.sha)) {
            part.delete()
            return DownloadResult.BAD_CHECKSUM
        }
        target.delete()
        return if (part.renameTo(target)) DownloadResult.OK else DownloadResult.FAILED
    }
}
