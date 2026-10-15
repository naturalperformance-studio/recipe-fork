package app.recipefork.core.paste.corpus

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.file.EntryLabels
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import java.io.File

/** One text of the corpus: the [set] it belongs to ("owner", "en", "ru"), its file [name] and the [text] as pasted. */
data class CorpusText(val set: String, val name: String, val text: String) {
    /** The owner's shopping list: no recipe, and no part of the measures. */
    val isShoppingList: Boolean get() = set == OWNER && name.contains("список покупок")

    companion object {
        const val OWNER = "owner"
        val SETS = listOf(OWNER, "en", "ru")

        /** Every text under [root] (core/src/test/resources/paste/corpus), set by set, in name order. */
        fun load(root: File = File("src/test/resources/paste/corpus")): List<CorpusText> = SETS.flatMap { set ->
            File(root, set).listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
                .map { CorpusText(set, it.name, it.readText()) }
        }

        /**
         * Every `*.txt` under [dir], at any depth, in path order: its set the first segment of its path below [dir] where
         * that is "ru" or "en", "extra" otherwise; its name the rest of the path.
         */
        fun loadExtra(dir: File): List<CorpusText> = dir.walkTopDown().filter { it.isFile && it.extension == "txt" }
            .map { it.relativeTo(dir).invariantSeparatorsPath to it }.sortedBy { it.first }
            .map { (path, file) ->
                val set = path.substringBefore('/').takeIf { '/' in path && it in EXTRA_SETS }
                CorpusText(set ?: EXTRA, if (set == null) path else path.substringAfter('/'), file.readText())
            }
            .toList()

        private const val EXTRA = "extra"
        private val EXTRA_SETS = setOf("ru", "en")
    }
}

/** What one text became and how well it was read. [recipe] is null where the reader found none. */
data class TextMeasure(
    val text: CorpusText,
    val recipe: Recipe?,
    val rows: Int,
    val bound: Int,
    val free: Int,
    /** Rows whose quantity and unit, or to-taste, were read; the others are as written. */
    val quantified: Int,
    val references: Int,
    /** Durations the steps' text names, by [WrittenDurations]' own count. */
    val writtenDurations: Int,
    /** Of those, how many the steps carry as timers: per step, the fewer of the two. */
    val timedDurations: Int,
    val timers: Int,
    val equipment: Int,
    val fileProblems: List<String>,
) {
    val asWritten: Int get() = rows - quantified
}

/** The sums of [measures]; ratios over the texts that count, the shopping list never among them. */
data class CorpusTotals(val measures: List<TextMeasure>) {
    private val counted = measures.filter { !it.text.isShoppingList }
    val texts = counted.size
    val recipes = counted.count { it.recipe != null }
    val rows = counted.sumOf { it.rows }
    val bound = counted.sumOf { it.bound }
    val free = counted.sumOf { it.free }
    val quantified = counted.sumOf { it.quantified }
    val writtenDurations = counted.sumOf { it.writtenDurations }
    val timedDurations = counted.sumOf { it.timedDurations }
    val timers = counted.sumOf { it.timers }
    val references = counted.sumOf { it.references }
    val equipment = counted.sumOf { it.equipment }
    val passingFileCheck = counted.count { it.recipe != null && it.fileProblems.isEmpty() }

    val quantifiedShare: Double get() = share(quantified, rows)
    val boundShare: Double get() = share(bound, rows)
    val timerShare: Double get() = share(timedDurations, writtenDurations)

    private fun share(part: Int, whole: Int) = if (whole == 0) 1.0 else part.toDouble() / whole
}

/** The corpus measures, read with any reader so the same figures come out of the test and the report. */
object CorpusMeasures {
    /**
     * The corpus floors: the share of rows with a quantity read, bound to the dictionary, durations timed — each what
     * the corpus measured after T-284 (99.3 %, 95.9 %, 99.6 %), rounded down to a whole percent. The measure errs low:
     * every qualifier on a to-taste row counts as a missed quantity ("Вода холодная", "whole-milk yogurt").
     */
    const val QUANTIFIED_FLOOR = 0.99
    const val BOUND_FLOOR = 0.95
    const val TIMER_FLOOR = 0.99

