package com.freellm.chat.domain

enum class IssueKind { PLACEHOLDER, TRUNCATED, EMPTY, INVALID_XML, MISSING_FILE, MISSING_RESOURCE, MISSING_CONFIG, MISSING_REF, PLANNED_MISSING, CUT_OFF }

data class ProjectIssue(val kind: IssueKind, val path: String, val detail: String)

data class ProjectReview(val files: List<CodeFile>, val issues: List<ProjectIssue>, val planned: List<String>) {
    val complete: Boolean get() = files.isNotEmpty() && issues.isEmpty()
}

object ProjectCheck {
    const val MAX_ISSUES = 14

    private val planRe = Regex("\\[\\[\\s*PLAN\\s*:([\\s\\S]*?)]]", RegexOption.IGNORE_CASE)

    fun planned(text: String): List<String> {
        val out = LinkedHashSet<String>()
        planRe.findAll(text).forEach { m ->
            m.groupValues[1].split(',', '\n', ';').forEach { raw ->
                val p = CodeFiles.safePath(raw.trim().trim('`', '*', '"', '\'', ' '))
                if (p != null) out.add(p)
            }
        }
        return out.toList()
    }

    fun stripPlan(text: String): String {
        if (text.indexOf('[') < 0) return text
        var t = planRe.replace(text, "")
        val i = t.lastIndexOf("[[")
        if (i >= 0) {
            val tail = t.substring(i)
            if (tail.indexOf(']') < 0 && tail.length <= 4000 && tail.take(8).uppercase().let { "[[PLAN".startsWith(it) || it.startsWith("[[PLAN") }) t = t.substring(0, i)
        }
        return t
    }

