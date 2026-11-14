package com.freellm.chat

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.freellm.chat.ui.Biometrics
import kotlinx.coroutines.launch
import com.freellm.chat.ui.AppRoot
import java.util.Locale

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val lang = com.freellm.chat.data.UiLang.resolve(newBase.getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("lang", null), com.freellm.chat.data.UiLang.device())
        if (lang == "system") {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val cfg = Configuration(newBase.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FreeLlmApp
        locked = app.local.appLock
        applySecure()
        setContent {
            Box(Modifier.fillMaxSize()) {
                if (!locked) {
                    AppRoot(app)
                } else {
                    val title = stringResource(R.string.app_locked)
                    val available = Biometrics.available(this@MainActivity)
                    val prompt = { if (available) Biometrics.gate(this@MainActivity, title) { locked = false } }
                    LaunchedEffect(Unit) { prompt() }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            if (available) {
                                Button(onClick = { prompt() }, modifier = Modifier.padding(top = 16.dp)) {
                                    Text(stringResource(R.string.unlock))
                                }
                            } else {
                                Text(
                                    stringResource(R.string.lock_cannot_unlock),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                Button(
                                    onClick = {
                                        app.local.appLock = false
                                        applySecure()
                                        locked = false
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) { Text(stringResource(R.string.lock_disable)) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applySecure() {
        val app = application as FreeLlmApp
        if (app.local.appLock) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    private var locked by mutableStateOf(false)
    private var leftAt = 0L

    override fun onStop() {
        super.onStop()
        leftAt = android.os.SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val app = application as FreeLlmApp
        applySecure()
        if (leftAt > 0 && app.local.appLock &&
            android.os.SystemClock.elapsedRealtime() - leftAt > LOCK_AFTER_MS
        ) locked = true
        app.appScope.launch { app.autoBackup.runIfDue() }
    }

    private companion object {
        const val LOCK_AFTER_MS = 30_000L
    }
}
