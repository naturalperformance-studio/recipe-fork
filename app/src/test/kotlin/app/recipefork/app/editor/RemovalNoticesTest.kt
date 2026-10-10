package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.EquipmentEntryDto
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The editor's removal notices name what went (v0.30, T-217), in English and in Russian: an ingredient by its name as
 * its line reads it — a dictionary row by its label in the interface language — or "Ingredient removed" where it has
 * none; a heading by its words; an item of equipment by its name; a step, which has none, by its number. Undo still puts
 * back what each names.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RemovalNoticesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        // The test ingredients, and the one item of equipment named here.
        val pan = EquipmentEntryDto(10001, "Сковорода", "Frying pan")
        runBlocking { device.database.dictionaryDao().replace(TEST_INGREDIENTS.copy(equipment = listOf(pan))) }
    }

    @After
    fun close() {
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private class Words(
        val sugar: String,
        val glaze: String,
        val qualifier: String,
        val milkRemoved: String,
        val sugarRemoved: String,
        val unnamedRemoved: String,
        val glazeRemoved: String,
        val panRemoved: String,
        val whiskRemoved: String,
        val stepRemoved: String,
    )

    private val english = Words(
        sugar = "Sugar",
        glaze = "Glaze",
        qualifier = "26 cm",
        milkRemoved = "“milk” removed",
        sugarRemoved = "“Sugar” removed",
        unnamedRemoved = "Ingredient removed",
        glazeRemoved = "“Glaze” removed",
        panRemoved = "“Frying pan 26 cm” removed",
        whiskRemoved = "“Whisk” removed",
        stepRemoved = "Step 2 removed",
    )

    private val russian = Words(
        sugar = "сахар",
        glaze = "Глазурь",
        qualifier = "26 см",
        milkRemoved = "Убрано: молоко",
        sugarRemoved = "Убрано: сахар",
        unnamedRemoved = "Ингредиент убран",
        glazeRemoved = "Заголовок «Глазурь» убран",
        panRemoved = "Убрано: Сковорода 26 см",
        whiskRemoved = "Убрано: Whisk",
        stepRemoved = "Шаг 2 убран",
    )

    /** Milk from the dictionary, sugar in the author's words, a row with no name; a heading over the sugar; two items. */
    private fun recipe(w: Words) = Recipe.create(now, null).copy(
        title = "Cake",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, 1, null, null, UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(200)),
            Ingredient(1, null, null, w.sugar, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(100)),
            Ingredient(2, null, null, null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(50)),
        ),
        ingredientGroups = listOf(IngredientGroup(w.glaze, 1)),
        equipment = listOf(EquipmentItem.entry(10001, w.qualifier), EquipmentItem.free("Whisk")),
        steps = listOf(Step(0, "Mix."), Step(1, "Bake.")),
    )

    private fun open(recipe: Recipe): Recipe {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        // The dictionary is read: the milk's row reads its label.
        compose.waitUntil(PATIENCE_MS) { compose.ingredientLine(0).contains(context.getString(R.string.editor_ingredient_unnamed)).not() }
        return recipe
    }

    private fun stored(recipe: Recipe): Recipe = runBlocking { recipes.get(recipe.id) }!!

    private fun undo() {
        compose.onNode(
            hasText(context.getString(R.string.editor_undo)) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
            useUnmergedTree = true,
        ).performClick()
    }

    private fun eachRemovalIsNamed(w: Words) {
        val recipe = open(recipe(w))

        // A dictionary row, by its label in the interface language; Undo puts it back.
        compose.removeIngredient(0)
        compose.awaitStatement(w.milkRemoved)
        undo()
        compose.waitUntil(PATIENCE_MS) { stored(recipe).ingredients.size == 3 }
        assertEquals(recipe.ingredients, stored(recipe).ingredients)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

        // A heading, by its words.
        compose.removeGroup(0)
        compose.awaitStatement(w.glazeRemoved)
        compose.swipeAwayStatement()

        // A row in the author's own words, by them.
        compose.removeIngredient(1)
        compose.awaitStatement(w.sugarRemoved)
        compose.swipeAwayStatement()

        // A row with no name: what kind of thing it was.
        compose.removeIngredient(1)
        compose.awaitStatement(w.unnamedRemoved)
        compose.swipeAwayStatement()

        // An item of equipment: an entry's by its label and qualifier, free text by its words.
        compose.onNodeWithTag(EditorTags.equipmentRemove(0)).performScrollTo().performClick()
        compose.awaitStatement(w.panRemoved)
        compose.swipeAwayStatement()
        compose.onNodeWithTag(EditorTags.equipmentRemove(0)).performScrollTo().performClick()
        compose.awaitStatement(w.whiskRemoved)
        undo()
        compose.waitUntil(PATIENCE_MS) { stored(recipe).equipment == listOf(EquipmentItem.free("Whisk")) }

        // A step, which has no name, by its number.
        compose.removeStep(1)
        compose.awaitStatement(w.stepRemoved)
        undo()
        compose.waitUntil(PATIENCE_MS) { stored(recipe).steps.size == 2 }
    }

    @Test
    fun eachRemovalNamesWhatWent() = eachRemovalIsNamed(english)

    @Test
    @Config(qualifiers = "ru")
    fun eachRemovalNamesWhatWentInRussian() = eachRemovalIsNamed(russian)
}
