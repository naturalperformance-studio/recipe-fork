package app.recipefork.core.paste

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.DictionaryMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T-285: the line shapes of recipe sites the parser read wrong, each on a line written for the test. */
class PasteSiteShapesTest {
    private val shipped = IngredientLineReaderTest.shipped
    private val reader = IngredientLineReader(DictionaryMatch(shipped.ingredients))

    private fun row(text: String): Ingredient = reader.read(IngredientLine(text, null), 0)

    private fun label(id: Long?) = shipped.ingredients.firstOrNull { it.id == id }?.labelEn

    /** What a row says, in the order quantity, unit, entry (its English label) or free text, qualifier, note. */
    private fun shown(text: String) = row(text).let { listOf(it.quantity, it.unit, label(it.dictionaryEntry) ?: it.freeTextName, it.qualifier, it.note) }

    private fun noNumberInTheQualifier(text: String) = assertTrue(row(text).qualifier.orEmpty().none(Char::isDigit), "$text: ${shown(text)}")

    private fun groups(text: String) = assertNotNull(TextLayout.read(text), text).ingredients.map { it.group to it.text }

    @Test
    fun twoEqualAmountsReadTheFirstAndKeepTheSecondInTheNote() {
        assertEquals(listOf(Rational.of(1, 2), CUP, "rolled oats", null, "= 40 г"), shown("Овсяные хлопья 0,5 стакана = 40 г"))
        assertEquals(listOf(Rational.ONE, PIECE, "yellow onion", null, "= 80 г"), shown("Лук репчатый 1 шт. = 80 г"))
        assertEquals(listOf(Rational.of(2), TABLESPOON, "honey", null, "= 34 г"), shown("Мёд 2 ст. л. = 34 г"))
        assertEquals(listOf(Rational.of(500), MILLILITRE, "kefir", null, "= 500 г"), shown("Кефир 500 мл = 500 г"))
        // The amount first, or a clause after the second.
        assertEquals(listOf(Rational.of(2), TABLESPOON, "sugar", null, "= 50 г"), shown("2 ст. л. сахара = 50 г"))
        assertEquals(listOf(Rational.of(2), PIECE, "carrot", null, "= 150 г, натереть"), shown("Морковь 2 шт. = 150 г, натереть"))
        assertEquals(listOf(Rational.of(100), GRAM, "hard cheese", null, "= 1 стакан"), shown("Сыр твёрдый 100 г = 1 стакан"))
        // Two amounts, then the name.
        assertEquals(listOf(Rational.ONE, CUP, "water", null, "= 240 ml"), shown("1 cup = 240 ml water"))
        assertEquals(listOf(Rational.ONE, CUP, "milk", null, "= 250 мл"), shown("1 стакан = 250 мл молока"))
        assertEquals(listOf(Rational.of(1, 2), CUP, "sugar", null, "= 100 г"), shown("0,5 стакана = 100 г сахара"))
        // An amount in a parenthesis is the quantity as before, its equal sign kept in the note.
        assertEquals(listOf(Rational.ONE, TEASPOON, "salt", null, "= 6 г"), shown("Соль (1 ч. л. = 6 г)"))
        // A first amount in no unit the reader knows: the second is the quantity, the first the note.
        assertEquals(listOf(Rational.of(15), GRAM, "scallion", null, "3 пучочка"), shown("Лук зелёный 3 пучочка = 15 г"))
        listOf(
            "Помидоры черри 12 шт. = 240 г", "Яйцо куриное 3 шт = 165 г", "Чеснок 2 зубчика = 10 г", "Соль 0,5 ч. л. = 4 г",
            "Вода 1 л = 1000 г", "Лук зелёный 3 пера = 15 г", "Соль = 5 г",
        ).forEach(::noNumberInTheQualifier)
    }

    @Test
    fun aFractionSlashBetweenDigitsIsASlash() {
        assertEquals(listOf(Rational.of(1, 2), TEASPOON, "salt", null, null), shown("1⁄2 tsp salt"))
        assertEquals(listOf(Rational.of(3, 2), CUP, "flour", null, null), shown("1 1⁄2 cups flour"))
        assertEquals(listOf(Rational.of(1, 2), PIECE, "red onion", null, null), shown("1⁄2 red onion"))
        assertEquals(listOf(Rational.of(3, 4), CUP, "sugar"), shown("3⁄4 cup sugar").take(3))
        assertEquals(listOf(Rational.of(1, 3), CUP, "milk"), shown("1⁄3cup milk").take(3))
        assertEquals(listOf(Rational.of(1, 2), TABLESPOON, "salt"), shown("Соль — 1⁄2 ст. л.").take(3))
        val line = assertNotNull(TextLayout.read("Bread\n\nIngredients\n- 2 1⁄4 tsp baking soda\n\nMethod\n1. Mix.")).ingredients.single()
        assertEquals(listOf(Rational.of(9, 4), TEASPOON, "baking soda"), reader.read(line, 0).let { listOf(it.quantity, it.unit, label(it.dictionaryEntry)) })
    }

