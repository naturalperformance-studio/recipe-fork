package app.recipefork.core.dictionary

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The shipped `dictionaries.json` is what the sources under `dictionary/` build to, byte for byte, so it is never
 * edited by hand: a change goes into the sources and `scripts/dictionary-build.sh` (dictionary/README.md).
 */
class ShippedDictionaryTest {
    /** Unit tests of `:core` run with the module directory as the working directory. */
    private val root = File("..")
    private val sources = File(root, "dictionary")
    private val asset = File(root, "app/src/main/assets/dictionaries.json")

    private fun built(): BuildResult.Built = when (val result = buildFrom(sources, asset, root)) {
        is BuildResult.Built -> result
        is BuildResult.Refused -> fail("the sources are refused:\n" + result.errors.joinToString("\n"))
    }

    @Test
    fun theShippedAssetIsExactlyWhatTheSourcesBuild() {
        assertEquals(
            asset.readText(Charsets.UTF_8),
            built().text,
            "app/src/main/assets/dictionaries.json differs from the sources; run scripts/dictionary-build.sh",
        )
        assertTrue(asset.readBytes().contentEquals(built().text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun theVersionIsTheDigestOfTheContent() {
        val dictionaries = built().dictionaries
        assertEquals(DictionaryBuild.versionOf(dictionaries), dictionaries.version)
    }

    @Test
    fun theEntriesShippedBeforeTheSourcesKeepTheirIdsLabelsFlagsAndFactors() {
        val dictionaries = built().dictionaries
        val legacy = dictionaries.ingredients.filter { it.id <= 5 }
        assertEquals(
            listOf(
                IngredientEntryDto(1, "курица", "chicken", null, true, mapOf("g" to 1.0, "pc" to 1500.0)),
                IngredientEntryDto(2, "крылышки", "chicken wings", 1, false, mapOf("g" to 1.0, "pc" to 80.0)),
                IngredientEntryDto(3, "мясо", "meat", factors = mapOf("g" to 1.0)),
                IngredientEntryDto(4, "фарш", "mince", factors = mapOf("g" to 1.0)),
                IngredientEntryDto(5, "говядина", "beef", factors = mapOf("g" to 1.0)),
            ),
            legacy,
        )
        assertEquals(
            listOf(TagDto(10, "итальянская", "Italian"), TagDto(11, "грузинская", "Georgian")),
            dictionaries.tags.filter { it.id <= 11 },
        )
    }

    @Test
    fun eachFamilyFileDeclaresItsRange() {
        val declared = mapOf(
            "produce.tsv" to listOf("@range 1000-2999"),
            "animal.tsv" to listOf("@range 3000-4999", "@range 1-5"),
            "pantry.tsv" to listOf("@range 5000-6999"),
            "seasoning-and-other.tsv" to listOf("@range 7000-8999"),
            "tags.tsv" to listOf("@range 100-999", "@range 10-11"),
        )
        declared.forEach { (name, ranges) ->
            val lines = File(sources, name).readLines(Charsets.UTF_8)
            assertEquals(ranges, lines.filter { it.startsWith("@range") }.map { it.trim() }, name)
        }
    }

    @Test
    fun theReadmeDocumentsTheGeneratorTheRangesAndTheContentRules() {
        val readme = File(sources, "README.md").readText(Charsets.UTF_8)
        listOf(
            "scripts/dictionary-build.sh", "1000–2999", "3000–4999", "5000–6999", "7000–8999", "100–999",
            "**Labels.**", "**Families, two levels.**", "**What a piece is.**", "**Divisibility.**",
            "**Factors, one scale.**", "**Ids.**", "**No brands**",
        ).forEach { assertTrue(it in readme, "README.md lacks $it") }
    }
}
