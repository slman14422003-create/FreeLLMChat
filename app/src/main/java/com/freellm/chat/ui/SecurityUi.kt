package com.freellm.chat.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.data.AutoBackup

@Composable
fun SecuritySection(app: FreeLlmApp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val local = app.local
    var lock by remember { mutableStateOf(local.appLock) }
    var abOn by remember { mutableStateOf(local.autoBackupOn) }
    var folder by remember { mutableStateOf(local.autoBackupFolder) }
    var days by remember { mutableIntStateOf(local.autoBackupDays) }
    var pw by remember { mutableStateOf("") }
    val gateTitle = stringResource(R.string.app_locked)
    val needSetup = stringResource(R.string.auto_backup_setup_first)
    val noLock = stringResource(R.string.lock_unavailable)
    val pwSaved = stringResource(R.string.saved)
    val hasPw = remember(pw) { !app.secrets.extra(AutoBackup.PW_KEY).isNullOrEmpty() }
    val swColors = SwitchDefaults.colors(checkedTrackColor = cs.tertiary, checkedThumbColor = Color.White)

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            local.autoBackupFolder = uri.toString()
            folder = uri.toString()
        }
    }

    SectionLabel(stringResource(R.string.security_label))
    SettingsGroup(modifier) {
        SettingsRow(
            Icons.Outlined.VpnKey,
            stringResource(R.string.app_lock),
            stringResource(R.string.app_lock_desc),
            onClick = null,
            trailing = {
                Switch(
                    checked = lock,
                    onCheckedChange = { want ->
                        val act = ctx as? FragmentActivity
                        if (!want) {
                            local.appLock = false
                            lock = false
                        } else if (act == null || !Biometrics.available(act)) {
                            Toast.makeText(ctx, noLock, Toast.LENGTH_LONG).show()
                        } else {
                            Biometrics.gate(act, gateTitle) {
                                local.appLock = true
                                lock = true
                            }
                        }
                    },
                    colors = swColors
                )
            }
        )
        SettingsRow(
            Icons.Outlined.Cloud,
            stringResource(R.string.auto_backup),
            stringResource(if (folder == null) R.string.auto_backup_no_folder else R.string.auto_backup_desc),
            trailing = {
                Switch(
                    checked = abOn,
                    onCheckedChange = { want ->
                        if (want && (folder == null || !hasPw)) {
                            Toast.makeText(ctx, needSetup, Toast.LENGTH_LONG).show()
                        } else {
                            local.autoBackupOn = want
                            abOn = want
                        }
                    },
                    colors = swColors
                )
            }
        )
        SettingsRow(
            Icons.Outlined.Info,
            stringResource(R.string.auto_backup_folder),
            onClick = { pickFolder.launch(null) }
        )
        SettingsRow(
            Icons.Outlined.Info,
            stringResource(if (days == 1) R.string.auto_backup_daily else R.string.auto_backup_weekly),
            onClick = {
                days = if (days == 1) 7 else 1
                local.autoBackupDays = days
            }
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            SecureWindow()
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.auto_backup_password)) },
                visualTransformation = PasswordVisualTransformation()
            )
            TextButton(
                onClick = {
                    app.secrets.setExtra(AutoBackup.PW_KEY, pw)
                    pw = ""
                    Toast.makeText(ctx, pwSaved, Toast.LENGTH_SHORT).show()
                },
                enabled = pw.length >= 6
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
