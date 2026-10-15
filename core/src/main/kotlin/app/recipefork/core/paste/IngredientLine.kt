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
 * "Творог — 500 г", "Соль щепотка", "Соль — по вкусу"), after a label ("подсластитель: щепотка стевии"), between a word
 * and the name ("juice of half a lime", "цедра одного лимона"), or, where the name holds none, in a clause after a comma
 * ("гауда, тёртая — 40 г") or in a parenthesis ("лапша (~250 г варёной)"). A word for a whole thing ("1 головка
 * чеснока", "small bunch parsley") counts pieces and qualifies the entry. What follows the name and says what it is
 * ("9%", "без кожи") qualifies the entry it is bound to; how it is prepared, what a dash or a colon adds to a bound
 * name ("мука — обвалять"), what follows a plus, a clause after a comma and a parenthesis go to the row's note, within
 * its bound — a parenthesis is left out where it does not fit (the original text keeps it). A quantity off its unit's
 * grid is moved to the nearest value on it, and less than ¼ teaspoon is a pinch; a quantity that cannot be read is kept
 * as typed. A line with no quantity at all is to taste. Nothing of a line is lost: an unbound name is free text.
 */
class IngredientLineReader(private val match: DictionaryMatch<IngredientEntryDto>) {

    /**
     * The lines [line] reads as, one row each: a label before a colon that names no ingredient, over an amount of one
     * that does ("Крем: 200 г мацони"), is their group; amounts joined by a plus ("20 г укропа + 15 г петрушки") are a
     * line each while each binds — the first that does not, and what follows it, stay in the note of the one before
     * ("1 яйцо + 1 желток" where no entry is a yolk). A part holding an alternative ("или", "or") is the last split off.
     */
    fun lines(line: IngredientLine): List<IngredientLine> {
        val labelled = labelled(line) ?: line
        val parts = plusParts(labelled.text)
        if (parts.size < 2) return listOf(labelled)
        val taken = mutableListOf<String>()
        for (part in parts) {
            if (read(IngredientLine(part, null), 0).dictionaryEntry == null) break
            taken += part
            if (ALTERNATIVE.containsMatchIn(part)) break
        }
        if (taken.size < 2) return listOf(labelled)
        taken[taken.lastIndex] = (listOf(taken.last()) + parts.drop(taken.size)).joinToString(" + ")
        return taken.map { IngredientLine(it, labelled.group) }
    }

    /**
     * [line] as the amount after its label, the label its group — an entry's name or not ("Тесто: 300 г муки"); null
     * when no amount of a bound name follows the colon ("Яйца: 2", "Соль: по вкусу").
     */
    private fun labelled(line: IngredientLine): IngredientLine? {
        val found = LABEL.matchEntire(squeeze(line.text.replace(UNICODE_SPACE, " "))) ?: return null
        val label = found.groups["label"]!!.value
        val rest = found.groups["rest"]!!.value
        val row = read(IngredientLine(rest, null), 0)
        return if (row.dictionaryEntry != null && row.unitType != UnitType.TASTE_UNMEASURED) IngredientLine(rest, label) else null
    }

    /** The row [line] reads as, at [position] of its recipe. */
    fun read(line: IngredientLine, position: Int): Ingredient {
        val text = line.text.replace(UNICODE_SPACE, " ")
        val parens = PAREN.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toMutableList()
        var body = PAREN.replace(text, " ")
        // "40 г муки + немного на подпыл": what follows a plus goes to the note, unless an alternative before holds it.
        // "1 cup + 2 tbsp flour": an amount with no name before the plus is of the name after it, the first its quantity.
        var plus: String? = null
        plusParts(body).takeIf { it.size > 1 && !ALTERNATIVE.containsMatchIn(it[0]) }?.let { parts ->
            val last = parts.last()
            val name = Amount.lead(last)?.name?.takeIf { it.isNotEmpty() && Amount.lead(parts[0])?.name?.isEmpty() == true }
            if (name != null && last.endsWith(name)) {
                body = "${parts[0]} $name"
                plus = "+ " + (parts.subList(1, parts.lastIndex) + last.dropLast(name.length).trim()).joinToString(" + ")
            } else {
                body = parts[0]
                plus = "+ " + parts.drop(1).joinToString(" + ")
            }
        }
        val clauses = body.split(CLAUSE).map(::squeeze).filter { it.isNotEmpty() }.toMutableList()
        var main = clauses.removeFirstOrNull() ?: ""

        var amount = Amount.lead(main) ?: Amount.trail(main) ?: Amount.dashed(main) ?: Amount.labelled(main) ?: between(main)
        if (amount == null) {
            // A name with no quantity: one in a clause after it ("гауда, тёртая — 40 г"), else in a parenthesis.
            amount = clauses.firstNotNullOfOrNull { clause -> Amount.ofClause(clause)?.let { clause to it } }?.let { (clause, found) ->
                clauses.remove(clause)
                found.named(main)
            } ?: parens.firstNotNullOfOrNull { paren -> Amount.ofParenthesis(paren)?.let { paren to it } }?.let { (paren, found) ->
                parens.remove(paren)
                found.named(main)
            }
        }
        if (amount != null) {
            main = amount.name
        } else if (UnitWords.TO_TASTE.containsMatchIn(main)) {
            main = squeeze(UnitWords.TO_TASTE.replace(main, " "))
        } else {
            clauses.remove(clauses.firstOrNull { UnitWords.TO_TASTE.replace(it, "").isBlank() })
        }
        // "мука — обвалять", "зелень: укроп или зелёный лук": what a dash or a colon adds to a name is a note.
        val (name, added) = apart(main, measured = amount != null)
        main = name

        // A whole thing is the entry naming it where that holds the quantity; otherwise pieces of what follows, qualified by it.
        var named = named(main)
        if (amount?.counted == true) named = headNoun(main, named)
        val count = amount?.word as? UnitWord.Count
        if (count != null && amount != null) {
            val ofCount = named("${amount.wordText} $main")
            val whole = ofCount.entry?.takeIf { entry ->
                ofCount.usesFirst && amount.quantity?.let { QuantityInput.holds(it, QuantityUnit.PIECE, entry.divisible ?: true) } == true
            }
            named = when {
                whole != null || named.entry == null -> ofCount
                // The name's own words first, the whole thing after them: "butter, can", not "can butter".
                else -> named.copy(qualifier = listOfNotNull(named.qualifier, amount.countQualifier).joinToString(", "))
            }
        }
        val unitQualifier = listOfNotNull((amount?.word as? UnitWord.Of)?.qualifier, amount?.countAfter?.base)
        // A qualifier qualifies an entry: with none, the words stay in the free text ("2 мультистакана чего-то").
        if (unitQualifier.isNotEmpty()) {
            named = if (named.entry != null) {
                named.copy(qualifier = (listOfNotNull(named.qualifier) + unitQualifier).joinToString(", "))
            } else {
                named.copy(freeText = (unitQualifier + named.freeText).filter { it.isNotEmpty() }.joinToString(" "))
            }
        }

        val entry = named.entry
        val notes = listOfNotNull(amount?.before) + listOf(named.preparation) + listOfNotNull(added, amount?.after) + clauses + listOfNotNull(plus)
        val base = Ingredient(
            position = position,
            dictionaryEntry = entry?.id,
            qualifier = named.qualifier,
            freeTextName = if (entry == null) named.freeText.ifEmpty { squeeze(text) }.ifEmpty { null } else null,
            unitType = UnitType.TASTE_UNMEASURED,
            unit = null,
            quantity = null,
            note = noteOf(notes, parens),
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
        // "quarter of a red onion": less than the least piece its grid holds is that piece, the quantity kept in the note.
        val below = quantity.takeIf { inUnit == QuantityUnit.PIECE && it < onGrid && onGrid <= Rational.ONE }
        return base.copy(
            unitType = inUnit.type,
            unit = inUnit,
            quantity = onGrid,
            divisible = divisible.takeIf { inUnit == QuantityUnit.PIECE },
            note = if (below == null) base.note else noteOf(listOf("$below") + notes, parens),
        )
    }

    /** "juice of half a lime", "цедра одного лимона": a word, then an amount of the entry it is of; the word is a note. */
    private fun between(text: String): Amount? {
        val found = BETWEEN.matchEntire(text) ?: return null
        val value = valueOf(found.groups["q"]!!.value) ?: return null
        val rest = found.groups["rest"]!!.value
        if (named(rest).entry == null) return null
        return Amount.of(value, null, found.groups["u"]?.value, rest).copy(before = found.groups["pre"]!!.value)
    }

    /**
     * [name] without what a dash or a colon adds to it, and that addition. The name of a [measured] row is what stands
     * before the first spaced dash or colon ("густого соуса: «…»", "масла — смазать верх"). One to taste keeps the part
     * before a spaced dash where it binds ("мука — обвалять") and, of the two sides of a colon, the one before where it
     * binds ("зелень: укроп"), else the one after ("опционально: зелёный лук"); a name neither side of which binds is
     * kept whole.
     */
    private fun apart(name: String, measured: Boolean): Pair<String, String?> {
        fun binds(text: String) = named(text).entry != null
        DASH.find(name)?.let { dash ->
            val left = name.substring(0, dash.range.first).trim(*EDGES)
            val right = name.substring(dash.range.last + 1).trim(*EDGES)
            if (left.any(Char::isLetter) && right.isNotEmpty() && (measured || binds(left))) return left to right
        }
        COLON.find(name)?.let { colon ->
            val left = name.substring(0, colon.range.first).trim(*EDGES)
            val right = name.substring(colon.range.last + 1).trim(*EDGES)
            if (!left.any(Char::isLetter) || right.isEmpty()) return@let
            if (measured || binds(left)) return left to right
            if (binds(right)) return right to left
        }
        return name to null
    }

    /**
     * After a word for a whole thing, in English, the name ends in what it is ("a can of butter beans" is beans): where
     * the entry bound leaves the name's last word unused, the longest run of its last words — [HEAD_WORDS] at most —
     * that is, as a whole, an entry's name, qualified by the words before it.
     */
    private fun headNoun(name: String, named: Named): Named {
        if (named.entry == null || named.preparation.isNotEmpty() || name.any { it in 'а'..'я' || it in 'А'..'Я' }) return named
        val tokens = name.split(' ').filter { it.isNotEmpty() }
        val last = tokens.lastOrNull()?.let(::wordsOf)?.lastOrNull() ?: return named
        val unused = match.bind(name)?.unused?.map { DictionaryMatch.fold(it) }.orEmpty()
        if (tokens.size < 2 || last !in unused) return named
        for (from in maxOf(1, tokens.size - HEAD_WORDS) until tokens.size) {
            val tail = named(tokens.drop(from).joinToString(" "))
            if (tail.entry != null && tail.qualifier == null && tail.preparation.isEmpty()) {
                return tail.copy(qualifier = tokens.take(from).joinToString(" ").trim(*EDGES), usesFirst = false)
            }
        }
        return named
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
     * [name] bound as a whole, so that an entry naming a preparation ("рубленые орехи") keeps it — but for an
     * alternative in it ("или яйцо"), which may stand for it and is never alone what it is bound to; of the words the
     * entry does not use, preparation goes to the note and the rest qualifies the entry.
     */
    private fun named(name: String): Named {
        val tokens = name.split(' ').filter { it.isNotEmpty() }
        val prepared = PREPARATION.findAll(name).map { it.range }.toList()
        var at = 0
        val preparation = tokens.map { token ->
            val start = name.indexOf(token, at).also { at = it + token.length }
            prepared.any { start <= it.last && it.first < start + token.length }
        }
        // "кусочки курицы или яйцо": what stands before an alternative is what is bound, the alternative left to the note —
        // unless only the two together name it ("сливочного или растительного масла" is butter).
        val alternative = ALTERNATIVE.find(name)?.range?.first?.takeIf { it > 0 && name.substring(0, it).any(Char::isLetter) }
        val head = alternative?.let { match.bind(name.substring(0, it)) }
        val bound = head ?: match.bind(name)?.takeIf { whole ->
            alternative == null || wordsOf(name.substring(0, alternative)).any { it !in whole.unused.map { w -> DictionaryMatch.fold(w) } }
        }
        val left = bound?.unused?.map { DictionaryMatch.fold(it) }?.toMutableList()
        if (head != null) left?.addAll(wordsOf(name.substring(alternative!!)))
        // A token of no letters or digits ("+") is no word an entry uses.
        val unused = tokens.map { token -> left == null || wordsOf(token).let { words -> words.isEmpty() || words.count { left.remove(it) } > 0 } }
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
     * [entered]; the unit word, as written ([wordText]), and the size before it ([size], "small bunch"); a word for a
     * whole thing after a unit ([countAfter], "400g can"); the [name] around them; and the words of the line the
     * quantity was found among that are no part of the name, for the note — [before] it (a label, "juice of", a clause's
     * own words) and [after] it ("с горкой").
     */
    private data class Amount(
        val quantity: Rational?,
        val entered: String?,
        val word: UnitWord?,
        val wordText: String,
        val name: String,
        val size: String? = null,
        val countAfter: UnitWord.Count? = null,
        val before: String? = null,
        val after: String? = null,
    ) {
        /** A word for a whole thing counts the quantity, or follows its unit. */
        val counted: Boolean get() = word is UnitWord.Count || countAfter != null

        /** What the word for a whole thing adds to the entry: its base form, its size before it. */
        val countQualifier: String? get() = (word as? UnitWord.Count)?.base?.let { base -> size?.let { "${it.lowercase()} $base" } ?: base }

        /** This amount, found in a clause or a parenthesis of a line, as that of the line's [main] name. */
        fun named(main: String) = copy(name = main, before = name.ifEmpty { null } ?: before)

        companion object {
            /** The quantity, a unit word or both before the name, or null when the line starts with neither. */
            fun lead(text: String): Amount? {
                val found = LEAD.matchEntire(text)
                val quantityText = found?.groups?.get("q")?.value
                val unitText = found?.groups?.get("u")?.value
                if (found != null && (quantityText != null || unitText != null)) {
                    val end = found.groups["q"]?.range?.last?.plus(1)
                    val continues = end != null && text.getOrNull(end)?.let { it in RAW_CHARS } == true
                    // "четверть от 400 г": a share of an amount, not an amount.
                    if (end != null && SHARE_OF.containsMatchIn(text.substring(end))) return null
                    val value = quantityText?.let(::valueOf)
                    if (quantityText == null || value != null && !continues) {
                        return of(value ?: Rational.ONE, null, unitText, found.groups["rest"]!!.value).copy(
                            size = found.groups["size"]?.value,
                            countAfter = found.groups["c"]?.value?.let(UnitWords::of) as? UnitWord.Count,
                        )
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

            /**
             * A name, a spaced dash and an amount with more words after it ("Кофе — 1–2 чайные ложки с горкой",
             * "красный лук — половина луковицы"), those words [after] it; null when no amount follows the first dash.
             */
            fun dashed(text: String): Amount? {
                val dash = DASH.find(text) ?: return null
                val name = text.substring(0, dash.range.first).trim(*EDGES)
                if (name.none(Char::isLetter)) return null
                val amount = lead(text.substring(dash.range.last + 1).trim()) ?: return null
                return amount.copy(name = name, after = amount.name.ifEmpty { null })
            }

            /** A label, a colon and an amount of a name ("подсластитель по вкусу: щепотка стевии"), the label [before] it. */
            fun labelled(text: String): Amount? {
                val colon = COLON.find(text) ?: return null
                val label = text.substring(0, colon.range.first).trim(*EDGES)
                val amount = lead(text.substring(colon.range.last + 1).trim()) ?: return null
                return amount.takeIf { label.isNotEmpty() && it.name.isNotEmpty() }?.copy(before = label)
            }

            /**
             * The amount a clause after a name is, its other words as its [name]: "тёртая — 40 г", "почти в пыль — 1 ч. л.
             * с горкой", "примерно 100 г"; null for a clause that is no amount or names something ("2 сорта").
             */
            fun ofClause(clause: String): Amount? =
                trail(clause) ?: dashed(clause) ?: lead(clause)?.takeIf { it.name.isEmpty() && it.quantity != null }

            /** The amount in a unit a parenthesis is ("~250 г варёной"), its other words as its [name]; null for any other. */
            fun ofParenthesis(paren: String): Amount? =
                lead(paren)?.takeIf { it.quantity != null && it.word is UnitWord.Of }

            fun of(value: Rational?, entered: String?, unitText: String?, name: String) =
                Amount(value, entered, unitText?.let(UnitWords::of), unitText?.trim().orEmpty(), squeeze(name).trim(*EDGES))
        }
    }

    internal companion object {
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
            listOf("четверть", "четверти", "quarter", "a quarter", "quarter of a", "quarter of an", "a quarter of a", "a quarter of an")
                .forEach { put(it, Rational.of(1, 4)) }
            listOf("треть", "трети", "third", "a third", "third of a", "third of an", "a third of a", "a third of an")
                .forEach { put(it, Rational.of(1, 3)) }
            listOf(
                "половина", "половину", "половины", "половинка", "половинку", "половинки", "пол", "half", "half a", "half an",
                "half of a", "half of an",
            ).forEach { put(it, Rational.of(1, 2)) }
            listOf("один", "одна", "одну", "одно", "одного", "одной", "one", "a", "an").forEach { put(it, Rational.ONE) }
            listOf("два", "две", "двух", "two").forEach { put(it, Rational.of(2)) }
            listOf("три", "трёх", "трех", "three").forEach { put(it, Rational.of(3)) }
            listOf("четыре", "четырёх", "четырех", "four").forEach { put(it, Rational.of(4)) }
            listOf("пять", "пяти", "five").forEach { put(it, Rational.of(5)) }
        }

        val UNIT = UnitWords.LINE_PATTERN
        val ATOM = """\d+\s+\d+/\d+|\d*\s?[$GLYPH_CLASS]|\d+/\d+|\d+(?:[.,]\d+)?"""
        val WORD = NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|") { it.replace(" ", """\s+""") } .let {
            """пол-|пол(?=$UNIT)|(?:$it)(?![\p{L}\p{N}])"""
        }
        val NUMBER = """(?:$ATOM|$WORD)"""
        val QUANTITY = """(?:от\s+)?$NUMBER(?:\s*(?:[-–—]|(?:до|to)(?![\p{L}]))\s*$NUMBER)?"""
        val RANGE = Regex("""^(?:от\s+)?($NUMBER)(?:\s*(?:[-–—]|до|to)\s*($NUMBER))?$""", RegexOption.IGNORE_CASE)
        const val RAW_CHARS = "0123456789./,-–—"

        /** "~", "примерно", "about" before a quantity: the quantity is read as written. */
        const val ABOUT = """(?:(?:[~≈]|примерно|около|about|approx\.?|approximately)\s*+)?+"""
        val LEAD = Regex(
            """^$ABOUT(?<q>$QUANTITY)?\s*(?:(?<size>${UnitWords.SIZE})\s++(?=${UnitWords.COUNT_PATTERN}))?(?<u>$UNIT)?\s*""" +
                """(?:(?<c>${UnitWords.COUNT_PATTERN})\s++)?(?:of\s+)?(?<rest>.*)$""",
            RegexOption.IGNORE_CASE,
        )
        val RAW_LEAD = Regex("""^(?<raw>[\d$GLYPH_CLASS][\d$GLYPH_CLASS./,\-–—]*)\s*(?<u>$UNIT)?\s*(?:of\s+)?(?<rest>.*)$""", RegexOption.IGNORE_CASE)
        // The name ends at a letter or a digit and the separator after it is taken whole: no backtracking on a run of dashes.
        val TRAIL = Regex("""^(?<name>.*?[^\s—–:-])[\s—–:-]++$ABOUT(?<q>$QUANTITY)\s*(?<u>$UNIT)?[\s.]*$""", RegexOption.IGNORE_CASE)
        val TRAIL_UNIT = Regex("""^(?<name>.*?[^\s—–:-])[\s—–:-]++(?<u>$UNIT)[\s.]*$""", RegexOption.IGNORE_CASE)

        val PAREN = Regex("""\(([^()]*)\)""")

        /**
         * A comma between two digits is a decimal mark; any other, a semicolon, an arrow ("→ ~500 г варёной") or a full
         * stop after a word of five letters or more before a capital ("эспрессо. Для Вики…", not "Tbsp. Olive") ends a clause.
         */
        val CLAUSE = Regex(""";|,(?!\d)|(?<!\d),|→|(?<=\p{L}\p{L}\p{L}\p{L}\p{L})\.\s++(?=\p{Lu})""")

        /** A dash with a space on either side: what stands after it says more of the name before it. */
        val DASH = Regex("""\s[—–-]\s""")
        val COLON = Regex(""":\s""")

        /**
         * "Крем: 200 г мацони", "For the dressing: 3 tbsp olive oil": a label of up to four words, from a capital or
         * "for" / "для", then a colon.
         */
        val LABEL = Regex("""^(?<label>(?:\p{Lu}|(?i:for|для)(?=\s))[\p{L}-]*+(?:\s[\p{L}-]++){0,3}+):\s++(?<rest>\S.*)$""")

        /**
         * "juice of half a lime", "zest and juice of 1 lemon", "сок половины лимона": up to four words — the fewest that
         * do — then an amount, then the name. Each word's letters are taken whole; only the count of words is tried.
         */
        val BETWEEN = Regex(
            """^(?<pre>\p{L}++(?:[\s,]++\p{L}++){0,3}?)\s++(?:of\s++)?(?<q>$QUANTITY)\s*+(?<u>$UNIT)?\s*+(?:of\s++)?(?<rest>\p{L}.*)$""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * The most words of a name's end tried as what it is, after a word for a whole thing: as many as the longest
         * name of the dictionary.
         */
        const val HEAD_WORDS = 6

        val SHARE_OF = Regex("""^\s*+(?:от|of)\s++\d""", RegexOption.IGNORE_CASE)

        /** An alternative to what is named before it: "или 1 желток", "or 2 eggs". */
        val ALTERNATIVE = Regex("""(?<![\p{L}])(?:или|or)(?![\p{L}])""", RegexOption.IGNORE_CASE)
        val EDGES = charArrayOf(' ', ',', ';', ':', '—', '–', '-', '.')

        /** How an ingredient is prepared, or what may stand for it ("или по вкусу"), never what it is: the row's note. */
        val PREPARATION = Regex(
            listOf(
                "мелко", "крупно", "тонко", "грубо", """кубик\p{L}*""", """соломк\p{L}*""", """(?:полу)?кольц\p{L}*""",
                """ломтик\p{L}*""", """\p{L}*рублен\p{L}*""", """\p{L}*резан\p{L}*""", "нарезать", "натереть",
                """\p{L}*т[её]рт\p{L}*""", """измельч\p{L}*""", """очищен\p{L}*""", "очистить", """растоплен\p{L}*""",
                "растопить", """размягч\p{L}*""", """взбит\p{L}*""", """просеян\p{L}*""", "просеять", """охлажд\p{L}*""",
                """нашинков\p{L}*""", """\p{L}*давлен\p{L}*""", """промыт\p{L}*""", """разморож\p{L}*""",
                """комнатной\s+температуры""", """по\s+желанию""", "опционально", """кусоч\p{L}*""", """на\s+подпыл""",
                """для\s.*""", """(?:или|и|or|and)\s.*""",
                "finely", "coarsely", "thinly", "roughly", "freshly", "chopped", "diced", "minced", "sliced", "grated",
                "shredded", "sifted", "melted", "softened", "beaten", "peeled", "crushed", "cubed", "halved", "quartered",
                "divided", """(?:at\s+)?room\s+temperature""", "optional", """for\s.*""",
            ).joinToString("|", """(?<![\p{L}\p{N}])(?:""", """)(?![\p{L}\p{N}])"""),
            RegexOption.IGNORE_CASE,
        )
        /** A note that is only one of these says nothing: "macaroni and cheese" leaves "and" when cheese is no part of it. */
        val CONJUNCTIONS = setOf("и", "или", "and", "or")
        val SPACES = Regex("""\s+""")
        val UNICODE_SPACE = Regex("""[\p{Z}\t]""")
        val WORD_SEPARATORS = Regex("""[^\p{L}\p{N}]+""")

        fun squeeze(text: String) = text.replace(SPACES, " ").trim()

        /**
         * "800 г", "1 ½ стакана", "4 горошины", "щепотка", "по вкусу": [text] is an amount and no name, as a line's
         * quantity is read — in any form, with any unit or word for a whole thing, or a unit word alone.
         */
        fun amountOnly(text: String): Boolean {
            val written = squeeze(text.replace(UNICODE_SPACE, " ")).trim(*EDGES)
            if (written.isEmpty()) return false
            if (UnitWords.TO_TASTE.replace(written, "").isBlank() || written.lowercase() == "по желанию") return true
            val amount = Amount.lead(written) ?: return false
            return amount.name.isEmpty() && (amount.quantity != null || amount.entered != null || amount.word != null)
        }

        /** [text] cut at each plus with a space on either side and outside a parenthesis, the parts trimmed. */
        fun plusParts(text: String): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var from = 0
            for (i in text.indices) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                    '+' -> if (depth == 0 && i > 0 && i < text.lastIndex && text[i - 1].isWhitespace() && text[i + 1].isWhitespace()) {
                        parts += text.substring(from, i).trim()
                        from = i + 1
                    }
                }
            }
            parts += text.substring(from).trim()
            return parts.filter { it.isNotEmpty() }
        }

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

        /**
         * The row's note: the [parts] — preparation and comma clauses — joined, cut to the bound at a word; then each
         * parenthesis that still fits, and none that does not.
         */
        fun noteOf(parts: List<String>, parens: List<String>): String? {
            var note = parts.filter { it.isNotBlank() && it.lowercase() !in CONJUNCTIONS }.joinToString(", ")
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