    private fun ext(path: String): String {
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot < 0) "" else name.substring(dot + 1).lowercase()
    }

    private fun name(path: String): String = path.substringAfterLast('/')

    private val braceExt = setOf("kt", "kts", "java", "gradle", "js", "mjs", "cjs", "jsx", "ts", "tsx", "c", "cpp", "cc", "h", "hpp", "cs", "go", "rs", "swift", "php", "dart", "scala", "css", "scss", "json", "py")
    private val textExt = setOf("md", "txt", "rst", "csv", "lock", "svg")
    private val mustHaveContent = setOf("kt", "kts", "java", "gradle", "js", "mjs", "cjs", "jsx", "ts", "tsx", "c", "cpp", "h", "cs", "go", "rs", "swift", "php", "dart", "css", "json", "xml", "html", "htm", "py", "yml", "yaml", "toml", "properties")

    private class Cfg(val slash: Boolean, val block: Boolean, val hash: Boolean, val sq: Boolean, val bt: Boolean, val regex: Boolean, val triple: Boolean)

    private fun cfgFor(ext: String): Cfg = when (ext) {
        "json" -> Cfg(false, false, false, false, false, false, false)
        "css", "scss" -> Cfg(false, true, false, true, false, false, false)
        "py" -> Cfg(false, false, true, true, false, false, true)
        "js", "mjs", "cjs", "jsx", "ts", "tsx" -> Cfg(true, true, false, true, true, true, false)
        "gradle", "php", "dart", "scala" -> Cfg(true, true, false, true, false, false, true)
        "go" -> Cfg(true, true, false, false, true, false, false)
        else -> Cfg(true, true, false, false, false, false, true)
    }

    internal fun unclosed(text: String, ext: String): Boolean {
        val cfg = cfgFor(ext)
        val stack = ArrayList<Char>()
        val n = text.length
        var i = 0
        var prev = '\n'
        while (i < n) {
            val c = text[i]
            val d = if (i + 1 < n) text[i + 1] else '\u0000'
            if (cfg.slash && c == '/' && d == '/') {
                while (i < n && text[i] != '\n') i++
                continue
            }
            if (cfg.block && c == '/' && d == '*') {
                val e = text.indexOf("*/", i + 2)
                if (e < 0) return true
                i = e + 2
                continue
            }
            if (cfg.hash && c == '#') {
                while (i < n && text[i] != '\n') i++
                continue
            }
            if (cfg.triple && (c == '"' || c == '\'') && text.startsWith("" + c + c + c, i)) {
                val e = text.indexOf("" + c + c + c, i + 3)
                if (e < 0) return true
                i = e + 3
                prev = '"'
                continue
            }
            if (c == '"' || (c == '\'' && cfg.sq) || (c == '`' && cfg.bt)) {
                i++
                while (i < n) {
                    val ch = text[i]
                    if (ch == '\\') {
                        i += 2
                        continue
                    }
                    if (ch == c) break
                    if (ch == '\n' && c != '`') break
                    i++
                }
                i++
                prev = '"'
                continue
            }
            if (c == '\'' && !cfg.sq) {
                if (d == '\\') {
                    val e = text.indexOf('\'', i + 2)
                    i = if (e in 0..(i + 10)) e + 1 else i + 1
                } else if (i + 2 < n && text[i + 2] == '\'') {
                    i += 3
                } else {
                    i++
                }
                prev = '"'
                continue
            }
            if (cfg.regex && c == '/' && d != '/' && d != '*' && "(,=:[!&|?{};\n".indexOf(prev) >= 0) {
                i++
                var inClass = false
                while (i < n && text[i] != '\n') {
                    val ch = text[i]
                    if (ch == '\\') {
                        i += 2
                        continue
                    }
                    if (ch == '[') inClass = true else if (ch == ']') inClass = false else if (ch == '/' && !inClass) break
                    i++
                }
                i++
                prev = '"'
                continue
            }
            when (c) {
                '{', '[', '(' -> stack.add(c)
                '}' -> if (stack.isNotEmpty() && stack.last() == '{') stack.removeAt(stack.lastIndex)
                ']' -> if (stack.isNotEmpty() && stack.last() == '[') stack.removeAt(stack.lastIndex)
                ')' -> if (stack.isNotEmpty() && stack.last() == '(') stack.removeAt(stack.lastIndex)
            }
            if (!c.isWhitespace()) prev = c
            i++
        }
        return stack.isNotEmpty()
    }

    private val phrase = Regex(
        "(?i)(\\.{3}|\u2026)\\s*(rest|remaining|existing|other|same|more|code|omitted|unchanged|truncated)|" +
            "(rest|remainder|remaining) of (the )?(code|file|class|method|methods|function|functions|implementation|content|layout|xml)|" +
            "(same|unchanged) as (before|above|previous|original)|(your|add|insert|put) (the )?(code|implementation|logic) here|code (goes|omitted) here|" +
            "\u0628\u0627\u0642\u064A \u0627\u0644\u0643\u0648\u062F|\u0628\u0642\u064A\u0629 \u0627\u0644\u0643\u0648\u062F|\u0628\u0627\u0642\u064A \u0627\u0644\u0645\u0644\u0641|\u0628\u0642\u064A\u0629 \u0627\u0644\u0645\u0644\u0641|\u0643\u0645\u0627 \u0641\u064A \u0627\u0644\u0633\u0627\u0628\u0642|\u0646\u0641\u0633 \u0627\u0644\u0643\u0648\u062F"
    )
    private val dotsOnly = Regex("^\\s*(//|#|--|/\\*|\\*|<!--)?\\s*(\\.{3}|\u2026)\\s*(\\*/|-->)?\\s*$")
    private val commentMark = Regex("//|#|<!--|/\\*|^\\s*\\*|--")

    private val stubRe = Regex("^\\[(?:saved|written|omitted|content|file)[^\\]]*]$|^Wrote .+ \\(\\d+ chars\\)\\.?$", RegexOption.IGNORE_CASE)

    fun isStub(text: String): Boolean = stubRe.matches(text.trim())

    private fun placeholder(f: CodeFile): String? {
        if (isStub(f.content)) return "content is only a placeholder marker, not the real file"
        val e = ext(f.path)
        if (e in textExt || e.isEmpty()) return null
        var n = 0
        for (line in f.content.lineSequence()) {
            val hit = (e != "py" && e != "sh" && e != "yml" && e != "yaml" && dotsOnly.matches(line)) ||
                (commentMark.containsMatchIn(line) && phrase.containsMatchIn(line))
            if (hit) n++
        }
        return if (n > 0) "contains $n placeholder line(s) instead of real code" else null
    }

    private fun brokenReason(f: CodeFile): String? {
        val e = ext(f.path)
        val t = f.content
        if (t.isBlank()) return null
        if (e == "xml") {
            val msg = XmlCheck.check(t) ?: return null
            return if (msg.startsWith("DOCTYPE")) null else "invalid xml, probably cut off: $msg"
        }
        if ((e == "html" || e == "htm") && t.contains("<html", true) && !t.contains("</html>", true)) return "missing the closing </html>, the file is cut off"
        if (e in braceExt && unclosed(t, e)) return "ends with unclosed brackets or strings, the file is cut off"
        return null
    }

    private val binaryExt = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "woff", "woff2", "ttf", "otf", "mp3", "mp4", "wav", "ogg", "pdf", "zip", "jar")

    fun cutReason(path: String, content: String): String? = brokenReason(CodeFile(path, content))

    private val emptyOk = setOf("__init__.py", ".gitignore", ".gitkeep", ".keep", "proguard-rules.pro", ".nojekyll")

    fun issues(files: List<CodeFile>, planned: List<String> = emptyList(), cutPath: String? = null): List<ProjectIssue> {
        val out = ArrayList<ProjectIssue>()
        val real = files.filter { it.attachId == null }
        cutPath?.let { out.add(ProjectIssue(IssueKind.CUT_OFF, it, "the reply stopped in the middle of this file")) }
        for (f in real) {
            val n = name(f.path)
            if (!f.content.isBlank() && !isStub(f.content) && f.content.trim().length < 6 && n !in emptyOk && ext(f.path) in mustHaveContent) {
                out.add(ProjectIssue(IssueKind.EMPTY, f.path, "file has almost no content"))
                continue
            }
            if (f.content.isBlank()) {
                if (n !in emptyOk && ext(f.path) in mustHaveContent) out.add(ProjectIssue(IssueKind.EMPTY, f.path, "file is empty"))
                continue
            }
            brokenReason(f)?.let { if (f.path != cutPath) out.add(ProjectIssue(IssueKind.TRUNCATED, f.path, it)) }
            placeholder(f)?.let { out.add(ProjectIssue(IssueKind.PLACEHOLDER, f.path, it)) }
        }
        val have = real.map { it.path }.toSet()
        for (p in planned) if (p !in have) out.add(ProjectIssue(IssueKind.PLANNED_MISSING, p, "listed in the plan but never written"))
        if (real.any { it.path.endsWith("AndroidManifest.xml") }) android(real, out)
        web(real, out)
        node(real, out)
        python(real, out)
        val seen = HashSet<String>()
        return out.filter { seen.add(it.kind.name + "|" + it.path + "|" + it.detail) }.take(MAX_ISSUES)
    }

    fun review(markdown: String): ProjectReview {
        val rep = CodeFiles.extractReport(markdown, true)
        val last = if (rep.truncated) rep.files.lastOrNull()?.path else null
        val plan = planned(markdown)
        return ProjectReview(rep.files, issues(rep.files, plan, last), plan)
    }

    fun fixPrompt(issues: List<ProjectIssue>): String {
        val sb = StringBuilder()
        sb.append("The project is not complete yet. Fix only the problems below. ")
            .append("For every file named here write the whole file again, complete from the first line to the last line, in the same format: the line File: path, then one fenced code block. ")
            .append("Write each missing file in full. Do not repeat files that are not listed. No explanations, no placeholders. ")
            .append("After the last file end with the same [[EXPORT_ZIP:name.zip]] line.\nProblems:\n")
        issues.forEach { sb.append("- ").append(it.path).append(": ").append(it.detail).append('\n') }
        return sb.toString().trimEnd()
    }

    fun summary(issues: List<ProjectIssue>, limit: Int = 6): String =
        issues.take(limit).joinToString("\n") { it.path + ": " + it.detail }

    private fun android(files: List<CodeFile>, out: MutableList<ProjectIssue>) {
        val byPath = files.associateBy { it.path }
        fun find(vararg n: String): CodeFile? = n.firstNotNullOfOrNull { byPath[it] }
        val settings = find("settings.gradle.kts", "settings.gradle")
        val rootBuild = find("build.gradle.kts", "build.gradle")
        val gradleFiles = files.filter { it.path.endsWith(".gradle") || it.path.endsWith(".gradle.kts") || it.path.endsWith(".toml") }
        val gradleAll = gradleFiles.joinToString("\n") { it.content }
        val appBuild = gradleFiles.firstOrNull { name(it.path).startsWith("build.gradle") && it.content.contains("com.android.application") && !it.content.contains("apply false") }
        val module = appBuild?.path?.substringBeforeLast('/', "") ?: "app"
        fun at(p: String) = if (module.isEmpty()) p else "$module/$p"
        if (settings == null) out.add(ProjectIssue(IssueKind.MISSING_FILE, "settings.gradle.kts", "settings file is missing (rootProject.name, include(\":app\"), repositories google() and mavenCentral())"))
        else {
            val st = settings.content
            if (!st.contains("include")) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, settings.path, "does not include the app module"))
            if (!st.contains("google()") && rootBuild?.content?.contains("google()") != true) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, settings.path, "repositories google() and mavenCentral() are missing (pluginManagement and dependencyResolutionManagement)"))
        }
        if (rootBuild == null) out.add(ProjectIssue(IssueKind.MISSING_FILE, "build.gradle.kts", "root build file with plugin versions is missing"))
        if (appBuild == null) out.add(ProjectIssue(IssueKind.MISSING_FILE, "app/build.gradle.kts", "app module build file with com.android.application is missing"))
        val usesAndroidx = gradleAll.contains("androidx") || gradleAll.contains("material") ||
            files.any { (it.path.endsWith(".kt") || it.path.endsWith(".java")) && it.content.contains("import androidx.") }
        val props = byPath["gradle.properties"]
        if (props == null && usesAndroidx) out.add(ProjectIssue(IssueKind.MISSING_FILE, "gradle.properties", "missing, it must contain android.useAndroidX=true"))
        else if (props != null && usesAndroidx && !props.content.contains("android.useAndroidX")) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, "gradle.properties", "android.useAndroidX=true is missing"))
        val agp = Regex("com\\.android\\.(?:application|library)[\"']?\\)?\\s*version\\s*[\"'](\\d+)\\.").find(gradleAll)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("com\\.android\\.tools\\.build:gradle:(\\d+)\\.").find(gradleAll)?.groupValues?.get(1)?.toIntOrNull()
        if (appBuild != null && (agp ?: 8) >= 8 && !appBuild.content.contains("namespace")) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, appBuild.path, "namespace is missing in the android block (required by Android Gradle Plugin 8 and newer)"))
        if (appBuild != null && Regex("proguard-rules\\.pro").containsMatchIn(appBuild.content) && byPath[at("proguard-rules.pro")] == null) {
            out.add(ProjectIssue(IssueKind.MISSING_FILE, at("proguard-rules.pro"), "referenced by the build file but not written"))
        }
        val composeOn = Regex("compose\\s*(=\\s*)?true").containsMatchIn(gradleAll)
        if (composeOn) {
            val km = Regex("org\\.jetbrains\\.kotlin\\.android[\"']?\\)?\\s*version\\s*[\"'](\\d+)\\.").find(gradleAll)?.groupValues?.get(1)?.toIntOrNull()
            if (km != null && km >= 2 && !gradleAll.contains("plugin.compose")) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, appBuild?.path ?: "app/build.gradle.kts", "Kotlin 2 with Compose needs the org.jetbrains.kotlin.plugin.compose plugin in the root and app build files"))
            if (km != null && km < 2 && !gradleAll.contains("kotlinCompilerExtensionVersion")) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, appBuild?.path ?: "app/build.gradle.kts", "composeOptions kotlinCompilerExtensionVersion is missing"))
        }
        val sources = files.filter { it.path.endsWith(".kt") || it.path.endsWith(".java") }
        val xmls = files.filter { it.path.endsWith(".xml") }
        val pkg = Regex("namespace\\s*=?\\s*[\"']([\\w.]+)[\"']").find(gradleAll)?.groupValues?.get(1)
            ?: Regex("applicationId\\s*=?\\s*[\"']([\\w.]+)[\"']").find(gradleAll)?.groupValues?.get(1)
        val manifest = files.first { it.path.endsWith("AndroidManifest.xml") }
        val declNames = HashSet<String>()
        val declRe = Regex("\\b(?:class|object|interface|enum|record|typealias|val|var|fun)\\s+(?:<[^>]*>\\s*)?(?:[\\w.]+\\.)?([A-Za-z_][A-Za-z0-9_]*)")
        sources.forEach { f -> declRe.findAll(f.content).forEach { declNames.add(it.groupValues[1]) } }
        Regex("<(?:activity|service|receiver|provider)[^>]*?android:name\\s*=\\s*\"([^\"]+)\"").findAll(manifest.content).forEach { m ->
            val raw = m.groupValues[1]
            val full = when {
                raw.startsWith(".") && pkg != null -> pkg + raw
                !raw.contains('.') && pkg != null -> "$pkg.$raw"
                else -> raw
            }
            if (pkg != null && full.startsWith(pkg)) {
                val simple = full.substringAfterLast('.')
                if (simple !in declNames) {
                    out.add(ProjectIssue(IssueKind.MISSING_REF, manifest.path, "declares $raw but no class $simple was written"))
                }
            }
        }
        val deps = listOf(
            Triple("androidx.constraintlayout", "constraintlayout", listOf("material")),
            Triple("androidx.recyclerview", "recyclerview", listOf("material")),
            Triple("androidx.cardview", "cardview", listOf("material")),
            Triple("androidx.viewpager2", "viewpager2", listOf("material")),
            Triple("androidx.appcompat", "appcompat", listOf("material")),
            Triple("androidx.swiperefreshlayout", "swiperefreshlayout", emptyList()),
            Triple("androidx.compose.material3", "material3", emptyList()),
            Triple("androidx.compose.material.icons", "material-icons", emptyList()),
            Triple("androidx.activity.compose", "activity-compose", emptyList()),
            Triple("androidx.navigation", "navigation", emptyList()),
            Triple("androidx.room", "androidx.room", emptyList()),
            Triple("androidx.work", "androidx.work", emptyList()),
            Triple("androidx.datastore", "datastore", emptyList()),
            Triple("androidx.media3", "media3", emptyList()),
            Triple("com.google.android.material", "com.google.android.material:material", emptyList()),
            Triple("okhttp3", "okhttp", emptyList()),
            Triple("com.google.gson", "gson", emptyList()),
            Triple("kotlinx.serialization", "serialization", emptyList()),
            Triple("coil", "coil", emptyList()),
            Triple("retrofit2", "retrofit", emptyList()),
            Triple("com.google.firebase", "firebase", emptyList()),
            Triple("org.jsoup", "jsoup", emptyList()),
            Triple("com.squareup.moshi", "moshi", emptyList()),
            Triple("com.bumptech.glide", "glide", emptyList())
        )
        val seenDep = HashSet<String>()
        fun checkDep(use: String, where: String) {
            for ((prefix, key, alt) in deps) {
                if (!(use == prefix || use.startsWith("$prefix.") || use.startsWith("$prefix:"))) continue
                if (gradleAll.contains(key) || alt.any { gradleAll.contains(it) }) return
                if (seenDep.add(key)) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, appBuild?.path ?: "app/build.gradle.kts", "dependency for $prefix is missing (used in $where)"))
                return
            }
        }
        if (appBuild != null && !gradleAll.contains("libs.")) {
            val importRe = Regex("^\\s*import\\s+([\\w.]+)", RegexOption.MULTILINE)
            sources.forEach { f -> importRe.findAll(f.content).forEach { checkDep(it.groupValues[1], f.path) } }
            xmls.filter { it.path.contains("/res/layout") || it.path.contains("res/layout") }.forEach { f ->
                Regex("<([a-z][\\w]*(?:\\.[\\w]+)+)").findAll(f.content).forEach { checkDep(it.groupValues[1], f.path) }
            }
        }
        val themeUse = xmls.any { Regex("parent\\s*=\\s*\"Theme\\.(MaterialComponents|Material3)").containsMatchIn(it.content) }
        if (themeUse && appBuild != null && !gradleAll.contains("com.google.android.material:material")) {
            out.add(ProjectIssue(IssueKind.MISSING_CONFIG, appBuild.path, "a Material theme is used but com.google.android.material:material is not a dependency"))
        }
        val defined = HashSet<String>()
        val resPath = Regex("(?:^|/)res/([a-z0-9\\-]+)/([^/]+)$")
        val valueItem = Regex("<(string|color|style|dimen|integer|bool|plurals|string-array|integer-array|array|attr)\\s[^>]*?name\\s*=\\s*\"([^\"]+)\"")
        val typedItem1 = Regex("<item\\s[^>]*?name\\s*=\\s*\"([^\"]+)\"[^>]*?type\\s*=\\s*\"([a-z]+)\"")
        val typedItem2 = Regex("<item\\s[^>]*?type\\s*=\\s*\"([a-z]+)\"[^>]*?name\\s*=\\s*\"([^\"]+)\"")
        fun norm(s: String) = s.replace('.', '_')
        var hasRes = false
        for (f in files) {
            val m = resPath.find(f.path) ?: continue
            hasRes = true
            val dir = m.groupValues[1].substringBefore('-')
            if (dir == "values") {
                valueItem.findAll(f.content).forEach { v ->
                    val t = when (v.groupValues[1]) {
                        "string-array", "integer-array" -> "array"
                        else -> v.groupValues[1]
                    }
                    defined.add(t + "/" + norm(v.groupValues[2]))
                }
                typedItem1.findAll(f.content).forEach { defined.add(it.groupValues[2] + "/" + norm(it.groupValues[1])) }
                typedItem2.findAll(f.content).forEach { defined.add(it.groupValues[1] + "/" + norm(it.groupValues[2])) }
            } else {
                defined.add(dir + "/" + norm(m.groupValues[2].substringBefore('.')))
            }
        }
        Regex("@\\+id/([A-Za-z0-9_.]+)").findAll(xmls.joinToString("\n") { it.content }).forEach { defined.add("id/" + norm(it.groupValues[1])) }
        fun has(type: String, n: String): Boolean {
            val k = norm(n)
            return when (type) {
                "drawable" -> "drawable/$k" in defined || "color/$k" in defined || "mipmap/$k" in defined
                "mipmap" -> "mipmap/$k" in defined || "drawable/$k" in defined
                else -> "$type/$k" in defined
            }
        }
        val missing = LinkedHashMap<String, String>()
        val xmlRef = Regex("@(?!android:)(\\+?)(string|color|style|drawable|mipmap|layout|dimen|xml|anim|menu|raw|font|integer|bool|array|plurals|animator|navigation|id|interpolator|transition)/([A-Za-z0-9_.]+)")
        for (f in xmls) {
            xmlRef.findAll(f.content).forEach { m ->
                if (m.groupValues[1] == "+") return@forEach
                val t = m.groupValues[2]
                val n = m.groupValues[3]
                if (!has(t, n)) missing.putIfAbsent("$t/$n", f.path)
            }
        }
        val codeRef = Regex("(?<![\\w.])R\\.(string|color|style|drawable|mipmap|layout|dimen|xml|anim|menu|raw|id|array|integer|bool|plurals|font)\\.([A-Za-z0-9_]+)")
        for (f in sources) {
            codeRef.findAll(f.content).forEach { m ->
                val t = m.groupValues[1]
                val n = m.groupValues[2]
                if (!has(t, n)) missing.putIfAbsent("$t/$n", f.path)
            }
        }
        if (hasRes || missing.isNotEmpty()) {
            missing.entries.forEach { (k, where) ->
                val t = k.substringBefore('/')
                val hint = when (t) {
                    "color" -> "define it in res/values/colors.xml"
                    "string" -> "define it in res/values/strings.xml"
                    "style" -> "define it in res/values/themes.xml"
                    "mipmap" -> "write the launcher icon (res/mipmap-anydpi-v26/ic_launcher.xml) or remove the reference"
                    "layout", "drawable", "menu", "xml", "anim" -> "write res/$t/${k.substringAfter('/')}.xml"
                    else -> "define it"
                }
                out.add(ProjectIssue(IssueKind.MISSING_RESOURCE, where, "@$k is used but not defined; $hint"))
            }
        }
        if (pkg != null) {
            val importRe = Regex("^\\s*import\\s+(" + Regex.escape(pkg) + "[\\w.]*)\\.([A-Z][A-Za-z0-9_]*)\\s*;?\\s*$", RegexOption.MULTILINE)
            val seenImp = HashSet<String>()
            sources.forEach { f ->
                importRe.findAll(f.content).forEach { m ->
                    val simple = m.groupValues[2]
                    if (simple == "R" || simple == "BuildConfig" || simple.endsWith("Binding")) return@forEach
                    if (simple !in declNames && seenImp.add(simple)) {
                        out.add(ProjectIssue(IssueKind.MISSING_REF, f.path, "imports ${m.groupValues[1]}.$simple but that class was never written"))
                    }
                }
            }
        }
    }

    private fun resolveRel(base: String, ref: String): String {
        val parts = ArrayList<String>()
        val dir = if (ref.startsWith("/")) "" else base.substringBeforeLast('/', "")
        (if (dir.isEmpty()) emptyList() else dir.split('/')).forEach { parts.add(it) }
        ref.trimStart('/').split('/').forEach { s ->
            when (s) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(s)
            }
        }
        return parts.joinToString("/")
    }

    private fun web(files: List<CodeFile>, out: MutableList<ProjectIssue>) {
        val have = files.map { it.path }.toSet()
        val ref = Regex("(?:src|href)\\s*=\\s*\"([^\"#?:{}]+\\.[A-Za-z0-9]{1,5})\"")
        files.filter { (ext(it.path) == "html" || ext(it.path) == "htm") && !it.path.contains("templates/") }.forEach { f ->
            ref.findAll(f.content).forEach { m ->
                val r = m.groupValues[1]
                if (r.startsWith("//") || r.contains("://") || ext(r) in binaryExt) return@forEach
                val target = resolveRel(f.path, r)
                if (target !in have && !have.any { it.endsWith("/$target") && f.path.contains("public/") }) {
                    out.add(ProjectIssue(IssueKind.MISSING_REF, f.path, "links to $r but that file was never written"))
                }
            }
        }
    }

    private val nodeBuiltin = setOf("fs", "path", "http", "https", "os", "url", "util", "crypto", "events", "stream", "child_process", "readline", "zlib", "net", "assert", "buffer", "querystring", "timers", "tls", "dns", "cluster", "worker_threads", "perf_hooks", "process", "module", "vm", "string_decoder")

    private fun node(files: List<CodeFile>, out: MutableList<ProjectIssue>) {
        val have = files.map { it.path }.toSet()
        val js = files.filter { ext(it.path) in setOf("js", "mjs", "cjs", "ts", "tsx", "jsx") && !it.path.contains("node_modules/") }
        val pkgJson = files.firstOrNull { it.path == "package.json" }
        if (js.isEmpty() && pkgJson == null) return
        val relRe = Regex("(?:require\\(|from\\s+|import\\s+)[\"'](\\.{1,2}/[^\"']+)[\"']")
        val extList = listOf("", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".json", "/index.js", "/index.ts", "/index.tsx", "/index.jsx")
        js.forEach { f ->
            relRe.findAll(f.content).forEach { m ->
                if (ext(m.groupValues[1]) in binaryExt) return@forEach
                val target = resolveRel(f.path, m.groupValues[1])
                if (extList.none { (target + it) in have }) out.add(ProjectIssue(IssueKind.MISSING_REF, f.path, "imports ${m.groupValues[1]} but that file was never written"))
            }
        }
        if (pkgJson != null) {
            Regex("\"main\"\\s*:\\s*\"([^\"]+)\"").find(pkgJson.content)?.groupValues?.get(1)?.let { mainFile ->
                val t = resolveRel("package.json", mainFile)
                if (extList.none { (t + it) in have }) out.add(ProjectIssue(IssueKind.MISSING_REF, "package.json", "main points to $mainFile which was never written"))
            }
            Regex("\"start\"\\s*:\\s*\"node\\s+([^\"\\s]+)").find(pkgJson.content)?.groupValues?.get(1)?.let { startFile ->
                val t = resolveRel("package.json", startFile)
                if (extList.none { (t + it) in have }) out.add(ProjectIssue(IssueKind.MISSING_REF, "package.json", "start script runs $startFile which was never written"))
            }
        }
        val usesRequire = js.any { it.content.contains("require(") || Regex("^\\s*import .* from ", RegexOption.MULTILINE).containsMatchIn(it.content) }
        if (pkgJson == null && usesRequire && js.any { f -> Regex("(?:require\\(|from\\s+)[\"']([A-Za-z@][^\"'./]*)").findAll(f.content).any { it.groupValues[1].removePrefix("node:") !in nodeBuiltin } }) {
            out.add(ProjectIssue(IssueKind.MISSING_FILE, "package.json", "missing although the code imports npm packages"))
        } else if (pkgJson != null) {
            val used = LinkedHashSet<String>()
            js.forEach { f ->
                Regex("(?:require\\(|from\\s+|import\\s+)[\"']([A-Za-z@][^\"']*)[\"']").findAll(f.content).forEach { m ->
                    val raw = m.groupValues[1].removePrefix("node:")
                    val pkg = if (raw.startsWith("@")) raw.split('/').take(2).joinToString("/") else raw.substringBefore('/')
                    if (pkg !in nodeBuiltin) used.add(pkg)
                }
            }
            used.forEach { p -> if (!pkgJson.content.contains("\"$p\"")) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, "package.json", "dependency $p is used in the code but not listed")) }
        }
    }

    private val pyThird = mapOf(
        "flask" to "Flask", "fastapi" to "fastapi", "django" to "Django", "requests" to "requests", "numpy" to "numpy", "pandas" to "pandas",
        "aiohttp" to "aiohttp", "httpx" to "httpx", "pydantic" to "pydantic", "sqlalchemy" to "SQLAlchemy", "uvicorn" to "uvicorn", "bs4" to "beautifulsoup4"
    )

    private fun python(files: List<CodeFile>, out: MutableList<ProjectIssue>) {
        val py = files.filter { ext(it.path) == "py" }
        if (py.isEmpty()) return
        val reqs = files.firstOrNull { name(it.path) == "requirements.txt" || name(it.path) == "pyproject.toml" || name(it.path) == "Pipfile" }
        val used = LinkedHashSet<String>()
        py.forEach { f ->
            Regex("^\\s*(?:from|import)\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.MULTILINE).findAll(f.content).forEach { m ->
                if (m.groupValues[1] in pyThird.keys) used.add(m.groupValues[1])
            }
        }
        if (used.isEmpty()) return
        if (reqs == null) out.add(ProjectIssue(IssueKind.MISSING_FILE, "requirements.txt", "missing although the code imports ${used.joinToString(", ")}"))
        else used.forEach { u ->
            val dist = pyThird.getValue(u)
            if (!reqs.content.contains(dist, true) && !reqs.content.contains(u, true)) out.add(ProjectIssue(IssueKind.MISSING_CONFIG, reqs.path, "$dist is imported but not listed"))
        }
    }
}
