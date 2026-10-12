package app.recipefork.app.card

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.print.PrintBlock
import app.recipefork.app.print.RecipeDocument
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitSystem.METRIC
import app.recipefork.core.recipe.UnitSystem.US
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.FixedAmount
import app.recipefork.core.scaling.FixedAmountReading
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.Locale

// The dictionary snapshot's measures of these entries, as T-247's tests take them.
internal val UNIT_ENTRIES = listOf(
    IngredientEntryEntity(1, "мука", "flour", null, factors = mapOf("g" to 1.0, "tsp" to 3.0, "tbsp" to 10.0, "cup" to 130.0)),
    IngredientEntryEntity(2, "масло", "butter", null, factors = mapOf("g" to 1.0, "tsp" to 5.0, "tbsp" to 14.0, "cup" to 227.0)),
    IngredientEntryEntity(3, "молоко", "milk", null, factors = mapOf("g" to 1.0, "ml" to 1.03, "tsp" to 5.0, "tbsp" to 15.0, "cup" to 258.0)),
    IngredientEntryEntity(4, "масло растительное", "oil", null, factors = mapOf("g" to 1.0, "ml" to 0.92, "tsp" to 5.0, "tbsp" to 14.0, "cup" to 230.0)),
    IngredientEntryEntity(5, "говядина", "beef", null, factors = mapOf("g" to 1.0)),
    IngredientEntryEntity(6, "соль", "salt", null, factors = mapOf("g" to 1.0, "tsp" to 7.0, "tbsp" to 20.0, "pinch" to 0.5)),
).associateBy { it.id }

private fun r(n: Long, d: Long = 1) = Rational.of(n, d)

private fun row(position: Int, entry: Long, amount: Rational, unit: QuantityUnit) =
    Ingredient(position, entry, unitType = unit.type, unit = unit, quantity = amount)

/** The card's spaces that never break read as plain ones, as a sighted reader sees them. */
private fun plain(text: String?) = text?.replace(' ', ' ')?.replace(' ', ' ')

/** Scenario 2's recipe, written in cups and ounces, for 4. */
internal val CUPS_RECIPE: List<Ingredient> = listOf(
    row(0, 1, r(2), QuantityUnit.CUP),
    row(1, 2, r(4), QuantityUnit.OUNCE),
    row(2, 3, r(1), QuantityUnit.CUP),
    row(3, 6, r(1), QuantityUnit.TEASPOON),
)

/** Scenario 3's recipe, written in grams and millilitres, for 4. */
internal val GRAMS_RECIPE: List<Ingredient> = listOf(
    row(0, 1, r(260), QuantityUnit.GRAM),
    row(1, 5, r(500), QuantityUnit.GRAM),
    row(2, 3, r(250), QuantityUnit.MILLILITRE),
    row(3, 4, r(15), QuantityUnit.MILLILITRE),
)

/**
 * v0.35 T-248: the card's lines, its step references and the printed recipe write each amount in the reader's system
 * (scenarios 2–4); a fix typed on a converted line sets the scale through the same ratio.
 */
