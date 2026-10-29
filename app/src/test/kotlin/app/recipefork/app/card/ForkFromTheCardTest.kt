package app.recipefork.app.card

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Scenario 5 of the increment (FORK-001, FORK-007 (4), FLOW-029): on the card, fork → the editor opens on an editable
 * copy; an edit to it leaves the original unchanged; leaving the editor shows the fork's card, which names the
 * original and opens it; both are in the own-recipes view.
 */
@OptIn(ExperimentalTestApi::class)
// A phone-sized window: with the book's two-row title bar and a version's provenance line, the default 320 × 470 dp
// window leaves the second entry below the fold when the test returns to the book.
@Config(qualifiers = "w360dp-h640dp")
@RunWith(AndroidJUnit4::class)
class ForkFromTheCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-09-20T09:00:00Z")
    private val now = Instant.parse("2026-10-02T12:00:00Z")

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

    private fun original(state: RecipeState) = Recipe.create(created, null).copy(
        title = "Borscht",
        description = "My grandmother's way.",
        servings = 4,
        sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT,
        ingredients = listOf(
            Ingredient(0, 1, unitType = UnitType.EXACT, unit = QuantityUnit.KILOGRAM, quantity = Rational.of(1)),
            Ingredient(1, null, freeTextName = "Beet", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(2), divisible = false),
        ),
        steps = listOf(Step(0, "Boil everything.")),
    ).inState(state)

    private fun show() {
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = editor, card = card) } }
    }

    private fun stored(id: RecipeId) = runBlocking { recipes.get(id) }

    // Scenario 5, from an editable and from a frozen original.
    @Test
    fun forkingAnEditableRecipeOpensTheEditorOnACopyThatNamesItsParent() = forkAndEdit(RecipeState.LOCAL)

    @Test
    fun forkingAFrozenRecipeOpensTheEditorOnACopyThatNamesItsParent() = forkAndEdit(RecipeState.PUBLISHED)

    private fun forkAndEdit(state: RecipeState) {
        val original = original(state).also { runBlocking { recipes.save(it) } }
        session.choose(LibraryView.OWN)
        session.openCard(original.id.value)
        show()
        compose.awaitCard()
        compose.forkFromTheCard()
        // The editor opens on the fork, a new recipe, editable.
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        val forkId = RecipeId(requireNotNull(session.editing.value))
        assertNotEquals(original.id, forkId)
        compose.onNodeWithTag(CardTags.SCREEN).assertDoesNotExist()
        val fork = requireNotNull(stored(forkId))
        assertEquals(original.id, fork.parent)
        assertEquals(RecipeState.LOCAL, fork.state)
        assertEquals(SentStatus.NEVER, fork.sent)
        assertFalse(fork.wasPublished)
        assertFalse(fork.replacesParent)
        assertNull(fork.owner)
        assertEquals(now, fork.createdAt)
        assertEquals(now, fork.contentChangedAt)
        assertEquals(original.ingredients, fork.ingredients)
        assertEquals(original.steps, fork.steps)
        assertEquals(original.description, fork.description)
        assertEquals("Borscht", compose.textsOf(EditorTags.TITLE).last())
        // An edit to the fork leaves the original as it was.
        compose.onNodeWithTag(EditorTags.TITLE).performTextReplacement("Green borscht")
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        // Leaving the editor shows the fork's card, which names the original.
        compose.awaitCard()
        assertEquals(forkId.value, session.viewing.value)
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf("Green borscht") }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.PROVENANCE), PATIENCE_MS)
        assertEquals(listOf("after “Borscht”"), compose.textsOf(CardTags.PROVENANCE))
        assertEquals("Green borscht", stored(forkId)?.title)
        assertEquals(original, stored(original.id))
        // The provenance opens the original's card.
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf("Borscht") }
        assertEquals(original.id.value, session.viewing.value)
        compose.onNodeWithTag(CardTags.PROVENANCE).assertDoesNotExist()
        // Back from it: the own view lists both.
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(forkId.value)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.entry(original.id.value)).assertExists()
        val listed = runBlocking { book.database.recipes().newest(10).first() }.map { it.recipeId }
        assertEquals(listOf(forkId.value, original.id.value), listed)
    }

    // Forking twice makes two forks, both of the same original: the first, unchanged, is offered, and another is
    // asked for (FLOW-015 (2)).
    @Test
    fun forkingAgainMakesAnotherFork() {
        val original = original(RecipeState.LOCAL).also { runBlocking { recipes.save(it) } }
        session.openCard(original.id.value)
        show()
        compose.awaitCard()
        compose.forkFromTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        val first = requireNotNull(session.editing.value)
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.runOnIdle { session.openCard(original.id.value) }
        compose.awaitCard()
        compose.forkFromTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.FORK_HELD), PATIENCE_MS)
        compose.onNodeWithTag(CardTags.FORK_HELD_ANOTHER).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        val second = requireNotNull(session.editing.value)
        assertNotEquals(first, second)
        assertEquals(original.id, stored(RecipeId(second))?.parent)
        assertEquals(original.id, stored(RecipeId(first))?.parent)
        assertEquals(3, runBlocking { recipes.count() })
    }
}
