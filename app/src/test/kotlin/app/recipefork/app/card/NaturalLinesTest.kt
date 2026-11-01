package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The card's ingredient lines read naturally (v0.30, T-217), on View and on Cooking, in English and in Russian: a row
 * with no amount yet shows no unit — "Grains of paradise", never "g Grains of paradise" — and a row to taste reads
 * "salt — to taste" / «соль — по вкусу», with nothing in the quantity column; the other lines keep their amounts, scaled
 * or as authored, a done mark still strikes the line, and a step's reference to each reads as before.
 */
@RunWith(AndroidJUnit4::class)
class NaturalLinesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val timers = TestTimers(context)

    @Before
    fun setUp() = compose.registerIdlingResource(timers.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(timers.idling)
        timers.close()
    }

    private class Words(
        val language: InterfaceLanguage,
        val unfinished: String,
        val salt: String,
        val flour: String,
        val note: String,
        val step: String,
        /** The lines as read at 6 servings of a recipe for 4: the name text, then the quantity where there is one. */
        val lines: List<List<String>>,
        /** The step as read at that count: each reference as the list writes its row, or as the editor does unfinished. */
        val stepRead: String,
    )

    private val english = Words(
        InterfaceLanguage.ENGLISH,
        unfinished = "Grains of paradise",
        salt = "salt",
        flour = "flour",
        note = "sifted",
        step = "Mix , then  and .",
        lines = listOf(
            listOf("Grains of paradise"),
            listOf("salt — to taste"),
            listOf("flour — sifted", "750 g"),
        ),
        stepRead = "Mix 750 g, then to taste and ___ g.",
    )

    private val russian = Words(
        InterfaceLanguage.RUSSIAN,
        unfinished = "райские зёрна",
        salt = "соль",
        flour = "мука",
        note = "просеянная",
        step = "Смешайте , затем  и .",
        lines = listOf(
            listOf("райские зёрна"),
            listOf("соль — по вкусу"),
            listOf("мука — просеянная", "750 г"),
        ),
        stepRead = "Смешайте 750 г, затем по вкусу и ___ г.",
    )

    private val now = Instant.parse("2026-10-10T12:00:00Z")

    private fun recipeIn(w: Words) = Recipe.create(now, null).copy(
        title = "Bread",
        servings = 4,
        ingredients = listOf(
            // New rows default to grams: one left with no amount yet.
            Ingredient(0, null, freeTextName = w.unfinished, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = null),
            Ingredient(1, null, freeTextName = w.salt, unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
            Ingredient(2, null, freeTextName = w.flour, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500), note = w.note),
        ),
        steps = listOf(
            Step(
                0,
                w.step,
                listOf(
                    IngredientReference(2, w.step.indexOf(',')),
                    IngredientReference(1, w.step.indexOf("  ") + 1),
                    IngredientReference(0, w.step.lastIndexOf('.')),
                ),
            ),
        ),
    )

    /** Shows the lists of [w]'s recipe, for 4, at 6 servings in [mode], the salt marked done. */
    private fun show(w: Words, mode: CardMode) {
        val recipe = recipeIn(w)
        val list = ingredientList(recipe.ingredients, emptyMap(), w.language, 4, CardScale(Rational.of(6)))
        val references = CardReferences.of(recipe.ingredients, list, w.language, emptyMap())
        val marks = CardMarks(DoneMarks.inMemory(), BookName.Pool, recipe)
        marks.toggle(MarkedPart.INGREDIENT, 1)
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).padding(horizontal = 16.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            IngredientsList(
                                recipe.ingredients, list, emptyList(), w.language, onFix = {}, onRelease = {},
                                marks = marks, groups = emptyList(), mode = mode,
                            )
                            StepsList(recipe.steps, recipe.id, recipe.title, references = references, marks = marks, mode = mode)
                        }
                    }
                }
            }
        }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(0)) }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun texts(tag: String, unmerged: Boolean = false): List<String> =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun assertLines(w: Words, mode: CardMode) {
        show(w, mode)
        w.lines.forEachIndexed { position, expected -> assertEquals("$mode, line $position", expected, texts(CardTags.ingredient(position))) }
        // Nothing stands in the quantity column of a row with no amount, nor of one to taste.
        listOf(0, 1).forEach { assertFalse("$mode, line $it shows a quantity", exists(IngredientTags.quantity(it))) }
        assertEquals(w.lines[2][1], compose.textLayoutsWithin(IngredientTags.quantity(2)).single().layoutInput.text.text)
        // The salt, marked done, is struck through, its "to taste" with it.
        assertEquals(TextDecoration.LineThrough, compose.textLayoutsWithin(IngredientTags.name(1)).single().layoutInput.style.textDecoration)
        // A step's references read as before: the scaled amount, "to taste", the editor's placeholder and unit — its
        // words as drawn, after the number Cooking draws before them.
        assertEquals(w.stepRead, compose.onNodeWithTag(StepTags.text(0), useUnmergedTree = true).fetchSemanticsNode().config[ShownText])
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun onViewTheLinesReadNaturally() = assertLines(english, CardMode.VIEW)

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun onCookingTheLinesReadNaturally() = assertLines(english, CardMode.COOKING)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun onViewTheLinesReadNaturallyInRussian() = assertLines(russian, CardMode.VIEW)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun onCookingTheLinesReadNaturallyInRussian() = assertLines(russian, CardMode.COOKING)

    // The amounts still read as the scale gives them, the amount fixed on Cooking too: 600 g of the flour reads 600 g,
    // and the other two lines still show no unit and "to taste".
    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun aFixedAmountLeavesTheOtherLinesAsTheyRead() {
        val recipe = recipeIn(english)
        val fixed = ingredientList(
            recipe.ingredients, emptyMap(), InterfaceLanguage.ENGLISH, 4,
            CardScale(Rational.of(4)).fix(CardFixing(2, Rational.of(600), QuantityUnit.GRAM)),
        )
        assertEquals(listOf(null, null, "600 g"), fixed.lines.map { it.quantity })
        assertEquals(listOf(null, "to taste", null), fixed.lines.map { it.toTaste })
        assertEquals(listOf(false, false, true), fixed.lines.map { it.fixed })
    }
}
