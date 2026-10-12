package app.recipefork.app.print

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import app.recipefork.app.R
import app.recipefork.app.card.CardReferences
import app.recipefork.app.card.CardScale
import app.recipefork.app.card.IngredientLine
import app.recipefork.app.card.RecipeSummary
import app.recipefork.app.card.cardLanguage
import app.recipefork.app.card.ingredientList
import app.recipefork.app.card.isListed
import app.recipefork.app.card.namedLine
import app.recipefork.app.card.provenanceText
import app.recipefork.app.card.recalculated
import app.recipefork.app.card.roundingMark
import app.recipefork.app.card.shownStepText
import app.recipefork.app.card.text
import app.recipefork.app.dictionary.DictionaryEntry
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.media.cover
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.EquipmentNames
import app.recipefork.app.timer.durationText
import app.recipefork.app.timer.shownName
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import app.recipefork.core.scaling.ServingCount
import app.recipefork.core.timer.TimerName
import java.io.File

/**
 * A recipe as a document (FLOW-075 (1)): what its card's View tab shows, in its order, at the scale the card holds,
 * without what is there to touch — no done marks, no "you lack", no clip, no control.
 */
object RecipeDocument {
    /**
     * [recipe] at [scale] — the count and fixings its card shows — in the language of [resources], as the card reads
     * the interface's: the cover where [photo] finds its file on the device, the title, the summary line, a stated
     * author and the provenance line (the parent the book holds by its title [parentTitle], or that it holds none),
     * the description; "Equipment", each item named as the card names it from [equipmentEntries]; "Ingredients ·
     * 4 servings" — no count where the card shows none — with the scale's status line, the proportions warning where
     * the card gives it, each group's heading and each line as the card writes it, read with [entries]; then "Steps",
     * numbered as the card numbers them, each step's text with its references as the card renders them, its note
     * muted under it, its photo where [photo] finds it (a clip-only step saying its video is in the app), and its
     * timers as View reads them. Every amount is shown in [system] as the card shows it (null: as written). Plain and
     * quick enough for any thread; the cover is only named, never read here.
     */
    fun of(
        recipe: Recipe,
        entries: Map<Long, IngredientEntryEntity>,
        equipmentEntries: Map<Long, DictionaryEntry>,
        scale: CardScale,
        resources: Resources,
        parentTitle: String? = null,
        system: UnitSystem? = null,
        photo: (mediaId: String) -> File? = { null },
    ): PrintDocument {
        val language = cardLanguage(resources.configuration)
        val title = recipe.title.ifBlank { resources.getString(R.string.common_untitled_recipe) }
        val blocks = buildList {
            recipe.cover?.let { photo(it.mediaId) }?.takeIf { it.isFile }?.let { add(PrintBlock.Picture(it)) }
            add(PrintBlock.Title(title))
            RecipeSummary.of(recipe, language).text(resources, spoken = false)?.let { add(PrintBlock.Muted(it)) }
            recipe.statedAuthor?.let { add(PrintBlock.Paragraph(resources.getString(R.string.file_stated_author, it))) }
            recipe.parent?.let { add(PrintBlock.Paragraph(provenanceText(resources, recipe.statedParent, parentTitle))) }
            if (recipe.description.isNotBlank()) add(PrintBlock.Paragraph(recipe.description))

            if (recipe.equipment.isNotEmpty()) {
                val reading = if (language == InterfaceLanguage.RUSSIAN) RecipeLanguage.RU else RecipeLanguage.EN
                add(PrintBlock.Heading(resources.getString(R.string.card_equipment)))
                recipe.equipment.forEach { add(PrintBlock.Bullet(EquipmentNames.shown(it, equipmentEntries, reading))) }
            }

            val ordered = recipe.ingredients.sortedBy { it.position }
            val list = ingredientList(ordered, entries, language, recipe.servings?.takeIf { it >= 1 }, scale, system)
            if (ordered.isNotEmpty()) {
                val heading = resources.getString(R.string.card_ingredients)
                add(
                    PrintBlock.Heading(
                        list.servingCount?.let {
                            resources.getString(R.string.print_ingredients_heading, heading, servings(it, language, resources))
                        } ?: heading,
                    ),
                )
                val status = if (list.recalculated) R.string.card_quantities_recalculated else R.string.card_quantities_as_authored
                add(PrintBlock.Muted(resources.getString(status)))
                if (!list.holdsProportions) add(PrintBlock.Paragraph(resources.getString(R.string.card_proportions_warning)))
                list.lines.forEachIndexed { i, line ->
                    recipe.ingredientGroups.forEach { if (it.firstPosition == i) add(PrintBlock.Heading(it.name)) }
                    add(PrintBlock.Bullet(lineText(line, resources)))
                }
            }

            val references = CardReferences.of(ordered, list, language, entries, system)
            // The steps the card lists, numbered as it numbers them: one holding nothing that reads or shows is left out.
            val steps = recipe.steps.sortedBy { it.position }
                .filter { isListed(it, references::quantity, recipe.stepMedia(it.position) != null) }
            if (steps.isNotEmpty()) add(PrintBlock.Heading(resources.getString(R.string.card_steps)))
            steps.forEachIndexed { index, step ->
                val text = shownStepText(step, references)
                add(PrintBlock.Numbered(index + 1, text))
                step.note?.let { add(PrintBlock.Muted(it)) }
                // A photo under the text where the device holds it; a clip, which paper cannot play, said where the
                // step has no text of its own.
                recipe.stepMedia(step.position)?.let { media ->
                    when (media.kind) {
                        MediaKind.PHOTO -> photo(media.mediaId)?.takeIf { it.isFile }?.let { add(PrintBlock.Picture(it)) }
                        MediaKind.VIDEO -> if (text.isBlank()) add(PrintBlock.Muted(resources.getString(R.string.print_step_video)))
                    }
                }
                step.timers.forEachIndexed { at, timer ->
                    val name = TimerName.of(step, at, references::quantity) ?: return@forEachIndexed
                    val shown = resources.getString(
                        R.string.card_view_timer,
                        shownName(resources, step, at, held = null, current = name),
                        durationText(resources, timer.duration),
                    )
                    add(PrintBlock.Muted(resources.getString(R.string.print_timer, shown)))
                }
            }
        }
        return PrintDocument(title, blocks)
    }

