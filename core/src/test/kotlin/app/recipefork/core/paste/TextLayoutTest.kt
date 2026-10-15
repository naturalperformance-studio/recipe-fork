package app.recipefork.core.paste

import app.recipefork.core.recipe.RecipeLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TextLayoutTest {
    private fun read(text: String) = assertNotNull(TextLayout.read(text.trimIndent()))

    private val pancakes = """
        Simple Buttermilk Pancakes

        Fluffy pancakes for a lazy weekend morning.

        Serves 4

        Ingredients
        2 cups all-purpose flour
        2 tbsp sugar
        1 tsp baking powder
        2 cups buttermilk
        1 tbsp butter, melted
        Salt to taste

        Directions
        1. Whisk the flour, sugar and baking powder in a large bowl.
        2. Stir in the buttermilk and melted butter until just combined.
        3. Cook on a hot griddle for 2 min per side.

        Notes
        Leftover batter keeps for a day in the fridge.
    """

    @Test
    fun `a plain English web text is split by its headings`() {
        val layout = read(pancakes)
        assertEquals(RecipeLanguage.EN, layout.language)
        assertEquals("Simple Buttermilk Pancakes", layout.title)
        assertEquals(4, layout.servings)
        assertEquals(listOf("Fluffy pancakes for a lazy weekend morning."), layout.description)
        assertEquals(
            listOf("2 cups all-purpose flour", "2 tbsp sugar", "1 tsp baking powder", "2 cups buttermilk", "1 tbsp butter, melted", "Salt to taste"),
            layout.ingredients.map { it.text },
        )
        assertEquals(
            listOf(
                "Whisk the flour, sugar and baking powder in a large bowl.",
                "Stir in the buttermilk and melted butter until just combined.",
                "Cook on a hot griddle for 2 min per side.",
            ),
            layout.steps,
        )
        assertEquals(listOf("Leftover batter keeps for a day in the fridge."), layout.notes)
    }

    // Scenario 5: no headings at all; the shape of the lines tells the parts apart.
    @Test
    fun `without headings a run of quantity lines is the ingredients and the sentences after it are the steps`() {
        val layout = read(
            """
            Quick Butter Cookies
            Crisp at the edges, soft in the middle.
            2 cups flour
            1 tbsp butter
            1/2 cup sugar
            1 egg
            a pinch of salt
            Mix the butter and sugar until pale, then beat in the egg.
            Fold in the flour and salt.
            Bake for 25 min.
            """,
        )
        assertEquals("Quick Butter Cookies", layout.title)
        assertEquals(listOf("Crisp at the edges, soft in the middle."), layout.description)
        assertEquals(listOf("2 cups flour", "1 tbsp butter", "1/2 cup sugar", "1 egg", "a pinch of salt"), layout.ingredients.map { it.text })
        assertEquals(
            listOf("Mix the butter and sugar until pale, then beat in the egg.", "Fold in the flour and salt.", "Bake for 25 min."),
            layout.steps,
        )
    }

    @Test
    fun `without headings numbered lines after the quantity run are the steps, in Russian too`() {
        val layout = read(
            """
            Сырники
            400 г творога
            1 яйцо
            40 г муки
            щепотка соли
            1. Смешать творог с яйцом и солью
            2. Всыпать муку, сформовать 12 сырников
            3. Жарить по 3 минуты с каждой стороны
            """,
        )
        assertEquals(RecipeLanguage.RU, layout.language)
        assertEquals(listOf("400 г творога", "1 яйцо", "40 г муки", "щепотка соли"), layout.ingredients.map { it.text })
        assertEquals(3, layout.steps.size)
        assertEquals("Смешать творог с яйцом и солью", layout.steps[0])
        assertNull(layout.servings)
    }

    @Test
    fun `an ingredient heading with numbered lines after the list and no step heading finds the steps by shape`() {
        val layout = read(
            """
            Tomato Pasta
            Ingredients:
            200 g spaghetti
            2 tbsp olive oil
            1 garlic clove
            1. Boil the spaghetti in salted water for 9 minutes.
            2. Fry the garlic in the oil, add the drained pasta and toss.
            """,
        )
        assertEquals(listOf("200 g spaghetti", "2 tbsp olive oil", "1 garlic clove"), layout.ingredients.map { it.text })
        assertEquals(2, layout.steps.size)
    }

    @Test
    fun `steps under headings of their own and no step heading are found by shape, a notes heading still holds notes`() {
        val layout = read(
            """
            # Сырники с начинкой
            ## Ингредиенты
            - 400 г творога
            - 1 яйцо
            ## Часть 1. Тесто
            Смешать творог с яйцом до однородности.
            ## Часть 2. Готовка
            Жарить по 3 минуты с каждой стороны.
            ## Хранение
            Три дня в холодильнике.
            """,
        )
        assertEquals(listOf("400 г творога", "1 яйцо"), layout.ingredients.map { it.text })
        assertEquals(listOf("Смешать творог с яйцом до однородности.", "Жарить по 3 минуты с каждой стороны."), layout.steps)
        assertEquals(listOf("Три дня в холодильнике."), layout.notes)
    }

    @Test
    fun `section headings in Russian and English are recognised in every form`() {
        val ruHeadings = listOf("Вам понадобится:", "**Ингредиенты:**", "## Ингредиенты (на 4 порции)", "Ингредиенты")
        val ruSteps = listOf("Способ приготовления:", "### Приготовление", "Приготовление")
        for (ingredients in ruHeadings) for (steps in ruSteps) {
            val layout = read("Суп\n$ingredients\n- 1 л воды\n- 2 картофелины\n$steps\nСварить картофель в воде 20 минут.")
            assertEquals(listOf("1 л воды", "2 картофелины"), layout.ingredients.map { it.text }, "$ingredients / $steps")
            assertEquals(listOf("Сварить картофель в воде 20 минут."), layout.steps, "$ingredients / $steps")
        }
        for (steps in listOf("Method", "Directions", "Instructions:", "## Preparation")) {
            val layout = read("Soup\nIngredients\n- 1 l water\n- 2 potatoes\n$steps\nBoil the potatoes for 20 minutes.")
            assertEquals(2, layout.ingredients.size, steps)
            assertEquals(listOf("Boil the potatoes for 20 minutes."), layout.steps, steps)
        }
    }

    @Test
    fun `sub-headings under the ingredient heading become groups`() {
        val layout = read(
            """
            # Hand Pies
            ## Ingredients
            For the dough:
            - 250 g flour
            - 125 g cold butter
            **Filling**
            - 2 apples
            - 1 tsp cinnamon
            ## Method
            1. Rub the butter into the flour, add water and chill.
            2. Fill, fold and bake for 30 minutes.
            """,
        )
        assertEquals(
            listOf(
                IngredientLine("250 g flour", "For the dough"),
                IngredientLine("125 g cold butter", "For the dough"),
                IngredientLine("2 apples", "Filling"),
                IngredientLine("1 tsp cinnamon", "Filling"),
            ),
            layout.ingredients,
        )
    }

    @Test
    fun `servings are read from the usual phrasings, and a count of pieces is not servings`() {
        fun servings(line: String) = read("Блюдо\n$line\nИнгредиенты\n- 2 яйца\nПриготовление\nВзбить яйца.").servings
        assertEquals(4, servings("Рецепт на 4 порции."))
        assertEquals(4, servings("Порций: 4"))
        assertEquals(4, servings("Serves 4"))
        assertEquals(4, servings("Serves 4-6"))
        assertEquals(4, servings("Yield: 4 servings"))
        assertEquals(2, servings("Две порции к завтраку."))
        assertNull(servings("Получится 12 сырников."))
        assertNull(servings("Makes 12 cookies"))
        assertNull(servings("Yield: 12 cookies"))
        assertNull(servings("Тесто отдыхает на 2 часа."))
    }

    @Test
    fun `a servings line is read for the servings and not kept as description`() {
        val layout = read("Блюдо\nПорций: 4\nКороткое описание.\nИнгредиенты\n- 2 яйца\nПриготовление\nВзбить яйца.")
        assertEquals(listOf("Короткое описание."), layout.description)
    }

    @Test
    fun `a leading bold step title stays part of the step text`() {
        val layout = read("Соус\nИнгредиенты\n- 3 г крахмала\nПриготовление\n1. **Крахмал.** Размешать крахмал с ложкой йогурта.")
        assertEquals(listOf("Крахмал. Размешать крахмал с ложкой йогурта."), layout.steps)
    }

    @Test
    fun `list markers and emphasis are stripped and the rest of an ingredient line is untouched`() {
        val layout = read(
            """
            Pesto
            Ingredients
            - [ ] 2 cloves *young* garlic
            * 100 g __fresh__ basil, [[Basil|leaves]] only
            • 1/4 ч. л. соли (по вкусу)
            + ½ cup pine nuts — toasted
            1) 50 g parmesan
            Method
            Step 1: Pound the garlic with the salt.
            2. Blend everything with the oil.
            """,
        )
        assertEquals(
            listOf("2 cloves young garlic", "100 g fresh basil, leaves only", "1/4 ч. л. соли (по вкусу)", "½ cup pine nuts — toasted", "50 g parmesan"),
            layout.ingredients.map { it.text },
        )
        assertEquals(listOf("Pound the garlic with the salt.", "Blend everything with the oil."), layout.steps)
    }

    @Test
    fun `the language is the script most of the words are written in`() {
        assertEquals(RecipeLanguage.EN, read("Borscht, my way\nIngredients\n- 1 beet (свёкла)\n- 2 potatoes\nMethod\nBoil everything.").language)
        assertEquals(RecipeLanguage.RU, read("Борщ\nИнгредиенты\n- 1 свёкла\n- 200 г Philadelphia\nПриготовление\nВарить всё.").language)
    }

    // Scenario 7: a text with no recipe in it creates nothing.
    @Test
    fun `a text without ingredient lines or without step lines is no recipe`() {
        assertNull(TextLayout.read("Рецепт по ссылке в профиле! 🔥"))
        assertNull(TextLayout.read(""))
        assertNull(TextLayout.read("Shopping\nIngredients\n- 2 eggs\n- 1 l milk"))
        assertNull(TextLayout.read("Method\n1. Boil the water.\n2. Pour it over the tea."))
        assertNull(TextLayout.read("2 eggs\n1 l milk\n200 g flour"))
    }
}
