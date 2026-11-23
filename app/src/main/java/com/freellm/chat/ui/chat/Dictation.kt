package com.freellm.chat.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.net.ConnectivityManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freellm.chat.FreeLlmApp
import com.freellm.chat.data.UiLang
import com.freellm.chat.data.dictation.DictationEngine
import com.freellm.chat.data.dictation.DictationManager
import com.freellm.chat.data.dictation.DictationPolicy
import com.freellm.chat.data.dictation.SystemDictation
import com.freellm.chat.data.dictation.VoiceInputMode
import com.freellm.chat.data.dictation.WhisperDictation
import kotlinx.coroutines.launch

@Stable
internal class DictationHandle(
    val listening: () -> Boolean,
    val level: () -> Float,
    val toggle: () -> Unit,
    val cancel: () -> Unit,
    val powerSave: () -> Boolean
)

private fun joinText(base: String, spoken: String): String =
    if (base.isBlank()) spoken else base.trimEnd() + " " + spoken

// Dictated text goes into the input field and is never sent automatically.
@Composable
internal fun rememberDictation(
    getInput: () -> String,
    onText: (String) -> Unit,
    onDenied: () -> Unit,
    onFailed: () -> Unit
): DictationHandle {
    val ctx = LocalContext.current
    val app = remember { ctx.applicationContext as FreeLlmApp }
    val mode by app.settings.voiceInput.collectAsStateWithLifecycle(initialValue = "system")
    val selected by app.settings.whisperModel.collectAsStateWithLifecycle(initialValue = "BALANCED_Q")
    val selectedNow by rememberUpdatedState(selected)
    val system = remember { SystemDictation(ctx.applicationContext) }
    val offline = remember { WhisperDictation { app.whisper.resolve(selectedNow) } }
    var active by remember { mutableStateOf<DictationEngine>(system) }
    var listening by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var base by remember { mutableStateOf("") }
    val text by rememberUpdatedState(onText)
    val denied by rememberUpdatedState(onDenied)
    val failed by rememberUpdatedState(onFailed)
    val input by rememberUpdatedState(getInput)
    val modeNow by rememberUpdatedState(mode)

    fun langTag(): String? {
        val stored = ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("lang", null)
        val ui = UiLang.resolve(stored, UiLang.device())
        return DictationPolicy.languageTag(if (ui == "system") UiLang.device() else ui)
    }

    fun pickEngine(): DictationEngine {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val manager = DictationManager({ system }, if (app.whisper.anyInstalled()) ({ offline }) else null)
        return manager.engine(
            VoiceInputMode.parse(modeNow),
            app.netState.network != null,
            cm?.isActiveNetworkMetered == true
        )
    }

    fun begin() {
        base = input()
        listening = true
        val e = pickEngine()
        active = e
        e.start(langTag())
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else denied()
    }

    LaunchedEffect(system, offline) {
        for (e in listOf<DictationEngine>(system, offline)) {
            launch { e.partial.collect { text(joinText(base, it)) } }
            launch {
                e.final.collect {
                    text(joinText(base, it))
                    listening = false
                    level = 0f
                }
            }
            launch {
                e.error.collect { code ->
                    listening = false
                    level = 0f
                    if (code != SpeechRecognizer.ERROR_NO_MATCH && code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) failed()
                }
            }
            launch { e.level.collect { level = it } }
        }
    }
    DisposableEffect(system, offline) {
        onDispose {
            system.cancel()
            offline.cancel()
        }
    }

    return remember(system, offline) {
        DictationHandle(
            listening = { listening },
            level = { level },
            toggle = {
                if (listening) active.stop()
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                else ask.launch(Manifest.permission.RECORD_AUDIO)
            },
            cancel = {
                system.cancel()
                offline.cancel()
                listening = false
                level = 0f
            },
            powerSave = { (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true }
        )
    }
}
