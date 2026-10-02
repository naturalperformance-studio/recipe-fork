package app.recipefork.core.file

import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every name the file format writes for an enumeration, pinned one by one: a typo in a table of [RecipeFile] — a
 * unit, a unit type, a language or a media kind — fails here, whether or not the golden document happens to use it.
 */
class RecipeFileNamesTest {
    private fun <E : Enum<E>> pin(table: RecipeFile.NameTable<E>, expected: Map<E, String>) {
        assertEquals(table.values.toSet(), expected.keys, "every value is pinned")
        expected.forEach { (value, name) ->
            assertEquals(name, table.nameOf(value), "$value is written as")
            assertEquals(value, table.valueOf(name), "$name reads as")
        }
        assertEquals(expected.size, expected.values.toSet().size, "no two values share a name")
    }

    @Test
    fun unitTypes() = pin(
        RecipeFile.Names.unitType,
        mapOf(
            UnitType.EXACT to "exact",
            UnitType.PIECE to "piece",
            UnitType.HOUSEHOLD to "household",
            UnitType.TASTE_MEASURED to "taste_measured",
            UnitType.TASTE_UNMEASURED to "taste_unmeasured",
        ),
    )

    @Test
    fun units() = pin(
        RecipeFile.Names.unit,
        mapOf(
            QuantityUnit.GRAM to "gram",
            QuantityUnit.KILOGRAM to "kilogram",
            QuantityUnit.TONNE to "tonne",
            QuantityUnit.MILLILITRE to "millilitre",
            QuantityUnit.LITRE to "litre",
            QuantityUnit.CUBIC_METRE to "cubic_metre",
            QuantityUnit.PIECE to "piece",
            QuantityUnit.TEASPOON to "teaspoon",
            QuantityUnit.TABLESPOON to "tablespoon",
            QuantityUnit.CUP to "cup",
            QuantityUnit.PINCH to "pinch",
            QuantityUnit.TO_TASTE to "to_taste",
        ),
    )

    @Test
    fun languages() = pin(RecipeFile.Names.language, mapOf(RecipeLanguage.RU to "ru", RecipeLanguage.EN to "en"))

    @Test
    fun mediaKinds() = pin(RecipeFile.Names.kind, mapOf(MediaKind.PHOTO to "photo", MediaKind.VIDEO to "video"))

    // The file carries no state: nothing of a recipe's state, ownership or sending travels (FLOW-063, FLOW-064).
    @Test
    fun noStateIsWritten() {
        val written = RecipeFile.encode(
            RecipeFileContent.of(app.recipefork.core.recipe.Recipe.create(java.time.Instant.EPOCH, null), null, null),
        )
        listOf("state", "owner", "sent", "local", "LOCAL").forEach { assertEquals(false, "\"$it\"" in written, it) }
    }
}
