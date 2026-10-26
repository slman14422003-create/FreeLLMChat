plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val ksFile: String? = System.getenv("KEYSTORE_FILE")
val hasKeystore: Boolean = !ksFile.isNullOrBlank() &&
    File(ksFile).exists() &&
    !System.getenv("KEYSTORE_PASSWORD").isNullOrBlank() &&
    !System.getenv("KEY_ALIAS").isNullOrBlank() &&
    !System.getenv("KEY_PASSWORD").isNullOrBlank()

val modelsUrl: String = (project.findProperty("MODELS_URL") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: "https://raw.githubusercontent.com/OWNER/REPO/main/app/src/main/assets/models.json"

val appVersionCode: Int = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.freellm.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.freellm.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.1.0"
        buildConfigField("String", "MODELS_URL", "\"$modelsUrl\"")
        buildConfigField("String", "DEFAULT_PROXY", "\"https://freellm.slman2233445566y.workers.dev\"")
        resourceConfigurations += listOf("ar", "en")
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = File(ksFile!!)
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
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

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}
