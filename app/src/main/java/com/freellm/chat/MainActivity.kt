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
        val lang = newBase.getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
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
        locked = app.local.appLock && Biometrics.available(this)
        setContent {
            Box(Modifier.fillMaxSize()) {
                AppRoot(app)
                if (locked) {
                    val title = stringResource(R.string.app_locked)
                    val prompt = { Biometrics.gate(this@MainActivity, title) { locked = false } }
                    LaunchedEffect(Unit) { prompt() }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            Button(onClick = { prompt() }, modifier = Modifier.padding(top = 16.dp)) {
                                Text(stringResource(R.string.unlock))
                            }
                        }
                    }
                }
            }
        }
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
        if (leftAt > 0 && app.local.appLock && Biometrics.available(this) &&
            android.os.SystemClock.elapsedRealtime() - leftAt > LOCK_AFTER_MS
        ) locked = true
        app.appScope.launch { app.autoBackup.runIfDue() }
    }

    private companion object {
        const val LOCK_AFTER_MS = 30_000L
    }
}
