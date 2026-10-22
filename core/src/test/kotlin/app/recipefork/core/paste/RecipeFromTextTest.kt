package app.recipefork.core.paste

import app.recipefork.core.file.EntryLabels
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-278: a pasted text read whole into a new recipe of the owner's that passes the rules of a recipe file. */
class RecipeFromTextTest {
    private val shipped = IngredientLineReaderTest.shipped
    private val at = Instant.parse("2026-10-15T10:00:00Z")
    private val owner = AccountId("owner")

    private fun read(text: String): Recipe? = RecipeFromText.read(text, shipped, at, owner)?.also(::assertPasses)

    private fun owners(name: String) = File("src/test/resources/paste/layout/$name").readText()

    private fun ingredient(labelEn: String) = shipped.ingredients.single { it.labelEn == labelEn }.id

    private fun equipment(recipe: Recipe) = recipe.equipment.map { item -> shipped.equipment.single { it.id == item.dictionaryEntry }.labelEn }

    /** [recipe], written as a recipe file's document and read back, breaks no rule of the file. */
    private fun assertPasses(recipe: Recipe) {
        val labels = shipped.equipment.associate { it.id to EntryLabels(it.labelRu, it.labelEn) }
        val content = RecipeFileContent.of(recipe, parentInBook = null, ownerNickname = null, equipmentLabels = labels::get)
        assertEquals(emptyList(), RecipeFileCheck.problems(content), recipe.title)
        assertEquals(emptyList(), RecipeFileCheck.problems(RecipeFile.decode(RecipeFile.encode(content))), recipe.title)
    }

    // v0.44, T-318: a pasted text never gives a recipe a finished weight, even where it states a yield in grams; reading
    // one is out of the increment's scope, and the author weighs the dish.
    @Test
    fun aPastedTextGivesNoFinishedWeight() {
        val recipe = assertNotNull(read("Плов\n\nВыход: 800 г\nПорций: 2\n\nРис — 300 г\nМорковь — 200 г\n\nОбжарить морковь. Добавить рис."))
        assertEquals(2, recipe.servings)
        assertNull(recipe.finishedWeightGrams)
    }

    // Scenario 1.
    @Test
    fun theOwnersBurgerSauceIsANewRecipeOfTheirs() {
        val recipe = assertNotNull(read(owners("burger-sauce-yogurt.md")))
        assertEquals(listOf(owner, RecipeState.LOCAL, at, at), listOf(recipe.owner, recipe.state, recipe.createdAt, recipe.contentChangedAt))
        assertNull(recipe.sourceText)
        assertEquals("Бургерный соус на йогурте", recipe.title)
        assertEquals(4, recipe.servings)
        assertEquals(RecipeLanguage.RU, recipe.language)
        assertEquals(
            listOf("Greek yogurt", "tomato paste", "mustard", null, "garlic", "paprika", "honey", "starch", "salt").map { it?.let(::ingredient) },
            recipe.ingredients.map { it.dictionaryEntry },
        )
        assertEquals((0 until 9).toList(), recipe.ingredients.map { it.position })
        assertEquals(listOf(0, 1, 2), recipe.steps.map { it.position })
        // The references in it (T-283) stand between spaces of its own; a row named without a quantity has its reference
        // after the name by default (the owner's choice at the checkpoint, 2026-10-16).
        assertEquals("Крахмал. Размешать крахмал  с ложкой йогурта  до гладкости, чтобы не было комков.", recipe.steps[0].text)
        // The description lines, then the notes.
        assertTrue(recipe.description.startsWith("~160 г — на 4 закрытых бургера\nЗамена майонезно-кетчупового соуса."), recipe.description)
        assertTrue(recipe.description.endsWith("\nХранение: холодильник, 2 дня."), recipe.description)
        assertEquals(emptyList(), recipe.ingredientGroups)
    }

    // Scenarios 2 and 3: timers on the steps, sub-headings as groups, the equipment section and the steps' equipment.
    @Test
    fun theCheeseBurgersReadTheirGroupsEquipmentAndTimers() {
        val recipe = assertNotNull(read(owners("chicken-burgers-cheese.md")))
        assertEquals(listOf("Тесто" to 0, "Котлеты" to 1, "Начинка" to 8, "Смазка" to 13), recipe.ingredientGroups.map { it.name to it.firstPosition })
        // "пиала" names no entry: dropped. The section first, then the steps.
        assertEquals(listOf("Meat grinder", "Frying pan", "Air fryer", "Parchment paper"), equipment(recipe).take(4))
        assertTrue(recipe.equipment.all { it.dictionaryEntry != null && it.qualifier == null })
        assertEquals(listOf(10L, 2L), recipe.steps[2].timers.map { it.duration.inWholeMinutes })
        assertEquals(listOf(30L), recipe.steps[8].timers.map { it.duration.inWholeMinutes })
        assertTrue(recipe.steps[8].text.contains("160 °C, 30 минут"), "the step keeps its words")
        assertNull(recipe.servings, "the text states none: the editor asks")
    }

