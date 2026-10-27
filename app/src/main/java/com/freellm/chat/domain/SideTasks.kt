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
        picker: (List<Target>, Health, UsageLimiter, Long) -> List<Target> = LightPicker::pick,
        temperature: Double? = null
    ): String? {
        val picks = picker(candidates, router.health, router.limiter, clock()).take(attempts)
        for (t in picks) {
            router.limiter.record(t, clock())
            val out = try {
                withTimeoutOrNull(timeoutMs) {
                    val sb = StringBuilder()
                    transport.stream(t, listOf(ChatMsg("system", system), ChatMsg("user", user)), maxTokens, temperature)
                        .collect { sb.append(it) }
                    sb.toString()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            if (!out.isNullOrBlank()) return out.trim()
        }
        return null
    }

    private companion object {
        const val TIMEOUT_MS = 25_000L
    }
}
