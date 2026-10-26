package com.freellm.chat.domain

import kotlin.math.min

enum class TaskKind(val mode: ChatMode, val keepFull: Int, val hint: String) {
    QUICK(ChatMode.FAST, 4, "Answer briefly and directly."),
    CODE(ChatMode.CODE, 10, "For code, give the code first with minimal explanation unless asked."),
    REASON(ChatMode.SMART, 8, "Think carefully and show only the essential steps."),
    WRITE(ChatMode.SMART, 6, ""),
    GENERAL(ChatMode.AUTO, 8, "")
}

object TaskClassifier {
    private val code = Regex(
        "\\b(function|class|def|import|compile|stack ?trace|exception|null ?pointer|regex|sql|json|html|css|python|kotlin|java|javascript|typescript|bug|debug|api|git|bash|gradle|android|xml|npm|docker)\\b|كود|برمجة|برنامج|دالة|دوال|سكربت|أكواد|اكواد|كوتلن|بايثون|جافا|اندرويد|أندرويد|كراش",
        RegexOption.IGNORE_CASE
    )
    private val reason = Regex(
        "\\b(solve|prove|calculate|explain why|step by step|analy[sz]e|compare|derive|equation|probability|why)\\b|احسب|اثبت|برهن|حلل|قارن|لماذا|ليش|معادلة|مسألة|خطوة بخطوة|استنتج|حل ",
        RegexOption.IGNORE_CASE
    )
    private val math = Regex("[0-9]+\\s*[+\\-*/^=]\\s*[0-9]+")
    private val write = Regex(
        "\\b(write (an? )?(article|essay|story|email|letter|post)|summari[sz]e|translate|rewrite|proofread)\\b|اكتب (لي )?(مقال|قصة|رسالة|بريد|خطاب|منشور)|لخص|ترجم|أعد صياغة|اعد صياغة|صحح",
        RegexOption.IGNORE_CASE
    )

    fun classify(text: String): TaskKind {
        val t = text.trim()
        if (t.isEmpty()) return TaskKind.GENERAL
        var c = code.findAll(t).count() * 2
        if (t.contains("```")) c += 5
        var r = reason.findAll(t).count() * 2
        if (math.containsMatchIn(t)) r += 2
        val w = write.findAll(t).count() * 3
        val best = maxOf(c, r, w)
        if (best >= 2) {
            return when (best) {
                c -> TaskKind.CODE
                w -> TaskKind.WRITE
                else -> TaskKind.REASON
            }
        }
        return if (t.length <= 60) TaskKind.QUICK else TaskKind.GENERAL
    }
}

data class Compacted(val messages: List<ChatMsg>, val savedTokens: Int)

object TokenSaver {
    private val fence = Regex("```[\\s\\S]*?```")

    private fun shrink(m: ChatMsg, limit: Int): ChatMsg {
        var s = m.content
        if (m.role == "assistant") s = s.replace(fence, "[code omitted]")
        if (s.length > limit) s = s.take(limit).trimEnd() + "…"
        return if (s == m.content) m else m.copy(content = s)
    }

    fun compact(msgs: List<ChatMsg>, kind: TaskKind): Compacted {
        val system = msgs.filter { it.role == "system" }
        val rest = msgs.filter { it.role != "system" }
        val keep = kind.keepFull
        if (rest.size <= keep) return Compacted(msgs, 0)
        val old = rest.take(rest.size - keep)
        val recent = rest.takeLast(keep)
        val limit = if (kind == TaskKind.QUICK) 240 else 420
        val shrunk = old.map { shrink(it, limit) }
        val before = old.sumOf { ContextTrimmer.estimate(it.content) }
        val after = shrunk.sumOf { ContextTrimmer.estimate(it.content) }
        return Compacted(system + shrunk + recent, (before - after).coerceAtLeast(0))
    }
}

object Overlap {
    private const val MIN = 6

    fun strip(tail: String, next: String): String {
        if (tail.isEmpty() || next.isEmpty()) return next
        var k = min(tail.length, next.length)
        while (k >= MIN) {
            if (tail.endsWith(next.substring(0, k))) return next.substring(k)
            k--
        }
        return next
    }
}

class ModelStats {
    private class S(var ok: Int = 0, var fail: Int = 0, var latencyMs: Long = 0, var samples: Int = 0)

    private val map = HashMap<String, S>()

    @Synchronized
    fun onFirstToken(key: String, ms: Long) {
        val s = map.getOrPut(key) { S() }
        s.latencyMs = if (s.samples == 0) ms else (s.latencyMs * 3 + ms) / 4
        s.samples++
    }

    @Synchronized
    fun onOk(key: String) {
        map.getOrPut(key) { S() }.ok++
    }

    @Synchronized
    fun onFail(key: String) {
        map.getOrPut(key) { S() }.fail++
    }

    @Synchronized
    fun failTier(key: String): Int {
        val s = map[key] ?: return 0
        val n = s.ok + s.fail
        return if (n >= 3 && s.fail * 10 >= n * 4) 1 else 0
    }

    @Synchronized
    fun latencyMs(key: String): Long? {
        val s = map[key] ?: return null
        return if (s.samples == 0) null else s.latencyMs
    }

    @Synchronized
    fun latencyBucket(key: String): Int {
        val s = map[key] ?: return 1
        if (s.samples == 0) return 1
        return (s.latencyMs / 1500).toInt().coerceAtMost(4)
    }
}

object OutputQuality {
    private val refusal = Regex("^(i('| a)m sorry|sorry|as an ai)[^\\n]{0,40}$", RegexOption.IGNORE_CASE)

    /** True when a finished reply is empty, a bare fragment, or cut mid-word and so not worth keeping. */
    fun isPoor(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2) return true
        if (refusal.matches(t)) return true
        val last = t.last()
        if (t.length < 40 && (last == ',' || last == ':' || last == '،')) return true
        return false
    }
}
