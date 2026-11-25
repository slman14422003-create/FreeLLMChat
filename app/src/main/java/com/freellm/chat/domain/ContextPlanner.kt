package com.freellm.chat.domain

object ContextPlanner {
    const val KEEP_RECENT = Tuning.KEEP_RECENT
    const val MIN_NEW_TO_SUMMARIZE = 4
    const val MIN_CHARS_TO_SUMMARIZE = 1800
    const val MAX_SUMMARY_CHARS = 1200

    fun <T> split(list: List<T>, keep: Int, role: (T) -> String): Pair<List<T>, List<T>> {
        if (list.size <= keep) return emptyList<T>() to list
        var cut = list.size - keep
        while (cut < list.size - 1 && role(list[cut]) != "user") cut++
        return list.subList(0, cut).toList() to list.subList(cut, list.size).toList()
    }

    fun shouldUpdate(pendingCount: Int, pendingChars: Int): Boolean =
        pendingCount > 0 && (pendingCount >= MIN_NEW_TO_SUMMARIZE || pendingChars >= MIN_CHARS_TO_SUMMARIZE)

    private val factPatterns = listOf(
        Regex("https?://\\S+"),
        Regex("\"[^\"\\n]{2,60}\""),
        Regex("\\b[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+\\b"),
        Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\("),
        Regex("\\d[\\d.,:/-]*\\d")
    )

    fun keyFacts(msgs: List<ChatMsg>, maxChars: Int = 320): String {
        val seen = LinkedHashSet<String>()
        for (m in msgs) for (p in factPatterns) for (r in p.findAll(m.content)) seen.add(r.value.trimEnd('.', ',', ')'))
        return seen.joinToString("; ").take(maxChars)
    }

    fun digest(old: List<ChatMsg>, maxChars: Int = 1400): String {
        val keys = keyFacts(old, minOf(320, maxChars / 4))
        val tail = if (keys.isNotEmpty()) "\nKeys: " + keys else ""
        val budget = maxChars - tail.length
        val lines = old.map { m ->
            val who = if (m.role == "user") "U" else "A"
            val body = m.content.replace(Regex("\\s+"), " ").trim().take(220)
            "$who: $body"
        }
        val out = StringBuilder()
        for (l in lines.asReversed()) {
            if (out.length + l.length + 1 > budget) break
            out.insert(0, l + "\n")
        }
        return out.toString().trim() + tail
    }

    fun summaryPrompt(previous: String?, fresh: List<ChatMsg>): String {
        val sb = StringBuilder()
        sb.append("Current summary:\n").append(previous?.takeIf { it.isNotBlank() } ?: "(none)").append("\n\nNew messages:\n")
        fresh.forEach { m ->
            sb.append(if (m.role == "user") "User: " else "Assistant: ")
                .append(m.content.replace(Regex("\\s+"), " ").trim().take(400)).append('\n')
        }
        return sb.toString()
    }

    const val SUMMARY_SYSTEM =
        "Update the running summary of a conversation using the new messages. Keep names, numbers, decisions, " +
            "user preferences and open questions. Max 120 words. Same language as the conversation. Output only the summary." +
            " Keep names, numbers, decisions and code identifiers exactly as written."

    fun clean(raw: String): String = raw.trim().take(MAX_SUMMARY_CHARS)

    fun asSystemLine(summary: String): String = "Earlier in this chat (summary): $summary"
}
