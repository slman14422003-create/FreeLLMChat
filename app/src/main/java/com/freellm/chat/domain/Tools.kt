package com.freellm.chat.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class ToolGroup { MATH, INFO, CODE, PROJECT }

data class ToolSpec(
    val id: String,
    val group: ToolGroup,
    val usage: String,
    val bodyKey: String? = null
)

data class ToolCall(val name: String, val args: Map<String, String>) {
    fun str(key: String): String = args[key].orEmpty().trim()
    fun int(key: String): Int? = args[key]?.trim()?.toDoubleOrNull()?.toInt()
    fun dbl(key: String): Double? = args[key]?.trim()?.toDoubleOrNull()
}

data class ToolResult(val text: String, val ok: Boolean = true, val project: Boolean = false)

data class ToolStep(val name: String, val summary: String, val ok: Boolean, val project: Boolean)

data class ToolContext(val conversationId: Long)

interface ToolExecutor {
    suspend fun execute(call: ToolCall, ctx: ToolContext): ToolResult
}

object ToolCatalog {
    val all: List<ToolSpec> = listOf(
        ToolSpec(
            "calculator", ToolGroup.MATH,
            "{\"expression\":\"...\",\"vars\":\"x=2;y=3\"} exact math. Operators + - * / ^ ! and functions sin cos tan asin acos atan ln log2 log10 sqrt abs floor ceil round min max gcd lcm mod(a,b) C(n,k) " +
                "sum(i,1,n,expr) prod(i,1,n,expr) der(f(x),x,x0) int(f(x),x,a,b) solve(f(x),x,a,b). Angles are radians, constants pi e."
        ),
        ToolSpec(
            "convert", ToolGroup.MATH,
            "{\"value\":12,\"from\":\"km\",\"to\":\"mi\"} converts length, mass, volume, area, speed, time, data size, temperature, pressure, angle and currency codes (USD, EUR, SAR...)."
        ),
        ToolSpec(
            "datetime", ToolGroup.INFO,
            "{\"op\":\"now|add|diff|weekday|hijri\",\"date\":\"2026-10-28\",\"to\":\"2027-01-01\",\"days\":10,\"months\":0,\"years\":0,\"zone\":\"Asia/Riyadh\"} real current date and time, date arithmetic, Hijri dates."
        ),
        ToolSpec("web_search", ToolGroup.INFO, "{\"query\":\"...\"} searches the web, returns titles, links and snippets."),
        ToolSpec("fetch_url", ToolGroup.INFO, "{\"url\":\"https://...\",\"offset\":0} reads a web page as text (use offset to continue a long page)."),
        ToolSpec("wikipedia", ToolGroup.INFO, "{\"query\":\"...\",\"lang\":\"ar|en\"} short encyclopedia summary of a topic."),
        ToolSpec("weather", ToolGroup.INFO, "{\"city\":\"Riyadh\"} live weather now and a 3 day forecast for a city."),
        ToolSpec(
            "run_js", ToolGroup.CODE,
            "body is JavaScript, no network, use console.log for output, await is allowed: <tool_call name=\"run_js\">code</tool_call> runs it and returns the output. Use it to test algorithms and verify numbers.",
            "code"
        ),
        ToolSpec(
            "run_code", ToolGroup.CODE,
            "<tool_call name=\"run_code\" language=\"python\">code</tool_call> runs code in another language on the user's remote runner and returns stdout and stderr.",
            "code"
        ),
        ToolSpec("check_syntax", ToolGroup.CODE, "{\"path\":\"src/data.json\"} checks a project file for syntax errors (json, xml, js)."),
        ToolSpec(
            "encode", ToolGroup.CODE,
            "{\"op\":\"base64|base64d|url|urld|hex|hexd|sha256|sha1|sha512|md5\",\"text\":\"...\"} exact encoding, decoding and hashes."
        ),
        ToolSpec(
            "random", ToolGroup.MATH,
            "{\"op\":\"int|uuid|coin|pick|shuffle|password\",\"min\":1,\"max\":6,\"count\":1,\"items\":\"a,b,c\",\"length\":16} real randomness."
        ),
        ToolSpec(
            "regex_test", ToolGroup.CODE,
            "{\"pattern\":\"...\",\"text\":\"...\",\"flags\":\"i|m|s\"} runs a regular expression on text and lists matches and groups."
        ),
        ToolSpec(
            "json_query", ToolGroup.CODE,
            "{\"json\":\"...\",\"path\":\"a.b[0].c\"} reads a value from JSON, an empty path describes the structure."
        ),
        ToolSpec("text_stats", ToolGroup.INFO, "{\"text\":\"...\"} exact counts of characters, words, lines, sentences and reading time."),
        ToolSpec(
            "project_template", ToolGroup.PROJECT,
            "{\"type\":\"web-static|node-express|python-flask|python-cli|kotlin-cli|android-compose\"} fills the project workspace with a ready starter project."
        ),
        ToolSpec(
            "project_write", ToolGroup.PROJECT,
            "<tool_call name=\"project_write\" path=\"src/main.py\">full file content</tool_call> creates or replaces one file of the project. Write complete files, never placeholders.",
            "content"
        ),
        ToolSpec("project_read", ToolGroup.PROJECT, "{\"path\":\"src/main.py\"} returns a project file."),
        ToolSpec("project_list", ToolGroup.PROJECT, "{} lists all project files."),
        ToolSpec("project_delete", ToolGroup.PROJECT, "{\"path\":\"src/old.py\"} removes a project file.")
    )

