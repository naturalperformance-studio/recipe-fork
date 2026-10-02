package app.recipefork.core.recipe

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
 * The closed list of twelve units (DOM-016), each with the unit type the table gives it.
 *
 * For an `exact` unit, [dimension] is its dimension and [baseMultiple] is how many of the
 * dimension's base unit — gram for a mass, millilitre for a volume — one of it holds; both
 * are null for every other unit type.
 */
enum class QuantityUnit(
    val type: UnitType,
    val dimension: Dimension? = null,
    val baseMultiple: Long? = null,
) {
    GRAM(UnitType.EXACT, Dimension.MASS, 1),
    KILOGRAM(UnitType.EXACT, Dimension.MASS, 1_000),
    TONNE(UnitType.EXACT, Dimension.MASS, 1_000_000),
    MILLILITRE(UnitType.EXACT, Dimension.VOLUME, 1),
    LITRE(UnitType.EXACT, Dimension.VOLUME, 1_000),
    CUBIC_METRE(UnitType.EXACT, Dimension.VOLUME, 1_000_000),
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
}
