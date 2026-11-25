@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EDGE_VOICES = listOf(
    "ar-SY-AmanyNeural", "ar-SY-LaithNeural", "ar-SA-HamedNeural", "ar-SA-ZariyahNeural", "ar-EG-SalmaNeural",
    "ar-EG-ShakirNeural", "ar-JO-SanaNeural", "ar-JO-TaimNeural", "ar-AE-FatimaNeural", "ar-AE-HamdanNeural"
)
private val GEMINI_VOICES = listOf("Kore", "Charon", "Puck", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr", "Algieba", "Sulafat", "Gacrux", "Schedar")
private val GEMINI_MODELS = listOf("gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts")
private val AZURE_REGIONS = listOf("westeurope", "northeurope", "uaenorth", "eastus", "southeastasia")
private val AZURE_VOICES = listOf("ar-SY-AmanyNeural", "ar-SY-LaithNeural", "ar-SA-HamedNeural", "ar-SA-ZariyahNeural")

@Composable
private fun VoiceChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
        containerColor = com.freellm.chat.ui.surfaceHigh(),
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
private fun VoiceSwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SettingsRow(
        Icons.Outlined.SwapHoriz, title, desc,
        onClick = { onChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = cs.tertiary, checkedThumbColor = Color.White)
            )
        }
    )
}

