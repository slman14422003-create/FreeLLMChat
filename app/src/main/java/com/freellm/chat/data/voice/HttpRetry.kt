package com.freellm.chat.data.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object HttpRetry {
    suspend fun post(
        client: OkHttpClient,
        request: Request,
        delaysMs: List<Long> = listOf(10_000L, 20_000L, 30_000L)
    ): ByteArray {
        var attempt = 0
        while (true) {
            val result = withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { r ->
                        val body = r.body?.bytes() ?: ByteArray(0)
                        Triple(r.code, r.isSuccessful, body)
                    }
                } catch (e: IOException) {
                    throw TtsException(0, "0 " + (e.message ?: "").take(220))
                }
            }
            val (code, ok, body) = result
            if (ok) return body
            if ((code == 429 || code == 503) && attempt < delaysMs.size) {
                delay(delaysMs[attempt])
                attempt++
                continue
            }
            throw TtsException(code, code.toString() + " " + String(body, Charsets.UTF_8).take(220))
        }
    }
}
