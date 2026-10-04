package app.recipefork.app.settings

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Scenario 2 of the increment: the book's top bar opens the settings — a destination with a top bar and back — which
 * list the language, the usual servings, the drag side and the saved media, each showing its value in force; back,
 * the system's, the bar's or the predictive gesture, returns to the book where its list was left.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val usual = HeldUsualServings(Rational.of(3))
    private val side = HeldDragHandleSide(initial = true)
    private val card = CardEnvironment(usual, ingredients = IngredientEntries { emptyList() })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = emptyList<TagEntity>()
        },
        dragHandleSide = side,
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun show() {
        session.choose(LibraryView.OWN)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor) } }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun openSettings() {
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.CLEAR_MEDIA) && !exists(LibraryTags.OWN_LIST) }
    }

    private fun awaitBook() = compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.OWN_LIST) && !exists(SettingsTags.SCREEN) }

    private fun value(item: String): String = compose.textsOf(SettingsTags.value(item)).single()

    // The book's bar holds one action, the settings; it opens them, and they list every setting with its value.
    @Test
    fun theBooksBarOpensTheSettingsWhichShowEveryValueInForce() {
        show()
        val barActions = compose.onAllNodes(hasAnyAncestor(hasTestTag(LibraryTags.TOP_BAR)))
            .fetchSemanticsNodes().filter { SemanticsActions.OnClick in it.config }
        assertEquals(listOf(LibraryTags.SETTINGS), barActions.map { it.config.getOrNull(SemanticsProperties.TestTag) })
        assertEquals("Open the settings", AccessibilityChecks.clickActionLabel(barActions.single()))
        openSettings()
        assertEquals(listOf("Settings"), compose.textsOf(SettingsTags.TITLE))
        assertEquals(listOf("Language", "As on the device"), compose.textsOf(SettingsTags.LANGUAGE))
        assertEquals(listOf("I usually cook for…", "3"), compose.textsOf(SettingsTags.USUAL))
        assertEquals(listOf("Drag handle on the right"), compose.textsOf(SettingsTags.HANDLE_RIGHT))
        assertEquals(
            ToggleableState.On,
            compose.onNodeWithTag(SettingsTags.HANDLE_RIGHT).fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )
        compose.waitUntil(PATIENCE_MS) { value(SettingsTags.CLEAR_MEDIA) == "Nothing to clear" }
        assertEquals("Clear saved media", compose.textsOf(SettingsTags.CLEAR_MEDIA).first())
        // The items in order, top to bottom.
        val tops = listOf(SettingsTags.LANGUAGE, SettingsTags.USUAL, SettingsTags.HANDLE_RIGHT, SettingsTags.CLEAR_MEDIA)
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 5)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 5)
        val back = compose.onNodeWithTag(SettingsTags.BACK).fetchSemanticsNode()
        assertEquals("Back to the book", AccessibilityChecks.clickActionLabel(back))
    }

    // A value changed in its item's dialog is shown at once.
    @Test
    fun aValueChangedIsShownAtOnce() {
        show()
        openSettings()
        compose.onNodeWithTag(SettingsTags.USUAL).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.USUAL_DIALOG) }
        compose.onNodeWithTag(LibraryTags.USUAL_MORE).performClick()
        compose.onNodeWithTag(LibraryTags.USUAL_SET).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.USUAL_DIALOG) }
        assertEquals("4", value(SettingsTags.USUAL))
        assertEquals(Rational.of(4), usual.count.value)
    }

    // L10N: the settings read in Russian, every word from the resources.
    @Test
    @Config(qualifiers = "ru")
    fun theSettingsReadInRussian() {
        usual.set(null)
        show()
        openSettings()
        compose.waitUntil(PATIENCE_MS) { value(SettingsTags.CLEAR_MEDIA) == "Удалять нечего" }
        assertEquals(listOf("Настройки"), compose.textsOf(SettingsTags.TITLE))
        LocalizationChecks.assertScreenTextFromResources(compose, context, emptySet())
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, emptySet())
    }

    /** Seventy own recipes, "Recipe 0" the newest: more than two of the list's pages. */
    private fun seventy(): List<Recipe> = (0 until 70).map { n ->
        Recipe.create(now.minusSeconds(n * 60L), owner = null).copy(title = "Recipe $n").also { runBlocking { recipes.save(it) } }
    }

    /** Scrolls the own list to [recipe]'s entry, loading more of the list as it goes; returns the list's first entry shown. */
    private fun scrollTo(recipe: Recipe): String {
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(LibraryTags.entry(recipe.id.value)))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        return firstShown()
    }

    /** The tag of the topmost entry laid out within the own list. */
    private fun firstShown(): String {
        val list = compose.onNodeWithTag(LibraryTags.OWN_LIST).fetchSemanticsNode().boundsInRoot
        return compose.onAllNodes(hasAnyAncestor(hasTestTag(LibraryTags.OWN_LIST))).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-entry-") == true }
            .filter { it.boundsInRoot.bottom > list.top && it.boundsInRoot.top < list.bottom }
            .minBy { it.boundsInRoot.top }
            .config[SemanticsProperties.TestTag]
    }

    /**
     * Brings back the book's bar, which scrolled away with the list, by a short swipe down the list as a reader would;
     * returns the list's first entry shown then.
     */
    private fun revealBar(): String {
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput { swipeDown(startY = centerY, endY = centerY + 300f) }
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(LibraryTags.SETTINGS).fetchSemanticsNode().boundsInRoot.top >= 0f &&
                compose.onNodeWithTag(LibraryTags.SETTINGS).fetchSemanticsNode().boundsInRoot.height > 0f
        }
        compose.waitForIdle()
        return firstShown()
    }

    private fun gesture(progress: Float) = BackEventCompat(progress * 1000f, 500f, progress, BackEventCompat.EDGE_LEFT)

    private fun settingsLeft() = compose.onNodeWithTag(SettingsTags.SCREEN).fetchSemanticsNode().boundsInRoot.left

    // The book's list keeps its place across the settings, left by the bar's back, by the system's back, and by the
    // predictive gesture — which shows the book beneath as the settings slide away, and leaves them when cancelled.
    @Test
    fun backReturnsToTheBookWhereItsListWasLeftThePredictiveGestureShowingItBeneath() {
        val all = seventy()
        show()
        scrollTo(all[50])
        val first = revealBar()
        assertTrue(first, first != LibraryTags.entry(all[0].id.value))

        openSettings()
        compose.onNodeWithTag(SettingsTags.BACK).performClick()
        awaitBook()
        assertEquals(first, firstShown())

        openSettings()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitBook()
        assertEquals(first, firstShown())

        openSettings()
        val dispatcher = compose.activity.onBackPressedDispatcher
        assertEquals(0f, settingsLeft())
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.5f))
        }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.OWN_LIST) }
        assertTrue("the settings have not moved", settingsLeft() > 0f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.OWN_LIST) }
        assertEquals(0f, settingsLeft())

        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.5f))
            dispatcher.onBackPressed()
        }
        awaitBook()
        assertEquals(first, firstShown())
    }
}