    fun spec(id: String): ToolSpec? = all.firstOrNull { it.id == id }

    fun ids(): List<String> = all.map { it.id }
}

data class ParsedCalls(val calls: List<ToolCall>, val cleanRaw: String, val visible: String, val bad: Int = 0)

object ToolProtocol {
    const val TAG = "<tool_call"
    const val CLOSE = "</tool_call>"
    const val MAX_CALLS = 8
    const val MAX_RESULT_CHARS = 3200

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
    private val attr = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
    private val dangling = Regex("(?:^|\\n)[ \\t]*(`{3,}|~{3,})[A-Za-z0-9_+\\-]*\\s*$")
    private val nameKeys = listOf("name", "tool", "function")
    private val argKeys = listOf("arguments", "args", "parameters", "input")

    fun system(tools: List<ToolSpec>, projectMode: Boolean): String {
        val sb = StringBuilder()
        sb.append("You are the brain and the tools are your hands. Work in this order every time.\n")
            .append("1. Think first: open your reply with <think>...</think> saying what the user really needs, whether a tool would make the answer exact or current, and which tools to call in which order. If you can answer reliably alone, say so and skip tools.\n")
            .append("2. To use a tool write a block exactly like <tool_call>{\"name\":\"TOOL\",\"arguments\":{...}}</tool_call> right after the think block (tools marked with a body use the form shown in their entry). Independent calls may share one reply. After the last block stop writing; the results come back in the next message inside <tool_result>.\n")
            .append("3. When results arrive write a short <think> to check they are enough and correct, then call more tools or give the final answer.\n")
            .append("4. The final answer goes outside think tags, in the user's language, with no tool block, and it does not narrate these steps.\n")
            .append("Never invent a tool result. If a tool fails, read the error, fix the arguments or use another tool, and say plainly what could not be verified.\n")
        if (projectMode) {
            sb.append("Project workspace: build projects by writing every file with project_write (complete content), check them, ")
                .append("then tell the user the project is ready and can be saved as ZIP from the message menu. Do not repeat file contents in the final answer.\n")
        }
        sb.append("Tools:\n")
        tools.forEach { sb.append("- ").append(it.id).append(' ').append(it.usage).append('\n') }
        return sb.toString().trimEnd()
    }

    fun result(name: String, r: ToolResult): String {
        val body = r.text.trim().let { if (it.length > MAX_RESULT_CHARS) it.take(MAX_RESULT_CHARS) + "\n…[truncated]" else it }
        return "<tool_result name=\"" + name + "\" ok=\"" + r.ok + "\">\n" + body + "\n</tool_result>"
    }

