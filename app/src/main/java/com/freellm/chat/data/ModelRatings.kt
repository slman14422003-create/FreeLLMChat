package com.freellm.chat.data

import com.freellm.chat.domain.UsageStore

class ModelRatings(private val store: UsageStore) {
    private fun k(key: String) = "rating:" + key
    private fun ka(key: String) = "rating_ar:" + key
    private fun kq(key: String) = "quality:" + key

    fun score(key: String): Int = store.read(k(key))?.toIntOrNull() ?: 0

    fun arabicScore(key: String): Int = store.read(ka(key))?.toIntOrNull() ?: 0

    fun rate(key: String, good: Boolean) {
        val next = (score(key) + if (good) 1 else -1).coerceIn(-20, 20)
        store.write(k(key), next.toString())
    }

    fun rateArabic(key: String, good: Boolean) {
        val next = (arabicScore(key) + if (good) 1 else -1).coerceIn(-20, 20)
        store.write(ka(key), next.toString())
    }

    private class Q(val ok: Int, val fail: Int, val latency: Long)

    private fun quality(key: String): Q? {
        val p = store.read(kq(key))?.split("|") ?: return null
        if (p.size != 3) return null
        return Q(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null, p[2].toLongOrNull() ?: return null)
    }

    fun outcome(key: String, ok: Boolean, latencyMs: Long?) {
        val q = quality(key)
        var o = q?.ok ?: 0
        var f = q?.fail ?: 0
        var lat = q?.latency ?: 0L
        if (ok) o++ else f++
        if (o + f > WINDOW) {
            o /= 2
            f /= 2
        }
        if (ok && latencyMs != null && latencyMs > 0) lat = if (lat == 0L) latencyMs else (lat * 3 + latencyMs) / 4
        store.write(kq(key), "$o|$f|$lat")
    }

    fun autoBonus(key: String): Int {
        val q = quality(key) ?: return 0
        val n = q.ok + q.fail
        if (n < MIN_SAMPLES) return 0
        val failRate = q.fail.toDouble() / n
        return when {
            failRate >= 0.4 -> -2
            failRate >= 0.2 -> -1
            failRate <= 0.05 && q.latency in 1..2000 -> 1
            else -> 0
        }
    }

    fun effective(key: String, arabic: Boolean): Int {
        var s = score(key) + autoBonus(key)
        if (arabic) s += arabicScore(key).coerceIn(-2, 2)
        return s
    }

    private companion object {
        const val WINDOW = 40
        const val MIN_SAMPLES = 5
    }
}
