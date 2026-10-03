package app.recipefork.core.dictionary

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import app.recipefork.core.search.WordPrefix
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** One source file of the dictionary: its [name] as messages show it, and its [text]. */
class SourceFile(val name: String, val text: String)

/** What a build gives: the dictionaries and the asset's exact text, or every refusal found. */
sealed interface BuildResult {
    class Built(val dictionaries: DictionariesDto, val text: String) : BuildResult
    class Refused(val errors: List<String>) : BuildResult
}

/**
 * The dictionary's sources (dictionary/README.md) turned into the shipped `dictionaries.json`.
 *
 * A source is tab-separated text, one entry per line; `#` starts a comment line, and `@range FIRST-LAST` declares an
 * id range the file may use. An ingredient line is `id, labelRu, labelEn, parentId, divisible, factors, removed`,
 * trailing empty columns left out; a tag line is `id, labelRu, labelEn`.
 */
object DictionaryBuild {
    /** The ranges an ingredient file may declare: produce, animal, pantry, seasoning and the rest, and the legacy ids. */
    val INGREDIENT_RANGES: List<LongRange> = listOf(1000L..2999L, 3000L..4999L, 5000L..6999L, 7000L..8999L, 1L..5L)

    /** The ranges the tags file may declare: the tags, and the legacy ids. */
    val TAG_RANGES: List<LongRange> = listOf(100L..999L, 10L..11L)

    private const val INGREDIENT_COLUMNS = 7
    private const val TAG_COLUMNS = 3

    /** The encoding of the version digest: the compact JSON the server and the application compute it over. */
    private val compact = Json

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** The lowercase hex SHA-256 of the compact encoding of [dictionaries] with an empty version. */
    fun versionOf(dictionaries: DictionariesDto): String {
        val content = compact.encodeToString(DictionariesDto.serializer(), dictionaries.copy(version = ""))
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Reads a shipped snapshot as the application does. */
    fun parseSnapshot(text: String): DictionariesDto =
        Json { ignoreUnknownKeys = true }.decodeFromString(DictionariesDto.serializer(), text)

    /**
     * Builds the dictionaries from [ingredientFiles] and [tagFile], checking them against [previous] — the snapshot
     * shipped so far, whose ids are permanent, named [previousName] in messages — when there is one.
     */
    fun build(
        ingredientFiles: List<SourceFile>,
        tagFile: SourceFile,
        previous: DictionariesDto?,
        previousName: String = "the previous snapshot",
    ): BuildResult {
        val errors = mutableListOf<String>()
        val ingredients = ingredientFiles.flatMap { parseFile(it, INGREDIENT_RANGES, INGREDIENT_COLUMNS, errors, ::ingredientRow) }
        val tags = parseFile(tagFile, TAG_RANGES, TAG_COLUMNS, errors, ::tagRow)

        uniqueIds(ingredients, errors)
        uniqueIds(tags, errors)
        uniqueLabels(ingredients.filterNot { it.value.removed }, errors)
        uniqueLabels(tags, errors)
        parents(ingredients, errors)
        if (previous != null) {
            kept(previousName, "ingredient", previous.ingredients.map { it.id }, ingredients, errors)
            kept(previousName, "tag", previous.tags.map { it.id }, tags, errors)
        }
        if (errors.isNotEmpty()) return BuildResult.Refused(errors)

        val unversioned = DictionariesDto(
            version = "",
            ingredients = ingredients.map { it.value }.sortedBy { it.id },
            tags = tags.map { it.value }.sortedBy { it.id },
        )
        val dictionaries = unversioned.copy(version = versionOf(unversioned))
        return BuildResult.Built(dictionaries, pretty.encodeToString(DictionariesDto.serializer(), dictionaries) + "\n")
    }

    /** A parsed line: where it stands, and what it says. */
    private class Row<T>(val at: String, val id: Long, val labels: List<String>, val value: T)

    private fun <T> parseFile(
        file: SourceFile,
        allowed: List<LongRange>,
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
                        range !in allowed -> errors += "$at: range ${range.first}-${range.last} is not one this kind of " +
                            "file may declare (${allowed.joinToString { "${it.first}-${it.last}" }})"
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
        if ("pc" in factors && divisible == null) {
            errors += "$at: a 'pc' factor needs the divisible flag ('true' or 'false')"
        }
        // A line with a bad cell is still held by its id and labels, so that the checks across lines see it too.
        if (id == null || !labelled) return null
        return Row(at, id, listOf(cells[1], cells[2]), IngredientEntryDto(id, cells[1], cells[2], parentId, divisible, factors, removed))
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
                // Folded and split as the search reads them: labels the search cannot tell apart are the same label.
                val earlier = first.putIfAbsent(WordPrefix.words(row.labels[language]), row)
                if (earlier != null) {
                    errors += "${row.at}: $name '${row.labels[language]}' is the same, as the search reads it, as " +
                        "'${earlier.labels[language]}' at ${earlier.at}"
                }
            }
        }
    }

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

    private fun kept(previousName: String, kind: String, shipped: List<Long>, rows: List<Row<*>>, errors: MutableList<String>) {
        val held = rows.map { it.id }.toSet()
        shipped.filterNot { it in held }.forEach {
            errors += "$previousName: $kind id $it was shipped and is missing from the sources " +
                "(ids are permanent; mark a dropped ingredient 'removed')"
        }
    }
}
