@file:OptIn(ExperimentalMaterial3Api::class)

package com.freellm.chat.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.BuildConfig
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.R
import com.freellm.chat.domain.ModelHealth
import com.freellm.chat.domain.Target
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome_body))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.welcome_privacy),
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start)) }
    }
}

@Composable
fun SettingsScreen(app: FreeLlmApp, onBack: () -> Unit, onProviders: () -> Unit, onStatus: () -> Unit) {
    val theme by app.settings.theme.collectAsStateWithLifecycle(initialValue = "system")
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val proxy by app.settings.proxyUrl.collectAsStateWithLifecycle(initialValue = "")
    var proxyText by remember(proxy) { mutableStateOf(proxy) }
    val curLang = remember {
        ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("lang", "ar") ?: "ar"
    }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.settings), onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.providers)) },
                modifier = Modifier.clickable(onClick = onProviders)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.model_status)) },
                modifier = Modifier.clickable(onClick = onStatus)
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.theme),
                Modifier.padding(top = 8.dp, start = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            listOf(
                "system" to R.string.theme_system,
                "light" to R.string.theme_light,
                "dark" to R.string.theme_dark
            ).forEach { (k, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { app.settings.setTheme(k) } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = theme == k, onClick = { scope.launch { app.settings.setTheme(k) } })
                    Text(stringResource(label))
                }
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.language),
                Modifier.padding(top = 8.dp, start = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            listOf(
                "ar" to R.string.lang_ar,
                "en" to R.string.lang_en,
                "system" to R.string.theme_system
            ).forEach { (k, label) ->
                val pick = {
                    ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).edit().putString("lang", k).apply()
                    (ctx as? Activity)?.recreate()
                    Unit
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = curLang == k, onClick = { pick() })
                    Text(stringResource(label))
                }
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.proxy_url),
                Modifier.padding(top = 8.dp, start = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = proxyText,
                onValueChange = { proxyText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("https://name.workers.dev") }
            )
            Text(
                stringResource(R.string.proxy_hint),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { scope.launch { app.settings.setProxyUrl(proxyText.trim()) } },
                enabled = proxyText.trim() != proxy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) { Text(stringResource(R.string.save)) }
            HorizontalDivider()
            Text(
                stringResource(R.string.about),
                Modifier.padding(top = 8.dp, start = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.about_body) + "\n" + BuildConfig.VERSION_NAME,
                Modifier.padding(start = 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusScreen(app: FreeLlmApp, onBack: () -> Unit) {
    var targets by remember { mutableStateOf<List<Target>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        targets = app.manager.activeTargets()
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.model_status), onBack) }) { pad ->
        if (targets.isEmpty()) {
            Text(stringResource(R.string.no_models), Modifier.padding(pad).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
                items(targets, key = { it.key }) { t ->
                    val st = app.router.health.state(t, now)
                    val label: String
                    val color = when (st) {
                        is ModelHealth.Ready -> MaterialTheme.colorScheme.primary
                        is ModelHealth.Cooling -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                    label = when (st) {
                        is ModelHealth.Ready -> stringResource(R.string.status_ready)
                        is ModelHealth.Cooling ->
                            stringResource(R.string.status_cooling, maxOf(0L, (st.untilMs - now) / 1000).toInt())
                        is ModelHealth.Dead -> stringResource(R.string.status_dead)
                        is ModelHealth.ProviderDown -> stringResource(R.string.status_disabled)
                    }
                    ListItem(
                        headlineContent = { Text(t.model.displayName) },
                        supportingContent = { Text(t.providerName) },
                        trailingContent = { Text(label, color = color) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
