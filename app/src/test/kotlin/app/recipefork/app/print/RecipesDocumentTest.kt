package app.recipefork.app.print

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.card.CardScale
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.Locale

/**
 * v0.37, T-262 — a document of several recipes (scenarios 4 and 7): a first page with its title and the numbered list
 * of the recipes' titles, then each recipe as [RecipeDocument] lays it out, each from a new page, with no blank page;
 * every footer names the document's title.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipesDocumentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    private fun resources(language: String): Resources =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale(language))) }).resources

    private val step = "Fry the onion over a medium heat until golden, stirring, then add the carrot and stew for five more minutes."

    /** A recipe of [steps] steps for [servings]: flour and sugar. */
    private fun recipe(title: String, steps: Int = 2, servings: Int = 4) = Recipe.create(now, null).copy(
        title = title,
        servings = servings,
        ingredients = listOf(grams(0, "Flour", 200), grams(1, "Sugar", 50)),
        steps = List(steps) { Step(it, "$step (${it + 1})") },
    )

    private fun grams(position: Int, name: String, amount: Long) =
        Ingredient(position, null, freeTextName = name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(amount))

    private fun document(recipe: Recipe, language: String = "en") =
        RecipeDocument.of(recipe, emptyMap(), emptyMap(), CardScale(Rational.of(recipe.servings!!.toLong())), resources(language))

    @Test
    fun theFirstPageListsTheRecipesThenEachFollowsAfterABreak() {
        val soup = document(recipe("Soup"))
        val bread = document(recipe("Bread"))
        val untitled = document(recipe(""))

        val whole = RecipesDocument.of("New Year's menu", listOf(soup, bread, untitled))

        assertEquals("New Year's menu", whole.title)
        assertEquals(
            listOf(
                PrintBlock.Title("New Year's menu"),
                PrintBlock.Numbered(1, "Soup"),
                PrintBlock.Numbered(2, "Bread"),
                PrintBlock.Numbered(3, "Untitled recipe"),
            ),
            whole.blocks.take(4),
        )
        // Each recipe's blocks as its own document holds them, a break before each.
        assertEquals(
            listOf(PrintBlock.PageBreak) + soup.blocks + PrintBlock.PageBreak + bread.blocks + PrintBlock.PageBreak + untitled.blocks,
            whole.blocks.drop(4),
        )
    }

    @Test
    fun eachRecipeBeginsAPageAndNoPageIsBlank() {
        // The second is long enough to run over three pages or more.
        val recipes = listOf(document(recipe("Soup")), document(recipe("Stew", steps = 80)), document(recipe("Bread")))
        val whole = RecipesDocument.of("Winter", recipes)

        val pages = PrintPages.layOut(whole)

        // The contents page holds the title and the list, and nothing of a recipe.
        assertEquals((0..3).toList(), pages.placements(0).map { it.block })
        val titles = whole.blocks.withIndex().filter { (index, block) -> index > 0 && block is PrintBlock.Title }.map { it.index }
        assertEquals(3, titles.size)
        val starts = titles.map { block -> (0 until pages.count).single { page -> pages.placements(page).any { it.block == block } } }
        for ((block, page) in titles.zip(starts)) {
            // Its title is the first thing on its page, at the top.
            assertEquals(Placement(block, 0, 1, 0f), pages.placements(page).first())
        }
        // Each recipe takes as many pages as it does alone.
        val alone = recipes.map { PrintPages.layOut(it).count }
        assertTrue("the stew runs over ${alone[1]} pages", alone[1] >= 3)
        assertEquals(listOf(1, 1 + alone[0], 1 + alone[0] + alone[1]), starts)
        assertEquals(1 + alone.sum(), pages.count)
        for (page in 0 until pages.count) {
            assertTrue("page ${page + 1} is blank", pages.placements(page).isNotEmpty())
            assertEquals(Footer("Winter", "${page + 1} / ${pages.count}"), pages.footer(page))
        }
    }

    // A list longer than a page goes on over the next, and the first recipe still begins its own.
    @Test
    fun aLongListRunsOverPagesWithoutABlankOne() {
        val recipes = List(80) { document(recipe("Recipe ${it + 1}", steps = 1)) }
        val whole = RecipesDocument.of("Everything", recipes)

        val pages = PrintPages.layOut(whole)

        val firstTitle = 1 + recipes.size + 1
        val start = (0 until pages.count).single { page -> pages.placements(page).any { it.block == firstTitle } }
        assertTrue("the list ends on page ${start}", start >= 2)
        // The list's pages hold only the list.
        for (page in 0 until start) assertTrue(pages.placements(page).all { it.block < firstTitle })
        assertEquals(Placement(firstTitle, 0, 1, 0f), pages.placements(start).first())
        assertEquals(start + recipes.size, pages.count)
        for (page in 0 until pages.count) assertTrue("page ${page + 1} is blank", pages.placements(page).isNotEmpty())
    }

    @Test
    fun inRussianEachRecipeReadsAsItsOwnDocument() {
        val soup = document(recipe("Суп"), "ru")
        val whole = RecipesDocument.of("Зимнее меню", listOf(soup))

        assertEquals(listOf(PrintBlock.Title("Зимнее меню"), PrintBlock.Numbered(1, "Суп"), PrintBlock.PageBreak), whole.blocks.take(3))
        assertTrue(PrintBlock.Heading("Ингредиенты · 4 порции") in whole.blocks)
        val pages = PrintPages.layOut(whole)
        assertEquals(2, pages.count)
        assertEquals("Зимнее меню", pages.footer(1).title)
    }
}
