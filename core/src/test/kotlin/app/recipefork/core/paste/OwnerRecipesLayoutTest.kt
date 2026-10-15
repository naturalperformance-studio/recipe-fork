package app.recipefork.core.paste

import app.recipefork.core.recipe.RecipeLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The owner's own Markdown recipes (an Obsidian vault), copied verbatim into test resources. */
class OwnerRecipesLayoutTest {
    private fun read(name: String): TextLayout {
        val text = checkNotNull(javaClass.getResource("/paste/layout/$name")).readText()
        return assertNotNull(TextLayout.read(text), name)
    }

    // Scenario 1: title, servings, nine ingredients.
    @Test
    fun `the yogurt burger sauce reads as title, servings, nine ingredients, three steps and notes`() {
        val layout = read("burger-sauce-yogurt.md")
        assertEquals("Бургерный соус на йогурте", layout.title)
        assertEquals(RecipeLanguage.RU, layout.language)
        assertEquals(4, layout.servings) // "~160 г — на 4 закрытых бургера"
        assertEquals(
            listOf(
                "100 г греческого йогурта (или мацони, откинутого на марлю — см. заметки)",
                "20 г томатной пасты",
                "5 г горчицы",
                "15 г мелко рубленного маринованного огурчика",
                "½ зубчика чеснока, натереть",
                "1 г паприки",
                "3–5 г мёда",
                "3 г крахмала",
                "щепотка соли",
            ),
            layout.ingredients.map { it.text },
        )
        assertTrue(layout.ingredients.all { it.group == null })
        assertEquals(3, layout.steps.size)
        assertEquals("Крахмал. Размешать крахмал с ложкой йогурта до гладкости, чтобы не было комков.", layout.steps[0])
        assertEquals(
            listOf(
                "~160 г — на 4 закрытых бургера",
                "Замена майонезно-кетчупового соуса. Густой, чтобы не размачивать тесто при запекании.",
            ),
            layout.description,
        )
        assertEquals(4, layout.notes.size)
        assertTrue(layout.notes[0].startsWith("Крахмал не даёт йогурту свернуться"))
        assertEquals("Хранение: холодильник, 2 дня.", layout.notes[3])
    }

    // Scenario 3: sub-headings become ingredient groups; a "Посуда" section is the equipment.
    @Test
    fun `the cheese chicken burgers read their bold sub-headings as groups and their dishes as equipment`() {
        val layout = read("chicken-burgers-cheese.md")
        assertEquals("Закрытые куриные бургеры с сыром в аэрогриле", layout.title)
        assertEquals(
            listOf("Тесто" to 1, "Котлеты" to 7, "Начинка" to 5, "Смазка" to 2),
            layout.ingredients.groupBy { it.group }.map { (group, lines) -> group to lines.size },
        )
        assertEquals("450 г готового дрожжевого теста", layout.ingredients.first().text)
        assertEquals(
            listOf("мясорубка — если фарш делаете сами", "толстая сковорода — лук и котлеты", "пиала диаметром 12–13 см — для сборки", "аэрогриль, пергамент"),
            layout.equipment,
        )
        assertEquals(10, layout.steps.size)
        assertEquals("Отдых 5–10 минут перед нарезкой.", layout.steps.last())
        assertEquals(4, layout.notes.size)
        assertNull(layout.servings, "\"4 бургера\" counts pieces, not servings")
    }

    @Test
    fun `the cappuccino keeps steps under step sub-headings, reads one cup as one serving and takes its equipment line`() {
        val layout = read("cappuccino.md")
        assertEquals(1, layout.servings) // "На одну чашку:"
        assertEquals(3, layout.ingredients.size)
        assertTrue(layout.ingredients.all { it.group == null })
        assertEquals("Кофе мелкого помола, почти в пыль — 1–2 чайные ложки с горкой", layout.ingredients[0].text)
        assertEquals(listOf("турка, ручной капучинатор, по желанию мелкое ситечко"), layout.equipment)
        assertEquals(10, layout.steps.size)
        assertEquals("Влить в чашку кофе, сверху — молоко с пеной.", layout.steps.last())
        assertTrue(layout.steps.none { it == "Молочная пена" || it == "Сборка" })
        assertEquals(5, layout.notes.size)
    }

    @Test
    fun `chikhirtma takes its servings from the ingredient heading, skips the nutrition table and keeps storage as a note`() {
        val layout = read("chikhirtma.md")
        assertEquals(2, layout.servings) // "## Ингредиенты (2 порции)"
        assertEquals(11, layout.ingredients.size)
        assertEquals("1/4 ч. л. чёрного перца", layout.ingredients[9].text)
        assertEquals(8, layout.steps.size)
        assertTrue(layout.description.none { it.contains('|') || it.contains("Ккал") })
        assertTrue(layout.notes.last().startsWith("Трое суток в холодильнике."))
    }