    private fun partialTag(s: String): Int {
        val max = minOf(TAG.length - 1, s.length)
        for (n in max downTo 1) {
            if (s.regionMatches(s.length - n, TAG, 0, n)) return n
        }
        return 0
    }

    fun visibleLength(raw: CharSequence, final: Boolean): Int {
        val s = raw.toString()
        val idx = s.indexOf(TAG)
        val end = when {
            idx >= 0 -> idx
            final -> s.length
            else -> s.length - partialTag(s)
        }
        if (idx < 0 && final) return end
        val head = s.substring(0, end)
        val m = dangling.find(head)
        return if (m != null) m.range.first else end
    }

    fun visible(raw: String): String = raw.substring(0, visibleLength(raw, true)).trimEnd()

    private fun jsonString(v: kotlinx.serialization.json.JsonElement?): String? = when (v) {
        null -> null
        is JsonPrimitive -> v.contentOrNull
        else -> v.toString()
    }

    private fun fromJson(text: String): ToolCall? {
        val start = text.indexOf('{')
        val endIdx = text.lastIndexOf('}')
        if (start < 0 || endIdx <= start) return null
        val parsed = try {
            lenient.parseToJsonElement(text.substring(start, endIdx + 1)) as? JsonObject
        } catch (e: Exception) {
            null
        }
        val obj = parsed ?: return null
        val name = nameKeys.firstNotNullOfOrNull { k -> (obj[k] as? JsonPrimitive)?.contentOrNull }?.trim().orEmpty()
        if (name.isEmpty()) return null
        val argsEl = argKeys.firstNotNullOfOrNull { k -> obj[k] }
        val args = LinkedHashMap<String, String>()
        when (argsEl) {
            is JsonObject -> argsEl.forEach { (k, v) -> jsonString(v)?.let { args[k] = it } }
            is JsonPrimitive -> {
                val raw = argsEl.contentOrNull.orEmpty()
                val inner = try {
                    lenient.parseToJsonElement(raw) as? JsonObject
                } catch (e: Exception) {
                    null
                }
                inner?.forEach { (k, v) -> jsonString(v)?.let { args[k] = it } }
            }
            is JsonArray, null -> Unit
        }
        obj.forEach { (k, v) -> if (k !in nameKeys && k !in argKeys && k !in args) jsonString(v)?.let { args[k] = it } }
        return ToolCall(name, args)
    }

    private fun fromAttr(head: String, body: String, bodyKeyOf: (String) -> String?): ToolCall? {
        val args = LinkedHashMap<String, String>()
        attr.findAll(head).forEach { m ->
            val v = if (m.groups[3] != null) m.groupValues[3] else m.groupValues[4]
            args[m.groupValues[1]] = v
        }
        val name = (args.remove("name") ?: args.remove("tool") ?: return null).trim()
        if (name.isEmpty()) return null
        val key = bodyKeyOf(name)
        val trimmed = body.trim('\n', '\r')
        if (key != null && trimmed.isNotBlank()) args[key] = unfence(trimmed)
        return ToolCall(name, args)
    }

    private val fenceBody = Regex("(?s)^\\s*(`{3,}|~{3,})[A-Za-z0-9_+\\-]*\\r?\\n(.*?)\\r?\\n?\\1\\s*$")

    private fun unfence(s: String): String {
        val m = fenceBody.find(s) ?: return s
        return m.groupValues[2]
    }

    fun parse(raw: String, bodyKeyOf: (String) -> String? = { ToolCatalog.spec(it)?.bodyKey }): ParsedCalls {
        val calls = ArrayList<ToolCall>()
        var bad = 0
        var pos = 0
        var lastEnd = -1
        while (calls.size < MAX_CALLS) {
            val open = raw.indexOf(TAG, pos)
            if (open < 0) break
            val gt = raw.indexOf('>', open)
            if (gt < 0) break
            val close = raw.indexOf(CLOSE, gt)
            val bodyEnd = if (close < 0) raw.length else close
            val head = raw.substring(open + TAG.length, gt)
            val body = raw.substring(gt + 1, bodyEnd)
            val call = if (head.isNotBlank() && attr.containsMatchIn(head)) fromAttr(head, body, bodyKeyOf) else fromJson(body)
            if (call != null) calls.add(call) else bad++
            lastEnd = if (close < 0) raw.length else close + CLOSE.length
            pos = lastEnd
            if (close < 0) break
        }
        val clean = if (lastEnd >= 0) raw.substring(0, lastEnd) else raw
        return ParsedCalls(calls, clean, visible(raw), bad)
    }
}

