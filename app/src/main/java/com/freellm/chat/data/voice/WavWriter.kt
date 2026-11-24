package com.freellm.chat.data.voice

object WavWriter {
    fun pcm16Mono(pcm: ByteArray, sampleRate: Int = 24000): ByteArray {
        val out = ByteArray(44 + pcm.size)
        fun str(o: Int, s: String) {
            for (i in s.indices) out[o + i] = s[i].code.toByte()
        }
        fun le32(o: Int, v: Int) {
            out[o] = v.toByte()
            out[o + 1] = (v shr 8).toByte()
            out[o + 2] = (v shr 16).toByte()
            out[o + 3] = (v shr 24).toByte()
        }
        fun le16(o: Int, v: Int) {
            out[o] = v.toByte()
            out[o + 1] = (v shr 8).toByte()
        }
        str(0, "RIFF")
        le32(4, 36 + pcm.size)
        str(8, "WAVE")
        str(12, "fmt ")
        le32(16, 16)
        le16(20, 1)
        le16(22, 1)
        le32(24, sampleRate)
        le32(28, sampleRate * 2)
        le16(32, 2)
        le16(34, 16)
        str(36, "data")
        le32(40, pcm.size)
        System.arraycopy(pcm, 0, out, 44, pcm.size)
        return out
    }
}
