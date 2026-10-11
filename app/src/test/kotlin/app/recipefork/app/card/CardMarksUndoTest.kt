package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * v0.30, T-220 — the card's done marks (FLOW-068) as the screen reader and the reader meet them:
 *
 * - "Clear marks" is in the ⋮ of either tab while the recipe has marks; clearing says so in the card's snackbar with
 *   Undo, which puts back exactly the marks cleared; a mark made since ends the Undo.
 * - On Cooking a mark is a check box: toggleable, in the check box role, its state on or off, read "Done" or "Not done".
 * - On View a mark is read-only: its state read in words, "Done" or "Not done", with neither a click, nor the check
 *   box role, nor a toggleable state; a recipe with no mark at all carries no state on View.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class CardMarksUndoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val marks = DoneMarks.inMemory()
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    private val pilaf = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        // Two items, so that one marked does not fold the list.
        equipment = listOf(EquipmentItem(null, freeTextName = "Pot"), EquipmentItem(null, freeTextName = "Spoon")),
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Rice", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(350)),
        ),
        steps = listOf(Step(0, "Fry the chicken."), Step(1, "Add the rice."), Step(2, "Season it.")),
    )

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun open(tab: CardMode) {
        runBlocking { recipes.save(pilaf) }
        val environment = CardEnvironment(marks = marks, lastTab = HeldLastCardTab(tab), ingredients = IngredientEntries { emptyList() })
        compose.setContent { MaterialTheme { CardScreen(pilaf.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(0)) }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private val parts = listOf(CardTags.equipment(0), CardTags.ingredient(0), CardTags.ingredient(1), CardTags.step(0), CardTags.step(1), CardTags.step(2))

    /** The parts of the pilaf marked done, as held. */
    private fun held(): Set<DoneMark> = marks.held(BookName.Pool, pilaf.id)

    private fun inSnackbar(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(CardTags.SNACKBAR))

    private fun awaitStatement(text: String, action: String) = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(inSnackbar(text), useUnmergedTree = true).fetchSemanticsNodes().size == 1 &&
            compose.onAllNodes(inSnackbar(action), useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }

    private fun clearFromTheMenu() {
        compose.openTheCardMenu()
        compose.onNodeWithTag(CardTags.CLEAR_MARKS).performClick()
        compose.waitUntil(PATIENCE_MS) { held().isEmpty() }
    }

    private fun undo(action: String = "Undo") {
        compose.onNode(inSnackbar(action), useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.SNACKBAR) }
        compose.waitForIdle()
    }

    // Cleared on Cooking, the marks come back with Undo — exactly those cleared, struck and read as done again.
    @Test
    fun clearingOnCookingOffersUndoWhichPutsBackExactlyTheMarksCleared() {
        open(CardMode.COOKING)
        listOf(CardTags.equipment(0), CardTags.ingredient(1), CardTags.step(0)).forEach(::tap)
        val before = held()
        assertEquals(3, before.size)

        clearFromTheMenu()
        awaitStatement("Marks cleared", "Undo")
        compose.waitForIdle()
        parts.forEach {
            val said = if (it == CardTags.step(0)) "Current step" else "Not done"
            assertEquals("$it after clearing", said, node(it).config.getOrNull(SemanticsProperties.StateDescription))
        }

        undo()
        assertEquals(before, held())
        val read = parts.associateWith { node(it).config.getOrNull(SemanticsProperties.StateDescription) }
        assertEquals(
            mapOf(
                CardTags.equipment(0) to "Done", CardTags.ingredient(0) to "Not done", CardTags.ingredient(1) to "Done",
                CardTags.step(0) to "Done", CardTags.step(1) to "Current step", CardTags.step(2) to "Not done",
            ),
            read,
        )
    }

    // View's ⋮ holds Clear marks while the recipe has marks — none while it has none — and its clearing offers Undo too.
    @Test
    fun clearingOnViewOffersUndoToo() {
        marks.hold(BookName.Pool, pilaf.id, setOf(DoneMark.of(pilaf.ingredients[0]), DoneMark.of(pilaf.steps[2])))
        val before = held()
        open(CardMode.VIEW)
        compose.openTheCardMenu()
        assertTrue(exists(CardTags.CLEAR_MARKS))
        compose.assertCardTarget(CardTags.CLEAR_MARKS)
        compose.onNodeWithTag(CardTags.CLEAR_MARKS).performClick()
        compose.waitUntil(PATIENCE_MS) { held().isEmpty() }
        awaitStatement("Marks cleared", "Undo")
        // Nothing left to clear: the ⋮ keeps Delete, and Clear marks goes.
        compose.openTheCardMenu()
        assertFalse(exists(CardTags.CLEAR_MARKS))
        compose.closeTheCardMenu()

        undo()
        assertEquals(before, held())
        assertEquals("Done", node(CardTags.ingredient(0)).config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals("Done", node(CardTags.step(2)).config.getOrNull(SemanticsProperties.StateDescription))
        compose.openTheCardMenu()
        assertTrue(exists(CardTags.CLEAR_MARKS))
    }

    // A mark made after clearing makes the clearing final: its Undo goes, and the new mark alone stays.
    @Test
    fun aMarkMadeAfterClearingEndsTheUndo() {
        open(CardMode.COOKING)
        listOf(CardTags.ingredient(0), CardTags.step(0)).forEach(::tap)
        clearFromTheMenu()
        awaitStatement("Marks cleared", "Undo")
        tap(CardTags.ingredient(1))
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.SNACKBAR) }
        assertEquals(setOf(DoneMark.of(pilaf.ingredients[1])), held())
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianClearingSaysSoAndOffersToPutThemBack() {
        open(CardMode.COOKING)
        tap(CardTags.ingredient(0))
        assertEquals("Готово", node(CardTags.ingredient(0)).config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals("Не готово", node(CardTags.ingredient(1)).config.getOrNull(SemanticsProperties.StateDescription))
        clearFromTheMenu()
        awaitStatement("Отметки сняты", "Вернуть")
        undo("Вернуть")
        assertEquals(setOf(DoneMark.of(pilaf.ingredients[0])), held())
    }

    // On Cooking each mark is a check box: toggleable, in the check box role, on or off, read "Done" or "Not done",
    // its tap named for what it does; a tap toggles it.
    @Test
    fun onCookingAMarkIsACheckBox() {
        open(CardMode.COOKING)
        tap(CardTags.ingredient(0))
        parts.forEach { tag ->
            val config = node(tag).config
            assertEquals("$tag's role", Role.Checkbox, config.getOrNull(SemanticsProperties.Role))
            val on = tag == CardTags.ingredient(0)
            assertEquals("$tag's state", if (on) ToggleableState.On else ToggleableState.Off, config.getOrNull(SemanticsProperties.ToggleableState))
            val said = when {
                on -> "Done"
                tag == CardTags.step(0) -> "Current step"
                else -> "Not done"
            }
            assertEquals("$tag is read", said, config.getOrNull(SemanticsProperties.StateDescription))
            assertEquals("$tag's tap", if (on) "Mark as not done" else "Mark as done", config.getOrNull(SemanticsActions.OnClick)?.label)
        }
    }

    // On View the marks are read-only: state read in words; no click, no check box role, no toggleable state; with no
    // mark at all, no state.
    @Test
    fun onViewAMarkIsReadOnly() {
        open(CardMode.VIEW)
        parts.forEach { tag ->
            val config = node(tag).config
            assertNull("$tag carries no state while nothing is marked", config.getOrNull(SemanticsProperties.ToggleableState))
            assertNull(config.getOrNull(SemanticsProperties.StateDescription))
        }
        compose.chooseCardTab(CardMode.COOKING)
        tap(CardTags.step(1))
        compose.chooseCardTab(CardMode.VIEW)
        parts.forEach { tag ->
            val config = node(tag).config
            val on = tag == CardTags.step(1)
            assertEquals("$tag's state, in words", if (on) "Done" else "Not done", config.getOrNull(SemanticsProperties.StateDescription))
            assertNull("$tag is not toggleable on View", config.getOrNull(SemanticsProperties.ToggleableState))
            assertNull("$tag is no check box on View", config.getOrNull(SemanticsProperties.Role))
            assertNull("$tag answers no tap on View", config.getOrNull(SemanticsActions.OnClick))
        }
    }
}
