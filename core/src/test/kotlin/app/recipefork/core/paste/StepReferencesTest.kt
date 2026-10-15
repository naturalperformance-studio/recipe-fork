package app.recipefork.core.paste

import app.recipefork.core.file.EntryLabels
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.IngredientShares
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepText
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.search.DictionaryMatch
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** T-283: the quantities and the ingredients a pasted recipe's steps name, read as references to its rows. */
class StepReferencesTest {
    private val shipped = IngredientLineReaderTest.shipped

    private fun read(text: String, firstMention: FirstMention = FirstMention.BEFORE): Recipe =
        assertNotNull(RecipeFromText.read(text, shipped, Instant.EPOCH, AccountId("owner"), firstMention)).also(::assertPasses)

    private fun assertPasses(recipe: Recipe) {
        val labels = shipped.equipment.associate { it.id to EntryLabels(it.labelRu, it.labelEn) }
        val content = RecipeFileContent.of(recipe, parentInBook = null, ownerNickname = null, equipmentLabels = labels::get)
        assertEquals(emptyList(), RecipeFileCheck.problems(content), recipe.title)
        assertEquals(emptyList(), RecipeFileCheck.problems(RecipeFile.decode(RecipeFile.encode(content))), recipe.title)
    }

    /** The steps as they read at the authored scale, each reference in brackets. */
    private fun steps(recipe: Recipe, language: InterfaceLanguage = InterfaceLanguage.RUSSIAN): List<String> {
        val authored = ReferenceText.authored(recipe.ingredients, language)
        return recipe.steps.map { step -> StepText.render(step) { "[" + authored(it) + "]" }.replace(StepText.NO_BREAK_SPACE, ' ') }
    }

    private fun ru(rows: String, vararg steps: String) =
        "Тест\n\n### Ингредиенты\n\n$rows\n\n### Приготовление\n\n" + steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")

    // Scenario 2: a quantity before a name is a reference to the row, a part of it, scaling with the servings.
    @Test
    fun aQuantityBeforeANameIsAReferenceToPartOfTheRow() {
        val recipe = read(ru("- 200 г сахара\n- 300 г муки", "Добавить 100 г сахара и перемешать."))
        val step = recipe.steps.single()
        assertEquals("Добавить  сахара и перемешать.", step.text)
        val reference = step.ingredientReferences.single()
        assertEquals(listOf(0, 9), listOf(reference.ingredientPosition, reference.textOffset))
        assertEquals(IngredientShare(Rational.of(100), QuantityUnit.GRAM), reference.share)
        assertEquals(listOf("Добавить [100 г] сахара и перемешать."), steps(recipe))
        val doubled = IngredientShares.display(reference.share!!, recipe.ingredients[0], ScaleFactor.of(Rational.of(2)))!!
        assertEquals("200 г", QuantityText.of(doubled, InterfaceLanguage.RUSSIAN))
    }

    @Test
    fun theWholeAmountInAnyUnitOfItsDimensionIsTheWholeRow() {
        val recipe = read(ru("- 200 г сахара\n- 1 кг муки", "Добавить 200 г сахара.", "Всыпать 1000 г муки.", "Посолить."))
        assertEquals(listOf("Добавить [200 г] сахара.", "Всыпать [1 кг] муки.", "Посолить."), steps(recipe))
        assertEquals(listOf(null, null), recipe.steps.take(2).map { it.ingredientReferences.single().share })
    }

    @Test
    fun aQuantityTheRowCannotReadStaysAsTyped() {
        val recipe = read(ru("- 200 г сахара\n- соль по вкусу", "Добавить 2 ст. л. сахара.", "Добавить 300 г сахара.", "Добавить 1 ч. л. соли."))
        assertEquals(listOf("Добавить 2 ст. л. сахара.", "Добавить 300 г сахара.", "Добавить 1 ч. л. соли."), recipe.steps.map { it.text })
        assertTrue(recipe.steps.all { it.ingredientReferences.isEmpty() }, "named with a quantity: no reference by the name either")
    }

    @Test
    fun spoonsGlyphsWordsBetweenAndPiecesReadAsTheRowsDo() {
        val recipe = read(ru("- 1 ч. л. соли\n- 4 яйца\n- 200 г сахара", "Добавить ½ ч. л. соли, 2 яйца и 50 г мелкого сахара."))
        assertEquals(listOf("Добавить [½ ч. л.] соли, [2 шт] яйца и [50 г] мелкого сахара."), steps(recipe))
        assertEquals(
            listOf(IngredientShare(Rational.of(1, 2), QuantityUnit.TEASPOON), IngredientShare(Rational.of(2), QuantityUnit.PIECE)),
            recipe.steps.single().ingredientReferences.take(2).map { it.share },
        )
    }

