package com.freellm.chat.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.freellm.chat.domain.ErrorKind
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.SearchKind
import com.freellm.chat.domain.SearchProvider
import com.freellm.chat.domain.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import kotlin.coroutines.resume

class EngineHealth(private val clock: () -> Long = { System.currentTimeMillis() }, private val cooldownMs: Long = 600_000L) {
    private val until = HashMap<Int, Long>()

    @Synchronized
    fun block(i: Int) {
        until[i] = clock() + cooldownMs
    }

    @Synchronized
    fun ok(i: Int) {
        until.remove(i)
    }

    @Synchronized
    fun order(n: Int): List<Int> {
        val now = clock()
        val (hot, cold) = (0 until n).partition { (until[it] ?: 0L) <= now }
        return hot + cold
    }
}

class WebViewSearch(private val context: Context, private val health: EngineHealth = EngineHealth()) : SearchProvider {
    override val kind = SearchKind.LOCAL
    private val lock = Mutex()

    override suspend fun search(query: String, count: Int): List<SearchResult> = lock.withLock {
        val raw = try {
            withTimeoutOrNull(TOTAL_MS) { load(query) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw LlmException(ErrorKind.NETWORK, message = e.javaClass.simpleName)
        } ?: throw LlmException(ErrorKind.TIMEOUT, message = "local search failed")
        LocalSearchParse.parse(raw, count)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(query: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            val wv = WebView(context.applicationContext)
            var pos = 0
            var done = false
            val q = URLEncoder.encode(query, "UTF-8")
            val urls = LocalSearchParse.engines(q)
            val order = health.order(urls.size)

            fun finish(value: String?) {
                if (done) return
                done = true
                main.post {
                    runCatching {
                        wv.stopLoading()
                        wipe(wv)
                        wv.destroy()
                    }
                }
                if (cont.isActive) cont.resume(value)
            }

            fun next() {
                health.block(order[pos])
                pos++
                if (pos >= order.size) finish(null) else wv.loadUrl(urls[order[pos]])
            }

            wipe(wv)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                blockNetworkImage = true
                loadsImagesAutomatically = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                allowFileAccess = false
                allowContentAccess = false
                userAgentString = LocalSearchParse.USER_AGENT
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit

                override fun onPageFinished(view: WebView, url: String?) {
                    if (done) return
                    if (LocalSearchParse.isBlockedPage(url)) {
                        next()
                        return
                    }
                    val at = pos
                    main.postDelayed({
                        if (done || at != pos) return@postDelayed
                        view.evaluateJavascript(LocalSearchParse.EXTRACT_JS) { result ->
                            if (done || at != pos) return@evaluateJavascript
                            val json = LocalSearchParse.fromEvaluate(result)
                            if (json != null && LocalSearchParse.parse(json, 1).isNotEmpty()) {
                                health.ok(order[pos])
                                finish(json)
                            } else {
                                next()
                            }
                        }
                    }, SETTLE_MS)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !done) next()
                }
            }
            cont.invokeOnCancellation { finish(null) }
            wv.loadUrl(urls[order[0]])
        }
    }

    private fun wipe(wv: WebView) {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        wv.clearCache(true)
        wv.clearFormData()
        wv.clearHistory()
    }

    private companion object {
        const val TOTAL_MS = 18_000L
        const val SETTLE_MS = 500L
    }
}
