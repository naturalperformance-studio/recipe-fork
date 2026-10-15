package app.recipefork.core.paste

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityReading
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.effectiveDivisible
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.DictionaryMatch

/**
 * The second stage of reading a paste: one [IngredientLine] read as one ingredient row of the model, its name bound to
 * the ingredient dictionary through [match].
 *
 * A line reads as a quantity, a unit and a name, the quantity before the name or after it ("500 г творога",
 * "Творог — 500 г", "Соль щепотка", "Соль — по вкусу"). What follows the name and says what it is ("9%", "без кожи")
 * qualifies the entry it is bound to; how it is prepared, a clause after a comma and a parenthesis go to the row's note,
 * within its bound — a parenthesis is left out where it does not fit (the original text keeps it). A quantity off its
 * unit's grid is moved to the nearest value on it, and less than ¼ teaspoon is a pinch; a quantity that cannot be read
 * is kept as typed. A line with no quantity at all is to taste. Nothing of a line is lost: an unbound name is free text.
 */
class IngredientLineReader(private val match: DictionaryMatch<IngredientEntryDto>) {

    /** The row [line] reads as, at [position] of its recipe. */
    fun read(line: IngredientLine, position: Int): Ingredient {
        val text = line.text.replace(UNICODE_SPACE, " ")
        val parens = PAREN.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        val clauses = PAREN.replace(text, " ").split(CLAUSE).map(::squeeze).filter { it.isNotEmpty() }.toMutableList()
        var main = clauses.removeFirstOrNull() ?: ""

        val amount = Amount.lead(main) ?: Amount.trail(main)
        if (amount != null) {
            main = amount.name
        } else if (UnitWords.TO_TASTE.containsMatchIn(main)) {
            main = squeeze(UnitWords.TO_TASTE.replace(main, " "))
        } else {
            clauses.remove(clauses.firstOrNull { UnitWords.TO_TASTE.replace(it, "").isBlank() })
        }

        // A clove is the entry naming it where that holds the quantity; otherwise pieces of what follows, qualified by it.
        var named = named(main)
        if (amount?.word == UnitWord.Clove) {
            val ofClove = named("${amount.wordText} $main")
            val clove = ofClove.entry?.takeIf { entry ->
                ofClove.usesFirst && amount.quantity?.let { QuantityInput.holds(it, QuantityUnit.PIECE, entry.divisible ?: true) } == true
            }
            named = when {
                clove != null || named.entry == null -> ofClove
                else -> named.copy(qualifier = listOfNotNull(cloveWord(amount.wordText), named.qualifier).joinToString(" "))
            }
        }

        val entry = named.entry
        val base = Ingredient(
            position = position,
            dictionaryEntry = entry?.id,
            qualifier = named.qualifier,
            freeTextName = if (entry == null) named.freeText.ifEmpty { squeeze(text) }.ifEmpty { null } else null,
            unitType = UnitType.TASTE_UNMEASURED,
            unit = null,
            quantity = null,
            note = noteOf(listOf(named.preparation) + clauses, parens),
        )
        if (amount == null) return base

        val unit = (amount.word as? UnitWord.Of)?.unit ?: QuantityUnit.PIECE
        val divisible = if (unit == QuantityUnit.PIECE) {
            effectiveDivisible(base.copy(unitType = UnitType.PIECE), entry?.divisible)
        } else {
            null
        }
        val quantity = amount.quantity
            ?: return base.copy(unitType = unit.type, unit = unit, divisible = divisible, enteredQuantity = amount.entered)
        val (onGrid, inUnit) = snapped(quantity, unit, divisible)
        return base.copy(
            unitType = inUnit.type,
            unit = inUnit,
            quantity = onGrid,
            divisible = divisible.takeIf { inUnit == QuantityUnit.PIECE },
        )
    }

    /**
     * What a name reads as: the [entry] it is bound to, or none; the words, as written, that say what it is beyond the
     * entry ([qualifier]), or the [freeText] that names it when no entry does; how it is prepared ([preparation]); and
     * whether the entry uses its first word.
     */
    private data class Named(
        val entry: IngredientEntryDto?,
        val qualifier: String?,
        val freeText: String,
        val preparation: String,
        val usesFirst: Boolean,
    )