    // Scenario 5.
    @Test
    fun anEnglishTextReadsTheSameWay() {
        val recipe = assertNotNull(
            read(
                """
                Butter Biscuits
                Serves 4

                Ingredients
                - 2 cups flour
                - 1 tbsp butter
                - 1 cup milk

                Method
                1. Preheat the oven to 200 °C.
                2. Rub the butter into the flour, then whisk in the milk.
                3. Melt more butter in a large skillet.
                4. Bake for 25 min, then rest for 1 hr 15 mins.
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("Butter Biscuits", 4, RecipeLanguage.EN), listOf(recipe.title, recipe.servings, recipe.language))
        assertEquals(listOf("flour", "butter", "milk").map(::ingredient), recipe.ingredients.map { it.dictionaryEntry })
        assertEquals(listOf("Oven", "Frying pan"), equipment(recipe))
        assertEquals(listOf(emptyList(), emptyList(), emptyList(), listOf(25L, 75L)), recipe.steps.map { s -> s.timers.map { it.duration.inWholeMinutes } })
    }

    // Scenario 7.
    @Test
    fun aTextWithNoRecipeGivesNone() {
        assertNull(read("Купить хлеб, молоко и яйца. Позвонить маме."))
        assertNull(read(owners("burgers-shopping-list.md")), "a shopping list is no recipe")
    }

    @Test
    fun aTextWithNoTitleIsARecipeAllTheSame() {
        val recipe = assertNotNull(read("- 200 г муки\n- 2 яйца\n\n1. Смешать муку и яйца.\n2. Жарить на сковороде 5 минут."))
        assertEquals("Рецепт", recipe.title)
        assertEquals(listOf("Frying pan"), equipment(recipe))
    }

    @Test
    fun whatIsOverALimitIsCutToIt() {
        val group = "Тесто ".repeat(50).trim()
        val text = "# " + "Очень длинное название ".repeat(10) + "\n\n" + "Описание. ".repeat(300) + "\n\n" +
            "### Ингредиенты\n\n#### $group\n\n" + (1..60).joinToString("\n") { "- $it г муки" } + "\n\n" +
            "### Приготовление\n\n" + (1..60).joinToString("\n") { "$it. Смешать всё и подождать $it минут." }
        val recipe = assertNotNull(read(text))
        assertEquals(RecipeBounds.MAX_INGREDIENTS, recipe.ingredients.size)
        assertEquals(RecipeBounds.MAX_STEPS, recipe.steps.size)
        assertTrue(recipe.title.length <= RecipeBounds.MAX_TITLE && recipe.title.endsWith("…"), recipe.title)
        assertTrue(recipe.description.length <= RecipeBounds.MAX_DESCRIPTION && recipe.description.endsWith("…"))
        val name = recipe.ingredientGroups.single().name
        assertTrue(name.length <= RecipeBounds.MAX_GROUP_NAME && name.startsWith("Тесто Тесто") && name.endsWith("…"), name)
    }

    @Test
    fun aLongStepIsCutAndTheEquipmentToFifteen() {
        val items = listOf("сковороду", "кастрюлю", "ковш", "казан", "турку", "скороварку", "чайник", "крышку", "противень", "скалку",
            "духовку", "плиту", "блендер", "миксер", "мясорубку", "тостер")
        val text = "Тест\n\n### Ингредиенты\n\n- 200 г муки\n\n### Приготовление\n\n" +
            "1. " + "Мешать долго. ".repeat(200) + "\n" + items.joinToString(", ", "2. Достать ", ".")
        val recipe = assertNotNull(read(text))
        assertTrue(recipe.steps[0].text.length <= RecipeBounds.MAX_STEP_TEXT && recipe.steps[0].text.endsWith("…"))
        assertEquals(RecipeBounds.MAX_EQUIPMENT, recipe.equipment.size)
        assertEquals("Frying pan", equipment(recipe).first())
    }

    @Test
    fun everyOwnerRecipeAssemblesAndPassesTheFileRules() {
        val files = File("src/test/resources/paste/layout").listFiles()!!.filter { it.name != "burgers-shopping-list.md" }
        assertEquals(11, files.size)
        files.forEach { file ->
            val recipe = assertNotNull(read(file.readText()), file.name)
            assertTrue(recipe.title.isNotBlank() && recipe.ingredients.isNotEmpty() && recipe.steps.isNotEmpty(), file.name)
        }
    }
}
