package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The editor's top app bar — back at its start, the checklist and "Done" at its end — and "Done" saying "Saved" for a
 * moment once what is written is in the book, never over an Undo still offered.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditorTopBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private var closed = 0

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
    }

    @After
    fun close() {
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private val flour = Ingredient(0, null, null, "Flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))
    private val milk = Ingredient(1, null, null, "Milk", UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(300))

    private fun open(): Recipe {
        val recipe = Recipe.create(now, null).copy(title = "Pancakes", ingredients = listOf(flour, milk))
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent {
            MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = { closed++ }) }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe
    }

    private fun stored(recipe: Recipe): Recipe = runBlocking { recipes.get(recipe.id) }!!

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun px(dp: Float): Float = dp * compose.density.density

    private fun shown(text: String): Boolean = compose.onAllNodes(
        hasText(text) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

    private fun inBar(tag: String): Boolean =
        compose.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(EditorTags.BAR))).fetchSemanticsNodes().size == 1

    // The editor has an M3 top app bar, as the book and the card do: back at its start, then the checklist and "Done"
    // at its end, each 48 dp and announced as what it does; back still leaves for the recipe.
    @Test
    fun theTopBarHoldsBackTheChecklistAndDone() = topBar("Back", "Show the recipe", "Done", "Save what is written")

    @Test
    @Config(qualifiers = "ru")
    fun theTopBarHoldsBackTheChecklistAndDoneInRussian() = topBar("Назад", "Показать рецепт", "Готово", "Сохранить написанное")

    private fun topBar(back: String, backAction: String, done: String, doneAction: String) {
        open()
        listOf(EditorTags.BACK, ChecklistTags.CONTROL, EditorTags.DONE).forEach { tag ->
            assertTrue("$tag is not in the bar", inBar(tag))
            val b = bounds(tag)
            assertTrue("$tag is smaller than 48 dp: $b", b.width >= px(48f) - 0.5f && b.height >= px(48f) - 0.5f)
        }
        assertTrue("the bar is not above the form", bounds(EditorTags.BAR).bottom <= bounds(EditorTags.FORM).top + 0.5f)
        // Back at the start, "Done" last at the end, the checklist before it.
        assertTrue(bounds(EditorTags.BACK).right <= bounds(ChecklistTags.CONTROL).left)
        assertTrue(bounds(ChecklistTags.CONTROL).right <= bounds(EditorTags.DONE).left)
        val backNode = node(EditorTags.BACK)
        assertEquals(back, AccessibilityChecks.announcedLabel(backNode))
        assertEquals(backAction, AccessibilityChecks.clickActionLabel(backNode))
        val doneNode = node(EditorTags.DONE)
        assertEquals(done, AccessibilityChecks.announcedLabel(doneNode))
        assertEquals(doneAction, AccessibilityChecks.clickActionLabel(doneNode))

        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { closed == 1 }
    }

    // "Done" leaves the field, waits for what is written to be in the book, then says "Saved" for a moment; the editor
    // stays open.
    @Test
    fun doneSaysSavedOnceTheChangesAreWritten() {
        val recipe = open()
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextReplacement("Pancakes with jam")
        compose.onNodeWithTag(EditorTags.DONE).performClick()
        compose.awaitStatement(context.getString(R.string.editor_saved))
        assertEquals("Pancakes with jam", stored(recipe).title)
        assertFalse(node(EditorTags.TITLE).config.getOrElse(SemanticsProperties.Focused) { false })
        assertEquals(0, closed)
        // Short: it carries no action.
        assertFalse(shown(context.getString(R.string.editor_undo)))
    }

    // "Saved" never replaces an Undo still offered: the removal stays undoable; once it is undone, "Done" says "Saved".
    @Test
    fun savedDoesNotReplaceUndo() {
        val recipe = open()
        compose.onNodeWithTag(EditorTags.ingredientRemove(0)).performScrollTo().performClick()
        val removed = context.getString(R.string.editor_ingredient_removed, 1)
        compose.awaitStatement(removed)
        compose.onNodeWithTag(EditorTags.DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(recipe).ingredients == listOf(milk.copy(position = 0)) }
        compose.waitForIdle()
        assertTrue(shown(removed))
        assertFalse(shown(context.getString(R.string.editor_saved)))

        compose.onNode(
            hasText(context.getString(R.string.editor_undo)) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
            useUnmergedTree = true,
        ).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(recipe).ingredients == listOf(flour, milk) }
        compose.onNodeWithTag(EditorTags.DONE).performClick()
        compose.awaitStatement(context.getString(R.string.editor_saved))
    }
}