    /**
     * [name] bound as a whole, so that an entry naming a preparation ("рубленые орехи") keeps it; of the words the entry
     * does not use, preparation goes to the note and the rest qualifies the entry.
     */
    private fun named(name: String): Named {
        val tokens = name.split(' ').filter { it.isNotEmpty() }
        val prepared = PREPARATION.findAll(name).map { it.range }.toList()
        var at = 0
        val preparation = tokens.map { token ->
            val start = name.indexOf(token, at).also { at = it + token.length }
            prepared.any { start <= it.last && it.first < start + token.length }
        }
        val bound = match.bind(name)
        val left = bound?.unused?.map { DictionaryMatch.fold(it) }?.toMutableList()
        val unused = tokens.map { token -> left == null || wordsOf(token).count { left.remove(it) } > 0 }
        fun words(keep: (Int) -> Boolean) = tokens.filterIndexed { i, _ -> keep(i) }.joinToString(" ").trim(*EDGES)
        return Named(
            entry = bound?.entry,
            qualifier = if (bound == null) null else words { unused[it] && !preparation[it] }.ifEmpty { null },
            freeText = words { !preparation[it] },
            preparation = words { unused[it] && preparation[it] },
            usesFirst = unused.firstOrNull() == false,
        )
    }

    /**
     * What a line's quantity says: its [quantity] — the larger of a range — or, when it cannot be read, what was
     * [entered]; the unit word, as written ([wordText]); and the [name] around them.
     */
    private class Amount(
        val quantity: Rational?,
        val entered: String?,
        val word: UnitWord?,
        val wordText: String,
        val name: String,
    ) {
        companion object {
            /** The quantity, a unit word or both before the name, or null when the line starts with neither. */
            fun lead(text: String): Amount? {
                val found = LEAD.matchEntire(text)
                val quantityText = found?.groups?.get("q")?.value
                val unitText = found?.groups?.get("u")?.value
                if (found != null && (quantityText != null || unitText != null)) {
                    val end = found.groups["q"]?.range?.last?.plus(1)
                    val continues = end != null && text.getOrNull(end)?.let { it in RAW_CHARS } == true
                    val value = quantityText?.let(::valueOf)
                    if (quantityText == null || value != null && !continues) {
                        return of(value ?: Rational.ONE, null, unitText, found.groups["rest"]!!.value)
                    }
                }
                val raw = RAW_LEAD.matchEntire(text) ?: return null
                return of(null, raw.groups["raw"]!!.value, raw.groups["u"]?.value, raw.groups["rest"]!!.value)
            }

            /** The quantity, a unit word or both after the name ("Творог — 500 г", "Соль щепотка"), or null. */
            fun trail(text: String): Amount? {
                TRAIL.matchEntire(text)?.let { found ->
                    val quantityText = found.groups["q"]!!.value
                    val value = valueOf(quantityText)
                    return of(value, quantityText.takeIf { value == null }, found.groups["u"]?.value, found.groups["name"]!!.value)
                }
                val found = TRAIL_UNIT.matchEntire(text) ?: return null
                return of(Rational.ONE, null, found.groups["u"]!!.value, found.groups["name"]!!.value)
            }

            private fun of(value: Rational?, entered: String?, unitText: String?, name: String) =
                Amount(value, entered, unitText?.let(UnitWords::of), unitText?.trim().orEmpty(), squeeze(name).trim(*EDGES))
        }
    }

