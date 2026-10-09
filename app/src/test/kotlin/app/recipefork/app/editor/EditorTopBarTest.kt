package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.text.TextLayoutResult
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
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CompletableDeferred
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
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.time.Instant

/**
 * The editor's top app bar — back and the recipe's title at its start, the checklist and "Done" at its end — and "Done",
 * which closes the editor once what is written is in the book, and keeps it open, saying so, where the book refused it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditorTopBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = Refusable(ActiveBook(BookName.Pool, book.database))
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

    /** The book's recipes, whose writes can be held back until [gate] opens, or refused while [refuse]. */
    private class Refusable(book: ActiveBook) : RecipeRepository(book) {
        @Volatile var gate: CompletableDeferred<Unit>? = null
        @Volatile var refuse = false

        override suspend fun save(recipe: Recipe) {
            gate?.await()
            if (refuse) throw IOException("the book refused the write")
            super.save(recipe)
        }
    }

    private fun open(state: RecipeState = RecipeState.LOCAL, title: String = "Pancakes"): Recipe {
        val recipe = Recipe.create(now, null)
            .copy(title = title, ingredients = listOf(flour, milk), sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT)
            .inState(state)
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
    fun theTopBarHoldsBackTheChecklistAndDone() = topBar("Back", "Show the recipe", "Done", "Save and close")

    @Test
    @Config(qualifiers = "ru")
    fun theTopBarHoldsBackTheChecklistAndDoneInRussian() = topBar("Назад", "Показать рецепт", "Готово", "Сохранить и закрыть")

    private fun topBar(back: String, backAction: String, done: String, doneAction: String) {
        open()
        listOf(EditorTags.BACK, EditorTags.FORK_NOTE, ChecklistTags.CONTROL, EditorTags.DONE).forEach { tag ->
            assertTrue("$tag is not in the bar", inBar(tag))
            val b = bounds(tag)
            assertTrue("$tag is smaller than 48 dp: $b", b.width >= px(48f) - 0.5f && b.height >= px(48f) - 0.5f)
        }
        assertTrue("the bar is not above the form", bounds(EditorTags.BAR).bottom <= bounds(EditorTags.FORM).top + 0.5f)
        // Back at the start, "Done" last at the end, the checklist before it.
        assertTrue(bounds(EditorTags.BACK).right <= bounds(EditorTags.FORK_NOTE).left)
        assertTrue(bounds(EditorTags.FORK_NOTE).right <= bounds(ChecklistTags.CONTROL).left)
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

    // The bar shows the recipe's title, on one line, cut short with an ellipsis where it is too long, between back and
    // the actions; it follows the title as it is typed.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h640dp")
    fun theBarShowsTheTitle() {
        open()
        assertEquals(listOf("Pancakes"), compose.textsOf(EditorTags.BAR_TITLE))
        assertTrue(inBar(EditorTags.BAR_TITLE))
        assertTrue(bounds(EditorTags.BACK).right <= bounds(EditorTags.BAR_TITLE).left + 0.5f)
        assertTrue(bounds(EditorTags.BAR_TITLE).right <= bounds(EditorTags.FORK_NOTE).left + 0.5f)
        val long = "Pancakes with jam, sour cream, honey and the berries of the season, as grandmother made them"
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextReplacement(long)
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(EditorTags.BAR_TITLE) == listOf(long) }
        val title = compose.onNodeWithTag(EditorTags.BAR_TITLE).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { title.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()
        assertEquals(1, layout.lineCount)
        assertTrue("the long title is not cut short", layout.isLineEllipsized(0))
        assertTrue(bounds(EditorTags.BAR_TITLE).right <= bounds(EditorTags.FORK_NOTE).left + 0.5f)
    }

    // A recipe with no title yet is "New recipe" in the bar.
    @Test
    fun aRecipeWithNoTitleIsANewRecipe() = newRecipe("New recipe")

    @Test
    @Config(qualifiers = "ru")
    fun aRecipeWithNoTitleIsANewRecipeInRussian() = newRecipe("Новый рецепт")

    private fun newRecipe(label: String) {
        open(title = "")
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(EditorTags.BAR_TITLE) == listOf(label) }
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextReplacement("Borscht")
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(EditorTags.BAR_TITLE) == listOf("Borscht") }
        compose.onNodeWithTag(EditorTags.TITLE).performTextReplacement("  ")
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(EditorTags.BAR_TITLE) == listOf(label) }
    }

    // At twice the font, in Russian, on a 320 dp phone: back, the title, what a fork takes, the checklist and "Done"
    // stand side by side without overlapping and within the screen, each action 48 dp — for a long title and for none.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2.0f)
    fun theBarFitsAt320DpAndTwiceTheFontInRussian() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        open(title = "Блины со сметаной, мёдом и ягодами по-бабушкиному")
        barFits()
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextReplacement("")
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(EditorTags.BAR_TITLE) == listOf("Новый рецепт") }
        barFits()
    }

    // A frozen recipe's bar — back, its title, what a fork takes and the checklist, with no "Done" — fits there too.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2.0f)
    fun aFrozenRecipesBarFitsAt320DpAndTwiceTheFontInRussian() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        open(RecipeState.PUBLISHED, title = "Блины со сметаной, мёдом и ягодами по-бабушкиному")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FROZEN), PATIENCE_MS)
        assertFalse(inBar(EditorTags.DONE))
        assertEquals(listOf("Блины со сметаной, мёдом и ягодами по-бабушкиному"), compose.textsOf(EditorTags.BAR_TITLE))
        barFits(listOf(EditorTags.BACK, EditorTags.BAR_TITLE, EditorTags.FORK_NOTE, ChecklistTags.CONTROL))
    }

    private fun barFits(
        tags: List<String> = listOf(EditorTags.BACK, EditorTags.BAR_TITLE, EditorTags.FORK_NOTE, ChecklistTags.CONTROL, EditorTags.DONE),
    ) {
        val order = tags.map { it to bounds(it) }
        order.forEach { (tag, b) ->
            assertTrue("$tag runs off the screen: $b", b.left >= -0.5f && b.right <= px(320f) + 0.5f)
            if (tag != EditorTags.BAR_TITLE) {
                assertTrue("$tag is smaller than 48 dp: $b", b.width >= px(48f) - 0.5f && b.height >= px(48f) - 0.5f)
            }
        }
        order.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.first} ${a.second} overlaps ${b.first} ${b.second}", a.second.right <= b.second.left + 0.5f)
        }
    }

    // "Done" leaves the field, waits for what is written to be in the book — the editor still open while it is not —
    // then closes the editor; it says nothing, "Saved" no more.
    @Test
    fun doneClosesTheEditorOnceTheChangesAreWritten() {
        val recipe = open()
        val gate = CompletableDeferred<Unit>().also { recipes.gate = it }
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextReplacement("Pancakes with jam")
        compose.onNodeWithTag(EditorTags.DONE).performClick()
        compose.waitForIdle()
        assertFalse(node(EditorTags.TITLE).config.getOrElse(SemanticsProperties.Focused) { false })
        assertEquals("Pancakes", stored(recipe).title)
        assertEquals(0, closed)
        compose.onNodeWithTag(EditorTags.DONE).performClick()

        gate.complete(Unit)

        compose.waitUntilWithMainThread { closed == 1 }
        assertEquals("Pancakes with jam", stored(recipe).title)
        compose.waitForIdle()
        assertFalse(exists(EditorTags.STATEMENT))
        // A second tap while the first waited closes nothing more.
        assertEquals(1, closed)
    }

    // A write the book refuses keeps the editor open, and "Done" says so; once a later write is taken, "Done" closes it.
    @Test
    fun aRefusedWriteKeepsTheEditorOpenAndSaysSo() = refused("Couldn't save the recipe")

    @Test
    @Config(qualifiers = "ru")
    fun aRefusedWriteKeepsTheEditorOpenAndSaysSoInRussian() = refused("Не удалось сохранить рецепт")

    private fun refused(said: String) {
        val recipe = open()
        recipes.refuse = true
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextReplacement("Pancakes with jam")
        compose.onNodeWithTag(EditorTags.DONE).performClick()
        compose.awaitStatement(said)
        assertEquals(0, closed)
        assertEquals("Pancakes", stored(recipe).title)
        compose.onNodeWithTag(EditorTags.FORM).assertExists()

        recipes.refuse = false
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextReplacement("Pancakes with honey")
        compose.onNodeWithTag(EditorTags.DONE).performClick()
        compose.waitUntilWithMainThread { closed == 1 }
        assertEquals("Pancakes with honey", stored(recipe).title)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
