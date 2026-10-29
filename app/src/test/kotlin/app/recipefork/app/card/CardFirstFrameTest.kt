package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.fork
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * T-312: a card stepped a frame at a time as it opens shows its title, and a version's provenance line above it, in the
 * first frame where they stay — under the bar and its tabs, which come with the recipe — not under the bar alone for one
 * frame and then dropped by the tabs' height.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardFirstFrameTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun save(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun recipe(title: String) = Recipe.create(now, null).copy(title = title, servings = 2, steps = listOf(Step(0, "Mix")))

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp-xhdpi")
    fun atTheUsualFontOn360dpTheVersionStandsInItsPlaceFromTheFirstFrame() = versionStandsFromTheFirstFrame()

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp-xhdpi", fontScale = 2f)
    fun atTwiceTheFontOn360dpTheVersionStandsInItsPlaceFromTheFirstFrame() = versionStandsFromTheFirstFrame()

    @Test
    @Config(qualifiers = "ru-w320dp-h711dp-xhdpi", fontScale = 2f)
    fun atTwiceTheFontOn320dpTheVersionStandsInItsPlaceFromTheFirstFrame() = versionStandsFromTheFirstFrame()

    // Not the provenance line's doing: a recipe with no parent opens the same way.
    @Test
    @Config(qualifiers = "ru-w360dp-h780dp-xhdpi")
    fun aRecipeWithNoParentStandsInItsPlaceFromTheFirstFrame() {
        val recipe = save(recipe("Шакшука по-тбилисски"))
        val first = openFrameByFrame(recipe)
        settle()
        assertEquals("the title's first top is where it settles", top(CardTags.TITLE), first.title, 0.5f)
        assertTrue("the title stands under the tabs from its first frame", first.title >= first.tabsBottom - 0.5f)
    }

    private fun versionStandsFromTheFirstFrame() {
        val parent = save(recipe("Шакшука по-тбилисски с печёными перцами"))
        val version = save(parent.fork(now, owner = null).copy(title = "Моя шакшука с фетой"))
        val first = openFrameByFrame(version)
        assertTrue("the line comes with the title", first.line != null)
        settle()
        assertEquals("the title's first top is where it settles", top(CardTags.TITLE), first.title, 0.5f)
        assertEquals("the line's first top is where it settles", top(CardTags.PROVENANCE), first.line!!, 0.5f)
        assertTrue("the line stands under the tabs from its first frame", first.line >= first.tabsBottom - 0.5f)
    }

    /** Where the title, the provenance line (if any) and the tabs' bottom stand in the first frame the title is in. */
    private class FirstFrame(val title: Float, val line: Float?, val tabsBottom: Float)

    /** Opens the card of [recipe] with the clock held, a frame at a time, until the first frame its title is in. */
    private fun openFrameByFrame(recipe: Recipe): FirstFrame {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        var frames = 0
        while (!exists(CardTags.TITLE)) {
            assertTrue("the title comes within a few seconds", frames++ < FRAMES_TO_WAIT)
            compose.mainClock.advanceTimeByFrame()
        }
        return FirstFrame(
            top(CardTags.TITLE),
            if (exists(CardTags.PROVENANCE)) top(CardTags.PROVENANCE) else null,
            compose.onNodeWithTag(CardTags.TABS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom,
        )
    }

    private fun settle() = repeat(SETTLING_FRAMES) { compose.mainClock.advanceTimeByFrame() }

    private companion object {
        /** How many frames the card may take to read the recipe and its parent. */
        const val FRAMES_TO_WAIT = 600

        /** How many frames the card is given to settle after its title first shows. */
        const val SETTLING_FRAMES = 30
    }
}