object ToolPolicy {
    const val MODE_SMART = "smart"
    const val MODE_ALWAYS = "always"

    private val opt = RegexOption.IGNORE_CASE
    private val math = Regex(
        "\\d\\s*[-+*/^×÷%]\\s*\\d|\\b(calculate|compute|solve|equation|integral|derivative|sqrt|factorial|percent|percentage|average|mean|probability|sum of|prime|gcd|lcm|modulo|matrix|logarithm|sine|cosine)\\b|" +
            "احسب|حساب|معادلة|معادله|تكامل|مشتق|تفاضل|جذر|نسبة|نسبه|احتمال|متوسط|مضروب|عاملي|أولي|اولي|قاسم|مضاعف|لوغاريتم|جيب|تقريب|رياضيات|مسألة|مسأله",
        opt
    )
    private val convert = Regex(
        "\\b(convert|conversion|how many (km|kilometers|miles|meters|kg|pounds|liters|gallons|hours|minutes)|in (km|kg|miles|celsius|fahrenheit|usd|eur|sar))\\b|حوّل|حول |تحويل|كم يساوي|كم تساوي|بالدولار|بالريال|بالكيلو|بالميل|سعر الصرف|exchange rate",
        opt
    )
    private val date = Regex(
        "\\b(today|tomorrow|yesterday|date|time|weekday|hijri|days (until|left|between)|how long until|age)\\b|التاريخ|اليوم|غدا|غداً|أمس|امس|الساعة|هجري|هجرية|كم يوم|كم باقي|كم باقٍ|متى|عمر",
        opt
    )
    private val fresh = Regex(
        "\\b(search|latest|news|current|price|weather|score|released|release date|who won|stock)\\b|ابحث|بحث|آخر|اخر|أخبار|اخبار|الآن|حاليا|حالياً|سعر|طقس|نتيجة|صدر",
        opt
    )
    private val factual = Regex("\\b(who is|what is|who was|define|definition|history of)\\b|من هو|من هي|ما هو|ما هي|تعريف|نبذة|تاريخ ", opt)
    private val url = Regex("https?://\\S+", opt)
    private val codeish = Regex(
        "\\b(code|function|script|javascript|js|algorithm|debug|bug|regex|json|xml|snippet|implement|refactor)\\b|كود|دالة|دوال|سكربت|سكريبت|خوارزمية|برمجة|برنامج|اختبر|شغل|شغّل|نفذ|نفّذ|صحح|أصلح|اصلح|خطأ",
        opt
    )
    private val project = Regex(
        "\\b(project|app|application|website|web ?site|api|bot|game|extension|plugin|scaffold|repo|files)\\b|مشروع|تطبيق|موقع|بوت|لعبة|إضافة|اضافة|ملفات|هيكل",
        opt
    )

    private val utility = Regex(
        "\\b(random|uuid|hash|sha256|md5|base64|regex|json|weather|temperature|forecast)\\b|\u0639\u0634\u0648\u0627\u0626\u064a|\u0639\u0634\u0648\u0627\u0626\u064a\u0629|\u0647\u0627\u0634|\u062a\u0634\u0641\u064a\u0631|\u0637\u0642\u0633|\u062d\u0631\u0627\u0631\u0629|\u0643\u0644\u0645\u0629 \u0645\u0631\u0648\u0631|\u0628\u0627\u0633\u0648\u0648\u0631\u062f",
        opt
    )
    private const val TRIVIAL_CHARS = 24

