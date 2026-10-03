package app.recipefork.core.dictionary

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import app.recipefork.core.search.WordPrefix
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** One source file of the dictionary: its [name] as messages show it (its file name decides its family), and its [text]. */
class SourceFile(val name: String, val text: String)

/** The snapshot already shipped, named [name] in messages: its ids are permanent and keep naming what they named. */
class Baseline(val name: String, val dictionaries: DictionariesDto)

/** What a build gives: the dictionaries and the asset's exact text, or every refusal found. */
sealed interface BuildResult {
    class Built(val dictionaries: DictionariesDto, val text: String) : BuildResult
    class Refused(val errors: List<String>) : BuildResult
}

/**
 * A family of source files: those whose file name begins with [prefix]. They hold tags when [tags] is true, ingredients
 * otherwise, and may declare only the id [ranges].
 */
enum class Family(val prefix: String, val tags: Boolean, val ranges: List<LongRange>) {
    PRODUCE("produce", false, listOf(1000L..2999L)),
    ANIMAL("animal", false, listOf(3000L..4999L, 1L..5L)),
    PANTRY("pantry", false, listOf(5000L..6999L)),
    SEASONING("seasoning", false, listOf(7000L..8999L)),
    TAGS("tags", true, listOf(100L..999L, 10L..11L)),
    ;

    companion object {
        /** The family of the file [name] (a path or a bare file name), or null when its name begins with none. */
        fun of(name: String): Family? = name.substringAfterLast('/').let { file -> entries.firstOrNull { file.startsWith(it.prefix) } }
    }
}

/**
 * The dictionary's sources (dictionary/README.md) turned into the shipped `dictionaries.json`.
 *
 * A source is tab-separated text, one entry per line; `#` starts a comment line, and `@range FIRST-LAST` declares an
 * id range the file may use. An ingredient line is `id, labelRu, labelEn, parentId, divisible, factors, removed,
 * synonymsRu, synonymsEn` — each synonym list `;`-separated — trailing empty columns left out; a tag line is `id, labelRu, labelEn`.
 */
object DictionaryBuild {
    private const val INGREDIENT_COLUMNS = 9
    private const val TAG_COLUMNS = 3
    private const val SYNONYM_SEPARATOR = ';'

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Reads a shipped snapshot as the application does. */
    fun parseSnapshot(text: String): DictionariesDto =
        Json { ignoreUnknownKeys = true }.decodeFromString(DictionariesDto.serializer(), text)

    /**
     * Builds the dictionaries from [files], each read as its [Family] says, checking them against [baseline] — the
     * snapshot shipped so far — when there is one.
     */
    fun build(files: List<SourceFile>, baseline: Baseline?): BuildResult {
        val errors = mutableListOf<String>()
        val ingredients = mutableListOf<Row<IngredientEntryDto>>()
        val tags = mutableListOf<Row<TagDto>>()
        files.forEach { file ->
            val family = Family.of(file.name)
            when {
                family == null -> errors += "${file.name}:1: the file name begins with no family " +
                    "(${Family.entries.joinToString { it.prefix }})"
                family.tags -> tags += parseFile(file, family, TAG_COLUMNS, errors, ::tagRow)
                else -> ingredients += parseFile(file, family, INGREDIENT_COLUMNS, errors, ::ingredientRow)
            }
        }

        uniqueIds(ingredients, errors)
        uniqueIds(tags, errors)
        uniqueLabels(ingredients.filterNot { it.value.removed }, errors)
        uniqueLabels(tags, errors)
        synonyms(ingredients, errors)
        parents(ingredients, errors)
        if (baseline != null) {
            val shippedIngredients = baseline.dictionaries.ingredients.associate { it.id to listOf(it.labelRu, it.labelEn) }
            val shippedTags = baseline.dictionaries.tags.associate { it.id to listOf(it.labelRu, it.labelEn) }
            kept(baseline.name, "ingredient", shippedIngredients.keys, ingredients, errors)
            kept(baseline.name, "tag", shippedTags.keys, tags, errors)
            notReused(baseline.name, shippedIngredients, ingredients, errors)
            notReused(baseline.name, shippedTags, tags, errors)
        }
        if (errors.isNotEmpty()) return BuildResult.Refused(errors)

        val dictionaries = DictionariesDto(
            version = "",
            ingredients = ingredients.map { it.value }.sortedBy { it.id },
            tags = tags.map { it.value }.sortedBy { it.id },
        ).versioned()
        return BuildResult.Built(dictionaries, pretty.encodeToString(DictionariesDto.serializer(), dictionaries) + "\n")
    }