    @Test
    fun aPackageSizeGoesToTheNoteAndTheNameBinds() {
        assertEquals(listOf(Rational.ONE, PIECE, "cream cheese", "package", "8-ounce"), shown("1 (8-ounce) package cream cheese"))
        assertEquals(listOf(Rational.of(2), PIECE, "tomato", "can", "chopped, 400g"), shown("2 x 400g cans chopped tomatoes"))
        assertEquals(listOf(Rational.of(4), PIECE, "cream cheese", "package", "8-ounce"), shown("4 8-ounce packages cream cheese"))
        assertEquals(listOf(Rational.ONE, PIECE, "spinach", "bag", "100g"), shown("1 x 100g bag spinach"))
        // Fresh lines of the same shapes.
        assertEquals(listOf(Rational.of(3), PIECE, "tuna", "tin", "200 g"), shown("3 × 200 g tins tuna"))
        assertEquals(listOf(Rational.of(2), PIECE, "black beans", "can", "15-oz."), shown("2 15-oz. cans black beans"))
        assertEquals(listOf(Rational.ONE, PIECE, "spaghetti", "box", "1-pound"), shown("1 1-pound box spaghetti"))
        assertEquals(listOf(Rational.of(2), PIECE, "salmon fillet", null, "150g"), shown("2 x 150g salmon fillets"))
        assertEquals(listOf(Rational.of(1, 2), PIECE, "cream cheese", "package", "8-ounce"), shown("1/2 8-ounce package cream cheese"))
        listOf("2 (14.5 oz) cans diced tomatoes", "1 x 500g bag frozen peas", "6 x 330ml bottles beer", "2 1-litre cartons milk")
            .forEach(::noNumberInTheQualifier)
        // A size joined by a hyphen right after the quantity, with no word for a whole thing.
        assertEquals(listOf(Rational.ONE, PIECE, "cream cheese", null, "8-ounce"), shown("1 8-ounce cream cheese"))
        assertEquals(listOf(Rational.of(2), PIECE, "black beans", null, "15-ounce, drained"), shown("2 15-ounce black beans, drained"))
        assertEquals(listOf(Rational.ONE, PIECE, "coconut milk", null, "14-oz"), shown("1 14-oz coconut milk"))
        assertEquals(listOf(Rational.ONE, PIECE, "cream cheese", "block", "8-ounce"), shown("1 8-ounce block cream cheese"))
        listOf("3 6-oz salmon fillets", "1 2-pound chicken", "2 250-g packs butter").forEach(::noNumberInTheQualifier)
        // A unit after the quantity with no "x" and no word for a whole thing after it is the quantity's own.
        assertEquals(listOf(Rational.of(400), GRAM, "beans", "butter, can"), shown("400g can butter beans").take(4))
    }

    @Test
    fun whatFollowsPlusAfterAQuantityGoesToTheNote() {
        assertEquals(listOf(Rational.of(2), CUP, "flour", null, "plus 2 tablespoons"), shown("2 cups plus 2 tablespoons flour"))
        assertEquals(listOf(Rational.ONE, CUP, "sugar", null, "plus 1 tbsp"), shown("1 cup plus 1 tbsp sugar"))
        assertEquals(listOf(Rational.of(1, 3), CUP, "cornstarch", null, "Plus 1 tablespoon"), shown("1/3 cup Plus 1 tablespoon cornstarch"))
        assertEquals(listOf(Rational.of(3), TABLESPOON, "butter", null, "plus extra for greasing"), shown("3 tbsp butter plus extra for greasing"))
        assertEquals(listOf(Rational.of(2), TABLESPOON, "olive oil", null, "plus more for drizzling"), shown("2 tbsp olive oil, plus more for drizzling"))
        assertEquals(listOf(Rational.of(40), GRAM, "flour", null, "плюс 1 ст. л."), shown("40 г муки плюс 1 ст. л."))
        assertEquals(listOf(Rational.of(2), TABLESPOON, "sugar", null, "+ для посыпки"), shown("сахар — 2 ст. л. + для посыпки"))
        // Two amounts of two names joined by "plus" are two rows, as with "+".
        assertEquals(listOf("20 г укропа", "15 г петрушки"), reader.lines(IngredientLine("20 г укропа плюс 15 г петрушки", null)).map { it.text })
        // A "plus" before no amount and no "more" is part of the name; a word that only holds "plus" is no plus.
        assertTrue(row("2 tbsp Whey Plus protein").let { it.note == null && "Plus" in (it.qualifier ?: it.freeTextName).orEmpty() }, "${shown("2 tbsp Whey Plus protein")}")
        assertEquals("surplus", row("1 cup surplus flour").qualifier)
    }

    @Test
    fun aDashIsAPinch() {
        assertEquals(listOf(Rational.ONE, PINCH, "salt"), shown("Dash salt").take(3))
        assertEquals(listOf(Rational.ONE, PINCH, "hot sauce"), shown("a dash of hot sauce").take(3))
        assertEquals(listOf(Rational.ONE, PINCH, "Worcestershire sauce"), shown("1 dash Worcestershire sauce").take(3))
        assertEquals(listOf(Rational.of(2), PINCH, "bitters"), shown("2 dashes Angostura bitters").take(3))
        assertEquals(listOf(Rational.ONE, PINCH, "cinnamon"), shown("dash of cinnamon").take(3))
    }