    fun trivial(text: String): Boolean {
        val t = text.trim()
        if (t.length > TRIVIAL_CHARS) return false
        return !(math.containsMatchIn(t) || convert.containsMatchIn(t) || date.containsMatchIn(t) || fresh.containsMatchIn(t) ||
            factual.containsMatchIn(t) || url.containsMatchIn(t) || codeish.containsMatchIn(t) || project.containsMatchIn(t) ||
            utility.containsMatchIn(t))
    }

    fun projectIntent(text: String, workspaceHasFiles: Boolean): Boolean {
        val wants = CodeFiles.wantsProject(text) || (project.containsMatchIn(text) && codeish.containsMatchIn(text))
        return wants || (workspaceHasFiles && (codeish.containsMatchIn(text) || project.containsMatchIn(text)))
    }

    fun select(text: String, enabled: Set<String>, mode: String, hasRunner: Boolean, workspaceHasFiles: Boolean): List<ToolSpec> {
        val pool = ToolCatalog.all.filter { it.id in enabled && (it.id != "run_code" || hasRunner) }
        if (pool.isEmpty()) return emptyList()
        if (mode == MODE_ALWAYS) {
            val withProject = projectIntent(text, workspaceHasFiles)
            return pool.filter { it.group != ToolGroup.PROJECT || withProject }
        }
        val want = LinkedHashSet<String>()
        if (math.containsMatchIn(text)) {
            want.add("calculator")
            want.add("convert")
            want.add("run_js")
        }
        if (convert.containsMatchIn(text)) want.add("convert")
        if (date.containsMatchIn(text)) want.add("datetime")
        if (utility.containsMatchIn(text)) want.addAll(listOf("weather", "encode", "random", "regex_test", "json_query", "text_stats"))
        if (url.containsMatchIn(text)) want.add("fetch_url")
        if (fresh.containsMatchIn(text)) {
            want.add("web_search")
            want.add("fetch_url")
        }
        if (factual.containsMatchIn(text)) {
            want.add("wikipedia")
            want.add("web_search")
        }
        val wantsProject = CodeFiles.wantsProject(text) || (project.containsMatchIn(text) && codeish.containsMatchIn(text))
        if (codeish.containsMatchIn(text) || wantsProject) {
            want.add("run_js")
            want.add("run_code")
            want.add("check_syntax")
        }
        if (wantsProject || (workspaceHasFiles && (codeish.containsMatchIn(text) || project.containsMatchIn(text)))) {
            want.addAll(listOf("project_template", "project_write", "project_read", "project_list", "project_delete", "check_syntax", "run_js"))
        }
        return pool.filter { it.id in want }
    }

    fun projectMode(tools: List<ToolSpec>): Boolean = tools.any { it.group == ToolGroup.PROJECT && it.id == "project_write" }
}

internal fun cutDanglingTool(s: String): String {
    val open = s.lastIndexOf(ToolProtocol.TAG)
    if (open >= 0 && s.indexOf(ToolProtocol.CLOSE, open) < 0) return s.substring(0, open)
    val max = minOf(ToolProtocol.TAG.length - 1, s.length)
    for (n in max downTo 1) {
        if (s.regionMatches(s.length - n, ToolProtocol.TAG, 0, n)) return s.substring(0, s.length - n)
    }
    return s
}

