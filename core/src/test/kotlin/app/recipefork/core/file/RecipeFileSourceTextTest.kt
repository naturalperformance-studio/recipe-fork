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
 * v0.39, T-279: the recipe file carries the text a recipe was made from as `source_text`, in format 8, written only by a
 * document holding one; a file without it — of any earlier format — still reads, with none.
 */
class RecipeFileSourceTextTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val pasted = "Pancakes\n\nFlour — 200 g\r\n  Milk 500 ml\t\"warm\"\n\nMix. Fry 2 min. 🥞\n"

    private val recipe = Recipe.create(now, null).copy(title = "Pancakes", language = RecipeLanguage.EN, steps = listOf(Step(0, "Mix")))

    private fun versionOf(document: String) = Json.parseToJsonElement(document).jsonObject["format_version"]!!.jsonPrimitive.int

    private fun recipeOf(document: String) = Json.parseToJsonElement(document).jsonObject["recipe"]!!.jsonObject

    /** [document] with its recipe's `source_text` set to [text], under [version]. */
    private fun withSourceText(document: String, text: String, version: Int): String {
        val json = Json.parseToJsonElement(document).jsonObject
        val recipe = JsonObject(json["recipe"]!!.jsonObject + ("source_text" to JsonPrimitive(text)))
        return JsonObject(json + mapOf("format_version" to JsonPrimitive(version), "recipe" to recipe)).toString()
    }

    @Test
    fun `a source text is written as format 8 and reads back exactly`() {
        val content = RecipeFileContent.of(recipe.withSourceText(pasted), null, null)
        assertEquals(pasted, content.sourceText)
        val written = RecipeFile.encode(content)
        assertEquals(8, versionOf(written))
        assertEquals(pasted, recipeOf(written)["source_text"]!!.jsonPrimitive.content)
        val read = RecipeFile.decode(written)
        assertEquals(content, read)
        assertEquals(pasted, read.sourceText)
        assertEquals(emptyList(), RecipeFileCheck.problems(read))
    }

    @Test
    fun `a recipe with no source text writes none and the format of before`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        assertEquals(RecipeFile.LAST_FORMAT_WITHOUT_US_UNITS, versionOf(written))
        assertFalse("source_text" in recipeOf(written))
        assertNull(RecipeFile.decode(written).sourceText)
    }

    @Test
    fun `a file of an earlier format, written without a source text, still reads with none`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        listOf(6, 7).forEach { version ->
            val earlier = written.replace("\"format_version\": 6", "\"format_version\": $version")
            assertTrue("\"format_version\": $version" in earlier)
            val read = RecipeFile.decode(earlier)
            assertNull(read.sourceText)
            assertEquals("Pancakes", read.title)
        }
    }

    @Test
    fun `a source text in a format before 8 is refused as no document of its format`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(withSourceText(written, pasted, 7)) }
    }

    @Test
    fun `a longer source text in a file is read cut at the limit, and an empty one as none`() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        val read = RecipeFile.decode(withSourceText(written, "z".repeat(30_000), 8))
        assertEquals("z".repeat(20_000), read.sourceText)
        assertEquals(emptyList(), RecipeFileCheck.problems(read))
        assertNull(RecipeFile.decode(withSourceText(written, "", 8)).sourceText)
    }

    @Test
    fun `the check refuses content holding a source text over the limit`() {
        val content = RecipeFileContent.of(recipe, null, null).copy(sourceText = "y".repeat(20_001))
        assertTrue(RecipeFileCheck.problems(content).any { "source text" in it })
    }
}