    @Test
    fun aLineOfCapitalsOrHashesOverIngredientLinesIsAGroup() {
        assertEquals(
            listOf("FISH" to "1 pound cod fillets", "FISH" to "1 cup flour", "LEMON DIPPING SAUCE" to "1/2 cup sour cream"),
            groups("Fish\n\nIngredients:\n- ## FISH\n- 1 pound cod fillets\n- 1 cup flour\n- ## LEMON DIPPING SAUCE\n- 1/2 cup sour cream\n\nInstructions:\n1. Fry the fish."),
        )
        assertEquals(
            listOf("GLAZE" to "1/2 cup honey", "STIR-FRY" to "1 tbsp oil", "STIR-FRY" to "1 cup peas"),
            groups("Shrimp\n\nIngredients:\n- *GLAZE*\n- 1/2 cup honey\n- STIR-FRY\n- 1 tbsp oil\n- 1 cup peas\n\nInstructions:\n1. Fry."),
        )
        assertEquals(
            listOf(null to "2 cups flour", "DILL DIPPING SAUCE" to "1 cup mayo", "FOR THE GLAZE" to "1 cup icing sugar"),
            groups("Cake\n\nIngredients\n2 cups flour\nDILL DIPPING SAUCE\n1 cup mayo\nFOR THE GLAZE:\n1 cup icing sugar\n\nMethod\n1. Bake."),
        )
        val soup = listOf(null to "500 г картофеля", "Соус" to "100 г сметаны")
        assertEquals(soup, groups("Суп\n\nИнгредиенты:\n- 500 г картофеля\n- ## Соус\n- 100 г сметаны\n\nПриготовление:\n1. Сварить."))
        assertEquals(soup, groups("Суп\n\nИнгредиенты:\n- 500 г картофеля\n## Соус\n- 100 г сметаны\n\nПриготовление:\n1. Сварить."))
        assertEquals(
            listOf(null to "2 cups flour", "FOR THE GLAZE" to "1 cup icing sugar", "FOR THE GLAZE" to "2 tbsp milk"),
            groups("Cake\n\n2 cups flour\nFOR THE GLAZE\n1 cup icing sugar\n2 tbsp milk\n\nBake the cake for 20 minutes until golden.\nGlaze it when cool."),
        )
        // In a recipe written in capitals, a line of capitals between ingredient lines is an ingredient.
        val pancakes = assertNotNull(TextLayout.read("PANCAKES\n\nINGREDIENTS\n2 CUPS FLOUR\nSALT\n1 TBSP OLIVE OIL\n2 EGGS\n\nDIRECTIONS\n1. MIX ALL."))
        assertEquals(listOf(null to "2 CUPS FLOUR", null to "SALT", null to "1 TBSP OLIVE OIL", null to "2 EGGS"), pancakes.ingredients.map { it.group to it.text })
        assertEquals(
            listOf(null to "2 CUPS FLOUR", null to "SALT AND PEPPER", null to "2 EGGS"),
            groups("PANCAKES\n\nINGREDIENTS\n- 2 CUPS FLOUR\n- SALT AND PEPPER\n- 2 EGGS\n\nDIRECTIONS\n1. MIX ALL."),
        )
        assertEquals(
            listOf(null to "2 ЯЙЦА", null to "СОЛЬ", null to "200 Г МУКИ", null to "500 МЛ МОЛОКА"),
            groups("БЛИНЫ\n\nИНГРЕДИЕНТЫ\n2 ЯЙЦА\nСОЛЬ\n200 Г МУКИ\n500 МЛ МОЛОКА\n\nПРИГОТОВЛЕНИЕ\n1. СМЕШАТЬ ВСЁ."),
        )
        assertEquals(
            listOf(null to "2 CUPS FLOUR", null to "SALT", null to "2 EGGS"),
            groups("PANCAKES\n\n2 CUPS FLOUR\nSALT\n2 EGGS\n\nMIX ALL OF IT TOGETHER WELL AND FRY FOR 2 MINUTES.\nSERVE HOT."),
        )
        // Only over an ingredient line: capitals over none, or over a line with no quantity, are ingredients.
        assertEquals(listOf(null to "500 г картофеля", null to "СОЛЬ", null to "ПЕРЕЦ"), groups("Суп\n\nИнгредиенты:\n- 500 г картофеля\n- СОЛЬ\n- ПЕРЕЦ\n\nПриготовление:\n1. Сварить."))
        assertEquals(listOf(null to "2 cups flour", null to "SAUCE"), groups("Cake\n\nIngredients\n- 2 cups flour\n- SAUCE\n\nMethod\n1. Bake."))
        // A line of capitals with a digit is no heading.
        assertEquals(listOf(null to "2 EGGS", null to "1 CUP FLOUR"), groups("Cake\n\nIngredients\n- 2 EGGS\n- 1 CUP FLOUR\n\nMethod\n1. Bake."))
    }
}
