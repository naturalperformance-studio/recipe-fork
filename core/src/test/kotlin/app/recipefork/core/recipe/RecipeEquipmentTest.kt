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
        assertEquals(listOf("Oven", "Frying pan 26 cm"), Recipe.equipmentOf(listOf("  Oven ", "", "   ", "\tFrying pan 26 cm\n")))
        assertEquals("Oven", Recipe.equipmentItemOf(" Oven  "))
        assertNull(Recipe.equipmentItemOf(" \t "))
        assertNull(Recipe.equipmentItemOf(""))
    }

    // Items are kept in the order the author added them, trimmed; a blank one adds nothing; a sixteenth is refused.
    @Test
    fun anItemIsAddedAfterTheOthersAndASixteenthIsRefused() {
        val oven = empty.withEquipment(" Oven ")!!
        assertEquals(listOf("Oven"), oven.equipment)
        assertEquals(listOf("Oven", "Pan"), oven.withEquipment("Pan")!!.equipment)
        assertSame(oven, oven.withEquipment("   "))

        val fourteen = (1..14).fold(empty) { recipe, n -> recipe.withEquipment("Item $n")!! }
        assertTrue(fourteen.canAddEquipment)
        val fifteen = fourteen.withEquipment("Item 15")!!
        assertEquals((1..15).map { "Item $it" }, fifteen.equipment)
        assertFalse(fifteen.canAddEquipment)
        assertNull(fifteen.withEquipment("Item 16"))
        assertNull(fifteen.withEquipment(" "))
    }

    // An item's name is bound at forty characters, inclusive, as the other limits are; no item, no bound.
    @Test
    fun eachItemHasItsOwnNameBound() {
        val recipe = empty.copy(equipment = listOf("o".repeat(40), "p".repeat(41), "Whisk"))
        assertNull(RecipeBounds.of(recipe, RecipePart.EquipmentName(0)))
        assertEquals(Bound.OverLimit(40, 41), RecipeBounds.of(recipe, RecipePart.EquipmentName(1)))
        assertNull(RecipeBounds.of(recipe, RecipePart.EquipmentName(2)))
        assertNull(RecipeBounds.of(recipe, RecipePart.EquipmentName(3)))
    }

    // The editor's marks list an item over its bound above the ingredients, as the card shows the equipment.
    @Test
    fun anItemOverItsBoundIsListedAboveTheIngredients() {
        val recipe = empty.copy(equipment = listOf("Oven", "p".repeat(41), "w".repeat(45)))
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

    // A fork carries the equipment whole, in order — the owner's and anyone else's.
    @Test
    fun aForkCarriesTheEquipment() {
        val parent = empty.copy(title = "Pie", equipment = listOf("Oven", "Tart tin 24 cm"))
        assertEquals(listOf("Oven", "Tart tin 24 cm"), parent.fork(at, owner = null).equipment)
        assertEquals(listOf("Oven", "Tart tin 24 cm"), parent.fork(at, owner = null, byAuthor = false).equipment)
    }
}
