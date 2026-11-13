package com.freellm.chat.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Blocks screenshots and the recents preview while this composable is in the composition. */
@Composable
fun SecureWindow() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = ctx.findActivity()?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Clears the clipboard only if it still holds the given secret. */
fun clearClipboardIfMatches(ctx: Context, secret: String) {
    if (secret.isBlank()) return
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        if (text.contains(secret.trim())) {
            if (android.os.Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