@RunWith(AndroidJUnit4::class)
class UnitsOnTheCardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun list(rows: List<Ingredient>, system: UnitSystem?, scale: CardScale = CardScale(r(4))) =
        ingredientList(rows, UNIT_ENTRIES, ENGLISH, 4, scale, system)

    private fun quantities(list: IngredientList) = list.lines.map { plain(it.quantity) }

    @Test
    fun scenario2_metricReadsCupsAndOuncesInGramsAndMillilitres_spoonsAsWritten() {
        val list = list(CUPS_RECIPE, METRIC)
        assertEquals(listOf("≈ 260 g", "113 g", "≈ 250 ml", "1 tsp"), quantities(list))
        // "≈" never parts from its number: a space that does not break stands between them.
        assertEquals("≈\u00A0260 g", list.lines[0].quantity)
        // Shown in another unit than written: the list reads as recalculated, a spoon as written does not count.
        assertTrue(list.recalculated)
        assertEquals(listOf(true, true, true, false), list.lines.map { it.derived })
    }

    @Test
    fun scenario3_usReadsGramsAndMillilitresInUsUnits() {
        assertEquals(listOf("≈ 2 cup", "17½ oz", "1 cup", "1 tbsp"), quantities(list(GRAMS_RECIPE, US)))
        // 500 g is 17.64 oz: rounded down on the ounce grid, and its arrow says so.
        assertEquals(Rounding.DOWN, list(GRAMS_RECIPE, US).lines[1].rounding)
    }

    @Test
    fun aRecipeInTheChosenSystemReadsAsWritten_andWithNoSystemEveryRecipeDoes() {
        val metric = list(GRAMS_RECIPE, METRIC)
        assertEquals(listOf("260 g", "500 g", "250 ml", "15 ml"), quantities(metric))
        assertFalse(metric.recalculated)
        assertEquals(listOf("2 cup", "4 oz", "1 cup", "1 tsp"), quantities(list(CUPS_RECIPE, null)))
        assertFalse(list(CUPS_RECIPE, null).recalculated)
    }

    @Test
    fun scalingConvertsTheScaledAmountAndRoundsOnce() {
        // For 8: 520 g of flour is 4 cups; 1 kg of beef is 35.27 oz, past 2 lb on the ¼ lb grid: 2¼ lb.
        assertEquals(listOf("≈ 4 cup", "2¼ lb", "2 cup", "2 tbsp"), quantities(list(GRAMS_RECIPE, US, CardScale(r(8)))))
    }

    @Test
    fun aTinyAmountHeldAtItsFloorAtTheAuthoredScaleCarriesTheUpArrow() {
        val chili = Ingredient(0, null, freeTextName = "Chili flakes", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = r(1))
        val line = list(listOf(chili), US).lines.single()
        assertEquals("¼ oz", plain(line.quantity))
        assertTrue(line.derived)
        assertEquals(Rounding.UP, line.rounding)
        // Two lines at their floors as shown: the proportions warning, as for a scaled recipe.
        assertFalse(list(listOf(chili, chili.copy(position = 1, quantity = r(2))), US).holdsProportions)
        assertTrue(list(listOf(chili, chili.copy(position = 1, quantity = r(2))), METRIC).holdsProportions)
    }

    @Test
    fun aFixTypedInTheShownUnitSetsTheScaleThroughTheSameRatio_andTheLineReadsIt() {
        val before = list(GRAMS_RECIPE, US)
        val offer = before.lines[0].fixing!!
        assertEquals(listOf(QuantityUnit.CUP), offer.units)
        assertEquals(QuantityUnit.CUP, offer.authoredUnit)
        val fixing = offer.fixing(0, r(3), QuantityUnit.CUP)
        // 3 cups of flour at 130 g a cup: the scale is set in the written grams, the unit typed in kept with it.
        assertEquals(CardFixing(0, r(390), QuantityUnit.GRAM, ShownFixing(QuantityUnit.CUP, r(130))), fixing)
        val fixed = list(GRAMS_RECIPE, US, CardScale(r(4)).fix(fixing))
        assertEquals("≈ 3 cup", plain(fixed.lines[0].quantity))
        assertTrue(fixed.lines[0].fixed)
        // ×1.5: 750 g of beef is 26.46 oz.
        assertEquals("26½ oz", plain(fixed.lines[1].quantity))
        assertEquals(r(6), fixed.servingCount!!.value)
        assertTrue(fixed.servingCount!!.approximate)
        // The control opens again on what was typed, in the unit typed.
        assertEquals(CardFixing(0, r(3), QuantityUnit.CUP), fixed.lines[0].fixing!!.typed(fixing))
    }

    @Test
    fun aFixOnALineConvertedExactlyReadsTheTypedValue_inAnyUnitOfItsLadder() {
        val offer = list(CUPS_RECIPE, METRIC).lines[1].fixing!!
        assertEquals(listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.TONNE), offer.units)
        val grams = offer.fixing(1, r(200), QuantityUnit.GRAM)
        assertEquals(QuantityUnit.OUNCE, grams.unit)
        assertEquals("200 g", plain(list(CUPS_RECIPE, METRIC, CardScale(r(4)).fix(grams)).lines[1].quantity))
        val kilo = offer.fixing(1, r(1), QuantityUnit.KILOGRAM)
        assertEquals("1 kg", plain(list(CUPS_RECIPE, METRIC, CardScale(r(4)).fix(kilo)).lines[1].quantity))
        // A line shown as written fixes as typed, as before.
        assertEquals(CardFixing(1, r(2), QuantityUnit.KILOGRAM), list(GRAMS_RECIPE, METRIC).lines[1].fixing!!.fixing(1, r(2), QuantityUnit.KILOGRAM))
    }

    @Test
    fun aFixedLineStaysInTheUnitItWasTypedIn() {
        // Oil, 15 ml, reads "1 tbsp" in US; 5 tbsp is more than ¼ cup, yet the fixed line reads what was typed.
        val oil = list(GRAMS_RECIPE, US).lines[3].fixing!!
        val spoons = oil.fixing(3, r(5), QuantityUnit.TABLESPOON)
        val fixed = list(GRAMS_RECIPE, US, CardScale(r(4)).fix(spoons))
        assertEquals("5 tbsp", plain(fixed.lines[3].quantity))
        assertEquals(Rounding.NONE, fixed.lines[3].rounding)
        assertEquals(CardFixing(3, r(5), QuantityUnit.TABLESPOON), fixed.lines[3].fixing!!.typed(spoons))
        // Flour, 20 g, reads "≈ 2 tbsp" by its own spoon; 5 tbsp stays 5 tbsp, never "≈ ⅓ cup".
        val flour = listOf(row(0, 1, r(20), QuantityUnit.GRAM))
        val offer = list(flour, US).lines[0].fixing!!
        assertEquals(QuantityUnit.TABLESPOON, offer.authoredUnit)
        val typed = offer.fixing(0, r(5), QuantityUnit.TABLESPOON)
        val shown = list(flour, US, CardScale(r(4)).fix(typed)).lines[0]
        assertEquals("≈ 5 tbsp", plain(shown.quantity))
        assertEquals(CardFixing(0, r(5), QuantityUnit.TABLESPOON), shown.fixing!!.typed(typed))
    }

    @Test
    fun aFixingTurnedIntoTheOtherSystemReopensOnTheValueTheLineShows() {
        // 600 g of beef fixed in Metric reads 21.16 oz in US: the control opens on the 21 oz the line shows.
        val beef = list(GRAMS_RECIPE, METRIC).lines[1].fixing!!.fixing(1, r(600), QuantityUnit.GRAM)
        val us = list(GRAMS_RECIPE, US, CardScale(r(4)).fix(beef))
        assertEquals("21 oz", plain(us.lines[1].quantity))
        assertEquals(CardFixing(1, r(21), QuantityUnit.OUNCE), us.lines[1].fixing!!.typed(beef))
        // 300 g of flour is 2.31 cups: the line's 2⅓ cups.
        val flour = list(GRAMS_RECIPE, METRIC).lines[0].fixing!!.fixing(0, r(300), QuantityUnit.GRAM)
        val cups = list(GRAMS_RECIPE, US, CardScale(r(4)).fix(flour))
        assertEquals("≈ 2⅓ cup", plain(cups.lines[0].quantity))
        val reopened = cups.lines[0].fixing!!.typed(flour)
        assertEquals(CardFixing(0, r(7, 3), QuantityUnit.CUP), reopened)
        assertTrue(FixedAmount.read(QuantityInput.write(reopened.amount, reopened.unit, ENGLISH), reopened.unit, null) is FixedAmountReading.Accepted)
        // 5 tbsp of oil typed in US is 73.93 ml in Metric: the line's 74 ml.
        val oil = list(GRAMS_RECIPE, US).lines[3].fixing!!.fixing(3, r(5), QuantityUnit.TABLESPOON)
        val metric = list(GRAMS_RECIPE, METRIC, CardScale(r(4)).fix(oil))
        assertEquals("74 ml", plain(metric.lines[3].quantity))
        assertEquals(CardFixing(3, r(74), QuantityUnit.MILLILITRE), metric.lines[3].fixing!!.typed(oil))
    }

    @Test
    fun theUnitAFixingWasTypedInIsKeptWithTheCardsScale_andAnEarlierSavedScaleStillReads() {
        val typed = CardFixing(3, r(1000, 13), QuantityUnit.MILLILITRE, ShownFixing(QuantityUnit.TABLESPOON, r(7, 3)))
        val held = HeldScale(r(4), typed, 7)
        assertEquals(held, HeldScale.decoded(held.encoded()))
        assertEquals(HeldScale(r(4), CardFixing(3, r(1000, 13), QuantityUnit.MILLILITRE), 7), HeldScale.decoded("4;3;1000/13;MILLILITRE;7"))
    }

    @Test
    fun referencesReadAsTheirLines_aPartInAnotherSystemReadsInItsRowsUnit() {
        // Butter in oz, a part of it still in the grams it was written in before the row changed; flour in cups, a part of it.
        val rows = listOf(row(0, 2, r(8), QuantityUnit.OUNCE), row(1, 1, r(2), QuantityUnit.CUP))
        val whole = IngredientReference(0, 0)
        val butterPart = IngredientReference(0, 0, IngredientShare(r(100), QuantityUnit.GRAM))
        val flourPart = IngredientReference(1, 0, IngredientShare(r(1), QuantityUnit.CUP))
        val us = CardReferences.of(rows, list(rows, US), ENGLISH, UNIT_ENTRIES, US)
        assertEquals("8 oz", plain(us.quantity(whole)))
        // 100 g is 3.53 oz: in ounces, as its row, never "≈ ½ cup" by butter's cup.
        assertEquals("3½ oz", plain(us.quantity(butterPart)))
        assertEquals(Rounding.DOWN, us.rounding(butterPart))
        val metric = CardReferences.of(rows, list(rows, METRIC), ENGLISH, UNIT_ENTRIES, METRIC)
        assertEquals("227 g", plain(metric.quantity(whole)))
        assertEquals("100 g", plain(metric.quantity(butterPart)))
        assertEquals("≈ 130 g", plain(metric.quantity(flourPart)))
        // A step reads them so.
        val step = Step(0, "Melt  with .", listOf(IngredientReference(0, 5), IngredientReference(1, 11, flourPart.share)))
        assertEquals("Melt 227 g ↑ with ≈ 130 g.", plain(shownStepText(step, metric)))
    }

    @Test
    fun thePrintedRecipeInUsWritesEveryAmountInUsUnits() {
        val recipe = Recipe.create(Instant.parse("2026-10-12T12:00:00Z"), null).copy(
            title = "Stew",
            servings = 4,
            ingredients = GRAMS_RECIPE,
            steps = listOf(Step(0, "Brown  in .", listOf(IngredientReference(1, 6), IngredientReference(3, 10)))),
        )
        val resources = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale("en"))) },
        ).resources
        val document = RecipeDocument.of(recipe, UNIT_ENTRIES, emptyMap(), CardScale(null), resources, system = US)
        val bullets = document.blocks.filterIsInstance<PrintBlock.Bullet>().map { plain(it.text) }
        assertEquals(listOf("≈ 2 cup flour", "17½ oz ↓ beef", "1 cup ↓ milk", "1 tbsp ↓ oil"), bullets)
        val steps = document.blocks.filterIsInstance<PrintBlock.Numbered>().map { plain(it.text) }
        assertEquals(listOf("Brown 17½ oz ↓ in 1 tbsp ↓."), steps)
        assertTrue(document.blocks.filterIsInstance<PrintBlock.Muted>().any { it.text == resources.getString(app.recipefork.app.R.string.card_quantities_recalculated) })
    }
}
