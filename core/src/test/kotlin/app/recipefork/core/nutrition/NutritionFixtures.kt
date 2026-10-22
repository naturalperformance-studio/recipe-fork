package app.recipefork.core.nutrition

import app.recipefork.core.api.NutritionDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.ScaleFactor
import java.math.BigInteger

internal fun r(n: Long, d: Long = 1) = Rational.of(n, d)

internal fun big(n: Long): BigInteger = BigInteger.valueOf(n)

internal fun totals(kcal: Long, protein: Long, fat: Long, carbs: Long) = NutritionValues(big(kcal), big(protein), big(fat), big(carbs))

internal fun counted(position: Int, kcal: Long) = CountedRow(position, big(kcal))

internal const val FLOUR = 1L
internal const val EGG = 2L
internal const val OIL = 3L
internal const val SALT = 4L
internal const val PURE = 5L
internal const val NO_DATA = 6L
internal const val BARE = 7L
internal const val HEAVY_GRAM = 8L
internal const val TENTH = 9L
internal const val CHICKEN = 10L
internal const val WINGS = 11L
internal const val THIGHS = 12L
internal const val NO_DATA_CHILD = 13L
internal const val ORPHAN = 14L
internal const val PIECELESS_CHILD = 15L
internal const val NO_OWN_DATA = 16L
internal const val UNKNOWN = 99L

/**
 * Fixture entries, not the shipped dictionary. [PURE] is 100 g protein per 100 g, so its protein in grams is the row's
 * grams; [TENTH] is 100 kcal and 1 g protein per 100 g; [HEAVY_GRAM] has a `g` factor of 2 that grams must ignore.
 */
internal val ENTRIES: Map<Long, NutritionEntry> = mapOf(
    FLOUR to NutritionEntry(factors = mapOf("g" to 1.0, "ml" to 0.5, "tbsp" to 10.0, "cup" to 130.0), nutrition = values(364.0, 10.0, 1.0, 76.0)),
    EGG to NutritionEntry(factors = mapOf("pc" to 50.0), nutrition = values(143.0, 12.6, 9.5, 0.7)),
    OIL to NutritionEntry(factors = mapOf("ml" to 0.92, "tsp" to 4.5, "tbsp" to 13.6), nutrition = values(884.0, 0.0, 100.0, 0.0)),
    SALT to NutritionEntry(factors = mapOf("g" to 1.0, "tsp" to 6.0, "pinch" to 0.4), nutrition = values(0.0, 0.0, 0.0, 0.0)),
    PURE to NutritionEntry(
        factors = mapOf("ml" to 0.5, "pc" to 40.0, "tsp" to 5.0, "tbsp" to 15.0, "cup" to 200.0, "pinch" to 0.5),
        nutrition = values(400.0, 100.0, 0.0, 0.0),
    ),
    NO_DATA to NutritionEntry(factors = mapOf("g" to 1.0, "tbsp" to 10.0, "pc" to 1000.0)),
    BARE to NutritionEntry(),
    HEAVY_GRAM to NutritionEntry(factors = mapOf("g" to 2.0), nutrition = values(400.0, 100.0, 0.0, 0.0)),
    TENTH to NutritionEntry(factors = mapOf("g" to 1.0), nutrition = values(100.0, 1.0, 1.0, 1.0)),
    CHICKEN to NutritionEntry(factors = mapOf("g" to 1.0, "pc" to 1000.0), nutrition = values(200.0, 20.0, 13.0, 0.0)),
    WINGS to NutritionEntry(parentId = CHICKEN, factors = mapOf("g" to 1.0, "pc" to 90.0)),
    THIGHS to NutritionEntry(parentId = CHICKEN, factors = mapOf("g" to 1.0), nutrition = values(180.0, 18.0, 10.0, 0.0)),
    NO_DATA_CHILD to NutritionEntry(parentId = NO_DATA, factors = mapOf("g" to 1.0)),
    ORPHAN to NutritionEntry(parentId = UNKNOWN, factors = mapOf("g" to 1.0)),
    PIECELESS_CHILD to NutritionEntry(parentId = CHICKEN, factors = mapOf("g" to 1.0)),
    NO_OWN_DATA to NutritionEntry(parentId = CHICKEN, factors = mapOf("g" to 1.0), noNutrition = true),
)

internal fun values(kcal: Double, protein: Double, fat: Double, carbs: Double) = NutritionDto(kcal, protein, fat, carbs)

/** A row naming [entry]: [quantity] of [unit], or to taste where [unit] is null. */
internal fun row(entry: Long, quantity: Rational?, unit: QuantityUnit?, pieceWeight: Rational? = null) = Ingredient(
    position = 0,
    dictionaryEntry = entry,
    unitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    unit = unit,
    quantity = quantity,
    pieceWeight = pieceWeight,
    divisible = if (unit == QuantityUnit.PIECE) false else null,
)

internal fun toTaste(entry: Long) = row(entry, null, null)

/** A row in the author's own words. */
internal fun free(text: String, quantity: Rational?, unit: QuantityUnit?) = Ingredient(
    position = 0,
    dictionaryEntry = null,
    freeTextName = text,
    unitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    unit = unit,
    quantity = quantity,
)

/**
 * The nutrition of a dish of [rows], numbered by their order, at [factor], one serving of [servings] where given, 100 g
 * of the finished dish where its [finishedWeight] is.
 */
internal fun dish(
    vararg rows: Ingredient,
    factor: ScaleFactor = ScaleFactor.ONE,
    servings: Rational? = null,
    entries: Map<Long, NutritionEntry> = ENTRIES,
    finishedWeight: Int? = null,
): DishNutrition? = Nutrition.of(
    rows.mapIndexed { i, row -> row.copy(position = i) },
    factor,
    NutritionEntries(entries::get),
    servings,
    finishedWeight,
)

/** The protein in grams of a dish of [row] alone, [PURE] giving one gram of protein per gram. */
internal fun proteinOf(row: Ingredient): BigInteger = checkNotNull(dish(row)).whole.protein
