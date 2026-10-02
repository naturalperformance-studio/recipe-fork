package app.recipefork.core.recipe

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RoundingGrid
import java.math.BigDecimal
import java.math.BigInteger

/** What a number typed into a quantity field reads as (DOM-019). */
sealed interface QuantityReading {
    /** Nothing but spaces typed. */
    data object Empty : QuantityReading

    /** A number, of any sign and whether or not its field holds it. */
    data class Value(val value: Rational) : QuantityReading

    /** A number carrying a space, two decimal marks, or a comma and a point (DOM-019 (5)). */
    data object Separators : QuantityReading

    /** Text that is no number. */
    data object NotANumber : QuantityReading
}

/** The values a quantity field holds for a unit, as the field's error names them (DOM-017). */
enum class AcceptedValues {
    /** `exact` mass: whole grams, and whole kilograms from 1000 kg. */
    WHOLE_GRAMS,

    /** `exact` volume: whole millilitres, and whole litres from 1000 l. */
    WHOLE_MILLILITRES,

    /** Indivisible `piece`: a whole number of one or more. */
    WHOLE_PIECES,

    /** Divisible `piece`: ½, a whole number, or a whole number followed by ½. */
    HALVES,

    /** `household` and `taste_measured`: ¼, ⅓, ½, ⅔, ¾, a whole number, or a whole number followed by one of them. */
    KITCHEN_FRACTIONS,
}

/** Why a row's quantity field is marked as an error (DOM-017 (6)). */
sealed interface QuantityProblem {
    /** What was typed carries a digit grouping separator or more than one decimal mark (DOM-019 (5)). */
    data object Separators : QuantityProblem

    /** What the field holds is no value of its unit type's grid, or no number at all. */
    data class NotAccepted(val accepts: AcceptedValues) : QuantityProblem
}

/**
 * The editor's quantity field: how a typed number is read (DOM-019), which values it holds (DOM-017), and how a
 * value is written back into it.
 */
object QuantityInput {

    /**
     * Reads [text] as a number. A comma and a point are both the decimal mark, in either interface language, so
     * `1,500` is one and a half (DOM-019 (1–3)); a space of any width, an apostrophe, or a second mark is a digit
     * grouping separator and is not read (DOM-019 (5)). A number is also read as a vulgar fraction, alone or after
     * a whole number (`⅓`, `1½`), or as `n/d`, which is how a keyboard without those glyphs writes ⅓.
     */
    fun read(text: String): QuantityReading {
        val typed = text.trim()
        if (typed.isEmpty()) return QuantityReading.Empty
        if (typed.any { it !in '0'..'9' && it !in SYMBOLS && !it.isWhitespace() && it !in GROUPING }) {
            return QuantityReading.NotANumber
        }
        if (typed.any { it.isWhitespace() || it in GROUPING } || typed.count { it in MARKS } > 1) {
            return QuantityReading.Separators
        }
        val negative = typed.first() in MINUS
        val body = if (negative) typed.drop(1) else typed
        val value = decimal(body) ?: mixed(body) ?: fraction(body) ?: return QuantityReading.NotANumber
        return QuantityReading.Value(if (negative) -value else value)
    }

    /**
     * The grid whose values the field holds for [unit], or null for `taste_unmeasured`, which holds no quantity.
     * A `piece` whose divisibility the author has not yet given is held to the divisible grid, the wider of the
     * two; answering "indivisible" then marks a half that the narrower grid does not hold (DOM-017 (6)).
     */
    fun grid(unit: QuantityUnit, divisible: Boolean?): RoundingGrid? =
        RoundingGrid.of(unit, if (unit.type == UnitType.PIECE) divisible ?: true else divisible)

    /** True when the field holds [value] in [unit]: above zero and on its unit type's grid (DOM-017). */
    fun holds(value: Rational, unit: QuantityUnit, divisible: Boolean?): Boolean {
        val grid = grid(unit, divisible) ?: return false
        val onGrid = unit.baseMultiple?.let { value * Rational.of(it) } ?: value
        return grid.holds(onGrid)
    }

