package com.freellm.chat.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

enum class SideState { RUNNING, DONE, FAILED }

data class CompareSide(
    val target: Target,
    val state: SideState = SideState.RUNNING,
    val text: String = "",
    val error: String? = null
)

data class CompareRequest(
    val question: String,
    val a: Target,
    val b: Target,
    val messages: List<ChatMsg> = listOf(ChatMsg("user", question)),
    val maxTokens: Int? = null
)

data class CompareResult(val a: CompareSide, val b: CompareSide) {
    val finished: Boolean get() = a.state != SideState.RUNNING && b.state != SideState.RUNNING

    val doneCount: Int get() = listOf(a, b).count { it.state == SideState.DONE }

    fun side(first: Boolean): CompareSide = if (first) a else b

    fun with(first: Boolean, s: CompareSide): CompareResult = if (first) copy(a = s) else copy(b = s)
}

class Comparer(private val router: Router) {
    fun run(req: CompareRequest): Flow<CompareResult> = channelFlow {
        val lock = Any()
        var current = CompareResult(CompareSide(req.a), CompareSide(req.b))
        send(current)

        suspend fun update(first: Boolean, change: (CompareSide) -> CompareSide) {
            val snap = synchronized(lock) {
                current = current.with(first, change(current.side(first)))
                current
            }
            send(snap)
        }

        for (first in listOf(true, false)) {
            val target = if (first) req.a else req.b
            launch {
                val rr = RouteRequest(
                    messages = req.messages,
                    mode = ChatMode.PINNED,
                    pinnedKey = target.key,
                    candidates = listOf(target),
                    maxTokens = req.maxTokens
                )
                try {
                    router.stream(rr).collect { ev ->
                        when (ev) {
                            is RouteEvent.Delta -> update(first) { it.copy(text = it.text + ev.text) }
                            is RouteEvent.Restart -> update(first) { it.copy(text = "") }
                            is RouteEvent.Done -> update(first) { it.copy(state = SideState.DONE) }
                            is RouteEvent.Failed -> update(first) { it.copy(state = SideState.FAILED, error = ev.message) }
                            else -> Unit
                        }
                    }
                    update(first) {
                        if (it.state != SideState.RUNNING) it
                        else it.copy(state = if (it.text.isBlank()) SideState.FAILED else SideState.DONE)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    update(first) { it.copy(state = SideState.FAILED, error = e.message.orEmpty()) }
                }
            }
        }
    }
}

object CompareChoice {
    fun apply(
        result: CompareResult,
        winnerFirst: Boolean,
        arabic: Boolean,
        rate: (String, Boolean) -> Unit,
        rateArabic: (String, Boolean) -> Unit
    ) {
        val winner = result.side(winnerFirst)
        val loser = result.side(!winnerFirst)
        if (winner.state != SideState.DONE) return
        rate(winner.target.key, true)
        if (arabic) rateArabic(winner.target.key, true)
        if (loser.state == SideState.DONE) {
            rate(loser.target.key, false)
            if (arabic) rateArabic(loser.target.key, false)
        }
    }
}

object Merger {
    const val MAX_TOKENS = 1600
    const val TIMEOUT_MS = 60_000L
    const val MAX_ANSWER_CHARS = 6000

    const val SYSTEM =
        "You merge two candidate answers to the same question. Summarize the best of both, correct any conflict between them, " +
            "and never invent anything that neither answer contains. Reply in the language of the question and output only the final answer. " +
            "لخّص أفضل ما في الجوابين وصحّح أي تعارض بينهما دون اختراع أي معلومة جديدة."

    fun canMerge(r: CompareResult): Boolean =
        r.a.state == SideState.DONE && r.b.state == SideState.DONE && r.a.text.isNotBlank() && r.b.text.isNotBlank()

    fun prompt(question: String, a: String, b: String): String =
        "<question>\n" + question.take(MAX_ANSWER_CHARS) + "\n</question>\n" +
            "<answer_1>\n" + a.take(MAX_ANSWER_CHARS) + "\n</answer_1>\n" +
            "<answer_2>\n" + b.take(MAX_ANSWER_CHARS) + "\n</answer_2>"

    suspend fun run(side: SideTasks, candidates: List<Target>, r: CompareResult, question: String): String? {
        if (!canMerge(r)) return null
        return side.run(
            candidates,
            SYSTEM,
            prompt(question, r.a.text, r.b.text),
            MAX_TOKENS,
            2,
            TIMEOUT_MS,
            StrongPicker::pick
        )
    }
}
