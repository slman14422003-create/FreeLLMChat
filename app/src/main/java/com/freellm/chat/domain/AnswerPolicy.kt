package com.freellm.chat.domain

object SourceRank {
    private val officialSuffix = Regex("(^|\\.)(gov|edu|mil|int|ac)(\\.[a-z]{2})?$")
    private val docPrefix = Regex("^(docs|developer|developers|support|help|learn|dev|api|kb)\\.")
    private val reputable = setOf(
        "wikipedia.org", "reuters.com", "apnews.com", "bbc.com", "bbc.co.uk", "who.int", "nature.com", "arxiv.org",
        "github.com", "stackoverflow.com", "developer.android.com", "kotlinlang.org"
    )
    private val weak = setOf("pinterest.com", "quora.com", "facebook.com", "instagram.com", "tiktok.com", "x.com", "twitter.com")

    fun isOfficial(url: String): Boolean {
        val h = UrlPolicy.host(url)
        return officialSuffix.containsMatchIn(h) || docPrefix.containsMatchIn(h)
    }

    fun weight(url: String): Double {
        val h = UrlPolicy.host(url)
        if (h.isEmpty()) return 0.0
        var w = 0.0
        if (officialSuffix.containsMatchIn(h)) w += 3.0
        if (docPrefix.containsMatchIn(h)) w += 2.0
        if (reputable.any { h == it || h.endsWith(".$it") }) w += 1.0
        if (weak.any { h == it || h.endsWith(".$it") }) w -= 2.0
        return w
    }

    fun rank(results: List<SearchResult>, max: Int, year: Int? = null): List<SearchResult> {
        val seen = HashSet<String>()
        val unique = ArrayList<SearchResult>()
        for (r in results) {
            if (!UrlPolicy.publicUrl(r.url)) continue
            if (!seen.add(UrlPolicy.key(r.url))) continue
            unique.add(r)
        }
        val scored = unique.mapIndexed { i, r ->
            var score = weight(r.url) - i * 0.4
            if (year != null) {
                val ys = year.toString()
                val last = (year - 1).toString()
                if (r.title.contains(ys) || r.snippet.contains(ys) || r.url.contains(ys)) score += 0.8
                else if (r.title.contains(last) || r.snippet.contains(last) || r.url.contains(last)) score += 0.3
            }
            Triple(i, score, r)
        }
        return scored.sortedWith(compareByDescending<Triple<Int, Double, SearchResult>> { it.second }.thenBy { it.first })
            .take(max)
            .map { it.third }
    }
}

data class AnswerSpec(
    val hasSources: Boolean,
    val style: String,
    val questionArabic: Boolean,
    val sourcesArabic: Boolean?,
    val official: Boolean,
    val sourceCount: Int
)

object AnswerPolicy {
    fun maxSources(style: String): Int = when (style) {
        "concise" -> 3
        "detailed", "teacher" -> 5
        else -> 4
    }

    fun template(style: String): String = when (style) {
        "concise" ->
            "Format: a direct answer in one to three sentences, details only if essential, then a last line \"Sources: [n]\"."
        "detailed", "teacher" ->
            "Format: a direct answer first, then structured details with short headings or bullets, then a last line \"Sources: [n], [n]\"."
        else ->
            "Format: a direct answer in one or two sentences first, then the details, then a last line \"Sources: [n], [n]\"."
    }

    fun language(questionArabic: Boolean, sourcesArabic: Boolean?): String {
        if (sourcesArabic == null || sourcesArabic == questionArabic) return ""
        return if (questionArabic) "Answer in Arabic even though the sources are in another language."
        else "Answer in the language of the question (not Arabic) even though the sources are in Arabic."
    }

    fun build(spec: AnswerSpec): String {
        if (!spec.hasSources) return ""
        val parts = ArrayList<String>()
        parts.add(SourcesPrompt.SYSTEM)
        parts.add(
            "Put the source number like [1] right after every fact taken from <sources>, use only numbers that exist, " +
                "and if the sources do not contain the answer say you did not find it (لم أجد) instead of guessing."
        )
        if (spec.sourceCount > 1) {
            parts.add(
                "If sources disagree, state the difference briefly" +
                    (if (spec.official) " and prefer official and more recent sources." else " and prefer the more recent source.")
            )
        }
        parts.add(template(spec.style))
        val lang = language(spec.questionArabic, spec.sourcesArabic)
        if (lang.isNotEmpty()) parts.add(lang)
        return parts.joinToString("\n")
    }

    fun spec(question: String, style: String, bundle: SearchBundle?): AnswerSpec {
        if (bundle == null || bundle.items.isEmpty()) return AnswerSpec(false, style, false, null, false, 0)
        val qAr = QuestionClassifier.arabicRatio(question) > 0.5
        val sample = bundle.items.joinToString(" ") { it.text.take(400) }.take(3000)
        val sAr = QuestionClassifier.arabicRatio(sample) > 0.5
        return AnswerSpec(true, style, qAr, sAr, bundle.items.any { SourceRank.isOfficial(it.url) }, bundle.items.size)
    }
}

object TemperaturePolicy {
    const val FACTUAL = 0.2
    const val INFO = 0.3
    const val CREATIVE = 0.9

    fun pick(kind: TaskKind, cls: Classification, hasSources: Boolean): Double = when {
        kind == TaskKind.WRITE -> CREATIVE
        hasSources || cls.needsFresh || cls.type == QType.CODE || kind == TaskKind.CODE || kind == TaskKind.REASON -> FACTUAL
        else -> INFO
    }
}

data class VerifyResult(val unsupported: List<String>)

object Verifier {
    const val MAX_CLAIMS = 4

    const val SYSTEM =
        "You check an answer against the <sources>. List every claim in the answer that the sources do not support or contradict, " +
            "one per line starting with \"- \". If every claim is supported reply exactly OK. Output nothing else."

    fun shouldVerify(enabled: Boolean, needsFresh: Boolean, difficulty: Difficulty, hasSources: Boolean, answered: Boolean): Boolean =
        enabled && hasSources && answered && (needsFresh || difficulty == Difficulty.HARD)

    fun prompt(question: String, sources: String, answer: String): String =
        "<question>\n" + question.take(800) + "\n</question>\n" + sources + "\n<answer>\n" + answer.take(5000) + "\n</answer>"

    fun parse(reply: String?): VerifyResult? {
        if (reply == null) return null
        val text = reply.trim()
        if (text.isEmpty()) return null
        val claims = text.lines()
            .map { it.trim() }
            .filter { it.startsWith("-") || it.startsWith("*") || it.startsWith("•") || Regex("^\\d+[.)]\\s").containsMatchIn(it) }
            .map { it.trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim().take(200) }
            .filter { it.isNotEmpty() && !it.equals("OK", ignoreCase = true) }
            .take(MAX_CLAIMS)
        return VerifyResult(claims)
    }

    fun reviewers(candidates: List<Target>, answeredKey: String?): List<Target> =
        candidates.filter { it.key != answeredKey }

    suspend fun run(
        side: SideTasks,
        candidates: List<Target>,
        answeredKey: String?,
        question: String,
        sources: String,
        answer: String
    ): VerifyResult? {
        val pool = reviewers(candidates, answeredKey)
        if (pool.isEmpty()) return null
        val reply = side.run(pool, SYSTEM, prompt(question, sources, answer), 300, 1, 30_000L, StrongPicker::pick, 0.0)
        return parse(reply)
    }

    fun warning(claims: List<String>): String = "⚠ " + claims.joinToString("؛ ").take(240)
}
