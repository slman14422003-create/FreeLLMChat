package com.freellm.chat.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.data.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

private sealed class BackupStep {
    data object None : BackupStep()
    data object ExportPassword : BackupStep()
    data class ImportPassword(val uri: Uri) : BackupStep()
    data class ImportConfirm(val data: JsonObject, val keys: Int, val providers: Int) : BackupStep()
}

@Composable
fun BackupSection(app: FreeLlmApp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<BackupStep>(BackupStep.None) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val doneMsg = stringResource(R.string.backup_done)
    val restoredMsg = stringResource(R.string.restore_done)
    val wrongMsg = stringResource(R.string.backup_wrong_password)
    val badMsg = stringResource(R.string.backup_bad_file)
    val failMsg = stringResource(R.string.backup_failed)
    val gateTitle = stringResource(R.string.backup_auth_title)

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()
    fun reset() {
        step = BackupStep.None
        password = ""
        confirm = ""
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pw = password.toCharArray()
        if (uri == null) {
            reset()
            return@rememberLauncherForActivityResult
        }
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.Default) { app.backup.export(uri, pw) }
                toast(doneMsg)
            } catch (e: Exception) {
                toast(failMsg)
            } finally {
                busy = false
                reset()
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) step = BackupStep.ImportPassword(uri)
    }

    fun gated(action: () -> Unit) {
        val a = ctx as? FragmentActivity
        if (a == null) action() else Biometrics.gate(a, gateTitle, action)
    }

    SectionLabel(stringResource(R.string.backup_label))
    SettingsGroup(modifier) {
        SettingsRow(
            Icons.Outlined.Backup,
            stringResource(R.string.backup_export),
            stringResource(R.string.backup_export_desc),
            onClick = { gated { step = BackupStep.ExportPassword } }
        )
        SettingsRow(
            Icons.Outlined.Restore,
            stringResource(R.string.backup_import),
            stringResource(R.string.backup_import_desc),
            onClick = { gated { openLauncher.launch(arrayOf("*/*")) } }
        )
    }

    when (val st = step) {
        BackupStep.None -> Unit
        BackupStep.ExportPassword -> {
            val valid = password.length >= 6 && password == confirm
            AlertDialog(
                onDismissRequest = { if (!busy) reset() },
                title = { Text(stringResource(R.string.backup_password_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.backup_password_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(R.string.backup_password)) }
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(R.string.backup_password_confirm)) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = valid && !busy, onClick = { createLauncher.launch("freellm-backup.flb") }) {
                        Text(stringResource(R.string.backup_save_file))
                    }
                },
                dismissButton = { TextButton(onClick = { reset() }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        is BackupStep.ImportPassword -> AlertDialog(
            onDismissRequest = { if (!busy) reset() },
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.backup_password)) }
                )
            },
            confirmButton = {
                TextButton(enabled = password.isNotEmpty() && !busy, onClick = {
                    busy = true
                    val pw = password.toCharArray()
                    scope.launch {
                        try {
                            val (obj, pv) = withContext(Dispatchers.Default) {
                                val o = app.backup.read(st.uri, pw)
                                o to app.backup.preview(o)
                            }
                            step = BackupStep.ImportConfirm(obj, pv.keys, pv.providers)
                        } catch (e: BackupManager.WrongPassword) {
                            toast(wrongMsg)
                        } catch (e: BackupManager.BadFile) {
                            toast(badMsg)
                            reset()
                        } catch (e: Exception) {
                            toast(failMsg)
                            reset()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.backup_continue)) }
            },
            dismissButton = { TextButton(onClick = { reset() }) { Text(stringResource(R.string.cancel)) } }
        )
        is BackupStep.ImportConfirm -> AlertDialog(
            onDismissRequest = { reset() },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_summary, st.keys, st.providers)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.Default) { app.backup.apply(st.data, replace = false) }
                            toast(restoredMsg)
                        } catch (e: Exception) {
                            toast(failMsg)
                        }
                        reset()
                    }
                }) { Text(stringResource(R.string.restore_merge)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.Default) { app.backup.apply(st.data, replace = true) }
                                toast(restoredMsg)
                            } catch (e: Exception) {
                                toast(failMsg)
                            }
                            reset()
                        }
                    }) { Text(stringResource(R.string.restore_replace), color = cs.error) }
                    TextButton(onClick = { reset() }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
}
