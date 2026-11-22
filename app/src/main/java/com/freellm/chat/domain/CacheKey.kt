package com.freellm.chat.domain

import java.security.MessageDigest

// Pure helpers for the opt-in response cache. Model-agnostic on purpose.
object CacheKey {
    const val TTL_MS = 24L * 60 * 60 * 1000
    const val MAX_PROMPT_CHARS = 200

    private val ws = Regex("\\s+")
    private val personal = Regex("\\b(my|me|i|mine|myself|today|now|tomorrow|yesterday)\\b", RegexOption.IGNORE_CASE)

    fun normalize(prompt: String): String =
        ws.replace(prompt.trim().lowercase(), " ").trimEnd('?', '.', '!', ' ')

    fun cacheable(prompt: String, usedTools: Boolean, hasAttachments: Boolean): Boolean {
        if (usedTools || hasAttachments) return false
        val n = normalize(prompt)
        return n.isNotEmpty() && n.length <= MAX_PROMPT_CHARS && !personal.containsMatchIn(n)
    }

    fun hash(prompt: String): String =
        MessageDigest.getInstance("SHA-256").digest(normalize(prompt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun fresh(storedAtMs: Long, nowMs: Long): Boolean = nowMs - storedAtMs in 0 until TTL_MS
}
