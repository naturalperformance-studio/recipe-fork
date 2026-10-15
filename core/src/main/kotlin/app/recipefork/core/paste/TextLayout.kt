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

/** [pinned]: stays where it was put, whatever its shape; [fromHeading]: a colon or bold heading line read as text. */
private class Entry(
    var section: Section,
    var text: String,
    val numbered: Boolean,
    var group: String?,
    val marked: Boolean = false,
    val fromTable: Boolean = false,
    val pinned: Boolean = false,
    val fromHeading: Boolean = false,
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
    private var listedUnderHeading = false

    /** Per line: a numbered line comes after it before the next Markdown heading. */
    private var numberedBelow = BooleanArray(0)

    fun read(): TextLayout? {
        val lines = PasteLines.split(text).toMutableList()
        val hasIngredientHeading = lines.any { it is PasteLine.Heading && PasteLines.sectionOf(it.text) == Section.INGREDIENTS }
        val title = lines.firstOrNull()?.let { titleOf(it, hasIngredientHeading) }
        if (title != null) {
            lines.removeAt(0)
            servingsSources += title
        }
        numberedBelow = numberedBelow(lines)
        // "Порции" over "8": the servings on two lines; the number alone is no line of the text, the label is read for them.
        val pairs = Servings.pairs(text)
        servingsSources += pairs
        lines.forEachIndexed { i, line ->
            when (line) {
                is PasteLine.Heading -> heading(lines, i)
                is PasteLine.Item -> if (pairs.isEmpty() || !Servings.isPairLabel(line.text)) item(line, lines.getOrNull(i + 1))
            }
        }
        if (!ingredientsHeading) ingredientsByShape()
        else if (!stepsHeading) {
            stepsByShape(entries.filter { it.section == Section.INGREDIENTS || it.section == Section.OTHER }, afterIngredient = true)
        }
        entries.filter { it.section == Section.OTHER }.forEach { it.section = Section.NOTES }
        namesOverAmounts()
        sentencesAmongBulletsToNotes()
        entries.filter { it.section == Section.STEPS && PasteLines.closing(it.text) }.forEach { it.section = Section.NOTES }

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
            !line.numbered && (!line.marked || line.symbol) && !line.fromTable && it.length <= TITLE_MAX &&
                !it.endsWith('.') && !it.endsWith(':') && (hasIngredientHeading || !PasteLines.ingredientLed(it))
        }
    }

    private fun heading(lines: List<PasteLine>, i: Int) {
        val h = lines[i] as PasteLine.Heading
        val next = lines.getOrNull(i + 1)
        val known = PasteLines.sectionOf(h.text)
        if (known != null) {
            section = known
            level = h.level
            group = null
            listedUnderHeading = false
            if (known == Section.INGREDIENTS || known == Section.STEPS) seenMain = true
            if (known == Section.INGREDIENTS) ingredientsHeading = true
            if (known == Section.STEPS) stepsHeading = true
            if (known == Section.STEPS) stepsSeen = true
            servingsSources += h.text
            return
        }
        // A heading level with the ingredient heading but followed by quantity lines, before any step: a group.
        val groupOfItsLevel = section == Section.INGREDIENTS && !stepsSeen && next is PasteLine.Item &&
            (next.fromTable || PasteLines.ingredientLed(next.text))
        // "## Утром: аэрогриль" level with "## Приготовление" and numbered lines under it: the method goes on.
        val methodGoesOn = section == Section.STEPS && numberedBelow[i]
        val closes = h.markdown && h.level <= level && !groupOfItsLevel && !methodGoesOn
        when (section) {
            Section.INGREDIENTS, Section.STEPS, Section.EQUIPMENT -> when {
                closes -> {
                    if (!seenMain) servingsSources += h.text
                    section = if (seenMain) Section.OTHER else Section.DESCRIPTION
                    level = h.level
                }
                section != Section.INGREDIENTS -> Unit // a sub-heading of the steps: the steps go on
                Servings.find(listOf(h.text)) != null -> {
                    servingsSources += h.text
                    group = null
                }
                else -> group = h.text
            }
            Section.DESCRIPTION, Section.NOTES, Section.OTHER -> if (h.markdown) {
                if (section == Section.DESCRIPTION) servingsSources += h.text
            } else {
                item(PasteLine.Item(h.text, numbered = false, marked = false), next, fromHeading = true)
            }
        }
    }

    /** For each line, whether a numbered line follows it before the next Markdown heading: one backward pass. */
    private fun numberedBelow(lines: List<PasteLine>): BooleanArray {
        val below = BooleanArray(lines.size)
        var ahead = false
        for (j in lines.indices.reversed()) {
            below[j] = ahead
            val line = lines[j]
            if (line is PasteLine.Heading && line.markdown) ahead = false
            if (line is PasteLine.Item && line.numbered) ahead = true
        }
        return below
    }

    private fun item(line: PasteLine.Item, next: PasteLine?, fromHeading: Boolean = false) {
        if (section != Section.STEPS) {
            PasteLines.equipmentLine(line.text)?.let { equipment += it; return }
            if (Servings.isServingsLine(line.text)) {
                servingsSources += line.text
                return
            }
            if (PasteLines.timeLine(line.text)) {
                entries += Entry(Section.DESCRIPTION, line.text, false, null, pinned = true)
                return
            }
        }
        if (section == Section.INGREDIENTS && !line.marked && !line.fromTable) {
            // "For 4 servings", "На 4 порции": the servings, never a group or an ingredient.
            if (line.text.length <= COMPANION_MAX && !PasteLines.ingredientLed(line.text) && Servings.find(listOf(line.text)) != null) {
                servingsSources += line.text
                return
            }
            groupLine(line.text, next)?.let { group = it; return }
        }
        // "- ## Соус", "- STIR-FRY" over ingredient lines: a group, marked or not.
        if (section == Section.INGREDIENTS && !line.fromTable && next is PasteLine.Item && PasteLines.ingredientLed(next.text)) {
            PasteLines.partHeading(line.text, next.text)?.let { group = it; return }
        }
        when {
            section == Section.DESCRIPTION && line.fromTable ->
                entries += Entry(Section.INGREDIENTS, line.text, false, null, marked = true, fromTable = true)
            section == Section.INGREDIENTS && !line.marked && line.text.endsWith(':') ->
                entries += Entry(Section.DESCRIPTION, line.text, false, null)
            else -> {
                if (section == Section.INGREDIENTS) listedUnderHeading = true
                entries += Entry(
                    section, line.text, line.numbered, group.takeIf { section == Section.INGREDIENTS }, line.marked,
                    line.fromTable, fromHeading = fromHeading,
                )
            }
        }
    }

    /**
     * The group an unmarked line under the ingredient heading opens, or null when it is not a group: a colon line up to
     * [COLON_GROUP_MAX] between ingredient lines ("Сырники-основа (базовое тесто, 8–10 сырников):"), or a short line
     * that is not an ingredient and either stands above a marked line (WP Recipe Maker) or starts with "For " / "Для ",
     * holds no number and stands above an ingredient line ("Для теста" over "200 г муки", not "Для жарки масло" over
     * "Соль").
     */
    private fun groupLine(text: String, next: PasteLine?): String? {
        val ingredientNext = next is PasteLine.Item && (next.marked || PasteLines.ingredientLed(next.text))
        if (text.endsWith(':')) {
            return text.trimEnd(':').trim().takeIf { text.length <= COLON_GROUP_MAX && listedUnderHeading && ingredientNext }
        }
        if (text.length > COMPANION_MAX || PasteLines.ingredientLed(text) || PasteLines.endsSentence(text)) return null
        if (next !is PasteLine.Item) return null
        val forPart = FOR_PART.containsMatchIn(text) && text.none(Char::isDigit) && PasteLines.ingredientLed(next.text)
        return text.takeIf { forPart || (next.marked && !next.fromTable) }
    }

    /**
     * No ingredient heading: the first run of short lines led by a quantity or a name and a dash, among the lines before
     * any section. A heading line inside the run or right above it ("Для теста:", "FOR THE GLAZE") is the group of the
     * lines below it.
     */
    private fun ingredientsByShape() {
        val candidates = entries.filter { it.section == Section.DESCRIPTION && !it.pinned }
        val start = candidates.indexOfFirst { ingredientShaped(it) && PasteLines.ingredientLed(it.text) }
        var end = start
        while (start >= 0 && end + 1 < candidates.size && staysInRun(candidates, end + 1)) end++
        val run = if (start < 0) emptyList() else candidates.subList(start, end + 1)
        if (run.count { PasteLines.ingredientLed(it.text) } >= 2) {
            val above = candidates.getOrNull(start - 1)?.takeIf { it.fromHeading || PasteLines.partHeading(it.text, candidates[start].text) != null }
            val groups = mutableSetOf<Entry>()
            var current: String? = null
            val lines = listOfNotNull(above) + run
            lines.forEachIndexed { i, line ->
                val next = lines.getOrNull(i + 1)
                val part = next?.takeIf { PasteLines.ingredientLed(it.text) }?.let { PasteLines.partHeading(line.text, it.text) }
                if (line.fromHeading && Servings.find(listOf(line.text)) != null) {
                    current = null // "На 4 порции:" states the servings: it stays in the description, and ends a group
                } else if ((line.fromHeading || part != null) && !PasteLines.ingredientLed(line.text)) {
                    current = if (line.fromHeading) line.text else part
                    groups += line
                } else {
                    line.section = Section.INGREDIENTS
                    line.group = current
                }
            }
            entries.removeAll(groups)
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
     * eda.ru's shape: each ingredient's name on a line of its own and its amount on the next ("Говядина на кости" over
     * "800 г", "Соль" over "по вкусу"). Where two or more unmarked ingredient lines with no quantity stand over a line
     * that is only an amount, each such pair is one line, "<name> — <amount>".
     */
    private fun namesOverAmounts() {
        fun pairAt(i: Int): Boolean {
            val (name, amount) = entries[i] to (entries.getOrNull(i + 1) ?: return false)
            return name.section == Section.INGREDIENTS && amount.section == Section.INGREDIENTS && name.group == amount.group &&
                !name.marked && !amount.marked && !name.fromTable && !amount.fromTable &&
                !PasteLines.amountOnly(name.text) && !PasteLines.quantityLed(name.text) && PasteLines.amountOnly(amount.text)
        }
        if (entries.indices.count(::pairAt) < 2) return
        val joined = ArrayList<Entry>(entries.size)
        var i = 0
        while (i < entries.size) {
            val entry = entries[i]
            if (pairAt(i)) {
                entry.text = "${entry.text} — ${entries[i + 1].text}"
                i++
            }
            joined += entry
            i++
        }
        entries.clear()
        entries += joined
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
            ingredientShaped(next) && PasteLines.ingredientLed(next.text)
    }

    private fun ingredientShaped(e: Entry) =
        !e.numbered && e.text.length <= INGREDIENT_MAX && !PasteLines.endsSentence(e.text) &&
            (PasteLines.ingredientLed(e.text) || e.text.length <= COMPANION_MAX)

    private fun stepShaped(e: Entry) =
        (e.numbered && !PasteLines.quantityLed(e.text)) || e.text.length > INGREDIENT_MAX || PasteLines.endsSentence(e.text)

    private companion object {
        const val TITLE_MAX = 120
        const val INGREDIENT_MAX = 80
        const val COMPANION_MAX = 40
        const val COLON_GROUP_MAX = 60
        val FOR_PART = Regex("""^(?:for|для)\s""", RegexOption.IGNORE_CASE)
    }
}