    fun measure(texts: List<CorpusText>, read: (String) -> Recipe?, dictionaries: DictionariesDto): List<TextMeasure> {
        val labels = dictionaries.equipment.associate { it.id to EntryLabels(it.labelRu, it.labelEn) }
        return texts.map { text ->
            val recipe = read(text.text)
            if (recipe == null) {
                TextMeasure(text, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, emptyList())
            } else {
                val written = recipe.steps.map { WrittenDurations.count(it.text) }
                TextMeasure(
                    text = text,
                    recipe = recipe,
                    rows = recipe.ingredients.size,
                    bound = recipe.ingredients.count { it.dictionaryEntry != null },
                    free = recipe.ingredients.count { it.dictionaryEntry == null },
                    quantified = readRows(recipe.ingredients).count { it },
                    references = recipe.steps.sumOf { it.ingredientReferences.size },
                    writtenDurations = written.sum(),
                    timedDurations = recipe.steps.zip(written).sumOf { (step, count) -> minOf(count, step.timers.size) },
                    timers = recipe.steps.sumOf { it.timers.size },
                    equipment = recipe.equipment.size,
                    fileProblems = fileProblems(recipe, labels),
                )
            }
        }
    }

    /**
     * For each of [rows], in order, whether its quantity was read ([quantityRead]): the row after it is part of the
     * question, since a name-only row above a quantity-only row is one line read as two.
     */
    fun readRows(rows: List<Ingredient>): List<Boolean> =
        rows.mapIndexed { i, row -> quantityRead(row, rows.getOrNull(i + 1), rows.getOrNull(i - 1)) }

    /**
     * Whether [row]'s quantity was read, [next] being the row after it in its recipe:
     * - never where its free-text name is a quantity ("800 г", "2 штуки", "по вкусу") or only a unit word ("зубчика"), or
     *   where [next] is such a quantity-only row (the name half of "Говядина на кости" / "800 г");
     * - never where its qualifier holds a number other than a percentage ("x 400g cans", not "9%");
     * - never where a bare name to taste and a one-word free-text row with a quantity follow each other — the eda.ru
     *   pair read as two ("Душистый перец" / "4 горошины"): both halves are unread;
     * - to taste: only where no number is left in its name, qualifier or note ("куриная грудка, замороженная — 350 г"),
     *   and its qualifier, if any, is only a fat content — a qualifier on a to-taste row is mostly a quantity in words
     *   ("of half a", "small bunch", "одного", "горсть"), so the measure counts it unread and errs low;
     * - otherwise: where it holds a unit and a quantity.
     */
    fun quantityRead(row: Ingredient, next: Ingredient?, previous: Ingredient? = null): Boolean {
        if (quantityOnly(row) || next != null && quantityOnly(next)) return false
        if (previous != null && pairedTail(previous, row) || next != null && pairedTail(row, next)) return false
        if (row.qualifier?.let(::holdsNumber) == true) return false
        return when (row.unitType) {
            UnitType.TASTE_UNMEASURED ->
                listOfNotNull(row.freeTextName, row.qualifier, row.note).none(::holdsNumber) &&
                    row.qualifier?.let { PERCENT.replace(it, "").isBlank() } != false
            else -> row.unit != null && row.quantity != null
        }
    }

    /** [tail] is the quantity half of [name]: a bare name to taste, then a one-word free-text row holding a quantity. */
    private fun pairedTail(name: Ingredient, tail: Ingredient): Boolean =
        name.unitType == UnitType.TASTE_UNMEASURED && name.qualifier.isNullOrBlank() &&
            tail.dictionaryEntry == null && tail.quantity != null &&
            tail.freeTextName?.trim()?.split(' ')?.size == 1

