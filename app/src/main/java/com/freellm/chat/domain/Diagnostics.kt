package com.freellm.chat.domain

data class TimingEntry(
    val at: Long,
    val classifyMs: Long,
    val preflightMs: Long,
    val searchMs: Long,
    val firstTokenMs: Long,
    val switches: Int,
    val model: String,
    val totalMs: Long,
    val ok: Boolean
) {
    fun encode(): String = listOf(at, classifyMs, preflightMs, searchMs, firstTokenMs, switches, model.replace('\t', ' ').replace('\n', ' '), totalMs, if (ok) 1 else 0).joinToString("\t")

    companion object {
        fun decode(line: String): TimingEntry? {
            val f = line.split('\t')
            if (f.size < 9) return null
            return TimingEntry(
                f[0].toLongOrNull() ?: return null, f[1].toLongOrNull() ?: 0, f[2].toLongOrNull() ?: 0, f[3].toLongOrNull() ?: 0,
                f[4].toLongOrNull() ?: 0, f[5].toIntOrNull() ?: 0, f[6], f[7].toLongOrNull() ?: 0, f[8] == "1"
            )
        }
    }
}

class TimingLog(private val store: UsageStore? = null, private val keep: Int = 50) {
    private val items = ArrayList<TimingEntry>()

    init {
        store?.read(KEY)?.lineSequence()?.mapNotNull { TimingEntry.decode(it) }?.let { items.addAll(it.toList().takeLast(keep)) }
    }

    @Synchronized
    fun add(e: TimingEntry) {
        items.add(e)
        while (items.size > keep) items.removeAt(0)
        store?.write(KEY, items.joinToString("\n") { it.encode() })
    }

    @Synchronized
    fun all(): List<TimingEntry> = items.toList().asReversed()

    private companion object {
        const val KEY = "timing_v1"
    }
}

enum class RouteLabel { DIRECT, WORKER, BLOCKED }

fun RouteInfo.label(): RouteLabel = when {
    viaWorker -> RouteLabel.WORKER
    directBad -> RouteLabel.BLOCKED
    else -> RouteLabel.DIRECT
}

fun RouteInfo.remainingSec(now: Long): Long = ((untilMs - now) / 1000).coerceAtLeast(0)

data class NetSummary(val vpn: Boolean, val validated: Boolean, val mode: String)
