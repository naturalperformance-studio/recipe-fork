package app.recipefork.app.card

import app.recipefork.app.R
import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.fork
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Instant

/**
 * Scenario 5 of the increment (FLOW-015): forking a recipe of which the book holds an unchanged fork says so and offers
 * to open it or make another; nothing is made unless another is asked for. A fork changed in the editor is not offered:
 * forking again then makes a fork and says nothing.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ForkHeldOfferTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-09-20T09:00:00Z")
    private val forked = Instant.parse("2026-10-02T12:00:00Z")
    @Volatile private var now = Instant.parse("2026-10-02T12:30:00Z")

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

    private val original = Recipe.create(created, null).copy(
        title = "Borscht",
        description = "My grandmother's way.",
        servings = 4,
        steps = listOf(Step(0, "Boil everything.")),
    ).inState(RecipeState.LOCAL)

    private fun show() {
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = editor, card = card) } }
    }

    private fun count() = runBlocking { recipes.count() }

    private fun dialogShown() = compose.onAllNodes(hasTestTag(CardTags.FORK_HELD)).fetchSemanticsNodes().isNotEmpty()

    /** Opens the original's card and forks it, then waits for the offer. */
    private fun forkAndAwaitOffer() {
        session.choose(LibraryView.OWN)
        session.openCard(original.id.value)
        show()
        compose.awaitCard()
        compose.forkFromTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.FORK_HELD), PATIENCE_MS)
    }

    private fun heldFork(): Recipe {
        runBlocking { recipes.save(original) }
        return original.fork(forked, null).also { runBlocking { recipes.save(it) } }
    }

    // FLOW-015 (1): the held fork is offered; opening it opens the editor on it and makes nothing.
    @Test
    fun anUnchangedForkIsOfferedAndOpened() {
        val held = heldFork()
        forkAndAwaitOffer()
        compose.onNodeWithText("You already have a fork of this recipe, unchanged since you made it.").assertExists()
        assertEquals(2, count())
        compose.onNodeWithTag(CardTags.FORK_HELD_OPEN).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        assertEquals(held.id.value, session.editing.value)
        assertEquals(2, count())
    }

    // FLOW-015 (1): dismissing the offer makes nothing and leaves the card.
    @Test
    fun dismissingTheOfferMakesNoFork() {
        heldFork()
        forkAndAwaitOffer()
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            ShadowDialog.getLatestDialog().onBackPressed()
        }
        compose.waitUntilDoesNotExist(hasTestTag(CardTags.FORK_HELD), PATIENCE_MS)
        compose.onNodeWithTag(CardTags.SCREEN).assertExists()
        assertEquals(null, session.editing.value)
        assertEquals(2, count())
    }

    // FLOW-015 (2): asking for another makes one.
    @Test
    fun askingForAnotherMakesASecondFork() {
        val held = heldFork()
        forkAndAwaitOffer()
        compose.onNodeWithTag(CardTags.FORK_HELD_ANOTHER).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        val second = RecipeId(requireNotNull(session.editing.value))
        assertNotEquals(held.id, second)
        assertEquals(original.id, runBlocking { recipes.get(second) }?.parent)
        assertEquals(3, count())
    }

    // FLOW-015 (3, 5): no fork is made silently; one changed in the editor is not offered, and forking again is silent.
    @Test
    fun aForkChangedInTheEditorIsNotOffered() {
        runBlocking { recipes.save(original) }
        session.choose(LibraryView.OWN)
        session.openCard(original.id.value)
        show()
        compose.awaitCard()
        now = forked
        compose.forkFromTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        assertEquals(false, dialogShown())
        val first = requireNotNull(session.editing.value)
        // The author edits the fork a while later.
        now = Instant.parse("2026-10-02T12:05:00Z")
        compose.onNodeWithTag(EditorTags.TITLE).performTextReplacement("Green borscht")
        compose.waitUntil(PATIENCE_MS) { runBlocking { recipes.get(RecipeId(first)) }?.title == "Green borscht" }
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.TITLE) and hasText("Borscht"), PATIENCE_MS)
        assertEquals(original.id.value, session.viewing.value)
        compose.forkFromTheCard()
        compose.waitForIdle()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        assertNotEquals(first, session.editing.value)
        assertEquals(false, dialogShown())
        assertEquals(3, count())
    }

    // Scenario 4 of v0.41: the offer is headed by the provenance glyph at 32 dp, above its words and unread.
    @Test
    fun theOfferIsHeadedByTheProvenanceGlyph() {
        heldFork()
        forkAndAwaitOffer()
        val glyphs = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(CardTags.FORK_HELD)) and SemanticsMatcher.expectValue(DrawnIcon, R.drawable.ic_provenance),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(1, glyphs.size)
        val glyph = glyphs.single()
        assertNull(glyph.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null })
        val side = (32 * context.resources.displayMetrics.density).toInt()
        assertEquals(side, glyph.size.width)
        assertEquals(side, glyph.size.height)
        val words = compose.onNodeWithText("You already have a fork of this recipe, unchanged since you made it.").fetchSemanticsNode()
        assertTrue("${glyph.boundsInRoot} above ${words.boundsInRoot}", glyph.boundsInRoot.bottom <= words.boundsInRoot.top)
    }

    // The offer in the interface's other language.
    @Test
    @Config(qualifiers = "ru")
    fun theOfferIsSaidInRussian() {
        heldFork()
        forkAndAwaitOffer()
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("У вас уже есть своя версия этого рецепта, она не менялась с тех пор, как вы её сделали.").assertExists()
        compose.onNodeWithText("Открыть её").assertExists()
        compose.onNodeWithText("Сделать ещё одну").assertExists()
    }
}
