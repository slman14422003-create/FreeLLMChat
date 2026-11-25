import java.time.Duration
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val envKs: String? = System.getenv("KEYSTORE_FILE")
val useEnvKs: Boolean = !envKs.isNullOrBlank() && File(envKs).exists() &&
    !System.getenv("KEYSTORE_PASSWORD").isNullOrBlank() &&
    !System.getenv("KEY_ALIAS").isNullOrBlank() &&
    !System.getenv("KEY_PASSWORD").isNullOrBlank()

val modelsUrl: String = (project.findProperty("MODELS_URL") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: "https://raw.githubusercontent.com/OWNER/REPO/main/app/src/main/assets/models.json"

val isReleaseTask: Boolean = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (isReleaseTask && modelsUrl.contains("OWNER/REPO")) {
    if (System.getenv("REQUIRE_RELEASE_SIGNING") == "1") throw GradleException("MODELS_URL still contains the OWNER/REPO placeholder")
    logger.warn("MODELS_URL still contains the OWNER/REPO placeholder")
}

val appVersionCode: Int = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1

val healthSeedDir = layout.buildDirectory.dir("generated/healthSeed")
val copyHealthSeed by tasks.registering(Copy::class) {
    from(rootProject.file("tools/health.json"))
    into(healthSeedDir)
}

android {
    namespace = "com.freellm.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.freellm.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.4.0"
        buildConfigField("String", "MODELS_URL", "\"$modelsUrl\"")
        resourceConfigurations += listOf("ar", "en")
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                val local = (project.findProperty("WHISPER_CPP_DIR") as String?)?.takeIf { it.isNotBlank() }
                if (local != null) arguments += "-DWHISPER_CPP_DIR=$local"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets["main"].assets.srcDir(healthSeedDir)

    if (System.getenv("REQUIRE_RELEASE_SIGNING") == "1" && !useEnvKs) {
        throw GradleException("Release signing is required but keystore secrets are missing")
    }

    signingConfigs {
        if (useEnvKs) {
            create("release") {
                storeFile = File(envKs!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (useEnvKs) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = if (useEnvKs) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
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
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.reorderable)
    implementation(libs.highlights)
    implementation(libs.mxparser)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    debugImplementation(libs.leakcanary)

    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.pdfbox.android)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(copyHealthSeed)
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(8))
    testLogging {
        events("started", "passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
