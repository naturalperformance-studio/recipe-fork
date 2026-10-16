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
        assertSame(step, StepReferences(rows, emptyList(), DictionaryMatch(shipped.ingredients), FirstMention.BEFORE).read(step))
    }

    // Fix round 1, must 1.
    @Test
    fun aQuantityRightAfterANameIsThatNamesNeverTheNext() {
        val rows = "- 60 г сметаны\n- 100 г муки"
        assertEquals(listOf("Плотный комок — сметана [60 г] и мука [50 г]."), steps(read(ru(rows, "Плотный комок — сметана 60 г и мука 50 г."))))
        assertEquals(listOf("Сверху сметана 2 ст. л. и [100 г] мука."), steps(read(ru(rows, "Сверху сметана 2 ст. л. и мука."))))
        // The dot of a unit word is its own where the sentence goes on; a quantity before a name and one after it.
        assertEquals(listOf("Добавить [100 г] муки."), steps(read(ru(rows, "Добавить 100 г. муки."))))
        assertEquals(listOf("Добавить [50 г] сметаны [100 г] муки."), steps(read(ru(rows, "Добавить 50 г сметаны 100 г муки."))))
        // The owner's syrniki with peach (S3), with apple (S3) and with ganache (S4).
        listOf("layout/syrniki-peach-freezer.md", "layout/syrniki-apple-freezer.md", "corpus/owner/Сырники с текучей сердцевиной (шоколадный ганаш).md")
            .forEach { name ->
                val owners = steps(read(File("src/test/resources/paste/$name").readText())).single { "Плотный комок" in it }
                assertTrue(owners.endsWith("Плотный комок, ничего не течёт — сметана [60 г] и мука [50 г]."), "$name: $owners")
            }
        // A bare number or a unit word after a name counts what follows it, unless the clause ends with it.
        fun eggShares(rows: String, step: String) =
            read(rows + step).steps.single().ingredientReferences.filter { it.ingredientPosition == 0 }.map { it.share }
        val ruEggs = "Т\n\nИнгредиенты\n- 4 яйца\n- 500 мл бульона\n\nПриготовление\n1. "
        assertEquals(listOf(null), eggShares(ruEggs, "Влить в яйца два половника бульона."))
        assertEquals(listOf(null), eggShares(ruEggs, "Добавить яйца одно за другим."))
        assertEquals(listOf(null), eggShares(ruEggs, "Варить яйца 3 или 4 минуты."))
        val enEggs = "Cake\n\nIngredients\n- 4 eggs\n\nMethod\n1. "
        assertEquals(listOf(null), eggShares(enEggs, "Add the eggs one at a time."))
        assertEquals(listOf(null), eggShares(enEggs, "Add the eggs two at a time."))
        assertTrue(steps(read(ru("- 1 щепотка соли", "Посолить, соль щепотка."))).single().contains("[1 щепотка]"))
    }

    // Fix round 1, must 2.
    @Test
    fun aNameOfSeveralRowsMeansTheOneItsQuantityOrItsGroupSays() {
        fun rows(step: String) = read(
            "Тест\n\n### Ингредиенты\n\n#### Тесто\n\n- 20 г сахара\n- 200 г муки\n\n#### Начинка\n\n- 15 г сахара\n- 100 г ягод\n\n" +
                "### Приготовление\n\n1. $step",
        ).steps.single().ingredientReferences.map { it.ingredientPosition }
        assertEquals(listOf(2), rows("Добавить 15 г сахара."))
        assertEquals(listOf(2), rows("Для начинки смешать сахар."))
        assertEquals(listOf(1, 0), rows("Смешать муку и сахар."))
        assertEquals(listOf(), rows("Добавить сахар."))
        val peach = read(File("src/test/resources/paste/layout/syrniki-peach-freezer.md").readText())
        val sugars = peach.ingredients.filter { it.dictionaryEntry == peach.ingredients[4].dictionaryEntry }.map { it.position }
        val filling = peach.steps.single { it.text.startsWith("Персиковая начинка.") }
        val sugar = filling.ingredientReferences.map { it.ingredientPosition }.filter { it in sugars }
        assertEquals(listOf(sugars.last()), sugar, "the filling's sugar, where the step names the filling: $sugars")
        // A group named with a parenthesis is named by its words outside it.
        val parenthesised = read(
            "Тест\n\n### Ингредиенты\n\n#### Тесто\n\n- 20 г сахара\n\n#### Начинка (ягодная, густая)\n\n- 15 г сахара\n\n" +
                "### Приготовление\n\n1. Для начинки смешать сахар.",
        ).steps.single().ingredientReferences.map { it.ingredientPosition }
        assertEquals(listOf(1), parenthesised)
        // A row outside every group is as good a match as one in the others' group: no reference (the owner's pelmeni).
        val water = read(
            "Тест\n\n### Ингредиенты\n\n- 2 л воды\n\n#### Соус\n\n- 40 г воды\n- 1 ч. л. соли\n\n" +
                "### Приготовление\n\n1. Поставить кастрюлю с водой и солью.",
        ).steps.single().ingredientReferences.map { it.ingredientPosition }
        assertTrue(1 !in water, "$water")
        // Where the step's other references are themselves outside every group, that is the group they say.
        val boiling = read(
            "Тест\n\n### Ингредиенты\n\n- 2 л воды\n- 500 г пельменей\n\n#### Соус\n\n- 40 г воды\n\n" +
                "### Приготовление\n\n1. Опустить 500 г пельменей в кипящую воду.",
        ).steps.single().ingredientReferences.map { it.ingredientPosition }
        assertTrue(0 in boiling && 2 !in boiling, "$boiling")
    }

    // Fix round 1, must 3.
    @Test
    fun aBareNumberOfMinutesIsNoCountOfAnIngredient() {
        val rows = "- 4 яйца\n- 100 г сахара"
        assertEquals(listOf("Через 2 минуты добавить [4 шт] яйца."), steps(read(ru(rows, "Через 2 минуты добавить яйца."))))
        assertEquals(listOf("Взбить 3 минуты [4 шт] яйца с [100 г] сахаром."), steps(read(ru(rows, "Взбить 3 минуты яйца с сахаром."))))
        assertEquals(listOf("Взять [2 шт] крупных яйца."), steps(read(ru(rows, "Взять 2 крупных яйца."))))
        assertEquals(listOf("Взять 2 очень крупных [4 шт] яйца."), steps(read(ru(rows, "Взять 2 очень крупных яйца."))), "one word at most after a bare number")
        val english = read("Eggs\n\nIngredients\n- 4 eggs\n\nMethod\n1. Beat for 2 minutes the eggs.").steps.single()
        assertEquals("Beat for 2 minutes the  eggs.", english.text)
        assertEquals(listOf(null), english.ingredientReferences.map { it.share })
        val englishBefore = read("Eggs\n\nIngredients\n- 4 eggs\n\nMethod\n1. Beat 2 minutes eggs with sugar.").steps.single()
        assertEquals(listOf(null), englishBefore.ingredientReferences.map { it.share }, "2 minutes is no count of eggs")
    }

    // Fix round 1, must 4 and fold-in 5: a row holding no quantity that scales has no reference by its name.
    @Test
    fun aRowWithNoQuantityThatScalesHasNoReferenceByItsName() {
        assertEquals(emptyList(), read(ru("- соль по вкусу", "Посолить, добавить соль.")).steps.single().ingredientReferences)
        val pieces = read(ru("- 2 шмурдяка", "Добавить шмурдяк."))
        assertEquals(listOf(null, null), with(pieces.ingredients.single()) { listOf(dictionaryEntry, authoredQuantity) })
        assertEquals(emptyList(), pieces.steps.single().ingredientReferences)
    }

    // Fold-in 6.
    @Test
    fun aQuantityTheReaderCannotBeSureOfBeforeANameGivesItNoReference() {
        val recipe = read(ru("- 200 г сахара\n- 18 г кофе", "Добавить (100 г) сахара.", "Засыпать ~9 г кофе."))
        assertEquals(listOf("Добавить (100 г) сахара.", "Засыпать ~9 г кофе."), recipe.steps.map { it.text })
        assertTrue(recipe.steps.all { it.ingredientReferences.isEmpty() })
    }

    @Test
    fun aReferenceByANameThatWouldTakeTheStepOverItsLimitIsLeftOut() {
        val rows = read(ru("- 200 г сахара", "Перемешать.")).ingredients
        val references = StepReferences(rows, emptyList(), DictionaryMatch(shipped.ingredients), FirstMention.BEFORE)
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
            val references = StepReferences(rows, emptyList(), DictionaryMatch(shipped.ingredients), FirstMention.BEFORE)
            val took = measureTime { references.read(Step(0, text)) }
            assertTrue(took.inWholeMilliseconds < 2_000, "${text.take(12)}…: $took")
        }
    }
}