    // Scenario 5.
    @Test
    fun anEnglishTextReadsTheSameWay() {
        val recipe = read(
            "Pancakes\n\nIngredients\n- 2 cups flour\n- 1 tsp salt\n- 1 cup milk\n\nMethod\n" +
                "1. Add 1 cup of flour and 1 tsp salt.\n2. Whisk in the milk, then the rest of the flour.",
        )
        assertEquals(
            listOf("Add [1 cup] of flour and [1 tsp] salt.", "Whisk in the [1 cup] milk, then the rest of the flour."),
            steps(recipe, InterfaceLanguage.ENGLISH),
        )
        assertEquals(IngredientShare(Rational.ONE, QuantityUnit.CUP), recipe.steps[0].ingredientReferences[0].share)
    }

    @Test
    fun anIngredientFirstNamedWithoutAQuantityHasItsReferenceWhereTheSwitchSays() {
        val text = ru("- 100 г сахара\n- 2 яйца", "Добавить сахара и яйца.", "Снова сахар.")
        assertEquals(listOf("Добавить [100 г] сахара и [2 шт] яйца.", "Снова сахар."), steps(read(text)))
        assertEquals(listOf("Добавить сахара [100 г] и яйца [2 шт].", "Снова сахар."), steps(read(text, FirstMention.AFTER)))
        val none = read(text, FirstMention.NONE)
        assertEquals(listOf("Добавить сахара и яйца.", "Снова сахар."), none.steps.map { it.text })
        assertTrue(none.steps.all { it.ingredientReferences.isEmpty() })
    }

    @Test
    fun aNameGivenAQuantityInItsStepHasNoReferenceByTheName() {
        val recipe = read(ru("- 200 г сахара", "Взять сахар, добавить 100 г сахара.", "Добавить сахар."))
        assertEquals(listOf("Взять сахар, добавить [100 г] сахара.", "Добавить сахар."), steps(recipe))
    }

    @Test
    fun aFreeTextRowIsNamedByItsWordsInAnyForm() {
        val recipe = read(ru("- 50 г шмурдяка", "Положить шмурдяк.", "Добавить 20 г шмурдяка."))
        assertEquals(null, recipe.ingredients.single().dictionaryEntry)
        assertEquals(listOf("Положить [50 г] шмурдяк.", "Добавить [20 г] шмурдяка."), steps(recipe))
    }

    @Test
    fun aStepNamingNoRowStaysAsItWas() {
        val step = Step(0, "Перемешать и оставить на 10 минут.")
        val rows = read(ru("- 200 г сахара", "Перемешать.")).ingredients
        assertSame(step, StepReferences(rows, DictionaryMatch(shipped.ingredients), FirstMention.BEFORE).read(step))
    }

    @Test
    fun aReferenceByANameThatWouldTakeTheStepOverItsLimitIsLeftOut() {
        val rows = read(ru("- 200 г сахара", "Перемешать.")).ingredients
        val references = StepReferences(rows, DictionaryMatch(shipped.ingredients), FirstMention.BEFORE)
        val full = "сахар " + "м".repeat(RecipeBounds.MAX_STEP_TEXT - 6)
        assertEquals(emptyList(), references.read(Step(0, full)).ingredientReferences)
        val cut = references.read(Step(1, "100 г сахара " + "м".repeat(RecipeBounds.MAX_STEP_TEXT - 13)))
        assertEquals(listOf(0), cut.ingredientReferences.map { it.textOffset }, "a quantity taken out makes room")
    }

    // Scenario 1.
    @Test
    fun theOwnersBurgerSauceRefersToItsRows() {
        val recipe = read(File("src/test/resources/paste/layout/burger-sauce-yogurt.md").readText())
        assertEquals(
            listOf(
                "Крахмал. Размешать [3 г] крахмал с ложкой [100 г] йогурта до гладкости, чтобы не было комков.",
                "Смешать. Добавить остальной йогурт и все ингредиенты, перемешать.",
                "Попробовать. [5 г] Мёд и [1 щепотка] соль довести по вкусу. Консистенция — густая сметана, соус не должен растекаться с ложки.",
            ),
            steps(recipe),
        )
        assertEquals(listOf(listOf(7, 0), emptyList(), listOf(6, 8)), recipe.steps.map { s -> s.ingredientReferences.map { it.ingredientPosition } })
    }

    @Test
    fun aLongStepIsReadInLinearTime() {
        val rows = read(ru("- 200 г сахара\n- 2 cups flour\n- 50 г шмурдяка", "Перемешать.")).ingredients
        listOf(
            "100 г ".repeat(20_000), "сахар ".repeat(20_000), "1".repeat(100_000) + " г сахара", "1 " + " ".repeat(100_000) + "г",
            "100 г сахара ".repeat(10_000), "1 - ".repeat(20_000), "2 cups of ".repeat(10_000), "ч. ".repeat(20_000),
            "мелкого ".repeat(20_000) + "сахара", "сахар-".repeat(20_000), "½".repeat(50_000), "a ".repeat(50_000) + "flour",
        ).forEach { text ->
            val references = StepReferences(rows, DictionaryMatch(shipped.ingredients), FirstMention.BEFORE)
            val took = measureTime { references.read(Step(0, text)) }
            assertTrue(took.inWholeMilliseconds < 2_000, "${text.take(12)}…: $took")
        }
    }
}
