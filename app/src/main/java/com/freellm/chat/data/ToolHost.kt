package com.freellm.chat.data

import com.freellm.chat.FreeLlmApp
import com.freellm.chat.domain.ChainSearch
import com.freellm.chat.domain.DateTools
import com.freellm.chat.domain.ExtraTools
import com.freellm.chat.domain.ProjectTemplates
import com.freellm.chat.domain.SearchKind
import com.freellm.chat.domain.SearchLimits
import com.freellm.chat.domain.SearchProvider
import com.freellm.chat.domain.SpeechText
import com.freellm.chat.domain.ToolCall
import com.freellm.chat.domain.ToolContext
import com.freellm.chat.domain.ToolExecutor
import com.freellm.chat.domain.ToolResult
import com.freellm.chat.domain.UnitConvert
import com.freellm.chat.domain.UrlPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class AppToolHost(private val app: FreeLlmApp) : ToolExecutor {
    private val json = Json { ignoreUnknownKeys = true }
    private val js = JsRunner(app)
    private val fetcher = PageFetcher(app.http) { app.settings.proxyUrl.first() }

    suspend fun hasRunner(): Boolean = app.settings.runnerUrl.first().isNotBlank()

    private fun fail(msg: String) = ToolResult(msg, false)

    override suspend fun execute(call: ToolCall, ctx: ToolContext): ToolResult = when (call.name) {
        "calculator" -> calculator(call)
        "convert" -> convert(call)
        "datetime" -> withContext(Dispatchers.Default) { ToolResult(DateTools.run(call)) }
        "web_search" -> webSearch(call)
        "fetch_url" -> fetchUrl(call)
        "wikipedia" -> wikipedia(call)
        "weather" -> weather(call)
        "run_js" -> runJs(call)
        "run_code" -> runCode(call)
        "check_syntax" -> checkSyntax(call, ctx)
        "project_template" -> template(call, ctx)
        "project_write" -> write(call, ctx)
        "project_read" -> read(call, ctx)
        "project_list" -> list(ctx)
        "project_delete" -> delete(call, ctx)
        else -> ExtraTools.run(call) ?: fail("Unknown tool: " + call.name)
    }

    private suspend fun calculator(call: ToolCall): ToolResult {
        val e = call.str("expression").ifEmpty { call.str("expr") }
        if (e.isEmpty()) return fail("expression is required")
        val r = withContext(Dispatchers.Default) { MathEngine.eval(e, call.str("vars")) }
        val bad = r.startsWith("Syntax error") || r.startsWith("Math error") || r.startsWith("Calculation timed out")
        return ToolResult(r, !bad)
    }

    private suspend fun convert(call: ToolCall): ToolResult {
        val v = call.dbl("value") ?: return fail("value must be a number")
        val from = call.str("from")
        val to = call.str("to")
        if (from.isEmpty() || to.isEmpty()) return fail("from and to are required")
        UnitConvert.describe(v, from, to)?.let { return ToolResult(it) }
        if (UnitConvert.known(from) && UnitConvert.known(to)) return fail("These units measure different things.")
        val a = UnitConvert.currencyCode(from)
        val b = UnitConvert.currencyCode(to)
        if (a == null || b == null || UnitConvert.known(from) || UnitConvert.known(to)) return fail("Unknown unit.")
        val body = getText("https://open.er-api.com/v6/latest/" + a) ?: return fail("Exchange rates are not reachable now.")
        val rate = try {
            val rates = (json.parseToJsonElement(body) as? JsonObject)?.get("rates") as? JsonObject
            (rates?.get(b) as? JsonPrimitive)?.doubleOrNull
        } catch (e: Exception) {
            null
        } ?: return fail("No exchange rate for $a to $b.")
        return ToolResult(UnitConvert.format(v) + " " + a + " = " + UnitConvert.format(v * rate) + " " + b + " (rate " + UnitConvert.format(rate) + ", open.er-api.com)")
    }

    private suspend fun provider(): SearchProvider? {
        val s = app.settings
        val useLocal = s.localSearch.first()
        val keyed = SearchFactory.resolve(SearchKind.of(s.searchProvider.first()), app.searchHttp, app.secrets, s.proxyUrl.first())
        return when {
            keyed != null && useLocal -> ChainSearch(keyed, app.localSearch)
            keyed != null -> keyed
            useLocal -> app.localSearch
            else -> null
        }
    }

    private suspend fun webSearch(call: ToolCall): ToolResult {
        val q = call.str("query").ifEmpty { call.str("q") }
        if (q.isEmpty()) return fail("query is required")
        val p = provider() ?: return fail("Web search is not set up. Add a search key or enable local search in settings.")
        val limit = SearchLimits.effective(p.kind, app.settings.searchLimits.first())
        val now = System.currentTimeMillis()
        if (app.searchQuota.remaining(p.kind, limit, now) <= 0) return fail("Search quota is used up.")
        val found = try {
            p.search(q, 5)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return fail("Search failed: " + (e.message ?: e.javaClass.simpleName).take(80))
        }
        app.searchQuota.record(p.kind, limit, System.currentTimeMillis())
        if (found.isEmpty()) return ToolResult("No results.")
        val sb = StringBuilder()
        found.take(5).forEachIndexed { i, r ->
            sb.append(i + 1).append(". ").append(r.title.ifBlank { UrlPolicy.host(r.url) }).append(" - ").append(r.url).append('\n')
                .append(r.snippet.replace('\n', ' ').take(260)).append("\n\n")
        }
        return ToolResult(sb.toString().trim())
    }

    private suspend fun fetchUrl(call: ToolCall): ToolResult {
        val url = call.str("url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) return fail("A full http(s) url is required.")
        val text = fetcher.fetch(url) ?: return fail("The page could not be read (blocked, empty or not text).")
        val offset = (call.int("offset") ?: 0).coerceIn(0, text.length)
        val part = text.substring(offset).take(PAGE_CHUNK)
        val next = offset + part.length
        return ToolResult(part + if (next < text.length) "\n[more text: call again with offset=" + next + "]" else "")
    }

    private suspend fun getText(url: String): String? = try {
        val req = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "FreeLLMChat/1.0")
            .build()
        app.searchHttp.newCall(req).await().use { r ->
            if (!r.isSuccessful) null else withContext(Dispatchers.IO) { r.body?.string() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private suspend fun wikipedia(call: ToolCall): ToolResult {
        val q = call.str("query").ifEmpty { call.str("title") }
        if (q.isEmpty()) return fail("query is required")
        val lang = call.str("lang").lowercase().filter { it in 'a'..'z' }.take(3).ifEmpty { if (SpeechText.isArabic(q)) "ar" else "en" }
        val url = "https://" + lang + ".wikipedia.org/w/api.php?action=query&format=json&prop=extracts&exintro=1&explaintext=1&redirects=1" +
            "&generator=search&gsrlimit=1&gsrsearch=" + URLEncoder.encode(q, "UTF-8")
        val body = getText(url) ?: return fail("Wikipedia is not reachable now.")
        val page = try {
            val pages = ((json.parseToJsonElement(body) as? JsonObject)?.get("query") as? JsonObject)?.get("pages") as? JsonObject
            pages?.values?.firstOrNull() as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return ToolResult("No article found.")
        val title = (page["title"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val extract = (page["extract"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        if (extract.isEmpty()) return ToolResult("No article found.")
        val link = "https://" + lang + ".wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        return ToolResult(title + "\n" + extract.take(2600) + "\nSource: " + link)
    }

    private fun numbers(o: JsonObject?, key: String): List<String> =
        (o?.get(key) as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } ?: emptyList()

    private fun fieldOf(o: JsonObject?, key: String): String = (o?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private suspend fun weather(call: ToolCall): ToolResult {
        val city = call.str("city").ifEmpty { call.str("query") }.ifEmpty { call.str("location") }
        if (city.isEmpty()) return fail("city is required")
        val lang = if (SpeechText.isArabic(city)) "ar" else "en"
        val geo = getText("https://geocoding-api.open-meteo.com/v1/search?count=1&language=" + lang + "&name=" + URLEncoder.encode(city, "UTF-8"))
            ?: return fail("Weather service is not reachable now.")
        val place = try {
            ((json.parseToJsonElement(geo) as? JsonObject)?.get("results") as? JsonArray)?.firstOrNull() as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return ToolResult("Place not found: " + city)
        val lat = (place["latitude"] as? JsonPrimitive)?.doubleOrNull
        val lon = (place["longitude"] as? JsonPrimitive)?.doubleOrNull
        if (lat == null || lon == null) return ToolResult("Place not found: " + city)
        val label = listOf(fieldOf(place, "name"), fieldOf(place, "admin1"), fieldOf(place, "country")).filter { it.isNotEmpty() }.distinct().joinToString(", ")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=3"
        val body = getText(url) ?: return fail("Weather service is not reachable now.")
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return fail("Weather data could not be read.")
        val cur = root["current"] as? JsonObject
        val daily = root["daily"] as? JsonObject
        val sb = StringBuilder()
        sb.append(label).append(": ")
            .append(fieldOf(cur, "temperature_2m")).append(" C, feels like ").append(fieldOf(cur, "apparent_temperature"))
            .append(", humidity ").append(fieldOf(cur, "relative_humidity_2m")).append("%, wind ").append(fieldOf(cur, "wind_speed_10m")).append(" km/h, ")
            .append(ExtraTools.weatherCode(fieldOf(cur, "weather_code").toDoubleOrNull()?.toInt() ?: -1)).append('\n')
        val days = numbers(daily, "time")
        val hi = numbers(daily, "temperature_2m_max")
        val lo = numbers(daily, "temperature_2m_min")
        val rain = numbers(daily, "precipitation_probability_max")
        val codes = numbers(daily, "weather_code")
        days.forEachIndexed { i, d ->
            sb.append(d).append(": ").append(lo.getOrElse(i) { "?" }).append(" to ").append(hi.getOrElse(i) { "?" }).append(" C, rain chance ")
                .append(rain.getOrElse(i) { "?" }).append("%, ")
                .append(ExtraTools.weatherCode(codes.getOrElse(i) { "" }.toDoubleOrNull()?.toInt() ?: -1)).append('\n')
        }
        return ToolResult(sb.toString().trim() + "\nSource: open-meteo.com")
    }

    private suspend fun runJs(call: ToolCall): ToolResult {
        val code = call.str("code").ifEmpty { call.str("content") }
        if (code.isEmpty()) return fail("code is required")
        val out = js.run(code)
        val bad = out.startsWith("SyntaxError") || out.startsWith("Error:") || out.startsWith("Timed out")
        return ToolResult(out.ifBlank { "(no output, use console.log to print values)" }, !bad)
    }

    private suspend fun runCode(call: ToolCall): ToolResult {
        val base = app.settings.runnerUrl.first().trim().trimEnd('/')
        if (base.isEmpty()) return fail("No remote runner is configured.")
        val lang = call.str("language")
        val code = call.str("code").ifEmpty { call.str("content") }
        if (lang.isEmpty() || code.isEmpty()) return fail("language and code are required")
        val payload = buildJsonObject {
            put("language", lang)
            put("version", "*")
            put("files", buildJsonArray { add(buildJsonObject { put("content", code) }) })
        }.toString()
        val client = app.http.newBuilder().callTimeout(40, TimeUnit.SECONDS).build()
        return try {
            val req = Request.Builder().url("$base/api/v2/execute")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).await().use { r ->
                val txt = withContext(Dispatchers.IO) { r.body?.string().orEmpty() }
                if (!r.isSuccessful) return@use fail("Runner error " + r.code + ": " + txt.take(160))
                val o = json.parseToJsonElement(txt) as? JsonObject
                val run = o?.get("run") as? JsonObject
                val compile = o?.get("compile") as? JsonObject
                val sb = StringBuilder()
                (compile?.get("stderr") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { sb.append(it).append('\n') }
                (run?.get("stdout") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { sb.append(it).append('\n') }
                (run?.get("stderr") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { sb.append("stderr:\n").append(it) }
                val exit = (run?.get("code") as? JsonPrimitive)?.doubleOrNull?.toInt()
                ToolResult(sb.toString().trim().ifEmpty { "(no output)" }, exit == null || exit == 0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            fail("Runner not reachable: " + (e.message ?: e.javaClass.simpleName).take(80))
        }
    }

    private suspend fun checkSyntax(call: ToolCall, ctx: ToolContext): ToolResult {
        val path = call.str("path")
        val text = withContext(Dispatchers.IO) { app.workspace.read(ctx.conversationId, path) } ?: return fail("File not found in the project: $path")
        val err: String? = when (path.substringAfterLast('.', "").lowercase()) {
            "json" -> try {
                json.parseToJsonElement(text)
                null
            } catch (e: Exception) {
                (e.message ?: "invalid json").take(200)
            }
            "xml" -> try {
                val f = DocumentBuilderFactory.newInstance()
                f.newDocumentBuilder().parse(InputSource(StringReader(text)))
                null
            } catch (e: SAXParseException) {
                "line " + e.lineNumber + ", column " + e.columnNumber + ": " + (e.message ?: "invalid xml").take(160)
            } catch (e: Exception) {
                (e.message ?: "invalid xml").take(200)
            }
            "js", "mjs" -> js.syntax(text)
            else -> return ToolResult("No checker for this file type (json, xml and js are supported).")
        }
        return if (err == null) ToolResult("$path: syntax OK") else ToolResult("$path: $err", false)
    }

    private suspend fun template(call: ToolCall, ctx: ToolContext): ToolResult {
        val type = call.str("type")
        val files = ProjectTemplates.files(type, call.str("name")) ?: return fail("Unknown type. Available: " + ProjectTemplates.types.joinToString(", "))
        return withContext(Dispatchers.IO) {
            for (f in files) {
                app.workspace.write(ctx.conversationId, f.path, f.content)?.let { return@withContext fail(it) }
            }
            ToolResult("Created " + files.size + " files: " + files.joinToString(", ") { it.path }, true, true)
        }
    }

    private suspend fun write(call: ToolCall, ctx: ToolContext): ToolResult {
        val path = call.str("path")
        val content = call.args["content"] ?: call.args["code"] ?: return fail("File content is missing. Put the full content inside the tool block.")
        if (path.isEmpty()) return fail("path is required")
        val body = content.trimEnd('\n', '\r', ' ') + "\n"
        return withContext(Dispatchers.IO) {
            val err = app.workspace.write(ctx.conversationId, path, body)
            if (err != null) fail(err)
            else ToolResult("Saved " + path + " (" + body.length + " chars). Project has " + app.workspace.count(ctx.conversationId) + " files.", true, true)
        }
    }

    private suspend fun read(call: ToolCall, ctx: ToolContext): ToolResult {
        val path = call.str("path")
        val text = withContext(Dispatchers.IO) { app.workspace.read(ctx.conversationId, path) } ?: return fail("File not found: $path")
        val offset = (call.int("offset") ?: 0).coerceIn(0, text.length)
        val part = text.substring(offset).take(PAGE_CHUNK)
        val next = offset + part.length
        return ToolResult(part + if (next < text.length) "\n[more text: call again with offset=" + next + "]" else "")
    }

    private suspend fun list(ctx: ToolContext): ToolResult {
        val files = withContext(Dispatchers.IO) { app.workspace.list(ctx.conversationId) }
        if (files.isEmpty()) return ToolResult("The project is empty.")
        return ToolResult(files.joinToString("\n") { it.path + " (" + it.size + " bytes)" })
    }

    private suspend fun delete(call: ToolCall, ctx: ToolContext): ToolResult {
        val path = call.str("path")
        val ok = withContext(Dispatchers.IO) { app.workspace.delete(ctx.conversationId, path) }
        return if (ok) ToolResult("Deleted $path", true, true) else fail("File not found: $path")
    }

    private companion object {
        const val PAGE_CHUNK = 3000
    }
}
