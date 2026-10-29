@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.LaunchedEffect
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.data.MAX_KEYS
import com.freellm.chat.R
import com.freellm.chat.data.ProviderStatus
import kotlinx.coroutines.launch

@Composable
private fun statusLabel(s: ProviderStatus): String = when (s) {
    ProviderStatus.NOT_CONFIGURED -> stringResource(R.string.status_not_configured)
    ProviderStatus.READY -> stringResource(R.string.status_ready)
    ProviderStatus.FAILED -> stringResource(R.string.status_failed)
}

@Composable
private fun statusColor(s: ProviderStatus): Color = when (s) {
    ProviderStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
    ProviderStatus.READY -> MaterialTheme.colorScheme.tertiary
    ProviderStatus.FAILED -> MaterialTheme.colorScheme.error
}

@Composable
fun ProvidersScreen(app: FreeLlmApp, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list by app.manager.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var local by remember { mutableStateOf(list) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(list) { if (!dragging) local = list }
    val lazyState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyState) { from, to ->
        local = local.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.providers), onBack) }) { pad ->
        androidx.compose.foundation.layout.Column(Modifier.padding(pad)) {
        ProvidersArt(Modifier.fillMaxWidth().height(120.dp).appear(0))
        LazyColumn(
            Modifier.weight(1f),
            state = lazyState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            itemsIndexed(local, key = { _, u -> u.def.id }) { index, ui ->
                ReorderableItem(reorderState, key = ui.def.id) {
                    Card(
                        onClick = { onOpen(ui.def.id) },
                        modifier = Modifier.fillMaxWidth().appear(index),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(Corners.card)
                    ) {
                        Row(
                            Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ui.def.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    statusLabel(ui.status),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = statusColor(ui.status)
                                )
                            }
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = { dragging = true },
                                    onDragStopped = {
                                        dragging = false
                                        val ids = local.map { it.def.id }
                                        scope.launch { app.manager.setOrder(ids) }
                                    }
                                )
                            ) {
                                Icon(Icons.Filled.DragHandle, stringResource(R.string.move_up))
                            }
                            Switch(
                                checked = ui.enabled,
                                enabled = ui.hasKey || ui.def.keyless,
                                onCheckedChange = { on -> scope.launch { app.manager.setEnabled(ui.def.id, on) } }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun ProviderWizardScreen(app: FreeLlmApp, providerId: String, onBack: () -> Unit) {
    val list by app.manager.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val ui = list.firstOrNull { it.def.id == providerId }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val secretChanges by app.secrets.changes.collectAsStateWithLifecycle()
    val savedKeys = remember(secretChanges, providerId) { app.manager.keysOf(providerId) }

    var key by remember(providerId) { mutableStateOf("") }
    var show by remember(providerId) { mutableStateOf(false) }
    var base by remember(providerId) { mutableStateOf(app.manager.baseUrlOf(providerId)) }
    androidx.compose.runtime.LaunchedEffect(ui?.def?.baseUrl) {
        val d = ui?.def?.baseUrl
        if (base.isBlank() && d != null) base = d
    }
    var acct by remember(providerId) { mutableStateOf(app.manager.accountIdOf(providerId)) }
    var modelId by remember(providerId) { mutableStateOf(app.manager.modelIdOf(providerId)) }
    var busy by remember(providerId) { mutableStateOf(false) }
    var result by remember(providerId) { mutableStateOf<String?>(null) }
    var ok by remember(providerId) { mutableStateOf(false) }
    val okText = stringResource(R.string.test_ok)
    SecureWindow()
    val clipCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(key) {
        if (key.isNotBlank()) {
            kotlinx.coroutines.delay(30_000)
            clearClipboardIfMatches(clipCtx, key.trim().split(Regex("\\s+")).first())
        }
    }
    var found by remember(providerId) { mutableStateOf<List<String>>(emptyList()) }
    val connOk = stringResource(R.string.conn_ok)
    val noModels = stringResource(R.string.no_models_found)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { AppTopBar(ui?.def?.name ?: "", onBack) }
    ) { pad ->
        if (ui == null) return@Scaffold
        val def = ui.def
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            KeysArt(Modifier.fillMaxWidth().height(130.dp).appear(0))
            Text(stringResource(R.string.setup_steps), Modifier.appear(1), style = MaterialTheme.typography.titleMedium)
            def.steps.forEachIndexed { i, s ->
                Row(Modifier.appear(i + 2)) {
                    Text("${i + 1}.", Modifier.width(28.dp), color = MaterialTheme.colorScheme.tertiary)
                    Text(s, Modifier.weight(1f))
                }
            }
            if (def.keyUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = { runCatching { uri.openUri(def.keyUrl) } },
                    Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.open_key_page)) }
            }
            if (def.warnings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(Corners.card)
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium)
                        def.warnings.forEach { w -> Text("• $w", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (savedKeys.isNotEmpty()) {
                Text(stringResource(R.string.keys_saved_title, savedKeys.size), style = MaterialTheme.typography.titleMedium)
                savedKeys.forEachIndexed { i, k ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(R.string.key_item, i + 1), Modifier.width(72.dp), color = MaterialTheme.colorScheme.tertiary)
                        Text(maskKey(k), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { scope.launch { app.manager.removeKeyAt(def.id, i) } }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.key_delete))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = savedKeys.size < MAX_KEYS,
                label = { Text(stringResource(if (savedKeys.isNotEmpty()) R.string.add_another_key else if (def.keyless) R.string.api_key_optional else R.string.api_key)) },
                supportingText = { Text(stringResource(if (savedKeys.size >= MAX_KEYS) R.string.keys_max_reached else R.string.multi_key_hint, MAX_KEYS)) },
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
            if (def.needsAccountId) {
                OutlinedTextField(
                    value = acct,
                    onValueChange = { acct = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.account_id)) }
                )
            }
            OutlinedTextField(
                value = base,
                onValueChange = { base = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.base_url_optional)) },
                placeholder = { Text(if (def.custom) "http://192.168.1.10:11434/v1" else def.baseUrl) }
            )
            if (def.custom) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.model_id)) }
                )
            }
            if (def.id == "ollama") {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            result = null
                            val (ids, err) = app.manager.discoverModels(base, key)
                            busy = false
                            if (ids.isNotEmpty()) {
                                found = ids
                                ok = true
                                result = connOk.replace("%d", ids.size.toString())
                                if (modelId.isBlank()) modelId = ids.first()
                            } else {
                                ok = false
                                result = err?.let { clipCtx.errorText(it) } ?: noModels
                            }
                        }
                    },
                    enabled = !busy && base.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.test_connection_discover)) }
                if (found.isNotEmpty()) {
                    Text(stringResource(R.string.discovered_models), style = MaterialTheme.typography.titleMedium)
                    val chosen = modelId.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    found.forEach { id ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = if (id in chosen) chosen - id else chosen + id
                                    modelId = next.joinToString(",")
                                },
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(checked = id in chosen, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(id, Modifier.weight(1f))
                        }
                    }
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        result = null
                        val err = app.manager.testAndSave(def, key, base, acct, modelId)
                        busy = false
                        ok = err == null
                        result = if (err == null) okText else clipCtx.errorText(err)
                        if (err == null) key = ""
                    }
                },
                enabled = !busy && (key.isNotBlank() || def.keyless) && (!def.custom || (base.isNotBlank() && modelId.isNotBlank())),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.testing))
                } else {
                    Text(stringResource(R.string.test_key))
                }
            }
            val res = result
            if (res != null) {
                Text(
                    res,
                    color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
            if (ui.hasKey) {
                TextButton(onClick = { scope.launch { app.manager.removeKey(def.id) } }) {
                    Text(stringResource(if (savedKeys.size > 1) R.string.remove_all_keys else R.string.remove_key), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun maskKey(k: String): String =
    if (k.length <= 10) "••••" + k.takeLast(2) else k.take(4) + "••••" + k.takeLast(4)
