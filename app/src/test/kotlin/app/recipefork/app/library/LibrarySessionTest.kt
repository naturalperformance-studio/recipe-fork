package app.recipefork.app.library

import android.os.Bundle
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.editor.textsOf
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.runBlocking
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/** FLOW-058: the view chosen in the recipe book lasts exactly as long as the session. */
@RunWith(AndroidJUnit4::class)
class LibrarySessionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val container = ApplicationProvider.getApplicationContext<RecipeForkApplication>().container

    private fun launch(saved: Bundle? = null): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java)
            .let { if (saved == null) it.setup() else it.setup(saved) }.also { compose.waitForIdle() }

    private fun chooseOwn() {
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun assertView(view: LibraryView) {
        compose.onNodeWithTag(LibraryTags.tab(view)).assertIsSelected()
    }

    /** The reader leaves and the Activity is torn down with its task: nothing is kept for it. */
    private fun ActivityController<MainActivity>.closeTask() {
        get().finish()
        pause().stop().destroy()
    }

    // FLOW-034 (6) at the application's start.
    @Test
    fun theApplicationOpensOnTheMarkedRecipes() {
        launch()
        assertView(LibraryView.MARKED)
    }

    // FLOW-058 (1)
    @Test
    fun theHomeActionAndReturnKeepTheView() {
        val activity = launch()
        chooseOwn()
        activity.pause().stop()
        activity.restart().start().resume()
        compose.waitForIdle()
        assertView(LibraryView.OWN)
    }

    // FLOW-058 (1): a configuration change on the way is the same session too.
    @Test
    fun aRecreatedActivityKeepsTheView() {
        val activity = launch()
        chooseOwn()
        activity.recreate()
        compose.waitForIdle()
        assertView(LibraryView.OWN)
    }

    // FLOW-058 (2): the process ended while away, and the reader returns — restored from saved state.
    @Test
    fun theProcessEndedWhileAwayKeepsTheView() {
        val activity = launch()
        chooseOwn()
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        // The process dies: the Activity and every object in memory are gone, only the saved state remains.
        activity.destroy()
        launch(saved)
        assertView(LibraryView.OWN)
    }

    // A card open when the process ends reopens on return, as the editor does (FLOW-058 (2), FLOW-029).
    @Test
    fun aCardOpenWhenTheProcessEndsReopensOnReturn() {
        val recipe = Recipe.create(Instant.parse("2026-10-01T12:00:00Z"), null).copy(title = "Borscht")
        runBlocking { container.recipes.save(recipe) }
        val activity = launch()
        chooseOwn()
        val entry = LibraryTags.entry(recipe.id.value)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(entry)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(entry).performClick()
        compose.awaitCard()
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        // The process dies: the Activity and every object in memory are gone, only the saved state remains.
        activity.destroy()
        launch(saved)
        compose.awaitCard()
        assertEquals(listOf("Borscht"), compose.textsOf(CardTags.TITLE))
        // And back from it is the own-recipes view.
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.waitForIdle()
        assertView(LibraryView.OWN)
        compose.onNodeWithTag(LibraryTags.OWN_LIST).assertExists()
    }

    // FLOW-058 (3): removed from the recent applications, the task starts anew with nothing to restore.
    @Test
    fun removalFromRecentsStartsANewSessionOnTheMarkedRecipes() {
        val activity = launch()
        chooseOwn()
        activity.closeTask()
        launch()
        assertView(LibraryView.MARKED)
    }

    // FLOW-058 (4): after a restart of the device the application starts with nothing to restore.
    @Test
    fun aDeviceRestartStartsANewSession() {
        val activity = launch()
        chooseOwn()
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        activity.destroy()
        // A restart keeps no task, so the platform hands the new Activity no saved state.
        launch(saved = null)
        assertView(LibraryView.MARKED)
    }

    /** In the own-recipes view of a book holding a recipe, types [query] and orders the view by title. */
    private fun searchOwnByTitle(query: String) {
        runBlocking { container.recipes.save(Recipe.create(Instant.parse("2026-10-01T12:00:00Z"), null).copy(title = "Borscht")) }
        chooseOwn()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.SEARCH)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement(query)
        compose.onNodeWithTag(LibraryTags.ORDER).performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasTestTag(LibraryTags.order(BookOrder.TITLE))).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag(LibraryTags.order(BookOrder.TITLE)).performClick()
        compose.waitForIdle()
    }

    private fun assertOwnSearch(query: String, order: String) {
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.SEARCH)).fetchSemanticsNodes().size == 1 }
        assertEquals(listOf(query), compose.textsOf(LibraryTags.SEARCH).drop(1))
        assertEquals(listOf("Order: $order"), compose.descriptionOf(LibraryTags.ORDER))
    }

    // FLOW-035 (3, 4), FLOW-058 (2): the query and the order of a view survive the end of the process.
    @Test
    fun theProcessEndedWhileAwayKeepsTheQueryAndTheOrder() {
        val activity = launch()
        searchOwnByTitle("Bor")
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        // The process dies: the Activity and every object in memory are gone, only the saved state remains.
        activity.destroy()
        launch(saved)
        assertView(LibraryView.OWN)
        assertOwnSearch("Bor", "By title")
    }

    // FLOW-058 (3): a new session starts with no query and the default order.
    @Test
    fun aNewSessionStartsWithNoQueryAndTheDefaultOrder() {
        val activity = launch()
        searchOwnByTitle("Bor")
        activity.closeTask()
        launch()
        chooseOwn()
        assertOwnSearch("", "Newest first")
    }
}
