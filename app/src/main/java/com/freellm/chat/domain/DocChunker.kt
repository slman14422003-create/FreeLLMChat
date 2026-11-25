package com.freellm.chat.domain

import kotlin.math.ln

object DocChunker {
    data class Piece(val index: Int, val text: String)

    fun chunk(text: String, target: Int = 1200): List<Piece> {
        val out = ArrayList<Piece>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotBlank()) out.add(Piece(out.size, cur.toString().trim()))
            cur.setLength(0)
        }
        for (para in text.split(Regex("\\n\\s*\\n"))) {
            var p = para
            while (p.length > target) {
                flush()
                out.add(Piece(out.size, p.take(target)))
                p = p.drop(target)
            }
            if (cur.length + p.length + 2 > target) flush()
            if (p.isNotBlank()) cur.append(p).append("\n\n")
        }
        flush()
        return out
    }

    fun keywords(question: String): List<String> =
        LocalBrain.tokens(question).filter { it.length >= 3 }.distinct()

    fun select(text: String, question: String, budgetTokens: Int): String {
        if (ContextTrimmer.estimate(text) <= budgetTokens) return text
        val pieces = chunk(text)
        if (pieces.isEmpty()) return ""
        val kws = keywords(question)
        val bags = pieces.map { p -> LocalBrain.tokens(p.text).groupingBy { it }.eachCount() }
        val n = pieces.size.toDouble()
        val idf = kws.associateWith { k -> ln((n + 1) / (bags.count { it.containsKey(k) } + 1)) + 1.0 }
        val scores = pieces.indices.map { i ->
            var s = 0.0
            kws.forEach { k -> s += (bags[i][k] ?: 0) * (idf[k] ?: 1.0) }
            if (i == 0) s += 0.5
            s
        }
        val order = if (scores.any { it > 0.5 }) {
            pieces.indices.sortedByDescending { scores[it] }
        } else {
            pieces.indices.toList()
        }
        val picked = ArrayList<Int>()
        var used = 0
        for (i in order) {
            val c = ContextTrimmer.estimate(pieces[i].text)
            if (used + c > budgetTokens) continue
            picked.add(i)
            used += c
        }
        if (picked.isEmpty()) return pieces[order.first()].text.take((budgetTokens * 3.5).toInt()) + "\n[…]"
        picked.sort()
        val sb = StringBuilder()
        var prev = -1
        for (i in picked) {
            if (prev >= 0 && i != prev + 1) sb.append("[…]\n")
            sb.append(pieces[i].text).append('\n')
            prev = i
        }
        if (picked.last() != pieces.size - 1) sb.append("[…]")
        return sb.toString().trim()
    }
}
