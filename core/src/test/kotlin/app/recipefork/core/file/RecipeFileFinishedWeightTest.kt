package app.recipefork.core.file

import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v0.44, T-318: the recipe file carries what the finished dish weighs as `finished_weight_grams`, in format 9, written
 * only by a document holding one; a file without it — of any earlier format — still reads, with none; a format before
 * 9 holding one is refused, and so is a format after 9.
 */
class RecipeFileFinishedWeightTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private val recipe = Recipe.create(now, null).copy(
        title = "Pilaf", language = RecipeLanguage.EN, servings = 2, steps = listOf(Step(0, "Simmer")),
    )

    private fun versionOf(document: String) = Json.parseToJsonElement(document).jsonObject["format_version"]!!.jsonPrimitive.int

    private fun recipeOf(document: String) = Json.parseToJsonElement(document).jsonObject["recipe"]!!.jsonObject

    /** [document] with its recipe's `finished_weight_grams` set to [grams], under [version]. */
    private fun withWeight(document: String, grams: Int, version: Int): String {
        val json = Json.parseToJsonElement(document).jsonObject
        val recipe = JsonObject(json["recipe"]!!.jsonObject + ("finished_weight_grams" to JsonPrimitive(grams)))
        return JsonObject(json + mapOf("format_version" to JsonPrimitive(version), "recipe" to recipe)).toString()
    }

    @Test
    fun `a finished weight is written as format 9 and reads back exactly`() {
        assertEquals(9, RecipeFile.FORMAT_VERSION)
        val content = RecipeFileContent.of(recipe.copy(finishedWeightGrams = 560), null, null)
        assertEquals(560, content.finishedWeightGrams)
        val written = RecipeFile.encode(content)
        assertEquals(9, versionOf(written))
        assertEquals(560, recipeOf(written)["finished_weight_grams"]!!.jsonPrimitive.int)
        val read = RecipeFile.decode(written)
        assertEquals(content, read)
        assertEquals(560, read.finishedWeightGrams)
        assertEquals(emptyList(), RecipeFileCheck.problems(read))
    }

    @Test
    fun `a finished weight beside a source text is still format 9, and each reads back`() {
        val content = RecipeFileContent.of(recipe.copy(finishedWeightGrams = 1_120).withSourceText("Pilaf\n\nRice 300 g"), null, null)
        val written = RecipeFile.encode(content)
        assertEquals(9, versionOf(written))
        assertEquals(content, RecipeFile.decode(written))
    }

    @Test
    fun `a recipe with no finished weight writes none and the format of before`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        assertEquals(RecipeFile.LAST_FORMAT_WITHOUT_US_UNITS, versionOf(written))
        assertFalse("finished_weight_grams" in recipeOf(written))
        assertNull(RecipeFile.decode(written).finishedWeightGrams)
        val withSourceText = RecipeFile.encode(RecipeFileContent.of(recipe.withSourceText("Pilaf"), null, null))
        assertEquals(8, versionOf(withSourceText))
        assertFalse("finished_weight_grams" in recipeOf(withSourceText))
    }

    @Test
    fun `a file of an earlier format, written without a finished weight, still reads with none`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        (1..8).forEach { version ->
            val earlier = written.replace("\"format_version\": 6", "\"format_version\": $version")
            assertTrue("\"format_version\": $version" in earlier)
            val read = RecipeFile.decode(earlier)
            assertNull(read.finishedWeightGrams)
            assertEquals("Pilaf", read.title)
            assertEquals(2, read.servings)
        }
    }

    @Test
    fun `a finished weight in a format before 9 is refused as no document of its format`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        listOf(1, 6, 8).forEach { version ->
            assertFailsWith<UnreadableRecipeFile>("format $version") { RecipeFile.decode(withWeight(written, 560, version)) }
        }
        assertEquals(560, RecipeFile.decode(withWeight(written, 560, 9)).finishedWeightGrams)
    }

    @Test
    fun `a format after 9 is refused as later`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe.copy(finishedWeightGrams = 560), null, null))
        val later = written.replace("\"format_version\": 9", "\"format_version\": 10")
        assertTrue("\"format_version\": 10" in later)
        assertEquals(10, assertFailsWith<LaterRecipeFile> { RecipeFile.decode(later) }.formatVersion)
    }

    @Test
    fun `a weight that is no whole number of grams does not parse`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe.copy(finishedWeightGrams = 560), null, null))
        listOf("560.5", "true", "10000000000").forEach { value ->
            val broken = written.replace("\"finished_weight_grams\": 560", "\"finished_weight_grams\": $value")
            assertTrue(value in broken)
            assertFailsWith<UnreadableRecipeFile>(value) { RecipeFile.decode(broken) }
        }
    }

    @Test
    fun `the check refuses a finished weight outside 1 to 100 000 grams`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        listOf(0, -5, 100_001).forEach { grams ->
            val read = RecipeFile.decode(withWeight(written, grams, 9))
            assertEquals(grams, read.finishedWeightGrams)
            assertTrue(RecipeFileCheck.problems(read).any { "finished weight" in it }, "$grams g")
        }
        listOf(1, 100_000).forEach { grams ->
            assertEquals(emptyList(), RecipeFileCheck.problems(RecipeFile.decode(withWeight(written, grams, 9))))
        }
    }
}
