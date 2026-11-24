package com.freellm.chat.domain

object GoldenCheck {
    fun pass(answer: String, must: List<String>, mustNot: List<String>): Boolean {
        val a = LocalBrain.normalize(answer)
        return must.all { a.contains(LocalBrain.normalize(it)) } && mustNot.none { a.contains(LocalBrain.normalize(it)) }
    }
}