    /** A row whose own words are a quantity or a unit, not a name: a line the reader took apart from its name. */
    private fun quantityOnly(row: Ingredient): Boolean {
        val free = row.freeTextName?.trim() ?: return false
        return free.firstOrNull()?.let { it.isDigit() || it in FRACTIONS } == true || TO_TASTE_ONLY.matches(free) || UNIT_ONLY.matches(free)
    }

    /** A digit, not counting a fat content ("9%", "1–3%", "3,5 жирности"), which says what the ingredient is. */
    private fun holdsNumber(text: String) = PERCENT.replace(text, "").any(Char::isDigit)

    private const val FRACTIONS = "½¼¾⅓⅔⅛"
    private val PERCENT = Regex("""\d++(?:[.,]\d++)?+(?:\s*+[–—-]\s*+\d++(?:[.,]\d++)?+)?+\s*+(?:%|жирност\p{L}*+)""", RegexOption.IGNORE_CASE)
    private val TO_TASTE_ONLY = Regex("""\s*+(?:по\s++вкусу|to\s++taste)\s*+""", RegexOption.IGNORE_CASE)
    private val UNIT_ONLY = Regex(
        """(?:зубчик\p{L}*+|штук\p{L}*+|шт|г|гр|грамм\p{L}*+|кг|мл|л|литр\p{L}*+|стакан\p{L}*+|ложк\p{L}*+|щепотк\p{L}*+|""" +
            """cloves?+|cups?+|tbsp|tsp|g|kg|ml|l|oz|lbs?+|pinch(?:es)?+|pieces?+)\.?+""",
        RegexOption.IGNORE_CASE,
    )

    /** Every rule of a recipe file [recipe] breaks, as written to a file's document and as read back from one. */
    fun fileProblems(recipe: Recipe, labels: Map<Long, EntryLabels>): List<String> {
        val content = RecipeFileContent.of(recipe, parentInBook = null, ownerNickname = null, equipmentLabels = labels::get)
        return (RecipeFileCheck.problems(content) + RecipeFileCheck.problems(RecipeFile.decode(RecipeFile.encode(content)))).distinct()
    }
}

/**
 * A count of the durations a step's text names, kept apart from the parser's own reading: a number (or a range of
 * numbers, or a number in words: "две минуты", "two hours") followed by a minute or hour word, a minute or an hour with
 * no number ("прогреть минуту", "около часа", "по минуте", "another minute", "an hour"), or "полчаса" / "half an hour".
 * An hour followed by its minutes ("1 ч 30 мин"), and two durations joined as a range ("45 mins - 1 hr"), count once;
 * one after "каждые" / "every" says how often, one in a comparison ("на минуту меньше", "an hour later") says how much
 * longer or sooner, and a minute or an hour with no number after an ordinal ("в первую минуту"), between "за" and "до"
 * ("за минуту до конца", but "подойдёт за час") or before "before" / "пик" says when or another thing: none of them is
 * counted. Seconds and days are no timer and are not counted.
 */