    /** The values the field holds for [unit], or null for `taste_unmeasured`. */
    fun accepts(unit: QuantityUnit, divisible: Boolean?): AcceptedValues? = when (grid(unit, divisible)) {
        RoundingGrid.EXACT -> if (unit.dimension == Dimension.MASS) AcceptedValues.WHOLE_GRAMS else AcceptedValues.WHOLE_MILLILITRES
        RoundingGrid.PIECE_INDIVISIBLE -> AcceptedValues.WHOLE_PIECES
        RoundingGrid.PIECE_DIVISIBLE -> AcceptedValues.HALVES
        RoundingGrid.FRACTIONAL -> AcceptedValues.KITCHEN_FRACTIONS
        RoundingGrid.SERVING_COUNT, null -> null
    }

    /**
     * Why [ingredient]'s quantity field is marked, or null when it is not: it holds a value on its grid, it holds
     * nothing yet, or the row is "to taste". The mark follows the row's unit and divisibility as they are now, so
     * changing either can raise it or clear it (DOM-017 (6)).
     */
    fun problem(ingredient: Ingredient): QuantityProblem? {
        if (ingredient.unitType == UnitType.TASTE_UNMEASURED) return null
        val unit = ingredient.unit ?: return null
        val accepts = accepts(unit, ingredient.divisible) ?: return null
        ingredient.enteredQuantity?.let { typed ->
            return if (read(typed) == QuantityReading.Separators) QuantityProblem.Separators
            else QuantityProblem.NotAccepted(accepts)
        }
        val quantity = ingredient.quantity ?: return null
        return if (holds(quantity, unit, ingredient.divisible)) null else QuantityProblem.NotAccepted(accepts)
    }

    /**
     * [value] as the field writes it in [unit]: a value of a fraction grid as the grid writes it — a whole number,
     * a vulgar fraction, or both (`0,5 ч. л.` is ½) — and an `exact` value as a decimal number in the unit the
     * author chose, with the decimal mark of [language] (DOM-019 (1, 2)). No digit is grouped, so the field reads
     * back what it wrote. A value off its grid is written so that it reads back as the same value.
     */
    fun write(value: Rational, unit: QuantityUnit, language: InterfaceLanguage): String {
        val glyphs = mixedNumber(value)
        if (unit.type != UnitType.EXACT && glyphs != null) return glyphs
        return decimalText(value, language) ?: glyphs ?: value.toString()
    }

    /** What the quantity field shows for [ingredient] when it is opened: what was typed, or the value written. */
    fun fieldText(ingredient: Ingredient, language: InterfaceLanguage): String {
        ingredient.enteredQuantity?.let { return it }
        val quantity = ingredient.quantity ?: return ""
        val unit = ingredient.unit ?: return ""
        return write(quantity, unit, language)
    }

    private fun decimal(body: String): Rational? {
        if (!DECIMAL.matches(body)) return null
        val number = BigDecimal(body.replace(',', '.'))
        return if (number.scale() > 0) Rational.of(number.unscaledValue(), BigInteger.TEN.pow(number.scale()))
        else Rational.of(number.toBigIntegerExact())
    }

    private fun mixed(body: String): Rational? {
        val match = MIXED.matchEntire(body) ?: return null
        val (whole, glyph) = match.destructured
        val fraction = GLYPHS.entries.single { it.value == glyph.single() }.key
        return Rational.of(whole.ifEmpty { "0" }.toBigInteger()) + fraction
    }

    private fun fraction(body: String): Rational? {
        val match = FRACTION.matchEntire(body) ?: return null
        val (numerator, denominator) = match.destructured
        if (denominator.toBigInteger().signum() == 0) return null
        return Rational.of(numerator.toBigInteger(), denominator.toBigInteger())
    }

    /** A positive [value] whose fractional part is a kitchen fraction, written as a whole number, a glyph, or both. */
    private fun mixedNumber(value: Rational): String? {
        if (value.signum() <= 0) return null
        val whole = value.floor()
        val rest = value - Rational.of(whole)
        if (rest.signum() == 0) return whole.toString()
        val glyph = GLYPHS[rest] ?: return null
        return if (whole.signum() == 0) "$glyph" else "$whole$glyph"
    }

