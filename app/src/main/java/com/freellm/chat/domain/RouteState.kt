package com.freellm.chat.domain

data class RouteInfo(
    val providerId: String,
    val directBad: Boolean,
    val viaWorker: Boolean,
    val untilMs: Long,
    val kind: ErrorKind?
)

class RouteState(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val directBadUntil = HashMap<String, Long>()
    private val workerGoodUntil = HashMap<String, Long>()
    private val badKind = HashMap<String, ErrorKind>()

    private fun key(providerId: String): String = providerId.substringBefore('#')

    @Synchronized
    fun onDirectFailure(providerId: String, kind: ErrorKind) {
        val k = key(providerId)
        directBadUntil[k] = clock() + (if (kind == ErrorKind.REGION) REGION_BAD_MS else DIRECT_BAD_MS)
        badKind[k] = kind
    }

    @Synchronized
    fun onDirectSuccess(providerId: String) {
        val k = key(providerId)
        directBadUntil.remove(k)
        workerGoodUntil.remove(k)
        badKind.remove(k)
    }

    @Synchronized
    fun onWorkerSuccess(providerId: String) {
        workerGoodUntil[key(providerId)] = clock() + WORKER_GOOD_MS
    }

    @Synchronized
    fun onWorkerFailure(providerId: String) {
        workerGoodUntil.remove(key(providerId))
    }

    @Synchronized
    fun directAllowed(t: Target): Boolean = (directBadUntil[key(t.providerId)] ?: 0L) <= clock()

    @Synchronized
    fun route(t: Target): NetRoute {
        val k = key(t.providerId)
        val now = clock()
        val bad = (directBadUntil[k] ?: 0L) > now
        val good = (workerGoodUntil[k] ?: 0L) > now
        return if (t.hasWorker && bad && good) NetRoute.WORKER else NetRoute.DIRECT
    }

    @Synchronized
    fun retryAt(t: Target): Long = directBadUntil[key(t.providerId)] ?: 0L

    @Synchronized
    fun clearDirectBad(): Boolean {
        val any = directBadUntil.isNotEmpty()
        directBadUntil.clear()
        badKind.clear()
        return any
    }

    @Synchronized
    fun reset() {
        directBadUntil.clear()
        workerGoodUntil.clear()
        badKind.clear()
    }

    @Synchronized
    fun snapshot(): List<RouteInfo> {
        val now = clock()
        val ids = (directBadUntil.keys + workerGoodUntil.keys).toSortedSet()
        return ids.mapNotNull { id ->
            val bad = (directBadUntil[id] ?: 0L) > now
            val good = (workerGoodUntil[id] ?: 0L) > now
            if (!bad && !good) null
            else RouteInfo(id, bad, bad && good, if (bad) directBadUntil[id] ?: 0L else workerGoodUntil[id] ?: 0L, badKind[id])
        }
    }

    private companion object {
        const val DIRECT_BAD_MS = 90_000L
        const val REGION_BAD_MS = 900_000L
        const val WORKER_GOOD_MS = 1_800_000L
    }
}
