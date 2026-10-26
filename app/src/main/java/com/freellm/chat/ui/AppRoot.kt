package com.freellm.chat.ui

import androidx.activity.compose.BackHandler
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
    val theme by app.settings.theme.collectAsStateWithLifecycle(initialValue = "system")
    val welcome: Boolean? by app.settings.welcomeDone.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    FreeLlmTheme(theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (welcome) {
                null -> Unit
                false -> WelcomeScreen { scope.launch { app.settings.setWelcomeDone() } }
                true -> MainNav(app)
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
            route == "providers" || route == "status" -> "settings"
            else -> "chat"
        }
    }

    when {
        route == "chat" -> ChatScreen(
            vm = chatVm,
            onSettings = { route = "settings" },
            onProviders = { route = "providers" }
        )
        route == "settings" -> SettingsScreen(
            app = app,
            onBack = { route = "chat" },
            onProviders = { route = "providers" },
            onStatus = { route = "status" }
        )
        route == "providers" -> ProvidersScreen(
            app = app,
            onBack = { route = "settings" },
            onOpen = { route = "provider:$it" }
        )
        route == "status" -> StatusScreen(app = app, onBack = { route = "settings" })
        route.startsWith("provider:") -> ProviderWizardScreen(
            app = app,
            providerId = route.removePrefix("provider:"),
            onBack = { route = "providers" }
        )
    }
}
