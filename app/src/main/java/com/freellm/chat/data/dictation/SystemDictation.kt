package com.freellm.chat.data.dictation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class SystemDictation(private val ctx: Context) : DictationEngine {
    private val p = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val f = MutableSharedFlow<String>(extraBufferCapacity = 2)
    private val e = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    private val l = MutableSharedFlow<Float>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val partial: SharedFlow<String> = p
    override val final: SharedFlow<String> = f
    override val error: SharedFlow<Int> = e
    override val level: SharedFlow<Float> = l
    private var rec: SpeechRecognizer? = null

    override fun start(languageTag: String?) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { e.tryEmit(SpeechRecognizer.ERROR_CLIENT); return }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) { text(b)?.let { f.tryEmit(it) } }
            override fun onPartialResults(b: Bundle?) { text(b)?.let { p.tryEmit(it) } }
            override fun onError(code: Int) { e.tryEmit(code) }
            override fun onRmsChanged(db: Float) { l.tryEmit(((db + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onReadyForSpeech(b: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (languageTag != null) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        rec = r
        r.startListening(i)
    }

    override fun stop() { rec?.stopListening() }

    override fun cancel() {
        rec?.cancel()
        rec?.destroy()
        rec = null
    }

    private fun text(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
