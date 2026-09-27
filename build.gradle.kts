plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The build command of docs/architecture/00-index.md.
tasks.register("assembleAll") {
    group = "build"
    description = "Builds every module: the core library, the server distribution and the debug APK."
    dependsOn(":core:assemble", ":server:installDist", ":app:assembleDebug")
}

// The test command of docs/architecture/00-index.md. Needs Docker, needs no device.
tasks.register("testAll") {
    group = "verification"
    description = "Runs the full JVM suite: core, server (against PostgreSQL in Docker) and the app under Robolectric."
    dependsOn(":core:test", ":server:test", ":app:testDebugUnitTest")
}
