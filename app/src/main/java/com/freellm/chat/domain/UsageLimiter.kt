package com.freellm.chat.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.max

interface UsageStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

data class UsageSnap(val minUsed: Int, val minLimit: Int, val dayUsed: Int, val dayLimit: Int)

class UsageLimiter(
    private val store: UsageStore? = null,
    private val zone: ZoneId = ZoneOffset.UTC
) {
    private val recent = HashMap<String, ArrayDeque<Long>>()
    private val counters = HashMap<String, Pair<String, Int>>()

    private fun scope(t: Target): String = if (t.perModelLimits && !t.sharedLimits) t.key else t.providerId

    private fun zoneOf(t: Target): ZoneId =
        t.resetTz?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone

    private fun dayId(now: Long, z: ZoneId): String = Instant.ofEpochMilli(now).atZone(z).toLocalDate().toString()

    private fun monthId(now: Long, z: ZoneId): String = dayId(now, z).take(7)

    private fun nextDay(now: Long, z: ZoneId): Long =
        LocalDate.parse(dayId(now, z)).plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()

    private fun nextMonth(now: Long, z: ZoneId): Long =
        LocalDate.parse(dayId(now, z)).withDayOfMonth(1).plusMonths(1).atStartOfDay(z).toInstant().toEpochMilli()

    private fun load(name: String): Pair<String, Int>? {
        counters[name]?.let { return it }
        val raw = store?.read(name) ?: return null
        val i = raw.lastIndexOf('|')
        if (i <= 0) return null
        val n = raw.substring(i + 1).toIntOrNull() ?: return null
        return (raw.substring(0, i) to n).also { counters[name] = it }
    }

    private fun count(name: String, id: String): Int {
        val c = load(name)
        return if (c != null && c.first == id) c.second else 0
    }

    private fun bump(name: String, id: String) {
        val n = count(name, id) + 1
        counters[name] = id to n
        store?.write(name, "$id|$n")
    }

    private fun windowUntil(q: List<Long>, limit: Int, span: Long, now: Long): Long {
        if (limit <= 0) return 0L
        val inside = q.filter { now - it < span }
        if (inside.size < limit) return 0L
        return inside[inside.size - limit] + span
    }

    @Synchronized
    fun blockedUntil(t: Target, now: Long): Long? {
        val m = t.model
        if (m.rpm <= 0 && m.rph <= 0 && m.rpd <= 0 && m.rpmo <= 0) return null
        val s = scope(t)
        val z = zoneOf(t)
        var until = 0L
        val q = recent[s]
        if (q != null) {
            until = max(until, windowUntil(q, m.rpm, MINUTE, now))
            until = max(until, windowUntil(q, m.rph, HOUR, now))
        }
        if (m.rpd > 0 && count("d:$s", dayId(now, z)) >= m.rpd) until = max(until, nextDay(now, z))
        if (m.rpmo > 0 && count("m:$s", monthId(now, z)) >= m.rpmo) until = max(until, nextMonth(now, z))
        return if (until > now) until else null
    }

    @Synchronized
    fun record(t: Target, now: Long) {
        val m = t.model
        val s = scope(t)
        val z = zoneOf(t)
        if (m.rpm > 0 || m.rph > 0) {
            val q = recent.getOrPut(s) { ArrayDeque() }
            while (q.isNotEmpty() && now - q.first() >= HOUR) q.removeFirst()
            q.addLast(now)
        }
        if (m.rpd > 0) bump("d:$s", dayId(now, z))
        if (m.rpmo > 0) bump("m:$s", monthId(now, z))
    }

    @Synchronized
    fun refund(t: Target, now: Long) {
        val m = t.model
        val s = scope(t)
        val z = zoneOf(t)
        recent[s]?.let { if (it.isNotEmpty()) it.removeLast() }
        if (m.rpd > 0) drop("d:$s", dayId(now, z))
        if (m.rpmo > 0) drop("m:$s", monthId(now, z))
    }

    private fun drop(name: String, id: String) {
        val n = count(name, id)
        if (n <= 0) return
        counters[name] = id to (n - 1)
        store?.write(name, "$id|${n - 1}")
    }

    @Synchronized
    fun snapshot(t: Target, now: Long): UsageSnap {
        val m = t.model
        val s = scope(t)
        val z = zoneOf(t)
        val minUsed = recent[s]?.count { now - it < MINUTE } ?: 0
        val day = if (m.rpd > 0) count("d:$s", dayId(now, z)) else 0
        return UsageSnap(minUsed, m.rpm, day, m.rpd)
    }

    @Synchronized
    fun budgetPressure(t: Target, now: Long): Double {
        val m = t.model
        val s = scope(t)
        val z = zoneOf(t)
        var p = 0.0
        if (m.rpd > 0) p = count("d:$s", dayId(now, z)).toDouble() / m.rpd
        if (m.rpmo > 0) p = max(p, count("m:$s", monthId(now, z)).toDouble() / m.rpmo)
        return p
    }

    @Synchronized
    fun pressure(t: Target, now: Long): Double {
        val m = t.model
        var p = budgetPressure(t, now)
        val q = recent[scope(t)]
        if (q != null) {
            if (m.rpm > 0) p = max(p, q.count { now - it < MINUTE }.toDouble() / m.rpm)
            if (m.rph > 0) p = max(p, q.count { now - it < HOUR }.toDouble() / m.rph)
        }
        return p
    }

    @Synchronized
    fun reset() {
        recent.clear()
        counters.clear()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
    }
}
