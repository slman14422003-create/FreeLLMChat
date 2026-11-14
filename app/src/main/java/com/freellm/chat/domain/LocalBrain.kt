package com.freellm.chat.domain

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One learned question/answer pair. */
data class BrainEntry(
    val id: Long,
    val question: String,
    val answer: String,
    val createdAt: Long,
    val score: Int = 1,
    val hits: Int = 0,
    val expiresAt: Long = createdAt + LocalBrain.DEFAULT_TTL_MS
)

data class BrainMatch(val entry: BrainEntry, val similarity: Double)

/**
 * Small on-device "model" that learns from every question/answer pair.
 *
 * It is a retrieval learner, not a neural network: questions are normalised (Arabic + Latin),
 * turned into word + character n-gram features and ranked with TF-IDF cosine similarity.
 * Feedback adjusts how much an entry is trusted. Pure Kotlin so it can be unit-tested.
 */
class LocalBrain(initial: List<BrainEntry> = emptyList(), private val capacity: Int = 1500) {
    private val entries = ArrayList<BrainEntry>(initial)
    private var vectors = ArrayList<Map<String, Double>>()
    private var idf = HashMap<String, Double>()
    private var dirty = true
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    @Synchronized fun size(): Int = entries.size

    @Synchronized fun all(): List<BrainEntry> = entries.toList()

    @Synchronized
    fun learn(question: String, answer: String, now: Long = System.currentTimeMillis()): BrainEntry? {
        val q = question.trim()
        val a = answer.trim()
        if (q.length < 3 || a.length < 2 || a.length > MAX_ANSWER) return null
        if (isTimeSensitive(q) || contextDependent(q)) return null
        val nq = normalize(q)
        val dup = entries.indexOfFirst { normalize(it.question) == nq }
        if (dup >= 0) {
            val old = entries[dup]
            val updated = old.copy(answer = a, createdAt = now, score = old.score + 1, expiresAt = now + DEFAULT_TTL_MS)
            entries[dup] = updated
            dirty = true
            return updated
        }
        val e = BrainEntry(nextId++, q, a, now, expiresAt = now + DEFAULT_TTL_MS)
        entries.add(e)
        while (entries.size > capacity) {
            val victim = entries.indices.minByOrNull { entries[it].score * 100_000L + entries[it].hits * 1000L + it }
                ?: break
            entries.removeAt(victim)
        }
        dirty = true
        return e
    }

