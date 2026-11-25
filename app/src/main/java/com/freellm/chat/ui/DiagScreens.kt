package com.freellm.chat.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.domain.ChatMode
import com.freellm.chat.domain.ChatMsg
import com.freellm.chat.domain.GoldenCheck
import com.freellm.chat.domain.ModelHealth
import com.freellm.chat.domain.NetSummary
import com.freellm.chat.domain.RouteEvent
import com.freellm.chat.domain.RouteInfo
import com.freellm.chat.domain.RouteLabel
import com.freellm.chat.domain.RouteRequest
import com.freellm.chat.domain.StatRow
import com.freellm.chat.domain.Target
import com.freellm.chat.domain.TimingEntry
import com.freellm.chat.domain.label
import com.freellm.chat.domain.remainingSec
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Composable
fun DiagnosticsScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var items by remember { mutableStateOf<List<TimingEntry>>(emptyList()) }
    var net by remember { mutableStateOf(NetSummary(false, false, "auto")) }
    var routes by remember { mutableStateOf<List<RouteInfo>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            items = app.timing.all()
            net = NetSummary(app.netState.vpn, app.netState.validated, app.netState.mode)
            routes = app.router.routes.snapshot()
            now = System.currentTimeMillis()
            delay(2000)
        }
    }
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    Scaffold(containerColor = cs.background, topBar = { AppTopBar(stringResource(R.string.diag_title), onBack) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(stringResource(R.string.net_section_title), style = MaterialTheme.typography.titleSmall)
            val pathName = stringResource(if (net.vpn) R.string.net_path_value_vpn else R.string.net_path_value_direct)
            val yesNo = stringResource(if (net.validated) R.string.net_yes else R.string.net_no)
            val modeName = stringResource(if (net.mode == "vpn_only") R.string.net_path_vpn_only_short else R.string.net_path_auto)
            Text(stringResource(R.string.net_current_path, pathName), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(stringResource(R.string.net_validated_line, yesNo), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(stringResource(R.string.net_setting_line, modeName), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            if (routes.isEmpty()) Text(stringResource(R.string.net_routes_none), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            routes.forEach { r ->
                val label = stringResource(
                    when (r.label()) {
                        RouteLabel.WORKER -> R.string.net_route_worker
                        RouteLabel.BLOCKED -> R.string.net_route_blocked
                        RouteLabel.DIRECT -> R.string.net_route_direct
                    }
                )
                Text(
                    stringResource(R.string.net_route_row, r.providerId, label, r.remainingSec(now)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (r.directBad) cs.error else cs.onSurfaceVariant
                )
            }
            if (items.isEmpty()) Text(stringResource(R.string.diag_empty), color = cs.onSurfaceVariant)
            items.forEach { e ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        fmt.format(Date(e.at)) + "  " + e.model + if (e.ok) "" else "  !",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (e.ok) cs.onSurface else cs.error
                    )
                    Text(
                        stringResource(R.string.diag_row, e.classifyMs, e.preflightMs, e.searchMs, e.firstTokenMs, e.switches, e.totalMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            GoldenRunner(app)
        }
    }
}

@Serializable
private data class GoldenQuestion(
    val id: String,
    val lang: String = "",
    val q: String,
    val must: List<String> = emptyList(),
    val mustNot: List<String> = emptyList()
)

@Composable
private fun GoldenRunner(app: FreeLlmApp) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var passed by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf<List<String>>(emptyList()) }
    Button(
        enabled = !running,
        onClick = {
            running = true
            finished = false
            done = 0
            passed = 0
            failed = emptyList()
            scope.launch {
                try {
                    val items = withContext(Dispatchers.IO) {
                        val text = app.assets.open("golden-questions.json").bufferedReader().use { it.readText() }
                        Json { ignoreUnknownKeys = true }.decodeFromString<List<GoldenQuestion>>(text)
                    }
                    total = items.size
                    val targets = withContext(Dispatchers.IO) { app.manager.activeTargets() }
                    val sel = app.settings.selection.first()
                    val pinned = sel.startsWith("PIN:")
                    val key = sel.removePrefix("PIN:")
                    val cands = if (pinned) targets.filter { it.key == key } else targets
                    val bad = ArrayList<String>()
                    var ok = 0
                    for (g in items) {
                        val sb = StringBuilder()
                        try {
                            app.router.stream(
                                RouteRequest(
                                    messages = listOf(ChatMsg("user", g.q)),
                                    mode = if (pinned) ChatMode.PINNED else ChatMode.AUTO,
                                    pinnedKey = if (pinned) key else null,
                                    candidates = cands,
                                    maxTokens = 300,
                                    adaptive = !pinned
                                )
                            ).collect { ev ->
                                when (ev) {
                                    is RouteEvent.Delta -> sb.append(ev.text)
                                    is RouteEvent.Restart -> sb.setLength(0)
                                    else -> Unit
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            sb.setLength(0)
                        }
                        if (GoldenCheck.pass(sb.toString(), g.must, g.mustNot)) ok++ else bad.add(g.id)
                        done++
                        passed = ok
                        failed = bad.toList()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    total = maxOf(total, done)
                } finally {
                    running = false
                    finished = true
                }
            }
        }
    ) {
        Text(if (running) stringResource(R.string.golden_running, done, total) else stringResource(R.string.golden_run))
    }
    if (finished) {
        Text(stringResource(R.string.golden_result, passed, total), style = MaterialTheme.typography.titleSmall)
        if (failed.isNotEmpty()) Text(failed.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = cs.error)
    }
}

@Composable
fun ModelHealthScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var targets by remember { mutableStateOf<List<Target>>(emptyList()) }
    var rows by remember { mutableStateOf<Map<String, StatRow>>(emptyMap()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        targets = app.manager.activeTargets().filter { '#' !in it.providerId }
        while (true) {
            now = System.currentTimeMillis()
            rows = app.router.stats.rows().associateBy { it.key }
            delay(2000)
        }
    }
    Scaffold(containerColor = cs.background, topBar = { AppTopBar(stringResource(R.string.health_title), onBack) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            targets.sortedBy { it.model.priority }.forEach { t ->
                val r = rows[t.key]
                val st = app.router.health.state(t, now)
                val state = when (st) {
                    is ModelHealth.Cooling -> stringResource(R.string.health_cooling, ((st.untilMs - now) / 1000).coerceAtLeast(0))
                    ModelHealth.Dead -> stringResource(R.string.health_dead)
                    ModelHealth.ProviderDown -> stringResource(R.string.health_provider_down)
                    ModelHealth.Ready -> stringResource(R.string.health_ready)
                }
                val total = (r?.ok ?: 0) + (r?.fail ?: 0)
                val rate = if (total == 0) "-" else (r!!.ok * 100 / total).toString() + "%"
                val dot = when (st) {
                    ModelHealth.Ready -> StatusColors.Ready
                    is ModelHealth.Cooling -> StatusColors.Cooling
                    else -> StatusColors.Dead
                }
                val pulse = if (st is ModelHealth.Cooling && !LocalReduceMotion.current) {
                    androidx.compose.animation.core.rememberInfiniteTransition(label = "cool").animateFloat(
                        1f, 0.3f,
                        androidx.compose.animation.core.infiniteRepeatable(
                            androidx.compose.animation.core.tween(800),
                            androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "alpha"
                    ).value
                } else 1f
                Column(Modifier.fillMaxWidth().background(surfaceLow(), androidx.compose.foundation.shape.RoundedCornerShape(Corners.card)).padding(Spacing.md)) {
                    androidx.compose.foundation.layout.Box(Modifier.size(10.dp).background(dot.copy(alpha = pulse), androidx.compose.foundation.shape.CircleShape))
                    Text(t.providerName + " / " + t.model.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.health_row, state, r?.latencyMs?.toString() ?: "-", rate, total),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = if (st is ModelHealth.Ready) cs.onSurfaceVariant else cs.error
                    )
                }
            }
        }
    }
}