class ToolRunner(private val router: Router, private val executor: ToolExecutor) {
    fun stream(
        req: RouteRequest,
        ctx: ToolContext,
        onStatus: (String?) -> Unit,
        onStep: (ToolStep) -> Unit
    ): Flow<RouteEvent> = flow {
        var msgs = req.messages
        var base = req
        val committed = StringBuilder()
        var round = 0
        while (true) {
            val last = round >= MAX_ROUNDS
            val raw = StringBuilder()
            var shown = 0
            var failed = false
            var done: RouteEvent.Done? = null
            var sep = committed.isNotEmpty()
            val roundReq = base.copy(messages = msgs, estimatedTokens = RequestFitter.needed(msgs, base.maxTokens))
            router.stream(roundReq).collect { ev ->
                when (ev) {
                    is RouteEvent.Delta -> {
                        raw.append(ev.text)
                        val vis = ToolProtocol.visibleLength(raw, false)
                        if (vis > shown) {
                            if (sep) {
                                emit(RouteEvent.Delta("\n\n"))
                                sep = false
                            }
                            emit(RouteEvent.Delta(raw.substring(shown, vis)))
                            shown = vis
                        }
                    }
                    is RouteEvent.Switched -> {
                        val cut = cutDanglingTool(raw.toString())
                        if (cut.length < raw.length) raw.setLength(cut.length)
                        if (shown > raw.length) shown = raw.length
                        emit(ev)
                    }
                    is RouteEvent.Restart -> {
                        raw.setLength(0)
                        shown = 0
                        sep = committed.isNotEmpty()
                        emit(RouteEvent.Restart)
                        if (committed.isNotEmpty()) emit(RouteEvent.Delta(committed.toString()))
                    }
                    is RouteEvent.Done -> {
                        done = ev
                    }
                    is RouteEvent.Failed -> {
                        failed = true
                        emit(ev)
                    }
                    else -> emit(ev)
                }
            }
            if (failed) return@flow
            val text = raw.toString()
            val finalVis = ToolProtocol.visibleLength(text, true)
            if (finalVis > shown) {
                if (sep) {
                    emit(RouteEvent.Delta("\n\n"))
                    sep = false
                }
                emit(RouteEvent.Delta(text.substring(shown, finalVis)))
            }
            val parsed = if (last) ParsedCalls(emptyList(), text, "") else ToolProtocol.parse(text)
            if (parsed.calls.isEmpty() && parsed.bad == 0) {
                done?.let { emit(it) }
                return@flow
            }
            done?.let { base = base.copy(preferredKey = it.target.key) }
            val shownText = ToolProtocol.visible(text)
            if (shownText.isNotEmpty()) {
                if (committed.isNotEmpty()) committed.append("\n\n")
                committed.append(shownText)
            }
            val results = StringBuilder()
            if (parsed.calls.isEmpty()) {
                results.append(ToolProtocol.result("tool_call", ToolResult(BAD_CALL, false))).append('\n')
            }
            for (call in parsed.calls) {
                onStatus(call.name)
                val r = try {
                    executor.execute(call, ctx)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    ToolResult("Tool error: " + (e.message ?: e.javaClass.simpleName).take(160), false)
                }
                onStep(ToolStep(call.name, summarize(call, r), r.ok, r.project))
                results.append(ToolProtocol.result(call.name, r)).append('\n')
            }
            onStatus(null)
            val callText = parsed.cleanRaw.trim()
            val nextIsLast = round + 1 >= MAX_ROUNDS
            msgs = msgs + ChatMsg("assistant", callText) +
                ChatMsg("user", results.toString().trim() + "\n" + (if (nextIsLast) FINAL_AFTER_TOOLS else CONTINUE_AFTER_TOOLS))
            round++
        }
    }

    private fun summarize(call: ToolCall, r: ToolResult): String {
        val arg = call.args.entries.firstOrNull { it.key != "content" && it.key != "code" }?.value.orEmpty()
        val a = arg.replace('\n', ' ').take(48)
        val res = r.text.replace('\n', ' ').trim().take(64)
        return if (a.isEmpty()) res else "$a → $res"
    }

    companion object {
        const val MAX_ROUNDS = 6
        private const val BAD_CALL =
            "The tool block could not be read. Use <tool_call>{\"name\":\"TOOL\",\"arguments\":{...}}</tool_call> or the body form shown for that tool."
        private const val CONTINUE_AFTER_TOOLS =
            "The earlier steps may have been written by another model; treat them as your own work and keep the same tool rules. " +
                "Write a short <think> to check the results above. If more tools are needed call them, otherwise write the final answer now without any tool block."
        private const val FINAL_AFTER_TOOLS =
            "Tools are no longer available. Write the final answer now from the results above, without any tool block, and say plainly what could not be verified."
    }
}
