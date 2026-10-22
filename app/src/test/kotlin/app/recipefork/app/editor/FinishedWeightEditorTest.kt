package app.recipefork.app.editor

import android.content.Context
import android.text.InputType
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.math.abs

/**
 * v0.44, T-319: the finished dish's weight in the editor — a field right under the serving count, labelled, with the
 * suffix «г» / "g" and a hint that names the serving count as it is typed; whole grams from 1 to 100 000 or nothing,
 * anything else refused as the serving count refuses it; Next goes servings → weight → what the serving count led to
 * before; a recipe that cannot be edited shows the weight as text.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FinishedWeightEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-22T12:00:00Z")

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

    private fun recipe(
        servings: Int? = 2,
        weight: Int? = null,
        equipment: List<String> = emptyList(),
        ingredients: Boolean = true,
    ) = Recipe.create(now, null).copy(
        title = "Сырники",
        servings = servings,
        finishedWeightGrams = weight,
        equipment = equipment.map(EquipmentItem::free),
        ingredients = if (ingredients) listOf(Ingredient(0, null, null, "творог", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(400))) else emptyList(),
        steps = listOf(Step(0, "Жарить.")),
    )

    private fun open(recipe: Recipe): Recipe {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe
    }

    private fun stored(recipe: Recipe): Recipe = runBlocking { recipes.get(recipe.id) }!!

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    /** Where the node tagged [tag] is laid out in the scrolling form, whether or not it is scrolled into view. */
    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun focused(tag: String) = exists(tag) && node(tag).config.getOrElse(SemanticsProperties.Focused) { false }

    private fun fieldText(tag: String): String = node(tag).config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun isMarkedAsError(tag: String): Boolean = SemanticsProperties.Error in node(tag).config

    private fun hint(): String = compose.textsOf(EditorTags.FINISHED_WEIGHT_HINT).single()

    /** The field's label, as its own text among the field's parts. */
    private fun shows(text: String): Boolean = compose.onAllNodes(
        hasAnyAncestor(hasTestTag(EditorTags.FINISHED_WEIGHT)) and hasText(text),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun density() = context.resources.displayMetrics.density

    // The field stands right under the serving count, with the form's 16 dp gap, before the equipment and the
    // ingredients, full width as the serving count is.
    @Test
    fun theFieldStandsRightUnderTheServingCount() {
        open(recipe(equipment = listOf("Сковорода")))
        val servings = bounds(EditorTags.SERVINGS)
        val weight = bounds(EditorTags.FINISHED_WEIGHT)
        val equipment = bounds(EditorTags.EQUIPMENT)
        val ingredients = bounds(EditorTags.INGREDIENTS)
        assertTrue("the weight is not under the serving count", servings.bottom <= weight.top)
        assertTrue("the gap is not the form's 16 dp: ${(weight.top - servings.bottom) / density()} dp", abs((weight.top - servings.bottom) - 16.dp.value * density()) < 1f)
        assertTrue("the equipment is not after the weight", weight.bottom <= equipment.top)
        assertTrue(equipment.bottom <= ingredients.top)
        assertEquals(servings.left, weight.left)
        assertEquals(servings.width, weight.width)
    }

    // EN: label, suffix "g" once the field holds a weight, the hint with the serving count in its plural, and without one.
    @Test
    fun theFieldIsLabelledWithItsHintAndSuffixInEnglish() {
        val recipe = open(recipe(servings = 2))
        assertTrue(shows("Finished dish weight"))
        assertFalse("the suffix shows on an empty field left alone", shows("g"))
        assertEquals("What the whole dish for 2 servings weighs — for nutrition per 100 g", hint())
        // The hint follows the serving count as it is typed.
        type(EditorTags.SERVINGS, "1")
        assertEquals("What the whole dish for 1 serving weighs — for nutrition per 100 g", hint())
        type(EditorTags.SERVINGS, "")
        assertEquals("What the whole dish weighs — for nutrition per 100 g", hint())
        type(EditorTags.FINISHED_WEIGHT, "560")
        compose.waitUntil(PATIENCE_MS) { stored(recipe).finishedWeightGrams == 560 }
        assertTrue("no suffix on a filled field", shows("g"))
        assertEquals("What the whole dish weighs — for nutrition per 100 g", hint())
    }

    // RU: «Вес готового блюда», «г», and «на 1 порцию / 2 порции / 5 порций / 21 порцию» in the hint.
    @Config(qualifiers = "ru")
    @Test
    fun theFieldIsLabelledWithItsHintAndSuffixInRussian() {
        open(recipe(servings = 2))
        assertTrue(shows("Вес готового блюда"))
        assertEquals("Сколько весит всё блюдо на 2 порции — для КБЖУ на 100 г", hint())
        listOf("1" to "1 порцию", "5" to "5 порций", "21" to "21 порцию", "22" to "22 порции", "11" to "11 порций").forEach { (typed, phrase) ->
            type(EditorTags.SERVINGS, typed)
            assertEquals(typed, "Сколько весит всё блюдо на $phrase — для КБЖУ на 100 г", hint())
        }
        type(EditorTags.SERVINGS, "")
        assertEquals("Сколько весит всё блюдо — для КБЖУ на 100 г", hint())
        // The suffix shows while the field is focused, before anything is typed.
        assertFalse(shows("г"))
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.FINISHED_WEIGHT) }
        compose.waitUntil(PATIENCE_MS) { shows("г") }
    }

    // A number keyboard; Next goes servings → weight → the first item of equipment.
    @Test
    fun nextGoesFromTheServingsToTheWeightAndOnToTheEquipment() {
        open(recipe(equipment = listOf("Pan")))
        compose.onNodeWithTag(EditorTags.SERVINGS).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.SERVINGS) }
        assertEquals(ImeAction.Next, node(EditorTags.SERVINGS).config[SemanticsProperties.ImeAction])
        compose.onNodeWithTag(EditorTags.SERVINGS).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.FINISHED_WEIGHT) }
        assertEquals(InputType.TYPE_CLASS_NUMBER, compose.keyboardAskedFor().inputType and InputType.TYPE_MASK_CLASS)
        assertEquals(ImeAction.Next, node(EditorTags.FINISHED_WEIGHT).config[SemanticsProperties.ImeAction])
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.equipmentName(0)) }
    }

    // With no equipment Next goes from the weight to the first ingredient row; with neither, the weight says Done,
    // which closes the keyboard.
    @Test
    fun withNoEquipmentNextGoesToTheIngredientsAndWithNeitherTheWeightSaysDone() {
        open(recipe())
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.FINISHED_WEIGHT) }
        assertEquals(ImeAction.Next, node(EditorTags.FINISHED_WEIGHT).config[SemanticsProperties.ImeAction])
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(0)) }
    }

    @Test
    fun withNeitherEquipmentNorIngredientsTheWeightSaysDone() {
        open(recipe(ingredients = false))
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.FINISHED_WEIGHT) }
        assertEquals(ImeAction.Done, node(EditorTags.FINISHED_WEIGHT).config[SemanticsProperties.ImeAction])
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !focused(EditorTags.FINISHED_WEIGHT) }
    }

    // Empty is no weight and no error; whole grams from 1 to 100 000 are held and saved; 0, a fraction, a sign, a
    // letter or more than 100 000 do not enter — the field and the book keep what they held, the field is drawn as
    // refused and the hint stays; clearing the field removes the weight.
    @Test
    fun theWeightHoldsWholeGramsUpTo100000OrNothing() {
        val recipe = open(recipe())
        assertEquals("", fieldText(EditorTags.FINISHED_WEIGHT))
        assertFalse(isMarkedAsError(EditorTags.FINISHED_WEIGHT))
        assertNull(stored(recipe).finishedWeightGrams)
        val hint = hint()

        listOf("1" to 1, "100000" to 100_000, "560" to 560).forEach { (typed, grams) ->
            type(EditorTags.FINISHED_WEIGHT, typed)
            compose.waitUntil(PATIENCE_MS) { stored(recipe).finishedWeightGrams == grams }
            assertEquals(typed, fieldText(EditorTags.FINISHED_WEIGHT))
            assertFalse(typed, isMarkedAsError(EditorTags.FINISHED_WEIGHT))
        }
        listOf("0", "100001", "5600000", "560.5", "560,5", "-5", "560г", "½", "99999999999").forEach { typed ->
            type(EditorTags.FINISHED_WEIGHT, typed)
            assertEquals(typed, "560", fieldText(EditorTags.FINISHED_WEIGHT))
            assertTrue(typed, isMarkedAsError(EditorTags.FINISHED_WEIGHT))
            assertEquals(typed, hint, hint())
            assertEquals(typed, 560, stored(recipe).finishedWeightGrams)
        }
        // Cleared: no weight, in the field and the book, and not an error.
        type(EditorTags.FINISHED_WEIGHT, "")
        compose.waitUntil(PATIENCE_MS) { stored(recipe).finishedWeightGrams == null }
        assertEquals("", fieldText(EditorTags.FINISHED_WEIGHT))
        assertFalse(isMarkedAsError(EditorTags.FINISHED_WEIGHT))
    }

    // A recipe saved with a weight opens with it in the field; a new weight replaces it in the book.
    @Test
    fun aSavedWeightReadsBack() {
        val recipe = open(recipe(weight = 560))
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.FINISHED_WEIGHT) == "560" }
        assertTrue(shows("g"))
        type(EditorTags.FINISHED_WEIGHT, "600")
        compose.waitUntil(PATIENCE_MS) { stored(recipe).finishedWeightGrams == 600 }
        assertEquals(2, stored(recipe).servings)
    }

    // A recipe that cannot be edited shows the weight as the serving count is shown: the label over the value, one
    // node for the screen reader, nothing to type into.
    @Config(qualifiers = "ru")
    @Test
    fun aFrozenRecipeShowsItsWeightAsText() {
        open(recipe(weight = 560).inState(RecipeState.PUBLISHED))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.FROZEN) }
        assertEquals(listOf("Вес готового блюда", "560 г"), compose.textsOf(EditorTags.FINISHED_WEIGHT))
        assertTrue(compose.onAllNodes(hasSetTextAction() and hasTestTag(EditorTags.FINISHED_WEIGHT)).fetchSemanticsNodes().isEmpty())
        assertTrue(bounds(EditorTags.SERVINGS).bottom <= bounds(EditorTags.FINISHED_WEIGHT).top)
    }

    @Test
    fun aFrozenRecipeWithoutAWeightSaysNotGiven() {
        open(recipe().inState(RecipeState.PUBLISHED))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.FROZEN) }
        assertEquals(listOf("Finished dish weight", "Not given"), compose.textsOf(EditorTags.FINISHED_WEIGHT))
        assertTrue(compose.onAllNodes(hasSetTextAction() and hasTestTag(EditorTags.FINISHED_WEIGHT)).fetchSemanticsNodes().isEmpty())
    }
}
