package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldUnitSystemSetting
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.FLUID_OUNCE
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.OUNCE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.POUND
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.QuantityUnit.TO_TASTE
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * v0.35 scenario 5 (T-249): a row's unit menu offers the reader's system's units and the shared ones; a row held in a
 * unit of the other system keeps it, shown as written and offered while the row holds it; nothing stored is converted.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UnitSystemEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-12T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)
    private val units = HeldUnitSystemSetting()
    private var shown = false

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

    private fun show() {
        if (shown) return
        shown = true
        val environment = EditorEnvironment(active, device.catalog(context), unitSystem = units) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun writeANewRecipe(): RecipeId {
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    private fun openDirectly(id: RecipeId) {
        show()
        compose.runOnIdle { opened.value = null }
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun seeded(vararg rows: Ingredient): RecipeId {
        val recipe = Recipe.create(created, null).copy(title = "Pancakes", servings = 4, ingredients = rows.toList())
        runBlocking { recipes.save(recipe) }
        return recipe.id
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    /** Opens the unit menu of the row at [position] and returns the units it offers, in the order listed. */
    private fun offered(position: Int): List<QuantityUnit> {
        compose.openIngredient(position)
        compose.onNodeWithTag(EditorTags.ingredientUnit(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(PIECE)), PATIENCE_MS)
        val options = QuantityUnit.entries.associateBy { EditorTags.unitOption(it) }
        return compose.onAllNodes(SemanticsMatcher("a unit option") { it.config.getOrNull(SemanticsProperties.TestTag) in options })
            .fetchSemanticsNodes().map { options.getValue(it.config[SemanticsProperties.TestTag]) }
    }

    /** Picks [unit] in the open unit menu. */
    private fun pick(unit: QuantityUnit) {
        compose.onNodeWithTag(EditorTags.unitOption(unit)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(PIECE)), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun unitShown(position: Int): String {
        compose.openIngredient(position)
        return compose.textsOf(EditorTags.ingredientUnit(position)).last()
    }

    @Test
    fun theMetricSystemOffersItsUnitsAndTheSharedOnes() {
        writeANewRecipe()
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(0)), PATIENCE_MS)
        assertEquals("g", unitShown(0))
        assertEquals(
            listOf(PIECE, GRAM, QuantityUnit.KILOGRAM, QuantityUnit.MILLILITRE, QuantityUnit.LITRE, TEASPOON, TABLESPOON, CUP, PINCH, TO_TASTE, QuantityUnit.TONNE, QuantityUnit.CUBIC_METRE),
            offered(0),
        )
    }

    // A new row starts in ounces; a US unit picked is stored as picked and read back when the recipe is opened again.
    @Test
    fun theUsSystemOffersItsUnitsAndAUsUnitIsSavedAndReadBack() {
        units.set(UnitSystem.US)
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(0)), PATIENCE_MS)
        assertEquals("oz", unitShown(0))
        assertEquals(OUNCE, stored(id).ingredients.single().unit)
        assertEquals(listOf(PIECE, OUNCE, POUND, FLUID_OUNCE, TEASPOON, TABLESPOON, CUP, PINCH, TO_TASTE), offered(0))
        pick(POUND)
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextReplacement("beef")
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("1.5")
        compose.closeIngredient()
        compose.waitUntil(PATIENCE_MS) { stored(id).ingredients.single().quantity == Rational.of(3, 2) }
        assertEquals(POUND, stored(id).ingredients.single().unit)
        assertEquals(UnitType.EXACT, stored(id).ingredients.single().unitType)

        openDirectly(id)
        assertEquals("lb", unitShown(0))
        assertEquals(Rational.of(3, 2), stored(id).ingredients.single().quantity)
    }

    // A metric row opened in US keeps grams, shown and offered in their place while the row holds them; a US row opened
    // in Metric keeps ounces. Nothing stored changes until the author picks another unit, which ends the offer.
    @Test
    fun aRowOfTheOtherSystemKeepsItsUnit() {
        val flour = Ingredient(0, null, null, "flour", UnitType.EXACT, GRAM, Rational.of(500))
        val butter = Ingredient(1, null, null, "butter", UnitType.EXACT, OUNCE, Rational.of(4))
        val id = seeded(flour, butter)
        units.set(UnitSystem.US)
        openDirectly(id)
        assertEquals("g", unitShown(0))
        assertEquals(listOf(PIECE, GRAM, OUNCE, POUND, FLUID_OUNCE, TEASPOON, TABLESPOON, CUP, PINCH, TO_TASTE), offered(0))
        pick(GRAM)
        compose.closeIngredient()
        assertEquals(listOf(flour, butter), stored(id).ingredients)

        units.set(UnitSystem.METRIC)
        assertEquals("oz", unitShown(1))
        assertEquals(
            listOf(PIECE, GRAM, QuantityUnit.KILOGRAM, QuantityUnit.MILLILITRE, QuantityUnit.LITRE, OUNCE, TEASPOON, TABLESPOON, CUP, PINCH, TO_TASTE, QuantityUnit.TONNE, QuantityUnit.CUBIC_METRE),
            offered(1),
        )
        pick(TABLESPOON)
        compose.waitUntil(PATIENCE_MS) { stored(id).ingredients[1].unit == TABLESPOON }
        assertEquals(flour, stored(id).ingredients[0])
        assertEquals(false, OUNCE in offered(1))
    }
}
