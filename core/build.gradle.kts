plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // :core is consumed by the Android app, which targets Java 17 bytecode.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// The dictionary generator (scripts/dictionary-build.sh): its own source set, so nothing of it ships in the APK,
// built on main's DTOs and Json so that what it writes is encoded exactly as the application encodes it.
val dictionary: SourceSet by sourceSets.creating
configurations[dictionary.implementationConfigurationName].extendsFrom(configurations.implementation.get())

dependencies {
    api(libs.kotlinx.serialization.json)
    dictionary.implementationConfigurationName(sourceSets.main.get().output)
    testImplementation(dictionary.output)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit5.launcher)
}

tasks.test {
    useJUnitPlatform()
    // ShippedDictionaryTest builds the sources and compares the shipped asset: both are inputs of the suite.
    val root = rootProject.layout.projectDirectory
    inputs.dir(root.dir("dictionary")).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("dictionarySources")
    inputs.file(root.file("app/src/main/assets/dictionaries.json")).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("dictionaryAsset")
    // -Ppaste.corpus.extra=<dir>: the corpus test also reads the texts of that outside directory (CorpusTest).
    providers.gradleProperty("paste.corpus.extra").orNull?.let { dir ->
        systemProperty("paste.corpus.extra", file(dir).absolutePath)
        inputs.dir(file(dir)).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("pasteCorpusExtra")
    }
}

// Validates the sources under dictionary/ and writes app/src/main/assets/dictionaries.json (dictionary/README.md).
tasks.register<JavaExec>("buildDictionary") {
    group = "build"
    description = "Validates dictionary/*.tsv against the committed snapshot and writes app/src/main/assets/dictionaries.json."
    classpath = dictionary.runtimeClasspath
    mainClass.set("app.recipefork.core.dictionary.DictionaryBuildKt")
    workingDir = rootProject.layout.projectDirectory.asFile
    args(".")
    // -PfirstBuild: no dictionary was ever shipped, so there is no baseline to check the ids against.
    if (providers.gradleProperty("firstBuild").isPresent) args("--first-build")
}
