package com.freellm.chat.domain

enum class QType { CODE, COMPLEX, SHORT, GENERAL }

enum class Difficulty { EASY, NORMAL, HARD }

data class Classification(
    val type: QType,
    val difficulty: Difficulty,
    val arabic: Boolean,
    val needsFresh: Boolean,
    val ambiguous: Boolean
) {
    val caps: List<String>
        get() = buildList {
            when (type) {
                QType.CODE -> add("code")
                QType.COMPLEX -> add("reasoning")
                QType.SHORT -> add("fast")
                QType.GENERAL -> Unit
            }
            if (difficulty == Difficulty.HARD && "reasoning" !in this) add("reasoning")
            if (arabic) add("arabic")
        }
}

object QuestionClassifier {
    private val fresh = Regex(
        "\\b(latest|current|currently|today|now|news|price|prices|version|release|released)\\b|\u062D\u0627\u0644\u064A|\u062D\u0627\u0644\u064A\u0627|\u062D\u0627\u0644\u064A\u0627\u064B|\u0622\u062E\u0631|\u0627\u062E\u0631|\u0623\u062D\u062F\u062B|\u0627\u062D\u062F\u062B|\u0627\u0644\u0622\u0646|\u0627\u0644\u0627\u0646|\u0627\u0644\u064A\u0648\u0645|\u0623\u062E\u0628\u0627\u0631|\u0627\u062E\u0628\u0627\u0631|\u0633\u0639\u0631|\u0623\u0633\u0639\u0627\u0631|\u0627\u0633\u0639\u0627\u0631|\u0625\u0635\u062F\u0627\u0631|\u0627\u0635\u062F\u0627\u0631",
        RegexOption.IGNORE_CASE
    )
    private val steps = Regex("step by step|\u062E\u0637\u0648\u0629 \u0628\u062E\u0637\u0648\u0629|\\n\\s*(\\d+[.)]|[-*])\\s", RegexOption.IGNORE_CASE)

    fun arabicRatio(text: String): Double {
        var letters = 0
        var ar = 0
        for (c in text) {
            if (!c.isLetter()) continue
            letters++
            if (c in '\u0600'..'\u06FF') ar++
        }
        return if (letters == 0) 0.0 else ar.toDouble() / letters
    }

    private val yearToken = Regex("(?<!\\d)(20\\d\\d)(?!\\d)")

    fun needsFresh(text: String, year: Int = java.time.Year.now().value): Boolean =
        fresh.containsMatchIn(text) || yearToken.findAll(text).any { it.groupValues[1].toInt() >= year }

    fun classify(text: String, hasAttachment: Boolean = false): Classification {
        val t = text.trim()
        val kind = TaskClassifier.classify(t)
        val arabic = arabicRatio(t) > 0.5
        val long = t.length > 600
        val type = when {
            kind == TaskKind.CODE -> QType.CODE
            kind == TaskKind.REASON || long || steps.containsMatchIn(t) -> QType.COMPLEX
            kind == TaskKind.QUICK && !hasAttachment -> QType.SHORT
            else -> QType.GENERAL
        }
        val difficulty = when {
            type == QType.COMPLEX && (long || kind == TaskKind.REASON) -> Difficulty.HARD
            type == QType.CODE && (t.contains("```") && t.length > 800) -> Difficulty.HARD
            type == QType.SHORT && t.length <= 60 -> Difficulty.EASY
            else -> Difficulty.NORMAL
        }
        val ambiguous = type == QType.GENERAL && t.length in 61..400 && kind == TaskKind.GENERAL
        return Classification(type, difficulty, arabic, needsFresh(t), ambiguous)
    }
}

object AmbiguityResolver {
    private const val SYSTEM =
        "Classify the user's message. Reply with exactly one word: code, reasoning, short or general."

    fun parse(reply: String): QType? {
        val w = reply.trim().lowercase()
        return when {
            w.startsWith("code") -> QType.CODE
            w.startsWith("reason") -> QType.COMPLEX
            w.startsWith("short") -> QType.SHORT
            w.startsWith("general") -> QType.GENERAL
            else -> null
        }
    }

    private val codeHint = Regex("[{};=<>]|\\b(error|exception|stack|compile|build|crash)\\b", RegexOption.IGNORE_CASE)
    private val reasonHint = Regex("\\b(why|how come|explain|compare|difference|pros and cons)\\b", RegexOption.IGNORE_CASE)

    fun heuristic(text: String): QType {
        val t = text.trim()
        return when {
            codeHint.containsMatchIn(t) -> QType.CODE
            reasonHint.containsMatchIn(t) -> QType.COMPLEX
            t.length <= 140 && (t.endsWith("?") || t.endsWith("\u061F")) -> QType.SHORT
            else -> QType.GENERAL
        }
    }
}

object ModelCaps {
    fun of(m: ModelDef): List<String> {
        if (m.caps.isNotEmpty()) return m.caps
        return buildList {
            if ("code" in m.modes) add("code")
            if ("fast" in m.modes) add("fast")
            if (m.reasoning) add("reasoning")
            if (m.contextWindow >= 200_000) add("long_context")
        }
    }

    fun matches(m: ModelDef, wanted: List<String>): Int {
        if (wanted.isEmpty()) return 0
        val have = of(m)
        return wanted.count { it in have }
    }
}
