package app.recipefork.core.paste

import app.recipefork.core.recipe.RecipeLanguage

/** One non-blank line of a pasted text, its Markdown markers and emphasis already stripped. */
internal sealed interface PasteLine {
    /** A Markdown heading ([markdown], [level] 1–6), or a bold-only, a short colon-ended or a bare keyword line. */
    data class Heading(val text: String, val level: Int, val markdown: Boolean) : PasteLine

    /**
     * A line of text; [marked] when it had a bullet, a number or a leading pictograph ("✅", "▢", "1️⃣"), [numbered] when
     * that was a number, [symbol] when it was only a pictograph.
     */
    data class Item(
        val text: String,
        val numbered: Boolean,
        val marked: Boolean,
        val fromTable: Boolean = false,
        val symbol: Boolean = false,
    ) : PasteLine
}

internal object PasteLines {
    private const val MINOR = 7
    private const val COLON_HEADING_MAX = 40
    private const val NAME_MAX = 40
    private const val TIME_LINE_MAX = 60

    private val MD_HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val MARKER = Regex("""^(?:([-*+•–—])|\d{1,2}[.)]|(?:step|шаг)\s*\d{1,2}[.:)]?)\s+(?:\[[ xX]]\s+)?""", RegexOption.IGNORE_CASE)
    private val LABEL_ONLY = Regex("""^(?:(?:step|шаг)\s*\d{1,3}\s*[.:)]?|\d{1,2}[.)]?)$""", RegexOption.IGNORE_CASE)
    private val WIKI_LINK = Regex("""\[\[([^\[\]|]+)(?:\|([^\[\]]+))?]]""")
    private val LINK = Regex("""\[([^\[\]]+)]\([^()]*\)""")
    private val STAR = Regex("""(?<![\p{L}\d*])\*(\S(?:[^*]*\S)?)\*(?![\p{L}\d*])""")
    private val UNDERSCORE = Regex("""(?<![\p{L}\d_])_(\S(?:[^_]*\S)?)_(?![\p{L}\d_])""")
    private val SPACES = Regex("""\s+""")
    private val PARENTHESES = Regex("""\([^()]*\)""")
    private val QUANTITY = Regex(
        """^[~≈]?\s*(?:\d|[½¼¾⅓⅔⅛⅜⅝⅞]|(?:one|two|three|four|five|six|half|pinch|dash|handful|щепотк\p{L}*|пучок|пол|""" +
            """половин\p{L}*|один|одна|одно|два|две|три|четыре|пять|несколько)(?!\p{L}))""",
    )
    private val EQUIPMENT_LINE = Regex("""^(?:посуда|оборудование|инвентарь|equipment|tools)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val ABBREVIATIONS = setOf("л", "ч", "ст", "г", "гр", "кг", "мл", "шт", "tsp", "tbsp", "oz", "lb", "lbs", "pkg", "c")
    private val NAME_DASH = Regex("""\s[—–-]\s""")
    private val TO_TASTE = Regex("""^(?:по вкусу|to taste)""", RegexOption.IGNORE_CASE)
    private val TIME_LINE = Regex(
        """^(?:(?:общее |активное )?время(?:\s+(?:приготовления|готовки|подготовки|выпечки|запекания|варки|жарки)(?!\p{L})|\s*[:—–\-\d])|""" +
            """(?:prep(?:aration)?|cook(?:ing)?|bake|baking|total|active)\s+time(?!\p{L})|ready in(?!\p{L}))""",
        RegexOption.IGNORE_CASE,
    )
    private val CLOSING = Regex("""^(?:приятного аппетита|bon app[eé]tit)(?!\p{L})""", RegexOption.IGNORE_CASE)

    /** Symbols that open a line as text, not as a list marker. */
    private val NOT_MARKERS = setOf('°'.code, '№'.code, '©'.code, '®'.code, '™'.code, '℃'.code, '℉'.code)

    /** A keyword that opens a section only as the whole heading ("Состав:", not "Состав на 100 г:" or "Составить"). */
    private val WHOLE_WORDS = setOf("состав", "инструкция")
    private val INGREDIENT_WORDS_INSIDE = listOf("понадобится", "потребуется")

    private val SECTIONS: List<Pair<Section, List<String>>> = listOf(
        Section.EQUIPMENT to listOf("посуда", "что нужно из посуды", "оборудование", "инвентарь", "equipment", "tools"),
        Section.INGREDIENTS to listOf(
            "ингредиент", "вам понадобится", "нам понадобится", "что понадобится", "понадобится", "что потребуется",
            "потребуется", "продукты", "состав",
            "ingredient", "you will need", "you'll need", "what you need", "what you'll need",
        ),
        Section.STEPS to listOf(
            "приготовление", "способ приготовления", "как приготовить", "как готовить", "порядок приготовления", "шаги",
            "инструкция приготовления", "инструкция по приготовлению", "инструкция",
            "method", "directions", "instructions", "preparation", "steps", "how to make", "how to cook",
        ),
        // A shopping list names what to buy, not what goes in: its lines are notes.
        Section.NOTES to listOf(
            "заметки", "примечани", "совет", "хранение", "список покупок", "notes", "note", "tips", "storage", "cook's note",
            "shopping list",
        ),
    )

    /** The lines of [text]; a run of table rows becomes its ingredient lines, or nothing (PasteTables). */
    fun split(text: String): List<PasteLine> {
        val lines = mutableListOf<PasteLine>()
        val table = mutableListOf<String>()
        fun endTable() {
            lines += PasteTables.ingredientLines(table)
            table.clear()
        }
        text.lines().forEach { raw ->
            if (raw.trim().startsWith('|')) {
                table += raw
            } else {
                endTable()
                classify(raw)?.let { lines += it }
            }
        }
        endTable()
        return lines
    }

    fun classify(raw: String): PasteLine? {
        var line = raw.trim().removePrefix("\uFEFF").trim()
        if (line.startsWith('>')) {
            val start = line.indexOfFirst { it != '>' && !it.isWhitespace() }
            line = if (start < 0) "" else line.substring(start)
        }
        if (line.isEmpty() || line.startsWith('|') || line.startsWith("```") || isRule(line) || isHashtags(line)) return null
        MD_HEADING.matchEntire(line)?.let { m ->
            val cleaned = clean(m.groupValues[2].trimEnd { it == '#' || it.isWhitespace() })
            // "## 🛒 Ингредиенты": a pictograph before the heading's words is not part of them.
            val text = cleaned.substring(symbolsEnd(cleaned)).trim().trimEnd(':').trim()
            return if (text.isEmpty()) null else PasteLine.Heading(text, m.groupValues[1].length, markdown = true)
        }
        // Markers are read after emphasis is gone, so "- **Шаг 1.** Промыть" loses both the bullet and the label.
        var text = clean(line)
        var listed = false
        var numbered = false
        var symbol = false
        repeat(3) {
            val keycaps = keycapsEnd(text)
            val symbols = if (keycaps == 0) symbolsEnd(text) else 0
            when {
                keycaps > 0 -> {
                    listed = true
                    numbered = true
                    text = text.substring(keycaps).trim()
                }
                symbols > 0 -> {
                    symbol = true
                    text = text.substring(symbols).trim()
                }
                else -> MARKER.find(text)?.let { m ->
                    listed = true
                    numbered = numbered || m.groups[1] == null
                    text = text.substring(m.range.last + 1).trim()
                }
            }
        }
        if (text.isEmpty() || LABEL_ONLY.matches(text)) return null
        if (listed) return PasteLine.Item(text, numbered, marked = true)
        // A line led by a pictograph alone ("🔸 Для теста:", "📝 Ингредиенты") may still be a heading.
        val name = text.trimEnd(':').trim()
        return when {
            isBoldOnly(line) && !endsSentence(name) -> PasteLine.Heading(name, MINOR, markdown = false)
            text.endsWith(':') && text.length <= COLON_HEADING_MAX -> PasteLine.Heading(name, MINOR, markdown = false)
            sectionOf(name, bare = true) != null -> PasteLine.Heading(name, MINOR, markdown = false)
            else -> PasteLine.Item(text, numbered = false, marked = symbol, symbol = symbol)
        }
    }

    /** The end of a run of keycap numbers ("1️⃣", "1️⃣0️⃣") at the start of [text]; 0 when it has none. */
    private fun keycapsEnd(text: String): Int {
        var i = 0
        while (i < text.length && (text[i].isDigit() || text[i] == '#' || text[i] == '*')) {
            var j = i + 1
            if (text.getOrNull(j) == '\uFE0F') j++
            if (text.getOrNull(j) != '\u20E3') break
            i = j + 1
        }
        return i
    }

    /**
     * The end of a run of pictographs ("🔸", "▪️", "✅", "▢", "👩‍🍳") at the start of [text], their variation selectors,
     * joiners and skin tones included; 0 when it has none.
     */
    private fun symbolsEnd(text: String): Int {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val type = Character.getType(cp)
            val pictograph = type == Character.OTHER_SYMBOL.toInt() && cp !in NOT_MARKERS
            val joining = i > 0 && (
                cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3 || type == Character.MODIFIER_SYMBOL.toInt()
                )
            if (!pictograph && !joining) break
            i += Character.charCount(cp)
        }
        return i
    }

    /** "#рецепт #сырники": a line of hashtags only. */
    private fun isHashtags(line: String): Boolean =
        line.length > 1 && line[1] != '#' && !line[1].isWhitespace() && line.split(SPACES).all { it.startsWith('#') && it.length > 1 }

    /** A thematic break: three or more of one of - * _ = and nothing else but spaces. */
    private fun isRule(line: String): Boolean {
        val marks = line.filterNot { it.isWhitespace() }
        return marks.length >= 3 && marks[0] in "-*_=" && marks.all { it == marks[0] }
    }

    /** "**Тесто**", "__Тесто:__", "**Тесто**:" — wholly bold. */
    private fun isBoldOnly(line: String): Boolean {
        val body = line.removeSuffix(":")
        val mark = body.take(2).takeIf { it == "**" || it == "__" } ?: return false
        if (body.length <= 4 || !body.endsWith(mark)) return false
        val inner = body.substring(2, body.length - 2)
        return "**" !in inner && "__" !in inner
    }

    /**
     * The section a heading opens, by its words; null for any other heading. A [bare] line (no Markdown, no bold, no
     * colon) counts only when it is the keyword itself, give or take an inflection ending.
     */
    fun sectionOf(heading: String, bare: Boolean = false): Section? {
        val name = heading.lowercase().replace('ё', 'е').replace(PARENTHESES, " ").trim().trimEnd(':').trim()
        val byStart = SECTIONS.firstOrNull { (_, words) ->
            words.any { opens(name, it) && (!bare || name.length - it.length <= 3) }
        }?.first
        if (byStart != null || bare) return byStart
        // "Для сырников нам понадобится:": a short heading that says something will be needed.
        return Section.INGREDIENTS.takeIf { name.length <= COLON_HEADING_MAX && INGREDIENT_WORDS_INSIDE.any { it in name } }
    }

    private fun opens(name: String, word: String) = if (word in WHOLE_WORDS) name == word else name.startsWith(word)

    /** Strips Markdown emphasis and links (Obsidian's included) and folds runs of whitespace. */
    fun clean(text: String): String = text
        .replace(WIKI_LINK) { it.groupValues[2].ifEmpty { it.groupValues[1] } }
        .replace(LINK, "$1")
        .replace("**", "").replace("__", "").replace("~~", "")
        .replace(STAR, "$1")
        .replace(UNDERSCORE, "$1")
        .replace(SPACES, " ")
        .trim()

    fun quantityLed(text: String): Boolean = QUANTITY.containsMatchIn(text.lowercase())

    /** "Творог — 500 г", "Соль — щепотка", "Milk – 500 ml": a short name, a dash, then a quantity. */
    fun nameFirst(text: String): Boolean {
        val dash = NAME_DASH.find(text) ?: return false
        val name = text.substring(0, dash.range.first)
        val rest = text.substring(dash.range.last + 1).trim()
        return name.length <= NAME_MAX && name.any(Char::isLetter) && (quantityLed(rest) || TO_TASTE.containsMatchIn(rest))
    }

    /** Led by a quantity, or [nameFirst]: the shape of an ingredient line. */
    fun ingredientLed(text: String): Boolean = quantityLed(text) || nameFirst(text)

    /** "Время приготовления: 40 минут", "Prep Time 15 minutes": how long it takes, not a part of the recipe. */
    fun timeLine(text: String): Boolean = text.length <= TIME_LINE_MAX && TIME_LINE.containsMatchIn(text) && text.any(Char::isDigit)

    /** "Приятного аппетита!", "Bon appétit": a closing wish, not a step. */
    fun closing(text: String): Boolean = CLOSING.containsMatchIn(text)

    /** Ends as a sentence does: "!", "?", "…", or a full stop that does not close a unit's abbreviation ("1 ч. л."). */
    fun endsSentence(text: String): Boolean = when (text.lastOrNull()) {
        '!', '?', '…' -> true
        '.' -> text.dropLast(1).takeLastWhile(Char::isLetter).lowercase() !in ABBREVIATIONS
        else -> false
    }

    /** The equipment a "Посуда: …" / "Equipment: …" line names, or null for any other line. */
    fun equipmentLine(text: String): String? = EQUIPMENT_LINE.matchEntire(text)?.groupValues?.get(1)?.trim()?.trimEnd('.')

    /** Russian when more of the text's words are written in Cyrillic than in Latin, English otherwise. */
    fun language(text: String): RecipeLanguage {
        var cyrillic = 0
        var latin = 0
        Regex("""\p{L}+""").findAll(text).forEach {
            when (Character.UnicodeScript.of(it.value[0].code)) {
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                Character.UnicodeScript.LATIN -> latin++
                else -> Unit
            }
        }
        return if (latin > cyrillic) RecipeLanguage.EN else RecipeLanguage.RU
    }
}

/** The serving count a recipe text states: "на 4 порции", "Порций: 4", "Serves 4", "Yield: 4 servings", "на 4 бургера". */
internal object Servings {
    private const val NUM = """(?<![\p{L}\d.,/])(\d{1,3}|одн[аоу]|один|две|два|три|четыре|пять|шесть|one|two|three|four|five|six)""" +
        """(?:\s*[–—-]\s*\d{1,3})?"""
    private val WORDS = mapOf(
        "одна" to 1, "одно" to 1, "одну" to 1, "один" to 1, "две" to 2, "два" to 2, "три" to 3, "четыре" to 4,
        "пять" to 5, "шесть" to 6, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
    )
    private val EXPLICIT = listOf(
        Regex("""$NUM\s+порци"""),
        Regex("""порци[йия]?\s*[:—–-]\s*$NUM"""),
        Regex("""serves\s*:?\s*$NUM"""),
        Regex("""servings?\s*[:—–-]?\s*$NUM"""),
        Regex("""$NUM\s+servings?"""),
        Regex("""feeds\s+$NUM"""),
        Regex("""for\s+$NUM\s+(?:people|persons)"""),
    )

    /** "на N <что-то>": for N of something, which is servings; time, weight and the like are not. */
    private val FOR_COUNT = Regex("""(?<!\p{L})на\s+$NUM\s+(\p{L}+)""")
    private val UNITS = setOf("ч", "г", "гр", "кг", "мл", "л", "см", "мм")
    private val UNIT_STEMS = listOf("мин", "сек", "час", "град", "дн", "ден", "сут", "недел", "месяц", "раз", "част", "процент")
    private val SERVINGS_LINE = Regex("""^(?:serves|servings?|yields?|makes|порций|порции|количество порций)(?!\p{L})""")

    fun find(sources: List<String>): Int? {
        val lower = sources.map { it.lowercase() }
        lower.forEach { s -> EXPLICIT.forEach { r -> r.find(s)?.let { m -> value(m.groupValues[1])?.let { return it } } } }
        lower.forEach { s ->
            FOR_COUNT.findAll(s).forEach { m ->
                val word = m.groupValues[2]
                if (word !in UNITS && UNIT_STEMS.none(word::startsWith)) value(m.groupValues[1])?.let { return it }
            }
        }
        return null
    }

    /** A line that only states the servings ("Serves 4", "Порций: 4"): read for them, not kept as description. */
    fun isServingsLine(text: String): Boolean = SERVINGS_LINE.containsMatchIn(text.lowercase()) && find(listOf(text)) != null

    private fun value(token: String): Int? = (token.toIntOrNull() ?: WORDS[token])?.takeIf { it in 1..99 }
}
