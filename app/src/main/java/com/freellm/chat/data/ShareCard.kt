package com.freellm.chat.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

object CardText {
    const val MAX_CHARS = 900
    const val MAX_SOURCES = 4

    private val fence = Regex("```[a-zA-Z0-9]*\\n?")
    private val heading = Regex("(?m)^#{1,6}\\s*")
    private val bold = Regex("\\*\\*|__")
    private val blanks = Regex("\\n{3,}")

    fun clean(s: String): String =
        s.replace(fence, "").replace(heading, "").replace(bold, "").replace(blanks, "\n\n").trim()

    fun fit(s: String, max: Int = MAX_CHARS): String {
        val t = clean(s)
        if (t.length <= max) return t
        val cut = t.substring(0, max)
        val sentence = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("؟ "), cut.lastIndexOf("! "), cut.lastIndexOf(".\n"), cut.lastIndexOf("\n\n"))
        val end = if (sentence >= max * 6 / 10) sentence + 1 else {
            val sp = cut.lastIndexOf(' ')
            if (sp >= max * 6 / 10) sp else max
        }
        return cut.substring(0, end).trimEnd() + "…"
    }

    fun host(url: String): String =
        url.substringAfter("://").substringBefore('/').removePrefix("www.")
}

object ShareCard {
    private const val W = 1080
    private const val PAD = 64

    fun render(
        text: String,
        sources: List<String>,
        appName: String,
        sourcesLabel: String,
        rtl: Boolean,
        dark: Boolean
    ): Bitmap {
        val bg = if (dark) Color.parseColor("#262624") else Color.parseColor("#FAF9F5")
        val fg = if (dark) Color.parseColor("#F5F4EE") else Color.parseColor("#1F1E1D")
        val muted = if (dark) Color.parseColor("#A8A69E") else Color.parseColor("#6B6A66")
        val accent = Color.parseColor("#D97757")
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = 40f
        }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 32f
        }
        val width = W - PAD * 2
        val align = if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        val layout = StaticLayout.Builder.obtain(CardText.fit(text), 0, CardText.fit(text).length, body, width)
            .setAlignment(align)
            .setLineSpacing(0f, 1.3f)
            .build()
        val srcs = sources.take(CardText.MAX_SOURCES)
        val srcLayouts = srcs.mapIndexed { i, s ->
            val line = "[" + (i + 1) + "] " + s
            StaticLayout.Builder.obtain(line, 0, line.length, small, width)
                .setAlignment(align)
                .setMaxLines(1)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
        }
        val headerH = 120
        val srcH = if (srcLayouts.isEmpty()) 0 else 70 + srcLayouts.sumOf { it.height + 12 }
        val footerH = 100
        val h = PAD + headerH + layout.height + srcH + footerH
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val dotX = if (rtl) (W - PAD - 24).toFloat() else (PAD + 24).toFloat()
        c.drawCircle(dotX, PAD + 36f, 24f, dot)
        val nameX = if (rtl) W - PAD - 64f - head.measureText(appName) else PAD + 64f
        c.drawText(appName, nameX, PAD + 50f, head)
        c.save()
        c.translate(PAD.toFloat(), (PAD + headerH).toFloat())
        layout.draw(c)
        c.restore()
        var y = (PAD + headerH + layout.height + 50).toFloat()
        if (srcLayouts.isNotEmpty()) {
            val line = Paint().apply { color = muted; alpha = 80; strokeWidth = 2f }
            c.drawLine(PAD.toFloat(), y - 20f, (W - PAD).toFloat(), y - 20f, line)
            val lx = if (rtl) W - PAD - small.measureText(sourcesLabel) else PAD.toFloat()
            c.drawText(sourcesLabel, lx, y + 20f, small.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            small.typeface = Typeface.DEFAULT
            y += 40f
            for (l in srcLayouts) {
                c.save()
                c.translate(PAD.toFloat(), y)
                l.draw(c)
                c.restore()
                y += l.height + 12
            }
        }
        return bmp
    }

    fun write(bmp: Bitmap, out: OutputStream): Boolean = bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
}
