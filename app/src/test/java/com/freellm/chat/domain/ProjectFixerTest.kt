package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFixerTest {
    private fun fixed(files: List<CodeFile>) = ProjectFixer.fix(files)

    private val activity = CodeFile(
        "app/src/main/java/com/example/flash/MainActivity.kt",
        "package com.example.flash\n\nimport android.os.Bundle\nimport androidx.appcompat.app.AppCompatActivity\n\nclass MainActivity : AppCompatActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        setContentView(R.layout.activity_main)\n        title = getString(R.string.app_name)\n    }\n}\n"
    )
    private val layout = CodeFile("app/src/main/res/layout/activity_main.xml", "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" />\n")
    private val appBuild = CodeFile(
        "app/build.gradle.kts",
        "plugins {\n    id(\"com.android.application\")\n    id(\"org.jetbrains.kotlin.android\")\n}\n\nandroid {\n    compileSdk = 35\n    defaultConfig {\n        applicationId = \"com.example.flash\"\n        minSdk = 26\n        targetSdk = 35\n    }\n    buildTypes {\n        release {\n            proguardFiles(getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\")\n        }\n    }\n}\n\ndependencies {\n    implementation(\"androidx.appcompat:appcompat:1.7.0\")\n    implementation(\"com.google.android.material:material:1.12.0\")\n}\n"
    )

    @Test fun stubbedAndroidSkeletonBecomesComplete() {
        val files = listOf(
            CodeFile("build.gradle.kts", "pluginManagement {\n    repositories { google() }\n}\nplugins {\n    id(\"com.android.application\") version \"8.5.0\" apply false\n    id(\"org.jetbrains.kotlin.android\") version \"1.9.23\" apply false\n}\n"),
            CodeFile("settings.gradle.kts", "[saved]\n"),
            appBuild,
            CodeFile("app/src/main/AndroidManifest.xml", "[saved]\n"),
            CodeFile("app/proguard-rules.pro", "[saved]\n"),
            CodeFile("gradle.properties", "[saved]\n"),
            activity,
            layout
        )
        val r = fixed(files)
        val issues = ProjectCheck.issues(r.files)
        assertEquals(issues.toString(), emptyList<ProjectIssue>(), issues)
        val paths = r.files.map { it.path }
        assertTrue(paths.contains("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"))
        assertTrue(paths.contains(".github/workflows/build.yml"))
        val settings = r.files.first { it.path == "settings.gradle.kts" }.content
        assertTrue(settings.contains("google()") && settings.contains("include(\":app\")"))
        assertFalse(r.files.first { it.path == "build.gradle.kts" }.content.contains("pluginManagement"))
        assertTrue(r.files.first { it.path.endsWith("build.gradle.kts") && it.path.startsWith("app/") }.content.contains("namespace = \"com.example.flash\""))
        val manifest = r.files.first { it.path.endsWith("AndroidManifest.xml") }.content
        assertTrue(manifest.contains(".MainActivity") && manifest.contains("LAUNCHER"))
    }

    @Test fun missingResourcesGetDefaults() {
        val man = CodeFile("app/src/main/AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n<application android:icon=\"@mipmap/ic_launcher\" android:roundIcon=\"@mipmap/ic_launcher_round\" android:label=\"@string/app_name\" android:theme=\"@style/Theme.Flash\">\n<activity android:name=\".MainActivity\" android:exported=\"true\" />\n</application>\n</manifest>\n")
        val themes = CodeFile("app/src/main/res/values/themes.xml", "<resources>\n<style name=\"Theme.Flash\" parent=\"Theme.MaterialComponents.DayNight.NoActionBar\">\n<item name=\"colorPrimary\">@color/purple_500</item>\n</style>\n</resources>\n")
        val r = fixed(listOf(man, themes, appBuild, activity, layout))
        val colors = r.files.first { it.path.endsWith("values/colors.xml") }.content
        assertTrue(colors.contains("purple_500") && colors.contains("ic_launcher_background"))
        assertTrue(r.files.any { it.path.endsWith("ic_launcher_round.xml") })
        val str = r.files.first { it.path.endsWith("values/strings.xml") }.content
        assertTrue(str.contains("app_name"))
        assertEquals(emptyList<ProjectIssue>(), ProjectCheck.issues(r.files))
    }

    @Test fun undeclaredActivityAndInternetPermissionAreAdded() {
        val man = CodeFile("app/src/main/AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n<application android:label=\"x\">\n</application>\n</manifest>\n")
        val net = CodeFile("app/src/main/java/com/example/flash/Net.kt", "package com.example.flash\n\nimport java.net.URL\n\nfun get(u: String) = URL(u).readText()\n")
        val r = fixed(listOf(man, appBuild, activity, net))
        val m = r.files.first { it.path.endsWith("AndroidManifest.xml") }.content
        assertTrue(m.contains("android.permission.INTERNET"))
        assertTrue(m.contains(".MainActivity"))
    }

    @Test fun nodeAndPythonDependenciesAreWritten() {
        val node = fixed(listOf(CodeFile("server.js", "const express = require(\"express\");\nconst fs = require(\"fs\");\n")))
        val pkg = node.files.first { it.path == "package.json" }.content
        assertTrue(pkg.contains("\"express\"") && pkg.contains("node server.js") && !pkg.contains("\"fs\""))
        val py = fixed(listOf(CodeFile("app.py", "from flask import Flask\nimport requests\n")))
        val req = py.files.first { it.path == "requirements.txt" }.content
        assertTrue(req.contains("Flask") && req.contains("requests"))
        assertEquals(emptyList<ProjectIssue>(), ProjectCheck.issues(py.files))
    }

    @Test fun completeProjectsAreLeftAlone() {
        for (t in ProjectTemplates.types) {
            val files = ProjectTemplates.files(t, "Demo App")!!
            val r = fixed(files)
            assertEquals(t, emptyList<String>(), r.changed)
            assertEquals(t, files.map { it.path }.toSet(), r.files.map { it.path }.toSet() - r.added.toSet())
        }
    }

    @Test fun reviewReportsAddedFiles() {
        val md = "File: server.js\n```js\nconst express = require(\"express\");\napp = express();\n```\n"
        val r = ProjectCheck.review(md)
        assertTrue(r.added.contains("package.json"))
    }
}