    /**
     * An ingredient line as the card writes it, on one line: its quantity with the arrow rounding gave it, then its name
     * — or the card's wording for none — "to taste" and its note.
     */
    private fun lineText(line: IngredientLine, resources: Resources): String {
        val arrow = roundingMark(line.rounding)?.first?.takeIf { line.derived }
        val quantity = line.quantity?.let { if (arrow == null) it else "$it$NARROW_SPACE$arrow" }
        val named = namedLine(AnnotatedString(line.name ?: resources.getString(R.string.card_unnamed_ingredient)), line, Color.Unspecified).text
        return listOfNotNull(quantity, named).joinToString(" ")
    }

    /** The serving [count] as the card writes it ("4", "½", "≈4"), with its word: "4 servings", «4 порции». */
    private fun servings(count: ServingCount, language: InterfaceLanguage, resources: Resources): String {
        val number = RequestedServings.text(count.value, language)
        val shown = if (count.approximate) resources.getString(R.string.print_servings_approximate, number) else number
        val whole = count.value.takeIf { it.isWhole }?.numerator?.toInt()
        return if (whole != null) {
            resources.getQuantityString(R.plurals.print_servings, whole, shown)
        } else {
            // Below one serving a fraction is said in the singular: "½ serving".
            val below = count.value < Rational.ONE
            resources.getString(if (below) R.string.print_servings_fraction_below_one else R.string.print_servings_fraction, shown)
        }
    }

    /** A narrow space that never breaks, so an arrow stays with its quantity, as in a step. */
    private const val NARROW_SPACE = " "
}
