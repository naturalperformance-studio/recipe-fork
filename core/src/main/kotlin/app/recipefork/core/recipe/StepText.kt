package app.recipefork.core.recipe

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.Scaling
import app.recipefork.core.scaling.UnitLabels

/** One run of a step as it reads: text the author typed, or an ingredient reference rendered as its quantity. */
sealed interface StepRun {
    val text: String

    data class Typed(override val text: String) : StepRun

    /**
     * The reference to the ingredient at [ingredientPosition], reading [text] (FLOW-032): its whole quantity, or the
     * part [share] of it.
     */
    data class Reference(val ingredientPosition: Int, override val text: String, val share: IngredientShare? = null) : StepRun
}

/**
 * What a reference reads as: the text a screen gives [IngredientReference] — its ingredient's whole quantity or the
 * reference's part of it — or null for a reference to no ingredient of the recipe.
 */
typealias ReferenceQuantity = (reference: IngredientReference) -> String?

/**
 * A step's text with its ingredient references in place (FLOW-032). The step's own text never holds a quantity: a
 * reference sits at an offset in it and reads as whatever a quantity formatter gives for it — the
 * authored quantity in the editor ([ReferenceText.authored]), the scaled one on the card. One rendering for every
 * screen, so a step reads the same wherever it is shown at the same scale (SCALE-007, DOM-007).
 */
object StepText {
    /**
     * [step] as runs, in reading order: its typed text cut at each reference's offset, and each reference as
     * [quantity] gives it, held together ([unbreakable]) so that a line never breaks inside it — not between its
     * number and its unit, nor inside a unit of two words. A reference [quantity] gives nothing for — one to no
     * ingredient of the recipe — is left out, and the text around it reads as typed. An offset beyond the text places
     * the reference at its end.
     */
    fun runs(step: Step, quantity: ReferenceQuantity): List<StepRun> =
        spliced(step) { reference -> quantity(reference)?.let(::unbreakable) }

    /** [step] as one string, each reference read as [quantity] gives it, held together as in [runs]. */
    fun render(step: Step, quantity: ReferenceQuantity): String =
        runs(step, quantity).joinToString("") { it.text }

    /**
     * [text] — a reference as it reads — with each of its spaces a no-break space (U+00A0): «4½ ст. л.», «1,5 кг»,
     * "1.5 kg" read on one line wherever a line breaks around them. Only display text is held so; what a step stores
     * keeps no quantity at all.
     */
    fun unbreakable(text: String): String = text.replace(' ', NO_BREAK_SPACE)

    /** The space a line never breaks at. */
    const val NO_BREAK_SPACE = '\u00A0'

    /**
     * [step] as runs, each reference exactly as [quantity] gives it, its spaces as given: [runs] before a reference is
     * held together. For a reading of the step that is no display — what a held timer is started from.
     */
    internal fun spliced(step: Step, quantity: ReferenceQuantity): List<StepRun> {
        val text = step.text
        val runs = mutableListOf<StepRun>()
        var at = 0
        for (reference in step.ingredientReferences.sortedBy { it.textOffset }) {
            val shown = quantity(reference) ?: continue
            val offset = reference.textOffset.coerceIn(at, text.length)
            if (offset > at) runs += StepRun.Typed(text.substring(at, offset))
            runs += StepRun.Reference(reference.ingredientPosition, shown, reference.share)
            at = offset
        }
        if (at < text.length) runs += StepRun.Typed(text.substring(at))
        return runs
    }
}

/** What a reference reads as at the authored scale, the recipe's own (FLOW-032 (5–7)). */
object ReferenceText {
    /** What stands for a quantity not yet entered: visibly empty, and a character range like any reference. */
    const val PLACEHOLDER = "___"

    /**
     * [ingredient]'s reference at the authored scale in [language]: its quantity and unit as the ingredient list
     * writes them ("200 г"); "to taste" in the interface language for a `taste_unmeasured` row, nothing in it marked
     * as derived; for a row with no quantity yet, [PLACEHOLDER] followed by the unit where one is chosen. A number the
     * row holds off its grid, or before its divisibility is given, reads as the quantity field writes it. A "to taste"
     * row reads as the list writes it too: its authored quantity is the label alone.
     */
    fun authored(ingredient: Ingredient, language: InterfaceLanguage): String {
        ingredient.authoredQuantity?.let { authored ->
            runCatching { QuantityText.of(Scaling.display(authored, ScaleFactor.ONE), language) }.getOrNull()?.let { return it }
        }
        val unit = ingredient.unit?.let { UnitLabels.of(it, language) }
        val quantity = ingredient.quantity?.let { value -> ingredient.unit?.let { QuantityInput.write(value, it, language) } }
            ?: PLACEHOLDER
        return if (unit == null) quantity else "$quantity $unit"
    }

    /**
     * The part [share] of [ingredient] at the authored scale in [language], written as the whole is ("2 шт", "1,5 кг");
     * a value the grid does not hold reads as the quantity field writes it. A share that no longer fits the row's unit
     * ([IngredientShares.fits]) reads as the whole.
     */
    fun authored(ingredient: Ingredient, share: IngredientShare?, language: InterfaceLanguage): String {
        if (share == null || !IngredientShares.fits(share, ingredient.unit)) return authored(ingredient, language)
        IngredientShares.display(share, ingredient, ScaleFactor.ONE)?.let { shown ->
            runCatching { QuantityText.of(shown, language) }.getOrNull()?.let { return it }
        }
        return "${QuantityInput.write(share.amount, share.unit, language)} ${UnitLabels.of(share.unit, language)}"
    }

    /** The authored-scale formatter for the references of a recipe holding [ingredients]: null for no such row. */
    fun authored(ingredients: List<Ingredient>, language: InterfaceLanguage): ReferenceQuantity =
        { reference ->
            ingredients.firstOrNull { it.position == reference.ingredientPosition }?.let { authored(it, reference.share, language) }
        }
}
