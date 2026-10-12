package app.recipefork.core.file

import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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

    // The document carries format_version 6 and the recipe's every field, in plain JSON a reader can check.
    @Test
    fun theDocumentCarriesItsVersionAndTheRecipe() {
        val original = recipe().copy(equipment = listOf(EquipmentItem.free("Pot")))
        val json = parse(RecipeFileContent.of(original, null, null))
        assertEquals(6, json["format_version"]!!.jsonPrimitive.int)
        val r = json["recipe"]!!.jsonObject
        assertEquals(original.id.value, r["id"]!!.jsonPrimitive.content)
        assertEquals("ru", r["language"]!!.jsonPrimitive.content)
        assertEquals(listOf(3, 7), r["tags"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(4, r["servings"]!!.jsonPrimitive.int)
        assertEquals(listOf("Pot"), r["equipment"]!!.jsonArray.map { it.jsonObject["free_text_name"]!!.jsonPrimitive.content })
        val piece = r["ingredients"]!!.jsonArray[1].jsonObject
        assertEquals("piece", piece["unit_type"]!!.jsonPrimitive.content)
        assertEquals("3/2", piece["quantity"]!!.jsonPrimitive.content)
        assertEquals("true", piece["divisible"]!!.jsonPrimitive.content)
        val free = r["ingredients"]!!.jsonArray[3].jsonObject
        assertEquals("smoked salt", free["free_text_name"]!!.jsonPrimitive.content)
        assertFalse("dictionary_entry" in free)
        val step = r["steps"]!!.jsonArray[1].jsonObject
        assertEquals(95, step["timers"]!!.jsonArray.single().jsonObject["minutes"]!!.jsonPrimitive.int)
        assertFalse("timer" in step)
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

    // FLOW-063 (6): a found recipe whose file named a parent the book also holds still names it as its file stated.
    @Test
    fun aFoundRecipeNamesTheParentItsFileStatedEvenWhenTheBookHoldsOne() {
        val held = recipe("Soup, edited since").copy(id = RecipeId("p-1"))
        val imported = recipe().copy(parent = RecipeId("p-1"), statedParent = StatedParent("Soup", "boris"), statedAuthor = "anna")
        assertEquals(FileParent(RecipeId("p-1"), "Soup", "boris"), RecipeFileContent.of(imported, held, null).parent)
    }

    // FLOW-065 (6): a fork of a found recipe names it as its parent, with its title and stated author, held or not.
    @Test
    fun aForkOfAFoundRecipeNamesItAsItsParent() {
        val found = recipe("Bean soup").copy(statedAuthor = "anna", parent = RecipeId("p-1"), statedParent = StatedParent("Soup", "boris"))
        val fork = found.fork(now, owner = null, byAuthor = false).copy(title = "Green bean soup")
        val expected = FileParent(found.id, "Bean soup", "anna")
        assertEquals(expected, RecipeFileContent.of(fork, parentInBook = found, ownerNickname = null).parent)
        assertEquals(expected, RecipeFileContent.of(fork, parentInBook = null, ownerNickname = null).parent)
        assertNull(RecipeFileContent.of(fork, found, null).author)
        val json = parse(RecipeFileContent.of(fork, null, null))["parent"]!!.jsonObject
        assertEquals(found.id.value, json["id"]!!.jsonPrimitive.content)
        assertEquals("Bean soup", json["title"]!!.jsonPrimitive.content)
        assertEquals("anna", json["nickname"]!!.jsonPrimitive.content)
    }

    // decisions/20260928-recipe-file-is-a-zip.md: a later format, and a document that does not parse, are refused.
    @Test
    fun aLaterFormatIsRefused() {
        val later = RecipeFile.encode(RecipeFileContent.of(recipe(), null, null))
            .replace("\"format_version\": 6", "\"format_version\": 8")
        assertTrue("\"format_version\": 8" in later)
        assertEquals(8, assertFailsWith<LaterRecipeFile> { RecipeFile.decode(later) }.formatVersion)
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

    // A step's part of an ingredient is carried in the document — an exact fraction in a unit named as a row's is —
    // and reads back; the format is version 6. A part in a unit the format does not name does not parse.
    @Test
    fun aPartOfAnIngredientIsWrittenAndReadBack() {
        val parted = recipe().copy(
            steps = listOf(
                Step(0, "Fry the onion until golden", listOf(IngredientReference(0, 9, IngredientShare(Rational.of(100), QuantityUnit.GRAM))), Timer(12.minutes)),
                Step(1, "Simmer with the salt", listOf(IngredientReference(3, 12), IngredientReference(1, 0, IngredientShare(Rational.of(1, 2), QuantityUnit.PIECE)))),
            ),
        )
        val content = RecipeFileContent.of(parted, null, null)
        val written = RecipeFile.encode(content)
        assertEquals(content, RecipeFile.decode(written))
        val references = parse(content)["recipe"]!!.jsonObject["steps"]!!.jsonArray[1].jsonObject["references"]!!.jsonArray
        assertNull(references[0].jsonObject["share"])
        val share = references[1].jsonObject["share"]!!.jsonObject
        assertEquals("1/2", share["quantity"]!!.jsonPrimitive.content)
        assertEquals("piece", share["unit"]!!.jsonPrimitive.content)
        assertEquals(6, parse(content)["format_version"]!!.jsonPrimitive.int)
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(written.replace("\"piece\"\n", "\"furlong\"\n")) }
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(written.replace("\"1/2\"", "\"1/0\"")) }
    }

    // DOM-022, DOM-023: a recipe's groups and notes are carried in the document and read back whole.
    @Test
    fun groupsAndNotesAreWrittenAndReadBack() {
        val grouped = recipe().copy(
            ingredients = ingredients.map {
                when (it.position) {
                    0 -> it.copy(note = "finely chopped")
                    3 -> it.copy(note = "room temperature")
                    else -> it
                }
            },
            ingredientGroups = listOf(IngredientGroup("Dough", 1), IngredientGroup("Glaze", 4)),
        )
        val content = RecipeFileContent.of(grouped, null, null)
        assertEquals(grouped.ingredientGroups, content.ingredientGroups)
        assertEquals(grouped.ingredients, content.ingredients)
        val read = RecipeFile.decode(RecipeFile.encode(content))
        assertEquals(content, read)
        assertEquals(grouped.ingredientGroups, read.ingredientGroups)
        assertEquals("finely chopped", read.ingredients[0].note)
        assertEquals("room temperature", read.ingredients[3].note)
        val r = parse(content)["recipe"]!!.jsonObject
        val groups = r["ingredient_groups"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("Dough", "Glaze"), groups.map { it["name"]!!.jsonPrimitive.content })
        assertEquals(listOf(1, 4), groups.map { it["first_ingredient"]!!.jsonPrimitive.int })
        assertEquals("finely chopped", r["ingredients"]!!.jsonArray[0].jsonObject["note"]!!.jsonPrimitive.content)
        assertFalse("note" in r["ingredients"]!!.jsonArray[1].jsonObject)
    }

    // DOM-023: a note is read as the editor takes one, trimmed, and a blank one is none.
    @Test
    fun aNoteIsReadTrimmedAndABlankOneIsNone() {
        val noted = recipe().copy(ingredients = ingredients.map { if (it.position == 0) it.copy(note = "@@") else it })
        val written = RecipeFile.encode(RecipeFileContent.of(noted, null, null))
        assertEquals("chopped", RecipeFile.decode(written.replace("\"@@\"", "\"  chopped \"")).ingredients[0].note)
        assertNull(RecipeFile.decode(written.replace("\"@@\"", "\"   \"")).ingredients[0].note)
    }

    // DOM-022, DOM-023: a document of format 1 — before groups and notes — still reads, with neither.
    @Test
    fun aFormat1DocumentReadsWithNoGroupsAndNoNotes() {
        val content = RecipeFileContent.of(recipe(), null, null)
        val format1 = oneTimerFormat(RecipeFile.encode(content), 1)
            .replace(Regex(",\\s*\"ingredient_groups\": \\[\\]"), "")
        assertFalse("ingredient_groups" in format1)
        assertTrue("\"format_version\": 1" in format1)
        val read = RecipeFile.decode(format1)
        assertEquals(content, read)
        assertEquals(emptyList(), read.ingredientGroups)
        assertTrue(read.ingredients.all { it.note == null })
    }

    // DOM-024: a step's timers are written as a list, in order, each with its own duration and name, and read back so.
    @Test
    fun severalTimersOfAStepAreWrittenAndReadBackInOrder() {
        val timers = listOf(Timer(10.minutes, "Pasta"), Timer(6.minutes), Timer(2.minutes, "Garlic"), Timer(1.minutes), Timer((99 * 60 + 59).minutes))
        val several = recipe().copy(steps = listOf(steps[0].copy(timers = timers), steps[1], steps[2]))
        val content = RecipeFileContent.of(several, null, null)
        assertEquals(timers, content.steps[0].timers)
        val read = RecipeFile.decode(RecipeFile.encode(content))
        assertEquals(content, read)
        assertEquals(timers, read.steps[0].timers)
        val written = parse(content)["recipe"]!!.jsonObject["steps"]!!.jsonArray.map { it.jsonObject["timers"]!!.jsonArray.map { t -> t.jsonObject } }
        assertEquals(listOf(10, 6, 2, 1, 5999), written[0].map { it["minutes"]!!.jsonPrimitive.int })
        assertEquals(listOf("Pasta", null, "Garlic", null, null), written[0].map { it["label"]?.jsonPrimitive?.content })
        assertEquals(listOf(1, 0), written.drop(1).map { it.size })
    }

    // DOM-024: a document of format 2, whose step carries one `timer`, reads as a step with that one timer; one with
    // none reads with none.
    @Test
    fun aFormat2DocumentReadsEachTimerAsTheStepsOnlyTimer() {
        val content = RecipeFileContent.of(recipe(), null, null)
        val format2 = oneTimerFormat(RecipeFile.encode(content), 2)
        assertTrue("\"format_version\": 2" in format2 && "\"timer\"" in format2 && "\"timers\"" !in format2)
        val read = RecipeFile.decode(format2)
        assertEquals(content, read)
        assertEquals(listOf(listOf(Timer(12.minutes, "Onion")), listOf(Timer(95.minutes)), emptyList()), read.steps.map { it.timers })
    }

    // A timer stands as its format writes it: a list of `timers` since format 3 and none in format 2, one `timer` in
    // format 2 and none since format 3. Anything else is no document of its format.
    @Test
    fun aTimerInAnotherFormatsShapeIsRefused() {
        val content = RecipeFileContent.of(recipe(), null, null)
        val format5 = RecipeFile.encode(content)
        val format5WithOneTimer = format5.replace("\"timers\": [", "\"timer\": {\"minutes\": 5},\n\"timers\": [")
        assertTrue("\"timer\": {" in format5WithOneTimer)
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(format5WithOneTimer) }
        val format2WithTimers = format5.replace("\"format_version\": 6", "\"format_version\": 2")
        assertTrue("\"format_version\": 2" in format2WithTimers)
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(format2WithTimers) }
        // A format-2 step whose list is empty holds no timer of format 3's shape, so it reads.
        val format2 = oneTimerFormat(format5, 2)
        val emptyList = format2.replace("\"text\": \"Season and serve\"", "\"text\": \"Season and serve\", \"timers\": []")
        assertTrue("\"timers\": []" in emptyList)
        assertEquals(content, RecipeFile.decode(emptyList))
    }

    // Each item is written as an ingredient's name is — an entry with its labels and its qualifier, or free text — in
    // order, and read back so; a recipe with none writes an empty list.
    @Test
    fun theEquipmentIsWrittenAndReadBackInOrder() {
        val equipped = recipe().copy(
            equipment = listOf(EquipmentItem.entry(10202), EquipmentItem.entry(10001, "26 cm"), EquipmentItem.free("Grandma's whisk"), EquipmentItem.entry(10999)),
        )
        val labels = mapOf(10202L to EntryLabels("Духовка", "Oven"), 10001L to EntryLabels("Сковорода", "Frying pan"))
        val content = RecipeFileContent.of(equipped, null, null, labels::get)
        assertEquals(equipped.equipment, content.equipment)
        // An entry whose labels are not known is written without them.
        assertEquals(labels, content.equipmentLabels)
        val read = RecipeFile.decode(RecipeFile.encode(content))
        assertEquals(content, read)
        val written = parse(content)["recipe"]!!.jsonObject["equipment"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("10202", "10001", null, "10999"), written.map { it["dictionary_entry"]?.jsonPrimitive?.content })
        assertEquals(listOf("Oven", "Frying pan", null, null), written.map { it["labels"]?.jsonObject?.get("en")?.jsonPrimitive?.content })
        assertEquals(listOf("Духовка", "Сковорода", null, null), written.map { it["labels"]?.jsonObject?.get("ru")?.jsonPrimitive?.content })
        assertEquals(listOf(null, "26 cm", null, null), written.map { it["qualifier"]?.jsonPrimitive?.content })
        assertEquals(listOf(null, null, "Grandma's whisk", null), written.map { it["free_text_name"]?.jsonPrimitive?.content })
        assertEquals(0, parse(RecipeFileContent.of(recipe(), null, null))["recipe"]!!.jsonObject["equipment"]!!.jsonArray.size)
    }

    // Format 5 names its items itself: free text stays free text, whatever entry its text might name.
    @Test
    fun aFormat5ItemIsReadAsItIsNamed() {
        val content = RecipeFileContent.of(recipe().copy(equipment = listOf(EquipmentItem.free("Oven"))), null, null)
        val read = RecipeFile.decode(RecipeFile.encode(content)) { 10202 }
        assertEquals(listOf(EquipmentItem.free("Oven")), read.equipment)
    }

    // Each item of format 4 is its text, read trimmed: the entry the matcher finds for the whole text, with no
    // qualifier, or free text; an item that is no text does not parse — nor does a format-5 item that is.
    @Test
    fun aFormat4ItemIsItsTextAndFindsItsEntry() {
        val format4 = textFormat(RecipeFile.encode(RecipeFileContent.of(recipe(), null, null)), listOf("  Oven ", "Frying pan 26 cm"))
        assertTrue("\"format_version\": 4" in format4 && "\"  Oven \"" in format4)
        val asked = mutableListOf<String>()
        val read = RecipeFile.decode(format4) { text -> asked += text; 10202L.takeIf { text == "Oven" } }
        assertEquals(listOf("Oven", "Frying pan 26 cm"), asked)
        assertEquals(listOf(EquipmentItem.entry(10202), EquipmentItem.free("Frying pan 26 cm")), read.equipment)
        assertEquals(emptyList(), RecipeFileCheck.problems(read))
        assertEquals(listOf(EquipmentItem.free("Oven"), EquipmentItem.free("Frying pan 26 cm")), RecipeFile.decode(format4).equipment)

        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(format4.replace("\"  Oven \"", "{\"free_text_name\": \"Oven\"}")) }
        val format5WithText = textFormat(RecipeFile.encode(RecipeFileContent.of(recipe(), null, null)), listOf("Oven"), version = 5)
        assertTrue("\"format_version\": 5" in format5WithText && "\"Oven\"" in format5WithText)
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(format5WithText) }
    }

    // An item is read trimmed, as a note is; one blank or long once trimmed is kept for the check, not the reader, to refuse.
    @Test
    fun anItemIsReadTrimmedAndTheCheckRefusesABlankOrLongOne() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe().copy(equipment = listOf(EquipmentItem.free("@@"), EquipmentItem.free("Pan"))), null, null))
        val untrimmed = RecipeFile.decode(written.replace("\"@@\"", "\"  Oven \\t\""))
        assertEquals(listOf(EquipmentItem.free("Oven"), EquipmentItem.free("Pan")), untrimmed.equipment)
        assertEquals(emptyList<String>(), equipmentProblems(untrimmed))
        val blank = RecipeFile.decode(written.replace("\"@@\"", "\"   \""))
        assertEquals(listOf(EquipmentItem.free(""), EquipmentItem.free("Pan")), blank.equipment)
        assertEquals(listOf("equipment 0 is blank or untrimmed"), equipmentProblems(blank))
        val long = RecipeFile.decode(written.replace("\"@@\"", "\" ${"p".repeat(41)} \""))
        assertEquals(listOf(EquipmentItem.free("p".repeat(41)), EquipmentItem.free("Pan")), long.equipment)
        assertEquals(listOf("equipment 0 is over 40 characters"), equipmentProblems(long))
        // Forty characters once trimmed pass, whatever the spaces around them.
        assertEquals(emptyList<String>(), equipmentProblems(RecipeFile.decode(written.replace("\"@@\"", "\"  ${"p".repeat(40)}  \""))))
        // So in format 4, where an item is its text.
        val format4 = textFormat(written, listOf("@@", "Pan"))
        assertEquals(listOf("equipment 0 is blank or untrimmed"), equipmentProblems(RecipeFile.decode(format4.replace("\"@@\"", "\"   \""))))
        assertEquals(listOf("equipment 0 is over 40 characters"), equipmentProblems(RecipeFile.decode(format4.replace("\"@@\"", "\" ${"p".repeat(41)} \""))))
        // A qualifier is read trimmed, and one of nothing but spaces is none.
        val qualified = RecipeFile.encode(RecipeFileContent.of(recipe().copy(equipment = listOf(EquipmentItem.entry(1, "@@"))), null, null))
        assertEquals(listOf(EquipmentItem.entry(1, "26 cm")), RecipeFile.decode(qualified.replace("\"@@\"", "\" 26 cm \"")).equipment)
        assertEquals(listOf(EquipmentItem(1, null, null)), RecipeFile.decode(qualified.replace("\"@@\"", "\"  \"")).equipment)
    }

    private fun equipmentProblems(content: RecipeFileContent) = RecipeFileCheck.problems(content).filter { "equipment" in it }

    /** [document] as format 4 writes it — or as format [version] — its equipment the lines [items]. */
    private fun textFormat(document: String, items: List<String>, version: Int = 4): String {
        val json = Json.parseToJsonElement(document).jsonObject
        val recipe = JsonObject(json["recipe"]!!.jsonObject + ("equipment" to JsonArray(items.map(::JsonPrimitive))))
        val rewritten = JsonObject(json + mapOf("format_version" to JsonPrimitive(version), "recipe" to recipe))
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), rewritten)
    }

    // A document of format 3 — before equipment — still reads, with none.
    @Test
    fun aFormat3DocumentReadsWithNoEquipment() {
        val content = RecipeFileContent.of(recipe(), null, null)
        val format3 = Json.parseToJsonElement(RecipeFile.encode(content)).jsonObject.let { json ->
            val recipe = JsonObject(json["recipe"]!!.jsonObject.filterKeys { it != "equipment" })
            Json { prettyPrint = true }.encodeToString(
                JsonObject.serializer(),
                JsonObject(json + mapOf("format_version" to JsonPrimitive(3), "recipe" to recipe)),
            )
        }
        assertTrue("\"format_version\": 3" in format3)
        assertFalse("equipment" in format3)
        val read = RecipeFile.decode(format3)
        assertEquals(content, read)
        assertEquals(emptyList(), read.equipment)
    }

    // Formats 1 to 3 wrote no equipment: a document of one of them that holds some is no document of its format; an
    // empty list holds none, and reads.
    @Test
    fun equipmentInAFormatBefore4IsRefused() {
        val written = RecipeFile.encode(RecipeFileContent.of(recipe().copy(equipment = listOf(EquipmentItem.free("Pot"))), null, null))
        assertEquals(listOf(EquipmentItem.free("Pot")), RecipeFile.decode(written).equipment)
        val format3 = written.replace("\"format_version\": 6", "\"format_version\": 3")
        assertTrue("\"format_version\": 3" in format3)
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(format3) }
        val format1 = oneTimerFormat(written, 1).replace("\"recipe\": {", "\"recipe\": {\"equipment\": [\"Pot\"],")
        assertTrue("\"equipment\": [\"Pot\"]" in format1)
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(format1) }
        val format3Empty = RecipeFile.encode(RecipeFileContent.of(recipe(), null, null))
            .replace("\"format_version\": 6", "\"format_version\": 3")
        assertTrue("\"equipment\": []" in format3Empty && "\"format_version\": 3" in format3Empty)
        assertEquals(emptyList(), RecipeFile.decode(format3Empty).equipment)
    }

    /**
     * [document] as format [version] — 1 or 2 — writes it: each step's list of timers as its one `timer`, or none, and
     * no equipment.
     */
    private fun oneTimerFormat(document: String, version: Int): String {
        val json = Json.parseToJsonElement(document).jsonObject
        val recipe = JsonObject(json["recipe"]!!.jsonObject.filterKeys { it != "equipment" })
        val steps = recipe["steps"]!!.jsonArray.map { element ->
            val step = element.jsonObject
            val timers = step["timers"]!!.jsonArray
            check(timers.size <= 1) { "format $version holds one timer a step" }
            JsonObject(step.filterKeys { it != "timers" } + listOfNotNull(timers.firstOrNull()?.let { "timer" to it }))
        }
        val rewritten = JsonObject(json + mapOf("format_version" to JsonPrimitive(version), "recipe" to JsonObject(recipe + ("steps" to JsonArray(steps)))))
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), rewritten)
    }
}
