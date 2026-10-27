@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.data.SearchFactory
import com.freellm.chat.domain.LimitPeriod
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.SearchKind
import com.freellm.chat.domain.SearchLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun nameRes(k: SearchKind): Int = when (k) {
    SearchKind.GOOGLE -> R.string.search_name_google
    SearchKind.BRAVE -> R.string.search_name_brave
    SearchKind.TAVILY -> R.string.search_name_tavily
    SearchKind.SERPER -> R.string.search_name_serper
}

private fun descRes(k: SearchKind): Int = when (k) {
    SearchKind.GOOGLE -> R.string.search_desc_google
    SearchKind.BRAVE -> R.string.search_desc_brave
    SearchKind.TAVILY -> R.string.search_desc_tavily
    SearchKind.SERPER -> R.string.search_desc_serper
}

private fun stepsRes(k: SearchKind): Int = when (k) {
    SearchKind.GOOGLE -> R.string.search_steps_google
    SearchKind.BRAVE -> R.string.search_steps_brave
    SearchKind.TAVILY -> R.string.search_steps_tavily
    SearchKind.SERPER -> R.string.search_steps_serper
}

private fun warnRes(k: SearchKind): Int? = when (k) {
    SearchKind.GOOGLE -> R.string.search_warn_google
    SearchKind.BRAVE -> R.string.search_warn_brave
    else -> null
}

private fun keyUrl(k: SearchKind): String = when (k) {
    SearchKind.GOOGLE -> "https://developers.google.com/custom-search/v1/overview"
    SearchKind.BRAVE -> "https://brave.com/search/api/"
    SearchKind.TAVILY -> "https://app.tavily.com/"
    SearchKind.SERPER -> "https://serper.dev/"
}

private fun periodRes(p: LimitPeriod): Int = when (p) {
    LimitPeriod.DAY -> R.string.search_period_day
    LimitPeriod.MONTH -> R.string.search_period_month
    LimitPeriod.TOTAL -> R.string.search_period_total
}

