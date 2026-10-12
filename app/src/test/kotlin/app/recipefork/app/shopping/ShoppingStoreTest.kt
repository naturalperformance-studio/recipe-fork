package app.recipefork.app.shopping

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.shopping.ShoppingAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-229: the shopping list is kept on the device, in a preferences file of its own as My kitchen is — its recipes as
 * (book, recipe, serving count) — in a form that its own lines and ticks join later without a break; nothing of it
 * goes into a book or a recipe. The composition root exposes it as a store the screen observes. A recipe the book no
 * longer holds leaves it by itself.
 */
@RunWith(AndroidJUnit4::class)
class ShoppingStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)

    @After
    fun close() = book.close()

    private fun file() = context.getSharedPreferences(PreferencesShopping.FILE, Context.MODE_PRIVATE)

    @Test
    fun theListStaysAcrossRestarts() {
        val store = PreferencesShopping(context)
        val soup = ListedRecipe(POOL, RecipeId("soup"), r(6))
        val cake = ListedRecipe(POOL, RecipeId("cake"), r(1, 4))
        val bread = ListedRecipe(POOL, RecipeId("bread"), null)
        store.update { it.with(soup).with(cake).with(bread) }
        store.update { it.withServings(POOL, RecipeId("soup"), r(4)) }
        val expected = ShoppingList(listOf(soup.copy(servings = r(4)), cake, bread))
        assertEquals(expected, store.list.value)
        // The application started anew reads it back.
        assertEquals(expected, PreferencesShopping(context).list.value)
    }

    // The stored form is one object, each part under a key of its own: a list kept with its own lines and ticks — as
    // T-231 and T-232 will keep it — reads its recipes as they are, and a key this version does not know is passed over.
    @Test
    fun theStoredFormHasRoomForOwnLinesAndTicks() {
        file().edit().putString(
            PreferencesShopping.KEY,
            """{"recipes":[{"book":"$POOL","recipe":"soup","servings":"6"},{"book":"$POOL","recipe":"cake"}],""" +
                """"own":[{"text":"bin bags"}],"ticks":["soup:onion"],"later":42}""",
        ).commit()
        val read = PreferencesShopping(context).list.value
        assertEquals(listOf(ListedRecipe(POOL, RecipeId("soup"), r(6)), ListedRecipe(POOL, RecipeId("cake"), null)), read.recipes)
        // What this version writes is an object too, its recipes under their own key, ready for parts beside them.
        PreferencesShopping(context).update { it.without(POOL, RecipeId("cake")) }
        val written = file().getString(PreferencesShopping.KEY, null)!!
        assertTrue(written, written.startsWith("{\"recipes\":["))
        assertEquals(ShoppingList(listOf(ListedRecipe(POOL, RecipeId("soup"), r(6)))), PreferencesShopping(context).list.value)
    }

    // T-231: the ticks are kept beside the recipes, under their own key, each with the amount its line asks for, and
    // stay across restarts; a line with no amount keeps its tick as well.
    @Test
    fun theTicksStayAcrossRestarts() {
        val store = PreferencesShopping(context)
        val soup = ListedRecipe(POOL, RecipeId("soup"), r(6))
        val onion = TickedLine(POOL, "e:$ONION")
        val salt = TickedLine(POOL, "e:$SALT:taste")
        val elsewhere = TickedLine("other.recipefork", "f:cake:bay leaf")
        store.update {
            it.with(soup)
                .withTick(onion, ShoppingAmount(QuantityUnit.PIECE, r(3)))
                .withTick(salt, null)
                .withTick(elsewhere, ShoppingAmount(QuantityUnit.GRAM, r(5, 2)))
        }
        val expected = ShoppingList(
            listOf(soup),
            mapOf(onion to ShoppingAmount(QuantityUnit.PIECE, r(3)), salt to null, elsewhere to ShoppingAmount(QuantityUnit.GRAM, r(5, 2))),
        )
        assertEquals(expected, store.list.value)
        assertEquals(expected, PreferencesShopping(context).list.value)
        val written = file().getString(PreferencesShopping.KEY, null)!!
        assertTrue(written, written.startsWith("{\"recipes\":[") && "\"ticks\":[" in written)
        // A second tap unticks it, kept as well.
        PreferencesShopping(context).update { it.withTick(onion, null, bought = false) }
        assertEquals(expected.ticks - onion, PreferencesShopping(context).list.value.ticks)
    }

    // A list kept before the ticks came reads as it was, nothing ticked; a tick that does not read back is passed over,
    // and an amount that does not read back reads as none.
    @Test
    fun aListKeptBeforeTheTicksReadsWithNothingTicked() {
        file().edit().putString(PreferencesShopping.KEY, """{"recipes":[{"book":"$POOL","recipe":"soup","servings":"6"}]}""").commit()
        assertEquals(ShoppingList(listOf(ListedRecipe(POOL, RecipeId("soup"), r(6)))), PreferencesShopping(context).list.value)
        file().edit().putString(
            PreferencesShopping.KEY,
            """{"recipes":[],"ticks":["soup:onion",7,{"book":"$POOL"},{"line":"e:1"},""" +
                """{"book":"$POOL","line":"e:1","unit":"SPOONFUL","amount":"2"},{"book":"$POOL","line":"e:2","unit":"GRAM","amount":"x"},""" +
                """{"book":"$POOL","line":"e:3","unit":"CUP","amount":"3/4"}]}""",
        ).commit()
        assertEquals(
            mapOf(
                TickedLine(POOL, "e:1") to null,
                TickedLine(POOL, "e:2") to null,
                TickedLine(POOL, "e:3") to ShoppingAmount(QuantityUnit.CUP, r(3, 4)),
            ),
            PreferencesShopping(context).list.value.ticks,
        )
    }

    // "Clear ticked" unticks every line of the book; the recipes stay, and so do the ticks of another book's lines.
    @Test
    fun clearingTheTicksKeepsTheRecipes() {
        val soup = ListedRecipe(POOL, RecipeId("soup"), r(2))
        val elsewhere = TickedLine("other.recipefork", "e:1")
        val list = ShoppingList(listOf(soup), mapOf(TickedLine(POOL, "e:1") to null, TickedLine(POOL, "e:2") to null, elsewhere to null))
        assertEquals(ShoppingList(listOf(soup), mapOf(elsewhere to null)), list.withoutTicked(POOL))
    }

    @Test
    fun whateverTheFileHoldsThatIsNoListReadsAsAnEmptyOne() {
        listOf("not json", "[1,2]", """{"recipes":"soup"}""", """{"recipes":[{"recipe":"soup"},{"book":"$POOL"},7]}""").forEach { text ->
            file().edit().putString(PreferencesShopping.KEY, text).commit()
            assertEquals(text, ShoppingList(), PreferencesShopping(context).list.value)
        }
        file().edit().putInt(PreferencesShopping.KEY, 3).commit()
        assertEquals(ShoppingList(), PreferencesShopping(context).list.value)
        // A count no reader can request reads as none: the recipe at its authored count.
        file().edit().putString(PreferencesShopping.KEY, """{"recipes":[{"book":"$POOL","recipe":"soup","servings":"3/2"}]}""").commit()
        assertEquals(listOf(ListedRecipe(POOL, RecipeId("soup"), null)), PreferencesShopping(context).list.value.recipes)
    }

    @Test
    fun theCompositionRootExposesTheListAsItChanges() {
        val shopping = (context as RecipeForkApplication).container.shopping
        assertTrue(shopping is PreferencesShopping)
        val soup = ListedRecipe(POOL, RecipeId("soup"), r(2))
        shopping.update { it.with(soup) }
        assertEquals(listOf(soup), shopping.list.value.recipes)
        assertEquals(listOf(soup), PreferencesShopping(context).list.value.recipes)
    }

    @Test
    fun theHeldListChangesAsTheKeptOneDoes() {
        val store = HeldShopping()
        val soup = ListedRecipe(POOL, RecipeId("soup"), r(2))
        store.update { it.with(soup) }
        // Listed again, a recipe keeps its place and takes the new count; it is never listed twice.
        store.update { it.with(soup.copy(servings = r(5))) }
        assertEquals(listOf(soup.copy(servings = r(5))), store.list.value.recipes)
        store.update { it.without(POOL, soup.recipe) }
        assertTrue(store.list.value.isEmpty)
    }

    // Keeping a recipe on the list at another count writes nothing into the book: the recipe reads as it was saved.
    @Test
    fun nothingOfTheListGoesIntoTheBookOrTheRecipe() = runBlocking {
        val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
        val soup = recipe("Soup", 2, row(ONION, r(1), QuantityUnit.PIECE))
        recipes.save(soup)
        val saved = recipes.get(soup.id)
        val store = PreferencesShopping(context)
        store.update { it.with(listed(soup, r(6))) }
        assertEquals(saved, recipes.get(soup.id))
        assertEquals(2, recipes.get(soup.id)!!.servings)
        assertEquals(1, recipes.count())
        assertEquals(setOf(PreferencesShopping.KEY), file().all.keys)
    }

    // A recipe deleted from the book leaves the list by itself; the others stay, at their counts.
    @Test
    fun aRecipeTheBookNoLongerHoldsLeavesTheList() = runBlocking {
        val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
        val soup = recipe("Soup", 2, row(ONION, r(1), QuantityUnit.PIECE))
        val cake = recipe("Cake", 8, row(FLOUR, r(250), QuantityUnit.GRAM))
        recipes.save(soup)
        recipes.save(cake)
        val store = HeldShopping(ShoppingList(listOf(listed(soup, r(4)), listed(cake))))
        val entries = shoppingEntries(store, recipes)
        withTimeout(PATIENCE) {
            assertEquals(listOf("Soup", "Cake"), entries.first().map { it.recipe.title })
        }
        assertTrue(recipes.delete(cake.id))
        withTimeout(PATIENCE) {
            assertEquals(listOf("Soup"), entries.first { it.size == 1 }.map { it.recipe.title })
            store.list.first { it.recipes == listOf(listed(soup, r(4))) }
        }
        assertFalse(store.list.value.recipes.any { it.recipe == cake.id })
    }

    private companion object {
        const val PATIENCE = 30_000L
    }
}
