package com.freellm.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import kotlinx.coroutines.launch

@Composable
fun AppRoot(app: FreeLlmApp) {
    val theme by app.settings.theme.collectAsStateWithLifecycle(initialValue = "dark")
    val welcome: Boolean? by app.settings.welcomeDone.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var splash by rememberSaveable { mutableStateOf(true) }
    FreeLlmTheme(theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (welcome) {
                    null -> Unit
                    false -> WelcomeScreen { scope.launch { app.settings.setWelcomeDone() } }
                    true -> MainNav(app)
                }
                if (splash) SplashOverlay { splash = false }
            }
        }
    }
}

@Composable
private fun MainNav(app: FreeLlmApp) {
    var route by rememberSaveable { mutableStateOf("chat") }
    val chatVm: ChatViewModel = viewModel(factory = VmFactory { ChatViewModel(app) })

    BackHandler(enabled = route != "chat") {
        route = when {
            route.startsWith("provider:") -> "providers"
            route == "providers" || route == "status" || route == "usage" || route == "brain" -> "settings"
            else -> "chat"
        }
    }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sign = if (rtl) -1 else 1
    AnimatedContent(
        targetState = route,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val forward = depthOf(targetState) > depthOf(initialState)
            val dir = if (forward) sign else -sign
            (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { w -> dir * (w / 3) } +
                fadeIn(tween(300)) + scaleIn(tween(340, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
                (slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { w -> -dir * (w / 5) } +
                    fadeOut(tween(200)))
        },
        label = "nav"
    ) { r ->
        when {
            r == "chat" -> ChatScreen(
                vm = chatVm,
                onSettings = { route = "settings" },
                onProviders = { route = "providers" }
            )
            r == "settings" -> SettingsScreen(
                app = app,
                onBack = { route = "chat" },
                onProviders = { route = "providers" },
                onStatus = { route = "status" },
                onUsage = { route = "usage" },
                onBrain = { route = "brain" }
            )
            r == "providers" -> ProvidersScreen(
                app = app,
                onBack = { route = "settings" },
                onOpen = { route = "provider:$it" }
            )
            r == "status" -> StatusScreen(app = app, onBack = { route = "settings" })
            r == "usage" -> UsageScreen(app = app, onBack = { route = "settings" })
            r == "brain" -> BrainScreen(app = app, onBack = { route = "settings" })
            r.startsWith("provider:") -> ProviderWizardScreen(
                app = app,
                providerId = r.removePrefix("provider:"),
                onBack = { route = "providers" }
            )
            else -> Unit
        }
    }
}

private fun depthOf(r: String): Int = when {
    r == "chat" -> 0
    r == "settings" -> 1
    r == "providers" || r == "status" || r == "usage" || r == "brain" -> 2
    else -> 3
}
