package app.recipefork.core.recipe

import app.recipefork.core.file.RecipeFileCheck
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A recipe lists the equipment it is cooked with: short lines, in the author's order, at most fifteen. */
class RecipeEquipmentTest {
    private val at = Instant.parse("2026-10-09T08:00:00Z")
    private val empty = Recipe.create(at, owner = null)

    @Test
    fun theBoundsAreFifteenItemsOfFortyCharacters() {
        assertEquals(15, RecipeBounds.MAX_EQUIPMENT)
        assertEquals(40, RecipeBounds.MAX_EQUIPMENT_NAME)
        assertEquals(RecipeBounds.MAX_EQUIPMENT, RecipeFileCheck.MAX_EQUIPMENT)
        assertEquals(RecipeBounds.MAX_EQUIPMENT_NAME, RecipeFileCheck.MAX_EQUIPMENT_NAME)
    }

    @Test
    fun aNewRecipeListsNoEquipment() = assertEquals(emptyList(), empty.equipment)

    // Each line the author typed is trimmed; a blank one is none.
    @Test
    fun typedLinesAreTrimmedAndBlankOnesDropped() {
        assertEquals(
            listOf(free("Oven"), free("Frying pan 26 cm")),
            Recipe.equipmentOf(listOf("  Oven ", "", "   ", "\tFrying pan 26 cm\n").map(::free)),
        )
        assertEquals(free("Oven"), Recipe.equipmentItemOf(" Oven  "))
        assertNull(Recipe.equipmentItemOf(" \t "))
        assertNull(Recipe.equipmentItemOf(""))
    }

    // Items are kept in the order the author added them, trimmed; a blank one adds nothing; a sixteenth is refused.
    @Test
    fun anItemIsAddedAfterTheOthersAndASixteenthIsRefused() {
        val oven = empty.withEquipment(" Oven ")!!
        assertEquals(listOf(free("Oven")), oven.equipment)
        assertEquals(listOf(free("Oven"), free("Pan")), oven.withEquipment("Pan")!!.equipment)
        assertSame(oven, oven.withEquipment("   "))

        val fourteen = (1..14).fold(empty) { recipe, n -> recipe.withEquipment("Item $n")!! }
        assertTrue(fourteen.canAddEquipment)
        val fifteen = fourteen.withEquipment("Item 15")!!
        assertEquals((1..15).map { free("Item $it") }, fifteen.equipment)
        assertFalse(fifteen.canAddEquipment)
        assertNull(fifteen.withEquipment("Item 16"))
        assertNull(fifteen.withEquipment(" "))
    }

    // An item's name is bound at forty characters, inclusive, as the other limits are; no item, no bound.
    @Test
    fun eachItemHasItsOwnNameBound() {
        val recipe = empty.copy(equipment = listOf(free("o".repeat(40)), free("p".repeat(41)), free("Whisk")))
        assertNull(RecipeBounds.of(recipe, RecipePart.EquipmentName(0)))
        assertEquals(Bound.OverLimit(40, 41), RecipeBounds.of(recipe, RecipePart.EquipmentName(1)))
        assertNull(RecipeBounds.of(recipe, RecipePart.EquipmentName(2)))
        assertNull(RecipeBounds.of(recipe, RecipePart.EquipmentName(3)))
    }

    // The editor's marks list an item over its bound above the ingredients, as the card shows the equipment.
    @Test
    fun anItemOverItsBoundIsListedAboveTheIngredients() {
        val recipe = empty.copy(equipment = listOf(free("Oven"), free("p".repeat(41)), free("w".repeat(45))))
        val all = RecipeBounds.all(recipe)
        assertEquals(Bound.OverLimit(40, 41), all[RecipePart.EquipmentName(1)])
        assertEquals(Bound.OverLimit(40, 45), all[RecipePart.EquipmentName(2)])
        assertNull(all[RecipePart.EquipmentName(0)])
        assertEquals(
            listOf(
                RecipePart.Title, RecipePart.Language, RecipePart.Servings,
                RecipePart.EquipmentName(1), RecipePart.EquipmentName(2), RecipePart.Ingredients, RecipePart.Steps,
            ),
            all.keys.toList(),
        )
    }

    // A fork carries the equipment whole, in order — entries with their qualifiers and free text — the owner's and
    // anyone else's.
    @Test
    fun aForkCarriesTheEquipment() {
        val items = listOf(EquipmentItem.entry(10202), EquipmentItem.entry(10101, "24 cm"), free("Grandma's tart tin"))
        val parent = empty.copy(title = "Pie", equipment = items)
        assertEquals(items, parent.fork(at, owner = null).equipment)
        assertEquals(items, parent.fork(at, owner = null, byAuthor = false).equipment)
    }

    // An item is named as an ingredient is (DOM-009): by an entry, which a qualifier narrows, or by free text, exactly
    // one of the two; it reads as the entry's label and its qualifier, or as its free text.
    @Test
    fun anItemIsAnEntryWithAQualifierOrFreeText() {
        val pan = EquipmentItem.entry(10001, "  26 cm ")
        assertEquals(EquipmentItem(10001, "26 cm", null), pan)
        assertEquals(EquipmentItem(10001, null, null), EquipmentItem.entry(10001, "   "))
        assertTrue(pan.isNamed)
        assertTrue(free("Pan").isNamed)
        assertFalse(EquipmentItem(10001, null, "Pan").isNamed)
        assertFalse(EquipmentItem(null, null, null).isNamed)
        assertEquals("Frying pan 26 cm", pan.shown("Frying pan"))
        assertEquals("Сковорода 26 cm", pan.shown("Сковорода"))
        assertEquals("Oven", EquipmentItem.entry(10202).shown("Oven"))
        // An entry whose label is not known reads as its qualifier; free text reads as itself, whatever label is given.
        assertEquals("26 cm", pan.shown(null))
        assertEquals("Grandma's pan", free("Grandma's pan").shown("Frying pan"))
    }

    // The forty characters are those the item reads as: an entry's label with its qualifier, where the label is given.
    @Test
    fun anEntrysItemIsBoundWithItsLabel() {
        val recipe = empty.copy(equipment = listOf(EquipmentItem.entry(10001, "q".repeat(29)), EquipmentItem.entry(10001, "q".repeat(30))))
        val label = { id: Long -> "Frying pan".takeIf { id == 10001L } }
        assertNull(RecipeBounds.of(recipe, RecipePart.EquipmentName(0), label))
        assertEquals(Bound.OverLimit(40, 41), RecipeBounds.of(recipe, RecipePart.EquipmentName(1), label))
        assertEquals(listOf<RecipePart>(RecipePart.EquipmentName(1)), RecipeBounds.all(recipe, label).keys.filterIsInstance<RecipePart.EquipmentName>())
        // With no label, the qualifier alone is counted.
        assertNull(RecipeBounds.of(recipe, RecipePart.EquipmentName(1)))
    }

    // The editor's settling trims free text and drops a blank item; an entry's item stays as it is.
    @Test
    fun settlingKeepsAnEntrysItem() {
        val items = listOf(EquipmentItem.entry(10001, "26 cm"), free("  Whisk "), free("  "), EquipmentItem.entry(10202))
        assertEquals(listOf(EquipmentItem.entry(10001, "26 cm"), free("Whisk"), EquipmentItem.entry(10202)), Recipe.equipmentOf(items))
    }

    private fun free(text: String) = EquipmentItem.free(text)
}
