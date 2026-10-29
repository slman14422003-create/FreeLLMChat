@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.BuildConfig
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.domain.ModelHealth
import com.freellm.chat.domain.Target
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Corners.sm))
                            .clickable { onPick(key) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == key, onClick = { onPick(key) })
                        Text(label)
                    }
                }
            }
        }
    )
}

@Composable
fun SettingsScreen(app: FreeLlmApp, onBack: () -> Unit, onProviders: () -> Unit, onStatus: () -> Unit, onUsage: () -> Unit, onBrain: () -> Unit, onSearch: () -> Unit, onAbout: () -> Unit, onPrivacy: () -> Unit, onTools: () -> Unit = {}, onVoice: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val theme by app.settings.theme.collectAsStateWithLifecycle(initialValue = "system")
    val proxy by app.settings.proxyUrl.collectAsStateWithLifecycle(initialValue = "")
    val autoSwitch by app.settings.autoSwitch.collectAsStateWithLifecycle(initialValue = true)
    val fastSwitch by app.settings.fastSwitch.collectAsStateWithLifecycle(initialValue = true)
    val autoResume by app.settings.autoResume.collectAsStateWithLifecycle(initialValue = true)
    val tokenSaver by app.settings.tokenSaver.collectAsStateWithLifecycle(initialValue = true)
    val ctxSummary by app.settings.contextSummary.collectAsStateWithLifecycle(initialValue = true)
    val answerCache by app.settings.answerCache.collectAsStateWithLifecycle(initialValue = true)
    val cacheTtl by app.settings.cacheTtlHours.collectAsStateWithLifecycle(initialValue = 24)
    val dynTokens by app.settings.dynamicTokens.collectAsStateWithLifecycle(initialValue = true)
    val smartAttach by app.settings.smartAttachments.collectAsStateWithLifecycle(initialValue = true)
    val smartRouting by app.settings.smartRouting.collectAsStateWithLifecycle(initialValue = true)
    val budgetAlerts by app.settings.budgetAlerts.collectAsStateWithLifecycle(initialValue = true)
    val compareMerge by app.settings.compareMerge.collectAsStateWithLifecycle(initialValue = true)
    val answerTemp by app.settings.answerTemperature.collectAsStateWithLifecycle(initialValue = true)
    val verifyAnswers by app.settings.verifyAnswers.collectAsStateWithLifecycle(initialValue = false)
    val webSearchRaw: Boolean? by app.settings.webSearch.collectAsStateWithLifecycle(initialValue = null)
    val secretChanges by app.secrets.changes.collectAsStateWithLifecycle()
    val localSearchOn by app.settings.localSearch.collectAsStateWithLifecycle(initialValue = false)
    val hasSearchKey = remember(secretChanges, localSearchOn) { localSearchOn || com.freellm.chat.data.SearchFactory.anyConfigured(app.secrets) }
    val avoidTraining by app.settings.avoidTraining.collectAsStateWithLifecycle(initialValue = false)
    val brainLearn by app.settings.brainLearn.collectAsStateWithLifecycle(initialValue = true)
    val brainLocalFirst by app.settings.brainLocalFirst.collectAsStateWithLifecycle(initialValue = true)
    val brainContext by app.settings.brainContext.collectAsStateWithLifecycle(initialValue = true)
    val deepThink by app.settings.deepThink.collectAsStateWithLifecycle(initialValue = false)
    var brainCount by remember { mutableStateOf(app.brainStore.brain.size()) }
    val style by app.settings.style.collectAsStateWithLifecycle(initialValue = "default")
    val instructions by app.settings.instructions.collectAsStateWithLifecycle(initialValue = "")
    val providers by app.manager.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val curLang = remember {
        ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("lang", "ar") ?: "ar"
    }
    var dialog by remember { mutableStateOf<String?>(null) }
    var advanced by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    val themeOptions = listOf(
        "system" to stringResource(R.string.theme_system),
        "light" to stringResource(R.string.theme_light),
        "dark" to stringResource(R.string.theme_dark)
    )
    val langOptions = listOf(
        "ar" to stringResource(R.string.lang_ar),
        "en" to stringResource(R.string.lang_en),
        "system" to stringResource(R.string.theme_system)
    )
    val styleOptions = listOf(
        "default" to stringResource(R.string.style_default),
        "concise" to stringResource(R.string.style_concise),
        "detailed" to stringResource(R.string.style_detailed),
        "teacher" to stringResource(R.string.style_teacher)
    )
    val activeCount = providers.count { it.enabled }

    when (dialog) {
        "theme" -> ChoiceDialog(
            stringResource(R.string.theme), themeOptions, theme,
            onPick = {
                scope.launch { app.settings.setTheme(it) }
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        "lang" -> ChoiceDialog(
            stringResource(R.string.language), langOptions, curLang,
            onPick = {
                ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).edit().putString("lang", it).apply()
                dialog = null
                (ctx as? Activity)?.recreate()
            },
            onDismiss = { dialog = null }
        )
        "style" -> ChoiceDialog(
            stringResource(R.string.response_style), styleOptions, style,
            onPick = {
                scope.launch { app.settings.setStyle(it) }
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        "instructions" -> {
            var text by remember { mutableStateOf(instructions) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.custom_instructions)) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        placeholder = { Text(stringResource(R.string.custom_instructions_hint)) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { app.settings.setInstructions(text.trim()) }
                        dialog = null
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        "proxy" -> {
            var text by remember { mutableStateOf(proxy) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.proxy_url)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("https://name.workers.dev") }
                        )
                        Text(
                            stringResource(R.string.proxy_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { app.settings.setProxyUrl(text.trim()) }
                        dialog = null
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        "wipe" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_all_chats)) },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.chatRepo.deleteAll() }
                    dialog = null
                }) { Text(stringResource(R.string.delete), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(containerColor = cs.background, topBar = { AppTopBar(stringResource(R.string.settings), onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            val allEnabled = providers.isNotEmpty() && providers.all { it.enabled }
            if (allEnabled) {
                Row(
                    Modifier
                        .appear(0)
                        .clip(CircleShape)
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        Modifier.size(20.dp),
                        tint = androidx.compose.ui.graphics.Color(0xFF34C759)
                    )
                    Text(stringResource(R.string.all_providers_enabled), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Column(
                    Modifier
                        .appear(0)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Corners.card))
                        .background(cs.surfaceContainerHigh)
                        .padding(Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(stringResource(R.string.want_more), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.want_more_body), color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onProviders, shape = CircleShape) { Text(stringResource(R.string.add_provider)) }
                }
            }

            SettingsGroup(Modifier.appear(2)) {
                SettingsRow(
                    Icons.Outlined.Cloud,
                    stringResource(R.string.providers),
                    stringResource(R.string.providers_active, activeCount),
                    onClick = onProviders
                )
                SettingsRow(Icons.Outlined.Speed, stringResource(R.string.model_status), onClick = onStatus)
                SettingsRow(Icons.Outlined.Info, stringResource(R.string.usage_title), onClick = onUsage)
            }

            BackupSection(app, Modifier.appear(3))
            SecuritySection(app, Modifier.appear(3))
            SectionLabel(stringResource(R.string.preferences_label))
            SettingsGroup(Modifier.appear(3)) {
                SettingsRow(
                    Icons.Outlined.SwapHoriz,
                    stringResource(R.string.auto_switch),
                    stringResource(R.string.auto_switch_desc),
                    onClick = { scope.launch { app.settings.setAutoSwitch(!autoSwitch) } },
                    trailing = {
                        Switch(
                            checked = autoSwitch,
                            onCheckedChange = { scope.launch { app.settings.setAutoSwitch(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(
                    Icons.Outlined.Speed,
                    stringResource(R.string.fast_switch),
                    stringResource(R.string.fast_switch_desc),
                    onClick = { scope.launch { app.settings.setFastSwitch(!fastSwitch) } },
                    trailing = {
                        Switch(
                            checked = fastSwitch,
                            onCheckedChange = { scope.launch { app.settings.setFastSwitch(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(
                    Icons.Outlined.Speed,
                    stringResource(R.string.auto_resume),
                    stringResource(R.string.auto_resume_desc),
                    onClick = { scope.launch { app.settings.setAutoResume(!autoResume) } },
                    trailing = {
                        Switch(
                            checked = autoResume,
                            onCheckedChange = { scope.launch { app.settings.setAutoResume(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(
                    Icons.Outlined.AutoAwesome,
                    stringResource(R.string.token_saver),
                    stringResource(R.string.token_saver_desc),
                    onClick = { scope.launch { app.settings.setTokenSaver(!tokenSaver) } },
                    trailing = {
                        Switch(
                            checked = tokenSaver,
                            onCheckedChange = { scope.launch { app.settings.setTokenSaver(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(
                    Icons.Outlined.Language,
                    stringResource(R.string.search_settings_title),
                    stringResource(R.string.search_settings_desc),
                    onClick = onSearch
                )
                SettingsRow(
                    Icons.Outlined.Psychology,
                    stringResource(R.string.tools_title),
                    stringResource(R.string.tools_desc),
                    onClick = onTools
                )
                SettingsRow(
                    Icons.Outlined.Speed,
                    stringResource(R.string.voice_settings_title),
                    stringResource(R.string.voice_settings_desc),
                    onClick = onVoice
                )
                CrashReportRow()
                SettingsRow(
                    Icons.Outlined.Psychology,
                    stringResource(R.string.advanced_title),
                    stringResource(R.string.advanced_desc),
                    onClick = { advanced = !advanced },
                    trailing = {
                        Icon(
                            if (advanced) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            null
                        )
                    }
                )
                AnimatedVisibility(visible = advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
                        ToggleRow(Icons.Outlined.AutoAwesome, R.string.context_summary, R.string.context_summary_desc, ctxSummary) {
                            scope.launch { app.settings.setContextSummary(it) }
                        }
                        ToggleRow(Icons.Outlined.Speed, R.string.answer_cache, R.string.answer_cache_desc, answerCache) {
                            scope.launch { app.settings.setAnswerCache(it) }
                        }
                        if (answerCache) {
                            SettingsRow(
                                Icons.Outlined.Speed,
                                stringResource(R.string.cache_ttl),
                                stringResource(R.string.cache_ttl_desc),
                                onClick = {
                                    val steps = listOf(6, 24, 72, 168)
                                    val next = steps.firstOrNull { it > cacheTtl } ?: steps.first()
                                    scope.launch { app.settings.setCacheTtlHours(next) }
                                },
                                trailing = { Text(stringResource(R.string.cache_ttl_value, cacheTtl), color = cs.onSurfaceVariant) }
                            )
                        }
                        ToggleRow(Icons.Outlined.AutoAwesome, R.string.dynamic_tokens, R.string.dynamic_tokens_desc, dynTokens) {
                            scope.launch { app.settings.setDynamicTokens(it) }
                        }
                        ToggleRow(Icons.Outlined.AutoAwesome, R.string.smart_attach, R.string.smart_attach_desc, smartAttach) {
                            scope.launch { app.settings.setSmartAttachments(it) }
                        }
                        ToggleRow(Icons.Outlined.Psychology, R.string.smart_routing, R.string.smart_routing_desc, smartRouting) {
                            scope.launch { app.settings.setSmartRouting(it) }
                        }
                        ToggleRow(Icons.Outlined.Info, R.string.budget_alerts, R.string.budget_alerts_desc, budgetAlerts) {
                            scope.launch { app.settings.setBudgetAlerts(it) }
                        }
                        ToggleRow(Icons.Outlined.Language, R.string.web_search, R.string.web_search_desc, webSearchRaw ?: hasSearchKey) {
                            scope.launch { app.settings.setWebSearch(it) }
                        }
                        ToggleRow(Icons.Outlined.Speed, R.string.answer_temperature, R.string.answer_temperature_desc, answerTemp) {
                            scope.launch { app.settings.setAnswerTemperature(it) }
                        }
                        ToggleRow(Icons.Outlined.Psychology, R.string.verify_answers, R.string.verify_answers_desc, verifyAnswers) {
                            scope.launch { app.settings.setVerifyAnswers(it) }
                        }
                        ToggleRow(Icons.Outlined.SwapHoriz, R.string.compare_merge_setting, R.string.compare_merge_setting_desc, compareMerge) {
                            scope.launch { app.settings.setCompareMerge(it) }
                        }
                    }
                }
                SettingsRow(
                    Icons.Outlined.Info,
                    stringResource(R.string.avoid_training),
                    stringResource(R.string.avoid_training_desc),
                    onClick = { scope.launch { app.settings.setAvoidTraining(!avoidTraining) } },
                    trailing = {
                        Switch(
                            checked = avoidTraining,
                            onCheckedChange = { scope.launch { app.settings.setAvoidTraining(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(
                    Icons.Outlined.AutoAwesome,
                    stringResource(R.string.response_style),
                    styleOptions.firstOrNull { it.first == style }?.second,
                    onClick = { dialog = "style" }
                )
                SettingsRow(
                    Icons.Outlined.Edit,
                    stringResource(R.string.custom_instructions),
                    instructions.ifBlank { stringResource(R.string.not_set) },
                    onClick = { dialog = "instructions" }
                )
            }

            SectionLabel(stringResource(R.string.brain_title))
            SettingsGroup(Modifier.appear(4)) {
                SettingsRow(
                    Icons.Outlined.Psychology,
                    stringResource(R.string.brain_learn),
                    stringResource(R.string.brain_learn_desc),
                    onClick = { scope.launch { app.settings.setBrainLearn(!brainLearn) } },
                    trailing = {
                        Switch(
                            checked = brainLearn,
                            onCheckedChange = { scope.launch { app.settings.setBrainLearn(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(
                    Icons.Outlined.Speed,
                    stringResource(R.string.brain_local_first),
                    stringResource(R.string.brain_local_first_desc),
                    onClick = { scope.launch { app.settings.setBrainLocalFirst(!brainLocalFirst) } },
                    trailing = {
                        Switch(
                            checked = brainLocalFirst,
                            onCheckedChange = { scope.launch { app.settings.setBrainLocalFirst(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(
                    Icons.Outlined.AutoAwesome,
                    stringResource(R.string.brain_context),
                    stringResource(R.string.brain_context_desc),
                    onClick = { scope.launch { app.settings.setBrainContext(!brainContext) } },
                    trailing = {
                        Switch(
                            checked = brainContext,
                            onCheckedChange = { scope.launch { app.settings.setBrainContext(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(
                    Icons.Outlined.Psychology,
                    stringResource(R.string.deep_think),
                    stringResource(R.string.deep_think_desc),
                    onClick = { scope.launch { app.settings.setDeepThink(!deepThink) } },
                    trailing = {
                        Switch(
                            checked = deepThink,
                            onCheckedChange = { scope.launch { app.settings.setDeepThink(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = cs.tertiary,
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                )
                SettingsRow(Icons.Outlined.Psychology, stringResource(R.string.brain_manage), onClick = onBrain)
                SettingsRow(
                    Icons.Outlined.DeleteOutline,
                    stringResource(R.string.brain_clear),
                    stringResource(R.string.brain_stats, brainCount),
                    onClick = {
                        app.brainStore.brain.clear()
                        scope.launch { app.brainStore.save() }
                        brainCount = 0
                    }
                )
            }

            SectionLabel(stringResource(R.string.app_label))
            SettingsGroup(Modifier.appear(4)) {
                SettingsRow(
                    Icons.Outlined.DarkMode,
                    stringResource(R.string.theme),
                    themeOptions.firstOrNull { it.first == theme }?.second,
                    onClick = { dialog = "theme" }
                )
                SettingsRow(
                    Icons.Outlined.Language,
                    stringResource(R.string.language),
                    langOptions.firstOrNull { it.first == curLang }?.second,
                    onClick = { dialog = "lang" }
                )
                SettingsRow(
                    Icons.Outlined.VpnKey,
                    stringResource(R.string.proxy_url),
                    proxy.ifBlank { stringResource(R.string.not_set) },
                    onClick = { dialog = "proxy" }
                )
            }

            SectionLabel(stringResource(R.string.data_label))
            SettingsGroup(Modifier.appear(5)) {
                SettingsRow(
                    Icons.Outlined.DeleteOutline,
                    stringResource(R.string.delete_all_chats),
                    onClick = { dialog = "wipe" }
                )
                SettingsRow(
                    Icons.Outlined.Lock,
                    stringResource(R.string.privacy_title),
                    stringResource(R.string.privacy_row_d),
                    onClick = onPrivacy
                )
                SettingsRow(
                    Icons.Outlined.Info,
                    stringResource(R.string.about),
                    stringResource(R.string.about_body) + " · " + BuildConfig.VERSION_NAME,
                    onClick = onAbout
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatusScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var targets by remember { mutableStateOf<List<Target>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        targets = app.manager.activeTargets()
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Scaffold(containerColor = cs.background, topBar = { AppTopBar(stringResource(R.string.model_status), onBack) }) { pad ->
        if (targets.isEmpty()) {
            Text(
                stringResource(R.string.no_models),
                Modifier
                    .padding(pad)
                    .padding(Spacing.xxl),
                color = cs.onSurfaceVariant
            )
        } else {
            Column(
                Modifier
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
            ) {
                StatusArt(Modifier.fillMaxWidth().height(90.dp).appear(0))
                Spacer(Modifier.height(8.dp))
                SettingsGroup(Modifier.appear(6)) {
                    targets.forEach { t ->
                        val st = app.router.health.state(t, now)
                        val color = when (st) {
                            is ModelHealth.Ready -> cs.tertiary
                            is ModelHealth.Cooling -> cs.secondary
                            else -> cs.error
                        }
                        val label = when (st) {
                            is ModelHealth.Ready -> stringResource(R.string.status_ready)
                            is ModelHealth.Cooling ->
                                stringResource(R.string.status_cooling, maxOf(0L, (st.untilMs - now) / 1000).toInt())
                            is ModelHealth.Dead -> stringResource(R.string.status_dead)
                            is ModelHealth.ProviderDown -> stringResource(R.string.status_disabled)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(cs.surfaceContainerHigh)
                                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.model.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    t.providerName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: Int,
    desc: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    SettingsRow(
        icon,
        stringResource(title),
        stringResource(desc),
        onClick = { onChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = cs.tertiary,
                    checkedThumbColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    )
}
