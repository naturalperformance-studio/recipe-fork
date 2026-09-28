package app.recipefork.core.scaling

import app.recipefork.core.recipe.Dimension
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import java.math.BigInteger

/** The two interface languages of L10N-001; numbers and unit labels follow it (L10N-009). */
enum class InterfaceLanguage(
    /** Separates groups of three digits: a non-breaking space in Russian, a comma in English. */
    val groupSeparator: Char,
    /** Precedes the fractional part of a decimal number. */
    val decimalMark: Char,
) {
    RUSSIAN(' ', ','),
    ENGLISH(',', '.'),
}

/** The names of the twelve units in both interface languages, exactly as DOM-016 gives them. */
object UnitLabels {
    private val labels: Map<QuantityUnit, Pair<String, String>> = mapOf(
        QuantityUnit.GRAM to ("г" to "g"),
        QuantityUnit.KILOGRAM to ("кг" to "kg"),
        QuantityUnit.TONNE to ("т" to "t"),
        QuantityUnit.MILLILITRE to ("мл" to "ml"),
        QuantityUnit.LITRE to ("л" to "l"),
        QuantityUnit.CUBIC_METRE to ("м³" to "m³"),
        QuantityUnit.PIECE to ("шт" to "pc"),
        QuantityUnit.TEASPOON to ("ч. л." to "tsp"),
        QuantityUnit.TABLESPOON to ("ст. л." to "tbsp"),
        QuantityUnit.CUP to ("стакан" to "cup"),
        QuantityUnit.PINCH to ("щепотка" to "pinch"),
        QuantityUnit.TO_TASTE to ("по вкусу" to "to taste"),
    )

    fun of(unit: QuantityUnit, language: InterfaceLanguage): String {
        val (russian, english) = labels.getValue(unit)
        return when (language) {
            InterfaceLanguage.RUSSIAN -> russian
            InterfaceLanguage.ENGLISH -> english
        }
    }
}

/**
 * The text of a displayed quantity: its number and its unit label in the interface language.
 *
 * - `exact` is written on the unit ladder of SCALE-013: below 1000 in the base unit (g, ml),
 *   from 1000 in kg or l to three places, from 1 000 000 in t or m³ to three places, with no
 *   trailing zero after the decimal mark.
 * - `household`, `taste_measured` and `piece` are written as a whole number, a vulgar fraction,
 *   or a whole number followed by one (SCALE-014), and never carry a decimal mark.
 * - Whole numbers are grouped by three digits as L10N-009 states.
 * - `taste_unmeasured` is its label alone.
 *
 * The derived marker and the rounding direction (SCALE-004) are not part of this text.
 */
object QuantityText {

    fun of(quantity: DisplayedQuantity, language: InterfaceLanguage): String {
        val value = quantity.value ?: return UnitLabels.of(quantity.unit, language)
        return when (quantity.unit.type) {
            UnitType.EXACT -> exact(quantity.unit, value, language)
            UnitType.PIECE, UnitType.HOUSEHOLD, UnitType.TASTE_MEASURED ->
                "${mixedNumber(value, language)} ${UnitLabels.of(quantity.unit, language)}"
            UnitType.TASTE_UNMEASURED -> UnitLabels.of(quantity.unit, language)
        }
    }

    private fun exact(unit: QuantityUnit, value: Rational, language: InterfaceLanguage): String {
        val base = unit.baseMultiple?.let { value * Rational.of(it) } ?: value
        // A grid value is returned unchanged; this only guarantees whole base units above a rung.
        val grams = RoundingGrid.EXACT.nearest(base).floor()
        val dimension = checkNotNull(unit.dimension)
        val (rungUnit, scale) = when {
            grams < THOUSAND -> baseUnitOf(dimension, 1) to 0
            grams < MILLION -> baseUnitOf(dimension, 1_000) to 3
            else -> baseUnitOf(dimension, 1_000_000) to 6
        }
        return "${decimal(grams, scale, language)} ${UnitLabels.of(rungUnit, language)}"
    }

    private fun baseUnitOf(dimension: Dimension, multiple: Long): QuantityUnit =
        QuantityUnit.entries.single { it.dimension == dimension && it.baseMultiple == multiple }

    /** [units] / 10^[scale], written to at most three places after the mark, with no trailing zero. */
    private fun decimal(units: BigInteger, scale: Int, language: InterfaceLanguage): String {
        if (scale == 0) return grouped(units, language)
        val divisor = BigInteger.TEN.pow(scale)
        // Above the kilogram rung the grid holds whole thousands, so the last three digits are zero.
        val step = BigInteger.TEN.pow(scale - 3)
        val (whole, rest) = units.divideAndRemainder(divisor)
        val fraction = (rest / step).toString().padStart(3, '0').trimEnd('0')
        return if (fraction.isEmpty()) grouped(whole, language)
        else "${grouped(whole, language)}${language.decimalMark}$fraction"
    }

    private fun mixedNumber(value: Rational, language: InterfaceLanguage): String {
        val whole = value.floor()
        val rest = value - Rational.of(whole)
        if (rest.signum() == 0) return grouped(whole, language)
        val glyph = requireNotNull(VULGAR_FRACTIONS[rest]) { "not a value of a fraction grid: $value" }
        return if (whole.signum() == 0) glyph else "${grouped(whole, language)}$glyph"
    }

    private fun grouped(number: BigInteger, language: InterfaceLanguage): String {
        val digits = number.abs().toString()
        val out = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) out.append(language.groupSeparator)
            out.append(c)
        }
        return if (number.signum() < 0) "-$out" else out.toString()
    }

    private val THOUSAND = BigInteger.valueOf(1_000)
    private val MILLION = BigInteger.valueOf(1_000_000)

    private val VULGAR_FRACTIONS: Map<Rational, String> = mapOf(
        Rational.of(1, 4) to "¼",
        Rational.of(1, 3) to "⅓",
        Rational.of(1, 2) to "½",
        Rational.of(2, 3) to "⅔",
        Rational.of(3, 4) to "¾",
    )
}
