@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.freellm.chat.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.max
import kotlin.math.min

data class RouterConfig(
    val defaultCooldownMs: Long = 60_000,
    val maxCooldownMs: Long = 3_600_000,
    val failureThreshold: Int = 3,
    val firstTokenTimeoutMs: Long = 30_000,
    val idleTimeoutMs: Long = 20_000,
    val restartBelowChars: Int = 20,
    val hedgeMs: Long = 0,
    val reasoningFactor: Int = 2
)

sealed class ModelHealth {
    data object Ready : ModelHealth()
    data class Cooling(val untilMs: Long) : ModelHealth()
    data object Dead : ModelHealth()
    data object ProviderDown : ModelHealth()
}

class Health(private val config: RouterConfig) {
    private val modelFails = HashMap<String, Int>()
    private val modelUntil = HashMap<String, Long>()
    private val modelBackoff = HashMap<String, Int>()
    private val providerUntil = HashMap<String, Long>()
    private val providerBackoff = HashMap<String, Int>()
    private val dead = HashSet<String>()
    private val authFailed = HashSet<String>()
    private val transientKeys = HashSet<String>()

    private fun cooldown(retryAfterSec: Long?, n: Int): Long {
        if (retryAfterSec != null && retryAfterSec > 0) {
            return min(retryAfterSec * 1000, config.maxCooldownMs)
        }
        val shift = min(n, 20)
        return min(config.defaultCooldownMs * (1L shl shift), config.maxCooldownMs)
    }

    @Synchronized
    fun blockedUntil(t: Target, now: Long): Long? {
        if (t.providerId in authFailed) return Long.MAX_VALUE
        if (t.key in dead) return Long.MAX_VALUE
        val until = max(providerUntil[t.providerId] ?: 0L, modelUntil[t.key] ?: 0L)
        return if (until > now) until else null
    }

    @Synchronized
    fun state(t: Target, now: Long): ModelHealth {
        if (t.providerId in authFailed) return ModelHealth.ProviderDown
        if (t.key in dead) return ModelHealth.Dead
        val until = max(providerUntil[t.providerId] ?: 0L, modelUntil[t.key] ?: 0L)
        return if (until > now) ModelHealth.Cooling(until) else ModelHealth.Ready
    }

    @Synchronized
    fun onSuccess(t: Target) {
        modelFails.remove(t.key)
        modelBackoff.remove(t.key)
        providerBackoff.remove(t.providerId)
    }

    @Synchronized
    fun onFailure(t: Target, ex: LlmException, now: Long) {
        when (ex.kind) {
            ErrorKind.RATE_LIMIT, ErrorKind.PAYMENT -> {
                if (t.perModelLimits) {
                    val n = modelBackoff[t.key] ?: 0
                    modelUntil[t.key] = now + cooldown(ex.retryAfterSec, n)
                    modelBackoff[t.key] = n + 1
                } else {
                    val n = providerBackoff[t.providerId] ?: 0
                    providerUntil[t.providerId] = now + cooldown(ex.retryAfterSec, n)
                    providerBackoff[t.providerId] = n + 1
                }
            }
            ErrorKind.AUTH, ErrorKind.POLICY -> authFailed.add(t.providerId)
            ErrorKind.NOT_FOUND -> dead.add(t.key)
            else -> {
                val f = (modelFails[t.key] ?: 0) + (if (ex.kind == ErrorKind.TIMEOUT || ex.kind == ErrorKind.INTERRUPTED) 3 else 1)
                if (f >= config.failureThreshold) {
                    val n = modelBackoff[t.key] ?: 0
                    modelUntil[t.key] = now + cooldown(null, n)
                    modelBackoff[t.key] = n + 1
                    modelFails[t.key] = 0
                    transientKeys.add(t.key)
                } else {
                    modelFails[t.key] = f
                }
            }
        }
    }

    @Synchronized
    fun clearTransient(): Boolean {
        val any = transientKeys.isNotEmpty() || modelFails.isNotEmpty()
        transientKeys.forEach {
            modelUntil.remove(it)
            modelBackoff.remove(it)
        }
        transientKeys.clear()
        modelFails.clear()
        return any
    }

