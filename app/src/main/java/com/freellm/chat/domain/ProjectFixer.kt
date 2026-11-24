package com.freellm.chat.domain

data class FixResult(val files: List<CodeFile>, val added: List<String>, val changed: List<String>)

object ProjectFixer {
    private const val AGP = "8.7.3"
    private const val KOTLIN = "2.0.21"
    private const val GRADLE = "8.10.2"

    private const val XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"

    private val workflow = """
name: Build
on:
  push:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: $GRADLE
      - run: gradle assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
""".trimStart('\n')

    private val npmVersions = mapOf(
        "express" to "^4.21.1", "cors" to "^2.8.5", "dotenv" to "^16.4.5", "ws" to "^8.18.0", "axios" to "^1.7.7",
        "socket.io" to "^4.8.0", "body-parser" to "^1.20.3", "mongoose" to "^8.7.0", "better-sqlite3" to "^11.3.0",
        "sqlite3" to "^5.1.7", "morgan" to "^1.10.0", "helmet" to "^8.0.0", "jsonwebtoken" to "^9.0.2", "bcryptjs" to "^2.4.3",
        "multer" to "^1.4.5-lts.1", "uuid" to "^10.0.0", "cheerio" to "^1.0.0", "node-fetch" to "^3.3.2", "commander" to "^12.1.0",
        "chalk" to "^5.3.0", "react" to "^18.3.1", "react-dom" to "^18.3.1", "vite" to "^5.4.8"
    )

    fun fix(input: List<CodeFile>): FixResult {
        val m = LinkedHashMap<String, String>()
        val other = ArrayList<CodeFile>()
        for (f in input) if (f.attachId == null) m[f.path] = f.content else other.add(f)
        val added = LinkedHashSet<String>()
        val changed = LinkedHashSet<String>()

        fun put(path: String, content: String) {
            val old = m[path]
            if (old == null) added.add(path) else if (old != content) changed.add(path)
            m[path] = content
        }

        fun stubbed(path: String): Boolean {
            val c = m[path] ?: return true
            return c.isBlank() || ProjectCheck.isStub(c) || c.trim().length < 6
        }

        if (m.keys.any { it.endsWith("AndroidManifest.xml") }) android(m, ::put, ::stubbed)
        node(m, ::put, ::stubbed)
        python(m, ::put, ::stubbed)

        val files = ArrayList<CodeFile>()
        for ((p, c) in m) files.add(CodeFile(p, c))
        files.addAll(other)
        return FixResult(files, added.toList(), changed.toList().filter { it !in added })
    }

    private fun gradleText(m: Map<String, String>): String =
        m.filterKeys { it.endsWith(".gradle") || it.endsWith(".gradle.kts") || it.endsWith(".toml") }.values.joinToString("\n")

