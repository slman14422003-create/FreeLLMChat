package com.freellm.chat.domain

import java.text.BreakIterator
import java.util.Locale

object TextChunker {
    fun chunks(text: String, max: Int): List<String> {
        val src = text.trim()
        if (src.isEmpty() || max <= 0) return emptyList()
        val it = BreakIterator.getSentenceInstance(if (SpeechText.isArabic(src)) Locale("ar") else Locale.getDefault())
        it.setText(src)
        val sentences = ArrayList<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            val s = src.substring(start, end).trim()
            if (s.isNotEmpty()) sentences.add(s)
            start = end
            end = it.next()
        }
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() {
            val t = cur.toString().trim()
            if (t.isNotEmpty()) out.add(t)
            cur.setLength(0)
        }
        for (s in sentences) {
            if (s.length > max) {
                flush()
                var rest = s
                while (rest.length > max) {
                    val cut = rest.lastIndexOf(' ', max).let { i -> if (i <= 0) max else i }
                    val head = rest.substring(0, cut).trim()
                    if (head.isNotEmpty()) out.add(head)
                    rest = rest.substring(cut).trim()
                }
                if (rest.isNotEmpty()) cur.append(rest)
                continue
            }
            if (cur.isNotEmpty() && cur.length + 1 + s.length > max) flush()
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        flush()
        return out
    }
}
