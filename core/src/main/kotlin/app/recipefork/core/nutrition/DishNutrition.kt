package app.recipefork.core.nutrition

import java.math.BigInteger

/**
 * Energy in [kcal], and [protein], [fat] and [carbs] in grams, each rounded once: kcal to 10, grams whole. Whole
 * numbers of any size, since a dish may be scaled without a ceiling (SCALE-010).
 */
data class NutritionValues(
    val kcal: BigInteger,
    val protein: BigInteger,
    val fat: BigInteger,
    val carbs: BigInteger,
)

/** A row that counted, at its [position] in the recipe, and the [kcal] it gives the whole dish, rounded on its own. */
data class CountedRow(val position: Int, val kcal: BigInteger)

/** A row the count left out, at its [position] in the recipe, for its one [reason]. */
data class LeftOutRow(val position: Int, val reason: LeftOutReason)

/**
 * Why a row is left out of the count; each row has exactly one, the first of these that holds. [FREE_TEXT] and
 * [NOT_IN_DICTIONARY] are kept apart here, yet the card shows both as the one reason «нет в словаре».
 */
enum class LeftOutReason {
    /** The row names its ingredient in the author's own words, not by an entry. */
    FREE_TEXT,

    /** The row names an entry the dictionary does not hold. */
    NOT_IN_DICTIONARY,

    /** The row is to taste. */
    TO_TASTE,

    /** The row states no quantity. */
    NO_QUANTITY,

    /** The row's unit has no measure in grams: none on its entry, nor a piece weight on the row. */
    NO_MEASURE,

    /** Neither the entry nor its parent says what 100 g of it holds, or the entry is known to have no values of its own. */
    NO_DATA,
}

/**
 * The nutrition of a dish at the scale in force: the [whole] dish, one serving of it ([perServing], null where no
 * serving count is shown), the kcal each [counted] row gives the whole dish and the rows [leftOut] of the count, both in
 * recipe order.
 */
data class DishNutrition(
    val whole: NutritionValues,
    val perServing: NutritionValues?,
    val counted: List<CountedRow>,
    val leftOut: List<LeftOutRow>,
)
