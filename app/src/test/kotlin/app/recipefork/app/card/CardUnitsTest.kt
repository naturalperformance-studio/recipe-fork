package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.data.HeldUnitSystemSetting
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * v0.35 T-248 on the card: an open card shows its amounts anew as the units setting changes, its step references with
 * them; a fix typed on a converted line, in the unit it is shown in, scales the recipe and the line reads what was typed.
 */
@RunWith(AndroidJUnit4::class)
class CardUnitsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val units = HeldUnitSystemSetting(UnitSystem.METRIC)
    private val environment = CardEnvironment(
        lastTab = HeldLastCardTab(CardMode.COOKING),
        unitSystem = units,
        ingredients = IngredientEntries { UNIT_ENTRIES.values.toList() },
    )

    /** Scenario 3's stew for 4, written in grams and millilitres; its step names the beef. */
    private val stew = Recipe.create(Instant.parse("2026-10-12T12:00:00Z"), null).copy(
        title = "Stew",
        servings = 4,
        ingredients = GRAMS_RECIPE,
        steps = listOf(Step(0, "Brown .", listOf(IngredientReference(1, 6)))),
    )

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun open() {
        runBlocking { recipes.save(stew) }
        compose.setContent { MaterialTheme { CardScreen(stew.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')
    private fun quantity(position: Int) = plain(compose.textsOf(CardTags.ingredient(position))[1])
    private fun step() = plain(compose.textsOf(StepTags.text(0)).single())
    private fun controlShown() = compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun anOpenCardShowsItsAmountsAnewAsTheSettingChanges() {
        open()
        assertEquals("500 g", quantity(1))
        assertTrue(step(), step().startsWith("Brown 500 g"))
        units.set(UnitSystem.US)
        compose.waitUntil(PATIENCE_MS) { quantity(1) == "17½ oz" }
        assertEquals("≈ 2 cup", quantity(0))
        assertTrue(step(), step().startsWith("Brown 17½ oz"))
        // Shown in other units than written, the list says it is recalculated.
        assertTrue(compose.onAllNodes(hasTestTag(IngredientTags.RECALCULATED), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        units.set(UnitSystem.METRIC)
        compose.waitUntil(PATIENCE_MS) { quantity(1) == "500 g" }
    }

    @Test
    fun aFixTypedInCupsOnAConvertedLineScalesTheRecipeAndTheLineReadsIt() {
        units.set(UnitSystem.US)
        open()
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { controlShown() }
        // Flour is shown in cups, so the amount is typed in cups, the one unit offered.
        assertTrue(compose.onAllNodes(hasTestTag(FixTags.UNIT)).fetchSemanticsNodes().isEmpty())
        val cup = hasText("cup") and hasAnyAncestor(hasTestTag(CardTags.FIX))
        assertTrue(compose.onAllNodes(cup, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("3")
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        compose.waitUntil(PATIENCE_MS) { quantity(0) == "≈ 3 cup" }
        assertEquals("26½ oz", quantity(1))
        assertEquals(listOf("Servings: ≈6"), compose.textsOf(CardTags.SERVINGS).take(1))
        // Opened again, the control holds what was typed, in cups.
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { controlShown() }
        assertEquals("3", compose.textsOf(CardTags.FIX_FIELD).last())
    }

    @Test
    fun aFixingReopenedAfterTheSettingChangedHoldsTheValueTheLineShows() {
        open()
        compose.onNodeWithTag(CardTags.fixAmount(1), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { controlShown() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("600")
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        units.set(UnitSystem.US)
        compose.waitUntil(PATIENCE_MS) { quantity(1) == "21 oz" }
        compose.onNodeWithTag(CardTags.fixAmount(1), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { controlShown() }
        // Not the raw 600 g in ounces, 960000000/45359237: what the line shows, accepted as it stands.
        assertEquals("21", compose.textsOf(CardTags.FIX_FIELD).last())
        assertTrue(compose.onAllNodes(hasTestTag(CardTags.FIX_PROBLEM), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).assertIsEnabled()
    }
}
