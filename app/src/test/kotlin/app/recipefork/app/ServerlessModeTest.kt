package app.recipefork.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * decisions/20260928-serverless-mode-is-a-build-flag.md: the Gradle property `recipefork.serverless` sets
 * `BuildConfig.SERVERLESS`; only MainActivity reads it, and the composition root is built in the mode it gives.
 * Both builds keep one application id.
 */
@RunWith(AndroidJUnit4::class)
class ServerlessModeTest {
    /** The repository's root; unit tests run in the module's directory. */
    private val root = File("..").canonicalFile

    @Test
    fun theFlagGivesTheMode() {
        assertEquals(Mode.Serverless, MainActivity.modeOf(serverless = true))
        assertEquals(Mode.Standard, MainActivity.modeOf(serverless = false))
    }

    // Without the property, the build is standard, and the composition root is built in the mode MainActivity gives.
    @Test
    fun withoutThePropertyTheCompositionRootIsStandard() {
        assertFalse(BuildConfig.SERVERLESS)
        assertEquals(Mode.Standard, MainActivity.MODE)
        val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
        assertEquals(MainActivity.MODE, application.container.mode)
    }

    @Test
    fun onlyTheBuildScriptReadsThePropertyAndOnlyMainActivityReadsTheConstant() {
        val sources = File(root, "app/src/main").walkTopDown().filter { it.isFile }.toList()
        val readers = sources.filter { "SERVERLESS" in it.readText() }.map { it.name }
        assertEquals(listOf("MainActivity.kt"), readers)
        val scripts = root.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "src", "docs", "spec") }
            .filter { it.isFile && it.name.endsWith(".kts") }
            .filter { "recipefork.serverless" in it.readText() }
            .map { it.relativeTo(root).path }
            .toList()
        assertEquals(listOf("app/build.gradle.kts"), scripts)
        val property = File(root, "app/build.gradle.kts").readText()
        assertTrue(property.contains("""buildConfigField("boolean", "SERVERLESS""""))
        val everySource = listOf("app/src", "core/src", "server/src").map { File(root, it) }.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") && it != File(root, "app/src/test/kotlin/app/recipefork/app/ServerlessModeTest.kt") }.toList()
        }
        assertEquals(emptyList<String>(), everySource.filter { "recipefork.serverless" in it.readText() }.map { it.path })
    }

    // The serverless build installs over the standard one, keeping the book: one application id, set once.
    @Test
    fun bothBuildsShareOneApplicationId() {
        assertEquals("app.recipefork.app", BuildConfig.APPLICATION_ID)
        val script = File(root, "app/build.gradle.kts").readText()
        assertEquals(1, Regex("""\bapplicationId\s*=""").findAll(script).count())
        assertFalse(script.contains("applicationIdSuffix"))
        assertFalse(script.contains("productFlavors"))
    }
}
