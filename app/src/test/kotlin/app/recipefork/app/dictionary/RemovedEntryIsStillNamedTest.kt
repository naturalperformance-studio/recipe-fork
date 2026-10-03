package app.recipefork.app.dictionary

import app.recipefork.app.card.ingredientLine
import app.recipefork.app.editor.nameIn
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DOM-009 (5), FLOW-001 (7): an entry removed upstream is held by the device (DictionarySeedingTest), and the card and
 * the editor, which resolve a row's entry by its identity, still name the recipe's ingredient by it.
 */
class RemovedEntryIsStillNamedTest {
    private val goose = IngredientEntryEntity(5, "гусь", "goose", null, removed = true)
    private val row = Ingredient(0, goose.id, "domestic", null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(900))

    @Test
    fun theCardNamesARemovedEntry() {
        assertEquals("goose, domestic", ingredientLine(row, mapOf(goose.id to goose), ENGLISH).name)
        assertEquals("гусь, domestic", ingredientLine(row, mapOf(goose.id to goose), RUSSIAN).name)
    }

    @Test
    fun theEditorNamesARemovedEntry() {
        assertEquals("goose", row.nameIn(listOf(goose), ENGLISH))
        assertEquals("гусь", row.nameIn(listOf(goose), RUSSIAN))
    }
}