    private companion object {
        val GLYPHS = mapOf(
            '¼' to Rational.of(1, 4), '½' to Rational.of(1, 2), '¾' to Rational.of(3, 4), '⅓' to Rational.of(1, 3),
            '⅔' to Rational.of(2, 3), '⅕' to Rational.of(1, 5), '⅖' to Rational.of(2, 5), '⅗' to Rational.of(3, 5),
            '⅘' to Rational.of(4, 5), '⅙' to Rational.of(1, 6), '⅚' to Rational.of(5, 6), '⅛' to Rational.of(1, 8),
            '⅜' to Rational.of(3, 8), '⅝' to Rational.of(5, 8), '⅞' to Rational.of(7, 8),
        )
        val GLYPH_CLASS = GLYPHS.keys.joinToString("")

        /** Words for small numbers, by their value; "пол" glued to a unit or joined by a hyphen is read apart. */
        val NUMBER_WORDS: Map<String, Rational> = buildMap {
            listOf("полтора", "полторы").forEach { put(it, Rational.of(3, 2)) }
            listOf("половина", "половину", "половинка", "половинку", "пол", "half", "half a", "half an")
                .forEach { put(it, Rational.of(1, 2)) }
            listOf("один", "одна", "одну", "одно", "одного", "одной", "one", "a", "an").forEach { put(it, Rational.ONE) }
            listOf("два", "две", "two").forEach { put(it, Rational.of(2)) }
            listOf("три", "three").forEach { put(it, Rational.of(3)) }
            listOf("четыре", "four").forEach { put(it, Rational.of(4)) }
            listOf("пять", "five").forEach { put(it, Rational.of(5)) }
        }

        val UNIT = UnitWords.PATTERN
        val ATOM = """\d+\s+\d+/\d+|\d*\s?[$GLYPH_CLASS]|\d+/\d+|\d+(?:[.,]\d+)?"""
        val WORD = NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|") { it.replace(" ", """\s+""") } .let {
            """пол-|пол(?=$UNIT)|(?:$it)(?![\p{L}\p{N}])"""
        }
        val NUMBER = """(?:$ATOM|$WORD)"""
        val QUANTITY = """(?:от\s+)?$NUMBER(?:\s*(?:[-–—]|(?:до|to)(?![\p{L}]))\s*$NUMBER)?"""
        val RANGE = Regex("""^(?:от\s+)?($NUMBER)(?:\s*(?:[-–—]|до|to)\s*($NUMBER))?$""", RegexOption.IGNORE_CASE)
        const val RAW_CHARS = "0123456789./,-–—"

        val LEAD = Regex("""^(?<q>$QUANTITY)?\s*(?<u>$UNIT)?\s*(?:of\s+)?(?<rest>.*)$""", RegexOption.IGNORE_CASE)
        val RAW_LEAD = Regex("""^(?<raw>[\d$GLYPH_CLASS][\d$GLYPH_CLASS./,\-–—]*)\s*(?<u>$UNIT)?\s*(?:of\s+)?(?<rest>.*)$""", RegexOption.IGNORE_CASE)
        // The name ends at a letter or a digit and the separator after it is taken whole: no backtracking on a run of dashes.
        val TRAIL = Regex("""^(?<name>.*?[^\s—–:-])[\s—–:-]++(?<q>$QUANTITY)\s*(?<u>$UNIT)?[\s.]*$""", RegexOption.IGNORE_CASE)
        val TRAIL_UNIT = Regex("""^(?<name>.*?[^\s—–:-])[\s—–:-]++(?<u>$UNIT)[\s.]*$""", RegexOption.IGNORE_CASE)

        val PAREN = Regex("""\(([^()]*)\)""")

        /** A comma between two digits is a decimal mark; any other, or a semicolon, ends a clause. */
        val CLAUSE = Regex(""";|,(?!\d)|(?<!\d),""")
        val EDGES = charArrayOf(' ', ',', ';', ':', '—', '–', '-', '.')

        /** How an ingredient is prepared, or what may stand for it ("или по вкусу"), never what it is: the row's note. */
        val PREPARATION = Regex(
            listOf(
                "мелко", "крупно", "тонко", "грубо", """кубик\p{L}*""", """соломк\p{L}*""", """(?:полу)?кольц\p{L}*""",
                """ломтик\p{L}*""", """\p{L}*рублен\p{L}*""", """\p{L}*резан\p{L}*""", "нарезать", "натереть",
                """\p{L}*т[её]рт\p{L}*""", """измельч\p{L}*""", """очищен\p{L}*""", "очистить", """растоплен\p{L}*""",
                "растопить", """размягч\p{L}*""", """взбит\p{L}*""", """просеян\p{L}*""", "просеять", """охлажд\p{L}*""",
                """нашинков\p{L}*""", """\p{L}*давлен\p{L}*""", """промыт\p{L}*""", """разморож\p{L}*""",
                """комнатной\s+температуры""", """по\s+желанию""", """для\s.*""", """(?:или|и|or|and)\s.*""",
                "finely", "coarsely", "thinly", "roughly", "freshly", "chopped", "diced", "minced", "sliced", "grated",
                "shredded", "sifted", "melted", "softened", "beaten", "peeled", "crushed", "cubed", "halved", "quartered",
                "divided", """(?:at\s+)?room\s+temperature""", "optional", """for\s.*""",
            ).joinToString("|", """(?<![\p{L}\p{N}])(?:""", """)(?![\p{L}\p{N}])"""),
            RegexOption.IGNORE_CASE,
        )
        val SPACES = Regex("""\s+""")
        val UNICODE_SPACE = Regex("""[\p{Z}\t]""")
        val WORD_SEPARATORS = Regex("""[^\p{L}\p{N}]+""")

        fun squeeze(text: String) = text.replace(SPACES, " ").trim()

        fun wordsOf(text: String) = text.split(WORD_SEPARATORS).filter { it.isNotEmpty() }.map { DictionaryMatch.fold(it) }

        /** The value of a quantity as written, the larger end of a range; null when it reads as no number above zero. */
        fun valueOf(text: String): Rational? {
            val found = RANGE.matchEntire(text.trim()) ?: return null
            val values = listOfNotNull(found.groups[1], found.groups[2]).map { numberOf(it.value) ?: return null }
            return values.max().takeIf { it.signum() > 0 }
        }

        private fun numberOf(text: String): Rational? {
            val written = squeeze(text).lowercase()
            NUMBER_WORDS[written.removeSuffix("-")]?.let { return it }
            if (written.startsWith("пол")) return NUMBER_WORDS["пол"]
            GLYPHS[written.last()]?.let { glyph ->
                val whole = written.dropLast(1).trim()
                return glyph + if (whole.isEmpty()) Rational.ZERO else Rational.of(whole.toBigInteger())
            }
            val parts = written.split(' ')
            if (parts.size == 2) return numberOf(parts[0])?.let { whole -> numberOf(parts[1])?.let { whole + it } }
            return (QuantityInput.read(written) as? QuantityReading.Value)?.value
        }

        /** [value] in [unit] moved onto its unit's grid, the nearest value (DOM-017); below ¼ teaspoon, one pinch. */
        fun snapped(value: Rational, unit: QuantityUnit, divisible: Boolean?): Pair<Rational, QuantityUnit> {
            if (unit == QuantityUnit.TEASPOON && value < Rational.of(1, 4)) return Rational.ONE to QuantityUnit.PINCH
            val grid = QuantityInput.grid(unit, divisible) ?: return value to unit
            if (QuantityInput.holds(value, unit, divisible)) return value to unit
            val multiple = unit.lineMultiple ?: Rational.ONE
            return grid.nearest(value * multiple) / multiple to unit
        }

        /** The base form of the clove word [written], which qualifies the entry the clove is of. */
        fun cloveWord(written: String) = if (written.any { it in 'а'..'я' || it in 'А'..'Я' }) "зубчик" else "clove"

        /**
         * The row's note: the [parts] — preparation and comma clauses — joined, cut to the bound at a word; then each
         * parenthesis that still fits, and none that does not.
         */
        fun noteOf(parts: List<String>, parens: List<String>): String? {
            var note = parts.filter { it.isNotBlank() }.joinToString(", ")
            if (note.length > RecipeBounds.MAX_NOTE) {
                val cut = note.take(RecipeBounds.MAX_NOTE - 1)
                note = cut.substringBeforeLast(' ', cut).trimEnd(*EDGES) + "…"
            }
            parens.forEach { paren ->
                val with = if (note.isEmpty()) paren else "$note ($paren)"
                if (with.length <= RecipeBounds.MAX_NOTE) note = with
            }
            return Ingredient.noteOf(note)
        }
    }
}
