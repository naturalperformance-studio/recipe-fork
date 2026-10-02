plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.recipefork.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.recipefork.app"
        minSdk = 29 // QUAL-005
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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

    buildTypes {
        debug {
            // The local server speaks plain HTTP; only a debug build may reach it that way.
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
    }

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
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
