package app.recipefork.app.shopping

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.shopping.ShoppingAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

internal const val BAKEWARE = 900L
internal const val TART_TIN = 901L
internal const val BAKING_PAPER = 902L
internal const val ELECTRICS = 910L
internal const val BATTERIES = 911L

/** Two groups of equipment: bakeware — a tart tin, baking paper — and electrics, whose batteries are also "AA cells". */
internal val SHOPPING_EQUIPMENT = listOf(
    EquipmentEntryEntity(BAKEWARE, "выпечка", "bakeware"),
    EquipmentEntryEntity(TART_TIN, "форма для тарта 24 см", "tart tin 24 cm", BAKEWARE),
    EquipmentEntryEntity(BAKING_PAPER, "пергамент", "baking paper", BAKEWARE, synonymsRu = listOf("бумага для выпечки")),
    EquipmentEntryEntity(ELECTRICS, "электрика", "electrics"),
    EquipmentEntryEntity(BATTERIES, "батарейки", "batteries", ELECTRICS, synonymsEn = listOf("AA cells")),
)

/**
 * T-232, scenario 4 of v0.32: the reader's own lines — kept on the device beside the recipes and ticks, under a key of
 * their own, per book; ticked as any line, their keys never a recipe line's; gone with "Clear ticked" once ticked and
 * with "Clear list"; and what the field offers for them from both dictionaries.
 */
