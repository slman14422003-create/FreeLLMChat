package com.freellm.chat.domain

object IntentMatch {
    private val negations = setOf("not", "no", "never", "without", "dont", "cant", "wont", "isnt", "doesnt", "didnt", "arent", "\u0628\u062F\u0648\u0646", "\u0644\u0627", "\u0644\u064A\u0633", "\u063A\u064A\u0631", "\u0645\u0648")
    private val numbers = Regex("\\d+")

    private fun clean(s: String): String = LocalBrain.normalize(s.replace("'", "").replace("\u2019", ""))

    fun same(query: String, matched: String): Boolean {
        val q = clean(query)
        val m = clean(matched)
        val qw = q.split(' ').filter { it.isNotEmpty() }
        val mw = m.split(' ').filter { it.isNotEmpty() }
        if (qw.isEmpty() || mw.isEmpty()) return false
        if (qw.any { it in negations } != mw.any { it in negations }) return false
        if (numbers.findAll(q).map { it.value }.toList() != numbers.findAll(m).map { it.value }.toList()) return false
        val qt = LocalBrain.tokens(query).toSet()
        val mt = LocalBrain.tokens(matched).toSet()
        if (qt.isEmpty() || mt.isEmpty()) return false
        val jaccard = qt.intersect(mt).size.toDouble() / qt.union(mt).size.toDouble()
        if (jaccard < 0.7) return false
        return minOf(q.length, m.length) * 2 >= maxOf(q.length, m.length)
    }
}
