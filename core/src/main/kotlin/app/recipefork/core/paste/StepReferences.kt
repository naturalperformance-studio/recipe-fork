package app.recipefork.core.paste

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.IngredientShares
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.ReferenceSpacing
import app.recipefork.core.recipe.Step
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.DictionaryMatch
import app.recipefork.core.search.Inflection
import app.recipefork.core.search.WordPrefix

/** Where the reference of an ingredient a recipe's steps first name without a quantity goes: before the name, after it, or nowhere. */
enum class FirstMention { BEFORE, AFTER, NONE }

/**
 * The ingredient references of a pasted recipe's steps, read in their order. A step names a row by the words its
 * dictionary entry is bound by, in any form ("сахара" is the sugar row), or by the entry above it ("йогурта" is the
 * Greek yogurt row when it is the only yogurt); a free-text row by its words. A quantity before the name ("100 г
 * сахара", "2 cups of flour", up to two words between) becomes a reference to the row in its place — to a part of it
 * when it is less than the whole — unless the row cannot read it: no quantity to share, a unit it does not convert to,
 * or more than it holds; then the quantity stays as typed. A row first named with no quantity gets a reference by its
 * name, as [placement] says, unless the same step gives it one with a quantity. A step opening with a name alone
 * ("Крахмал. Размешать…") is titled by it: that is no mention.
 */
