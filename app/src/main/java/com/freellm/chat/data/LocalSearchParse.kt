package com.freellm.chat.data

import com.freellm.chat.domain.SearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLDecoder
import java.util.Base64

object LocalSearchParse {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    const val EXTRACT_JS =
        "(function(){" +
        "var out=[];" +
        "function add(t,u,s){if(u)out.push({title:(t||'').trim(),url:u,snippet:(s||'').trim()});}" +
        "function txt(n){return n?n.textContent:'';}" +
        "document.querySelectorAll('a h3').forEach(function(h){" +
        "var a=h.closest('a');if(!a)return;" +
        "var c=a.parentElement;var s=null;" +
        "for(var i=0;i<4&&c&&!s;i++){s=c.querySelector('.VwiC3b, [data-sncf], .lEBKkf');c=c.parentElement;}" +
        "add(h.textContent,a.href,txt(s));});" +
        "if(out.length==0){document.querySelectorAll('a.result__a').forEach(function(a){" +
        "var r=a.closest('.result');add(a.textContent,a.href,txt(r?r.querySelector('.result__snippet'):null));});}" +
        "if(out.length==0){document.querySelectorAll('li.b_algo').forEach(function(li){" +
        "var h=li.querySelector('h2 a');if(h)add(h.textContent,h.href,txt(li.querySelector('.b_caption p, p')));});}" +
        "return JSON.stringify(out);" +
        "})()"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val blockedHosts = listOf("duckduckgo.com", "bing.com", "microsoft.com", "google.com", "gstatic.com", "googleusercontent.com")

    val NAMES = listOf("google", "ddg", "bing")

    fun engines(encodedQuery: String): List<String> = listOf(
        "https://www.google.com/search?q=$encodedQuery&udm=14&pws=0&nfpr=1&hl=en",
        "https://html.duckduckgo.com/html/?q=$encodedQuery",
        "https://www.bing.com/search?q=$encodedQuery"
    )

    fun isBlockedPage(url: String?): Boolean {
        val u = url.orEmpty().lowercase()
        val h = host(u)
        return (h.endsWith("google.com") && (u.contains("/sorry/") || h.startsWith("consent."))) ||
            u.contains("/captcha") || u.contains("anomaly")
    }

    fun fromEvaluate(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            (json.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull
        } catch (e: Exception) {
            null
        }
    }

    private fun queryParam(url: String, name: String): String? {
        val q = url.substringAfter('?', "").substringBefore('#')
        for (part in q.split('&')) {
            val i = part.indexOf('=')
            if (i > 0 && part.substring(0, i) == name) {
                return try {
                    URLDecoder.decode(part.substring(i + 1), "UTF-8")
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    fun host(url: String): String = url.substringAfter("://", "").substringBefore('/').substringBefore('?').lowercase()

    fun unwrap(url: String): String {
        val u = url.trim()
        val h = host(u)
        if (h.endsWith("google.com") && u.substringAfter("://").substringAfter('/').startsWith("url")) {
            (queryParam(u, "q") ?: queryParam(u, "url"))?.let { return it }
        }
        if (h.endsWith("duckduckgo.com")) {
            queryParam(u, "uddg")?.let { return it }
        }
        if (h.endsWith("bing.com") && u.contains("/ck/a")) {
            val enc = queryParam(u, "u")
            if (enc != null && enc.startsWith("a1")) {
                try {
                    return String(Base64.getUrlDecoder().decode(enc.substring(2).padEnd((enc.length - 2 + 3) / 4 * 4, '=')))
                } catch (e: Exception) {
                }
            }
        }
        return u
    }

    fun parse(raw: String, max: Int): List<SearchResult> {
        val arr = try {
            json.parseToJsonElement(raw) as? JsonArray
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<SearchResult>()
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val url = unwrap((o["url"] as? JsonPrimitive)?.contentOrNull.orEmpty())
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            val h = host(url)
            if (blockedHosts.any { h == it || h.endsWith(".$it") }) continue
            if (!seen.add(url)) continue
            val title = (o["title"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            val snippet = (o["snippet"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            out.add(SearchResult(title.ifEmpty { h }, url, snippet))
            if (out.size >= max) break
        }
        return out
    }
}
