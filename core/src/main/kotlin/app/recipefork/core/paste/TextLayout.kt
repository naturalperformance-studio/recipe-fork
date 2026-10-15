package app.recipefork.core.paste

import app.recipefork.core.recipe.RecipeLanguage

/** One ingredient line as written, minus its list marker and emphasis, under the group heading it stands below. */
data class IngredientLine(val text: String, val group: String?)

/**
 * A pasted recipe text split into its parts: the first stage of reading a paste. Nothing here reads a quantity or a
 * dictionary entry; the lines are handed on as written, without list markers and Markdown emphasis.
 *
 * Sections are found by their headings ("Ингредиенты", "Method", …) and, where a heading is missing, by the shape of
 * the lines: a run of short lines starting with a quantity is the ingredient list, numbered or sentence lines after it
 * are the steps.
 */
data class TextLayout(
    val language: RecipeLanguage,
    val title: String?,
    val servings: Int?,
    val description: List<String>,
    val ingredients: List<IngredientLine>,
    val equipment: List<String>,
    val steps: List<String>,
    val notes: List<String>,
) {
    companion object {
        /** The parts of [text], or null when it holds no recipe: no ingredient line or no step line. */
        fun read(text: String): TextLayout? = LayoutReader(text).read()
    }
}

/** [OTHER]: under an unknown heading after the ingredients or the steps; notes, unless no heading names the steps. */
internal enum class Section { DESCRIPTION, INGREDIENTS, EQUIPMENT, STEPS, NOTES, OTHER }

private class Entry(
    var section: Section,
    val text: String,
    val numbered: Boolean,
    val group: String?,
    val marked: Boolean = false,
    val fromTable: Boolean = false,
)

private class LayoutReader(private val text: String) {
    private val entries = mutableListOf<Entry>()
    private val equipment = mutableListOf<String>()
    private val servingsSources = mutableListOf<String>()
    private var section = Section.DESCRIPTION
    private var level = 0
    private var group: String? = null
    private var seenMain = false
    private var ingredientsHeading = false
    private var stepsHeading = false
    private var stepsSeen = false

    fun read(): TextLayout? {
        val lines = PasteLines.split(text).toMutableList()
        val hasIngredientHeading = lines.any { it is PasteLine.Heading && PasteLines.sectionOf(it.text) == Section.INGREDIENTS }
        val title = lines.firstOrNull()?.let { titleOf(it, hasIngredientHeading) }
        if (title != null) {
            lines.removeAt(0)
            servingsSources += title
        }
        lines.forEachIndexed { i, line ->
            when (line) {
                is PasteLine.Heading -> heading(line, lines.getOrNull(i + 1))
                is PasteLine.Item -> item(line)
            }
        }
        if (!ingredientsHeading) ingredientsByShape()
        else if (!stepsHeading) {
            stepsByShape(entries.filter { it.section == Section.INGREDIENTS || it.section == Section.OTHER }, afterIngredient = true)
        }
        entries.filter { it.section == Section.OTHER }.forEach { it.section = Section.NOTES }
        sentencesAmongBulletsToNotes()

        val ingredients = entries.filter { it.section == Section.INGREDIENTS }.map { IngredientLine(it.text, it.group) }
        val steps = of(Section.STEPS)
        if (ingredients.isEmpty() || steps.isEmpty()) return null
        return TextLayout(
            language = PasteLines.language(text),
            title = title,
            servings = Servings.find(servingsSources + of(Section.DESCRIPTION)),
            description = of(Section.DESCRIPTION),
            ingredients = ingredients,
            equipment = equipment + of(Section.EQUIPMENT),
            steps = steps,
            notes = of(Section.NOTES),
        )
    }

    private fun of(section: Section) = entries.filter { it.section == section }.map { it.text }

    /** The first line, unless it opens a section or is a list item; led by a number only above an ingredient heading. */
    private fun titleOf(line: PasteLine, hasIngredientHeading: Boolean): String? = when (line) {
        is PasteLine.Heading -> line.text.takeIf { PasteLines.sectionOf(it) == null }
        is PasteLine.Item -> line.text.takeIf {
            !line.numbered && !line.marked && !line.fromTable && it.length <= TITLE_MAX && !it.endsWith('.') &&
                !it.endsWith(':') && (hasIngredientHeading || !PasteLines.quantityLed(it))
        }
    }

