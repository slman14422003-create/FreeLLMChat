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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VpnKey
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
import com.freellm.chat.data.GroqVoice
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

private fun <T> cycle(list: List<T>, cur: T): T = list[(list.indexOf(cur) + 1) % list.size]

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
    val mode by app.settings.toolsMode.collectAsStateWithLifecycle(initialValue = ToolPolicy.MODE_ALWAYS)
    val off by app.settings.toolsOff.collectAsStateWithLifecycle(initialValue = emptySet())
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
                    TextButton(onClick = {
                        scope.launch { app.settings.setRunnerUrl(text.trim()) }
                        dialog = null
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        "wipe" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.tools_workspace_clear)) },
            text = { Text(stringResource(R.string.tools_workspace_confirm)) },
            confirmButton = {
                TextButton(onClick = {
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

@Composable
fun VoiceSettingsScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val secretChanges by app.secrets.changes.collectAsStateWithLifecycle()
    val hasKey = remember(secretChanges) { app.groqVoice.hasKey() }
    val stt by app.settings.voiceStt.collectAsStateWithLifecycle(initialValue = "auto")
    val model by app.settings.voiceSttModel.collectAsStateWithLifecycle(initialValue = "whisper-large-v3-turbo")
    val lang by app.settings.voiceLang.collectAsStateWithLifecycle(initialValue = "auto")
    val tts by app.settings.voiceTts.collectAsStateWithLifecycle(initialValue = "auto")
    val nameEn by app.settings.voiceNameEn.collectAsStateWithLifecycle(initialValue = "autumn")
    val nameAr by app.settings.voiceNameAr.collectAsStateWithLifecycle(initialValue = "noura")
    val barge by app.settings.voiceBargeIn.collectAsStateWithLifecycle(initialValue = false)
    val silence by app.settings.voiceSilenceMs.collectAsStateWithLifecycle(initialValue = 900)
    val dictation by app.settings.voiceDictation.collectAsStateWithLifecycle(initialValue = true)
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var checking by remember { mutableStateOf(false) }
    var termsResult by remember { mutableStateOf<Map<String, com.freellm.chat.data.VoiceFail?>>(emptyMap()) }
    fun termsLabel(f: com.freellm.chat.data.VoiceFail?): Int = when (f) {
        null -> R.string.voice_terms_ok
        com.freellm.chat.data.VoiceFail.TERMS -> R.string.voice_terms_needed
        com.freellm.chat.data.VoiceFail.RATE -> R.string.voice_terms_rate
        com.freellm.chat.data.VoiceFail.AUTH -> R.string.voice_terms_auth
        else -> R.string.voice_terms_error
    }

    val modes = listOf("auto", "groq", "device")
    fun modeLabel(m: String): Int = when (m) {
        "groq" -> R.string.voice_mode_groq
        "device" -> R.string.voice_mode_device
        else -> R.string.voice_mode_auto
    }
    val langs = listOf("auto", "ar", "en", "fr", "tr", "ur", "es", "de")
    val silences = listOf(600, 900, 1300, 1800)

    Scaffold(containerColor = cs.background, topBar = { AppTopBar(stringResource(R.string.voice_settings_title), onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SettingsGroup(Modifier.appear(0)) {
                SettingsRow(
                    Icons.Outlined.VpnKey,
                    stringResource(R.string.voice_groq_key),
                    stringResource(if (hasKey) R.string.voice_key_ok else R.string.voice_key_missing)
                )
            }
            SectionLabel(stringResource(R.string.voice_hearing))
            SettingsGroup(Modifier.appear(1)) {
                SettingsRow(
                    Icons.Outlined.Mic,
                    stringResource(R.string.voice_stt),
                    stringResource(R.string.voice_stt_desc),
                    onClick = { scope.launch { app.settings.setVoiceStt(cycle(modes, stt)) } },
                    trailing = { Text(stringResource(modeLabel(stt)), color = cs.onSurfaceVariant) }
                )
                SettingsRow(
                    Icons.Outlined.Speed,
                    stringResource(R.string.voice_stt_model),
                    model,
                    onClick = { scope.launch { app.settings.setVoiceSttModel(cycle(GroqVoice.STT_MODELS, model)) } }
                )
                SettingsRow(
                    Icons.Outlined.Language,
                    stringResource(R.string.voice_lang),
                    stringResource(R.string.voice_lang_desc),
                    onClick = { scope.launch { app.settings.setVoiceLang(cycle(langs, lang)) } },
                    trailing = { Text(if (lang == "auto") stringResource(R.string.voice_mode_auto) else lang, color = cs.onSurfaceVariant) }
                )
                SettingsRow(
                    Icons.Outlined.Info,
                    stringResource(R.string.voice_silence),
                    stringResource(R.string.voice_silence_desc),
                    onClick = { scope.launch { app.settings.setVoiceSilenceMs(cycle(silences, silence)) } },
                    trailing = { Text(stringResource(R.string.voice_silence_value, silence), color = cs.onSurfaceVariant) }
                )
                SwitchRow(Icons.Outlined.Mic, stringResource(R.string.voice_dictation), stringResource(R.string.voice_dictation_desc), dictation) {
                    scope.launch { app.settings.setVoiceDictation(it) }
                }
            }
            SectionLabel(stringResource(R.string.voice_speaking_label))
            SettingsGroup(Modifier.appear(2)) {
                SettingsRow(
                    Icons.Outlined.Speed,
                    stringResource(R.string.voice_tts),
                    stringResource(R.string.voice_tts_desc),
                    onClick = { scope.launch { app.settings.setVoiceTts(cycle(modes, tts)) } },
                    trailing = { Text(stringResource(modeLabel(tts)), color = cs.onSurfaceVariant) }
                )
                SettingsRow(
                    Icons.Outlined.Mic,
                    stringResource(R.string.voice_name_en),
                    nameEn,
                    onClick = { scope.launch { app.settings.setVoiceNameEn(cycle(GroqVoice.EN_VOICES, nameEn)) } }
                )
                SettingsRow(
                    Icons.Outlined.Mic,
                    stringResource(R.string.voice_name_ar),
                    nameAr,
                    onClick = { scope.launch { app.settings.setVoiceNameAr(cycle(GroqVoice.AR_VOICES, nameAr)) } }
                )
                SwitchRow(Icons.Outlined.SwapHoriz, stringResource(R.string.voice_barge_in), stringResource(R.string.voice_barge_in_desc), barge) {
                    scope.launch { app.settings.setVoiceBargeIn(it) }
                }
            }
            SectionLabel(stringResource(R.string.voice_terms_title))
            SettingsGroup(Modifier.appear(3)) {
                SettingsRow(
                    Icons.Outlined.Language,
                    stringResource(R.string.voice_terms_en),
                    stringResource(R.string.voice_terms_open_desc),
                    onClick = { uri.openUri(GroqVoice.termsUrl(false)) },
                    trailing = { termsResult["en"]?.let { Text(stringResource(termsLabel(it)), color = cs.onSurfaceVariant) } }
                )
                SettingsRow(
                    Icons.Outlined.Language,
                    stringResource(R.string.voice_terms_ar),
                    stringResource(R.string.voice_terms_open_desc),
                    onClick = { uri.openUri(GroqVoice.termsUrl(true)) },
                    trailing = { termsResult["ar"]?.let { Text(stringResource(termsLabel(it)), color = cs.onSurfaceVariant) } }
                )
                SettingsRow(
                    Icons.Outlined.Info,
                    stringResource(R.string.voice_terms_check),
                    stringResource(if (checking) R.string.voice_terms_checking else R.string.voice_terms_check_desc),
                    onClick = {
                        if (!checking && hasKey) {
                            checking = true
                            scope.launch {
                                val en = app.groqVoice.checkTts(false)
                                val ar = app.groqVoice.checkTts(true)
                                termsResult = mapOf("en" to en, "ar" to ar)
                                checking = false
                            }
                        }
                    }
                )
            }
            Text(stringResource(R.string.voice_terms_help), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(Spacing.sm))
            Text(stringResource(R.string.voice_note), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(Spacing.sm))
        }
    }
}
