package com.freellm.chat.ui.live

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.ui.ChatViewModel
import com.freellm.chat.ui.CircleButton
import com.freellm.chat.ui.LocalReduceMotion
import com.freellm.chat.ui.Motion
import com.freellm.chat.ui.Spacing
import com.freellm.chat.ui.Speaker
import com.freellm.chat.ui.StatusColors
import com.freellm.chat.ui.surfaceHigh

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun LiveScreen(
    app: FreeLlmApp,
    vm: ChatViewModel,
    onEnd: () -> Unit,
    onVoiceEngine: () -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val speaker = remember { Speaker(ctx) }
    val endNow by rememberUpdatedState(onEnd)
    var denied by remember { mutableStateOf(false) }
    val session = remember {
        LiveSession(
            app = app,
            scope = scope,
            speaker = speaker,
            stream = vm.stream,
            send = { vm.send(it) },
            stopGeneration = { vm.stop() },
            dismissError = { vm.dismissError() },
            onFailed = { endNow() }
        )
    }
    val state by session.state.collectAsStateWithLifecycle()
    val level by session.micLevel.collectAsStateWithLifecycle()
    val caption by session.caption.collectAsStateWithLifecycle()
    val lines by session.recentLines.collectAsStateWithLifecycle()
    val muted by session.muted.collectAsStateWithLifecycle()

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) session.start() else denied = true
    }
    LaunchedEffect(Unit) {
        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (has) session.start() else ask.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            session.close()
            speaker.shutdown()
        }
    }

    val label = when {
        denied -> stringResource(R.string.live_permission_needed)
        state == LiveState.Thinking -> stringResource(R.string.live_thinking)
        state == LiveState.Speaking -> stringResource(R.string.live_speaking)
        else -> stringResource(R.string.live_listening)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = Spacing.xxl, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.live_title),
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Orb(state = state, level = if (muted) 0f else level)
            Spacer(Modifier.height(Spacing.xxl))
            val previous = if (state == LiveState.Speaking) lines.dropLast(1).takeLast(2) else emptyList()
            previous.forEachIndexed { i, line ->
                val a = if (i == previous.lastIndex) 0.4f else 0.25f
                LineText(line, a, MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.sm))
            }
            LineText(caption, 1f, MaterialTheme.typography.titleMedium)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleButton(
                onClick = { session.toggleMute() },
                size = 64.dp,
                background = if (muted) cs.primary else surfaceHigh()
            ) {
                Icon(
                    if (muted) Icons.Outlined.MicOff else Icons.Filled.Mic,
                    stringResource(if (muted) R.string.live_unmute else R.string.live_mute),
                    Modifier.size(28.dp),
                    tint = if (muted) cs.onPrimary else cs.onSurface
                )
            }
            CircleButton(
                onClick = { endNow() },
                size = 64.dp,
                background = StatusColors.Dead
            ) {
                Icon(Icons.Filled.CallEnd, stringResource(R.string.live_end), Modifier.size(28.dp), tint = Color.White)
            }
            CircleButton(
                onClick = onVoiceEngine,
                size = 64.dp,
                background = surfaceHigh()
            ) {
                Icon(
                    Icons.Outlined.RecordVoiceOver,
                    stringResource(R.string.voice_engine_title),
                    Modifier.size(28.dp),
                    tint = cs.onSurface
                )
            }
        }
    }
}

@Composable
private fun LineText(text: String, alpha: Float, style: androidx.compose.ui.text.TextStyle) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (reduce) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(androidx.compose.animation.core.tween(Motion.medium)) +
                    slideInVertically(androidx.compose.animation.core.tween(Motion.medium)) { it / 2 }) togetherWith
                    (fadeOut(androidx.compose.animation.core.tween(Motion.short)) +
                        slideOutVertically(androidx.compose.animation.core.tween(Motion.medium)) { -it / 2 })
            }
        },
        label = "liveLine"
    ) { t ->
        Text(
            t,
            Modifier.fillMaxWidth(),
            style = style,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            maxLines = 4
        )
    }
}
