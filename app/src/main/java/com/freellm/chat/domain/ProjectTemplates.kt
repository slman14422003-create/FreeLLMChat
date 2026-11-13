package com.freellm.chat.domain

object ProjectTemplates {
    val types = listOf("web-static", "node-express", "python-flask", "python-cli", "kotlin-cli", "android-compose")

    fun safeName(raw: String): String {
        val s = raw.trim().replace(Regex("[^A-Za-z0-9 _\\-]"), "").trim().take(40)
        return s.ifEmpty { "MyApp" }
    }

    fun files(type: String, rawName: String): List<CodeFile>? {
        val name = safeName(rawName)
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "app" }
        val list = when (type.trim().lowercase()) {
            "web-static" -> webStatic()
            "node-express" -> nodeExpress(slug)
            "python-flask" -> pythonFlask()
            "python-cli" -> pythonCli()
            "kotlin-cli" -> kotlinCli(slug)
            "android-compose" -> androidCompose(name)
            else -> return null
        }
        return list.map { CodeFile(it.path, it.content.replace("{{name}}", name)) }
    }

    private fun f(path: String, content: String) = CodeFile(path, content.trimStart('\n').trimEnd() + "\n")

    private fun webStatic() = listOf(
        f(
            "index.html",
            """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{{name}}</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <main>
    <h1>{{name}}</h1>
    <button id="btn">Click</button>
    <p id="out"></p>
  </main>
  <script src="app.js"></script>
</body>
</html>
"""
        ),
        f(
            "style.css",
            """
body { margin: 0; font-family: system-ui, sans-serif; background: #111; color: #eee; display: grid; place-items: center; min-height: 100vh; }
main { text-align: center; }
button { padding: 10px 22px; border: 0; border-radius: 999px; background: #3b82f6; color: #fff; font-size: 16px; }
"""
        ),
        f(
            "app.js",
            """
let count = 0;
document.getElementById("btn").addEventListener("click", () => {
  count += 1;
  document.getElementById("out").textContent = "Clicks: " + count;
});
"""
        )
    )

    private fun nodeExpress(slug: String) = listOf(
        f(
            "package.json",
            """
{
  "name": "$slug",
  "version": "1.0.0",
  "private": true,
  "main": "server.js",
  "scripts": { "start": "node server.js" },
  "dependencies": { "express": "^4.19.2" }
}
"""
        ),
        f(
            "server.js",
            """
const express = require("express");
const path = require("path");
const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));
app.get("/api/health", (req, res) => res.json({ ok: true }));
const port = process.env.PORT || 3000;
app.listen(port, () => console.log("Listening on " + port));
"""
        ),
        f("public/index.html", "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>{{name}}</title></head><body><h1>{{name}}</h1></body></html>"),
        f(".gitignore", "node_modules\n.env")
    )

    private fun pythonFlask() = listOf(
        f(
            "app.py",
            """
from flask import Flask, jsonify, render_template

app = Flask(__name__)


@app.route("/")
def index():
    return render_template("index.html", title="{{name}}")


@app.route("/api/health")
def health():
    return jsonify(ok=True)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
"""
        ),
        f("requirements.txt", "Flask>=3.0"),
        f("templates/index.html", "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>{{ title }}</title></head><body><h1>{{ title }}</h1></body></html>"),
        f(".gitignore", "__pycache__/\n.venv/\n*.pyc")
    )

    private fun pythonCli() = listOf(
        f(
            "main.py",
            """
import argparse


def greet(name: str) -> str:
    return "Hello, " + name + "!"


def main() -> None:
    parser = argparse.ArgumentParser(description="{{name}}")
    parser.add_argument("name", nargs="?", default="world")
    args = parser.parse_args()
    print(greet(args.name))


if __name__ == "__main__":
    main()
"""
        ),
        f(
            "tests/test_main.py",
            """
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from main import greet


def test_greet():
    assert greet("Ali") == "Hello, Ali!"
"""
        ),
        f("requirements.txt", "pytest>=8.0"),
        f(".gitignore", "__pycache__/\n.venv/\n*.pyc")
    )

    private fun kotlinCli(slug: String) = listOf(
        f("settings.gradle.kts", "rootProject.name = \"$slug\""),
        f(
            "build.gradle.kts",
            """
plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}

tasks.test {
    useJUnitPlatform()
}
"""
        ),
        f(
            "src/main/kotlin/Main.kt",
            """
fun greet(name: String): String = "Hello, " + name + "!"

fun main(args: Array<String>) {
    println(greet(args.firstOrNull() ?: "world"))
}
"""
        ),
        f(
            "src/test/kotlin/MainTest.kt",
            """
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun greets() {
        assertEquals("Hello, Ali!", greet("Ali"))
    }
}
"""
        ),
        f(".gitignore", "build/\n.gradle/\n.idea/")
    )

    private fun androidCompose(name: String) = listOf(
        f(
            "settings.gradle.kts",
            """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "$name"
include(":app")
"""
        ),
        f(
            "build.gradle.kts",
            """
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
"""
        ),
        f("gradle.properties", "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\nkotlin.code.style=official"),
        f(
            "app/build.gradle.kts",
            """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
"""
        ),
        f(
            "app/src/main/AndroidManifest.xml",
            """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""
        ),
        f(
            "app/src/main/java/com/example/app/MainActivity.kt",
            """
package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("{{name}}")
                }
            }
        }
    }
}
"""
        ),
        f(
            "app/src/main/res/values/strings.xml",
            """
<resources>
    <string name="app_name">{{name}}</string>
</resources>
"""
        ),
        f(".gitignore", "build/\n.gradle/\nlocal.properties\n.idea/")
    )
}
