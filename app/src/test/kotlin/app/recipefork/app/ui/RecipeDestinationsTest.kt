package app.recipefork.app.ui

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.awaitCard
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The book, a recipe's card and the editor are destinations of the navigation host: back goes one destination back,
 * the predictive gesture showing the book beneath the card; the book's list keeps its place and what it has loaded
 * across a card or the editor opened and left, and across a configuration change; the end of the process on a card
 * or in the editor returns to it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RecipeDestinationsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val saved = SavedStateHandle()
    private var session = LibrarySession(saved)
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val card = CardEnvironment(ingredients = IngredientEntries { emptyList() })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = emptyList<TagEntity>()
        },
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    /** Seventy recipes of [view], "Recipe 0" the newest: more than two of the own list's pages. */
    private fun seventy(view: LibraryView): List<Recipe> = (0 until 70).map { n ->
        val recipe = Recipe.create(now.minusSeconds(n * 60L), owner = null).copy(title = "Recipe $n")
        runBlocking {
            if (view == LibraryView.OWN) {
                recipes.save(recipe)
            } else {
                recipes.addFound(recipe.inState(RecipeImport.FOUND_STATE), now.minusSeconds(n * 60L))
            }
        }
        recipe
    }

    private fun show(view: LibraryView): StateRestorationTester {
        session.choose(view)
        val tester = StateRestorationTester(compose)
        // The session read anew each time the content is: a process ended and restored brings a new one.
        tester.setContent { MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor) } }
        compose.waitForIdle()
        return tester
    }

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    /** Scrolls [view]'s list to the entry of [recipe], loading more of the list as it goes. */
    private fun scrollTo(view: LibraryView, recipe: Recipe) = compose.waitUntil(PATIENCE_MS) {
        try {
            compose.onNodeWithTag(list(view)).performScrollToNode(hasTestTag(LibraryTags.entry(recipe.id.value)))
            true
        } catch (e: AssertionError) {
            false
        }
    }

    /**
     * The titles of the entries laid out within [view]'s list, top first. An entry the list keeps composed but does not
     * place — just scrolled out, held for reuse — is not in view, whatever bounds it last had.
     */
    private fun inView(view: LibraryView, all: List<Recipe>): List<String> {
        val bounds = compose.onNodeWithTag(list(view)).fetchSemanticsNode().boundsInRoot
        val titles = all.associate { LibraryTags.entry(it.id.value) to it.title }
        val entry = SemanticsMatcher("an entry") { it.config.getOrNull(SemanticsProperties.TestTag) in titles }
        return compose.onAllNodes(entry).fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced && it.boundsInRoot.bottom > bounds.top && it.boundsInRoot.top < bounds.bottom }
            .sortedBy { it.boundsInRoot.top }
            .map { titles.getValue(it.config[SemanticsProperties.TestTag]) }
    }

    private fun systemBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun awaitBook(view: LibraryView) {
        compose.waitUntilExactlyOneExists(hasTestTag(list(view)), PATIENCE_MS)
        compose.onNodeWithTag(CardTags.SCREEN).assertDoesNotExist()
    }

    private fun openCard(recipe: Recipe) {
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).performClick()
        compose.awaitCard()
        compose.onNodeWithTag(list(LibraryView.OWN)).assertDoesNotExist()
        compose.onNodeWithTag(list(LibraryView.MARKED)).assertDoesNotExist()
    }

    /** Scrolls deep into [view]'s list, past its first page, and opens the deep entry's card; returns what was in view. */
    private fun deepCard(view: LibraryView, all: List<Recipe>): List<String> {
        val deep = all[50]
        scrollTo(view, deep)
        val shown = inView(view, all)
        assertTrue(shown.toString(), deep.title in shown && all[0].title !in shown)
        openCard(deep)
        return shown
    }

    private fun assertInView(view: LibraryView, all: List<Recipe>, expected: List<String>) {
        awaitBook(view)
        assertEquals(expected, inView(view, all))
    }

    // Scenario 3: scroll the book down, open a recipe, come back — the list is where it was, with its pages loaded.
    @Test
    fun theOwnListKeepsItsPlaceAcrossACard() = keepsItsPlaceAcrossACard(LibraryView.OWN)

    @Test
    fun theMarkedListKeepsItsPlaceAcrossACard() = keepsItsPlaceAcrossACard(LibraryView.MARKED)

    private fun keepsItsPlaceAcrossACard(view: LibraryView) {
        val all = seventy(view)
        show(view)
        val shown = deepCard(view, all)
        systemBack()
        assertInView(view, all, shown)
        // And by the card's own back action.
        openCard(all[50])
        compose.onNodeWithTag(CardTags.BACK).performClick()
        assertInView(view, all, shown)
        assertNull(session.viewing.value)
    }

    // The editor opened from the card, left for the card and the card for the book: one destination back each time.
    @Test
    fun theOwnListKeepsItsPlaceAcrossTheEditor() {
        val all = seventy(LibraryView.OWN)
        show(LibraryView.OWN)
        val shown = deepCard(LibraryView.OWN, all)
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        compose.onNodeWithTag(CardTags.SCREEN).assertDoesNotExist()
        systemBack()
        compose.awaitCard()
        compose.onNodeWithTag(EditorTags.SCREEN).assertDoesNotExist()
        assertEquals(listOf("Recipe 50"), compose.textsOf(CardTags.TITLE))
        assertNull(session.editing.value)
        systemBack()
        assertInView(LibraryView.OWN, all, shown)
    }

    // The screen turns while the card is open: the card is still open, and back finds the list where it was.
    @Test
    fun theListKeepsItsPlaceAcrossAConfigurationChangeOnTheCard() {
        val all = seventy(LibraryView.OWN)
        val tester = show(LibraryView.OWN)
        val shown = deepCard(LibraryView.OWN, all)
        tester.emulateSavedInstanceStateRestore()
        compose.awaitCard()
        assertEquals(listOf("Recipe 50"), compose.textsOf(CardTags.TITLE))
        systemBack()
        assertInView(LibraryView.OWN, all, shown)
    }

    /** The process ends and is restored: a new session from what the old one saved, the screen from its saved state. */
    private fun endTheProcess(tester: StateRestorationTester) {
        session = LibrarySession(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        tester.emulateSavedInstanceStateRestore()
    }

    // The process ends on a card: the card is open again, and back leads to the book, where the list was left.
    @Test
    fun theEndOfTheProcessOnACardReturnsToIt() {
        val all = seventy(LibraryView.OWN)
        val tester = show(LibraryView.OWN)
        val shown = deepCard(LibraryView.OWN, all)
        endTheProcess(tester)
        compose.awaitCard()
        assertEquals(listOf("Recipe 50"), compose.textsOf(CardTags.TITLE))
        assertEquals(all[50].id.value, session.viewing.value)
        systemBack()
        assertInView(LibraryView.OWN, all, shown)
        assertNull(session.viewing.value)
    }

    // The process ends in the editor: the editor is open again on its recipe, and back leads to that recipe's card.
    @Test
    fun theEndOfTheProcessInTheEditorReturnsToIt() {
        val all = seventy(LibraryView.OWN)
        val tester = show(LibraryView.OWN)
        deepCard(LibraryView.OWN, all)
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        endTheProcess(tester)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        assertEquals(all[50].id.value, session.editing.value)
        systemBack()
        compose.awaitCard()
        assertEquals(listOf("Recipe 50"), compose.textsOf(CardTags.TITLE))
        assertNull(session.editing.value)
    }

    private fun gesture(progress: Float) = BackEventCompat(progress * 1000f, 500f, progress, BackEventCompat.EDGE_LEFT)

    private fun cardLeft() = compose.onNodeWithTag(CardTags.SCREEN).fetchSemanticsNode().boundsInRoot.left

    // Scenario 4: the back gesture held on a card shows the book beneath as the card slides away; cancelled, the card
    // stays; released, the book is shown where it was.
    @Test
    fun thePredictiveBackGestureShowsTheBookBeneathTheCard() {
        val all = seventy(LibraryView.OWN)
        show(LibraryView.OWN)
        val shown = deepCard(LibraryView.OWN, all)
        val dispatcher = compose.activity.onBackPressedDispatcher
        assertEquals(0f, cardLeft())

        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.5f))
        }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.OWN_LIST)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(CardTags.SCREEN).assertExists()
        assertTrue("the card has not moved", cardLeft() > 0f)

        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.OWN_LIST), PATIENCE_MS)
        assertEquals(0f, cardLeft())
        assertEquals(all[50].id.value, session.viewing.value)

        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.5f))
            dispatcher.onBackPressed()
        }
        assertInView(LibraryView.OWN, all, shown)
        assertNull(session.viewing.value)
    }
}
