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
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * decisions/20260928-recipe-file-is-a-zip.md: an importer validates the document against the rules a sending must
 * pass — DOM-012's limits, DOM-016's units, DOM-017's quantities — and the shape of a recipe. Each rule is broken once.
 */
class RecipeFileCheckTest {
    private val valid = RecipeFileContent(
        id = RecipeId("0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10"),
        title = "Borscht",
        description = "Serve hot.",
        tags = setOf(3),
        language = null,
        servings = 4,
        ingredients = listOf(
            Ingredient(0, 101, qualifier = "ripe", unitType = UnitType.EXACT, unit = QuantityUnit.KILOGRAM, quantity = Rational.of(3, 2), note = "finely grated"),
            Ingredient(1, 102, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(5, 2), pieceWeight = Rational.of(120), divisible = true),
            Ingredient(2, null, freeTextName = "salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(1, 3), note = "coarse"),
            Ingredient(3, null, freeTextName = "pepper", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
            Ingredient(4, 104, unitType = UnitType.TASTE_MEASURED, unit = QuantityUnit.PINCH, quantity = Rational.of(2)),
        ),
        steps = listOf(
            Step(0, "Fry the beet", listOf(IngredientReference(0, 8)), Timer(12.minutes, "Beet")),
            Step(1, "Season", timer = Timer((99 * 60 + 59).minutes)),
        ),
        author = "anna",
        parent = FileParent(RecipeId("p-1"), "Soup", "boris"),
        media = listOf(
            FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/m-1.jpg"),
            FileMedia("m-2", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/m-2.mp4"),
        ),
        ingredientGroups = listOf(IngredientGroup("Soup", 0), IngredientGroup("Seasoning", 2)),
        equipment = listOf(EquipmentItem.free("Pot"), EquipmentItem.entry(10001, "26 cm")),
        equipmentLabels = mapOf(10001L to EntryLabels("Сковорода", "Frying pan")),
    )

    private fun refused(content: RecipeFileContent) =
        assertTrue(RecipeFileCheck.problems(content).isNotEmpty(), "refused: $content")

    /** [content] is refused for the one problem that says [problem], and for nothing else. */
    private fun refusedFor(problem: String, content: RecipeFileContent) {
        val problems = RecipeFileCheck.problems(content)
        assertEquals(1, problems.size, "$problems")
        assertTrue(problem in problems.single(), "$problems")
    }

    private fun groups(vararg groups: IngredientGroup) = valid.copy(ingredientGroups = groups.toList())

    private fun row(position: Int, change: (Ingredient) -> Ingredient) =
        valid.copy(ingredients = valid.ingredients.map { if (it.position == position) change(it) else it })

    @Test
    fun aRecipeWithinEveryRuleHasNoProblem() = assertEquals(emptyList(), RecipeFileCheck.problems(valid))

    // DOM-012: the limits are inclusive; one over each is refused.
    @Test
    fun theLimitsOfDom012() {
        val fifty = (0 until 50).map { Ingredient(it, null, freeTextName = "i$it", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(1)) }
        val fiftySteps = (0 until 50).map { Step(it, "s".repeat(2_000)) }
        val atLimits = valid.copy(
            title = "t".repeat(120), description = "d".repeat(2_000), ingredients = fifty, steps = fiftySteps,
            media = emptyList(),
        )
        assertEquals(emptyList(), RecipeFileCheck.problems(atLimits))
        assertEquals(emptyList(), RecipeFileCheck.problems(valid.copy(steps = listOf(Step(0, "x", timer = Timer(5.minutes, "n".repeat(60)))), media = emptyList())))
        refused(atLimits.copy(title = "t".repeat(121)))
        refused(atLimits.copy(description = "d".repeat(2_001)))
        refused(atLimits.copy(ingredients = fifty + Ingredient(50, null, freeTextName = "x", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(1))))
        refused(atLimits.copy(steps = fiftySteps + Step(50, "x")))
        refused(atLimits.copy(steps = listOf(Step(0, "s".repeat(2_001)))))
        refused(valid.copy(steps = listOf(Step(0, "x", timer = Timer(5.minutes, "n".repeat(61)))), media = emptyList()))
    }

    // DOM-016: a unit, where there is one, is of the type its row states; a row to taste holds no unit of measure and
    // no quantity.
    @Test
    fun theUnitsOfDom016() {
        refused(row(0) { it.copy(unit = QuantityUnit.TEASPOON) })
        refused(row(1) { it.copy(unit = QuantityUnit.GRAM) })
        refused(row(3) { it.copy(unit = QuantityUnit.GRAM) })
        refused(row(3) { it.copy(quantity = Rational.of(1)) })
        refused(row(3) { it.copy(enteredQuantity = "2") })
        refused(row(0) { it.copy(divisible = true) })
        refused(row(2) { it.copy(pieceWeight = Rational.of(5)) })
        assertEquals(emptyList(), RecipeFileCheck.problems(row(3) { it.copy(unit = QuantityUnit.TO_TASTE) }))
    }

    // DOM-017: a value held is above zero; one off its grid, or a row still being written, is kept as an own draft
    // keeps it (FLOW-024 (6), DOM-017 (6)), because export takes a recipe in any state (FLOW-063 (1)).
    @Test
    fun theQuantitiesOfDom017() {
        refused(row(0) { it.copy(quantity = Rational.ZERO) })
        refused(row(0) { it.copy(quantity = Rational.of(-1)) })
        refused(row(0) { it.copy(enteredQuantity = "-3") })
        refused(row(1) { it.copy(pieceWeight = Rational.ZERO) })
        listOf(
            row(0) { it.copy(unit = QuantityUnit.GRAM, quantity = Rational.of(15, 2)) },
            row(2) { it.copy(quantity = Rational.of(3, 10)) },
            row(1) { it.copy(divisible = false) },
            row(1) { it.copy(divisible = null) },
            row(0) { it.copy(quantity = null) },
            row(0) { it.copy(quantity = null, enteredQuantity = "-3") },
            row(0) { it.copy(quantity = null, enteredQuantity = "1 500") },
            row(2) { it.copy(unit = null, quantity = null) },
        ).forEach { assertEquals(emptyList(), RecipeFileCheck.problems(it), "$it") }
    }

    // DOM-009: an ingredient is never named both by an entry and by words; a row not named yet is a draft's.
    @Test
    fun aRowIsNamedOnce() {
        refused(row(0) { it.copy(freeTextName = "beet") })
        refused(row(2) { it.copy(freeTextName = null, qualifier = "fine") })
        assertEquals(emptyList(), RecipeFileCheck.problems(row(2) { it.copy(freeTextName = null) }))
        assertEquals(emptyList(), RecipeFileCheck.problems(row(2) { it.copy(freeTextName = " ") }))
    }

    // A part of an ingredient is checked like any quantity: above zero, on its grid, of its ingredient's unit type.
    // Parts are not checked against the whole: one larger than it, or several adding up beyond it, pass.
    @Test
    fun aPartOfAnIngredient() {
        fun step(vararg references: IngredientReference) = valid.copy(steps = listOf(Step(0, "Fry the beet and", references.toList()), valid.steps[1]))
        fun part(position: Int, amount: Rational, unit: QuantityUnit, offset: Int = 0) =
            IngredientReference(position, offset, IngredientShare(amount, unit))
        assertEquals(emptyList(), RecipeFileCheck.problems(step(part(0, Rational.of(500), QuantityUnit.GRAM))))
        assertEquals(emptyList(), RecipeFileCheck.problems(step(part(1, Rational.of(3, 2), QuantityUnit.PIECE))))
        assertEquals(emptyList(), RecipeFileCheck.problems(step(part(2, Rational.of(2), QuantityUnit.TEASPOON))))
        assertEquals(emptyList(), RecipeFileCheck.problems(step(part(1, Rational.of(2), QuantityUnit.PIECE), part(1, Rational.of(2), QuantityUnit.PIECE, 4))))
        refused(step(part(0, Rational.ZERO, QuantityUnit.GRAM)))
        refused(step(part(0, Rational.of(-5), QuantityUnit.GRAM)))
        refused(step(part(0, Rational.of(1, 2), QuantityUnit.GRAM)))
        refused(step(part(1, Rational.of(1, 3), QuantityUnit.PIECE)))
        refused(step(part(2, Rational.of(1, 5), QuantityUnit.TEASPOON)))
        refused(step(part(0, Rational.of(2), QuantityUnit.PIECE)))
        refused(step(part(3, Rational.of(1), QuantityUnit.PINCH)))
        refused(step(part(3, Rational.of(1), QuantityUnit.TO_TASTE)))
        // A part its row can never read: another dimension, another household unit. A part in grams of a row in
        // kilograms reads, as the editor keeps it.
        refused(step(part(0, Rational.of(1), QuantityUnit.LITRE)))
        refused(step(part(0, Rational.of(500), QuantityUnit.MILLILITRE)))
        refused(step(part(2, Rational.of(1, 2), QuantityUnit.TABLESPOON)))
        refused(step(part(2, Rational.of(1), QuantityUnit.CUP)))
        assertEquals(emptyList(), RecipeFileCheck.problems(step(part(0, Rational.of(1), QuantityUnit.KILOGRAM))))
    }

    @Test
    fun theRecipesShape() {
        refused(valid.copy(ingredients = valid.ingredients.drop(1)))
        refused(valid.copy(steps = valid.steps.reversed()))
        refused(valid.copy(servings = 0))
        refused(valid.copy(id = RecipeId("../etc")))
        refused(valid.copy(id = RecipeId("")))
        refused(valid.copy(parent = FileParent(RecipeId("a/b"), null, null)))
        refused(valid.copy(author = " "))
        refused(valid.copy(author = "a".repeat(101)))
        refused(valid.copy(steps = listOf(Step(0, "Fry", listOf(IngredientReference(9, 0))), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", listOf(IngredientReference(0, 4))), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", listOf(IngredientReference(0, 0), IngredientReference(1, 0))), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", timer = Timer(0.minutes)), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", timer = Timer((100 * 60).minutes)), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", timer = Timer(90.seconds)), valid.steps[1])))
    }

    // DOM-024: a step carries up to five timers; a sixth is refused, and so is the one problem it makes.
    @Test
    fun aSixthTimerOnAStepIsRefused() {
        fun timers(count: Int) = valid.copy(steps = listOf(valid.steps[0].copy(timers = (1..count).map { Timer(it.minutes) }), valid.steps[1]))
        assertEquals(emptyList(), RecipeFileCheck.problems(timers(5)))
        refusedFor("more than 5 timers", timers(6))
        // Each step is counted on its own: five on each of two steps is within the bound.
        val fiveEach = valid.copy(steps = valid.steps.map { it.copy(timers = (1..5).map { m -> Timer(m.minutes) }) })
        assertEquals(emptyList(), RecipeFileCheck.problems(fiveEach))
    }

    // DOM-024, FLOW-027, DOM-012: every timer of a step, not only its first, holds a duration of whole minutes from
    // 1 minute to 99 h 59 min and a name of at most 60 characters.
    @Test
    fun everyTimerOfAStepIsHeldToItsBounds() {
        fun second(timer: Timer) = valid.copy(steps = listOf(valid.steps[0].copy(timers = listOf(Timer(12.minutes, "Beet"), timer)), valid.steps[1]))
        assertEquals(emptyList(), RecipeFileCheck.problems(second(Timer(1.minutes, "n".repeat(60)))))
        assertEquals(emptyList(), RecipeFileCheck.problems(second(Timer((99 * 60 + 59).minutes))))
        refusedFor("timer 1 out of range", second(Timer(0.minutes)))
        refusedFor("timer 1 out of range", second(Timer((100 * 60).minutes)))
        refusedFor("timer 1 out of range", second(Timer(90.seconds)))
        refusedFor("timer 1 name is over 60", second(Timer(5.minutes, "n".repeat(61))))
        // The fifth timer, the last a step may carry, is checked as well.
        val fifth = valid.copy(steps = listOf(valid.steps[0].copy(timers = List(4) { Timer(1.minutes) } + Timer(5.minutes, "n".repeat(61))), valid.steps[1]))
        refusedFor("timer 4 name is over 60", fifth)
    }

    // MEDIA-001 and the ADR: one photograph as cover, one item per step held, each in an entry of its own under media/.
    @Test
    fun theMediaItNames() {
        val cover = valid.media[0]
        refused(valid.copy(media = valid.media + cover.copy(mediaId = "m-3", entry = "media/m-3.jpg")))
        refused(valid.copy(media = listOf(cover.copy(kind = MediaKind.VIDEO))))
        refused(valid.copy(media = listOf(cover.copy(attachment = MediaAttachment.ToStep(7)))))
        refused(valid.copy(media = valid.media + valid.media[1].copy(mediaId = "m-3", entry = "media/m-3.mp4")))
        // A photograph and a clip on one step are two items there, whatever their kinds (MEDIA-001).
        refused(valid.copy(media = valid.media + FileMedia("m-3", MediaKind.PHOTO, MediaAttachment.ToStep(1), "media/m-3.jpg")))
        refused(valid.copy(media = valid.media + valid.media[1].copy(attachment = MediaAttachment.ToStep(0), entry = "media/m-3.mp4")))
        refused(valid.copy(media = valid.media + valid.media[1].copy(mediaId = "m-3", attachment = MediaAttachment.ToStep(0))))
        listOf("m-1.jpg", "/media/m-1.jpg", "media/../m-1.jpg", "media/a/m-1.jpg", "media/", "media\\m-1.jpg", "other/m-1.jpg")
            .forEach { refused(valid.copy(media = listOf(cover.copy(entry = it)))) }
    }

    @Test
    fun entryNames() {
        listOf("recipe.json", "media/m-1.jpg", "media/", "notes/readme.txt").forEach { assertTrue(RecipeFileCheck.isSafeEntryName(it), it) }
        listOf("/recipe.json", "../recipe.json", "media/../../x", "media/..", "\\x", "C:\\x", "a\\..\\b", "").forEach {
            assertFalse(RecipeFileCheck.isSafeEntryName(it), it)
        }
    }

    // FLOW-064 (5): a document from a later version is told apart from a damaged one.
    @Test
    fun aLaterFormatIsToldApart() {
        val later = RecipeFile.encode(valid).replace("\"format_version\": 6", "\"format_version\": 10")
        assertTrue("\"format_version\": 10" in later)
        val thrown = runCatching { RecipeFile.decode(later) }.exceptionOrNull()
        assertTrue(thrown is LaterRecipeFile, "$thrown")
        assertEquals(10, thrown.formatVersion)
        val damaged = runCatching { RecipeFile.decode("{") }.exceptionOrNull()
        assertTrue(damaged is UnreadableRecipeFile && damaged !is LaterRecipeFile)
        val huge = RecipeFile.encode(valid).replace("\"3/2\"", "\"${"9".repeat(100_000)}/2\"")
        assertTrue(runCatching { RecipeFile.decode(huge) }.exceptionOrNull() is UnreadableRecipeFile)
    }

    // DOM-023: a note is at most 60 characters, trimmed and not blank; a free-text row may carry one too.
    @Test
    fun theNoteOfDom023() {
        assertEquals(emptyList(), RecipeFileCheck.problems(row(0) { it.copy(note = "n".repeat(60)) }))
        assertEquals(emptyList(), RecipeFileCheck.problems(row(3) { it.copy(note = "freshly ground") }))
        assertEquals(emptyList(), RecipeFileCheck.problems(row(0) { it.copy(note = null) }))
        refusedFor("note is over 60", row(0) { it.copy(note = "n".repeat(61)) })
        refusedFor("blank or untrimmed note", row(0) { it.copy(note = " ") })
        refusedFor("blank or untrimmed note", row(0) { it.copy(note = "") })
        refusedFor("blank or untrimmed note", row(0) { it.copy(note = " grated") })
    }

    // DOM-022: a group's name is 1 to 40 characters and not blank.
    @Test
    fun theGroupNameOfDom022() {
        assertEquals(emptyList(), RecipeFileCheck.problems(groups(IngredientGroup("g".repeat(40), 0), IngredientGroup("G", 2))))
        refusedFor("name is over 40", groups(IngredientGroup("g".repeat(41), 0)))
        refusedFor("has no name", groups(IngredientGroup("", 0)))
        refusedFor("has no name", groups(IngredientGroup("  ", 0)))
    }

    // DOM-022: each heading starts at a row of the list or at its end, the headings in order, at most one at a row.
    @Test
    fun theGroupPositionsOfDom022() {
        val count = valid.ingredients.size
        assertEquals(emptyList(), RecipeFileCheck.problems(groups(IngredientGroup("Soup", 0), IngredientGroup("Later", count))))
        assertEquals(emptyList(), RecipeFileCheck.problems(groups()))
        refusedFor("starts at no ingredient", groups(IngredientGroup("Soup", count + 1)))
        refusedFor("starts at no ingredient", groups(IngredientGroup("Soup", -1)))
        refusedFor("starts at no ingredient", valid.copy(ingredients = emptyList(), steps = emptyList(), media = emptyList(), ingredientGroups = listOf(IngredientGroup("Soup", 1))))
        refusedFor("out of order", groups(IngredientGroup("Seasoning", 2), IngredientGroup("Soup", 0)))
        refusedFor("two groups start at one ingredient", groups(IngredientGroup("Soup", 1), IngredientGroup("Seasoning", 1)))
    }

    // The equipment: at most fifteen items, each 1 to 40 characters, trimmed and not blank.
    @Test
    fun theEquipment() {
        val fifteen = (1..15).map { EquipmentItem.free("Item $it".padEnd(40, 'x')) }
        assertEquals(emptyList(), RecipeFileCheck.problems(valid))
        assertEquals(emptyList(), RecipeFileCheck.problems(unlabelled.copy(equipment = fifteen)))
        assertEquals(emptyList(), RecipeFileCheck.problems(unlabelled.copy(equipment = listOf(free("P")))))
        assertEquals(emptyList(), RecipeFileCheck.problems(unlabelled.copy(equipment = emptyList())))
        refusedFor("more than 15 items of equipment", unlabelled.copy(equipment = fifteen + free("Item 16")))
        refusedFor("equipment 1 is over 40", unlabelled.copy(equipment = listOf(free("Pot"), free("p".repeat(41)))))
        refusedFor("equipment 0 is blank", unlabelled.copy(equipment = listOf(free(""))))
        refusedFor("equipment 1 is blank", unlabelled.copy(equipment = listOf(free("Pot"), free("   "))))
        refusedFor("equipment 0 is blank or untrimmed", unlabelled.copy(equipment = listOf(free(" Pot"))))
        refusedFor("equipment 0 is blank or untrimmed", unlabelled.copy(equipment = listOf(free("Pot "))))
    }

    // An item is named as an ingredient is (DOM-009): an entry, which a qualifier may narrow, or free text — exactly one.
    @Test
    fun anItemIsAnEntryOrFreeTextNeverBoth() {
        assertEquals(emptyList(), RecipeFileCheck.problems(unlabelled.copy(equipment = listOf(EquipmentItem.entry(10001)))))
        assertEquals(emptyList(), RecipeFileCheck.problems(unlabelled.copy(equipment = listOf(EquipmentItem.entry(10001, "26 cm")))))
        refusedFor("equipment 0 is named by both", unlabelled.copy(equipment = listOf(EquipmentItem(10001, null, "Pan"))))
        refusedFor("equipment 0 is named by both an entry and free text, or by neither", unlabelled.copy(equipment = listOf(EquipmentItem(null, null, null))))
        refusedFor("equipment 0 qualifies no entry", unlabelled.copy(equipment = listOf(EquipmentItem(null, "26 cm", "Pan"))))
        refusedFor("equipment 0 names an entry that is none", unlabelled.copy(equipment = listOf(EquipmentItem.entry(-1))))
        refusedFor("equipment 0 is blank or untrimmed", unlabelled.copy(equipment = listOf(EquipmentItem(10001, " ", null))))
        refusedFor("equipment 0 is blank or untrimmed", unlabelled.copy(equipment = listOf(EquipmentItem(10001, "26 cm ", null))))
    }

    // An entry's item reads as its label with its qualifier, and is held to forty characters in either language where
    // the file gives the labels; the labels themselves are labels.
    @Test
    fun anEntrysItemReadsAsItsLabelWithItsQualifier() {
        val labels = mapOf(10001L to EntryLabels("Сковорода", "Frying pan"))
        // "Frying pan " is 11 characters: 29 more make 40, and 30 one too many; the Russian label is shorter.
        assertEquals(emptyList(), RecipeFileCheck.problems(valid.copy(equipment = listOf(EquipmentItem.entry(10001, "q".repeat(29))), equipmentLabels = labels)))
        refusedFor("equipment 0 is over 40", valid.copy(equipment = listOf(EquipmentItem.entry(10001, "q".repeat(30))), equipmentLabels = labels))
        // With no labels, the qualifier alone is counted.
        assertEquals(emptyList(), RecipeFileCheck.problems(valid.copy(equipment = listOf(EquipmentItem.entry(10001, "q".repeat(40))), equipmentLabels = emptyMap())))
        refusedFor("equipment 0 is over 40", valid.copy(equipment = listOf(EquipmentItem.entry(10001, "q".repeat(41))), equipmentLabels = emptyMap()))
        refusedFor("labels of equipment entry 10001 are not labels", valid.copy(equipmentLabels = mapOf(10001L to EntryLabels("", "Frying pan"))))
        refusedFor("labels of equipment entry 10001 are not labels", valid.copy(equipmentLabels = mapOf(10001L to EntryLabels("Сковорода", " Frying pan"))))
    }

    // Labels are given for the entries the equipment names and no other: a file cannot make an importer hold labels of
    // entries beyond its fifteen items.
    @Test
    fun labelsBeyondTheEquipmentAreRefused() {
        val knife = 10002L to EntryLabels("Нож", "Knife")
        refusedFor("labels of an entry the equipment does not name", valid.copy(equipmentLabels = valid.equipmentLabels + knife))
        val many = (20_000L until 25_000L).associateWith { EntryLabels("Нож $it", "Knife $it") }
        refusedFor("labels of an entry the equipment does not name", valid.copy(equipmentLabels = valid.equipmentLabels + many))
        // Labels of an entry an item names, and of none when no item names one.
        assertEquals(emptyList(), RecipeFileCheck.problems(valid.copy(equipment = valid.equipment + EquipmentItem.entry(10002), equipmentLabels = valid.equipmentLabels + knife)))
        refusedFor("labels of an entry the equipment does not name", valid.copy(equipment = listOf(free("Pot"))))
    }

    // A recipe carries at most a thousand tags — well above the whole dictionary's, which is the most the editor gives
    // one; many distinct tags, each held as a number of its own, are refused.
    @Test
    fun theTagsAreHeldToTheirCap() {
        assertEquals(1_000, RecipeFileCheck.MAX_TAGS)
        assertEquals(emptyList(), RecipeFileCheck.problems(valid.copy(tags = (0L until 1_000L).toSet())))
        refusedFor("more than 1000 tags", valid.copy(tags = (0L until 1_001L).toSet()))
        refusedFor("more than 1000 tags", valid.copy(tags = (0L until 128_000L).toSet()))
    }

    private val unlabelled = valid.copy(equipmentLabels = emptyMap())

    private fun free(text: String) = EquipmentItem.free(text)
}