@Composable
private fun SecretField(label: String, value: String, supporting: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        singleLine = true,
        label = { Text(label) },
        supportingText = if (supporting != null) ({ Text(supporting) }) else null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

@Composable
fun VoiceEngineScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val app = remember { ctx.applicationContext as FreeLlmApp }
    val scope = rememberCoroutineScope()
    val engine by app.settings.voiceEngine.collectAsStateWithLifecycle(initialValue = "system")
    val preload by app.settings.voicePreload.collectAsStateWithLifecycle(initialValue = "auto")
    val fallback by app.settings.voiceFallback.collectAsStateWithLifecycle(initialValue = true)
    val edgeVoice by app.settings.voiceEdgeVoice.collectAsStateWithLifecycle(initialValue = "ar-SY-AmanyNeural")
    val geminiVoice by app.settings.voiceGeminiVoice.collectAsStateWithLifecycle(initialValue = "Kore")
    val geminiModel by app.settings.voiceGeminiModel.collectAsStateWithLifecycle(initialValue = "gemini-3.8-flash-lite-tts")
    val azureRegion by app.settings.voiceAzureRegion.collectAsStateWithLifecycle(initialValue = "westeurope")
    val azureVoice by app.settings.voiceAzureVoice.collectAsStateWithLifecycle(initialValue = "ar-SY-AmanyNeural")
    var geminiKey by remember { mutableStateOf(app.secrets.extra("voice_gemini_key").orEmpty()) }
    var azureKey by remember { mutableStateOf(app.secrets.extra("voice_azure_key").orEmpty()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    val speaker = remember { Speaker(ctx) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    LaunchedEffect(refresh) { cacheBytes = withContext(Dispatchers.IO) { app.voiceCache.size() } }

    val engineOptions = listOf(
        "system" to stringResource(R.string.voice_engine_system),
        "edge" to stringResource(R.string.voice_engine_edge),
        "gemini" to stringResource(R.string.voice_engine_gemini),
        "azure" to stringResource(R.string.voice_engine_azure)
    )
    val preloadOptions = listOf(
        "auto" to stringResource(R.string.vs_preload_auto),
        "3" to "3",
        "6" to "6",
        "10" to "10"
    )
    val testSentence = stringResource(R.string.voice_test_sentence)

    when (dialog) {
        "engine" -> VoiceChoiceDialog(stringResource(R.string.vs_engine), engineOptions, engine, {
            scope.launch { app.settings.setVoiceEngine(it) }
            dialog = null
        }, { dialog = null })
        "edge_voice" -> VoiceChoiceDialog(stringResource(R.string.vs_voice), EDGE_VOICES.map { it to it }, edgeVoice, {
            scope.launch { app.settings.setVoiceEdgeVoice(it) }
            dialog = null
        }, { dialog = null })
        "gemini_voice" -> VoiceChoiceDialog(stringResource(R.string.vs_voice), GEMINI_VOICES.map { it to it }, geminiVoice, {
            scope.launch { app.settings.setVoiceGeminiVoice(it) }
            dialog = null
        }, { dialog = null })
        "gemini_model" -> VoiceChoiceDialog(stringResource(R.string.vs_model), GEMINI_MODELS.map { it to it }, geminiModel, {
            scope.launch { app.settings.setVoiceGeminiModel(it) }
            dialog = null
        }, { dialog = null })
        "azure_region" -> VoiceChoiceDialog(stringResource(R.string.vs_region), AZURE_REGIONS.map { it to it }, azureRegion, {
            scope.launch { app.settings.setVoiceAzureRegion(it) }
            dialog = null
        }, { dialog = null })
        "azure_voice" -> VoiceChoiceDialog(stringResource(R.string.vs_voice), AZURE_VOICES.map { it to it }, azureVoice, {
            scope.launch { app.settings.setVoiceAzureVoice(it) }
            dialog = null
        }, { dialog = null })
        "preload" -> VoiceChoiceDialog(stringResource(R.string.vs_preload), preloadOptions, preload, {
            scope.launch { app.settings.setVoicePreload(it) }
            dialog = null
        }, { dialog = null })
    }

    Scaffold(
        containerColor = cs.background,
        topBar = { AppTopBar(stringResource(R.string.voice_engine_title), onBack) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SectionLabel(stringResource(R.string.vs_section_engine))
            SettingsGroup(Modifier.appear(0)) {
                SettingsRow(
                    Icons.Outlined.RecordVoiceOver,
                    stringResource(R.string.vs_engine),
                    engineOptions.firstOrNull { it.first == engine }?.second,
                    onClick = { dialog = "engine" }
                )
            }

            SectionLabel(stringResource(R.string.vs_section_edge))
            SettingsGroup(Modifier.appear(1)) {
                SettingsRow(
                    Icons.Outlined.Cloud,
                    stringResource(R.string.vs_voice),
                    edgeVoice,
                    onClick = { dialog = "edge_voice" }
                )
                Text(
                    stringResource(R.string.voice_edge_unofficial_note),
                    Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }

            SectionLabel(stringResource(R.string.vs_section_gemini))
            SettingsGroup(Modifier.appear(2)) {
                SecretField(stringResource(R.string.vs_api_key), geminiKey, stringResource(R.string.vs_gemini_note)) {
                    geminiKey = it
                    app.secrets.setExtra("voice_gemini_key", it.trim())
                }
                SettingsRow(Icons.Outlined.Key, stringResource(R.string.vs_voice), geminiVoice, onClick = { dialog = "gemini_voice" })
                SettingsRow(Icons.Outlined.Tune, stringResource(R.string.vs_model), geminiModel, onClick = { dialog = "gemini_model" })
            }

            SectionLabel(stringResource(R.string.vs_section_azure))
            SettingsGroup(Modifier.appear(3)) {
                SecretField(stringResource(R.string.vs_api_key), azureKey, null) {
                    azureKey = it
                    app.secrets.setExtra("voice_azure_key", it.trim())
                }
                SettingsRow(Icons.Outlined.Public, stringResource(R.string.vs_region), azureRegion, onClick = { dialog = "azure_region" })
                SettingsRow(Icons.Outlined.Key, stringResource(R.string.vs_voice), azureVoice, onClick = { dialog = "azure_voice" })
            }

            SectionLabel(stringResource(R.string.vs_section_common))
            SettingsGroup(Modifier.appear(4)) {
                SettingsRow(
                    Icons.Outlined.Tune,
                    stringResource(R.string.vs_preload),
                    preloadOptions.firstOrNull { it.first == preload }?.second,
                    onClick = { dialog = "preload" }
                )
                VoiceSwitchRow(
                    stringResource(R.string.vs_fallback),
                    stringResource(R.string.vs_fallback_desc),
                    fallback
                ) { v -> scope.launch { app.settings.setVoiceFallback(v) } }
            }

            SectionLabel(stringResource(R.string.vs_section_test))
            SettingsGroup(Modifier.appear(5)) {
                SettingsRow(
                    Icons.Outlined.PlayArrow,
                    stringResource(R.string.vs_test_button),
                    null,
                    onClick = { speaker.toggle(testSentence) }
                )
            }

            SectionLabel(stringResource(R.string.voice_cache_title))
            SettingsGroup(Modifier.appear(6)) {
                SettingsRow(
                    Icons.Outlined.Storage,
                    stringResource(R.string.voice_cache_title),
                    stringResource(R.string.vs_cache_size, (cacheBytes shr 20).toInt())
                )
                SettingsRow(
                    Icons.Outlined.DeleteOutline,
                    stringResource(R.string.voice_cache_clear),
                    null,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { app.voiceCache.clear() }
                            refresh++
                        }
                    }
                )
            }
        }
    }
}
