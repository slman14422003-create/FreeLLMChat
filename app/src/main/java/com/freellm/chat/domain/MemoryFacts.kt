package com.freellm.chat.domain

// Builds the short system block from user-pinned facts only.
object MemoryFacts {
    const val MAX_CHARS = 600
    private const val HEADER = "Facts the user asked you to remember:\n"

    fun block(facts: List<String>, enabled: Boolean): String {
        if (!enabled) return ""
        val sb = StringBuilder(HEADER)
        var added = 0
        for (f in facts.map { it.trim().replace('\n', ' ') }.filter { it.isNotEmpty() }) {
            val line = "- $f\n"
            if (sb.length + line.length > MAX_CHARS) break
            sb.append(line)
            added++
        }
        return if (added == 0) "" else sb.toString().trimEnd()
    }
}
