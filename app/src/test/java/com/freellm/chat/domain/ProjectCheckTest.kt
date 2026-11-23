package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCheckTest {
    private val fence = "```"

    private fun kinds(files: List<CodeFile>, planned: List<String> = emptyList()) = ProjectCheck.issues(files, planned).map { it.kind }

    @Test fun everyTemplateIsComplete() {
        for (t in ProjectTemplates.types) {
            val files = ProjectTemplates.files(t, "Demo App")!!
            assertEquals(t, emptyList<ProjectIssue>(), ProjectCheck.issues(files))
        }
    }

    @Test fun balancedKotlinPasses() {
        val src = "class A {\n    fun f(x: Int): String = \"\${x}{\" + '}'.toString()\n    // {\n}\n"
        assertFalse(ProjectCheck.unclosed(src, "kt"))
    }

    @Test fun cutKotlinIsDetected() {
        assertTrue(ProjectCheck.unclosed("class A {\n    fun f() {\n        println(1)\n", "kt"))
        assertTrue(ProjectCheck.unclosed("val s = \"\"\"abc", "kt"))
        assertTrue(ProjectCheck.unclosed("{\"a\": [1, 2", "json"))
        assertFalse(ProjectCheck.unclosed("{\"a\": [1, 2]}", "json"))
    }

    @Test fun jsRegexAndTemplatesDoNotConfuseTheScanner() {
        val js = "const r = /[{]+/g;\nconst t = `a\${b}c`;\nfunction f() { return r.test(t) }\n"
        assertFalse(ProjectCheck.unclosed(js, "js"))
    }

    @Test fun placeholderLinesAreFlagged() {
        val f = CodeFile("a/Main.kt", "fun main() {\n    // ... rest of the code\n}\n")
        assertTrue(IssueKind.PLACEHOLDER in kinds(listOf(f)))
        val ok = CodeFile("a/Main.kt", "fun main() {\n    println(\"hi\")\n}\n")
        assertTrue(kinds(listOf(ok)).isEmpty())
    }

    @Test fun cutFileAndMissingPlannedFileAreReported() {
        val files = listOf(CodeFile("src/a.kt", "class A {\n fun x() {\n"), CodeFile("src/b.kt", "class B\n"))
        val issues = ProjectCheck.issues(files, listOf("src/a.kt", "src/b.kt", "src/c.kt"))
        assertTrue(issues.any { it.kind == IssueKind.TRUNCATED && it.path == "src/a.kt" })
        assertTrue(issues.any { it.kind == IssueKind.PLANNED_MISSING && it.path == "src/c.kt" })
    }

    @Test fun planLineIsParsedAndStripped() {
        val text = "[[PLAN: a/b.kt, c.xml,\n d/e.gradle.kts]]\nFile: a/b.kt\n"
        assertEquals(listOf("a/b.kt", "c.xml", "d/e.gradle.kts"), ProjectCheck.planned(text))
        assertFalse(ProjectCheck.stripPlan(text).contains("PLAN"))
        assertFalse(CodeFiles.stripCommand("[[PLAN: a.kt, b.kt]]\nhello").contains("PLAN"))
    }

    @Test fun incompleteAndroidProjectIsCaught() {
        val files = listOf(
            CodeFile("settings.gradle", "rootProject.name = \"F\"\ninclude(\":app\")\n"),
            CodeFile("build.gradle", "plugins {\n    id 'com.android.application' version '8.2.0' apply false\n}\n"),
            CodeFile("app/build.gradle", "plugins {\n    id 'com.android.application'\n}\nandroid {\n    compileSdk = 34\n    buildTypes { release { proguardFiles 'proguard-rules.pro' } }\n}\ndependencies {\n    implementation \"androidx.appcompat:appcompat:1.7.0\"\n}\n"),
            CodeFile("app/src/main/AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n<application android:icon=\"@mipmap/ic_launcher\" android:theme=\"@style/Theme.F\">\n<activity android:name=\".MainActivity\" android:exported=\"true\"/>\n</application>\n</manifest>\n"),
            CodeFile("app/src/main/res/values/themes.xml", "<resources>\n<style name=\"Theme.F\" parent=\"Theme.AppCompat\">\n<item name=\"colorPrimary\">@color/purple_500</item>\n</style>\n</resources>\n")
        )
        val issues = ProjectCheck.issues(files)
        val text = issues.joinToString("\n") { it.path + ": " + it.detail }
        assertTrue(text, issues.any { it.kind == IssueKind.MISSING_RESOURCE && it.detail.contains("@color/purple_500") })
        assertTrue(text, issues.any { it.kind == IssueKind.MISSING_RESOURCE && it.detail.contains("@mipmap/ic_launcher") })
        assertTrue(text, issues.any { it.kind == IssueKind.MISSING_FILE && it.path == "gradle.properties" })
        assertTrue(text, issues.any { it.kind == IssueKind.MISSING_FILE && it.path == "app/proguard-rules.pro" })
        assertTrue(text, issues.any { it.kind == IssueKind.MISSING_CONFIG && it.detail.contains("namespace") })
        assertTrue(text, issues.any { it.kind == IssueKind.MISSING_CONFIG && it.path == "settings.gradle" })
    }

    @Test fun webReferencesAndNodePackagesAreChecked() {
        val files = listOf(
            CodeFile("index.html", "<html><head><link href=\"style.css\" rel=\"stylesheet\"></head><body><script src=\"app.js\"></script></body></html>\n"),
            CodeFile("server.js", "const express = require(\"express\");\nconst x = require(\"./lib/util\");\n")
        )
        val issues = ProjectCheck.issues(files)
        assertTrue(issues.any { it.path == "index.html" && it.detail.contains("style.css") })
        assertTrue(issues.any { it.path == "index.html" && it.detail.contains("app.js") })
        assertTrue(issues.any { it.path == "server.js" && it.detail.contains("./lib/util") })
        assertTrue(issues.any { it.kind == IssueKind.MISSING_FILE && it.path == "package.json" })
    }

    @Test fun reviewReadsReplyAndLaterRewriteReplacesEarlierFile() {
        val md = "[[PLAN: a.py, b.py]]\nFile: a.py\n${fence}python\ndef f(:\n$fence\nFile: a.py\n${fence}python\ndef f():\n    return 1\n$fence\nFile: b.py\n${fence}python\nprint(1)\n$fence\n"
        val r = ProjectCheck.review(md)
        assertEquals(listOf("a.py", "b.py"), r.files.map { it.path })
        assertEquals("def f():\n    return 1\n", r.files[0].content)
        assertTrue(r.issues.toString(), r.complete)
    }

    @Test fun unclosedLastFenceIsCutOff() {
        val md = "File: a.kt\n${fence}kotlin\nclass A {\n    fun x() {\n"
        val r = ProjectCheck.review(md)
        assertTrue(r.issues.any { it.kind == IssueKind.CUT_OFF && it.path == "a.kt" })
    }

    @Test fun cutToolBlockIsNotExecuted() {
        val cut = "<tool_call name=\"project_write\" path=\"a/Main.kt\">\nclass Main {\n    fun f() {\n"
        val p = ToolProtocol.parse(cut)
        assertTrue(p.calls.isEmpty())
        assertEquals(1, p.cut.size)
        val whole = "<tool_call name=\"project_write\" path=\"a/Main.kt\">\nclass Main\n"
        assertEquals(1, ToolProtocol.parse(whole).calls.size)
        assertNull(ProjectCheck.cutReason("a.kt", "class A\n"))
    }

    @Test fun projectTokenBudgetFitsTheWindow() {
        assertEquals(MaxTokensPlanner.PROJECT, MaxTokensPlanner.project(200000))
        assertTrue(MaxTokensPlanner.project(16000) in 4096..16000)
        assertEquals(MaxTokensPlanner.PROJECT, MaxTokensPlanner.project(0))
    }

    @Test fun compactWritesDropsFileBodies() {
        val raw = "<tool_call name=\"project_write\" path=\"a.kt\">\n" + "x".repeat(500) + "\n</tool_call>"
        val c = ToolRunner(Router(object : ChatTransport {
            override fun stream(target: Target, messages: List<ChatMsg>, maxTokens: Int?) = kotlinx.coroutines.flow.emptyFlow<String>()
        }, clock = { 0L }), object : ToolExecutor {
            override suspend fun execute(call: ToolCall, ctx: ToolContext) = ToolResult("")
        }).compactWrites(raw)
        assertFalse(c.contains("xxxx"))
        assertTrue(c.contains("project_write"))
    }
}
