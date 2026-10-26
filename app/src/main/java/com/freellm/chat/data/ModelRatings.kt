package com.freellm.chat.data

import com.freellm.chat.domain.UsageStore

/** Thumbs up/down totals per model key, used to rank models inside each mode. */
class ModelRatings(private val store: UsageStore) {
    private fun k(key: String) = "rating:" + key

    fun score(key: String): Int = store.read(k(key))?.toIntOrNull() ?: 0

    fun rate(key: String, good: Boolean) {
        val next = (score(key) + if (good) 1 else -1).coerceIn(-20, 20)
        store.write(k(key), next.toString())
    }
}
