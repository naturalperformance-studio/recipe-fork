package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * A step's ingredient reference never breaks between its number and its unit, nor inside a unit of two words
 * («4½ ст. л.», «1,5 кг»): on a 320 dp-wide phone at font scale 2.0, in English and in Russian, no line of a step's
 * text as the card lays it out ends inside a reference's quantity. The steps put their references at every place along
 * a line, so that some fall where a line would otherwise break.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class CardReferenceNoBreakTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { emptyList() }
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    private val ingredients = listOf(
        Ingredient(0, null, freeTextName = "Sugar", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TABLESPOON, quantity = Rational.of(9, 2)),
        Ingredient(1, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.KILOGRAM, quantity = Rational.of(3, 2)),
        Ingredient(2, null, freeTextName = "Salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(3, 2)),
        Ingredient(3, null, freeTextName = "Pepper", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    /**
     * A step of [parts] — typed text, and a reference wherever a part is an ingredient's position — its typed text with
     * each reference at its offset.
     */
    private fun step(position: Int, vararg parts: Any): Step {
        val text = StringBuilder()
        val references = mutableListOf<IngredientReference>()
        parts.forEach { part ->
            if (part is Int) references += IngredientReference(part, text.length) else text.append(part as String)
        }
        return Step(position, text.toString(), references)
    }

    /** Steps whose references move along the line a character at a time, by a word before them that grows. */
    private fun steps(words: List<String>) = (0 until 14).map { k ->
        step(k, "${words[0]} ${"o".repeat(k + 1)} ", 0, " ${words[1]} ", 1, " ${words[2]}, ", 2, " ${words[3]} ", 3, " ${words[4]}.")
    }

    private fun open(steps: List<Step>) {
        val recipe = Recipe.create(now, null).copy(title = "Bread", servings = 4, ingredients = ingredients, steps = steps)
        runBlocking { recipes.save(recipe) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {})
                }
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(StepTags.text(steps.last().position)), useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
    }

    private fun layoutOf(position: Int): TextLayoutResult {
        val node = compose.onNodeWithTag(StepTags.text(position), useUnmergedTree = true).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.let { action ->
            mutableListOf<TextLayoutResult>().also { action(it) }.first()
        }
    }

    /**
     * No line of any step ends inside one of [quantities] — each found by its words, whatever space is between them —
     * and the steps do wrap, over several lines each.
     */
    private fun assertNoBreakInside(steps: List<Step>, quantities: List<String>) {
        val patterns = quantities.map { quantity -> Regex(quantity.split(' ').joinToString("[  ]") { Regex.escape(it) }) }
        var breaks = 0
        steps.forEach { step ->
            val layout = layoutOf(step.position)
            val text = layout.layoutInput.text.text
            assertTrue("step ${step.position} wraps: \"$text\"", layout.lineCount > 1)
            val ends = (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
            breaks += ends.size
            patterns.forEachIndexed { index, pattern ->
                val found = pattern.find(text)
                assertTrue("step ${step.position} reads ${quantities[index]}: \"$text\"", found != null)
                val range = found!!.range
                ends.forEach { end ->
                    assertTrue(
                        "step ${step.position} breaks inside \"${found.value}\" at $end: \"$text\"",
                        end <= range.first || end > range.last,
                    )
                }
            }
        }
        assertTrue(breaks > steps.size)
    }

    @Test
    @Config(qualifiers = "+en")
    fun inEnglishAStepNeverBreaksInsideAReference() {
        val steps = steps(listOf("Add", "sugar to", "flour", "salt and", "pepper"))
        open(steps)
        assertNoBreakInside(steps, listOf("4½ tbsp", "1.5 kg", "1½ tsp", "to taste"))
    }

    @Test
    @Config(qualifiers = "+ru")
    fun inRussianAStepNeverBreaksInsideAReference() {
        val steps = steps(listOf("Всыпьте", "сахара в", "муки", "соли и", "перца"))
        open(steps)
        assertNoBreakInside(steps, listOf("4½ ст. л.", "1,5 кг", "1½ ч. л.", "по вкусу"))
    }
}
