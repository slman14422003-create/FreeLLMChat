package com.freellm.chat.ui

import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.freellm.chat.R
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine

private class Heard(val text: String?, val fatal: Boolean)

private suspend fun listen(ctx: android.content.Context): Heard =
    suspendCancellableCoroutine { c ->
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            c.resume(Heard(null, true))
            return@suspendCancellableCoroutine
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
        fun done(h: Heard) {
            if (c.isActive) c.resume(h)
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                val fatal = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                done(Heard(null, fatal && !quiet))
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                done(Heard(t, false))
            }
        })
        c.invokeOnCancellation {
            runCatching { rec.cancel() }
            runCatching { rec.destroy() }
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        rec.startListening(i)
    }

@Composable
fun VoiceChat(vm: ChatViewModel, speaker: Speaker, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var phase by remember { mutableIntStateOf(0) }
    var heard by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        var misses = 0
        while (isActive) {
            phase = 0
            val h = listen(ctx)
            if (h.fatal) {
                onClose()
                return@LaunchedEffect
            }
            val said = h.text?.trim().orEmpty()
            if (said.isEmpty()) {
                misses++
                if (misses >= 6) {
                    onClose()
                    return@LaunchedEffect
                }
                delay(300)
                continue
            }
            misses = 0
            heard = said
            phase = 1
            vm.send(said)
            delay(600)
            while (vm.generating) delay(250)
            val reply = vm.messages.value.lastOrNull { it.role == "assistant" }?.content.orEmpty()
            phase = 2
            speaker.say(reply)
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.92f,
        targetValue = if (phase == 1) 1.0f else 1.12f,
        animationSpec = infiniteRepeatable(tween(if (phase == 2) 500 else 900), RepeatMode.Reverse),
        label = "scale"
    )
    val cs = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(cs.background)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.voice_end))
            }
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Box(
                    Modifier
                        .size(160.dp)
                        .scale(pulse.value)
                        .clip(CircleShape)
                        .background(if (phase == 2) cs.tertiary else cs.primary)
                        .clickable {
                            if (phase == 2) speaker.stop()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Mic, null, Modifier.size(56.dp), tint = cs.onPrimary)
                }
                Text(
                    stringResource(
                        when (phase) {
                            0 -> R.string.voice_listening
                            1 -> R.string.voice_thinking
                            else -> R.string.voice_speaking
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                if (heard.isNotEmpty()) {
                    Text(
                        heard,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 4
                    )
                }
                if (phase == 2) {
                    Text(
                        stringResource(R.string.voice_tap_skip),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}
