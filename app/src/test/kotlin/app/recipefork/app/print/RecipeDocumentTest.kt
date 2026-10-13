package app.recipefork.app.print

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.card.CardFixing
import app.recipefork.app.card.CardScale
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/** A recipe as a document (FLOW-075 (1)): what the card's View tab shows, in its order, at the scale the card holds. */
@RunWith(AndroidJUnit4::class)
class RecipeDocumentTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    private fun resources(language: String): Resources =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale(language))) }).resources

    private fun grams(position: Int, name: String, amount: Long, note: String? = null) =
        Ingredient(position, null, freeTextName = name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(amount), note = note)

    /** Two servings of a loaf: its batter and its glaze in groups, a step with references, a timer and a note. */
    private val cake = Recipe.create(now, null).copy(
        title = "Lemon cake",
        description = "A bright loaf.",
        statedAuthor = "Anna",
        servings = 2,
        equipment = listOf(EquipmentItem(null, freeTextName = "Loaf tin")),
        ingredients = listOf(
            grams(0, "Flour", 100, note = "sifted"),
            grams(1, "Sugar", 50),
            grams(2, "Icing sugar", 30),
            Ingredient(3, null, freeTextName = "Salt", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        ),
        ingredientGroups = listOf(IngredientGroup("Batter", 0), IngredientGroup("Glaze", 2)),
        steps = listOf(
            Step(0, "Whisk  with .", listOf(IngredientReference(1, 6), IngredientReference(0, 12)), listOf(Timer(10.minutes)), note = "Do not overmix."),
            // Nothing to read: left out, as on the card.
            Step(1, ""),
            Step(2, "Bake until golden.", timers = listOf(Timer(40.minutes, "Oven"))),
        ),
    )

    private fun texts(document: PrintDocument) = document.blocks.map { block ->
        when (block) {
            is PrintBlock.Title -> "title: ${block.text}"
            is PrintBlock.Heading -> "heading: ${block.text}"
            is PrintBlock.Paragraph -> block.text
            is PrintBlock.Muted -> "muted: ${block.text}"
            is PrintBlock.Bullet -> "• ${block.text}"
            is PrintBlock.Numbered -> "${block.number}. ${block.text}"
            is PrintBlock.Box -> "box: ${block.text}"
            is PrintBlock.Picture -> "picture: ${block.file.name}"
            PrintBlock.PageBreak -> "page break"
        }.replace(' ', ' ').replace(' ', ' ')
    }

    private fun document(recipe: Recipe, scale: CardScale, language: String = "en", parentTitle: String? = null) =
        RecipeDocument.of(recipe, emptyMap(), emptyMap(), scale, resources(language), parentTitle)

    @Test
    fun aRecipeScaledToFourServingsReadsAsItsViewTabInItsOrder() {
        val document = document(cake, CardScale(Rational.of(4)))

        assertEquals("Lemon cake", document.title)
        assertEquals(
            listOf(
                "title: Lemon cake",
                "muted: 4 ingredients · 2 steps · timers ≈ 50 min",
                "Author: Anna (stated in the file, not verified)",
                "A bright loaf.",
                "heading: Equipment",
                "• Loaf tin",
                "heading: Ingredients · 4 servings",
                "muted: Recalculated · ↑ rounded up · ↓ rounded down",
                "heading: Batter",
                "• 200 g Flour — sifted",
                "• 100 g Sugar",
                "heading: Glaze",
                "• 60 g Icing sugar",
                "• Salt — to taste",
                "heading: Steps",
                "1. Whisk 100 g with 200 g.",
                "muted: Do not overmix.",
                "muted: ⏱ Whisk 100 g with 200 g. · 10 min",
                "2. Bake until golden.",
                "muted: ⏱ Oven · 40 min",
            ),
            texts(document),
        )
    }

    @Test
    fun atTheAuthoredCountTheLinesAreTheAuthorsAndAFixedAmountShowsTheApproximateCount() {
        val authored = texts(document(cake, CardScale(Rational.of(2))))
        assertTrue(authored.toString(), "heading: Ingredients · 2 servings" in authored)
        assertTrue(authored.toString(), "muted: As the author wrote it" in authored)
        assertTrue(authored.toString(), "• 100 g Flour — sifted" in authored)
        assertTrue(authored.toString(), "1. Whisk 50 g with 100 g." in authored)

        // 150 g of flour where the author wrote 100 g for two: about three servings, the step's references with it.
        val fixed = texts(document(cake, CardScale(Rational.of(2), listOf(CardFixing(0, Rational.of(150), QuantityUnit.GRAM)))))
        assertTrue(fixed.toString(), "heading: Ingredients · ≈3 servings" in fixed)
        assertTrue(fixed.toString(), "• 150 g Flour — sifted" in fixed)
        assertTrue(fixed.toString(), "1. Whisk 75 g with 150 g." in fixed)

        val one = texts(document(cake.copy(servings = 1), CardScale(Rational.ONE)))
        assertTrue(one.toString(), "heading: Ingredients · 1 serving" in one)
    }

    @Test
    fun aRoundedQuantityCarriesItsArrowInItsLineAndInTheStepAsOnTheCard() {
        // Three whole eggs for four, at six: 4.5 shown as 5, rounded up.
        val eggs = Recipe.create(now, null).copy(
            title = "Omelette",
            servings = 4,
            ingredients = listOf(
                Ingredient(0, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
            ),
            steps = listOf(Step(0, "Beat .", listOf(IngredientReference(0, 5)))),
        )
        val texts = texts(document(eggs, CardScale(Rational.of(6))))
        assertTrue(texts.toString(), "• 5 pc ↑ Eggs" in texts)
        assertTrue(texts.toString(), "1. Beat 5 pc ↑." in texts)
    }

    @Test
    fun theStepsAreNumberedAsOnTheCardAPhotoPrintedUnderItsStepAndAClipSaidToBeInTheApp() {
        val photo = folder.newFile("photo-2")
        fun media(id: String, kind: MediaKind, step: Int) = Media(id, null, kind, MediaAttachment.ToStep(step), MediaVisibility.PRIVATE)
        val shown = Recipe.create(now, null).copy(
            title = "Bread",
            steps = listOf(Step(0, "Mix."), Step(1, ""), Step(2, ""), Step(3, "Bake, as in step 2.")),
            media = listOf(media("photo-2", MediaKind.PHOTO, 1), media("clip-3", MediaKind.VIDEO, 2)),
        )
        fun printed(recipe: Recipe, language: String = "en", at: String = "") =
            texts(RecipeDocument.of(recipe, emptyMap(), emptyMap(), CardScale(null), resources(language)) { folder.root.resolve(at + it) })

        // The summary counts the four steps the page lists.
        assertEquals(
            listOf(
                "title: Bread",
                "muted: 4 steps",
                "heading: Steps",
                "1. Mix.",
                "2. ",
                "picture: ${photo.name}",
                "3. ",
                "muted: Video in the app",
                "4. Bake, as in step 2.",
            ),
            printed(shown),
        )
        assertTrue(printed(shown, "ru").toString(), "muted: Видео в приложении" in printed(shown, "ru"))

        // A photo the device does not hold is left out; its step keeps its number.
        assertEquals(
            listOf("1. Mix.", "2. ", "3. ", "muted: Video in the app", "4. Bake, as in step 2."),
            printed(shown, at = "elsewhere-").drop(3),
        )

        // A clip under a step with text of its own adds nothing to it.
        val withText = shown.copy(steps = shown.steps.map { if (it.position == 2) it.copy(text = "Shape.") else it })
        assertEquals(listOf("3. Shape.", "4. Bake, as in step 2."), printed(withText).drop(6))
    }

    @Test
    fun aFractionBelowOneServingIsSingular() {
        val one = cake.copy(servings = 1)
        assertTrue(texts(document(one, CardScale(Rational.of(1, 2)))).contains("heading: Ingredients · ½ serving"))
        assertTrue(texts(document(one, CardScale(Rational.of(1, 4)))).contains("heading: Ingredients · ¼ serving"))
        assertTrue(texts(document(one, CardScale(Rational.of(1, 2)), language = "ru")).contains("heading: Ингредиенты · ½ порции"))
    }

    @Test
    fun aRecipeWithNoCountShowsNoneAndAnUntitledOneSaysSo() {
        val texts = texts(document(cake.copy(title = " ", servings = null), CardScale(null)))
        assertEquals("title: Untitled recipe", texts.first())
        assertTrue(texts.toString(), "heading: Ingredients" in texts)
        assertTrue(texts.toString(), "• 100 g Flour — sifted" in texts)
    }

    @Test
    fun theProvenanceLineAndTheCoverStandWhereTheCardShowsThem() {
        val cover = folder.newFile("cover-1")
        val forked = cake.copy(
            parent = RecipeId("parent"),
            statedParent = StatedParent("Lemon loaf", "Olga"),
            media = listOf(Media("cover-1", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)),
        )
        val held = RecipeDocument.of(forked, emptyMap(), emptyMap(), CardScale(Rational.of(2)), resources("en"), parentTitle = "Loaf") {
            folder.root.resolve(it)
        }
        assertEquals(PrintBlock.Picture(cover), held.blocks[0])
        assertEquals(PrintBlock.Title("Lemon cake"), held.blocks[1])
        assertTrue(texts(held).toString(), "Forked from “Lemon loaf” (author: Olga, stated in the file, not verified)" in texts(held))

        // A parent the book does not hold is said to be so; a cover the device does not hold is left out.
        val missing = RecipeDocument.of(forked.copy(statedParent = null), emptyMap(), emptyMap(), CardScale(Rational.of(2)), resources("en")) {
            folder.root.resolve("elsewhere")
        }
        assertEquals(PrintBlock.Title("Lemon cake"), missing.blocks[0])
        assertTrue(texts(missing).toString(), "Forked from a recipe that is not in this recipe book" in texts(missing))
    }

    @Test
    fun aRussianInterfaceYieldsRussianWording() {
        val texts = texts(document(cake, CardScale(Rational.of(4)), language = "ru"))
        assertEquals(
            listOf(
                "title: Lemon cake",
                "muted: 4 ингредиента · 2 шага · таймеры ≈ 50 мин",
                "Автор: Anna (указан в файле, не проверен)",
                "A bright loaf.",
                "heading: Посуда и техника",
                "• Loaf tin",
                "heading: Ингредиенты · 4 порции",
                "muted: Пересчитано · ↑ округлено вверх · ↓ вниз",
                "heading: Batter",
                "• 200 г Flour — sifted",
                "• 100 г Sugar",
                "heading: Glaze",
                "• 60 г Icing sugar",
                "• Salt — по вкусу",
                "heading: Шаги",
                "1. Whisk 100 г with 200 г.",
                "muted: Do not overmix.",
                "muted: ⏱ Whisk 100 г with 200 г. · 10 мин",
                "2. Bake until golden.",
                "muted: ⏱ Oven · 40 мин",
            ),
            texts,
        )
        assertTrue(texts(document(cake, CardScale(Rational.of(5)), language = "ru")).contains("heading: Ингредиенты · 5 порций"))
    }
}
