package app.recipefork.core.paste

import app.recipefork.core.recipe.RecipeLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

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
        assertEquals(4, servings("Serves: 4"))
        assertEquals(6, servings("**Serves:** 6 | Prep: 10 min"))
        assertEquals(3, servings("**Порций:** 3"))
        assertEquals(4, servings("Yield: 4 servings"))
        assertEquals(2, servings("Две порции к завтраку."))
        assertNull(servings("Получится 12 сырников."))
        assertNull(servings("Makes 12 cookies"))
        assertNull(servings("Yield: 12 cookies"))
        assertNull(servings("Тесто отдыхает на 2 часа."))
    }

    @Test
    fun `an ingredient table with no heading above it gives name and quantity lines, and the steps follow by shape`() {
        val layout = read(
            """
            Tomato Salad
            | Ingredient | Amount | Why |
            |---|---|---|
            | Tomatoes | 300 g | The base |
            | Olive oil | 2 tbsp | Richness |
            | Basil | — | Smell |
            | Salt | to taste | |
            Slice the tomatoes and dress them with the oil and salt.
            """,
        )
        assertEquals(listOf("Tomatoes 300 g", "Olive oil 2 tbsp", "Basil", "Salt to taste"), layout.ingredients.map { it.text })
        assertEquals(listOf("Slice the tomatoes and dress them with the oil and salt."), layout.steps)
    }

    @Test
    fun `a nutrition table stays skipped, under the ingredient heading too`() {
        val layout = read(
            """
            Сырники
            Ингредиенты
            | | Ккал | Белки | Жиры |
            |---|---|---|---|
            | На 100 г | 183 | 16,5 | 7,5 |
            | Всего | ~860 | 78 | 36 |
            - 400 г творога
            Приготовление
            Жарить по 3 минуты с каждой стороны.
            """,
        )
        assertEquals(listOf("400 г творога"), layout.ingredients.map { it.text })
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

    @Test
    fun `pathological lines neither throw nor stall`() {
        val inputs = listOf(
            "Soup\n" + "-".repeat(3200) + "\n- 2 eggs\n- 1 l milk\n1. Mix it.",
            "=".repeat(3200),
            "*".repeat(3200),
            "|---".repeat(1600),
            "|" + "-".repeat(5000) + "|",
            "**" + "a".repeat(10_000) + "**",
            "# " + " ".repeat(10_000) + "x",
            "*a".repeat(5000),
            "_a".repeat(5000),
            "[".repeat(5000) + "](" + "x".repeat(5000),
            "[[".repeat(5000) + "|" + "x".repeat(5000),
            "## Ingredients " + "(".repeat(10_000),
            "на " + "1 ".repeat(5000),
            "a".repeat(20_000) + ".",
            "# x" + " ".repeat(40_000) + "y",
            "# x" + " ".repeat(40_000) + "#".repeat(40_000) + "y",
            ">".repeat(320_000) + " 1 egg",
            "> ".repeat(160_000) + "1 egg",
            "a".repeat(40_000) + ".b.",
            "🔸".repeat(100_000) + " 1 egg",
            "1️⃣".repeat(100_000) + " Mix.",
            "Ingredients\n" + "a — ".repeat(20_000) + "1 g",
            "Ingredients\n" + "x:\n- 1 egg\n".repeat(20_000) + "Method\nMix it.",
            "Method\n" + "## x\n".repeat(20_000) + "1. Mix it.",
            "время " + "1".repeat(40_000),
            "Method\n" + "x:\n".repeat(80_000),
            "Method\n" + "**x**\nMix it.\n".repeat(40_000),
            "Instructions\n" + "Step:\n".repeat(40_000),
            // T-284: eda.ru pairs, servings on two lines, a title's servings.
            "Ингредиенты\n" + "Соль\n1 г\n".repeat(20_000) + "Приготовление\n1. Смешать.",
            "Порции\n".repeat(40_000) + "5\n",
            "Ингредиенты\n" + "Соль\n1 ½ ст. л.\n".repeat(10_000) + "Приготовление\n1. Смешать.",
            "Ингредиенты\nСоль\n" + "1 ".repeat(20_000) + "г\n1. Mix.",
            "Ингредиенты\n" + "1 " + "1".repeat(40_000) + " г\n1. Mix.",
            "Ингредиенты\n" + "~".repeat(40_000) + "1 г\n1. Mix.",
            "Суп (порций " + "1 ".repeat(20_000) + ")\nИнгредиенты\n- 1 egg\n1. Mix.",
        )
        for (input in inputs) {
            val took = measureTime { TextLayout.read(input) }
            assertTrue(took < 2.seconds, "${input.take(20)}… took $took")
        }
        assertEquals(listOf("2 eggs", "1 l milk"), read(inputs[0]).ingredients.map { it.text })
    }

    @Test
    fun `a bare step label line is dropped, and a label inside bold after a bullet is stripped`() {
        val layout = read(
            """
            Easy Flapjacks
            Ingredients
            250g butter
            250g light brown sugar
            350g rolled oats
            Method
            STEP 1
            Heat oven to 200C/180C fan.
            STEP 2
            Melt the butter and sugar, then stir in the oats.
            Step 3:
            Press into a tin and bake for 15 mins.
            """,
        )
        assertEquals(
            listOf("Heat oven to 200C/180C fan.", "Melt the butter and sugar, then stir in the oats.", "Press into a tin and bake for 15 mins."),
            layout.steps,
        )
        val ru = read("Рис\nИнгредиенты\n- 200 г риса\nПриготовление\nШаг 1.\n- **Шаг 2.** Промыть рис.\n**1.** Сварить рис.")
        assertEquals(listOf("Промыть рис.", "Сварить рис."), ru.steps)
    }

    @Test
    fun `a short sentence inside the ingredient run does not start the steps`() {
        val withHeading = read(
            "Omelette\nIngredients\n3 eggs\nSalt and pepper.\n2 tbsp milk\n1 tsp butter\n" +
                "Beat the eggs with the milk, salt and pepper.\nMelt the butter and cook the eggs for 3 minutes.",
        )
        val withoutHeadings = read(
            "Omelette\n3 eggs\nSalt and pepper.\n2 tbsp milk\n1 tsp butter\n" +
                "Beat the eggs with the milk, salt and pepper.\nMelt the butter and cook the eggs for 3 minutes.",
        )
        for (layout in listOf(withHeading, withoutHeadings)) {
            assertEquals(listOf("3 eggs", "Salt and pepper.", "2 tbsp milk", "1 tsp butter"), layout.ingredients.map { it.text })
            assertEquals(
                listOf("Beat the eggs with the milk, salt and pepper.", "Melt the butter and cook the eggs for 3 minutes."),
                layout.steps,
            )
            assertEquals(emptyList(), layout.notes)
        }
    }

    @Test
    fun `a blockquote prefix and a byte order mark are stripped`() {
        val layout = read("\uFEFFPorridge\n> Ingredients\n> - 50 g oats\n> - 200 ml milk\n> Method\n> 1. Simmer the oats in the milk.")
        assertEquals("Porridge", layout.title)
        assertEquals(listOf("50 g oats", "200 ml milk"), layout.ingredients.map { it.text })
        assertEquals(listOf("Simmer the oats in the milk."), layout.steps)
    }

    @Test
    fun `a first line led by a number above an ingredient heading is the title`() {
        for (title in listOf("10-Minute Garlic Noodles", "5 Ingredient Peanut Butter Cookies")) {
            val layout = read("$title\nIngredients\n- 200 g noodles\n- 4 garlic cloves\nMethod\n1. Boil the noodles.")
            assertEquals(title, layout.title)
            assertEquals(2, layout.ingredients.size)
        }
    }

    @Test
    fun `a heading level with the ingredient heading and followed by quantity lines is a group`() {
        val layout = read(
            """
            # Garlic Noodles
            ## Ingredients
            - 200 g noodles
            ## For the sauce
            - 3 tbsp soy sauce
            - 1 tsp sugar
            ## Instructions
            1. Boil the noodles.
            2. Toss with the sauce.
            """,
        )
        assertEquals(
            listOf(
                IngredientLine("200 g noodles", null),
                IngredientLine("3 tbsp soy sauce", "For the sauce"),
                IngredientLine("1 tsp sugar", "For the sauce"),
            ),
            layout.ingredients,
        )
        assertEquals(2, layout.steps.size)
    }

    @Test
    fun `an unmarked sentence among bulleted ingredient lines is a note`() {
        val layout = read(
            """
            ## Ингредиенты
            **Корзинки**
            - 50 г тёртого пармезана
            - 40 г моцареллы

            На одну корзинку — 25 г пармезана и 20 г моцареллы.

            **Начинка**
            - 4 яйца
            ## Приготовление
            1. Выложить сыр в форму.
            """,
        )
        assertEquals(listOf("50 г тёртого пармезана", "40 г моцареллы", "4 яйца"), layout.ingredients.map { it.text })
        assertEquals(listOf("На одну корзинку — 25 г пармезана и 20 г моцареллы."), layout.notes)
    }

    @Test
    fun `a servings line under the ingredients is read for the servings and never becomes an ingredient`() {
        // povarenok.ru: the servings and the time stand under the ingredient list.
        val povarenok = read(
            """
            Сырники из творога
            Ингредиенты
            Творог — 500 г
            Яйцо куриное — 1 шт
            Мука пшеничная — 3 ст. л.
            Сахар — 2 ст. л.
            Количество порций: 6
            Время приготовления: 40 минут
            Приготовление
            Творог размять вилкой, добавить яйцо и сахар.
            Всыпать муку, сформовать сырники и обжарить с двух сторон.
            """,
        )
        assertEquals(6, povarenok.servings)
        assertEquals(
            listOf("Творог — 500 г", "Яйцо куриное — 1 шт", "Мука пшеничная — 3 ст. л.", "Сахар — 2 ст. л."),
            povarenok.ingredients.map { it.text },
        )
        assertEquals(listOf("Время приготовления: 40 минут"), povarenok.description)
        assertEquals(2, povarenok.steps.size)

        // NYT Cooking: the yield heads the ingredient list.
        val nyt = read(
            """
            Simple Roast Chicken
            INGREDIENTS
            Yield: 4 servings
            1 whole chicken, 3 to 4 pounds
            Kosher salt and black pepper
            2 tablespoons unsalted butter
            PREPARATION
            Step 1
            Heat oven to 450 degrees.
            Step 2
            Season the chicken all over and roast for 1 hour.
            """,
        )
        assertEquals(4, nyt.servings)
        assertEquals("1 whole chicken, 3 to 4 pounds", nyt.ingredients.first().text)
        assertEquals(3, nyt.ingredients.size)
        assertEquals(listOf("Heat oven to 450 degrees.", "Season the chicken all over and roast for 1 hour."), nyt.steps)

        // A servings line among the notes is read too; among the steps it stays a step.
        val notes = read("Блюдо\nИнгредиенты\n- 2 яйца\nПриготовление\nВзбить яйца.\nЗаметки\nПорций: 3")
        assertEquals(3, notes.servings)
        assertEquals(emptyList(), notes.notes)
    }

    @Test
    fun `a Telegram post with Russian headings reads as ingredients and steps`() {
        val layout = read(
            "Нам понадобится:\nтворог — 500 г\nяйцо — 1 шт\nсахар — 2 ст. л.\nПриготовление:\nСмешать творог с яйцом и сахаром.\n" +
                "Жарить на сковороде по 3 минуты с каждой стороны.",
        )
        assertEquals(listOf("творог — 500 г", "яйцо — 1 шт", "сахар — 2 ст. л."), layout.ingredients.map { it.text })
        assertEquals(listOf("Смешать творог с яйцом и сахаром.", "Жарить на сковороде по 3 минуты с каждой стороны."), layout.steps)

        for (ingredients in listOf("Потребуется:", "Состав:", "Для сырников нам понадобится:", "Что потребуется", "Нам понадобится")) {
            for (steps in listOf("Инструкция приготовления", "Инструкция приготовления:", "Приготовление:")) {
                val other = read("Сырники\n$ingredients\n- 500 г творога\n- 1 яйцо\n$steps\nЖарить по 3 минуты с каждой стороны.")
                assertEquals(2, other.ingredients.size, "$ingredients / $steps")
                assertEquals(emptyList(), other.description, "$ingredients / $steps")
                assertEquals(listOf("Жарить по 3 минуты с каждой стороны."), other.steps, "$ingredients / $steps")
            }
        }
        assertEquals(null, PasteLines.sectionOf("Составить", bare = true))
        assertEquals(null, PasteLines.sectionOf("Составить меню на неделю"))
    }

    @Test
    fun `a WP Recipe Maker paste reads its box markers and its group lines`() {
        val layout = read(
            """
            Classic Apple Crumble
            A warm, easy dessert for autumn.
            Prep Time 15 minutes
            Cook Time 40 minutes
            Servings 6
            Ingredients
            For the filling
            ▢ 6 apples, peeled and sliced
            ▢ 2 tbsp sugar
            ☐ 1 tsp cinnamon
            Crumble topping
            □ 1 cup flour
            ▢ ½ cup cold butter, cubed
            ▢ ½ cup brown sugar
            Instructions
            Heat the oven to 180C.
            Toss the apples with the sugar and cinnamon and spread them in a dish.
            Rub the butter into the flour and sugar, scatter over the apples and bake for 40 minutes.
            """,
        )
        assertEquals("Classic Apple Crumble", layout.title)
        assertEquals(6, layout.servings)
        assertEquals(listOf("A warm, easy dessert for autumn.", "Prep Time 15 minutes", "Cook Time 40 minutes"), layout.description)
        assertEquals(
            listOf(
                IngredientLine("6 apples, peeled and sliced", "For the filling"),
                IngredientLine("2 tbsp sugar", "For the filling"),
                IngredientLine("1 tsp cinnamon", "For the filling"),
                IngredientLine("1 cup flour", "Crumble topping"),
                IngredientLine("½ cup cold butter, cubed", "Crumble topping"),
                IngredientLine("½ cup brown sugar", "Crumble topping"),
            ),
            layout.ingredients,
        )
        assertEquals(3, layout.steps.size)

        val ru = read(
            "Пирог\nИнгредиенты\nДля теста\n▢ 200 г муки\n▢ 100 г масла\nДля начинки\n▢ 3 яблока\nПриготовление\nИспечь пирог за 40 минут.",
        )
        assertEquals(listOf("Для теста", "Для теста", "Для начинки"), ru.ingredients.map { it.group })
    }

    @Test
    fun `name-first lines are ingredient lines when the ingredients are found by shape`() {
        val layout = read(
            """
            Сырники за 15 минут
            Творог — 500 г
            Яйцо — 1 шт
            Соль — щепотка
            Сахар — по вкусу
            Смешать всё и сформовать сырники.
            Жарить по 3 минуты с каждой стороны.
            """,
        )
        assertEquals("Сырники за 15 минут", layout.title)
        assertEquals(listOf("Творог — 500 г", "Яйцо — 1 шт", "Соль — щепотка", "Сахар — по вкусу"), layout.ingredients.map { it.text })
        assertEquals(2, layout.steps.size)

        val en = read("Rice Pudding\nMilk – 500 ml\nRice – 80 g\nSugar – 40 g\nSimmer everything for 40 minutes, stirring often.")
        assertEquals(listOf("Milk – 500 ml", "Rice – 80 g", "Sugar – 40 g"), en.ingredients.map { it.text })

        // A colon heading above or inside such a run is the group of the lines below it.
        val groups = read("Пирог\nДля теста:\nМука — 200 г\nМасло — 100 г\nДля начинки:\nЯблоки — 3 шт\nИспечь пирог за 40 минут.")
        assertEquals(
            listOf(
                IngredientLine("Мука — 200 г", "Для теста"),
                IngredientLine("Масло — 100 г", "Для теста"),
                IngredientLine("Яблоки — 3 шт", "Для начинки"),
            ),
            groups.ingredients,
        )
        assertEquals(emptyList(), groups.description)
    }

    @Test
    fun `Telegram emoji bullets, keycap numbers and bare number lines are markers, the closing wish is a note`() {
        val layout = read(
            """
            🍰 Ленивые сырники
            🛒 Ингредиенты:
            🔸 Творог — 500 г
            ▪️ Яйцо — 1 шт
            ✅ Сахар — 2 ст. л.
            🔹Мука — 3 ст. л.
            • Соль — щепотка
            👩‍🍳 Приготовление:
            1️⃣ Смешать творог, яйцо и сахар.
            2️⃣ Всыпать муку и соль, вымесить.
            1
            Сформовать сырники.
            2
            Жарить по 3 минуты с каждой стороны.
            Приятного аппетита! 😋
            #рецепты #сырники
            """,
        )
        assertEquals("Ленивые сырники", layout.title)
        assertEquals(
            listOf("Творог — 500 г", "Яйцо — 1 шт", "Сахар — 2 ст. л.", "Мука — 3 ст. л.", "Соль — щепотка"),
            layout.ingredients.map { it.text },
        )
        assertEquals(
            listOf(
                "Смешать творог, яйцо и сахар.",
                "Всыпать муку и соль, вымесить.",
                "Сформовать сырники.",
                "Жарить по 3 минуты с каждой стороны.",
            ),
            layout.steps,
        )
        assertEquals(listOf("Приятного аппетита! 😋"), layout.notes)
        val tea = read("Чай\nИнгредиенты\n- 1 пакетик чая\nПриготовление\nЗаварить чай.\n🥰 Приятного аппетита!")
        assertEquals(listOf("Приятного аппетита!"), tea.notes)
    }

    @Test
    fun `a colon line stating the servings above a shape-found ingredient run is not a group`() {
        val layout = read("Сырники\nНа 4 порции:\n500 г творога\n1 яйцо\n2 ст. л. сахара\nСмешать всё и сформовать сырники.\nЖарить по 3 минуты.")
        assertEquals(4, layout.servings)
        assertEquals(listOf("На 4 порции"), layout.description)
        assertEquals(listOf("500 г творога", "1 яйцо", "2 ст. л. сахара"), layout.ingredients.map { it.text })
        assertTrue(layout.ingredients.all { it.group == null })
    }

    @Test
    fun `only a time line is description, a storage time under the notes stays a note`() {
        val layout = read(
            "Сырники\nВремя приготовления: 40 минут\nИнгредиенты\n- 500 г творога\nПриготовление\nЖарить по 3 минуты.\n" +
                "Заметки\nВремя хранения: 3 дня в холодильнике",
        )
        assertEquals(listOf("Время приготовления: 40 минут"), layout.description)
        assertEquals(listOf("Время хранения: 3 дня в холодильнике"), layout.notes)
    }

    @Test
    fun `a time line names its kind of time and needs no colon after it`() {
        val underHeading = read(
            "Сырники\nИнгредиенты\n- 500 г творога\n- 1 яйцо\nВремя приготовления около 40 минут\nПриготовление\nЖарить по 3 минуты.",
        )
        assertEquals(listOf("500 г творога", "1 яйцо"), underHeading.ingredients.map { it.text })
        assertEquals(listOf("Время приготовления около 40 минут"), underHeading.description)
        val byShape = read(
            "Сырники\n500 г творога\n1 яйцо\nВремя запекания в духовке: 40 минут\nСмешать всё и сформовать сырники.\nЖарить по 3 минуты.",
        )
        assertEquals(listOf("500 г творога", "1 яйцо"), byShape.ingredients.map { it.text })
        assertEquals(listOf("Время запекания в духовке: 40 минут"), byShape.description)
        assertFalse(PasteLines.timeLine("Время жаркими днями 2 часа")) // a kind of time is a whole word
    }

    @Test
    fun `a servings heading inside the ingredient run ends the group above it`() {
        val layout = read("Торт\nДля коржа:\n200 г муки\n2 яйца\nКрем на 1 торт:\n200 г сливок\n100 г сахара\nСмешать всё.\nВыпекать 30 минут.")
        assertEquals(listOf("Для коржа", "Для коржа", null, null), layout.ingredients.map { it.group })
        val headed = read(
            "Торт\nИнгредиенты\n**Для коржа**\n- 200 г муки\n**Крем на 1 торт**\n- 200 г сливок\nПриготовление\nСмешать всё.",
        )
        assertEquals(listOf("Для коржа", null), headed.ingredients.map { it.group })
    }

    @Test
    fun `Состав and Инструкция open a section only as the whole heading`() {
        assertEquals(Section.INGREDIENTS, PasteLines.sectionOf("Состав:"))
        assertEquals(Section.INGREDIENTS, PasteLines.sectionOf("Состав"))
        assertEquals(Section.STEPS, PasteLines.sectionOf("Инструкция"))
        assertEquals(Section.STEPS, PasteLines.sectionOf("Инструкция приготовления"))
        assertEquals(Section.STEPS, PasteLines.sectionOf("Инструкция по приготовлению:"))
        assertNull(PasteLines.sectionOf("Состав на 100 г:"))
        assertNull(PasteLines.sectionOf("Инструкция по хранению:"))
        val layout = read(
            "Сырники\nИнгредиенты\n- 500 г творога\nПриготовление\nЖарить по 3 минуты.\nИнструкция по хранению:\n" +
                "Хранить в холодильнике два дня.",
        )
        assertEquals(listOf("Жарить по 3 минуты.", "Хранить в холодильнике два дня."), layout.steps)
    }

    @Test
    fun `a Markdown heading with a leading emoji is recognised`() {
        val layout = read("# Сырники\n## 🛒 Ингредиенты\n- 500 г творога\n- 1 яйцо\n## 👩‍🍳 Приготовление\n1. Жарить по 3 минуты.")
        assertEquals("Сырники", layout.title)
        assertEquals(listOf("500 г творога", "1 яйцо"), layout.ingredients.map { it.text })
        assertEquals(listOf("Жарить по 3 минуты."), layout.steps)
    }

    @Test
    fun `in an unmarked list a For line is a group only above an ingredient line and without a number of its own`() {
        val layout = read(
            """
            Сырники
            Ингредиенты
            For 4 servings
            Для теста
            500 г творога
            1 яйцо
            Для жарки масло
            Соль
            Приготовление
            Смешать всё и жарить по 3 минуты.
            """,
        )
        assertEquals(4, layout.servings)
        assertEquals(
            listOf(
                IngredientLine("500 г творога", "Для теста"),
                IngredientLine("1 яйцо", "Для теста"),
                IngredientLine("Для жарки масло", "Для теста"),
                IngredientLine("Соль", "Для теста"),
            ),
            layout.ingredients,
        )
    }
}
