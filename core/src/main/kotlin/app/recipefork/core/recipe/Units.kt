package app.recipefork.core.recipe

import app.recipefork.core.scaling.Rational

/**
 * The five unit types of the domain model (spec/50-domain-model/00-index.md, "Unit types").
 * The type decides how a quantity behaves when a recipe is scaled.
 */
enum class UnitType(
    /** False only for `taste_unmeasured`, which holds no quantity. */
    val carriesQuantity: Boolean,
) {
    EXACT(true),
    PIECE(true),
    HOUSEHOLD(true),
    TASTE_MEASURED(true),
    TASTE_UNMEASURED(false),
}

/** The physical dimension of an `exact` unit: nothing converts a mass to a volume (SCALE-018). */
enum class Dimension { MASS, VOLUME }

/**
 * The closed list of units: the twelve of DOM-016, each with the unit type the table gives it, and the three US
 * customary `exact` units of v0.35 — ounce, pound and fluid ounce.
 *
 * For an `exact` unit, [dimension] is its dimension, [baseMultiple] is how many of the dimension's base unit — gram for
 * a mass, millilitre for a volume — one of it holds, exactly (an ounce is 28.349523125 g), and [system] is the system it
 * belongs to; all three are null for every other unit type.
 */
enum class QuantityUnit(
    val type: UnitType,
    val dimension: Dimension? = null,
    val baseMultiple: Rational? = null,
    val system: UnitSystem? = null,
) {
    GRAM(UnitType.EXACT, Dimension.MASS, Rational.ONE, UnitSystem.METRIC),
    KILOGRAM(UnitType.EXACT, Dimension.MASS, Rational.of(1_000), UnitSystem.METRIC),
    TONNE(UnitType.EXACT, Dimension.MASS, Rational.of(1_000_000), UnitSystem.METRIC),
    MILLILITRE(UnitType.EXACT, Dimension.VOLUME, Rational.ONE, UnitSystem.METRIC),
    LITRE(UnitType.EXACT, Dimension.VOLUME, Rational.of(1_000), UnitSystem.METRIC),
    CUBIC_METRE(UnitType.EXACT, Dimension.VOLUME, Rational.of(1_000_000), UnitSystem.METRIC),

    /** The avoirdupois ounce: 28.349523125 g exactly. */
    OUNCE(UnitType.EXACT, Dimension.MASS, Rational.of(28_349_523_125, 1_000_000_000), UnitSystem.US),

    /** The avoirdupois pound: 16 oz, 453.59237 g exactly. */
    POUND(UnitType.EXACT, Dimension.MASS, Rational.of(45_359_237, 100_000), UnitSystem.US),

    /** The US fluid ounce: 29.5735295625 ml exactly. */
    FLUID_OUNCE(UnitType.EXACT, Dimension.VOLUME, Rational.of(295_735_295_625, 10_000_000_000), UnitSystem.US),
    PIECE(UnitType.PIECE),
    TEASPOON(UnitType.HOUSEHOLD),
    TABLESPOON(UnitType.HOUSEHOLD),
    CUP(UnitType.HOUSEHOLD),
    PINCH(UnitType.TASTE_MEASURED),
    TO_TASTE(UnitType.TASTE_UNMEASURED),
    ;

    /** The base unit of this unit's dimension (gram or millilitre); null outside `exact`. */
    val baseUnit: QuantityUnit?
        get() = when (dimension) {
            Dimension.MASS -> GRAM
            Dimension.VOLUME -> MILLILITRE
            null -> null
        }

    /**
     * The unit an `exact` line is computed and rounded in: the smallest unit of this unit's system and dimension — gram,
     * millilitre, ounce or fluid ounce. Its magnitude ladder writes the line (g → kg → t, oz → lb), so an amount written
     * in one system stays in it when scaled. Null outside `exact`.
     */
    val lineUnit: QuantityUnit?
        get() = when (system) {
            UnitSystem.METRIC -> baseUnit
            UnitSystem.US -> if (dimension == Dimension.MASS) OUNCE else FLUID_OUNCE
            null -> null
        }

    /** How many of [lineUnit] one of this unit holds, exactly (1000 for a kilogram, 16 for a pound); null outside `exact`. */
    val lineMultiple: Rational? by lazy { lineUnit?.let { checkNotNull(baseMultiple) / checkNotNull(it.baseMultiple) } }
}