    /** A parsed line: where it stands, and what it says. */
    private class Row<T>(val at: String, val id: Long, val labels: List<String>, val value: T)

    private fun <T> parseFile(
        file: SourceFile,
        family: Family,
        columns: Int,
        errors: MutableList<String>,
        row: (at: String, cells: List<String>, errors: MutableList<String>) -> Row<T>?,
    ): List<Row<T>> {
        val lines = file.text.lines()
        val ranges = mutableListOf<LongRange>()
        val rows = mutableListOf<Row<T>>()
        lines.forEachIndexed { index, line ->
            val at = "${file.name}:${index + 1}"
            when {
                line.isBlank() || line.startsWith("#") -> Unit
                line.startsWith("@") -> {
                    val range = Regex("""@range\s+(\d+)-(\d+)\s*""").matchEntire(line)
                        ?.let { it.groupValues[1].toLong()..it.groupValues[2].toLong() }
                    when {
                        range == null -> errors += "$at: unknown directive '$line' (expected '@range FIRST-LAST')"
                        range !in family.ranges -> errors += "$at: range ${range.first}-${range.last} is not one a " +
                            "'${family.prefix}' file may declare (${family.ranges.joinToString { "${it.first}-${it.last}" }})"
                        else -> ranges += range
                    }
                }
                else -> {
                    val cells = line.split('\t')
                    if (cells.size > columns) {
                        errors += "$at: ${cells.size} columns, at most $columns expected"
                    } else {
                        row(at, cells + List(columns - cells.size) { "" }, errors)?.let { rows += it }
                    }
                }
            }
        }
        if (lines.none { it.startsWith("@range") }) {
            errors += "${file.name}:1: the file declares no id range ('@range FIRST-LAST')"
        } else if (ranges.isNotEmpty()) {
            rows.filter { row -> ranges.none { row.id in it } }.forEach { row ->
                errors += "${row.at}: id ${row.id} is outside the file's range " +
                    ranges.joinToString { "${it.first}-${it.last}" }
            }
        }
        return rows
    }

    private fun id(at: String, cell: String, errors: MutableList<String>): Long? =
        cell.toLongOrNull()?.takeIf { it > 0 } ?: null.also { errors += "$at: id '$cell' is not a positive whole number" }

    private fun labels(at: String, cells: List<String>, errors: MutableList<String>): Boolean {
        var good = true
        listOf("labelRu" to cells[1], "labelEn" to cells[2]).forEach { (name, label) ->
            when {
                WordPrefix.words(label).isEmpty() -> {
                    errors += "$at: $name is blank"
                    good = false
                }
                label != label.trim() -> {
                    errors += "$at: $name '$label' has spaces around it"
                    good = false
                }
            }
        }
        return good
    }

    private fun tagRow(at: String, cells: List<String>, errors: MutableList<String>): Row<TagDto>? {
        val id = id(at, cells[0], errors)
        val labelled = labels(at, cells, errors)
        if (id == null || !labelled) return null
        return Row(at, id, listOf(cells[1], cells[2]), TagDto(id, cells[1], cells[2]))
    }