    @Test
    fun `the lemon bars read their ingredient table as name and quantity lines and skip the nutrition table`() {
        val layout = read("lemon-bars.md")
        assertEquals(
            listOf(
                IngredientLine("Сливочное масло 100 г", "Тесто"),
                IngredientLine("Мацони 1–3% 50 г", "Тесто"),
                IngredientLine("Сахар 180 г", "Тесто"),
                IngredientLine("Ванильный сахар 5 г", "Тесто"),
                IngredientLine("Цедра 1 лимона", "Тесто"),
                IngredientLine("Яйца 2 шт", "Тесто"),
                IngredientLine("Лимонный сок 40 г", "Тесто"),
                IngredientLine("Пшеничная мука 110 г", "Тесто"),
                IngredientLine("Овсяные хлопья 40 г", "Тесто"),
                IngredientLine("Кукурузный крахмал 15 г", "Тесто"),
                IngredientLine("Разрыхлитель 3 г", "Тесто"),
                IngredientLine("Соль щепотка", "Тесто"),
                IngredientLine("40 г сахарной пудры", "Глазурь"),
                IngredientLine("10–12 г лимонного сока", "Глазурь"),
            ),
            layout.ingredients,
        )
        assertTrue(layout.description.none { it.contains("Ккал") || it.contains("385") })
        assertEquals(8, layout.equipment.size)
    }

    @Test
    fun `a sentence ending in a colon above the ingredient list is description, not an ingredient`() {
        val layout = read("buckwheat.md")
        assertEquals(5, layout.ingredients.size)
        assertEquals("соль, чёрный перец", layout.ingredients.last().text)
        assertTrue("На кружку сухой гречки, это примерно 200 г:" in layout.description)
        assertEquals(5, layout.steps.size)
    }

    @Test
    fun `a heading level with the method and numbered lines under it does not close the steps`() {
        for (name in listOf("syrniki-peach-freezer.md", "syrniki-apple-freezer.md")) {
            val layout = read(name)
            // "## Утром: аэрогриль": the bake from the freezer stays in the method.
            assertTrue("180 °C, 25 минут. Не размораживать." in layout.steps, name)
            assertTrue("Разогреть аэрогриль 3 минуты — на холодном старте заготовка успевает потечь." in layout.steps, name)
            assertTrue("Отдых 5 минут. Фруктовая начинка держит температуру и обжигает." in layout.steps, name)
            assertTrue(layout.notes.none { it.startsWith("Разогреть аэрогриль") }, name)
            // "## Утром: на сковороде (…)" has no numbered line: it closes the method and is kept as notes.
            assertTrue("Сковорода на слабом огне, 10 г масла, прогрев 2 минуты." in layout.notes, name)
            assertTrue(layout.steps.none { it.startsWith("Сковорода на слабом огне") }, name)
            assertEquals("Заготовки хранятся в морозилке до месяца.", layout.notes.last(), name)
        }
        assertEquals(25, read("syrniki-peach-freezer.md").steps.size)
    }

    @Test
    fun `variant sections after the notes heading stay notes, numbered lines and all`() {
        val pelmeni = read("pelmeni-wok.md")
        assertEquals(6, pelmeni.steps.size)
        assertTrue("Когда вода выкипит, жарить ещё 2–3 минуты, встряхивая." in pelmeni.notes)
        assertTrue("Опустить в кипяток и помешать." in pelmeni.notes)

        val chicken = read("chicken-sweet-sour.md")
        assertEquals(14, chicken.steps.size)
        assertTrue(chicken.steps.last().startsWith("Соединить. Вернуть курицу в сковороду"))
        assertTrue("Варить под крышкой на минимальном огне 15 минут. Не открывать и не мешать." in chicken.notes)
        assertEquals(2, chicken.servings)
    }

    @Test
    fun `a colon line between ingredient lines opens a group`() {
        val layout = read("syrniki-tiramisu.md")
        val base = "Сырники-основа (базовое тесто, 8–10 сырников)"
        assertEquals(listOf(null to 4, base to 7), layout.ingredients.groupBy { it.group }.map { (group, lines) -> group to lines.size })
        assertEquals(IngredientLine("400 г творога 5% (сухого, не пастообразного)", base), layout.ingredients[4])
        assertTrue(layout.description.none { it.startsWith("Сырники-основа") })
    }

    @Test
    fun `the shopping list is no recipe`() {
        val text = checkNotNull(javaClass.getResource("/paste/layout/burgers-shopping-list.md")).readText()
        assertNull(TextLayout.read(text))
    }
}
