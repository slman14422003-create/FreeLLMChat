@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.freellm.chat.R
import com.freellm.chat.data.ProviderStatus
import com.freellm.chat.domain.arabic
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
    Scaffold(topBar = { AppTopBar(stringResource(R.string.providers), onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list, key = { it.def.id }) { ui ->
                Card(
                    onClick = { onOpen(ui.def.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
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
                        IconButton(onClick = { scope.launch { app.manager.move(ui.def.id, -1) } }) {
                            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { scope.launch { app.manager.move(ui.def.id, 1) } }) {
                            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down))
                        }
                        Switch(
                            checked = ui.enabled,
                            enabled = ui.hasKey,
                            onCheckedChange = { on -> scope.launch { app.manager.setEnabled(ui.def.id, on) } }
                        )
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

    var key by remember(providerId) { mutableStateOf("") }
    var show by remember(providerId) { mutableStateOf(false) }
    var base by remember(providerId) { mutableStateOf(app.manager.baseUrlOf(providerId)) }
    androidx.compose.runtime.LaunchedEffect(ui?.def?.baseUrl) {
        val d = ui?.def?.baseUrl
        if (base.isBlank() && d != null) base = d
    }
    var acct by remember(providerId) { mutableStateOf(app.manager.accountIdOf(providerId)) }
    var busy by remember(providerId) { mutableStateOf(false) }
    var result by remember(providerId) { mutableStateOf<String?>(null) }
    var ok by remember(providerId) { mutableStateOf(false) }
    val okText = stringResource(R.string.test_ok)

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.setup_steps), style = MaterialTheme.typography.titleMedium)
            def.steps.forEachIndexed { i, s ->
                Row {
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium)
                        def.warnings.forEach { w -> Text("• $w", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.api_key)) },
                placeholder = { if (ui.hasKey) Text(stringResource(R.string.key_saved_hint)) },
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
                placeholder = { Text(def.baseUrl) }
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        result = null
                        val err = app.manager.testAndSave(def, key, base, acct)
                        busy = false
                        ok = err == null
                        result = if (err == null) okText else err.arabic()
                        if (err == null) key = ""
                    }
                },
                enabled = !busy && key.isNotBlank(),
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
                    Text(stringResource(R.string.remove_key), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
