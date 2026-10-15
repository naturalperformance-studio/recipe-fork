package app.recipefork.core.paste

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeBounds

/**
 * The ingredient lines of a pasted text as its rows will read them, the dictionary at hand: the first
 * [RecipeBounds.MAX_INGREDIENTS] lines, each as the lines [IngredientLineReader.lines] makes of it ("Крем: 200 г
 * мацони" under its label, "20 г укропа + 15 г петрушки" as two), and a line under the ingredient heading that names
 * equipment and no ingredient by the name it reads as, in no unit but pieces ("аэрогриль", "кофемолка или мощный блендер",
 * "банка с крышкой", "a jar with a lid"), set apart as an equipment line.
 */
internal class IngredientSection(lines: List<IngredientLine>, reader: IngredientLineReader, mentions: EquipmentMentions) {
    val ingredients: List<IngredientLine>
    val equipment: List<String>

    init {
        val (equipmentLines, ingredientLines) = lines.take(RecipeBounds.MAX_INGREDIENTS).flatMap(reader::lines).partition { line ->
            val row = reader.read(line, 0)
            // The name it reads as names the equipment, not a parenthesis: "6 сырников (запечённых в аэрогриле)" is food.
            row.dictionaryEntry == null && (row.unit == null || row.unit == QuantityUnit.PIECE) &&
                row.freeTextName?.let { mentions.of(it, inStep = false).isNotEmpty() } == true
        }
        ingredients = ingredientLines
        equipment = equipmentLines.map { it.text }
    }
}
