package com.freellm.chat.data.dictation

object WhisperNative {
    @Volatile private var checked = false
    @Volatile private var ok = false

    fun available(): Boolean {
        if (!checked) synchronized(this) {
            if (!checked) {
                ok = runCatching { System.loadLibrary("freellm_whisper") }.isSuccess
                checked = true
            }
        }
        return ok
    }

    external fun initContext(path: String): Long
    external fun freeContext(handle: Long)
    external fun transcribe(handle: Long, samples: FloatArray, lang: String, threads: Int): ByteArray
}
