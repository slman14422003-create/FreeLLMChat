@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.domain.ToolCatalog
import com.freellm.chat.domain.ToolGroup
import com.freellm.chat.domain.ToolPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SwitchRow(icon: ImageVector, title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SettingsRow(
        icon, title, desc,
        onClick = { onChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = cs.tertiary, checkedThumbColor = androidx.compose.ui.graphics.Color.White)
            )
        }
    )
}

private fun toolName(id: String): Int = when (id) {
    "calculator" -> R.string.tool_calculator
    "convert" -> R.string.tool_convert
    "datetime" -> R.string.tool_datetime
    "web_search" -> R.string.tool_web_search
    "fetch_url" -> R.string.tool_fetch_url
    "wikipedia" -> R.string.tool_wikipedia
    "weather" -> R.string.tool_weather
    "encode" -> R.string.tool_encode
    "random" -> R.string.tool_random
    "regex_test" -> R.string.tool_regex_test
    "json_query" -> R.string.tool_json_query
    "text_stats" -> R.string.tool_text_stats
    "run_js" -> R.string.tool_run_js
    "run_code" -> R.string.tool_run_code
    "check_syntax" -> R.string.tool_check_syntax
    "project_template" -> R.string.tool_project_template
    "project_plan" -> R.string.tool_project_plan
    "project_write" -> R.string.tool_project_write
    "project_read" -> R.string.tool_project_read
    "project_list" -> R.string.tool_project_list
    else -> R.string.tool_project_delete
}

private fun toolDesc(id: String): Int = when (id) {
    "calculator" -> R.string.tool_calculator_desc
    "convert" -> R.string.tool_convert_desc
    "datetime" -> R.string.tool_datetime_desc
    "web_search" -> R.string.tool_web_search_desc
    "fetch_url" -> R.string.tool_fetch_url_desc
    "wikipedia" -> R.string.tool_wikipedia_desc
    "weather" -> R.string.tool_weather_desc
    "encode" -> R.string.tool_encode_desc
    "random" -> R.string.tool_random_desc
    "regex_test" -> R.string.tool_regex_test_desc
    "json_query" -> R.string.tool_json_query_desc
    "text_stats" -> R.string.tool_text_stats_desc
    "run_js" -> R.string.tool_run_js_desc
    "run_code" -> R.string.tool_run_code_desc
    "check_syntax" -> R.string.tool_check_syntax_desc
    "project_template" -> R.string.tool_project_template_desc
    "project_plan" -> R.string.tool_project_plan_desc
    "project_write" -> R.string.tool_project_write_desc
    "project_read" -> R.string.tool_project_read_desc
    "project_list" -> R.string.tool_project_list_desc
    else -> R.string.tool_project_delete_desc
}

private fun groupIcon(g: ToolGroup): ImageVector = when (g) {
    ToolGroup.MATH -> Icons.Outlined.Calculate
    ToolGroup.INFO -> Icons.Outlined.Language
    ToolGroup.CODE -> Icons.Outlined.Code
    ToolGroup.PROJECT -> Icons.Outlined.Folder
}

@Composable
fun ToolsSettingsScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val enabled by app.settings.toolsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val mode by app.settings.toolsMode.collectAsStateWithLifecycle(initialValue = ToolPolicy.MODE_SMART)
    val off by app.settings.toolsOff.collectAsStateWithLifecycle(initialValue = emptySet())
    val autoFix by app.settings.projectAutoFix.collectAsStateWithLifecycle(initialValue = true)
    val runner by app.settings.runnerUrl.collectAsStateWithLifecycle(initialValue = "")
    var dialog by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var stats by remember { mutableStateOf(0 to 0L) }
    LaunchedEffect(refresh) {
        stats = withContext(Dispatchers.IO) { app.workspace.totalFiles() to app.workspace.totalBytes() }
    }

    when (dialog) {
        "runner" -> {
            var text by remember { mutableStateOf(runner) }
            AlertDialog(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
                containerColor = com.freellm.chat.ui.surfaceHigh(),
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.tools_runner)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("https://runner.example.com") }
                        )
                        Text(stringResource(R.string.tools_runner_hint), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        scope.launch { app.settings.setRunnerUrl(text.trim()) }
                        dialog = null
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        "wipe" -> AlertDialog(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
            containerColor = com.freellm.chat.ui.surfaceHigh(),
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.tools_workspace_clear)) },
            text = { Text(stringResource(R.string.tools_workspace_confirm)) },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { app.workspace.clearAll() }
                        refresh++
                    }
                    dialog = null
                }) { Text(stringResource(R.string.delete), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(containerColor = cs.background, topBar = { AppTopBar(stringResource(R.string.tools_title), onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(stringResource(R.string.tools_intro), color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.sm))
            SettingsGroup(Modifier.appear(0)) {
                SwitchRow(Icons.Outlined.Terminal, stringResource(R.string.tools_master), stringResource(R.string.tools_master_desc), enabled) {
                    scope.launch { app.settings.setToolsEnabled(it) }
                }
                SwitchRow(Icons.Outlined.Folder, stringResource(R.string.project_autofix), stringResource(R.string.project_autofix_desc), autoFix) {
                    scope.launch { app.settings.setProjectAutoFix(it) }
                }
                if (enabled) {
                    SettingsRow(
                        Icons.Outlined.Speed,
                        stringResource(R.string.tools_mode),
                        stringResource(if (mode == ToolPolicy.MODE_ALWAYS) R.string.tools_mode_always_desc else R.string.tools_mode_smart_desc),
                        onClick = {
                            val next = if (mode == ToolPolicy.MODE_ALWAYS) ToolPolicy.MODE_SMART else ToolPolicy.MODE_ALWAYS
                            scope.launch { app.settings.setToolsMode(next) }
                        },
                        trailing = {
                            Text(
                                stringResource(if (mode == ToolPolicy.MODE_ALWAYS) R.string.tools_mode_always else R.string.tools_mode_smart),
                                color = cs.onSurfaceVariant
                            )
                        }
                    )
                }
            }
            if (enabled) {
                SectionLabel(stringResource(R.string.tools_list_label))
                SettingsGroup(Modifier.appear(1)) {
                    ToolCatalog.all.forEach { t ->
                        SwitchRow(groupIcon(t.group), stringResource(toolName(t.id)), stringResource(toolDesc(t.id)), t.id !in off) { on ->
                            scope.launch { app.settings.setToolOff(t.id, !on) }
                        }
                    }
                }
                SectionLabel(stringResource(R.string.tools_advanced))
                SettingsGroup(Modifier.appear(2)) {
                    SettingsRow(
                        Icons.Outlined.Code,
                        stringResource(R.string.tools_runner),
                        runner.ifBlank { stringResource(R.string.not_set) },
                        onClick = { dialog = "runner" }
                    )
                    SettingsRow(
                        Icons.Outlined.Folder,
                        stringResource(R.string.tools_workspace),
                        stringResource(R.string.tools_workspace_info, stats.first, stats.second / 1024)
                    )
                    if (stats.first > 0) {
                        SettingsRow(
                            Icons.Outlined.DeleteOutline,
                            stringResource(R.string.tools_workspace_clear),
                            onClick = { dialog = "wipe" }
                        )
                    }
                }
            }
            Text(stringResource(R.string.tools_libs), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(Spacing.sm))
        }
    }
}
