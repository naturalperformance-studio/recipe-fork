package app.recipefork.core.paste

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
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
 * Greek yogurt row); a free-text row by its words. Where a name fits several rows, it means the one the quantity
 * written is the whole of, else the one of the group the step names, else the one of the group of the step's other
 * rows; failing those, none.
 *
 * A quantity right after a name ("сметана 60 г") is that name's; failing one, a quantity before the name ("100 г
 * сахара", "2 cups of flour", up to two words between, one after a bare number, which is never one of minutes or
 * degrees). It becomes a reference to the row in its place — to a part of it when it is less than the whole — unless
 * the row cannot read it: no quantity to share, a unit it does not convert to, or more than it holds; then the quantity
 * stays as typed, as one the reader cannot be sure of does ("~9 г", "(100 г) сахара"). A row first named with no
 * quantity gets a reference by its name, as [placement] says, when it holds a quantity that scales and the same step
 * gives it no reference with a quantity. A step opening with a name alone ("Крахмал. Размешать…") is titled by it:
 * that is no mention.
 */
internal class StepReferences(
    private val rows: List<Ingredient>,
    groups: List<IngredientGroup>,
    private val match: DictionaryMatch<IngredientEntryDto>,
    private val placement: FirstMention,
) {
    /** The rows a step before has named. */
    private val named = HashSet<Int>()
    private val parentOf: Map<Long, Long?>
    private val freeWords: Map<Int, List<String>>

    /** By row, the index of its group in the recipe; -1 for a row before the first. */
    private val groupOf: IntArray = IntArray(rows.size) { row -> groups.indexOfLast { it.firstPosition <= row } }

    /** By group, the words of its name, a parenthesis left out. */
    private val groupWords: List<List<String>> = groups.map { WordPrefix.words(it.name.replace(PAREN, " ")) }

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
        val amounts = amounts(text, mentions)
        val resolved = IntArray(mentions.size) { mentions[it].rows.singleOrNull() ?: -1 }
        val others = resolved.filter { it >= 0 }.mapTo(HashSet()) { groupOf[it] }
        val forms by lazy { WORD.findAll(text).flatMapTo(HashSet()) { Inflection.forms(WordPrefix.fold(it.value)) } }
        val namesGroup = { group: Int -> groupWords[group].all { word -> Inflection.forms(word).any { it in forms } } }
        mentions.indices.filter { resolved[it] < 0 }.forEach { i -> resolved[i] = chosen(mentions[i].rows, amounts[i], others, namesGroup) ?: -1 }

        val edits = mutableListOf<Edit>()
        val referenced = HashSet<Int>()
        mentions.indices.forEach { i ->
            val row = resolved[i].takeIf { it >= 0 } ?: return@forEach
            val amount = amounts[i]?.takeIf { it.readable } ?: return@forEach
            taken(rows[row], amount.found)?.let { share ->
                val written = listOfNotNull(amount.found.groups["q"], amount.found.groups["u"], amount.found.groups["w"])
                val end = written.maxOf { it.range.last } + 1
                // The dot of a unit word that ends the sentence too ("мука 50 г.") is the sentence's.
                edits += Edit(written.minOf { it.range.first }, if (text[end - 1] == '.' && endsSentence(text, end)) end - 1 else end, row, share.share)
                referenced += row
            }
        }
        mentions.forEachIndexed { i, mention ->
            val row = resolved[i].takeIf { it >= 0 } ?: return@forEachIndexed
            if (!named.add(row) || amounts[i] != null || row in referenced || rows[row].authoredQuantity?.quantity == null) return@forEachIndexed
            val at = when (placement) {
                FirstMention.BEFORE -> mention.start
                FirstMention.AFTER -> mention.end
                FirstMention.NONE -> return@forEachIndexed
            }
            edits += Edit(at, at, row, null)
        }
        if (edits.isEmpty()) return step
        val result = applied(step, edits.sortedBy { it.from })
        // A reference by a name adds spaces: where that is over the step's limit, those references are left out.
        return if (result.text.length <= RecipeBounds.MAX_STEP_TEXT) result else applied(step, edits.filter { it.to > it.from }.sortedBy { it.from })
    }

    /** Words naming [rows] — one, or several to choose from — at [start] until [end] of a step's text. */
    private class Mention(val rows: List<Int>, val start: Int, val end: Int)

    /** The quantity [found] a mention is given; [readable] when it may become a reference, else it stays as typed. */
    private class Amount(val found: MatchResult, val readable: Boolean)

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
                    val named = rowsOf(text.substring(words[i].start, words[to - 1].end), words.subList(i, to))
                    if (named.isEmpty()) continue
                    val title = i == 0 && text.getOrNull(words[to - 1].end) == '.'
                    if (!title) found += Mention(named, words[i].start, words[to - 1].end)
                    size = to - i
                    break
                }
            }
            i += size
        }
        return found
    }

    /**
     * The quantity each of [mentions] is given, in their order: one before its name that the name before took not,
     * failing it the one right after its name; each quantity given once.
     */
    private fun amounts(text: String, mentions: List<Mention>): Array<Amount?> {
        val quantities = QUANTITY.findAll(text).filterNot { measuresOther(text, it) }.toList()
        val amounts = arrayOfNulls<Amount>(mentions.size)
        var next = 0
        var used = -1
        mentions.forEachIndexed { i, mention ->
            while (next < quantities.size && quantities[next].range.last < mention.start) next++
            val previousEnd = mentions.getOrNull(i - 1)?.end ?: 0
            val before = quantities.getOrNull(next - 1)
                ?.takeIf { next - 1 > used && it.range.first >= previousEnd }?.let { before(text, it, mention) }
            val after = quantities.getOrNull(next)?.takeIf { next > used && it.range.first - mention.end in 0..MAX_GAP }
                ?.takeIf { TRAIL_GAP.matches(text.substring(mention.end, it.range.first)) }
                // Only a number with its unit trails a name; a bare number or a unit word does so only where the clause
                // ends with it ("Соль щепотка."), else it counts what follows: "яйца два половника", "eggs one at a time".
                ?.takeIf { it.groups["u"] != null || clauseEndsAt(text, it.range.last + 1) }
            if (before != null) {
                amounts[i] = before
                used = next - 1
            } else if (after != null) {
                amounts[i] = Amount(after, readable = true)
                used = next
            }
        }
        return amounts
    }

    /** Nothing but spaces before the end of the text or a mark that ends a clause. */
    private fun clauseEndsAt(text: String, from: Int): Boolean {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i == text.length || text[i] in CLAUSE_ENDS
    }

    /** [found] as the quantity of the [mention] after it; null when it is not: too far, or more than words between. */
    private fun before(text: String, found: MatchResult, mention: Mention): Amount? {
        val end = found.range.last + 1
        if (end < mention.start - MAX_GAP) return null
        val between = text.substring(end, mention.start)
        val bare = found.groups["u"] == null && found.groups["w"] == null
        if (if (bare) BARE_GAP.matches(between) else GAP.matches(between)) {
            return Amount(found, readable = text.getOrNull(found.range.first - 1)?.let { it in APPROXIMATE } != true)
        }
        return if (CLOSED.matches(between)) Amount(found, readable = false) else null
    }

    /** The rows [run] names: those whose entry it is bound to, or else those under it; else the free-text rows of its words. */
    private fun rowsOf(run: String, words: List<Word>): List<Int> {
        if (run !in bindings) bindings[run] = match.bind(run)?.takeIf { it.unused.isEmpty() }?.entry?.id
        bindings[run]?.let { entry ->
            val bound = rows.filter { it.dictionaryEntry == entry }.ifEmpty { rows.filter { row -> row.dictionaryEntry?.let(parentOf::get) == entry } }
            if (bound.isNotEmpty()) return bound.map { it.position }
        }
        return freeWords.filter { (_, free) ->
            free.size == words.size && free.indices.all { i -> Inflection.forms(words[i].folded).any { it in Inflection.forms(free[i]) } }
        }.keys.toList()
    }

    /**
     * Of [candidates], the row a name means: the one [amount] is the whole of, else the one that reads it, else the one in a group the step
     * [names], else the one in a group of the step's [others]; null when none of these tells one row.
     */
    private fun chosen(candidates: List<Int>, amount: Amount?, others: Set<Int>, names: (Int) -> Boolean): Int? {
        val narrowing = listOf<(Int) -> Boolean>(
            { row -> amount != null && amount.readable && taken(rows[row], amount.found)?.let { it.share == null } == true },
            { row -> amount != null && amount.readable && taken(rows[row], amount.found) != null },
            { row -> groupOf[row] >= 0 && names(groupOf[row]) },
            // A row outside every group is no worse a match than one in the others' group: "Поставить кастрюлю с водой и
            // солью" with the salt only in the sauce says nothing of which water.
            // unless the others are themselves outside every group: then that is the group they say.
            { row -> (-1 in others || candidates.none { groupOf[it] < 0 }) && groupOf[row] in others },
        )
        var left = candidates
        for (keep in narrowing) {
            left = left.filter(keep).ifEmpty { left }
            if (left.size == 1) return left.single()
        }
        return null
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
        val PAREN = Regex("""\([^()]*\)""")
        const val CLAUSE_ENDS = ".,;:!?)"

        /** A quantity as an ingredient line writes it, a unit word or both, and what leads from it to a name. */
        val QUANTITY = Regex(
            """(?<![\p{L}\p{N}])(?:(?<q>${IngredientLineReader.QUANTITY})(?:\s*+(?<u>${UnitWords.PATTERN}))?|(?<w>${UnitWords.PATTERN}))\s*+(?:of\s++)?""",
            RegexOption.IGNORE_CASE,
        )

        /** What makes a bare number one of something else than an ingredient: time, heat, length, times over. */
        val MEASURE: java.util.regex.Pattern = Regex(
            """\s*+(?:°|(?:минут\p{L}*|мин|час\p{L}*|ч|секунд\p{L}*|сек|градус\p{L}*|см|мм|раза?|minutes?|mins?|hours?|hrs?|h|seconds?|secs?|degrees?|cm|mm|inch(?:es)?|times?)(?![\p{L}\p{N}]))""",
            RegexOption.IGNORE_CASE,
        ).toPattern()

        /** At most two words between a quantity and the name it is of: "100 г мелкого сахара"; one after a bare number. */
        val GAP = Regex("""(?:[\p{L}-]++\s++){0,2}""")
        val BARE_GAP = Regex("""(?:[\p{L}-]++\s++)?""")

        /** A name and the quantity it is given right after it: spaces, a dash or a colon between. */
        val TRAIL_GAP = Regex("""[\s—–:-]*+""")

        /** A quantity closed in a bracket before a name, "(100 г) сахара": it stays as typed. */
        val CLOSED = Regex("""[\s)\]»"]*+""")

        /** A quantity written as about so much, "~9 г": it stays as typed. */
        const val APPROXIMATE = "~≈"
        const val MAX_GAP = 80

        /** Whether [found] is a bare number of minutes, degrees or the like. */
        fun measuresOther(text: String, found: MatchResult): Boolean {
            val number = found.groups["q"] ?: return false
            if (found.groups["u"] != null) return false
            return MEASURE.matcher(text).region(number.range.last + 1, text.length).lookingAt()
        }

        /** Whether a sentence ends at [at] of [text]: nothing but spaces follow, or a capital letter after them. */
        fun endsSentence(text: String, at: Int): Boolean {
            var i = at
            while (i < text.length && text[i].isWhitespace()) i++
            return i == text.length || i > at && text[i].isUpperCase()
        }

        fun joined(text: String, a: Word, b: Word) = (a.end until b.start).all { text[it].isWhitespace() || text[it] == '-' }

        fun inBase(amount: Rational, unit: QuantityUnit): Rational = unit.baseMultiple?.let { amount * it } ?: amount

        /**
         * [step] with each of [edits], in the order of the text, made in one pass: the text replaced taken out and the
         * reference put in its place, set apart from a word as [ReferenceSpacing.insert] sets it; the spaces that stood
         * around what was taken out stay, and none is added beside them.
         */
        fun applied(step: Step, edits: List<Edit>): Step {
            val text = step.text
            val out = StringBuilder(text.length + 2 * edits.size)
            val references = mutableListOf<IngredientReference>()
            var at = 0
            for (edit in edits) {
                out.append(text, at, edit.from)
                at = edit.to
                if (ReferenceSpacing.spaceBefore(out.lastOrNull())) out.append(' ')
                references += IngredientReference(edit.row, out.length, edit.share)
                if (ReferenceSpacing.spaceAfter(text.getOrNull(at))) out.append(' ')
            }
            out.append(text, at, text.length)
            return step.copy(text = out.toString(), ingredientReferences = references)
        }
    }
}
