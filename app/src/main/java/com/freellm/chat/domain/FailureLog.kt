package com.freellm.chat.domain

data class FailureEntry(val time: Long, val provider: String, val reason: String)

class FailureLog(private val store: UsageStore? = null, private val max: Int = 50) {
    private val items = ArrayList<String>()

    init {
        store?.read(KEY)?.split('\n')?.filter { it.isNotBlank() }?.let { items.addAll(it) }
    }

    private fun clean(s: String) = s.replace('|', ' ').replace('\n', ' ')

    @Synchronized
    fun add(ts: Long, provider: String, reason: String) {
        items.add("$ts|${clean(provider)}|${clean(reason)}")
        while (items.size > max) items.removeAt(0)
        store?.write(KEY, items.joinToString("\n"))
    }

    @Synchronized
    fun all(): List<FailureEntry> = items.mapNotNull { line ->
        val p = line.split('|')
        val t = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        FailureEntry(t, p.getOrElse(1) { "" }, p.getOrElse(2) { "" })
    }.reversed()

    @Synchronized
    fun clear() {
        items.clear()
        store?.write(KEY, "")
    }

    private companion object {
        const val KEY = "failure_log"
    }
}