    private fun ingredientRow(at: String, cells: List<String>, errors: MutableList<String>): Row<IngredientEntryDto>? {
        val id = id(at, cells[0], errors)
        val labelled = labels(at, cells, errors)
        val parentId = cells[3].takeIf { it.isNotEmpty() }?.let { cell ->
            cell.toLongOrNull() ?: null.also { errors += "$at: parentId '$cell' is not a whole number" }
        }
        val divisible = when (cells[4]) {
            "" -> null
            "true" -> true
            "false" -> false
            else -> null.also { errors += "$at: divisible '${cells[4]}' is neither 'true', 'false' nor empty" }
        }
        val factors = factors(at, cells[5], errors)
        val removed = when (cells[6]) {
            "" -> false
            "removed" -> true
            else -> false.also { errors += "$at: '${cells[6]}' in the removed column (expected 'removed' or empty)" }
        }
        val synonymsRu = synonyms(at, "synonymsRu", cells[7], errors)
        val synonymsEn = synonyms(at, "synonymsEn", cells[8], errors)
        if ("pc" in factors && divisible == null) {
            errors += "$at: a 'pc' factor needs the divisible flag ('true' or 'false')"
        }
        // A line with a bad cell is still held by its id and labels, so that the checks across lines see it too.
        if (id == null || !labelled) return null
        return Row(at, id, listOf(cells[1], cells[2]), IngredientEntryDto(id, cells[1], cells[2], parentId, divisible, factors, removed, synonymsRu, synonymsEn))
    }

    /** The `;`-separated synonyms of [cell]; each must hold a letter or digit and have no spaces around it. */
    private fun synonyms(at: String, name: String, cell: String, errors: MutableList<String>): List<String> {
        if (cell.isEmpty()) return emptyList()
        return cell.split(SYNONYM_SEPARATOR).filter { synonym ->
            when {
                WordPrefix.words(synonym).isEmpty() -> false.also { errors += "$at: $name holds a blank synonym" }
                synonym != synonym.trim() -> false.also { errors += "$at: $name '$synonym' has spaces around it" }
                else -> true
            }
        }
    }

    private fun factors(at: String, cell: String, errors: MutableList<String>): Map<String, Double> {
        val factors = linkedMapOf<String, Double>()
        cell.split(' ').filter { it.isNotEmpty() }.forEach { pair ->
            val unit = pair.substringBefore('=', "")
            val number = pair.substringAfter('=', "").toDoubleOrNull()
            when {
                unit.isEmpty() || number == null -> errors += "$at: factor '$pair' is not 'unit=number'"
                unit !in IngredientEntryDto.FACTOR_UNITS ->
                    errors += "$at: unknown factor unit '$unit' (known: ${IngredientEntryDto.FACTOR_UNITS.joinToString()})"
                !number.isFinite() || number <= 0 -> errors += "$at: factor $unit=$number is not a finite number above zero"
                unit in factors -> errors += "$at: factor unit '$unit' given twice"
                else -> factors[unit] = number
            }
        }
        return factors
    }

    private fun uniqueIds(rows: List<Row<*>>, errors: MutableList<String>) {
        val first = HashMap<Long, Row<*>>()
        rows.forEach { row ->
            val earlier = first.putIfAbsent(row.id, row)
            if (earlier != null) errors += "${row.at}: duplicate id ${row.id} (also at ${earlier.at})"
        }
    }

    private fun uniqueLabels(rows: List<Row<*>>, errors: MutableList<String>) {
        listOf("labelRu", "labelEn").forEachIndexed { language, name ->
            val first = HashMap<List<String>, Row<*>>()
            rows.forEach { row ->
                // Folded and split as the search reads them, in any order as its exact match takes them: labels the
                // search cannot tell apart are the same label ("масло сливочное" and "сливочное масло", "creme" and "crème").
                val earlier = first.putIfAbsent(WordPrefix.words(row.labels[language]).sorted(), row)
                if (earlier != null) {
                    errors += "${row.at}: $name '${row.labels[language]}' is the same, as the search reads it, as " +
                        "'${earlier.labels[language]}' at ${earlier.at}"
                }
            }
        }
    }