    private fun heading(h: PasteLine.Heading, next: PasteLine?) {
        val known = PasteLines.sectionOf(h.text)
        if (known != null) {
            section = known
            level = h.level
            group = null
            if (known == Section.INGREDIENTS || known == Section.STEPS) seenMain = true
            if (known == Section.INGREDIENTS) ingredientsHeading = true
            if (known == Section.STEPS) stepsHeading = true
            if (known == Section.STEPS) stepsSeen = true
            servingsSources += h.text
            return
        }
        // A heading level with the ingredient heading but followed by quantity lines, before any step: a group.
        val groupOfItsLevel = section == Section.INGREDIENTS && !stepsSeen && next is PasteLine.Item &&
            (next.fromTable || PasteLines.quantityLed(next.text))
        val closes = h.markdown && h.level <= level && !groupOfItsLevel
        when (section) {
            Section.INGREDIENTS, Section.STEPS, Section.EQUIPMENT -> when {
                closes -> {
                    if (!seenMain) servingsSources += h.text
                    section = if (seenMain) Section.OTHER else Section.DESCRIPTION
                    level = h.level
                }
                section != Section.INGREDIENTS -> Unit // a sub-heading of the steps: the steps go on
                Servings.find(listOf(h.text)) != null -> servingsSources += h.text
                else -> group = h.text
            }
            Section.DESCRIPTION, Section.NOTES, Section.OTHER -> if (h.markdown) {
                if (section == Section.DESCRIPTION) servingsSources += h.text
            } else {
                item(PasteLine.Item(h.text, numbered = false, marked = false))
            }
        }
    }

    private fun item(line: PasteLine.Item) {
        if (section != Section.STEPS) {
            PasteLines.equipmentLine(line.text)?.let { equipment += it; return }
        }
        when {
            section == Section.DESCRIPTION && line.fromTable ->
                entries += Entry(Section.INGREDIENTS, line.text, false, null, marked = true, fromTable = true)
            section == Section.DESCRIPTION && Servings.isServingsLine(line.text) -> servingsSources += line.text
            section == Section.INGREDIENTS && !line.marked && line.text.endsWith(':') ->
                entries += Entry(Section.DESCRIPTION, line.text, false, null)
            else -> entries += Entry(
                section, line.text, line.numbered, group.takeIf { section == Section.INGREDIENTS }, line.marked, line.fromTable,
            )
        }
    }

    /** No ingredient heading: the first run of short lines led by a quantity, among the lines before any section. */
    private fun ingredientsByShape() {
        val candidates = entries.filter { it.section == Section.DESCRIPTION }
        val start = candidates.indexOfFirst { ingredientShaped(it) && PasteLines.quantityLed(it.text) }
        var end = start
        while (start >= 0 && end + 1 < candidates.size && staysInRun(candidates, end + 1)) end++
        val run = if (start < 0) emptyList() else candidates.subList(start, end + 1)
        if (run.count { PasteLines.quantityLed(it.text) } >= 2) {
            run.forEach { it.section = Section.INGREDIENTS }
            if (!stepsHeading) stepsByShape(candidates.drop(end + 1), afterIngredient = false)
        } else if (!stepsHeading) {
            // An ingredient table with no heading above it: the steps are found by shape in the lines after it.
            val tableEnd = entries.indexOfLast { it.section == Section.INGREDIENTS }
            if (tableEnd >= 0) stepsByShape(entries.drop(tableEnd + 1).filter { it.section == Section.DESCRIPTION }, afterIngredient = false)
        }
    }

    /**
     * No step heading: from the first numbered or sentence line that is not led by a quantity, the [lines] are steps
     * (numbered, sentence or long lines) and notes (anything else).
     */
    private fun stepsByShape(lines: List<Entry>, afterIngredient: Boolean) {
        val from = lines.indices.firstOrNull { i ->
            (!afterIngredient || i > 0) && stepShaped(lines[i]) && !PasteLines.quantityLed(lines[i].text) &&
                !staysInRun(lines, i)
        } ?: return
        lines.drop(from).forEach { it.section = if (stepShaped(it)) Section.STEPS else Section.NOTES }
    }

    /**
     * Under bulleted ingredient lines, an unmarked sentence ("Соль не нужна, сыра хватает на всё блюдо.") is a note
     * about them, not an ingredient.
     */
    private fun sentencesAmongBulletsToNotes() {
        val listed = entries.filter { it.section == Section.INGREDIENTS && !it.fromTable }
        if (listed.none { it.marked }) return
        listed.filter { !it.marked && (PasteLines.endsSentence(it.text) || it.text.length > INGREDIENT_MAX) }
            .forEach { it.section = Section.NOTES }
    }

    /** An ingredient line, or a short line ("Salt and pepper.") with a quantity line right after it in the same part. */
    private fun staysInRun(lines: List<Entry>, i: Int): Boolean {
        val line = lines[i]
        if (ingredientShaped(line)) return true
        val next = lines.getOrNull(i + 1) ?: return false
        return !line.numbered && line.text.length <= COMPANION_MAX && next.section == line.section &&
            ingredientShaped(next) && PasteLines.quantityLed(next.text)
    }

    private fun ingredientShaped(e: Entry) =
        !e.numbered && e.text.length <= INGREDIENT_MAX && !PasteLines.endsSentence(e.text) &&
            (PasteLines.quantityLed(e.text) || e.text.length <= COMPANION_MAX)

    private fun stepShaped(e: Entry) =
        (e.numbered && !PasteLines.quantityLed(e.text)) || e.text.length > INGREDIENT_MAX || PasteLines.endsSentence(e.text)

    private companion object {
        const val TITLE_MAX = 120
        const val INGREDIENT_MAX = 80
        const val COMPANION_MAX = 40
    }
}