internal class StepReferences(
    private val rows: List<Ingredient>,
    private val match: DictionaryMatch<IngredientEntryDto>,
    private val placement: FirstMention,
) {
    /** The rows a step before has named. */
    private val named = HashSet<Int>()
    private val parentOf: Map<Long, Long?>
    private val freeWords: Map<Int, List<String>>

    /** Every form of every word that names a row; no run of words holding any other names one. */
    private val known: Set<String>
    private val longest: Int
    private val bindings = HashMap<String, Long?>()

    init {
        val ids = rows.mapNotNullTo(HashSet()) { it.dictionaryEntry }
        parentOf = match.dictionary.filter { it.id in ids }.associate { it.id to it.parentId }
        val parents = parentOf.values.filterNotNullTo(HashSet())
        freeWords = rows.mapNotNull { row -> row.freeTextName?.let { row.position to WordPrefix.words(it) } }.toMap()
        val names = match.dictionary.filter { it.id in ids || it.id in parents }
            .flatMap { listOf(it.labelRu, it.labelEn) + it.synonymsRu + it.synonymsEn }.map(WordPrefix::words) + freeWords.values
        known = names.flatten().flatMapTo(HashSet(), Inflection::forms)
        longest = names.maxOfOrNull { it.size } ?: 0
    }

    /** [step], the next of the recipe, with its references; one that names no row as it was. */
    fun read(step: Step): Step {
        val text = step.text
        val mentions = mentions(text)
        if (mentions.isEmpty()) return step
        val quantities = QUANTITY.findAll(text).toList()
        val edits = mutableListOf<Edit>()
        val quantified = BooleanArray(mentions.size)
        val referenced = HashSet<Int>()
        var next = 0
        var used = -1
        var previousEnd = 0
        mentions.forEachIndexed { i, mention ->
            while (next < quantities.size && quantities[next].range.last < mention.start) next++
            val at = next - 1
            val found = quantities.getOrNull(at)?.takeIf {
                at > used && it.range.first >= previousEnd && it.range.last + 1 >= mention.start - MAX_GAP &&
                    GAP.matches(text.substring(it.range.last + 1, mention.start))
            }
            if (found != null) {
                used = at
                quantified[i] = true
                val amount = listOfNotNull(found.groups["q"], found.groups["u"], found.groups["w"])
                taken(rows[mention.row], found)?.let { share ->
                    edits += Edit(amount.minOf { it.range.first }, amount.maxOf { it.range.last } + 1, mention.row, share.share)
                    referenced += mention.row
                }
            }
            previousEnd = mention.end
        }
        mentions.forEachIndexed { i, mention ->
            if (!named.add(mention.row) || quantified[i] || mention.row in referenced || rows[mention.row].quantity == null) return@forEachIndexed
            val at = when (placement) {
                FirstMention.BEFORE -> mention.start
                FirstMention.AFTER -> mention.end
                FirstMention.NONE -> return@forEachIndexed
            }
            edits += Edit(at, at, mention.row, null)
        }
        if (edits.isEmpty()) return step
        val result = applied(step, edits.sortedBy { it.from })
        // A reference by a name adds spaces: where that is over the step's limit, those references are left out.
        return if (result.text.length <= RecipeBounds.MAX_STEP_TEXT) result else applied(step, edits.filter { it.to > it.from }.sortedBy { it.from })
    }

    /** A row named at [start] until [end] of a step's text. */
    private class Mention(val row: Int, val start: Int, val end: Int)

    /** The text from [from] until [to] — a quantity, or nothing — replaced by a reference to [row], to its [share] or whole. */
    private class Edit(val from: Int, val to: Int, val row: Int, val share: IngredientShare?)

    /** A reference's part of its row: null for the whole. */
    private class Taken(val share: IngredientShare?)

    private class Word(val start: Int, val end: Int, val folded: String)

    /** The rows [text] names, in its order: at each word, the longest run of words naming one. */
    private fun mentions(text: String): List<Mention> {
        val words = WORD.findAll(text).map { Word(it.range.first, it.range.last + 1, WordPrefix.fold(it.value)) }.toList()
        val isKnown = words.map { word -> Inflection.forms(word.folded).any { it in known } }
        val found = mutableListOf<Mention>()
        var i = 0
        while (i < words.size) {
            var size = 1
            if (isKnown[i]) {
                var end = i + 1
                while (end < words.size && end - i < longest && isKnown[end] && joined(text, words[end - 1], words[end])) end++
                for (to in end downTo i + 1) {
                    val row = rowOf(text.substring(words[i].start, words[to - 1].end), words.subList(i, to)) ?: continue
                    val title = i == 0 && text.getOrNull(words[to - 1].end) == '.'
                    if (!title) found += Mention(row, words[i].start, words[to - 1].end)
                    size = to - i
                    break
                }
            }
            i += size
        }
        return found
    }

    /** The row [run] names: one whose entry it is bound to, or the only one under it; else a free-text row of its words. */
    private fun rowOf(run: String, words: List<Word>): Int? {
        if (run !in bindings) bindings[run] = match.bind(run)?.takeIf { it.unused.isEmpty() }?.entry?.id
        bindings[run]?.let { entry ->
            rows.firstOrNull { it.dictionaryEntry == entry }?.let { return it.position }
            rows.singleOrNull { row -> row.dictionaryEntry?.let(parentOf::get) == entry }?.let { return it.position }
        }
        return freeWords.entries.firstOrNull { (_, free) ->
            free.size == words.size && free.indices.all { i -> Inflection.forms(words[i].folded).any { it in Inflection.forms(free[i]) } }
        }?.key
    }

    /**
     * The part of [row] the quantity [found] says, read as the row's line is read and moved onto its unit's grid; null
     * where the row cannot read it: it offers no part, the unit is not one it converts to, or it is more than the row.
     */
    private fun taken(row: Ingredient, found: MatchResult): Taken? {
        if (!IngredientShares.offered(row)) return null
        val value = found.groups["q"]?.let { IngredientLineReader.valueOf(it.value) ?: return null } ?: Rational.ONE
        val word = (found.groups["u"] ?: found.groups["w"])?.value?.let(UnitWords::of)
        val unit = (word as? UnitWord.Of)?.unit ?: QuantityUnit.PIECE
        val (amount, inUnit) = IngredientLineReader.snapped(value, unit, row.divisible)
        val share = IngredientShare(amount, inUnit)
        if (amount.signum() <= 0 || inUnit.type != row.unitType || !IngredientShares.fits(share, row.unit)) return null
        if (IngredientShares.exceeds(share, row)) return null
        val whole = inBase(checkNotNull(row.quantity), checkNotNull(row.unit))
        return Taken(share.takeIf { inBase(amount, inUnit).compareTo(whole) != 0 })
    }

    private companion object {
        val WORD = Regex("""[\p{L}\p{N}]++""")

        /** A quantity as an ingredient line writes it, a unit word or both, and what leads from it to a name. */
        val QUANTITY = Regex(
            """(?<![\p{L}\p{N}])(?:(?<q>${IngredientLineReader.QUANTITY})(?:\s*+(?<u>${UnitWords.PATTERN}))?|(?<w>${UnitWords.PATTERN}))\s*+(?:of\s++)?""",
            RegexOption.IGNORE_CASE,
        )

        /** At most two words between a quantity and the name it is of: "100 г мелкого сахара". */
        val GAP = Regex("""(?:[\p{L}-]++\s++){0,2}""")
        const val MAX_GAP = 80

        fun joined(text: String, a: Word, b: Word) = (a.end until b.start).all { text[it].isWhitespace() || text[it] == '-' }

        fun inBase(amount: Rational, unit: QuantityUnit): Rational = unit.baseMultiple?.let { amount * it } ?: amount

        /**
         * [step] with each of [edits], in the order of the text, made in one pass: the text replaced taken out, closed up
         * as [ReferenceSpacing.closeUp] does, and the reference put in its place, set apart from a word as
         * [ReferenceSpacing.insert] does.
         */
        fun applied(step: Step, edits: List<Edit>): Step {
            val text = step.text
            val out = StringBuilder(text.length + 2 * edits.size)
            val references = mutableListOf<IngredientReference>()
            var at = 0
            for (edit in edits) {
                out.append(text, at, edit.from)
                at = edit.to
                if (edit.to > edit.from) {
                    val left = out.lastOrNull()
                    val right = text.getOrNull(at)
                    when {
                        right == ' ' && (left == ' ' || left == null || left == '\n') -> at++
                        left == ' ' && (right == null || right == '\n' || right in ReferenceSpacing.CLOSING) -> out.setLength(out.length - 1)
                    }
                }
                if (ReferenceSpacing.spaceBefore(out.lastOrNull())) out.append(' ')
                references += IngredientReference(edit.row, out.length, edit.share)
                if (ReferenceSpacing.spaceAfter(text.getOrNull(at))) out.append(' ')
            }
            out.append(text, at, text.length)
            return step.copy(text = out.toString(), ingredientReferences = references)
        }
    }
}
