package com.freellm.chat.domain

import java.security.MessageDigest

object CacheKey {
    fun of(question: String, context: String): String {
        val raw = LocalBrain.normalize(question) + "\u0001" + LocalBrain.normalize(context).take(400)
        val d = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    fun cacheable(question: String): Boolean {
        val q = question.trim()
        return q.length in 3..600 && !LocalBrain.isTimeSensitive(q)
    }

    fun contextOf(previous: List<String>): String = previous.takeLast(2).joinToString("\u0002") { it.take(200) }
}

object CachePolicy {
    const val HOUR_MS = 3_600_000L

    fun ttlMs(hours: Int): Long = hours.coerceIn(1, 720) * HOUR_MS

    fun fresh(createdAt: Long, now: Long, ttlMs: Long): Boolean = now - createdAt in 0..ttlMs
}
