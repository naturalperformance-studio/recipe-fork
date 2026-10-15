package app.recipefork.core.paste

/**
 * The ingredient lines of a pasted text as its rows will read them, the dictionary at hand: each line as the lines
 * [IngredientLineReader.lines] makes of it ("Крем: 200 г мацони" under its label, "20 г укропа + 15 г петрушки" as two),
 * and a line under the ingredient heading that names equipment and no ingredient, with no quantity ("аэрогриль",
 * "кофемолка или мощный блендер"), set apart as an equipment line.
 */
internal class IngredientSection(lines: List<IngredientLine>, reader: IngredientLineReader, mentions: EquipmentMentions) {
    val ingredients: List<IngredientLine>
    val equipment: List<String>

    init {
        val (equipmentLines, ingredientLines) = lines.flatMap(reader::lines).partition { line ->
            val row = reader.read(line, 0)
            row.dictionaryEntry == null && row.quantity == null && row.enteredQuantity == null &&
                mentions.of(line.text, inStep = false).isNotEmpty()
        }
        ingredients = ingredientLines
        equipment = equipmentLines.map { it.text }
    }
}
