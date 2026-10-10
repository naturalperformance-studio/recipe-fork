package app.recipefork.core.file

import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * The recipe file's contract, pinned: a fixed recipe is written as exactly the `recipe.json` checked in beside this
 * test, and that document reads back as the recipe. A change of the format changes this file, on purpose or not at all.
 */
class RecipeFileGoldenTest {
    private val golden = RecipeFileContent(
        id = RecipeId("0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10"),
        title = "Борщ «домашний»",
        description = "Serve with sour cream.\nKeeps for two days.",
        tags = setOf(12, 3),
        language = RecipeLanguage.RU,
        servings = 4,
        ingredients = listOf(
            Ingredient(0, 101, qualifier = "ripe", unitType = UnitType.EXACT, unit = QuantityUnit.KILOGRAM, quantity = Rational.of(3, 2), note = "finely grated"),
            Ingredient(1, 102, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(5, 2), pieceWeight = Rational.of(120), divisible = true),
            Ingredient(2, 103, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(2), divisible = false),
            Ingredient(3, null, freeTextName = "smoked salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TABLESPOON, quantity = Rational.of(1, 2), note = "coarse"),
            Ingredient(4, 104, unitType = UnitType.TASTE_MEASURED, unit = QuantityUnit.PINCH, quantity = Rational.of(2)),
            Ingredient(5, 105, qualifier = "black", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
            Ingredient(6, null, freeTextName = "cream", unitType = UnitType.EXACT, unit = QuantityUnit.CUBIC_METRE, quantity = null, enteredQuantity = "-3"),
        ),
        steps = listOf(
            Step(0, "Fry the beet until soft", listOf(IngredientReference(0, 8)), listOf(Timer(12.minutes, "Beet"))),
            Step(
                1, "Simmer with the salt and onions",
                listOf(IngredientReference(3, 14, IngredientShare(Rational.of(1, 4), QuantityUnit.TABLESPOON)), IngredientReference(1, 0)),
                listOf(Timer(95.minutes), Timer(10.minutes, "Onions"), Timer(3.minutes)),
            ),
            Step(2, "Season and serve"),
        ),
        author = "anna",
        parent = FileParent(RecipeId("7d0e5c4a-2b1f-4e3d-8c6a-1f2e3d4c5b6a"), "Борщ", "boris"),
        media = listOf(
            FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/m-1.jpg"),
            FileMedia("m-2", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/m-2.mp4"),
        ),
        ingredientGroups = listOf(IngredientGroup("Vegetables", 1), IngredientGroup("Seasoning", 3)),
        equipment = listOf(EquipmentItem.entry(10006, "5 l"), EquipmentItem.entry(10001, "26 cm"), EquipmentItem.free("Old cast-iron grater")),
        equipmentLabels = mapOf(10006L to EntryLabels("Кастрюля", "Saucepan"), 10001L to EntryLabels("Сковорода", "Frying pan")),
    )

    private val expected = checkNotNull(javaClass.getResource("/recipe-file/golden-recipe.json")).readText()

    @Test
    fun aFixedRecipeIsWrittenAsTheCheckedInDocument() = assertEquals(expected.trimEnd(), RecipeFile.encode(golden))

    @Test
    fun theCheckedInDocumentReadsBackAsTheRecipe() = assertEquals(golden, RecipeFile.decode(expected))

    /** The golden recipe as format 3 held it: with no equipment. */
    private val withoutEquipment = golden.copy(equipment = emptyList(), equipmentLabels = emptyMap())

    /** The golden recipe as format 4 held it: each item of equipment a line of text. */
    private val textEquipment = withoutEquipment.copy(
        equipment = listOf(EquipmentItem.free("Pot 5 l"), EquipmentItem.free("Frying pan 26 cm"), EquipmentItem.free("Grater")),
    )

    private val format4 = checkNotNull(javaClass.getResource("/recipe-file/golden-recipe-v4.json")).readText()

    // The same recipe as format 4 wrote it, each item its text, still reads — each item free text where no entry is found.
    @Test
    fun theFormat4DocumentReadsBackWithFreeTextEquipment() = assertEquals(textEquipment, RecipeFile.decode(format4))

    // A format-4 item whose whole text is an entry's name finds that entry, with no qualifier; the others stay free text.
    @Test
    fun aFormat4ItemThatIsAnEntrysNameFindsTheEntry() {
        val read = RecipeFile.decode(format4) { text -> 10311L.takeIf { text == "Grater" } }
        assertEquals(
            listOf(EquipmentItem.free("Pot 5 l"), EquipmentItem.free("Frying pan 26 cm"), EquipmentItem(10311, null, null)),
            read.equipment,
        )
        assertEquals(emptyMap(), read.equipmentLabels)
        assertEquals(emptyList(), RecipeFileCheck.problems(read))
    }

    /** The golden recipe as formats 1 and 2 held it: each step's first timer alone (DOM-024), and no equipment. */
    private val oneTimerEach = withoutEquipment.copy(steps = golden.steps.map { it.copy(timers = it.timers.take(1)) })

    // DOM-022, DOM-023: the same recipe as format 1 wrote it, before groups and notes, still reads — with neither.
    @Test
    fun theFormat1DocumentReadsBackWithNoGroupsAndNoNotes() {
        val format1 = checkNotNull(javaClass.getResource("/recipe-file/golden-recipe-v1.json")).readText()
        val withNeither = oneTimerEach.copy(ingredientGroups = emptyList(), ingredients = golden.ingredients.map { it.copy(note = null) })
        assertEquals(withNeither, RecipeFile.decode(format1))
    }

    // DOM-024: the same recipe as format 2 wrote it, one `timer` a step, still reads — each timer its step's only one.
    @Test
    fun theFormat2DocumentReadsBackWithOneTimerAStep() {
        val format2 = checkNotNull(javaClass.getResource("/recipe-file/golden-recipe-v2.json")).readText()
        assertEquals(oneTimerEach, RecipeFile.decode(format2))
    }

    // The same recipe as format 3 wrote it, before equipment, still reads — with none.
    @Test
    fun theFormat3DocumentReadsBackWithNoEquipment() {
        val format3 = checkNotNull(javaClass.getResource("/recipe-file/golden-recipe-v3.json")).readText()
        assertEquals(withoutEquipment, RecipeFile.decode(format3))
    }
}