    @Synchronized
    fun clearProvider(id: String) {
        fun mine(k: String): Boolean = k == id || k.startsWith("$id#")
        authFailed.removeAll { mine(it) }
        providerUntil.keys.removeAll { mine(it) }
        providerBackoff.keys.removeAll { mine(it) }
    }

    @Synchronized
    fun reset() {
        modelFails.clear()
        modelUntil.clear()
        modelBackoff.clear()
        providerUntil.clear()
        providerBackoff.clear()
        dead.clear()
        authFailed.clear()
        transientKeys.clear()
    }
}

object ContextTrimmer {
    fun estimate(s: String): Int = Math.ceil(s.length / 3.5).toInt() + 4

    fun cost(m: ChatMsg): Int = estimate(m.content) + m.images.size * 1200

    fun trim(msgs: List<ChatMsg>, window: Int, reserve: Int, keepLast: Int = 1): List<ChatMsg> {
        val budget = window - reserve
        val system = msgs.filter { it.role == "system" }
        val rest = msgs.filter { it.role != "system" }.toMutableList()
        val systemCost = system.sumOf { cost(it) }
        fun total(): Int = systemCost + rest.sumOf { cost(it) }
        while (rest.size > keepLast && total() > budget) rest.removeAt(0)
        while (rest.size > keepLast && rest.first().role != "user") rest.removeAt(0)
        return system + rest
    }
}

internal fun baseProviderId(t: Target): String = t.providerId.substringBefore('#')

internal fun baseKey(t: Target): String = baseProviderId(t) + "/" + t.model.id

internal fun groupKeys(list: List<Target>): List<Target> {
    val out = ArrayList<Target>(list.size)
    val seen = BooleanArray(list.size)
    for (i in list.indices) {
        if (seen[i]) continue
        seen[i] = true
        out.add(list[i])
        val k = baseKey(list[i])
        for (j in i + 1 until list.size) {
            if (!seen[j] && baseKey(list[j]) == k) {
                seen[j] = true
                out.add(list[j])
            }
        }
    }
    return out
}

private class Chunk(val text: String?, val error: Throwable?)

private class Lane(val t: Target, val ch: ReceiveChannel<Chunk>, val opened: Long)

private const val CONTINUE_PROMPT =
    "Continue your previous reply exactly where it stopped. Do not repeat anything and do not add an introduction. أكمل ردك السابق من حيث توقفت تماماً دون تكرار ودون مقدمة."
private const val HOLD_CHARS = 48
private const val QUALITY_CHECK_BELOW = 200
private const val TAIL_CHARS = 240
private const val ALERT_AT = 0.8
private const val PROACTIVE_AT = 0.9
private const val DAY_MS = 86_400_000L