    /** [value] as a decimal number, or null when it has no finite decimal form (⅓). */
    private fun decimalText(value: Rational, language: InterfaceLanguage): String? {
        var rest = value.denominator
        for (prime in listOf(TWO, FIVE)) while (rest.mod(prime).signum() == 0) rest /= prime
        if (rest != BigInteger.ONE) return null
        val number = BigDecimal(value.numerator).divide(BigDecimal(value.denominator)).stripTrailingZeros()
        return number.toPlainString().replace('.', language.decimalMark)
    }

    private val TWO = BigInteger.valueOf(2)
    private val FIVE = BigInteger.valueOf(5)
    private val GLYPHS: Map<Rational, Char> = mapOf(
        Rational.of(1, 4) to '¼',
        Rational.of(1, 3) to '⅓',
        Rational.of(1, 2) to '½',
        Rational.of(2, 3) to '⅔',
        Rational.of(3, 4) to '¾',
    )
    private const val MARKS = ",."
    private const val MINUS = "-−"
    private const val GROUPING = "'’"
    private val SYMBOLS: Set<Char> = (MARKS + MINUS + "/").toSet() + GLYPHS.values
    private val DECIMAL = Regex("""[0-9]+[.,]?[0-9]*|[.,][0-9]+""")
    private val MIXED = Regex("""([0-9]*)([¼⅓½⅔¾])""")
    private val FRACTION = Regex("""([0-9]+)/([0-9]+)""")
}

/**
 * [this] row with [text] typed into its quantity field. A number above zero is kept as its value, on its grid or
 * not (DOM-017 (6)); anything else typed — zero, a negative number, a number with separators, or no number — is
 * kept as typed, so the field gives it back after the editor is left (FLOW-024 (6)).
 */
fun Ingredient.withTypedQuantity(text: String): Ingredient = when (val reading = QuantityInput.read(text)) {
    QuantityReading.Empty -> copy(quantity = null, enteredQuantity = null)
    is QuantityReading.Value ->
        if (reading.value.signum() > 0) copy(quantity = reading.value, enteredQuantity = null)
        else copy(quantity = null, enteredQuantity = text)
    QuantityReading.Separators, QuantityReading.NotANumber -> copy(quantity = null, enteredQuantity = text)
}

/**
 * [this] row with [unit] chosen. Its unit type follows from the unit (DOM-016 (2)). "To taste" holds no unit and no
 * quantity (DOM-002); leaving it starts the quantity empty (FLOW-023 (9)). Any other change keeps the quantity as it
 * stands, on the new grid or not (DOM-017 (6)). Divisibility and a piece's weight belong to `piece` only.
 */
fun Ingredient.withUnit(unit: QuantityUnit): Ingredient {
    if (unit.type == UnitType.TASTE_UNMEASURED) {
        return copy(unitType = unit.type, unit = null, quantity = null, enteredQuantity = null, pieceWeight = null, divisible = null)
    }
    val piece = unit.type == UnitType.PIECE
    val fromTaste = unitType == UnitType.TASTE_UNMEASURED
    return copy(
        unitType = unit.type,
        unit = unit,
        quantity = if (fromTaste) null else quantity,
        enteredQuantity = if (fromTaste) null else enteredQuantity,
        pieceWeight = pieceWeight.takeIf { piece },
        divisible = divisible.takeIf { piece },
    )
}

/** What the serving count field reads a typed text as (DOM-015). */
sealed interface ServingCountReading {
    /** The field holds no value. */
    data object Empty : ServingCountReading

    data class Count(val servings: Int) : ServingCountReading

    /** Zero, a negative number, a fraction, or anything but a whole number: the field does not hold it. */
    data object Refused : ServingCountReading
}

/** The editor's serving count field: a whole number of one or more, or nothing (DOM-015). */
object ServingCountInput {
    fun read(text: String): ServingCountReading {
        val typed = text.trim()
        if (typed.isEmpty()) return ServingCountReading.Empty
        if (typed.any { it !in '0'..'9' }) return ServingCountReading.Refused
        val count = typed.toBigInteger()
        return if (count < BigInteger.ONE || count > Int.MAX_VALUE.toBigInteger()) ServingCountReading.Refused
        else ServingCountReading.Count(count.toInt())
    }
}
