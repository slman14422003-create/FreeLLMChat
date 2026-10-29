package com.freellm.chat.domain

data class CodeFile(val path: String, val content: String)

object CodeFiles {
    const val MAX_FILES = 300
    const val MAX_TOTAL_CHARS = 4_000_000

    const val SYSTEM =
        "When the user asks for a project, an app or several files, give every file in its own fenced code block " +
            "and write its relative path alone on the line right before the fence, exactly like: File: app/src/Main.kt. " +
            "Include every file needed to build and run the project. Never shorten a file with placeholders or ellipses."

    private val fence = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*(.*)$")
    private val pathSeg = Regex("^[\\w@+.\\-]+(?:/[\\w@+.\\-]+)*$")
    private val extension = Regex(".*\\.[A-Za-z][A-Za-z0-9]{0,7}$")
    private val bare = setOf("Dockerfile", "Makefile", "gradlew", "Procfile", "LICENSE", "Gemfile", "Jenkinsfile")
    private val labelPrefix = Regex("^(?i:file(?:name)?|path|الملف|ملف|المسار)\\s*[:：\\-]\\s*")
    private val dotFile = Regex("^\\.[A-Za-z][A-Za-z0-9_\\-]{1,30}$")
    private val numbering = Regex("^\\d+[.)]\\s*")
    private val infoKey = Regex("(?i)(?:path|file|filename|title)\\s*=\\s*[\"']?([^\"'\\s]+)")
    private val firstLine = Regex("^(?://|#|--|<!--|/\\*)\\s*(?i:file|path|filename|الملف)\\s*[:=]\\s*(\\S+?)\\s*(?:-->|\\*/)?\\s*$")

    private val ask = Regex(
        "\\b(zip|project|repo|repository|files)\\b|مشروع|ملفات|ضغط",
        RegexOption.IGNORE_CASE
    )
    private val build = Regex(
        "\\b(create|build|generate|make|write|scaffold|develop)\\b|أنشئ|انشئ|اصنع|اعمل|ابني|ابنِ|اكتب|سوي|سوّي|طور|طوّر",
        RegexOption.IGNORE_CASE
    )
    private val kinds = Regex(
        "\\b(app|android|website|web ?site|api|bot|game|extension|plugin)\\b|تطبيق|موقع|بوت|لعبة|إضافة|اضافة",
        RegexOption.IGNORE_CASE
    )

    fun wantsProject(question: String): Boolean = build.containsMatchIn(question) && (ask.containsMatchIn(question) || kinds.containsMatchIn(question))

    fun safePath(raw: String): String? {
        var p = raw.trim().replace('\\', '/')
        while (p.startsWith("./")) p = p.substring(2)
        p = p.trimStart('/')
        if (p.isEmpty() || p.length > 200) return null
        if (p.split('/').any { it == ".." || it.isEmpty() }) return null
        if (!pathSeg.matches(p)) return null
        val last = p.substringAfterLast('/')
        return if (extension.matches(last) || last in bare || dotFile.matches(last)) p else null
    }

    private fun label(line: String): String? {
        var s = line.trim().trim('#', '>', ' ', '*', '`', ':', '-')
        s = numbering.replace(s, "")
        s = labelPrefix.replace(s, "").trim('`', '*', ' ', ':', '"', '\'')
        return safePath(s)
    }

    private fun fromInfo(info: String): String? {
        infoKey.find(info)?.let { m -> safePath(m.groupValues[1])?.let { return it } }
        val tokens = info.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for ((i, t) in tokens.withIndex()) {
            val c = if (i == 0) t.substringAfter(':', "") else t
            if (c.isNotEmpty()) safePath(c)?.let { return it }
        }
        return null
    }

    fun extract(markdown: String): List<CodeFile> {
        val lines = markdown.lines()
        val out = LinkedHashMap<String, String>()
        var total = 0
        var i = 0
        while (i < lines.size) {
            val m = fence.find(lines[i])
            if (m == null) {
                i++
                continue
            }
            val marker = m.groupValues[1]
            val info = m.groupValues[2]
            var prev: String? = null
            var back = i - 1
            var seen = 0
            while (back >= 0 && seen < 2) {
                val l = lines[back]
                if (l.isNotBlank()) {
                    prev = if (fence.matches(l)) null else l
                    break
                }
                seen++
                back--
            }
            var j = i + 1
            val body = ArrayList<String>()
            while (j < lines.size) {
                val t = lines[j].trim()
                if (t.length >= marker.length && t.all { it == marker[0] }) break
                body.add(lines[j])
                j++
            }
            var path = fromInfo(info) ?: prev?.let { label(it) }
            if (path == null && body.isNotEmpty()) {
                path = firstLine.find(body[0].trim())?.groupValues?.get(1)?.let { safePath(it) }
            }
            if (path != null) {
                val text = body.joinToString("\n") + "\n"
                total += text.length
                if (total > MAX_TOTAL_CHARS || (out.size >= MAX_FILES && path !in out)) break
                out[path] = text
            }
            i = j + 1
        }
        return out.map { CodeFile(it.key, it.value) }
    }

    fun projectName(files: List<CodeFile>): String {
        val roots = files.map { it.path.substringBefore('/', "") }.toSet()
        val root = roots.singleOrNull()
        return if (root != null && root.isNotEmpty() && files.all { '/' in it.path }) root else "project"
    }
}
