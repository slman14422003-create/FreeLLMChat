package com.freellm.chat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

@Serializable
data class Attachment(
    val kind: String,
    val name: String,
    val path: String
) {
    val isImage: Boolean get() = kind == KIND_IMAGE

    companion object {
        const val KIND_IMAGE = "image"
        const val KIND_FILE = "file"
    }
}

data class ImportResult(val items: List<Attachment>, val notice: String? = null)

object AttachmentCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(Attachment.serializer())

    fun encode(list: List<Attachment>): String? =
        if (list.isEmpty()) null else json.encodeToString(ser, list)

    fun decode(s: String?): List<Attachment> =
        if (s.isNullOrBlank()) emptyList() else try {
            json.decodeFromString(ser, s)
        } catch (e: Exception) {
            emptyList()
        }
}

class AttachmentStore(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }

    fun file(a: Attachment): File = File(a.path)

    fun displayName(uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) {
            // fall through
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    private fun newFile(ext: String): File = File(dir, UUID.randomUUID().toString() + "." + ext)

    // ---------- images ----------

    private fun decodeScaled(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val bmp = try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val m = max(w, h)
                    if (m > MAX_SIDE) {
                        val s = MAX_SIDE.toFloat() / m
                        dec.setTargetSize(max(1, (w * s).toInt()), max(1, (h * s).toInt()))
                    }
                }
            } catch (e: Throwable) {
                null
            }
            if (bmp != null) return bmp
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val m = max(raw.width, raw.height)
            if (m > MAX_SIDE) {
                val s = MAX_SIDE.toFloat() / m
                Bitmap.createScaledBitmap(raw, max(1, (raw.width * s).toInt()), max(1, (raw.height * s).toInt()), true)
            } else raw
        } catch (e: Throwable) {
            null
        }
    }

    private fun saveJpeg(bmp: Bitmap, name: String): Attachment? = try {
        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bmp, 0f, 0f, null)
        }
        val f = newFile("jpg")
        f.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        Attachment(Attachment.KIND_IMAGE, name, f.path)
    } catch (e: Throwable) {
        null
    }

    suspend fun importImage(uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        val bmp = decodeScaled(uri) ?: return@withContext null
        saveJpeg(bmp, displayName(uri))
    }

    fun dataUrl(a: Attachment): String? = try {
        val bytes = File(a.path).readBytes()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Throwable) {
        null
    }

    // ---------- files ----------

    private fun copyToCache(uri: Uri): File? = try {
        val f = File.createTempFile("imp", ".bin", ctx.cacheDir)
        ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return null
        f
    } catch (e: Throwable) {
        null
    }

    private fun pdfText(src: File): String = try {
        PDFBoxResourceLoader.init(ctx.applicationContext)
        PDDocument.load(src).use { doc ->
            val stripper = PDFTextStripper()
            stripper.setSortByPosition(true)
            stripper.getText(doc)
        }
    } catch (e: Throwable) {
        ""
    }

    private fun docxText(src: File): String = try {
        var xml = ""
        ZipInputStream(src.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "word/document.xml") {
                    val bo = ByteArrayOutputStream()
                    z.copyTo(bo)
                    xml = bo.toString("UTF-8")
                    break
                }
            }
        }
        xml.replace("</w:p>", "\n")
            .replace("<w:tab/>", "\t")
            .replace("<w:br/>", "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    } catch (e: Throwable) {
        ""
    }

    private fun plainText(src: File): String? = try {
        val bytes = src.inputStream().use { it.readNBytesCompat(MAX_RAW_BYTES) }
        val nul = bytes.count { it.toInt() == 0 }
        if (bytes.isNotEmpty() && nul * 50 > bytes.size) null else String(bytes, Charsets.UTF_8)
    } catch (e: Throwable) {
        null
    }

    private fun saveText(text: String, name: String): Attachment {
        val f = newFile("txt")
        f.writeText(text)
        return Attachment(Attachment.KIND_FILE, name, f.path)
    }

    suspend fun importFile(uri: Uri, noTextNotice: String, unsupportedNotice: String, scannedNotice: String): ImportResult =
        withContext(Dispatchers.IO) {
            val name = displayName(uri)
            val mime = ctx.contentResolver.getType(uri).orEmpty()
            val lower = name.lowercase()
            if (mime.startsWith("image/")) {
                val a = importImage(uri)
                return@withContext if (a != null) ImportResult(listOf(a)) else ImportResult(emptyList(), unsupportedNotice)
            }
            val src = copyToCache(uri) ?: return@withContext ImportResult(emptyList(), unsupportedNotice)
            try {
                when {
                    mime.contains("pdf") || lower.endsWith(".pdf") -> {
                        val t = pdfText(src).trim()
                        if (t.length >= 40) {
                            ImportResult(listOf(saveText(t, name)))
                        } else {
                            val pages = renderPdf(src, name)
                            if (pages.isEmpty()) ImportResult(emptyList(), noTextNotice)
                            else ImportResult(pages, scannedNotice)
                        }
                    }
                    lower.endsWith(".docx") -> {
                        val t = docxText(src).trim()
                        if (t.isEmpty()) ImportResult(emptyList(), noTextNotice)
                        else ImportResult(listOf(saveText(t, name)))
                    }
                    else -> {
                        val t = plainText(src)?.trim()
                        if (t.isNullOrEmpty()) ImportResult(emptyList(), unsupportedNotice)
                        else ImportResult(listOf(saveText(t, name)))
                    }
                }
            } finally {
                src.delete()
            }
        }

    suspend fun importPdfAsImages(uri: Uri, unsupportedNotice: String): ImportResult = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val src = copyToCache(uri) ?: return@withContext ImportResult(emptyList(), unsupportedNotice)
        try {
            val pages = renderPdf(src, name)
            if (pages.isEmpty()) ImportResult(emptyList(), unsupportedNotice) else ImportResult(pages)
        } finally {
            src.delete()
        }
    }

    private fun renderPdf(src: File, name: String): List<Attachment> {
        val out = ArrayList<Attachment>()
        try {
            ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val r = PdfRenderer(pfd)
                try {
                    val n = min(r.pageCount, MAX_PDF_PAGES)
                    for (i in 0 until n) {
                        val page = r.openPage(i)
                        try {
                            val s = PAGE_SIDE.toFloat() / max(page.width, page.height)
                            val bmp = Bitmap.createBitmap(
                                max(1, (page.width * s).toInt()),
                                max(1, (page.height * s).toInt()),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            saveJpeg(bmp, name + " · " + (i + 1))?.let { out.add(it) }
                        } finally {
                            page.close()
                        }
                    }
                } finally {
                    r.close()
                }
            }
        } catch (e: Throwable) {
            // return whatever was rendered
        }
        return out
    }

    fun readText(a: Attachment): String = try {
        val t = File(a.path).readText()
        if (t.length > MAX_FILE_CHARS) t.take(MAX_FILE_CHARS) + "\n[…]" else t
    } catch (e: Throwable) {
        ""
    }

    fun thumb(a: Attachment, target: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(a.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        BitmapFactory.decodeFile(a.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }

    companion object {
        const val MAX_SIDE = 1600
        const val PAGE_SIDE = 1500
        const val MAX_PDF_PAGES = 8
        const val MAX_FILE_CHARS = 40_000
        const val MAX_RAW_BYTES = 2_000_000
    }
}

private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
    val bo = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var left = n
    while (left > 0) {
        val r = read(buf, 0, min(buf.size, left))
        if (r < 0) break
        bo.write(buf, 0, r)
        left -= r
    }
    return bo.toByteArray()
}
