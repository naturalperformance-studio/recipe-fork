package app.recipefork.core.recipe

import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.timer.TimerName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * A reference's quantity never breaks between its number and its unit, nor inside a unit of two words («4½ ст. л.»,
 * «1,5 кг»): wherever a step reads with its references in place — the card, a timer's derived name — the spaces of a
 * reference are no-break spaces. What a step stores, and so what a file exports, holds no quantity and is unchanged.
 */
class ReferenceNoBreakTest {
    private val nbsp = "\u00A0"

    private val ingredients = listOf(
        Ingredient(0, null, freeTextName = "сахар", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TABLESPOON, quantity = Rational.of(9, 2)),
        Ingredient(1, null, freeTextName = "мука", unitType = UnitType.EXACT, unit = QuantityUnit.KILOGRAM, quantity = Rational.of(3, 2)),
        Ingredient(2, null, freeTextName = "соль", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(1, 2)),
        Ingredient(3, null, freeTextName = "перец", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        Ingredient(4, null, freeTextName = "сливки", unitType = UnitType.EXACT, unit = QuantityUnit.MILLILITRE, quantity = null),
    )

    // "Всыпьте |сахара, |муки, |соли, |перца и |сливок"
    private val text = "Всыпьте  сахара,  муки,  соли,  перца и  сливок"
    private val step = Step(
        0, text,
        listOf(
            IngredientReference(0, 8), IngredientReference(1, 17), IngredientReference(2, 24),
            IngredientReference(3, 31), IngredientReference(4, 40),
        ),
        listOf(Timer(5.minutes)),
    )

    private fun references(language: InterfaceLanguage): List<String> =
        StepText.runs(step, ReferenceText.authored(ingredients, language)).filterIsInstance<StepRun.Reference>().map { it.text }

    @Test
    fun `in Russian a reference's number and unit, and a two-word unit, are held by no-break spaces`() {
        assertEquals(
            listOf("4½${nbsp}ст.${nbsp}л.", "1,5${nbsp}кг", "½${nbsp}ч.${nbsp}л.", "по${nbsp}вкусу", "${ReferenceText.PLACEHOLDER}${nbsp}мл"),
            references(InterfaceLanguage.RUSSIAN),
        )
    }

    @Test
    fun `in English a reference's number and unit, and a two-word unit, are held by no-break spaces`() {
        assertEquals(
            listOf("4½${nbsp}tbsp", "1.5${nbsp}kg", "½${nbsp}tsp", "to${nbsp}taste", "${ReferenceText.PLACEHOLDER}${nbsp}ml"),
            references(InterfaceLanguage.ENGLISH),
        )
    }

    @Test
    fun `the typed text around references keeps its own spaces`() {
        val typed = StepText.runs(step, ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN))
            .filterIsInstance<StepRun.Typed>().joinToString("") { it.text }
        assertEquals(text, typed)
        assertFalse(nbsp in typed)
    }

    @Test
    fun `a timer's name derived from its step holds each reference together as the card does`() {
        val name = TimerName.of(step, 0, ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN))!!
        assertTrue(name.startsWith("Всыпьте 4½${nbsp}ст.${nbsp}л. сахара, 1,5${nbsp}кг муки"), name)
        assertEquals(StepText.render(step, ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN)).take(name.length - 1), name.dropLast(1))
    }

    @Test
    fun `what a held timer is started from is read as stored, not as displayed`() {
        val part = step.copy(text = "Всыпьте  сахара", ingredientReferences = listOf(IngredientReference(0, 8, IngredientShare(Rational.of(2), QuantityUnit.TABLESPOON))))
        assertEquals("Всыпьте \uFFFC2 TABLESPOON сахара", TimerName.source(part))
    }

    @Test
    fun `a timer started before references were held together is still its step's timer`() {
        val sugar = Step(0, "Всыпьте  сахара", listOf(IngredientReference(0, 8)), listOf(Timer(5.minutes)))
        // As it was held: named with plain spaces, from the source read as it always was.
        val oldName = "Всыпьте 4½ ст. л. сахара"
        val oldSource = "Всыпьте \uFFFC сахара"
        assertEquals(oldSource, TimerName.source(sugar, 0))
        assertTrue(TimerName.holds(sugar, 0, oldName, oldSource))
    }

    @Test
    fun `an exported recipe's step text is byte-identical to its stored text`() {
        val recipe = Recipe.create(Instant.parse("2026-10-09T12:00:00Z"), owner = null)
            .copy(title = "Пирог", servings = 4, ingredients = ingredients, steps = listOf(step))
        val file = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        assertFalse(nbsp in file)
        val exported = Json.parseToJsonElement(file).jsonObject.getValue("recipe").jsonObject.getValue("steps").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content
        assertContentEquals(text.toByteArray(Charsets.UTF_8), exported.toByteArray(Charsets.UTF_8))
        assertEquals(step, RecipeFile.decode(file).steps.single())
        // The display of that same step does hold the no-break spaces: only the display changed.
        assertTrue(nbsp in StepText.render(step, ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN)))
    }
}