class Router(
    private val transport: ChatTransport,
    private val config: RouterConfig = RouterConfig(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    val limiter: UsageLimiter = UsageLimiter(),
    val failLog: FailureLog = FailureLog()
) {
    val health = Health(config)
    val stats = ModelStats()
    var onOutcome: ((String, Boolean, Long?) -> Unit)? = null
    private val alerted = HashSet<String>()
    @Volatile var hedgeMs: Long = config.hedgeMs

    internal fun order(req: RouteRequest): List<Target> {
        val pool = if (req.needsVision) req.candidates.filter { it.model.vision } else req.candidates
        if (req.mode == ChatMode.PINNED) return pool.filter { baseKey(it) == req.pinnedKey }
        val wanted = when (req.mode) {
            ChatMode.FAST -> "fast"
            ChatMode.SMART -> "smart"
            ChatMode.CODE -> "code"
            else -> null
        }
        return groupKeys(pool.sortedWith(
            compareBy<Target>(
                { if (req.estimatedTokens > 0 && !RequestFitter.fits(it, req.estimatedTokens)) 1 else 0 },
                { if (req.preferredKey != null && it.key == req.preferredKey) 0 else 1 },
                { if (req.proactive && limiter.pressure(it, clock()) >= PROACTIVE_AT) 1 else 0 },
                { if (wanted != null && wanted in it.model.modes) 0 else 1 },
                { -ModelCaps.matches(it.model, req.wantCaps) },
                { if (req.adaptive) stats.failTier(it.key) else 0 },
                { -req.rating(it.key).coerceIn(-3, 3) },
                { if (req.adaptive) stats.latencyBucket(it.key) else 0 },
                { it.model.priority },
                { it.providerRank }
            )
        ))
    }

    private fun firstTokenMs(t: Target): Long =
        if (t.model.reasoning) config.firstTokenTimeoutMs * config.reasoningFactor else config.firstTokenTimeoutMs

    private fun idleMs(t: Target): Long =
        if (t.model.reasoning) config.idleTimeoutMs * config.reasoningFactor else config.idleTimeoutMs

    private fun hedgeDelay(t: Target): Long {
        val base = hedgeMs
        val lat = stats.latencyMs(t.key) ?: return base
        return (lat * 3).coerceIn(max(1800L, base / 2), base * 2)
    }

    private fun pickHedge(ordered: List<Target>, t: Target, consumed: Set<String>): Target? {
        val rest = ordered.dropWhile { it != t }.drop(1).filter {
            it.key !in consumed && health.blockedUntil(it, clock()) == null && limiter.blockedUntil(it, clock()) == null
        }
        return rest.firstOrNull { baseProviderId(it) != baseProviderId(t) } ?: rest.firstOrNull()
    }

    private fun buildMessages(req: RouteRequest, t: Target, partial: String): List<ChatMsg> {
        var msgs = req.messages
        var keep = 1
        if (partial.isNotEmpty()) {
            msgs = msgs + ChatMsg("assistant", partial) + ChatMsg("user", CONTINUE_PROMPT)
            keep = 2
        }
        val window = t.model.contextWindow
        val reserve = min(4096, window / 4)
        return ContextTrimmer.trim(msgs, window, reserve, keep)
    }

    internal fun classify(e: Throwable): LlmException = when (e) {
        is LlmException -> e
        is SocketTimeoutException -> LlmException(ErrorKind.TIMEOUT, message = "timeout")
        is IOException -> LlmException(ErrorKind.NETWORK, message = "network")
        else -> LlmException(ErrorKind.SERVER, message = e.javaClass.simpleName)
    }

    private fun failure(reasons: List<FailReason>, soonest: Long, now: Long, pinned: Boolean): RouteEvent.Failed {
        val retry = if (soonest != Long.MAX_VALUE) max(1L, (soonest - now + 999) / 1000) else null
        val text = reasons.joinToString("; ") { it.provider + ":" + (it.error?.name ?: it.reason.name) } +
            (if (retry != null) " retry=" + retry + "s" else "")
        return RouteEvent.Failed(text, pinned, FailKind.ALL_FAILED, reasons, retry)
    }

    fun stream(req: RouteRequest): Flow<RouteEvent> = runOnce(req, true)

    private fun runOnce(req: RouteRequest, again: Boolean): Flow<RouteEvent> = flow {
        val pinned = req.mode == ChatMode.PINNED
        val ordered = order(req)
        if (ordered.isEmpty()) {
            val none = when {
                req.needsVision && req.candidates.isNotEmpty() -> FailKind.NO_VISION
                pinned -> FailKind.PINNED_MISSING
                else -> FailKind.NO_PROVIDER
            }
            emit(RouteEvent.Failed(none.name, pinned, none))
            return@flow
        }
        val partial = StringBuilder()
        val reasons = LinkedHashMap<String, FailReason>()
        var shown: Target? = null
        var soonest = Long.MAX_VALUE
        var qualityRetried = false
        val consumed = HashSet<String>()

        suspend fun fail(tt: Target, ex: LlmException) {
            reasons[tt.providerName] = FailReason(tt.providerName, ReasonKind.ERROR, ex.kind)
            failLog.add(clock(), tt.providerName, ex.kind.name)
            health.onFailure(tt, ex, clock())
            stats.onFail(tt.key)
            onOutcome?.invoke(tt.key, false, null)
            val after = health.blockedUntil(tt, clock())
            if (after != null && after != Long.MAX_VALUE) soonest = min(soonest, after)
            if (ex.kind == ErrorKind.AUTH) emit(RouteEvent.AuthFailed(tt.providerId, ex.httpCode))
        }

        for (t in ordered) {
            if (t.key in consumed) continue
            val skipBlocked = !pinned || (ordered.size > 1 && t !== ordered.last())
            if (skipBlocked) {
                val until = health.blockedUntil(t, clock())
                if (until != null) {
                    if (until == Long.MAX_VALUE) {
                        reasons.putIfAbsent(t.providerName, FailReason(t.providerName, ReasonKind.UNAVAILABLE))
                    } else {
                        reasons.putIfAbsent(t.providerName, FailReason(t.providerName, ReasonKind.COOLING))
                        soonest = min(soonest, until)
                    }
                    continue
                }
            }
            if (skipBlocked) {
                val lim = limiter.blockedUntil(t, clock())
                if (lim != null) {
                    reasons.putIfAbsent(t.providerName, FailReason(t.providerName, ReasonKind.USAGE_LIMIT))
                    soonest = min(soonest, lim)
                    continue
                }
            }
            if (req.proactive) {
                val bp = limiter.budgetPressure(t, clock())
                val tag = (if (t.perModelLimits) t.key else t.providerId) + "|" + clock() / DAY_MS
                if (bp >= ALERT_AT && alerted.add(tag)) emit(RouteEvent.NearLimit(t, (bp * 100).toInt()))
            }
            limiter.record(t, clock())
            val msgs = buildMessages(req, t, partial.toString())
            var got = false
            var completed = false
            val tail = if (partial.isNotEmpty()) partial.takeLast(TAIL_CHARS).toString() else ""
            var holding = tail.isNotEmpty()
            val hold = StringBuilder()
            var began = clock()
            var cur = t

            suspend fun push(text: String) {
                if (text.isEmpty()) return
                if (!got) {
                    got = true
                    stats.onFirstToken(cur.key, clock() - began)
                    val prev = shown
                    shown = cur
                    if (prev == null) emit(RouteEvent.Started(cur))
                    else if (prev != cur) emit(RouteEvent.Switched(cur))
                }
                partial.append(text)
                emit(RouteEvent.Delta(text))
            }

            try {
                coroutineScope {
                    val scope = this
                    fun open(tt: Target, m: List<ChatMsg>): Lane = Lane(
                        tt,
                        transport.stream(tt, m, req.maxTokens, if (tt.model.reasoning) null else req.temperature)
                            .map { Chunk(it, null) }
                            .catch { emit(Chunk(null, it)) }
                            .produceIn(scope),
                        clock()
                    )
                    val primary = open(t, msgs)
                    val firstMs = firstTokenMs(t)
                    val idle = idleMs(t)
                    var ch = primary.ch
                    var r0: ChannelResult<Chunk>? = null
                    var waitLeft = firstMs
                    val hedgeAfter = if (!pinned && partial.isEmpty() && hedgeMs > 0 && !t.model.reasoning) hedgeDelay(t) else 0L
                    if (hedgeAfter in 1 until firstMs) {
                        r0 = withTimeoutOrNull(hedgeAfter) { ch.receiveCatching() }
                        waitLeft = firstMs - hedgeAfter
                        val cand = if (r0 == null) pickHedge(ordered, t, consumed) else null
                        if (cand != null) {
                            consumed.add(cand.key)
                            limiter.record(cand, clock())
                            var lanes = listOf(primary, open(cand, buildMessages(req, cand, "")))
                            val won = withTimeoutOrNull(waitLeft) {
                                var found: Pair<Lane, ChannelResult<Chunk>>? = null
                                while (found == null && lanes.isNotEmpty()) {
                                    val now = lanes
                                    val (lane, res) = select<Pair<Lane, ChannelResult<Chunk>>> {
                                        now.forEach { l -> l.ch.onReceiveCatching { l to it } }
                                    }
                                    val chunk = res.getOrNull()
                                    val err: Throwable? = if (chunk == null) LlmException(ErrorKind.EMPTY) else chunk.error
                                    if (err != null) {
                                        fail(lane.t, classify(err))
                                        lane.ch.cancel()
                                        lanes = lanes.filter { it !== lane }
                                    } else {
                                        found = lane to res
                                    }
                                }
                                found
                            }
                            if (won == null) {
                                for (l in lanes) {
                                    fail(l.t, LlmException(ErrorKind.TIMEOUT, message = "first token timeout"))
                                    l.ch.cancel()
                                }
                                return@coroutineScope
                            }
                            for (l in lanes) {
                                if (l !== won.first) {
                                    l.ch.cancel()
                                    if (l === primary) stats.onFail(primary.t.key)
                                }
                            }
                            cur = won.first.t
                            ch = won.first.ch
                            began = won.first.opened
                            r0 = won.second
                        }
                    }
                    if (r0 == null) r0 = withTimeoutOrNull(waitLeft) { ch.receiveCatching() }
                    try {
                        var r: ChannelResult<Chunk> = r0 ?: throw LlmException(ErrorKind.TIMEOUT, message = "first token timeout")
                        while (true) {
                            val c = r.getOrNull() ?: break
                            val err = c.error
                            if (err != null) throw err
                            val text = c.text.orEmpty()
                            if (text.isNotEmpty()) {
                                if (holding) {
                                    hold.append(text)
                                    if (hold.length >= HOLD_CHARS) {
                                        holding = false
                                        push(Overlap.strip(tail, hold.toString()))
                                        hold.setLength(0)
                                    }
                                } else {
                                    push(text)
                                }
                            }
                            // A stalled stream is treated like a hit limit: hand the partial text to the next model now.
                            r = withTimeoutOrNull(idle) { ch.receiveCatching() }
                                ?: throw LlmException(ErrorKind.INTERRUPTED, message = "stream stalled")
                        }
                        if (holding && hold.isNotEmpty()) {
                            val rest = Overlap.strip(tail, hold.toString())
                            if (rest.isNotEmpty()) push(rest) else got = true
                            holding = false
                        }
                        if (!got) throw LlmException(ErrorKind.EMPTY)
                        if (!pinned && !qualityRetried && partial.length < QUALITY_CHECK_BELOW &&
                            OutputQuality.isPoor(partial.toString()) &&
                            ordered.dropWhile { it != cur }.drop(1).any {
                                health.blockedUntil(it, clock()) == null && limiter.blockedUntil(it, clock()) == null
                            }
                        ) {
                            qualityRetried = true
                            throw LlmException(ErrorKind.EMPTY, message = "poor output")
                        }
                        completed = true
                    } finally {
                        ch.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val ex = classify(e)
                fail(cur, ex)
                if (pinned) {
                    val moreKeys = ordered.dropWhile { it != cur }.drop(1).isNotEmpty()
                    val switchable = ex.kind == ErrorKind.RATE_LIMIT || ex.kind == ErrorKind.PAYMENT || ex.kind == ErrorKind.AUTH
                    if (!(moreKeys && switchable)) {
                        emit(failure(reasons.values.toList(), soonest, clock(), true))
                        return@flow
                    }
                }
                if (got && (partial.length < config.restartBelowChars || ex.message == "poor output")) {
                    partial.clear()
                    emit(RouteEvent.Restart)
                }
                continue
            }
            if (completed) {
                health.onSuccess(cur)
                stats.onOk(cur.key)
                onOutcome?.invoke(cur.key, true, stats.latencyMs(cur.key))
                emit(RouteEvent.Done(cur))
                return@flow
            }
        }
        val blip = reasons.isNotEmpty() && reasons.values.all {
            it.reason == ReasonKind.COOLING || (it.reason == ReasonKind.ERROR && it.error == ErrorKind.NETWORK)
        }
        if (again && !pinned && partial.isEmpty() && blip) {
            val cleared = health.clearTransient()
            if (cleared || reasons.values.any { it.reason == ReasonKind.ERROR }) {
                delay(1000)
                emitAll(runOnce(req, false))
                return@flow
            }
        }
        emit(failure(reasons.values.toList(), soonest, clock(), pinned))
    }
}