@Composable
private fun StepCard(number: Int, title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corners.card))
            .background(cs.surfaceContainerHigh)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(cs.tertiary),
                contentAlignment = Alignment.Center
            ) { Text(number.toString(), color = cs.onTertiary, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
fun SearchSettingsScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val selectedId by app.settings.searchProvider.collectAsStateWithLifecycle(initialValue = "tavily")
    val kind = SearchKind.of(selectedId) ?: SearchKind.TAVILY
    val cacheHours by app.settings.searchCacheHours.collectAsStateWithLifecycle(initialValue = 6)
    val overrides by app.settings.searchLimits.collectAsStateWithLifecycle(initialValue = emptyMap())
    val changes by app.secrets.changes.collectAsStateWithLifecycle()
    val saved = remember(changes, kind) { SearchFactory.configured(kind, app.secrets) }
    var key by remember(kind) { mutableStateOf("") }
    var cx by remember(kind) { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember(kind) { mutableStateOf<String?>(null) }
    var ok by remember(kind) { mutableStateOf(false) }
    val limit = SearchLimits.effective(kind, overrides)
    var limitText by remember(kind, limit.amount) { mutableStateOf(limit.amount.toString()) }
    val used = remember(kind, result, limit.amount, changes) {
        app.searchQuota.used(kind, limit, System.currentTimeMillis())
    }
    val okText = stringResource(R.string.search_test_ok)
    val needKey = stringResource(R.string.search_need_key)
    val failText = stringResource(R.string.search_test_failed)
    SecureWindow()

    Scaffold(topBar = { AppTopBar(stringResource(R.string.search_settings_title), onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text(
                stringResource(R.string.search_intro),
                Modifier.appear(0),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            StepCard(1, stringResource(R.string.search_step_provider), Modifier.appear(1)) {
                SearchKind.entries.forEach { k ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Corners.md))
                            .clickable { scope.launch { app.settings.setSearchProvider(k.id) } }
                            .padding(vertical = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = k == kind, onClick = { scope.launch { app.settings.setSearchProvider(k.id) } })
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(nameRes(k)), style = MaterialTheme.typography.bodyLarge)
                                if (k == SearchKind.TAVILY) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.search_recommended),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.tertiary
                                    )
                                }
                                if (SearchFactory.configured(k, app.secrets)) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.status_ready),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.tertiary
                                    )
                                }
                            }
                            Text(
                                stringResource(descRes(k)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                warnRes(kind)?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = cs.secondary)
                }
            }
            StepCard(2, stringResource(R.string.search_step_get_key), Modifier.appear(2)) {
                Text(stringResource(stepsRes(kind)), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { runCatching { uri.openUri(keyUrl(kind)) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_key_page))
                }
            }
            StepCard(3, stringResource(R.string.search_step_paste), Modifier.appear(3)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.api_key)) },
                    placeholder = { if (saved) Text(stringResource(R.string.key_saved_hint)) },
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(
                                if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                stringResource(if (show) R.string.hide_key else R.string.show_key)
                            )
                        }
                    }
                )
                if (kind == SearchKind.GOOGLE) {
                    OutlinedTextField(
                        value = cx,
                        onValueChange = { cx = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_cx)) }
                    )
                }
            }
            StepCard(4, stringResource(R.string.search_step_test), Modifier.appear(4)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            result = null
                            val id = SearchFactory.keyId(kind)
                            val k = key.trim().ifEmpty { app.secrets.apiKey(id).orEmpty() }
                            val c = cx.trim().ifEmpty { app.secrets.accountId(id).orEmpty() }
                            val p = SearchFactory.build(kind, app.searchHttp, k, c)
                            if (p == null) {
                                ok = false
                                result = needKey
                            } else {
                                try {
                                    val r = p.search("test", 1)
                                    app.searchQuota.record(kind, limit, System.currentTimeMillis())
                                    app.secrets.setApiKey(id, k)
                                    if (kind == SearchKind.GOOGLE) app.secrets.setAccountId(id, c)
                                    app.settings.setSearchProvider(kind.id)
                                    ok = true
                                    result = okText.replace("%d", r.size.toString())
                                    key = ""
                                    cx = ""
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: LlmException) {
                                    ok = false
                                    result = ctx.errorText(e)
                                } catch (e: Throwable) {
                                    ok = false
                                    result = failText
                                }
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && (key.isNotBlank() || saved),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.testing))
                    } else {
                        Text(stringResource(R.string.search_test_save))
                    }
                }
                result?.let { Text(it, color = if (ok) cs.tertiary else cs.error) }
                if (saved) {
                    TextButton(onClick = {
                        val id = SearchFactory.keyId(kind)
                        app.secrets.setApiKey(id, null)
                        app.secrets.setAccountId(id, null)
                        result = null
                    }) { Text(stringResource(R.string.remove_key), color = cs.error) }
                }
            }
            StepCard(5, stringResource(R.string.search_step_limits), Modifier.appear(5)) {
                Text(
                    stringResource(R.string.search_usage_line, used, limit.amount, stringResource(periodRes(limit.period))),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { v ->
                        limitText = v.filter { it.isDigit() }.take(7)
                        limitText.toIntOrNull()?.takeIf { it > 0 }?.let { n -> scope.launch { app.settings.setSearchLimit(kind, n) } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_limit_label, stringResource(periodRes(limit.period)))) },
                    supportingText = { Text(stringResource(R.string.search_limit_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            SettingsGroup(Modifier.appear(6)) {
                SettingsRow(
                    Icons.Outlined.Speed,
                    stringResource(R.string.search_cache_hours),
                    stringResource(R.string.search_cache_hours_desc),
                    onClick = {
                        val steps = listOf(1, 6, 24, 72)
                        val next = steps.firstOrNull { it > cacheHours } ?: steps.first()
                        scope.launch { app.settings.setSearchCacheHours(next) }
                    },
                    trailing = { Text(stringResource(R.string.cache_ttl_value, cacheHours), color = cs.onSurfaceVariant) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
