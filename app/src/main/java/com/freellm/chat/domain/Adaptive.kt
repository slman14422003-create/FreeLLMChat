package com.freellm.chat.domain

import kotlin.math.min

enum class TaskKind(val mode: ChatMode, val keepFull: Int, val hint: String) {
    QUICK(ChatMode.FAST, 4, "Be brief."),
    CODE(ChatMode.CODE, 10, "Code first, minimal prose."),
    REASON(ChatMode.SMART, 8, "Reason carefully; show key steps only."),
    WRITE(ChatMode.SMART, 6, ""),
    GENERAL(ChatMode.AUTO, 8, "")
}

object TaskClassifier {
    private val code = Regex(
        "\\b(function|class|def|import|compile|stack ?trace|exception|null ?pointer|regex|sql|json|html|css|python|kotlin|java|javascript|typescript|bug|debug|api|git|bash|gradle|android|xml|npm|docker)\\b|\u0643\u0648\u062F|\u0628\u0631\u0645\u062C\u0629|\u0628\u0631\u0646\u0627\u0645\u062C|\u062F\u0627\u0644\u0629|\u062F\u0648\u0627\u0644|\u0633\u0643\u0631\u0628\u062A|\u0623\u0643\u0648\u0627\u062F|\u0627\u0643\u0648\u0627\u062F|\u0643\u0648\u062A\u0644\u0646|\u0628\u0627\u064A\u062B\u0648\u0646|\u062C\u0627\u0641\u0627|\u0627\u0646\u062F\u0631\u0648\u064A\u062F|\u0623\u0646\u062F\u0631\u0648\u064A\u062F|\u0643\u0631\u0627\u0634",
        RegexOption.IGNORE_CASE
    )
    private val reason = Regex(
        "\\b(solve|prove|calculate|explain why|step by step|analy[sz]e|compare|derive|equation|probability|why)\\b|\u0627\u062D\u0633\u0628|\u0627\u062B\u0628\u062A|\u0628\u0631\u0647\u0646|\u062D\u0644\u0644|\u0642\u0627\u0631\u0646|\u0644\u0645\u0627\u0630\u0627|\u0644\u064A\u0634|\u0645\u0639\u0627\u062F\u0644\u0629|\u0645\u0633\u0623\u0644\u0629|\u062E\u0637\u0648\u0629 \u0628\u062E\u0637\u0648\u0629|\u0627\u0633\u062A\u0646\u062A\u062C|\u062D\u0644 ",
        RegexOption.IGNORE_CASE
    )
    private val math = Regex("[0-9]+\\s*[+\\-*/^=]\\s*[0-9]+")
    private val write = Regex(
        "\\b(write (an? )?(article|essay|story|email|letter|post)|summari[sz]e|translate|rewrite|proofread)\\b|\u0627\u0643\u062A\u0628 (\u0644\u064A )?(\u0645\u0642\u0627\u0644|\u0642\u0635\u0629|\u0631\u0633\u0627\u0644\u0629|\u0628\u0631\u064A\u062F|\u062E\u0637\u0627\u0628|\u0645\u0646\u0634\u0648\u0631)|\u0644\u062E\u0635|\u062A\u0631\u062C\u0645|\u0623\u0639\u062F \u0635\u064A\u0627\u063A\u0629|\u0627\u0639\u062F \u0635\u064A\u0627\u063A\u0629|\u0635\u062D\u062D",
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
        if (s.length > limit) s = s.take(limit).trimEnd() + "\u2026"
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

    private fun skipped(c: Char): Boolean =
        c.isWhitespace() || c == '*' || c == '_' || c == '`' || c == '#' || c == '>' || c == '-' || c == '\u2022'

    fun strip(tail: String, next: String): String {
        if (tail.isEmpty() || next.isEmpty()) return next
        val nt = StringBuilder()
        for (c in tail) if (!skipped(c)) nt.append(c)
        val nn = StringBuilder()
        val idx = ArrayList<Int>()
        for ((i, c) in next.withIndex()) if (!skipped(c)) {
            nn.append(c)
            idx.add(i)
        }
        val t = nt.toString()
        val n = nn.toString()
        var k = min(t.length, n.length)
        while (k >= MIN) {
            if (t.endsWith(n.substring(0, k))) return next.substring(idx[k - 1] + 1)
            k--
        }
        return next
    }
}

data class StatRow(val key: String, val ok: Int, val fail: Int, val latencyMs: Long?, val samples: Int, val p95: Long?)

class ModelStats(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private class S {
        var ok = 0.0
        var fail = 0.0
        var latencyMs = 0L
        var samples = 0
        var seeded = false
        val ring = ArrayList<Long>()
    }

    private val map = HashMap<String, S>()

    @Synchronized
    fun onFirstToken(key: String, ms: Long) {
        val s = map.getOrPut(key) { S() }
        s.latencyMs = if (s.samples == 0) ms else (s.latencyMs * 3 + ms) / 4
        s.samples++
        s.seeded = false
        s.ring.add(ms)
        if (s.ring.size > RING) s.ring.removeAt(0)
    }

    @Synchronized
    fun onOk(key: String) {
        map.getOrPut(key) { S() }.ok += 1.0
    }

    @Synchronized
    fun onFail(key: String) {
        map.getOrPut(key) { S() }.fail += 1.0
    }

    @Synchronized
    fun failTier(key: String): Int {
        val s = map[key] ?: return 0
        val n = s.ok + s.fail
        return if (n >= 3 && s.fail * 10 >= n * 4) 1 else 0
    }

    @Synchronized
    fun lastResort(key: String, minAttempts: Int = LAST_RESORT_ATTEMPTS): Boolean {
        val s = map[key] ?: return false
        return s.ok == 0.0 && s.fail >= minAttempts
    }

    @Synchronized
    fun samples(key: String): Int = map[key]?.samples ?: 0

    @Synchronized
    fun latencyMs(key: String): Long? {
        val s = map[key] ?: return null
        return if (s.samples == 0 && !s.seeded) null else s.latencyMs
    }

    @Synchronized
    fun p95(key: String): Long? {
        val s = map[key] ?: return null
        if (s.ring.size < MIN_RING) return null
        val sorted = s.ring.sorted()
        return sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
    }

    @Synchronized
    fun latencyBucket(key: String): Int {
        val s = map[key] ?: return 2
        if (s.samples == 0 && !s.seeded) return 2
        val ms = s.latencyMs
        return when {
            ms < 700 -> 0
            ms < 1500 -> 1
            ms < 3000 -> 2
            ms < 6000 -> 3
            else -> 4
        }
    }

    @Synchronized
    fun seed(latencies: Map<String, Long>) {
        for ((k, ms) in latencies) {
            if (map.containsKey(k) || ms <= 0) continue
            map[k] = S().also {
                it.latencyMs = ms
                it.seeded = true
            }
        }
    }

    @Synchronized
    fun hasHistory(): Boolean = map.values.any { it.samples > 0 || it.ok > 0 || it.fail > 0 }

    @Synchronized
    fun rows(): List<StatRow> = map.entries.map { (k, s) ->
        StatRow(
            k, Math.round(s.ok).toInt(), Math.round(s.fail).toInt(),
            if (s.samples == 0 && !s.seeded) null else s.latencyMs, s.samples,
            if (s.ring.size >= MIN_RING) s.ring.sorted()[(s.ring.size * 0.95).toInt().coerceAtMost(s.ring.size - 1)] else null
        )
    }.sortedBy { it.key }

    @Synchronized
    fun export(now: Long): String = buildString {
        for ((k, s) in map) {
            if (s.samples == 0 && s.ok == 0.0 && s.fail == 0.0) continue
            append(k).append('\t').append(s.ok).append('\t').append(s.fail).append('\t').append(s.latencyMs).append('\t')
                .append(s.samples).append('\t').append(now).append('\t').append(s.ring.joinToString(",")).append('\n')
        }
    }

    @Synchronized
    fun import(raw: String, now: Long) {
        for (line in raw.lineSequence()) {
            val f = line.split('\t')
            if (f.size < 6) continue
            val updated = f[5].toLongOrNull() ?: continue
            val age = (now - updated).coerceAtLeast(0L)
            if (age > MAX_AGE_MS) continue
            val k = Math.pow(0.5, age.toDouble() / HALF_LIFE_MS)
            val s = S()
            s.ok = (f[1].toDoubleOrNull() ?: 0.0) * k
            s.fail = (f[2].toDoubleOrNull() ?: 0.0) * k
            s.latencyMs = f[3].toLongOrNull() ?: 0L
            s.samples = Math.round((f[4].toIntOrNull() ?: 0) * k).toInt()
            val ring = if (f.size > 6 && f[6].isNotEmpty()) f[6].split(',').mapNotNull { it.toLongOrNull() } else emptyList()
            s.ring.addAll(ring.takeLast(Math.round(ring.size * k).toInt()))
            if (s.samples == 0) s.ring.clear()
            map[f[0]] = s
        }
    }

    companion object {
        const val RING = 20
        const val MIN_RING = 5
        const val LAST_RESORT_ATTEMPTS = 5
        const val HALF_LIFE_MS = 3L * 24 * 3_600_000
        const val MAX_AGE_MS = 14L * 24 * 3_600_000
    }
}

object HealthSeed {
    fun parse(text: String): Map<String, Long> = try {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
        val lat = obj?.get("latency") as? kotlinx.serialization.json.JsonObject
        lat.orEmpty().mapNotNull { (k, v) ->
            (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()?.let { k to it }
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

object OutputQuality {
    private val refusal = Regex("^(i('| a)m sorry|sorry|as an ai)[^\\n]{0,40}$", RegexOption.IGNORE_CASE)

    fun isPoor(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2) return true
        if (refusal.matches(t)) return true
        val last = t.last()
        if (t.length < 40 && (last == ',' || last == ':' || last == '\u060C')) return true
        return false
    }
}
