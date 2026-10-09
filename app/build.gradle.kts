plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The version every build carries, from recipefork.version in gradle.properties (the scheme is described there).
val appVersionName: String = providers.gradleProperty("recipefork.version").get()
val appVersionCode: Int = run {
    val parts = appVersionName.split(".").map { it.toIntOrNull() }
    require(parts.size in 2..3 && parts.all { it != null && it >= 0 } && parts.drop(1).all { it!! < 1000 }) {
        "recipefork.version must be MAJOR.MINOR or MAJOR.MINOR.PATCH with MINOR and PATCH below 1000, not $appVersionName"
    }
    val (major, minor) = parts.map { it!! }
    major * 1_000_000 + minor * 1_000 + (parts.getOrNull(2) ?: 0)
}

// The Play upload key. It never lives in the repository: scripts/play-bundle.sh takes it from ~/Projects/.env or the
// environment and hands it to Gradle as environment variables. Without them the play bundle is left unsigned.
val uploadKey: Map<String, String>? = listOf(
    "RECIPEFORK_UPLOAD_STORE_FILE",
    "RECIPEFORK_UPLOAD_STORE_PASSWORD",
    "RECIPEFORK_UPLOAD_KEY_ALIAS",
    "RECIPEFORK_UPLOAD_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull.orEmpty() }.takeIf { key -> key.values.all { it.isNotEmpty() } }

android {
    namespace = "app.recipefork.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.recipefork.app"
        minSdk = 29 // QUAL-005
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Where the device reaches the product's server. The default is the development machine as the
        // emulator sees it (deploy/local); override with -Precipefork.serverUrl=...
        val serverUrl = providers.gradleProperty("recipefork.serverUrl").getOrElse("http://10.0.2.2:8080")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        manifestPlaceholders["usesCleartextTraffic"] = "false"

        // Serverless mode is a build flag (decisions/20260928-serverless-mode-is-a-build-flag.md): only MainActivity
        // reads it. Both builds keep one application id, so the serverless one installs over the standard one.
        val serverless = providers.gradleProperty("recipefork.serverless").map { it.toBoolean() }.getOrElse(false)
        buildConfigField("boolean", "SERVERLESS", "$serverless")
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getValue("RECIPEFORK_UPLOAD_STORE_FILE"))
                storePassword = uploadKey.getValue("RECIPEFORK_UPLOAD_STORE_PASSWORD")
                keyAlias = uploadKey.getValue("RECIPEFORK_UPLOAD_KEY_ALIAS")
                keyPassword = uploadKey.getValue("RECIPEFORK_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // The local server speaks plain HTTP; only a debug build may reach it that way.
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            // The build the owner installs by hand: shrunk by R8, its unused resources dropped, and signed with the
            // debug keystore, so it installs over a debug build (one application id, one certificate) keeping the book.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        // The release build as the instrumented tests reach it (scripts/device-test-release.sh): shrunk by the same
        // rules, plus only what the test runner and the smoke test take from the shrunk application.
        create("releaseSmoke") {
            initWith(getByName("release"))
            proguardFile("proguard-smoke-test.pro")
            testProguardFile("proguard-smoke-test-apk.pro")
            matchingFallbacks += "release"
        }
        // The app bundle for Google Play (scripts/play-bundle.sh): the release build, shrunk by the same rules, but
        // signed with the upload key instead of the debug one, and never with the debug one: unsigned without the key.
        // It is always the serverless build, whatever recipefork.serverless says, and src/play/AndroidManifest.xml
        // takes the network permissions out of it, since that build never reaches a server.
        create("play") {
            initWith(getByName("release"))
            buildConfigField("boolean", "SERVERLESS", "true")
            signingConfig = signingConfigs.findByName("upload")
            matchingFallbacks += "release"
        }
    }

    // The instrumented tests run against the debug build; scripts/device-test-release.sh points them at the minified
    // one instead (-Precipefork.testBuildType=releaseSmoke), whose test APK R8 maps onto the shrunk application.
    testBuildType = providers.gradleProperty("recipefork.testBuildType").getOrElse("debug")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric reaches into java.io.FileDescriptor and jdk.internal.access, which the JDK closes by default.
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // Converts a step's clip on the device (MEDIA-004); the client-stack ADR names Media3 Transformer.
    implementation(libs.media3.transformer)
    implementation(libs.media3.common)
    implementation(libs.media3.effect)
    implementation(libs.media3.container)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
