package app.recipefork.app.shopping

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.print.PrintBlock.Box
import app.recipefork.app.print.PrintBlock.Bullet
import app.recipefork.app.print.PrintBlock.Heading
import app.recipefork.app.print.PrintBlock.Title
import app.recipefork.app.print.PrintDocument
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.shopping.ShoppingAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T-236, scenario 5 of v0.33: the shopping list as a document — its recipes with their counts, the lines to buy as
 * empty boxes with their notes, own lines after them, then "In the basket", ticked and struck through; each line as
 * the screen writes it, in the interface language; no document for an empty list.
 */
@RunWith(AndroidJUnit4::class)
class ShoppingDocumentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dictionary = SHOPPING_DICTIONARY.associateBy { it.id }

    // Soup at twice its count, bread at its own, a cake with neither title nor count; their flour adds up approximately.
    private val soup = recipe("Soup", 2, row(ONION, r(1), PIECE))
    private val bread = recipe("Bread", 4, row(FLOUR, r(300), GRAM), row(SALT, r(5), GRAM))
    private val cake = recipe("", null, row(FLOUR, r(2), CUP))
    private val entries = listOf(ShoppingEntry(listed(soup, r(4)), soup), ShoppingEntry(listed(bread), bread), ShoppingEntry(listed(cake), cake))
    private val paper = OwnLine(POOL, "1", "baking paper")
    private val batteries = OwnLine(POOL, "2", "batteries")

    /** The list of [entries] and [own] with the lines named by [ticked] ticked at what they ask now, or at [at]. */
    private fun list(
        language: InterfaceLanguage,
        vararg ticked: String,
        own: List<OwnLine> = listOf(paper, batteries),
        at: Map<String, ShoppingAmount?> = emptyMap(),
        system: UnitSystem = UnitSystem.METRIC,
    ): ShoppingList {
        val lines = (listedLines(entries, dictionary, language, context.resources, system) + ownLines(ShoppingList(own = own), POOL))
            .associateBy { it.shown.text }
        return ShoppingList(
            entries.map { it.listed },
            ticked.associate { text -> lines.getValue(text).tick.let { TickedLine(POOL, it.key) to (if (text in at) at[text] else it.amount) } },
            own,
        )
    }

    private fun document(
        list: ShoppingList,
        language: InterfaceLanguage = InterfaceLanguage.ENGLISH,
        of: List<ShoppingEntry> = entries,
        system: UnitSystem = UnitSystem.METRIC,
    ) =
        shoppingDocument(of, dictionary, list, POOL, language, context.resources, system)

    // The recipes with their counts, the lines to buy as empty boxes with what they counted and their recipes muted
    // under them, own lines after them, then the basket in the list's order, ticked.
    @Test
    fun theListAsItsScreenShowsIt() {
        val document = document(list(InterfaceLanguage.ENGLISH, "salt — 5 g", "baking paper"))
        assertEquals(
            PrintDocument(
                "Shopping list",
                listOf(
                    Title("Shopping list"),
                    Heading("Recipes"),
                    Bullet("Soup — Servings: 4"),
                    Bullet("Bread — Servings: 4"),
                    Bullet("Untitled recipe"),
                    Heading("To buy"),
                    Box("onion — 2 pc", ticked = false, note = "Soup"),
                    Box("flour — ≈ 560 g", ticked = false, note = "2 cup counted as ≈ 260 g\nBread, Untitled recipe"),
                    Box("batteries", ticked = false, note = null),
                    Heading("In the basket"),
                    Box("salt — 5 g", ticked = true, note = "Bread"),
                    Box("baking paper", ticked = true, note = null),
                ),
            ),
            document,
        )
    }

    // v0.35 T-250: the printed list follows the chosen system as the screen does — the flour of a gram recipe and a cup
    // recipe in cups, what it counted in cups, the salt in teaspoons — and a line ticked in Metric is in the basket.
    @Test
    fun thePrintedListFollowsTheChosenSystem() {
        val document = document(list(InterfaceLanguage.ENGLISH, "salt — 5 g"), system = UnitSystem.US)!!
        assertEquals(
            listOf(
                Box("onion — 2 pc", ticked = false, note = "Soup"),
                Box("flour — ≈ 4⅓ cup", ticked = false, note = "300 g counted as ≈ 2⅓ cup\nBread, Untitled recipe"),
                Box("baking paper", ticked = false, note = null),
                Box("batteries", ticked = false, note = null),
                Box("salt — ≈ ¾ tsp", ticked = true, note = "5 g counted as ≈ ¾ tsp\nBread"),
            ),
            document.blocks.filterIsInstance<Box>(),
        )
    }

    // A line ticked when it asked for less is to buy again, as on the screen; with nothing ticked there is no basket.
    @Test
    fun aGrownLineIsToBuyAndNoBasketWithoutTicks() {
        val grown = document(list(InterfaceLanguage.ENGLISH, "onion — 2 pc", at = mapOf("onion — 2 pc" to ShoppingAmount(PIECE, r(1)))))!!
        val boxes = grown.blocks.filterIsInstance<Box>()
        assertEquals(listOf(false, false, false, false, false), boxes.map { it.ticked })
        assertFalse(Heading("In the basket") in grown.blocks)
        assertEquals(listOf("onion — 2 pc", "flour — ≈ 560 g", "salt — 5 g", "baking paper", "batteries"), boxes.map { it.text })
    }

    // Own lines alone: no recipes part; a recipe's count written as the serving control writes it, a fraction too.
    @Test
    fun ownLinesAloneAndFractionalCounts() {
        val alone = document(list(InterfaceLanguage.ENGLISH), of = emptyList())!!
        assertEquals(
            listOf(Title("Shopping list"), Heading("To buy"), Box("baking paper"), Box("batteries")),
            alone.blocks,
        )
        val half = ShoppingEntry(listed(soup, r(1, 2)), soup)
        assertEquals(Bullet("Soup — Servings: ½"), document(ShoppingList(listOf(half.listed)), of = listOf(half))!!.blocks[2])
    }

    // An empty list — or one holding only another book's own lines — yields no document.
    @Test
    fun anEmptyListYieldsNoDocument() {
        assertNull(document(ShoppingList(), of = emptyList()))
        assertNull(document(ShoppingList(own = listOf(OwnLine("other.recipefork", "3", "bin bags"))), of = emptyList()))
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        val ru = InterfaceLanguage.RUSSIAN
        val document = document(list(ru, "соль — 5 г", "baking paper"), ru)
        assertEquals(
            PrintDocument(
                "Список покупок",
                listOf(
                    Title("Список покупок"),
                    Heading("Рецепты"),
                    Bullet("Soup — Порций: 4"),
                    Bullet("Bread — Порций: 4"),
                    Bullet("Рецепт без названия"),
                    Heading("Купить"),
                    Box("лук — 2 шт", ticked = false, note = "Soup"),
                    Box("мука — ≈ 560 г", ticked = false, note = "2 стакан — посчитано как ≈ 260 г\nBread, Рецепт без названия"),
                    Box("batteries", ticked = false, note = null),
                    Heading("В корзине"),
                    Box("соль — 5 г", ticked = true, note = "Bread"),
                    Box("baking paper", ticked = true, note = null),
                ),
            ),
            document,
        )
    }
}
