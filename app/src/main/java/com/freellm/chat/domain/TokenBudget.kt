package com.freellm.chat.domain

import kotlin.math.max

object TokenEstimator {
    fun text(s: String): Int = ContextTrimmer.estimate(s)

    fun messages(msgs: List<ChatMsg>): Int = msgs.sumOf { ContextTrimmer.cost(it) }
}

object MaxTokensPlanner {
    const val MIN = 256
    const val MAX = 4096

    private val longAsk = Regex(
        "\\b(detailed|in detail|thorough|long|comprehensive|full|step by step)\\b|مفصل|بالتفصيل|شامل|طويل|كامل|خطوة بخطوة",
        RegexOption.IGNORE_CASE
    )

    fun plan(kind: TaskKind, question: String, style: String, hasAttachment: Boolean): Int {
        var base = when (kind) {
            TaskKind.QUICK -> if (question.trim().length <= 25) 500 else 700
            TaskKind.GENERAL -> 1800
            TaskKind.CODE -> 3000
            TaskKind.REASON -> 2500
            TaskKind.WRITE -> 3000
        }
        if (hasAttachment && kind != TaskKind.CODE) base = max(base, 2000)
        base = when (style) {
            "concise" -> base * 6 / 10
            "detailed", "teacher" -> base * 3 / 2
            else -> base
        }
        if (longAsk.containsMatchIn(question)) base = max(base, 3500)
        return base.coerceIn(MIN, MAX)
    }
}

object RequestFitter {
    const val MARGIN = 256
    const val DEFAULT_REPLY = 1024

    fun needed(msgs: List<ChatMsg>, maxTokens: Int?): Int =
        TokenEstimator.messages(msgs) + (maxTokens ?: DEFAULT_REPLY)

    fun fits(t: Target, need: Int): Boolean = t.model.contextWindow >= need + MARGIN

    fun clampLast(msgs: List<ChatMsg>, budgetTokens: Int): List<ChatMsg> {
        val i = msgs.indexOfLast { it.role == "user" }
        if (i < 0) return msgs
        val others = msgs.indices.filter { it != i }.sumOf { ContextTrimmer.cost(msgs[it]) }
        val room = budgetTokens - others
        val m = msgs[i]
        if (ContextTrimmer.cost(m) <= room) return msgs
        val allowed = ((room - m.images.size * 1200 - 4) * 3.5).toInt()
        if (allowed <= 200) return msgs
        val tailLen = if (allowed > 1400) 600 else allowed / 2
        val head = m.content.take(allowed - tailLen).trimEnd()
        val tail = m.content.takeLast(tailLen)
        val merged = if (m.content.length <= allowed) m.content else "$head\n[…]\n$tail"
        return msgs.toMutableList().also { it[i] = m.copy(content = merged) }
    }
}
