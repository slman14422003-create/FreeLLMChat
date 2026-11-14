package com.freellm.chat.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object QueryBuilder {
    const val MAX_QUERIES = 3
    const val MAX_WORDS = 12

    private val techHint = Regex(
        "\\b(api|sdk|error|exception|bug|crash|compile|gradle|kotlin|java|python|javascript|typescript|android|ios|linux|docker|git|sql|http|json|regex|npm|pip|library|framework|function|class|server|database|compose|react|flutter|rust|golang|cpp)\\b|" +
            "برمجة|كود|خطأ|مكتبة|دالة|تطبيق|سيرفر|قاعدة بيانات",
        RegexOption.IGNORE_CASE
    )

    private val fillerEn = setOf(
        "please", "pls", "can", "could", "would", "you", "tell", "me", "about", "what", "is", "are", "the", "a", "an",
        "do", "does", "i", "want", "to", "know", "give", "show", "find", "search", "for", "of", "how", "hey", "hi", "hello",
        "kindly", "just", "some", "any", "info", "information", "explain", "and"
    )

    private val fillerAr = setOf(
        "من", "فضلك", "لو", "سمحت", "هل", "يمكنك", "ممكن", "اريد", "أريد", "ابحث", "ابحثلي", "عن", "ما", "هو", "هي", "ماهو", "ماهي",
        "اخبرني", "أخبرني", "قل", "لي", "اعطني", "أعطني", "اعرف", "أعرف", "ان", "أن", "في", "على", "الى", "إلى", "كيف", "ايش", "شو",
        "وش", "لو", "سمحتي", "معلومات", "اشرح", "و", "يا", "اخي", "أخي", "عندي", "سؤال", "اسأل", "اسال"
    )

    fun isTechnical(question: String, type: QType): Boolean = type == QType.CODE || techHint.containsMatchIn(question)

    fun system(technical: Boolean): String =
        "Write 1 to $MAX_QUERIES short web search queries (at most 8 words each) that would find the current facts needed to answer the user's message. " +
            (if (technical) "Write them in English. " else "Write them in the language of the message. ") +
            "One query per line, no numbering, no quotes, no explanation."

    fun parse(raw: String): List<String> {
        val seen = LinkedHashSet<String>()
        for (line in raw.lines()) {
            val q = line.trim()
                .trimStart('-', '*', '•', ' ', '\t')
                .replace(Regex("^\\d+[.)\\-:]\\s*"), "")
                .trim('"', '\'', '`', '«', '»', ' ')
            if (q.length < 3 || q.length > 120) continue
            if (q.endsWith(":")) continue
            seen.add(q)
            if (seen.size >= MAX_QUERIES) break
        }
        return seen.toList()
    }

    fun fallback(question: String): List<String> {
        val words = question.replace(Regex("[\\p{Punct}؟،؛]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val kept = words.filter { it.lowercase() !in fillerEn && it !in fillerAr }
        val chosen = (if (kept.size >= 2) kept else words).take(MAX_WORDS)
        val q = chosen.joinToString(" ").trim()
        return if (q.isEmpty()) emptyList() else listOf(q)
    }

    suspend fun generate(side: SideTasks, candidates: List<Target>, question: String, technical: Boolean): List<String> {
        val raw = side.run(candidates, system(technical), question.take(600), 80, 2, 8_000L)
        val parsed = if (raw == null) emptyList() else parse(raw)
        return parsed.ifEmpty { fallback(question) }
    }
}

interface SearchCacheStore {
    suspend fun get(key: String, ttlMs: Long, now: Long): String?
    suspend fun put(key: String, payload: String, now: Long, ttlMs: Long)
}

data class SearchOutcome(
    val bundle: SearchBundle?,
    val error: LlmException? = null,
    val fromCache: Int = 0,
    val calls: Int = 0
)

class WebSearchPipeline(
    private val side: SideTasks,
    private val cache: SearchCacheStore,
    private val quota: SearchQuota,
    private val fetch: suspend (String) -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun run(
        provider: SearchProvider,
        limit: SearchLimit,
        question: String,
        candidates: List<Target>,
        technical: Boolean,
        ttlMs: Long,
        resultCount: Int = RESULTS,
        pages: Int = PAGES,
        maxQueries: Int = QueryBuilder.MAX_QUERIES
    ): SearchOutcome {
        val queries = QueryBuilder.generate(side, candidates, question, technical).take(maxQueries.coerceAtLeast(1))
        if (queries.isEmpty()) return SearchOutcome(null)
        val all = ArrayList<SearchResult>()
        var error: LlmException? = null
        var fromCache = 0
        var calls = 0
        for (q in queries) {
            val key = SearchCacheKey.of(provider.kind, q)
            val hit = cache.get(key, ttlMs, clock())?.let { SearchParsers.decode(it) }
            if (hit != null) {
                fromCache++
                all.addAll(hit)
                continue
            }
            if (quota.remaining(provider.kind, limit, clock()) <= 0) {
                if (error == null) error = LlmException(ErrorKind.RATE_LIMIT, message = "search quota")
                continue
            }
            try {
                val found = provider.search(q, resultCount)
                quota.record(provider.kind, limit, clock())
                calls++
                if (found.isNotEmpty()) cache.put(key, SearchParsers.encode(found), clock(), ttlMs)
                all.addAll(found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmException) {
                error = e
                if (e.kind == ErrorKind.AUTH || e.kind == ErrorKind.PAYMENT || e.kind == ErrorKind.RATE_LIMIT ||
                    e.kind == ErrorKind.NETWORK || e.kind == ErrorKind.TIMEOUT
                ) break
            } catch (e: Throwable) {
                error = LlmException(ErrorKind.NETWORK, message = e.javaClass.simpleName)
                break
            }
        }
        val ranked = SourceRank.rank(all, resultCount, java.time.LocalDate.now().year)
        if (ranked.isEmpty()) return SearchOutcome(null, error, fromCache, calls)
        val top = ranked.take(pages)
        val texts = coroutineScope {
            top.map { r ->
                async {
                    try {
                        if (UrlPolicy.allowedUrl(r.url)) fetch(r.url) else null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        null
                    }
                }
            }.awaitAll()
        }
        val items = ranked.mapIndexed { i, r ->
            val page = if (i < texts.size) texts[i]?.takeIf { it.isNotBlank() } else null
            if (page != null) SourceItem(r.title, r.url, Excerpt.fit(page, question), true)
            else SourceItem(r.title, r.url, r.snippet, false)
        }.filter { it.text.isNotBlank() }
        if (items.isEmpty()) return SearchOutcome(null, error, fromCache, calls)
        return SearchOutcome(SearchBundle(queries, items), error, fromCache, calls)
    }

    companion object {
        const val RESULTS = 5
        const val PAGES = 3
    }
}