object WrittenDurations {
    // Possessive quantifiers, a fixed lookbehind: linear on any text.
    private const val NUMBER = """\d++(?:[.,]\d++)?+(?:\s*+[½¼¾⅓⅔])?+|[½¼¾⅓⅔]"""
    private const val UNIT = """час(?:а|ов)?+|ч(?!\.?+\s*+/?+\s*+л)\.?+|hours?+|hrs?+\.?+|h|минут[аыуе]?+|мин\.?+|minutes?+|mins?+\.?+"""
    private val DURATION = Regex(
        """(?<![\p{L}\p{N}])(?:(?:$NUMBER)(?:\s*+(?:[-–—]|до|to)\s*+(?:$NUMBER))?+\s*+(?:$UNIT)|полчаса|полтора\s++часа|half\s++an\s++hour""" +
            """|(?<words>$WORDS)\s++(?:минут[аыу]?+|час(?:а|ов)?+|minutes?+|hours?+)""" +
            """|(?<bare>минут(?:ку|очку|а|у)|по\s++минуте|$HOW_LONG\s++(?:минуты|минутки|часа)|час|(?:an?|one|another)(?:\s++(?:further|extra|additional|more))?+\s++(?:minute|hour)))(?!\p{L})""",
        RegexOption.IGNORE_CASE,
    )
    private const val HOW_LONG = """(?:около|в\s++течение|до|не\s++(?:менее|более|больше|меньше|дольше)|минимум|максимум|больше|меньше|ещё|еще)"""
    private const val WORDS = """одну|одна|один|две|два|пару|три|четыре|пять|шесть|семь|восемь|девять|десять|пол|two|three|four|five""" +
        """|six|seven|eight|nine|ten|a\s++couple\s++of|couple\s++of"""
    private val JOINS = Regex("""^\s*+(?:[-–—]|и|and|до|to)?+\s*+$""", RegexOption.IGNORE_CASE)
    private val EVERY = Regex("""(?:каждые|каждую|каждый|every)\s*+$""", RegexOption.IGNORE_CASE)
    private val BEFORE_COMPARISON = Regex("""(?<!\p{L})на\s*+$""", RegexOption.IGNORE_CASE)
    private val COMPARATIVE = Regex("""^\s*+(?:меньше|больше|раньше|позже|дольше|короче)(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val COMPARATIVE_EN = Regex("""^\s*+(?:earlier|later|less)(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val ORDINAL = Regex(
        """(?<!\p{L})(?:первую|первой|первый|вторую|второй|третью|третьей|четв[её]ртую|четв[её]ртой|пятую|пятой|последнюю|последней|последний""" +
            """|first|second|third|last|final)\s*+$""",
        RegexOption.IGNORE_CASE,
    )
    private val FIGURE = Regex("""\d[^\s\d]{0,6}+\s*+$""")
    private val ZA = Regex("""(?<!\p{L})за\s*+$""", RegexOption.IGNORE_CASE)
    private val DO = Regex("""^\s*+до(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val OTHER_THING = Regex("""^\s*+(?:before|пик)(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val WAITING = Regex("""(?<!\p{L})(?:for|least|cool|rest|stand|sit|chill|marinate|set)\s*+$""", RegexOption.IGNORE_CASE)
    private val TENS = Regex(
        """(?:-|(?<!\p{L})(?:twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|двадцать|тридцать|сорок|пятьдесят|шестьдесят|семьдесят|восемьдесят|девяносто)\s++)$""",
        RegexOption.IGNORE_CASE,
    )

    fun count(text: String): Int {
        var count = 0
        var previousEnd = -1
        for (found in DURATION.findAll(text)) {
            val joined = previousEnd >= 0 && JOINS.matches(text.substring(previousEnd, found.range.first))
            val before = text.substring(maxOf(0, found.range.first - 12), found.range.first)
            val after = text.substring(found.range.last + 1, minOf(text.length, found.range.last + 13))
            val every = EVERY.containsMatchIn(before)
            val comparison = BEFORE_COMPARISON.containsMatchIn(before) && COMPARATIVE.containsMatchIn(after) || COMPARATIVE_EN.containsMatchIn(after)
            val bare = found.groups["bare"] != null
            val otherThing = OTHER_THING.containsMatchIn(after) && !WAITING.containsMatchIn(before)
            val at = bare && (ORDINAL.containsMatchIn(before) || FIGURE.containsMatchIn(before) || ZA.containsMatchIn(before) && DO.containsMatchIn(after) || otherThing)
            val compound = found.groups["words"] != null && TENS.containsMatchIn(before)
            if (!joined && !every && !comparison && !at && !compound) count++
            previousEnd = found.range.last + 1
        }
        return count
    }
}
