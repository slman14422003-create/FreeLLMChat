package com.freellm.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.ui.live.LiveScreen
import kotlinx.coroutines.launch

@Composable
fun AppRoot(app: FreeLlmApp) {
    val theme by app.settings.theme.collectAsStateWithLifecycle(initialValue = "dark")
    val welcome: Boolean? by app.settings.welcomeDone.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var splash by rememberSaveable { mutableStateOf(true) }
    var splashExiting by rememberSaveable { mutableStateOf(false) }
    val saverOn by app.saver.active.collectAsStateWithLifecycle()
    val userReduce by app.settings.reduceMotion.collectAsStateWithLifecycle(initialValue = false)
    val textScale by app.settings.textScale.collectAsStateWithLifecycle(initialValue = 1f)
    val dynamicColor by app.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val systemOff = androidx.compose.runtime.remember { runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
    androidx.compose.runtime.CompositionLocalProvider(LocalReduceMotion provides (userReduce || com.freellm.chat.domain.SaverPolicy.reduceMotion(saverOn, systemOff)),
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * textScale)
    ) {
    FreeLlmTheme(theme, dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                Crossfade(targetState = welcome, animationSpec = tween(Motion.Slow), label = "welcome") { w ->
                    when (w) {
                        null -> Unit
                        false -> OnboardingScreen(ready = !splash || splashExiting) { scope.launch { app.settings.setWelcomeDone() } }
                        true -> MainNav(app)
                    }
                }
                if (splash) SplashOverlay(onExit = { splashExiting = true }) { splash = false }
            }
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainNav(app: FreeLlmApp) {
    var route by rememberSaveable { mutableStateOf("chat") }
    var privacyFrom by rememberSaveable { mutableStateOf("settings") }
    var voiceFrom by rememberSaveable { mutableStateOf("settings") }
    val chatVm: ChatViewModel = viewModel(factory = VmFactory { ChatViewModel(app) })
    var showReset by rememberSaveable { mutableStateOf(app.secrets.resetPending()) }
    if (showReset) {
        androidx.compose.material3.AlertDialog(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.freellm.chat.ui.Corners.xl),
            containerColor = com.freellm.chat.ui.surfaceHigh(),
            onDismissRequest = {},
            title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.freellm.chat.R.string.secret_reset_title)) },
            text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.freellm.chat.R.string.secret_reset_body)) },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    app.secrets.ackReset()
                    showReset = false
                    route = "settings"
                }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.freellm.chat.R.string.secret_reset_restore)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    app.secrets.ackReset()
                    showReset = false
                }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.freellm.chat.R.string.ok)) }
            }
        )
    }

    BackHandler(enabled = route != "chat") {
        route = when {
            route.startsWith("provider:") -> "providers"
            route == "privacy" -> privacyFrom
            route == "tour" -> "about"
            route == "about" -> "settings"
            route == "voice_engine" -> voiceFrom
            route == "providers" || route == "status" || route == "usage" || route == "brain" || route == "search" || route == "tools" || route == "diag" || route == "health" || route == "whisper" -> "settings"
            else -> "chat"
        }
    }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sign = if (rtl) -1 else 1
    val reduce = LocalReduceMotion.current
    val barVisible = route == "providers" || route == "settings"
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = route,
        modifier = Modifier.weight(1f).fillMaxWidth().then(
            if (barVisible) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier
        ),
        transitionSpec = {
            if (reduce) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward = depthOf(targetState) > depthOf(initialState)
                val dir = if (forward) sign else -sign
                (slideInHorizontally(tween(Motion.medium, easing = FastOutSlowInEasing)) { w -> dir * (w / 4) } +
                    fadeIn(tween(Motion.medium))) togetherWith
                    (slideOutHorizontally(tween(Motion.medium, easing = FastOutSlowInEasing)) { w -> -dir * (w / 8) } +
                        fadeOut(tween(Motion.short)) + scaleOut(tween(Motion.medium), targetScale = 0.97f))
            }
        },
        label = "nav"
    ) { r ->
        when {
            r == "chat" -> ChatScreen(
                vm = chatVm,
                onSettings = { route = "settings" },
                onProviders = { route = "providers" },
                onLive = { route = "live" }
            )
            r == "live" -> LiveScreen(
                app = app,
                vm = chatVm,
                onEnd = { route = "chat" },
                onVoiceEngine = {
                    voiceFrom = "live"
                    route = "voice_engine"
                }
            )
            r == "settings" -> SettingsScreen(
                app = app,
                onBack = { route = "chat" },
                onProviders = { route = "providers" },
                onStatus = { route = "status" },
                onUsage = { route = "usage" },
                onBrain = { route = "brain" },
                onSearch = { route = "search" },
                onTools = { route = "tools" },
                onDiag = { route = "diag" },
                onHealth = { route = "health" },
                onWhisper = { route = "whisper" },
                onVoiceEngine = {
                    voiceFrom = "settings"
                    route = "voice_engine"
                },
                onWipeChats = { chatVm.deleteAllChats() },
                onAbout = { route = "about" },
                onPrivacy = {
                    privacyFrom = "settings"
                    route = "privacy"
                }
            )
            r == "about" -> AboutScreen(
                onBack = { route = "settings" },
                onTour = { route = "tour" },
                onPrivacy = {
                    privacyFrom = "about"
                    route = "privacy"
                }
            )
            r == "tour" -> OnboardingScreen { route = "about" }
            r == "privacy" -> PrivacyScreen(onBack = { route = privacyFrom })
            r == "providers" -> ProvidersScreen(
                app = app,
                onBack = { route = "settings" },
                onOpen = { route = "provider:$it" }
            )
            r == "status" -> StatusScreen(app = app, onBack = { route = "settings" })
            r == "usage" -> UsageScreen(app = app, onBack = { route = "settings" })
            r == "brain" -> BrainScreen(app = app, onBack = { route = "settings" })
            r == "search" -> SearchSettingsScreen(app = app, onBack = { route = "settings" })
            r == "tools" -> ToolsSettingsScreen(app = app, onBack = { route = "settings" })
            r == "diag" -> DiagnosticsScreen(app = app, onBack = { route = "settings" })
            r == "health" -> ModelHealthScreen(app = app, onBack = { route = "settings" })
            r == "whisper" -> WhisperSettingsScreen(app = app, onBack = { route = "settings" })
            r == "voice_engine" -> VoiceEngineScreen(onBack = { route = voiceFrom })
            r.startsWith("provider:") -> ProviderWizardScreen(
                app = app,
                providerId = r.removePrefix("provider:"),
                onBack = { route = "providers" }
            )
            else -> Unit
        }
    }
    androidx.compose.animation.AnimatedVisibility(visible = barVisible) {
        BottomBar(route) { route = it }
    }
    }
}

private fun depthOf(r: String): Int = when {
    r == "chat" -> 0
    r == "settings" || r == "live" -> 1
    r == "providers" || r == "status" || r == "usage" || r == "brain" || r == "about" || r == "search" || r == "tools" || r == "diag" || r == "health" || r == "whisper" || r == "voice_engine" -> 2
    else -> 3
}
