package app.recipefork.core.shopping

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.DisplayedQuantity
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding

/** What a shopping line is called by: a dictionary entry, or words of an author's own, as written. */
sealed interface ShoppingName {
    data class Entry(val id: Long) : ShoppingName
    data class FreeText(val text: String) : ShoppingName
}

/**
 * An amount on the shopping list: [value] in [unit], a value of the unit's rounding grid. An `exact` amount is held in
 * the line unit of its ladder (gram, millilitre, ounce, fluid ounce), or in gram or millilitre where metric and US
 * parts meet; [text] writes it on the magnitude ladder (300 g + 1.2 kg reads "1.5 kg", 12 oz + 8 oz reads "1¼ lb").
 */
data class ShoppingAmount(val unit: QuantityUnit, val value: Rational) {
    /** The amount as the card writes a quantity, in [language]; the approximate mark is not part of it. */
    fun text(language: InterfaceLanguage): String =
        QuantityText.of(DisplayedQuantity(unit, value, derived = false, rounding = Rounding.NONE), language)
}

/**
 * A part of an approximate line counted in the line's unit: [original] as the recipes need it, [countedAs] in the line's
 * unit. [belowFloor] is true exactly when the part counted is less than the floor of the line's grid (SCALE-005):
 * [countedAs] then holds that floor, and the part reads as less than it ("< ¼ cup"), since the line's total counted
 * the part itself and not the floor.
 */
data class CountedPart(
    val original: ShoppingAmount,
    val countedAs: ShoppingAmount,
    val belowFloor: Boolean = false,
) {
    /**
     * What the part was counted as, in [language]: [countedAs] as an amount is written, or "< " and it where the part
     * lies below the floor ("< ¼ cup", «< ¼ стакан»). The approximate mark is not part of it.
     */
    fun countedText(language: InterfaceLanguage): String =
        if (belowFloor) "< ${countedAs.text(language)}" else countedAs.text(language)
}

/** How much a shopping line asks for. */
sealed interface ShoppingQuantity {
    /** An amount, followed by "+ to taste" when [plusToTaste]: a recipe also takes the entry to taste. */
    data class Measured(val amount: ShoppingAmount, val plusToTaste: Boolean = false) : ShoppingQuantity

    /** "to taste" alone. */
    data object ToTaste : ShoppingQuantity

    /** A row that cannot be scaled (incomplete, or off its grid), shown as the author wrote it, never added to. */
    data class AsWritten(val row: Ingredient) : ShoppingQuantity
}

/**
 * A measured line as the reader's system shows it (v0.35): its [amount], the line's exact total converted once by
 * [UnitConversion.convert][app.recipefork.core.scaling.UnitConversion.convert] and rounded on the shown unit's grid;
 * [approximate] where the line merged parts by the entry's measures or the conversion went through them; and, on an
 * approximate line, [counted]: each part written in a unit that does not convert exactly into the shown one — as the
 * recipes need it, and what it was counted as in the shown unit.
 */
data class ShoppingShown(
    val amount: ShoppingAmount,
    val approximate: Boolean,
    val counted: List<CountedPart>,
)

/**
 * One line of a shopping list: what it is called, how much it asks for, whether that amount is [approximate] — parts
 * in other units were counted by the entry's measures, each named in [counted] — and the [recipes] it comes from, in
 * the order they were given. [quantity], [approximate] and [counted] stand in the units the recipes are written in,
 * the same in every system; [shown] is a measured line in the reader's system, where one was asked for.
 */
data class ShoppingLine(
    val name: ShoppingName,
    val quantity: ShoppingQuantity,
    val approximate: Boolean = false,
    val counted: List<CountedPart> = emptyList(),
    val recipes: List<RecipeId>,
    val shown: ShoppingShown? = null,
)
