package com.freellm.chat.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freellm.chat.R
import com.freellm.chat.data.CrashLog

@Composable
fun CrashReportRow() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read()) }
    var open by remember { mutableStateOf(false) }
    val text = report ?: return
    SettingsRow(
        Icons.Outlined.Info,
        stringResource(R.string.crash_report),
        stringResource(R.string.crash_report_desc),
        onClick = { open = true }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.crash_report)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text.take(2500), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.crash_share)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLog.clear()
                    report = null
                    open = false
                }) { Text(stringResource(R.string.crash_clear)) }
            }
        )
    }
}
