package com.freellm.chat.data.voice

class TtsException(val code: Int, message: String) : Exception(message)

interface CloudTts {
    val id: String
    val ext: String
    val maxChars: Int
    val altExts: List<String> get() = emptyList()
    fun voiceFor(text: String): String
    suspend fun synth(text: String): ByteArray
    suspend fun synthExt(text: String): Pair<ByteArray, String> = synth(text) to ext
}
