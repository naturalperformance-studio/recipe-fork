package app.recipefork.app.editor

import android.os.Bundle
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/**
 * FLOW-024 (6) in the application itself: the author leaves the editor by the home action, or the process ends
 * while they are away, and returns to the editor in the same order with everything entered.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditorSessionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val container = ApplicationProvider.getApplicationContext<RecipeForkApplication>().container

    private fun launch(saved: Bundle? = null): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java)
            .let { if (saved == null) it.setup() else it.setup(saved) }.also { compose.waitForIdle() }

    private fun stored(id: RecipeId): Recipe? = runBlocking { container.recipes.get(id) }

    /** Takes the offer of the empty own view and fills the title, the language and a tag; returns the recipe. */
    private fun writeARecipe(): RecipeId {
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.WRITE)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(LibraryTags.WRITE).performScrollTo().performClick()
        compose.onNodeWithTag(LibraryTags.FROM_SCRATCH).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(EditorTags.FORM)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Borscht")
        compose.declareLanguage(RecipeLanguage.RU)
        // The tag editor offers the shipped dictionaries on this fresh installation.
        compose.addTag("ital", 10)
        compose.waitForIdle()
        // The one recipe the book holds is the one the offer created.
        val id = RecipeId(runBlocking { container.recipes.ownNewest(10).first() }.single().recipeId)
        // Everything entered is in the book.
        compose.waitUntil(PATIENCE_MS) { stored(id)?.let { it.title == "Borscht" && it.tags == setOf(10L) } == true }
        return id
    }

    /** The editor is open on [id], in its order, holding what was entered. */
    private fun assertTheEditorHoldsWhatWasEntered(id: RecipeId) {
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasTestTag(EditorTags.chosen(10))).fetchSemanticsNodes().size == 1
        }
        assertEquals("Borscht", compose.textsOf(EditorTags.TITLE).last())
        assertEquals(RecipeLanguage.RU, compose.declaredLanguage())
        // Unclipped, as the tags may sit below the screen's bottom under the cover's placeholder.
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }
        val title = bounds(EditorTags.TITLE)
        val language = bounds(EditorTags.LANGUAGE)
        val tags = bounds(EditorTags.TAGS)
        assertEquals(true, language.left >= title.right && tags.top >= title.bottom)
        val recipe = stored(id)!!
        assertEquals("Borscht", recipe.title)
        assertEquals(RecipeLanguage.RU, recipe.language)
        assertEquals(setOf(10L), recipe.tags)
    }

    @Test
    fun leavingByTheHomeActionAndReturningKeepsTheEditorAndEverythingEntered() {
        val activity = launch()
        val id = writeARecipe()
        activity.pause().stop()
        activity.restart().start().resume()
        compose.waitForIdle()
        assertTheEditorHoldsWhatWasEntered(id)
    }

    @Test
    fun theProcessEndingWhileAwayReturnsToTheEditorWithEverythingEntered() {
        val activity = launch()
        val id = writeARecipe()
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        // The process dies: the Activity and every object in memory are gone; the book and the saved state remain.
        activity.destroy()
        launch(saved)
        assertTheEditorHoldsWhatWasEntered(id)
    }
}
