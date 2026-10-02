package app.recipefork.core.file

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.fork
import app.recipefork.core.scaling.Rational
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** FLOW-063 (3, 5, 6) and decisions/20260928-recipe-file-is-a-zip.md: what `recipe.json` holds, and that it reads back. */
class RecipeFileTest {
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    private val ingredients = listOf(
        Ingredient(0, 101, qualifier = "ripe", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(250)),
        Ingredient(
            1, 102, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3, 2),
            pieceWeight = Rational.of(120), divisible = true,
        ),
        Ingredient(2, 106, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(2), divisible = false),
        Ingredient(3, null, freeTextName = "smoked salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(1, 2)),
        Ingredient(4, 104, unitType = UnitType.TASTE_MEASURED, unit = QuantityUnit.PINCH, quantity = Rational.of(2)),
        Ingredient(5, 105, qualifier = "black", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        // A row still being written keeps what was typed (FLOW-024 (6)).
        Ingredient(6, null, freeTextName = "cream", unitType = UnitType.EXACT, unit = QuantityUnit.MILLILITRE, quantity = null, enteredQuantity = "-3"),
    )
    private val steps = listOf(
        Step(0, "Fry the onion until golden", listOf(IngredientReference(0, 9)), Timer(12.minutes, "Onion")),
        Step(1, "Simmer with the salt", listOf(IngredientReference(3, 12), IngredientReference(1, 0)), Timer(95.minutes)),
        Step(2, "Season and serve"),
    )

    private fun recipe(title: String = "Борщ «домашний»") = Recipe.create(now, owner = null).copy(
        title = title,
        description = "Line one\nline \"two\"",
        tags = setOf(7, 3),
        language = RecipeLanguage.RU,
        servings = 4,
        ingredients = ingredients,
        steps = steps,
    )

    private fun parse(content: RecipeFileContent): JsonObject = Json.parseToJsonElement(RecipeFile.encode(content)).jsonObject

    // The file's content written and read back equals the original: references, timers and divisibility included.
    @Test
    fun aRecipeWrittenAndReadBackEqualsTheOriginal() {
        val original = recipe()
        val content = RecipeFileContent.of(original, parentInBook = null, ownerNickname = null)
        val read = RecipeFile.decode(RecipeFile.encode(content))
        assertEquals(content, read)
        assertEquals(original.id, read.id)
        assertEquals(original.title, read.title)
        assertEquals(original.description, read.description)
        assertEquals(original.tags, read.tags)
        assertEquals(original.language, read.language)
        assertEquals(original.servings, read.servings)
        assertEquals(original.ingredients, read.ingredients)
        assertEquals(original.steps, read.steps)
    }

    @Test
    fun aRecipeWithNothingDeclaredReadsBackToo() {
        val empty = Recipe.create(now, owner = null)
        val content = RecipeFileContent.of(empty, null, null)
        assertEquals(content, RecipeFile.decode(RecipeFile.encode(content)))
        val json = parse(content)["recipe"]!!.jsonObject
        assertFalse("language" in json)
        assertFalse("servings" in json)
    }

    @Test
    fun aParentAuthorAndMediaReadBack() {
        val content = RecipeFileContent.of(recipe(), null, null).copy(
            author = "anna",
            parent = FileParent(RecipeId("p-1"), "Soup", "boris"),
            media = listOf(
                FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/m-1.jpg"),
                FileMedia("m-2", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/m-2.mp4"),
            ),
        )
        assertEquals(content, RecipeFile.decode(RecipeFile.encode(content)))
    }

    // The document carries format_version 1 and the recipe's every field, in plain JSON a reader can check.
    @Test
    fun theDocumentCarriesItsVersionAndTheRecipe() {
        val original = recipe()
        val json = parse(RecipeFileContent.of(original, null, null))
        assertEquals(1, json["format_version"]!!.jsonPrimitive.int)
        val r = json["recipe"]!!.jsonObject
        assertEquals(original.id.value, r["id"]!!.jsonPrimitive.content)
        assertEquals("ru", r["language"]!!.jsonPrimitive.content)
        assertEquals(listOf(3, 7), r["tags"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(4, r["servings"]!!.jsonPrimitive.int)
        val piece = r["ingredients"]!!.jsonArray[1].jsonObject
        assertEquals("piece", piece["unit_type"]!!.jsonPrimitive.content)
        assertEquals("3/2", piece["quantity"]!!.jsonPrimitive.content)
        assertEquals("true", piece["divisible"]!!.jsonPrimitive.content)
        val free = r["ingredients"]!!.jsonArray[3].jsonObject
        assertEquals("smoked salt", free["free_text_name"]!!.jsonPrimitive.content)
        assertFalse("dictionary_entry" in free)
        val step = r["steps"]!!.jsonArray[1].jsonObject
        assertEquals(95, step["timer"]!!.jsonObject["minutes"]!!.jsonPrimitive.int)
        assertEquals(2, step["references"]!!.jsonArray.size)
        // The media list is there, empty while the device holds no media.
        assertEquals(0, json["media"]!!.jsonArray.size)
    }

    // FLOW-063 (3): a recipe written with no account active names no author.
    @Test
    fun aRecipeWrittenWithNoAccountNamesNoAuthor() {
        val content = RecipeFileContent.of(recipe(), null, ownerNickname = null)
        assertNull(content.author)
        assertFalse("author" in parse(content))
    }

    // FLOW-063: a found recipe from a file keeps the author the file stated.
    @Test
    fun aRecipeFromAFileKeepsItsStatedAuthor() {
        val imported = recipe().copy(statedAuthor = "anna")
        val json = parse(RecipeFileContent.of(imported, null, null))
        assertEquals("anna", json["author"]!!.jsonObject["nickname"]!!.jsonPrimitive.content)
    }

    // FLOW-063 (5): a recipe with no parent names none.
    @Test
    fun aRecipeWithNoParentNamesNone() {
        val content = RecipeFileContent.of(recipe(), null, null)
        assertNull(content.parent)
        assertFalse("parent" in parse(content))
    }

    // FLOW-063: the direct parent is named by its identity and, where the book holds it, its title.
    @Test
    fun aForkNamesItsParentWithTheTitleTheBookHolds() {
        val parent = recipe("Borscht")
        val fork = parent.fork(now, owner = null).copy(title = "Green borscht")
        val content = RecipeFileContent.of(fork, parentInBook = parent, ownerNickname = null)
        assertEquals(FileParent(parent.id, "Borscht", null), content.parent)
        val json = parse(content)["parent"]!!.jsonObject
        assertEquals(parent.id.value, json["id"]!!.jsonPrimitive.content)
        assertEquals("Borscht", json["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun aParentTheBookDoesNotHoldIsNamedByItsIdentityAlone() {
        val fork = recipe().copy(parent = RecipeId("gone"))
        assertEquals(FileParent(RecipeId("gone"), null, null), RecipeFileContent.of(fork, null, null).parent)
    }

    // FLOW-063 (6): a recipe imported from a file, exported again, names the parent the first file stated.
    @Test
    fun anImportExportedAgainNamesTheParentItsFileStated() {
        val imported = recipe().copy(parent = RecipeId("p-1"), statedParent = StatedParent("Soup", "boris"), statedAuthor = "anna")
        val content = RecipeFileContent.of(imported, null, null)
        assertEquals(FileParent(RecipeId("p-1"), "Soup", "boris"), content.parent)
        assertEquals("anna", content.author)
    }

    // decisions/20260928-recipe-file-is-a-zip.md: a later format, and a document that does not parse, are refused.
    @Test
    fun aLaterFormatIsRefused() {
        val later = RecipeFile.encode(RecipeFileContent.of(recipe(), null, null))
            .replace("\"format_version\": 1", "\"format_version\": 2")
        assertTrue("\"format_version\": 2" in later)
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(later) }
    }

    @Test
    fun aDocumentThatDoesNotParseIsRefused() {
        listOf(
            "",
            "not json",
            "{}",
            "{\"format_version\": 1}",
            RecipeFile.encode(RecipeFileContent.of(recipe(), null, null)).replace("\"gram\"", "\"furlong\""),
            RecipeFile.encode(RecipeFileContent.of(recipe(), null, null)).replace("\"3/2\"", "\"3/0\""),
        ).forEach { text -> assertFailsWith<UnreadableRecipeFile>(text) { RecipeFile.decode(text) } }
    }
}
