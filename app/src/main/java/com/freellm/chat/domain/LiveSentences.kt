package com.freellm.chat.domain

object LiveSentences {
    private const val ENDERS = ".!?؟…。\n"
    private const val SOFT_LIMIT = 240

    class Result(val sentences: List<String>, val next: Int)

    fun take(text: String, from: Int, finished: Boolean): Result {
        var start = from.coerceIn(0, text.length)
        val out = ArrayList<String>()
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (c in ENDERS) {
                var j = i
                while (j + 1 < text.length && text[j + 1] in ENDERS) j++
                val atEnd = j + 1 >= text.length
                val boundary = if (atEnd) finished else text[j + 1].isWhitespace()
                if (boundary) {
                    val s = text.substring(start, j + 1).trim()
                    if (s.isNotEmpty()) out.add(s)
                    start = j + 1
                }
                i = j + 1
                continue
            }
            if (!finished && i - start >= SOFT_LIMIT && c == ' ') {
                val s = text.substring(start, i).trim()
                if (s.isNotEmpty()) out.add(s)
                start = i + 1
            }
            i++
        }
        if (finished) {
            val s = text.substring(start).trim()
            if (s.isNotEmpty()) out.add(s)
            start = text.length
        }
        return Result(out, start)
    }
}