    /**
     * A synonym finds its entry, so it must find that entry alone: as the search folds it, it is neither its own entry's
     * label, nor any live entry's label in its language, nor another synonym in its language. A removed entry is offered to nobody, so
     * its synonyms are checked against its own label only.
     */
    private fun synonyms(rows: List<Row<IngredientEntryDto>>, errors: MutableList<String>) {
        val live = rows.filterNot { it.value.removed }
        listOf("synonymsRu", "synonymsEn").forEachIndexed { language, name ->
            val labels = HashMap<List<String>, Row<*>>()
            live.forEach { labels.putIfAbsent(folded(it.labels[language]), it) }
            val synonyms = HashMap<List<String>, Pair<Row<*>, String>>()
            rows.forEach { row ->
                val own = folded(row.labels[language])
                val list = if (language == 0) row.value.synonymsRu else row.value.synonymsEn
                list.forEach synonym@{ synonym ->
                    val key = folded(synonym)
                    if (key == own) {
                        errors += "${row.at}: $name '$synonym' is the entry's own label as the search reads it"
                        return@synonym
                    }
                    if (row.value.removed) return@synonym
                    val label = labels[key]
                    val earlier = synonyms.putIfAbsent(key, row to synonym)
                    when {
                        label != null -> errors += "${row.at}: $name '$synonym' is the same, as the search reads it, " +
                            "as the label '${label.labels[language]}' at ${label.at}"
                        earlier != null -> errors += "${row.at}: $name '$synonym' is the same, as the search reads it, " +
                            "as the synonym '${earlier.second}' at ${earlier.first.at}"
                    }
                }
            }
        }
    }

    /** A label or synonym folded and split as the search reads it, its words in any order. */
    private fun folded(text: String): List<String> = WordPrefix.words(text).sorted()

    private fun parents(rows: List<Row<IngredientEntryDto>>, errors: MutableList<String>) {
        val byId = rows.associateBy { it.id }
        rows.forEach { row ->
            val parentId = row.value.parentId ?: return@forEach
            val parent = byId[parentId]
            when {
                parent == null -> errors += "${row.at}: parentId $parentId names no entry"
                parent.id == row.id -> errors += "${row.at}: the entry names itself as its parent"
                parent.value.parentId != null -> errors += "${row.at}: parentId $parentId is not a top-level entry " +
                    "(${parent.at} has parent ${parent.value.parentId}); entries are two levels at most"
                parent.value.removed && !row.value.removed -> errors += "${parent.at}: removed entry ${parent.id} is " +
                    "still the parent of live entry ${row.id} at ${row.at}"
            }
        }
    }

    private fun notReused(
        baselineName: String,
        shipped: Map<Long, List<String>>,
        rows: List<Row<*>>,
        errors: MutableList<String>,
    ) {
        rows.forEach { row ->
            val before = shipped[row.id] ?: return@forEach
            // One label corrected is the same thing named better; both changed is another thing under an old id.
            val changed = before.indices.all { WordPrefix.words(before[it]) != WordPrefix.words(row.labels[it]) }
            if (changed) {
                errors += "${row.at}: id ${row.id} was shipped as '${before[0]}' / '${before[1]}' ($baselineName); " +
                    "both labels changed, so the id would name something else — give the new entry a new id"
            }
        }
    }

    private fun kept(previousName: String, kind: String, shipped: Set<Long>, rows: List<Row<*>>, errors: MutableList<String>) {
        val held = rows.map { it.id }.toSet()
        shipped.filterNot { it in held }.forEach {
            errors += "$previousName: $kind id $it was shipped and is missing from the sources " +
                "(ids are permanent; mark a dropped ingredient 'removed')"
        }
    }
}
