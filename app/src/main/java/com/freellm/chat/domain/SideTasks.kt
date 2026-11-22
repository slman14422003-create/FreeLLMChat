package com.freellm.chat.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

object LightPicker {
    const val MAX_PRESSURE = 0.85

    fun pick(candidates: List<Target>, health: Health, limiter: UsageLimiter, now: Long): List<Target> =
        candidates
            .filter {
                health.blockedUntil(it, now) == null &&
                    limiter.blockedUntil(it, now) == null &&
                    limiter.pressure(it, now) < MAX_PRESSURE
            }
            .sortedWith(
                compareBy<Target>(
                    { if ("fast" in it.model.modes) 0 else 1 },
                    { if (it.model.reasoning) 1 else 0 },
                    { it.model.priority },
                    { it.providerRank }
                )
            )
}

object StrongPicker {
    fun pick(candidates: List<Target>, health: Health, limiter: UsageLimiter, now: Long): List<Target> =
        candidates
            .filter {
                health.blockedUntil(it, now) == null &&
                    limiter.blockedUntil(it, now) == null &&
                    limiter.pressure(it, now) < LightPicker.MAX_PRESSURE
            }
            .sortedWith(
                compareBy<Target>(
                    { if ("smart" in it.model.modes) 0 else 1 },
                    { if (it.model.reasoning) 0 else 1 },
                    { it.model.priority },
                    { it.providerRank }
                )
            )
}

object SidePicker {
    const val MAX_PRESSURE = 0.5
    const val RESERVED = 2

    fun pick(candidates: List<Target>, health: Health, limiter: UsageLimiter, now: Long): List<Target> {
        val reserved = if (candidates.size > RESERVED + 2) {
            candidates.sortedBy { it.model.priority }.take(RESERVED).map { it.key }.toSet()
        } else {
            emptySet()
        }
        return LightPicker.pick(
            candidates.filter { it.key !in reserved && limiter.pressure(it, now) < MAX_PRESSURE },
            health, limiter, now
        )
    }
}

class SideTasks(
    private val transport: ChatTransport,
    private val router: Router,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun run(
        candidates: List<Target>,
        system: String,
        user: String,
        maxTokens: Int = 300,
        attempts: Int = 2,
        timeoutMs: Long = TIMEOUT_MS,
        picker: (List<Target>, Health, UsageLimiter, Long) -> List<Target> = SidePicker::pick,
        temperature: Double? = null
    ): String? {
        val picks = picker(candidates, router.health, router.limiter, clock()).take(attempts)
        for (t in picks) {
            router.limiter.record(t, clock())
            var used = router.routed(t)
            var (out, ex) = attempt(used, system, user, maxTokens, temperature, timeoutMs)
            if (out.isNullOrBlank() && ex != null && routeKind(ex) && t.hasWorker && used.via == NetRoute.DIRECT) {
                router.report(used, ex)
                used = t.viaWorker()
                val second = attempt(used, system, user, maxTokens, temperature, timeoutMs)
                out = second.first
                ex = second.second
            }
            if (!out.isNullOrBlank()) {
                router.report(used, null)
                return out.trim()
            }
            router.report(used, ex ?: LlmException(ErrorKind.EMPTY))
        }
        return null
    }

    private suspend fun attempt(
        t: Target,
        system: String,
        user: String,
        maxTokens: Int,
        temperature: Double?,
        timeoutMs: Long
    ): Pair<String?, LlmException?> = try {
        val out = withTimeoutOrNull(timeoutMs) {
            val sb = StringBuilder()
            transport.stream(t, listOf(ChatMsg("system", system), ChatMsg("user", user)), maxTokens, temperature)
                .collect { sb.append(it) }
            sb.toString()
        }
        if (out == null) null to LlmException(ErrorKind.TIMEOUT, message = "side task timeout") else out to null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null to router.classify(e)
    }

    private companion object {
        const val TIMEOUT_MS = 25_000L
    }
}
