package com.freellm.chat.data

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

object PdfExport {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40
    private const val CONTENT_W = PAGE_W - MARGIN * 2

    private class Writer(val doc: PdfDocument) {
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = MARGIN.toFloat()

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN.toFloat()
        }

        fun ensure() {
            if (page == null) newPage()
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }

        fun gap(h: Float) {
            ensure()
            y += h
            if (y > PAGE_H - MARGIN) newPage()
        }

        fun draw(layout: StaticLayout) {
            ensure()
            var line = 0
            val bottom = (PAGE_H - MARGIN).toFloat()
            while (line < layout.lineCount) {
                val avail = bottom - y
                var end = line
                while (end < layout.lineCount && layout.getLineBottom(end) - layout.getLineTop(line) <= avail) end++
                if (end == line) {
                    if (y <= MARGIN + 0.5f) end = line + 1 else {
                        newPage()
                        continue
                    }
                }
                val top = layout.getLineTop(line).toFloat()
                val bot = layout.getLineBottom(end - 1).toFloat()
                val c = page!!.canvas
                c.save()
                c.translate(MARGIN.toFloat(), y - top)
                c.clipRect(0f, top, CONTENT_W.toFloat(), bot)
                layout.draw(c)
                c.restore()
                y += bot - top
                line = end
                if (line < layout.lineCount) newPage()
            }
        }
    }

    private fun paint(size: Float, bold: Boolean = false, mono: Boolean = false): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = when {
            mono -> Typeface.MONOSPACE
            bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else -> Typeface.DEFAULT
        }
    }

    private fun layout(text: CharSequence, p: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, CONTENT_W)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.3f)
            .setIncludePad(false)
            .build()

    private fun clean(s: String): String = s
        .replace("**", "")
        .replace("__", "")
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1 ($2)")

    suspend fun fromText(text: String, out: OutputStream) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            val w = Writer(doc)
            val body = paint(12f)
            val code = paint(10f, mono = true)
            var inCode = false
            for (raw in text.replace("\r", "").split("\n")) {
                val t = raw.trimEnd()
                if (t.trimStart().startsWith("```")) {
                    inCode = !inCode
                    continue
                }
                if (inCode) {
                    if (t.isNotEmpty()) w.draw(layout(t, code)) else w.gap(6f)
                    continue
                }
                if (t.isBlank()) {
                    w.gap(6f)
                    continue
                }
                val trimmed = t.trimStart()
                when {
                    trimmed.startsWith("#") -> {
                        val level = trimmed.takeWhile { it == '#' }.length
                        val title = clean(trimmed.dropWhile { it == '#' }.trim())
                        w.gap(4f)
                        w.draw(layout(title, paint(max(13f, 20f - level * 2f), bold = true)))
                    }
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                        w.draw(layout("• " + clean(trimmed.drop(2)), body))
                    trimmed == "---" || trimmed == "***" -> w.gap(8f)
                    else -> w.draw(layout(clean(t), body))
                }
            }
            w.ensure()
            w.finish()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    suspend fun fromImages(files: List<File>, out: OutputStream) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            var n = 0
            for (f in files) {
                val bmp = BitmapFactory.decodeFile(f.path) ?: continue
                n++
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, n).create())
                val availW = (PAGE_W - MARGIN).toFloat()
                val availH = (PAGE_H - MARGIN).toFloat()
                val s = min(availW / bmp.width, availH / bmp.height)
                val w = bmp.width * s
                val h = bmp.height * s
                val l = (PAGE_W - w) / 2f
                val t = (PAGE_H - h) / 2f
                page.canvas.drawBitmap(
                    bmp,
                    null,
                    android.graphics.RectF(l, t, l + w, t + h),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                doc.finishPage(page)
                bmp.recycle()
            }
            if (n == 0) throw IllegalStateException("no pages")
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
