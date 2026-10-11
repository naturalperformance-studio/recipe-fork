package app.recipefork.app.library

import android.os.Bundle
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
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
        // The list is laid out once it is read from the device.
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.OWN_LIST)).fetchSemanticsNodes().size == 1 }
    }

    // FLOW-058 (3): removed from the recent applications, the task starts anew with nothing to restore; the book opens
    // on the view last left, which the device keeps (the owner's decision over FLOW-034 (6)).
    @Test
    fun removalFromRecentsStartsANewSessionOnTheViewLastLeft() {
        val activity = launch()
        chooseOwn()
        activity.closeTask()
        val again = launch()
        assertView(LibraryView.OWN)
        // And the view left is followed again: back on the marked recipes, the next new session opens on them.
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitForIdle()
        again.closeTask()
        launch()
        assertView(LibraryView.MARKED)
    }

    // FLOW-058 (4): after a restart of the device the application starts with nothing to restore, on the view last left.
    @Test
    fun aDeviceRestartStartsANewSessionOnTheViewLastLeft() {
        val activity = launch()
        chooseOwn()
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        activity.destroy()
        // A restart keeps no task, so the platform hands the new Activity no saved state.
        launch(saved = null)
        assertView(LibraryView.OWN)
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
    /** In the own-recipes view of a book holding a recipe tagged with the shipped tag 10, filters by that tag. */
    private fun filterOwnByTag() {
        runBlocking {
            container.recipes.save(
                Recipe.create(Instant.parse("2026-10-01T12:00:00Z"), null).copy(title = "Lasagne", tags = setOf(ITALIAN)),
            )
        }
        chooseOwn()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.FILTER_ADD)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(LibraryTags.FILTER_ADD).scrolledTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.TAG_FIELD)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(LibraryTags.TAG_FIELD).scrolledTo().performTextReplacement("ital")
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.tagChoice(ITALIAN))).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(LibraryTags.tagChoice(ITALIAN)).scrolledTo().performClick()
        compose.onNodeWithTag(LibraryTags.FILTERS_SHEET_DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.FILTERS_SHEET)).fetchSemanticsNodes().isEmpty() }
    }

    private fun tagFilterShown(): Boolean =
        compose.onAllNodes(hasTestTag(LibraryTags.tagFilter(ITALIAN))).fetchSemanticsNodes().size == 1

    // FLOW-035, FLOW-058 (2): a view's filters survive the end of the process, and a new session starts with none.
    @Test
    fun theProcessEndedWhileAwayKeepsTheFilters() {
        val activity = launch()
        filterOwnByTag()
        compose.waitUntil(PATIENCE_MS) { tagFilterShown() }
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        // The process dies: the Activity and every object in memory are gone, only the saved state remains.
        activity.destroy()
        val restored = launch(saved)
        assertView(LibraryView.OWN)
        compose.waitUntil(PATIENCE_MS) { tagFilterShown() }
        restored.closeTask()
        launch()
        chooseOwn()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.FILTER_ADD)).fetchSemanticsNodes().size == 1 }
        assertEquals(false, tagFilterShown())
    }

    // L10N-005, FLOW-058 (2): a view's language filter survives the end of the process.
    @Test
    fun theProcessEndedWhileAwayKeepsTheLanguageFilter() {
        val activity = launch()
        runBlocking {
            container.recipes.save(
                Recipe.create(Instant.parse("2026-10-01T12:00:00Z"), null).copy(title = "Pie", language = RecipeLanguage.EN),
            )
            // A view of two languages, so that the sheet offers the language filter.
            container.recipes.save(
                Recipe.create(Instant.parse("2026-10-01T11:00:00Z"), null).copy(title = "Пирог", language = RecipeLanguage.RU),
            )
        }
        chooseOwn()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.FILTER_ADD)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(LibraryTags.FILTER_ADD).scrolledTo().performClick()
        val english = LibraryTags.languageChoice(BookLanguage.EN)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(english)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(english).scrolledTo().performClick()
        compose.onNodeWithTag(LibraryTags.FILTERS_SHEET_DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.FILTERS_SHEET)).fetchSemanticsNodes().isEmpty() }
        fun chipShown() = compose.onAllNodes(hasTestTag(LibraryTags.LANGUAGE_FILTER)).fetchSemanticsNodes().size == 1
        compose.waitUntil(PATIENCE_MS) { chipShown() }
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        activity.destroy()
        launch(saved)
        assertView(LibraryView.OWN)
        compose.waitUntil(PATIENCE_MS) { chipShown() }
        assertEquals(listOf("English"), compose.textsOf(LibraryTags.LANGUAGE_FILTER))
    }

    // T-206: the filter "Enough equipment" survives the end of the process as the other filters do; a new session has none.
    @Test
    fun theProcessEndedWhileAwayKeepsTheEquipmentFilter() {
        container.kitchen.hold(listOf(FRYING_PAN), true)
        val activity = launch()
        runBlocking {
            container.recipes.save(Recipe.create(Instant.parse("2026-10-01T12:00:00Z"), null).copy(title = "Pancakes", equipment = listOf(EquipmentItem(FRYING_PAN))))
            container.recipes.save(Recipe.create(Instant.parse("2026-10-01T11:00:00Z"), null).copy(title = "Roast", equipment = listOf(EquipmentItem(OVEN))))
        }
        chooseOwn()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.FILTER_ADD)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(LibraryTags.FILTER_ADD).scrolledTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.EQUIPMENT_CHOICE)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(LibraryTags.EQUIPMENT_CHOICE).scrolledTo().performClick()
        compose.onNodeWithTag(LibraryTags.FILTERS_SHEET_DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.FILTERS_SHEET)).fetchSemanticsNodes().isEmpty() }
        fun chipShown() = compose.onAllNodes(hasTestTag(LibraryTags.EQUIPMENT_FILTER)).fetchSemanticsNodes().size == 1
        fun listed(title: String) = compose.onAllNodes(hasText(title), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        compose.waitUntil(PATIENCE_MS) { chipShown() && listed("Pancakes") && !listed("Roast") }
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        activity.destroy()
        val restored = launch(saved)
        assertView(LibraryView.OWN)
        compose.waitUntil(PATIENCE_MS) { chipShown() && listed("Pancakes") && !listed("Roast") }
        restored.closeTask()
        launch()
        chooseOwn()
        compose.waitUntil(PATIENCE_MS) { listed("Pancakes") && listed("Roast") }
        assertEquals(false, chipShown())
    }

    private companion object {
        /** A tag of the shipped dictionary. */
        const val ITALIAN = 10L

        /** Equipment of the shipped dictionary. */
        const val FRYING_PAN = 10001L
        const val OVEN = 10202L
    }
}