@RunWith(AndroidJUnit4::class)
class OwnLinesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val elsewhere = "other.recipefork"
    private val paper = OwnLine(POOL, "1", "baking paper")
    private val batteries = OwnLine(POOL, "2", "batteries")
    private val bags = OwnLine(elsewhere, "3", "bin bags")

    private fun file() = context.getSharedPreferences(PreferencesShopping.FILE, Context.MODE_PRIVATE)

    @Test
    fun ownLinesStayAcrossRestartsWithTheirTicks() {
        val store = PreferencesShopping(context)
        val soup = ListedRecipe(POOL, RecipeId("soup"), r(2))
        store.update { it.with(soup).with(paper).with(batteries).with(bags).withTick(TickedLine(POOL, paper.key), null) }
        val expected = ShoppingList(listOf(soup), mapOf(TickedLine(POOL, "o:1") to null), listOf(paper, batteries, bags))
        assertEquals(expected, store.list.value)
        assertEquals(expected, PreferencesShopping(context).list.value)
        val written = file().getString(PreferencesShopping.KEY, null)!!
        assertTrue(written, written.startsWith("{\"recipes\":[") && "\"own\":[" in written)
    }

    // A list kept before own lines came reads with none; an own line that does not read back is passed over.
    @Test
    fun aListKeptBeforeOwnLinesReadsWithNone() {
        file().edit().putString(PreferencesShopping.KEY, """{"recipes":[{"book":"$POOL","recipe":"soup"}],"ticks":[]}""").commit()
        assertEquals(ShoppingList(listOf(ListedRecipe(POOL, RecipeId("soup"), null))), PreferencesShopping(context).list.value)
        file().edit().putString(
            PreferencesShopping.KEY,
            """{"own":[7,{"text":"bin bags"},{"book":"$POOL","text":"x"},{"book":"$POOL","id":"1","text":" "},""" +
                """{"book":"$POOL","id":"2","text":"batteries"},{"book":"$POOL","id":"2","text":"again"}]}""",
        ).commit()
        assertEquals(listOf(batteries), PreferencesShopping(context).list.value.own)
    }

    // A list of own lines alone is not empty; "Clear list" — a new list — holds none of them.
    @Test
    fun aListOfOwnLinesAloneIsNotEmpty() {
        assertFalse(ShoppingList(own = listOf(paper)).isEmpty)
        assertTrue(ShoppingList().isEmpty)
        // Added at the end, each with an id of its own.
        val added = ShoppingList().with(OwnLine.of(POOL, "tape")).with(OwnLine.of(POOL, "tape"))
        assertEquals(listOf("tape", "tape"), added.own.map { it.text })
        assertEquals(2, added.own.map { it.key }.toSet().size)
    }

    // "Clear ticked" takes the book's ticked own lines away with their ticks; its unticked ones and another book's stay.
    @Test
    fun clearingTheTicksRemovesTheTickedOwnLines() {
        val onion = TickedLine(POOL, "e:$ONION")
        val list = ShoppingList(
            ticks = mapOf(onion to ShoppingAmount(PIECE, r(1)), TickedLine(POOL, paper.key) to null, TickedLine(elsewhere, bags.key) to null),
            own = listOf(paper, batteries, bags),
        )
        assertEquals(
            ShoppingList(ticks = mapOf(TickedLine(elsewhere, bags.key) to null), own = listOf(batteries, bags)),
            list.withoutTicked(POOL),
        )
    }

    // An own line removed takes its tick with it.
    @Test
    fun aRemovedOwnLineTakesItsTick() {
        val list = ShoppingList(ticks = mapOf(TickedLine(POOL, paper.key) to null), own = listOf(paper, batteries))
        assertEquals(ShoppingList(own = listOf(batteries)), list.withoutOwn(POOL, paper.key))
        // Another book's line of the same key stays.
        assertEquals(list, list.withoutOwn(elsewhere, paper.key))
    }

    // An own line's tick stays when the ticks are settled against the recipes, as long as the line does; its key is
    // never a recipe line's, even for a line that reads the same.
    @Test
    fun ownLinesAreNeverRecipeLines() {
        val soup = recipe("Soup", 2, row(FLOUR, r(300), GRAM), free("baking paper", null, null))
        val dictionary = SHOPPING_DICTIONARY.associateBy { it.id }
        val flour = OwnLine(POOL, "4", "flour")
        val list = ShoppingList(listOf(listed(soup)), own = listOf(paper, flour))
            .withTick(TickedLine(POOL, paper.key), null)
            .withTick(TickedLine(POOL, "o:gone"), null)
        val settled = list.settled(POOL, mapOf(soup.id to soup), dictionary)
        assertEquals(setOf(TickedLine(POOL, paper.key)), settled.ticks.keys)
        val recipeKeys = listedLines(listOf(ShoppingEntry(listed(soup), soup)), dictionary, InterfaceLanguage.ENGLISH, context.resources, UnitSystem.METRIC).map { it.tick.key }
        val own = ownLines(list, POOL)
        assertEquals(listOf("baking paper", "flour"), own.map { it.shown.text })
        assertTrue(own.all { it.own && it.shown.from.isEmpty() && it.tick.amount == null })
        assertTrue(recipeKeys.none { it.startsWith(OwnLine.OWN_KEY) })
        assertTrue(own.map { it.tick.key }.intersect(recipeKeys.toSet()).isEmpty())
        // Own lines are per book.
        assertEquals(listOf("bin bags"), ownLines(ShoppingList(own = listOf(paper, bags)), elsewhere).map { it.shown.text })
    }

    private fun offer(typed: String, language: InterfaceLanguage = InterfaceLanguage.ENGLISH, ingredients: List<IngredientEntryEntity> = SHOPPING_DICTIONARY) =
        OwnLineOffer.of(typed, ingredients, SHOPPING_EQUIPMENT, language)

    // The field offers ingredients and items of equipment — never a group — labelled in the interface language, found
    // by label or synonym in either language; the typed text is offered as written unless it is exactly one of them.
    @Test
    fun theFieldOffersIngredientsAndEquipment() {
        assertEquals(listOf("tart tin 24 cm"), offer("tart").entries.map { it.label })
        assertTrue(offer("tart").typedOffered)
        // "bak" finds the bakeware's items — by their group's name too — but not the group itself.
        assertEquals(listOf("baking paper", "tart tin 24 cm"), offer("bak").entries.map { it.label })
        assertEquals(listOf("flour"), offer("flo").entries.map { it.label })
        assertEquals(listOf("flour"), offer("flour").entries.map { it.label })
        assertFalse(offer("flour").typedOffered)
        assertFalse(offer("batteries").typedOffered)
        // Either language finds; the label is the interface language's.
        assertEquals(listOf("flour"), offer("мука").entries.map { it.label })
        assertEquals(listOf("мука"), offer("flour", InterfaceLanguage.RUSSIAN).entries.map { it.label })
        // A synonym finds its entry and is shown beside it.
        val cells = offer("cells").entries.single()
        assertEquals("batteries" to "AA cells", cells.label to cells.synonym)
        val paperRu = offer("бумага", InterfaceLanguage.RUSSIAN).entries.single()
        assertEquals("пергамент" to "бумага для выпечки", paperRu.label to paperRu.synonym)
        // Nothing for blank text.
        assertTrue(offer("   ").entries.isEmpty())
        assertFalse(offer("   ").typedOffered)
        // Ingredients first, then equipment; the exact match of either first.
        val pack = IngredientEntryEntity(1, "набор батареек", "batteries pack", null)
        assertEquals(listOf("batteries pack", "batteries"), offer("batt", ingredients = listOf(pack)).entries.map { it.label })
        assertEquals(listOf("batteries", "batteries pack"), offer("batteries", ingredients = listOf(pack)).entries.map { it.label })
    }
}
