package com.freellm.chat.domain

data class ThinkParts(val thoughts: String, val thinking: Boolean, val answer: String)

object ThinkSplit {
    private val open = Regex("<(think|thinking|reasoning|thought)>", RegexOption.IGNORE_CASE)
    private val partialTag = Regex("<[A-Za-z/]{0,10}$")
    private val sentenceEnd = Regex("(?<=[.!?\u061F])\\s+")
    private val blank = Regex("\n\\s*\n")
    const val MAX_STEPS = 60

    fun split(raw: String): ThinkParts {
        if (raw.indexOf('<') < 0) return ThinkParts("", false, raw)
        val answer = StringBuilder()
        val thoughts = StringBuilder()
        var thinking = false
        var pos = 0
        while (pos < raw.length) {
            val m = open.find(raw, pos)
            if (m == null) {
                answer.append(raw, pos, raw.length)
                break
            }
            answer.append(raw, pos, m.range.first)
            val from = m.range.last + 1
            val close = Regex("</" + m.groupValues[1] + ">", RegexOption.IGNORE_CASE).find(raw, from)
            if (close == null) {
                thoughts.append(raw, from, raw.length)
                thinking = true
                pos = raw.length
            } else {
                if (thoughts.isNotEmpty()) thoughts.append("\n\n")
                thoughts.append(raw, from, close.range.first)
                pos = close.range.last + 1
            }
        }
        val clean = partialTag.replace(answer.toString(), "").trimStart()
        return ThinkParts(thoughts.toString().trim(), thinking, clean)
    }

    fun strip(raw: String): String = split(raw).answer

    fun steps(thoughts: String): List<String> {
        var parts = thoughts.split(blank).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) parts = thoughts.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) parts = thoughts.split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }.chunked(2).map { it.joinToString(" ") }
        return parts.takeLast(MAX_STEPS)
    }
}
