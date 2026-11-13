package com.freellm.chat.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive

class JsRunner(private val context: Context) {
    private class Bridge(private val sink: CompletableDeferred<String>) {
        @JavascriptInterface
        fun done(s: String?) {
            sink.complete(s.orEmpty())
        }
    }

    suspend fun run(code: String, timeoutMs: Long = 8000): String = execute(code, false, timeoutMs)

    suspend fun syntax(code: String): String? {
        val r = execute(code, true, 4000)
        return if (r == "OK") null else r
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun execute(code: String, checkOnly: Boolean, timeoutMs: Long): String {
        val done = CompletableDeferred<String>()
        var view: WebView? = null
        try {
            withContext(Dispatchers.Main) {
                val wv = WebView(context.applicationContext)
                view = wv
                wv.settings.javaScriptEnabled = true
                wv.settings.blockNetworkLoads = true
                wv.settings.allowFileAccess = false
                wv.settings.allowContentAccess = false
                wv.addJavascriptInterface(Bridge(done), "FreeLlmBridge")
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView?, url: String?) {
                        v?.evaluateJavascript("window.__run(" + JsonPrimitive(code).toString() + "," + checkOnly + ")", null)
                    }
                }
                wv.loadDataWithBaseURL(null, PAGE, "text/html", "utf-8", null)
            }
            val out = withTimeoutOrNull(timeoutMs) { done.await() }
            return out ?: "Timed out after " + timeoutMs / 1000 + " seconds (possible infinite loop)."
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                view?.let {
                    runCatching { it.stopLoading() }
                    runCatching { it.removeJavascriptInterface("FreeLlmBridge") }
                    runCatching { it.destroy() }
                }
            }
        }
    }

    private companion object {
        const val PAGE = """<html><body><script>
window.__run = function (code, check) {
  var out = [];
  function fmt(v) {
    if (typeof v === 'string') return v;
    try {
      var s = JSON.stringify(v, null, 1);
      return s === undefined ? String(v) : s;
    } catch (e) {
      return String(v);
    }
  }
  var cons = {};
  cons.log = function () { out.push(Array.prototype.map.call(arguments, fmt).join(' ')); };
  cons.info = cons.log; cons.warn = cons.log; cons.error = cons.log; cons.debug = cons.log;
  window.console = cons;
  var AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
  var fn;
  try {
    fn = new AsyncFunction(code);
  } catch (e) {
    FreeLlmBridge.done('SyntaxError: ' + e.message);
    return;
  }
  if (check) {
    FreeLlmBridge.done('OK');
    return;
  }
  fn().then(function (r) {
    if (r !== undefined) out.push(fmt(r));
    FreeLlmBridge.done(out.join('\n'));
  }, function (e) {
    out.push('Error: ' + (e && e.message ? e.message : e));
    FreeLlmBridge.done(out.join('\n'));
  });
};
</script></body></html>"""
    }
}
