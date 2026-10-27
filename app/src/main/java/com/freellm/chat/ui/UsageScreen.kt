package com.freellm.chat.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.freellm.chat.data.KeyCheck
import com.freellm.chat.data.KeyState
import com.freellm.chat.domain.FailureEntry
import com.freellm.chat.domain.Target
import com.freellm.chat.domain.UsageSnap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val WARN_AT = 0.8f

@Composable
fun UsageScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var targets by remember { mutableStateOf<List<Target>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val failCtx = androidx.compose.ui.platform.LocalContext.current
    var failures by remember { mutableStateOf<List<FailureEntry>>(emptyList()) }
    var checks by remember { mutableStateOf<List<KeyCheck>?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        targets = app.manager.activeTargets()
        while (true) {
            now = System.currentTimeMillis()
            failures = app.router.failLog.all()
            delay(2000)
        }
    }

    val rows = targets
        .filter { it.model.rpm > 0 || it.model.rpd > 0 }
        .distinctBy { if (it.perModelLimits && !it.sharedLimits) it.key else it.providerId }
        .map { it to app.router.limiter.snapshot(it, now) }
    val nearLimit = rows.any { (_, s) -> ratio(s.minUsed, s.minLimit) >= WARN_AT || ratio(s.dayUsed, s.dayLimit) >= WARN_AT }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { AppTopBar(stringResource(R.string.usage_title), onBack) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            UsageArt(Modifier.fillMaxWidth().height(100.dp).appear(0))
            if (nearLimit) {
                Text(stringResource(R.string.near_limit_warning), color = cs.error)
            }
            if (rows.isEmpty()) Text(stringResource(R.string.no_limits), color = cs.onSurfaceVariant)
            rows.forEach { (t, s) -> UsageRow(t, s) }

            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        checks = app.manager.testAll()
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(if (busy) R.string.testing else R.string.test_all_keys)) }
            checks?.forEach { c ->
                val (label, color) = when (c.state) {
                    KeyState.OK -> R.string.key_ok to cs.tertiary
                    KeyState.INVALID -> R.string.key_invalid to cs.error
                    KeyState.FAILING -> R.string.key_failing to cs.error
                }
                Text(c.providerName + ": " + stringResource(label), color = color)
            }

            Text(stringResource(R.string.failure_log), style = MaterialTheme.typography.titleMedium)
            if (failures.isEmpty()) Text(stringResource(R.string.no_failures), color = cs.onSurfaceVariant)
            failures.take(30).forEach { f ->
                Text(
                    fmt.format(Date(f.time)) + " · " + f.provider + " · " + com.freellm.chat.domain.labelOf(f.reason) { r -> failCtx.getString(r) },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (failures.isNotEmpty()) {
                TextButton(onClick = { app.router.failLog.clear(); failures = emptyList() }) {
                    Text(stringResource(R.string.clear_log))
                }
            }
        }
    }
}

private fun ratio(used: Int, limit: Int): Float = if (limit <= 0) 0f else (used.toFloat() / limit).coerceIn(0f, 1f)

@Composable
private fun UsageRow(t: Target, s: UsageSnap) {
    val cs = MaterialTheme.colorScheme
    val name = if (t.perModelLimits && !t.sharedLimits) t.model.displayName else t.providerName
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        if (s.minLimit > 0) {
            val r = ratio(s.minUsed, s.minLimit)
            Text(stringResource(R.string.usage_minute, s.minUsed, s.minLimit), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { r },
                modifier = Modifier.fillMaxWidth(),
                color = if (r >= WARN_AT) cs.error else cs.primary
            )
        }
        if (s.dayLimit > 0) {
            val r = ratio(s.dayUsed, s.dayLimit)
            Text(stringResource(R.string.usage_day, s.dayUsed, s.dayLimit), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { r },
                modifier = Modifier.fillMaxWidth(),
                color = if (r >= WARN_AT) cs.error else cs.primary
            )
        }
    }
}
