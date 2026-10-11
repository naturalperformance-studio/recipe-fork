package app.recipefork.core.shopping

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal fun r(n: Long, d: Long = 1) = Rational.of(n, d)

internal val A = RecipeId("recipe-a")
internal val B = RecipeId("recipe-b")
internal val C = RecipeId("recipe-c")

internal const val FLOUR = 1L
internal const val ONION = 2L
internal const val SALT = 3L
internal const val MILK = 4L
internal const val EGG = 5L
internal const val PEPPER = 6L
internal const val SUGAR = 7L

/** Flour: 1 g per g, 0.6 g per ml, 130 g per cup — no pinch. Onion: 150 g a piece, indivisible. Salt: 6 g per tsp. */
internal val MEASURES: Map<Long, EntryMeasures> = mapOf(
    FLOUR to EntryMeasures(factors = mapOf("g" to 1.0, "ml" to 0.6, "cup" to 130.0)),
    ONION to EntryMeasures(divisible = false, factors = mapOf("pc" to 150.0, "g" to 1.0)),
    SALT to EntryMeasures(factors = mapOf("g" to 1.0, "tsp" to 6.0)),
    MILK to EntryMeasures(factors = mapOf("ml" to 1.0)),
    EGG to EntryMeasures(divisible = false),
)

/** A row naming [entry]: [quantity] of [unit], or to taste where [unit] is null. */
internal fun row(
    entry: Long,
    quantity: Rational?,
    unit: QuantityUnit?,
    qualifier: String? = null,
    divisible: Boolean? = null,
) = Ingredient(
    position = 0,
    dictionaryEntry = entry,
    qualifier = qualifier,
    unitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    unit = unit,
    quantity = quantity,
    divisible = divisible ?: if (unit == QuantityUnit.PIECE) false else null,
)

internal fun toTaste(entry: Long) = row(entry, null, null)

/** A row in the author's own words. */
internal fun free(text: String, quantity: Rational?, unit: QuantityUnit?, divisible: Boolean? = null) = Ingredient(
    position = 0,
    dictionaryEntry = null,
    freeTextName = text,
    unitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    unit = unit,
    quantity = quantity,
    divisible = divisible ?: if (unit == QuantityUnit.PIECE) false else null,
)

/** Recipe [id] authored for [servings] (4 unless given) holding [rows], asked for at [requested]. */
internal fun recipe(id: RecipeId, vararg rows: Ingredient, servings: Int? = 4, requested: Rational? = null) =
    ShoppingRecipe(id, rows.toList(), servings, requested)

internal fun list(vararg recipes: ShoppingRecipe, measures: Map<Long, EntryMeasures> = MEASURES) =
    ShoppingTotals.of(recipes.toList(), measures)

internal fun amount(unit: QuantityUnit, value: Rational) = ShoppingAmount(unit, value)

/** The measured amount of [line], asserting it has one. */
internal fun amountOf(line: ShoppingLine): ShoppingAmount = assertIs<ShoppingQuantity.Measured>(line.quantity).amount

internal fun assertMeasured(unit: QuantityUnit, value: Rational, line: ShoppingLine) =
    assertEquals(amount(unit, value), amountOf(line))
