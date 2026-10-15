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
    /** The corpus floors (T-281): the share of rows with a quantity read, bound to the dictionary, durations timed. */
    const val QUANTIFIED_FLOOR = 0.95
    const val BOUND_FLOOR = 0.80
    const val TIMER_FLOOR = 0.90

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
                    quantified = recipe.ingredients.count(::quantityRead),
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
     * Whether a row's quantity was read: a unit and a quantity, or to taste where no number was left in its name — a
     * to-taste row named "Rice 500g" is a quantity the reader missed. A row whose own words are a quantity ("800 г",
     * "по вкусу") is a line read apart from its name, and is not read either.
     */
    fun quantityRead(row: Ingredient): Boolean {
        val free = row.freeTextName
        if (free != null && (free.any(Char::isDigit) || TO_TASTE_ONLY.matches(free))) return false
        return when (row.unitType) {
            UnitType.TASTE_UNMEASURED -> listOfNotNull(row.freeTextName, row.qualifier).none { name -> name.any(Char::isDigit) }
            else -> row.unit != null && row.quantity != null
        }
    }

    private val TO_TASTE_ONLY = Regex("""\s*+(?:по\s++вкусу|to\s++taste)\s*+""", RegexOption.IGNORE_CASE)

    /** Every rule of a recipe file [recipe] breaks, as written to a file's document and as read back from one. */
    fun fileProblems(recipe: Recipe, labels: Map<Long, EntryLabels>): List<String> {
        val content = RecipeFileContent.of(recipe, parentInBook = null, ownerNickname = null, equipmentLabels = labels::get)
        return (RecipeFileCheck.problems(content) + RecipeFileCheck.problems(RecipeFile.decode(RecipeFile.encode(content)))).distinct()
    }
}

/**
 * A count of the durations a step's text names, kept apart from the parser's own reading: a number (or a range of
 * numbers) followed by a minute or hour word, or "полчаса" / "half an hour". An hour followed by its minutes ("1 ч 30
 * мин"), and two durations joined as a range ("45 mins - 1 hr"), count once; one after "каждые" / "every" says how
 * often, and is not counted. Seconds and days are no timer and are not counted.
 */
object WrittenDurations {
    // Possessive quantifiers, a fixed lookbehind: linear on any text.
    private const val NUMBER = """\d++(?:[.,]\d++)?+(?:\s*+[½¼¾⅓⅔])?+|[½¼¾⅓⅔]"""
    private const val UNIT = """час(?:а|ов)?+|ч(?!\.?+\s*+/?+\s*+л)\.?+|hours?+|hrs?+\.?+|h|минут[аыу]?+|мин\.?+|minutes?+|mins?+\.?+"""
    private val DURATION = Regex(
        """(?<![\p{L}\p{N}])(?:(?:$NUMBER)(?:\s*+(?:[-–—]|до|to)\s*+(?:$NUMBER))?+\s*+(?:$UNIT)|полчаса|half\s++an\s++hour)(?!\p{L})""",
        RegexOption.IGNORE_CASE,
    )
    private val JOINS = Regex("""^\s*+(?:[-–—]|и|and|до|to)?+\s*+$""", RegexOption.IGNORE_CASE)
    private val EVERY = Regex("""(?:каждые|каждую|каждый|every)\s*+$""", RegexOption.IGNORE_CASE)

    fun count(text: String): Int {
        var count = 0
        var previousEnd = -1
        for (found in DURATION.findAll(text)) {
            val joined = previousEnd >= 0 && JOINS.matches(text.substring(previousEnd, found.range.first))
            val every = EVERY.containsMatchIn(text.substring(maxOf(0, found.range.first - 12), found.range.first))
            if (!joined && !every) count++
            previousEnd = found.range.last + 1
        }
        return count
    }
}
