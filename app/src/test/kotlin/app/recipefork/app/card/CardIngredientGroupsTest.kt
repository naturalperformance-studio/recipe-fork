package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.TestTimers
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * DOM-022 on the card: each group's name stands as a subheading over its lines — a heading for the screen reader —
 * the lines above the first heading under none, a heading with no lines not at all; the lines, their scaling, the
 * references in the steps, the notes, the fixing and the done marks read exactly as they do without groups.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardIngredientGroupsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database), context.filesDir)
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private val timers = TestTimers(context)
    private var shown by mutableStateOf<RecipeId?>(null)

    @Before
    fun setUp() {
        listOf(book.idling, timers.idling).forEach(compose::registerIdlingResource)
    }

    @After
    fun close() {
        listOf(book.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        book.close()
        timers.close()
    }

    private fun grams(position: Int, name: String, amount: Long, note: String? = null) =
        Ingredient(position, null, freeTextName = name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(amount), note = note)

    /** Salt under no heading, "Dough" over flour and water, "Glaze" over sugar, and "Later" over no rows yet. */
    private val grouped = Recipe.create(now, null).copy(
        title = "Buns",
        servings = 2,
        ingredients = listOf(grams(0, "Salt", 4), grams(1, "Flour", 500, note = "sifted"), grams(2, "Water", 300), grams(3, "Sugar", 60)),
        ingredientGroups = listOf(IngredientGroup("Dough", 1), IngredientGroup("Glaze", 3), IngredientGroup("Later", 4)),
        steps = listOf(Step(0, "Mix  with .", listOf(IngredientReference(1, 4), IngredientReference(2, 10)))),
    )

    private val plain = grouped.copy(id = RecipeId("plain-buns"), ingredientGroups = emptyList())

    private fun show(recipe: Recipe) {
        compose.runOnIdle { shown = recipe.id }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(3))).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Where the node tagged [tag] is laid out, scrolled out of view or not. */
    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun bottom(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }

    /** What the card reads of the lines and the step. */
    private fun texts() = (0..3).map { compose.textsOf(CardTags.ingredient(it)) } + listOf(compose.textsOf(CardTags.step(0)))

    @Test
    fun eachGroupIsASubheadingOverItsLinesAndTheLinesReadAsWithoutGroups() {
        runBlocking {
            recipes.save(grouped)
            recipes.save(plain)
        }
        compose.setContent {
            MaterialTheme {
                shown?.let { CardScreen(it, recipes, CardEnvironment(ingredients = IngredientEntries { emptyList() }), onBack = {}, onEdit = {}) }
            }
        }
        show(plain)
        val authored = texts()
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SERVINGS).single() == "Servings: 3" }
        val scaled = texts()
        assertFalse(exists(IngredientTags.group(0)))

        show(grouped)
        assertEquals(authored, texts())
        listOf("Dough", "Glaze").forEachIndexed { index, name ->
            val heading = compose.onNodeWithTag(IngredientTags.group(index)).fetchSemanticsNode()
            assertEquals(listOf(name), compose.textsOf(IngredientTags.group(index)))
            assertTrue("$name is a heading", heading.config.getOrNull(SemanticsProperties.Heading) != null)
        }
        // A heading with no rows yet is not shown.
        assertFalse(exists(IngredientTags.group(2)))
        // Salt stands above the first subheading; each subheading stands over its first line, after the line before.
        assertTrue(bottom(CardTags.ingredient(0)) <= top(IngredientTags.group(0)))
        assertTrue(bottom(IngredientTags.group(0)) <= top(CardTags.ingredient(1)))
        assertTrue(bottom(CardTags.ingredient(2)) <= top(IngredientTags.group(1)))
        assertTrue(bottom(IngredientTags.group(1)) <= top(CardTags.ingredient(3)))

        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SERVINGS).single() == "Servings: 3" }
        assertEquals(scaled, texts())

        // A line under a subheading is marked done by a tap and fixed from its amount, as any line is.
        compose.onNodeWithTag(CardTags.ingredient(1)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(CardTags.ingredient(1)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "Done"
        }
        compose.onNodeWithTag(CardTags.fixAmount(3), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX) }
    }
}