    @Synchronized
    fun feedback(id: Long, good: Boolean): Boolean {
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0) return false
        if (good) entries[i] = entries[i].copy(score = entries[i].score + 3)
        else entries.removeAt(i)
        dirty = true
        return true
    }

    @Synchronized
    fun forgetQuestion(question: String) {
        val nq = normalize(question)
        if (entries.removeAll { normalize(it.question) == nq }) dirty = true
    }

    @Synchronized
    fun remove(id: Long): Boolean {
        val removed = entries.removeAll { it.id == id }
        if (removed) dirty = true
        return removed
    }

    @Synchronized
    fun update(id: Long, question: String, answer: String): Boolean {
        val q = question.trim()
        val a = answer.trim()
        if (q.length < 3 || a.isEmpty()) return false
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0) return false
        entries[i] = entries[i].copy(question = q, answer = a)
        dirty = true
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
        dirty = true
    }

    @Synchronized
    fun markUsed(id: Long) {
        val i = entries.indexOfFirst { it.id == id }
        if (i >= 0) entries[i] = entries[i].copy(hits = entries[i].hits + 1)
    }

    @Synchronized
    fun search(query: String, limit: Int = 3, minSimilarity: Double = 0.35, now: Long = System.currentTimeMillis()): List<BrainMatch> {
        if (entries.isEmpty()) return emptyList()
        rebuild()
        val qv = vectorize(query, useIdf = true)
        if (qv.isEmpty()) return emptyList()
        val out = ArrayList<BrainMatch>()
        for (i in entries.indices) {
            if (entries[i].expiresAt <= now) continue
            val sim = cosine(qv, vectors[i])
            if (sim >= minSimilarity) {
                val trust = 1.0 + min(entries[i].score, 10) * 0.01
                out.add(BrainMatch(entries[i], min(1.0, sim * trust)))
            }
        }
        return out.sortedByDescending { it.similarity }.take(limit)
    }

    fun best(query: String, minSimilarity: Double, now: Long = System.currentTimeMillis()): BrainMatch? =
        search(query, 1, minSimilarity, now).firstOrNull()

    fun bestStrict(query: String, minSimilarity: Double, now: Long = System.currentTimeMillis()): BrainMatch? =
        search(query, 5, minSimilarity, now).firstOrNull { coversTokens(query, it.entry.question) }

    private fun rebuild() {
        if (!dirty) return
        val df = HashMap<String, Int>()
        val raw = entries.map { vectorize(it.question, useIdf = false) }
        raw.forEach { v -> v.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = max(1, entries.size).toDouble()
        idf = HashMap()
        df.forEach { (t, c) -> idf[t] = ln((n + 1) / (c + 1)) + 1.0 }
        vectors = ArrayList(raw.map { v -> weigh(v) })
        dirty = false
    }

    private fun weigh(v: Map<String, Double>): Map<String, Double> {
        val w = HashMap<String, Double>()
        var norm = 0.0
        v.forEach { (t, f) ->
            val x = f * (idf[t] ?: 1.0)
            w[t] = x
            norm += x * x
        }
        val d = sqrt(norm)
        if (d > 0) w.keys.toList().forEach { w[it] = w[it]!! / d }
        return w
    }

    private fun vectorize(text: String, useIdf: Boolean): Map<String, Double> {
        val words = tokens(text)
        val f = HashMap<String, Double>()
        words.forEach { f["w:$it"] = (f["w:$it"] ?: 0.0) + 1.0 }
        for (w in words) {
            if (w.length < 4) continue
            val p = "#$w#"
            for (i in 0..p.length - 3) {
                val g = "c:" + p.substring(i, i + 3)
                f[g] = (f[g] ?: 0.0) + 0.35
            }
        }
        for (i in 0 until words.size - 1) {
            val g = "b:${words[i]}_${words[i + 1]}"
            f[g] = (f[g] ?: 0.0) + 0.6
        }
        return if (useIdf) weigh(f) else f
    }

    private fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        val (s, l) = if (a.size <= b.size) a to b else b to a
        var dot = 0.0
        s.forEach { (k, v) -> l[k]?.let { dot += v * it } }
        return dot
    }

    companion object {
        private const val MAX_ANSWER = 6000
        const val DEFAULT_TTL_MS = 30L * 24 * 3_600_000L

        private val stop = setOf(
            "the", "a", "an", "is", "are", "of", "to", "in", "on", "and", "or", "for", "what", "how", "do", "does",
            "me", "my", "i", "you", "it", "this", "that", "please",
            "ما", "ماذا", "هو", "هي", "في", "من", "على", "الى", "إلى", "عن", "هل", "كيف", "لي", "لو", "ممكن",
            "اريد", "أريد", "اكتب", "قل", "لماذا", "هذا", "هذه", "ذلك", "و", "يا"
        )

        private val timeWords = Regex(
            "\\b(today|now|tonight|tomorrow|latest|current|news|weather|price|score)\\b|اليوم|الآن|الان|الطقس|أخبار|اخبار|سعر|الأسعار|نتيجة|مباراة|الساعة",
            RegexOption.IGNORE_CASE
        )

        private val numberToken = Regex("\\d+(?:[.,]\\d+)?")
        private val quotedToken = Regex("\"([^\"]+)\"|'([^']+)'|\u00AB([^\u00BB]+)\u00BB|\u201C([^\u201D]+)\u201D")
        private val referenceWords = Regex(
            "\\b(it|this|that|these|those|they|them|him|her|above|previous|same|again|more|continue|also|another|else)\\b|" +
                "\u0647\u0630\u0627|\u0647\u0630\u0647|\u0630\u0644\u0643|\u062a\u0644\u0643|\u0647\u0624\u0644\u0627\u0621|\u0627\u0644\u0633\u0627\u0628\u0642|\u0646\u0641\u0633|\u0627\u0643\u0645\u0644|\u0623\u0643\u0645\u0644|\u0643\u0645\u0644|\u0627\u0644\u0645\u0632\u064a\u062f|\u0623\u064a\u0636\u0627|\u0627\u064a\u0636\u0627",
            RegexOption.IGNORE_CASE
        )
        private val leadingConnector = Regex("^\\s*(and|but|so|then|what about|how about|why|\u0648|\u0644\u0643\u0646|\u0641\u0645\u0627\u0630\u0627|\u0648\u0645\u0627\u0630\u0627|\u0648\u0644\u0645\u0627\u0630\u0627)\\b", RegexOption.IGNORE_CASE)

        fun contextDependent(q: String): Boolean {
            val t = q.trim()
            return referenceWords.containsMatchIn(t) || leadingConnector.containsMatchIn(t)
        }

        fun coversTokens(query: String, matched: String): Boolean {
            val m = normalize(matched)
            val nums = numberToken.findAll(query).map { it.value.replace(',', '.') }.toList()
            if (nums.any { n -> !Regex("(^|\\s)" + Regex.escape(n) + "(\\s|$)").containsMatchIn(m.replace(',', '.')) }) return false
            val quoted = quotedToken.findAll(query).map { r -> r.groupValues.drop(1).first { it.isNotEmpty() } }.toList()
            return quoted.all { normalize(it).let { n -> n.isEmpty() || m.contains(n) } }
        }

        fun isTimeSensitive(q: String, year: Int = java.time.Year.now().value): Boolean =
            timeWords.containsMatchIn(q) || QuestionClassifier.needsFresh(q, year)

        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s.lowercase()) {
                when {
                    ch in '\u064B'..'\u065F' || ch == '\u0640' -> Unit
                    ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
                    ch == 'ى' -> sb.append('ي')
                    ch == 'ة' -> sb.append('ه')
                    ch == 'ؤ' -> sb.append('و')
                    ch == 'ئ' -> sb.append('ي')
                    ch.isLetterOrDigit() -> sb.append(ch)
                    else -> sb.append(' ')
                }
            }
            return sb.toString().trim().replace(Regex("\\s+"), " ")
        }

        internal fun stem(w: String): String {
            var x = w
            if (x.length > 4 && x.startsWith("ال")) x = x.substring(2)
            if (x.length > 5 && (x.startsWith("وال") || x.startsWith("بال") || x.startsWith("كال") || x.startsWith("فال"))) x = x.substring(3)
            if (x.length > 4 && (x.endsWith("ات") || x.endsWith("ون") || x.endsWith("ين") || x.endsWith("ان"))) x = x.dropLast(2)
            if (x.length > 4 && x.endsWith("ه")) x = x.dropLast(1)
            if (x.length > 4 && x.endsWith("s") && !x.endsWith("ss")) x = x.dropLast(1)
            return x
        }

        fun tokens(text: String): List<String> =
            normalize(text).split(' ').filter { it.isNotEmpty() && it !in stop }.map { Concepts.canonical(stem(it)) }.filter { it.isNotEmpty() }
    }
}
