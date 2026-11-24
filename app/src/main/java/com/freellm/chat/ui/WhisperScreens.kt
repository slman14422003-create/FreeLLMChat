@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.data.dictation.DownloadResult
import com.freellm.chat.data.dictation.ModelLevel
import com.freellm.chat.data.dictation.ModelStore
import com.freellm.chat.data.dictation.WhisperNative
import kotlinx.coroutines.launch

private fun levelTitle(l: ModelLevel): Int = when (l) {
    ModelLevel.FAST -> R.string.whisper_m_fast
    ModelLevel.BALANCED -> R.string.whisper_m_balanced
    ModelLevel.ACCURATE -> R.string.whisper_m_accurate
    ModelLevel.MOST_ACCURATE -> R.string.whisper_m_most
    ModelLevel.FAST_Q -> R.string.whisper_m_fast_q
    ModelLevel.BALANCED_Q -> R.string.whisper_m_balanced_q
    ModelLevel.ACCURATE_Q -> R.string.whisper_m_accurate_q
    ModelLevel.MOST_ACCURATE_Q -> R.string.whisper_m_most_q
    ModelLevel.TURBO_Q -> R.string.whisper_m_turbo_q
}

private fun mb(bytes: Long): String = (bytes shr 20).toString() + " MB"

@Composable
fun WhisperSettingsScreen(app: FreeLlmApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val selected by app.settings.whisperModel.collectAsStateWithLifecycle(initialValue = ModelStore.DEFAULT)
    val progress by app.whisper.progress.collectAsStateWithLifecycle()
    val result by app.whisper.result.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var importOk by remember { mutableStateOf<Boolean?>(null) }
    val nativeOk = remember { WhisperNative.available() }
    val ramMb = remember {
        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        (mi.totalMem shr 20).toInt()
    }

    val ramWarnText = stringResource(R.string.whisper_ram_warn)
    val msgSpace = stringResource(R.string.whisper_err_space)
    val msgFailed = stringResource(R.string.whisper_err_failed)
    val msgSum = stringResource(R.string.whisper_err_checksum)
    val msgDone = stringResource(R.string.whisper_done)
    val msgImportBad = stringResource(R.string.whisper_import_bad)
    val msgImportOk = stringResource(R.string.whisper_import_ok)

    LaunchedEffect(progress) { refresh++ }
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        app.whisper.result.value = null
        refresh++
        snack.showSnackbar(
            when (r) {
                DownloadResult.OK -> msgDone
                DownloadResult.NO_SPACE -> msgSpace
                DownloadResult.FAILED -> msgFailed
                DownloadResult.BAD_CHECKSUM -> msgSum
            }
        )
    }
    LaunchedEffect(importOk) {
        val v = importOk ?: return@LaunchedEffect
        importOk = null
        refresh++
        snack.showSnackbar(if (v) msgImportOk else msgImportBad)
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = app.whisper.importCustom(uri)
            if (ok) app.settings.setWhisperModel(ModelStore.CUSTOM)
            importOk = ok
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = { AppTopBar(stringResource(R.string.whisper_title), onBack) },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(stringResource(R.string.whisper_intro), color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.sm))
            if (!nativeOk) {
                Text(stringResource(R.string.whisper_native_missing), color = cs.error, modifier = Modifier.padding(horizontal = Spacing.sm))
            }

            SectionLabel(stringResource(R.string.whisper_models))
            SettingsGroup(Modifier.appear(0)) {
                val installedKey = refresh
                for (l in ModelLevel.values()) {
                    val have = remember(installedKey, progress) { app.whisper.installed(l) }
                    val running = progress?.level == l
                    val busy = progress != null && !running
                    val warn = ModelStore.needsRamWarning(l, ramMb)
                    val partial = remember(installedKey, progress) { app.whisper.partialBytes(l) }
                    val sub = buildString {
                        append(mb(l.approxBytes))
                        if (warn) append(" · ").append(ramWarnText)
                        if (running) {
                            val p = progress
                            if (p != null && p.total > 0) append(" · ").append(((p.done * 100) / p.total).toInt()).append("%")
                        } else if (!have && partial > 0) append(" · ").append(mb(partial))
                        append("\n").append(ModelStore.url(l))
                    }
                    SettingsRow(
                        Icons.Outlined.Mic,
                        stringResource(levelTitle(l)),
                        sub,
                        onClick = if (have) ({ scope.launch { app.settings.setWhisperModel(l.name) } }) else null,
                        trailing = {
                            Row {
                                IconButton(onClick = {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(ModelStore.pageUrl(l))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }) { Icon(Icons.Outlined.Link, stringResource(R.string.whisper_open_link)) }
                                when {
                                    running -> IconButton(onClick = { app.whisper.cancelDownload() }) {
                                        Icon(Icons.Outlined.Close, stringResource(R.string.whisper_cancel))
                                    }
                                    have -> {
                                        RadioButton(
                                            selected = selected == l.name,
                                            onClick = { scope.launch { app.settings.setWhisperModel(l.name) } }
                                        )
                                        IconButton(onClick = {
                                            app.whisper.delete(l)
                                            refresh++
                                        }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.whisper_delete)) }
                                    }
                                    else -> IconButton(
                                        onClick = { app.whisper.startDownload(app.appScope, l) },
                                        enabled = !busy
                                    ) { Icon(Icons.Outlined.Download, stringResource(R.string.whisper_download)) }
                                }
                            }
                        }
                    )
                }
            }

            SectionLabel(stringResource(R.string.whisper_custom))
            SettingsGroup(Modifier.appear(1)) {
                val hasCustom = remember(refresh) { app.whisper.hasCustom() }
                SettingsRow(
                    Icons.Outlined.Folder,
                    stringResource(R.string.whisper_import),
                    stringResource(R.string.whisper_import_desc),
                    onClick = { pick.launch(arrayOf("*/*")) },
                    trailing = {
                        if (hasCustom) {
                            Row {
                                RadioButton(
                                    selected = selected == ModelStore.CUSTOM,
                                    onClick = { scope.launch { app.settings.setWhisperModel(ModelStore.CUSTOM) } }
                                )
                                IconButton(onClick = {
                                    app.whisper.deleteCustom()
                                    refresh++
                                }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.whisper_delete)) }
                            }
                        }
                    }
                )
            }
        }
    }
}