    private fun removeBlock(text: String, name: String): String {
        val re = Regex("(?m)^\\s*" + name + "\\s*\\{")
        var t = text
        while (true) {
            val m = re.find(t) ?: return t
            var i = m.range.last
            var depth = 1
            i++
            while (i < t.length && depth > 0) {
                when (t[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            }
            if (depth != 0) return t
            t = t.substring(0, m.range.first) + t.substring(i)
        }
    }

    private fun humanize(name: String): String =
        name.replace('_', ' ').replace('.', ' ').trim().replaceFirstChar { it.uppercase() }

    private fun colorFor(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("white") -> "#FFFFFF"
            n.contains("black") -> "#000000"
            n.contains("transparent") -> "#00000000"
            n.contains("dark") || n.contains("variant") -> "#3700B3"
            n.contains("secondary") || n.contains("accent") || n.contains("teal") -> "#03DAC5"
            n.contains("text") || n.contains("on_surface") -> "#212121"
            n.contains("background") || n.contains("surface") -> "#FFFFFF"
            n.contains("red") || n.contains("error") -> "#B00020"
            n.contains("green") -> "#2E7D32"
            n.contains("gray") || n.contains("grey") -> "#9E9E9E"
            else -> "#6200EE"
        }
    }

    private fun android(m: MutableMap<String, String>, put: (String, String) -> Unit, stubbed: (String) -> Boolean) {
        val manifestPath = m.keys.first { it.endsWith("AndroidManifest.xml") }
        val gradle0 = gradleText(m)
        val appPath = m.entries.firstOrNull {
            val n = it.key.substringAfterLast('/')
            n.startsWith("build.gradle") && it.value.contains("com.android.application") && !it.value.contains("apply false")
        }?.key
        val module = appPath?.substringBeforeLast('/', "") ?: "app"
        fun inModule(p: String) = if (module.isEmpty()) p else "$module/$p"
        val kts = appPath?.endsWith(".kts") ?: !m.containsKey("settings.gradle")
        val sources = m.filterKeys { it.endsWith(".kt") || it.endsWith(".java") }.values
        val appText = appPath?.let { m[it] }.orEmpty()
        val pkg = Regex("namespace\\s*=?\\s*[\"']([\\w.]+)[\"']").find(appText)?.groupValues?.get(1)
            ?: Regex("applicationId\\s*=?\\s*[\"']([\\w.]+)[\"']").find(appText)?.groupValues?.get(1)
            ?: Regex("package\\s*=\\s*\"([\\w.]+)\"").find(m[manifestPath].orEmpty())?.groupValues?.get(1)
            ?: sources.firstNotNullOfOrNull { Regex("^\\s*package\\s+([\\w.]+)", RegexOption.MULTILINE).find(it)?.groupValues?.get(1) }
            ?: "com.example.app"
        val settingsPath = if (m.containsKey("settings.gradle.kts")) "settings.gradle.kts" else if (m.containsKey("settings.gradle")) "settings.gradle" else if (kts) "settings.gradle.kts" else "settings.gradle"
        val rootPath = if (m.containsKey("build.gradle.kts")) "build.gradle.kts" else if (m.containsKey("build.gradle")) "build.gradle" else if (kts) "build.gradle.kts" else "build.gradle"
        val projectName = Regex("rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']").find(m[settingsPath].orEmpty())?.groupValues?.get(1)
            ?: pkg.substringAfterLast('.').ifEmpty { "app" }

        val repoBlocks = "pluginManagement {\n    repositories {\n        google()\n        mavenCentral()\n        gradlePluginPortal()\n    }\n}\n\n" +
            "dependencyResolutionManagement {\n    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)\n    repositories {\n        google()\n        mavenCentral()\n    }\n}\n\n"

        for (p in listOf("build.gradle.kts", "build.gradle", inModule("build.gradle.kts"), inModule("build.gradle")).distinct()) {
            val c = m[p] ?: continue
            if (c.contains("pluginManagement") || c.contains("dependencyResolutionManagement")) {
                var t = removeBlock(c, "pluginManagement")
                t = removeBlock(t, "dependencyResolutionManagement")
                if (t != c) put(p, t.trim('\n') + "\n")
            }
        }

        if (stubbed(settingsPath)) {
            put(settingsPath, repoBlocks + "rootProject.name = \"$projectName\"\ninclude(\":$module\")\n")
        } else {
            val st = m.getValue(settingsPath)
            var t = st
            if (!st.contains("pluginManagement") && !st.contains("dependencyResolutionManagement") && !st.contains("google()") && m[rootPath]?.contains("google()") != true) {
                t = repoBlocks + t
            }
            if (!Regex("\\binclude\\b").containsMatchIn(t)) t = t.trimEnd() + "\ninclude(\":$module\")\n"
            if (t != st) put(settingsPath, t)
        }

        val rootNow = m[rootPath]
        val usesKotlin = appText.contains("kotlin.android") || appText.contains("kotlin(\"android\")") || appText.contains("kotlin-android")
        val composeOn = Regex("compose\\s*(=\\s*)?true").containsMatchIn(appText)
        if (rootNow == null || stubbed(rootPath) || !Regex("plugins\\s*\\{").containsMatchIn(rootNow)) {
            if (rootNow == null || stubbed(rootPath)) {
                val sb = StringBuilder("plugins {\n")
                sb.append("    id(\"com.android.application\") version \"$AGP\" apply false\n")
                if (usesKotlin) sb.append("    id(\"org.jetbrains.kotlin.android\") version \"$KOTLIN\" apply false\n")
                if (usesKotlin && composeOn) sb.append("    id(\"org.jetbrains.kotlin.plugin.compose\") version \"$KOTLIN\" apply false\n")
                sb.append("}\n")
                put(rootPath, sb.toString())
                if (usesKotlin && composeOn && kts && appPath != null && !appText.contains("plugin.compose")) {
                    val fixed = Regex("(id\\(\"org\\.jetbrains\\.kotlin\\.android\"\\)|kotlin\\(\"android\"\\))").replace(appText) { it.value + "\n    id(\"org.jetbrains.kotlin.plugin.compose\")" }
                    if (fixed != appText) put(appPath, fixed)
                }
            }
        }

        val appNow = appPath?.let { m[it] }.orEmpty()
        if (appPath != null && !appNow.contains("namespace")) {
            val fixed = Regex("android\\s*\\{").replaceFirst(appNow) { it.value + "\n    namespace = \"$pkg\"" }
            if (fixed != appNow) put(appPath, fixed)
        }

        val text = gradleText(m)
        val usesAndroidx = text.contains("androidx") || text.contains("material") || sources.any { it.contains("import androidx.") }
        val props = m["gradle.properties"]
        if (props == null || stubbed("gradle.properties")) {
            put("gradle.properties", "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\nandroid.nonTransitiveRClass=true\nkotlin.code.style=official\n")
        } else if (usesAndroidx && !props.contains("android.useAndroidX")) {
            put("gradle.properties", props.trimEnd() + "\nandroid.useAndroidX=true\n")
        }

        val proguardPath = inModule("proguard-rules.pro")
        if (Regex("proguard-rules\\.pro").containsMatchIn(appNow + text) && stubbed(proguardPath) || (m.containsKey(proguardPath) && stubbed(proguardPath))) {
            put(proguardPath, "-keep class kotlin.Metadata { *; }\n")
        }
        if (!m.keys.any { it == ".gitignore" }) put(".gitignore", "build/\n.gradle/\nlocal.properties\n.idea/\n*.iml\n.kotlin/\n*.apk\n")
        if (!m.keys.any { it.startsWith(".github/workflows/") }) put(".github/workflows/build.yml", workflow)

        val actRe = Regex("\\bclass\\s+(\\w+)\\b[^\\n{]*?:\\s*(?:AppCompatActivity|ComponentActivity|FragmentActivity|Activity)\\s*\\(|\\bclass\\s+(\\w+)\\s+extends\\s+(?:AppCompatActivity|ComponentActivity|FragmentActivity|Activity)\\b")
        val activities = ArrayList<String>()
        for ((path, content) in m) {
            if (!path.endsWith(".kt") && !path.endsWith(".java")) continue
            val filePkg = Regex("^\\s*package\\s+([\\w.]+)", RegexOption.MULTILINE).find(content)?.groupValues?.get(1) ?: pkg
            actRe.findAll(content).forEach { r ->
                val cls = r.groupValues[1].ifEmpty { r.groupValues[2] }
                activities.add(if (filePkg == pkg) ".$cls" else "$filePkg.$cls")
            }
        }
        val netUse = sources.any { it.contains("okhttp3") || it.contains("java.net.URL") || it.contains("HttpURLConnection") || it.contains("retrofit2") || it.contains("io.ktor") || it.contains("android.webkit.WebView") }
        val perm = "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
        if (stubbed(manifestPath)) {
            if (activities.isNotEmpty()) {
                val main = activities.firstOrNull { it.contains("Main") } ?: activities.first()
                val sb = StringBuilder(XML)
                sb.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
                if (netUse) sb.append(perm)
                sb.append("    <application\n        android:allowBackup=\"true\"\n        android:icon=\"@mipmap/ic_launcher\"\n        android:label=\"@string/app_name\"\n        android:supportsRtl=\"true\"\n        android:theme=\"@style/Theme.App\">\n")
                for (a in activities.distinct()) {
                    if (a == main) {
                        sb.append("        <activity android:name=\"$a\" android:exported=\"true\">\n            <intent-filter>\n                <action android:name=\"android.intent.action.MAIN\" />\n                <category android:name=\"android.intent.category.LAUNCHER\" />\n            </intent-filter>\n        </activity>\n")
                    } else sb.append("        <activity android:name=\"$a\" android:exported=\"false\" />\n")
                }
                sb.append("    </application>\n</manifest>\n")
                put(manifestPath, sb.toString())
            }
        } else {
            var man = m.getValue(manifestPath)
            val orig = man
            if (netUse && !man.contains("android.permission.INTERNET")) {
                man = Regex("(<manifest\\b[^>]*>)").replaceFirst(man) { it.value + "\n" + perm.trimEnd() }
            }
            val missingActs = activities.distinct().filter { a -> !Regex("android:name\\s*=\\s*\"[^\"]*" + Regex.escape(a.substringAfterLast('.')) + "\"").containsMatchIn(man) }
            if (missingActs.isNotEmpty() && man.contains("</application>")) {
                val decl = missingActs.joinToString("") { "        <activity android:name=\"$it\" android:exported=\"false\" />\n" }
                man = man.replaceFirst("</application>", decl + "    </application>")
            }
            if (man != orig) put(manifestPath, man)
        }

        val res = manifestPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "res" else "$it/res" }
        fun addValue(file: String, line: String) {
            val path = "$res/values/$file"
            val cur = m[path]
            if (cur == null || stubbed(path) || !cur.contains("</resources>")) {
                put(path, "$XML<resources>\n    $line\n</resources>\n")
            } else {
                val i = cur.lastIndexOf("</resources>")
                put(path, cur.substring(0, i).trimEnd() + "\n    $line\n" + cur.substring(i))
            }
        }
        fun hasValue(tag: String, n: String) = m.filterKeys { it.contains("/values") && it.endsWith(".xml") }.values.any { Regex("<$tag\\s[^>]*name\\s*=\\s*\"" + Regex.escape(n) + "\"").containsMatchIn(it) }

        val material = text.contains("com.google.android.material:material")
        val appcompat = text.contains("androidx.appcompat:appcompat")
        val themeParent = if (material) "Theme.MaterialComponents.DayNight.NoActionBar" else if (appcompat) "Theme.AppCompat.DayNight.NoActionBar" else "android:Theme.Material.Light.NoActionBar"

        val (missing, _) = ProjectCheck.missingResources(m.map { CodeFile(it.key, it.value) })
        var iconNeeded = false
        var roundNeeded = false
        for (key in missing.keys) {
            val t = key.substringBefore('/')
            val n = key.substringAfter('/')
            when {
                t == "mipmap" && n == "ic_launcher" -> iconNeeded = true
                t == "mipmap" && n == "ic_launcher_round" -> {
                    iconNeeded = true
                    roundNeeded = true
                }
                t == "drawable" && n == "ic_launcher_foreground" -> iconNeeded = true
                t == "color" && !hasValue("color", n) -> addValue("colors.xml", "<color name=\"$n\">${colorFor(n)}</color>")
                t == "string" && !hasValue("string", n) -> addValue("strings.xml", "<string name=\"$n\">${if (n == "app_name") projectName else humanize(n)}</string>")
                t == "dimen" && !hasValue("dimen", n) -> addValue("dimens.xml", "<dimen name=\"$n\">16dp</dimen>")
                t == "style" && !hasValue("style", n) -> addValue("themes.xml", "<style name=\"$n\" parent=\"$themeParent\" />")
            }
        }
        if (iconNeeded) {
            if (!hasValue("color", "ic_launcher_background")) addValue("colors.xml", "<color name=\"ic_launcher_background\">#3B82F6</color>")
            val adaptive = XML + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n    <background android:drawable=\"@color/ic_launcher_background\" />\n    <foreground android:drawable=\"@drawable/ic_launcher_foreground\" />\n</adaptive-icon>\n"
            if (!m.keys.any { it.contains("/mipmap") && it.substringAfterLast('/').substringBefore('.') == "ic_launcher" }) put("$res/mipmap-anydpi-v26/ic_launcher.xml", adaptive)
            if (roundNeeded && !m.keys.any { it.contains("/mipmap") && it.substringAfterLast('/').substringBefore('.') == "ic_launcher_round" }) put("$res/mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)
            if (!m.keys.any { it.contains("/drawable") && it.substringAfterLast('/').substringBefore('.') == "ic_launcher_foreground" }) {
                put(
                    "$res/drawable/ic_launcher_foreground.xml",
                    XML + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:width=\"108dp\"\n    android:height=\"108dp\"\n    android:viewportWidth=\"108\"\n    android:viewportHeight=\"108\">\n    <path\n        android:fillColor=\"#FFFFFF\"\n        android:pathData=\"M54,30 L78,54 L54,78 L30,54 Z\" />\n</vector>\n"
                )
            }
        }
    }

    private fun node(m: MutableMap<String, String>, put: (String, String) -> Unit, stubbed: (String) -> Boolean) {
        val files = m.map { CodeFile(it.key, it.value) }
        val deps = ProjectCheck.nodeDeps(files)
        val js = m.keys.filter { it.substringAfterLast('.') in setOf("js", "mjs", "cjs") && !it.contains("node_modules/") }
        if (js.isEmpty()) return
        if (m["package.json"] != null && !stubbed("package.json")) return
        if (deps.isEmpty() && m["package.json"] == null) return
        val main = listOf("server.js", "index.js", "app.js", "main.js", "src/index.js", "src/server.js").firstOrNull { it in m } ?: js.first()
        val sb = StringBuilder()
        sb.append("{\n  \"name\": \"project\",\n  \"version\": \"1.0.0\",\n  \"main\": \"").append(main).append("\",\n  \"scripts\": {\n    \"start\": \"node ").append(main).append("\"\n  }")
        if (deps.isNotEmpty()) {
            sb.append(",\n  \"dependencies\": {\n")
            sb.append(deps.joinToString(",\n") { "    \"$it\": \"" + (npmVersions[it] ?: "latest") + "\"" })
            sb.append("\n  }")
        }
        sb.append("\n}\n")
        put("package.json", sb.toString())
    }

    private fun python(m: MutableMap<String, String>, put: (String, String) -> Unit, stubbed: (String) -> Boolean) {
        val deps = ProjectCheck.pyDeps(m.map { CodeFile(it.key, it.value) })
        if (deps.isEmpty()) return
        val path = "requirements.txt"
        val cur = m[path]
        if (cur == null || stubbed(path)) {
            put(path, deps.joinToString("\n") + "\n")
            return
        }
        val missing = deps.filter { !cur.contains(it, true) }
        if (missing.isNotEmpty()) put(path, cur.trimEnd() + "\n" + missing.joinToString("\n") + "\n")
    }
}
