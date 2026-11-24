package com.freellm.chat.data.dictation

import kotlinx.coroutines.flow.Flow

interface DictationEngine {
    val partial: Flow<String>
    val final: Flow<String>
    val error: Flow<Int>
    val level: Flow<Float>
    fun start(languageTag: String?)
    fun stop()
    fun cancel()
}
