package app.recipefork.app.shopping

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.OUNCE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.shopping.ShoppingAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T-229, scenarios 1–3 of v0.32: the lines the list shows, added up by `ShoppingTotals` from each recipe at its count,
 * named in the interface language, their amounts written by the line model — "≈", "+ to taste", "to taste" — with the
 * muted note of what an approximate line counted and the recipes each line comes from.
 */
@RunWith(AndroidJUnit4::class)
class ShoppingLinesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dictionary = SHOPPING_DICTIONARY.associateBy { it.id }

    private fun lines(
        vararg recipes: Pair<Recipe, Rational?>,
        language: InterfaceLanguage = InterfaceLanguage.ENGLISH,
        system: UnitSystem = UnitSystem.METRIC,
    ) =
        shownLines(recipes.map { (recipe, count) -> ShoppingEntry(listed(recipe, count), recipe) }, dictionary, language, context.resources, system)

    private fun at(recipe: Recipe, count: Rational? = recipe.servings?.let { r(it.toLong()) }) = recipe to count

    // Scenario 1: the list holds each recipe's quantities at its serving count.
    @Test
    fun eachRecipeCountsAtItsServingCount() {
        val soup = recipe("Soup", 2, row(ONION, r(1), PIECE), row(FLOUR, r(100), GRAM))
        assertEquals(listOf("onion — 1 pc", "flour — 100 g"), lines(at(soup)).map { it.text })
        assertEquals(listOf("onion — 2 pc", "flour — 200 g"), lines(at(soup, r(4))).map { it.text })
        assertEquals(listOf("onion — 1 pc", "flour — 25 g"), lines(at(soup, r(1, 2))).map { it.text })
    }

    // Scenario 2: onions of two recipes are one line with the sum; 300 g and 1.2 kg of flour read "1.5 kg"; under each
    // line the recipes it comes from.
    @Test
    fun theSameIngredientIsOneLineWithTheSum() {
        val soup = recipe("Soup", 4, row(ONION, r(2), PIECE), row(FLOUR, r(300), GRAM))
        val pie = recipe("Onion pie", 4, row(ONION, r(1), PIECE), row(FLOUR, r(12, 10), KILOGRAM))
        val shown = lines(at(soup), at(pie))
        assertEquals(
            listOf(
                ShownLine("onion — 3 pc", null, "Soup, Onion pie"),
                ShownLine("flour — 1.5 kg", null, "Soup, Onion pie"),
            ),
            shown,
        )
    }

    // Scenario 3 (amounts written as the card writes them, "2 cup"): flour in grams and in cups is one line marked "≈",
    // with what was counted; a part below the line's floor is noted as below it; a part with no measure is a line of
    // its own.
    @Test
    fun partsInOtherUnitsAreCountedApproximately() {
        val bread = recipe("Bread", 4, row(FLOUR, r(300), GRAM), row(FLOUR, r(1), PINCH))
        val cake = recipe("Cake", 4, row(FLOUR, r(2), CUP))
        assertEquals(
            listOf(
                ShownLine("flour — ≈ 560 g", "2 cup counted as ≈ 260 g", "Bread, Cake"),
                ShownLine("flour — 1 pinch", null, "Bread"),
            ),
            lines(at(bread), at(cake)),
        )
        // In US the line stays in cups (v0.35), and its tablespoon lies below the cups' floor.
        val tea = recipe("Tea cake", 4, row(SUGAR, r(1), CUP))
        val icing = recipe("Icing", 4, row(SUGAR, r(1), TABLESPOON))
        assertEquals(
            listOf(ShownLine("sugar — ≈ 1 cup", "1 tbsp counted as < ¼ cup", "Tea cake, Icing")),
            lines(at(tea), at(icing), system = UnitSystem.US),
        )
    }

    // Scenario 3: free text stays its own lines; "salt — to taste" of two recipes is one line; a measured salt line
    // gains "+ to taste". A qualifier is no part of a line's name.
    @Test
    fun freeTextAndTasteLines() {
        val soup = recipe("Soup", 4, free("bay leaf", r(2), PIECE), row(SALT, null, null), row(FLOUR, r(50), GRAM, qualifier = "sifted"))
        val stew = recipe("Stew", 4, free("bay leaf", r(1), PIECE), row(SALT, null, null))
        assertEquals(
            listOf("bay leaf — 2 pc", "salt — to taste", "flour — 50 g", "bay leaf — 1 pc"),
            lines(at(soup), at(stew)).map { it.text },
        )
        val bread = recipe("Bread", 4, row(SALT, r(5), GRAM))
        assertEquals(listOf(ShownLine("salt — 5 g + to taste", null, "Bread, Stew")), lines(at(bread), at(stew)).filter { it.text.startsWith("salt") })
    }

    private fun listedOf(vararg recipes: Pair<Recipe, Rational?>, system: UnitSystem = UnitSystem.METRIC) =
        listedLines(recipes.map { (recipe, count) -> ShoppingEntry(listed(recipe, count), recipe) }, dictionary, InterfaceLanguage.ENGLISH, context.resources, system)

    /** Each line's tick, by the line's text. */
    private fun ticksOf(vararg recipes: Pair<Recipe, Rational?>, system: UnitSystem = UnitSystem.METRIC) =
        listedOf(*recipes, system = system).associate { it.shown.text to it.tick }

    /** A list of [recipes] at their counts with the lines named by [texts] ticked as they ask now, read in [system]. */
    private fun ticked(vararg recipes: Pair<Recipe, Rational?>, texts: List<String>, system: UnitSystem = UnitSystem.METRIC): ShoppingList {
        val ticks = ticksOf(*recipes, system = system)
        return ShoppingList(
            recipes.map { (recipe, count) -> listed(recipe, count) },
            texts.associate { text -> ticks.getValue(text).let { TickedLine(POOL, it.key) to it.amount } },
        )
    }

    /** The texts of the lines of [list] ticked and still shown ticked, read in [system]. */
    private fun tickedTexts(list: ShoppingList, held: List<Recipe>, system: UnitSystem = UnitSystem.METRIC): Set<String> = listedOf(
        *list.recipes.map { listed -> held.first { it.id == listed.recipe } to listed.servings }.toTypedArray(),
        system = system,
    ).filter { line -> TickedLine(POOL, line.tick.key).let { it in list.ticks && line.tick.keeps(list.ticks[it]) } }.map { it.shown.text }.toSet()

    private fun ShoppingList.settledOver(vararg held: Recipe) = settled(POOL, held.associateBy { it.id }, dictionary)

    // T-231: a line's tick names what the line is — its entry and kind, free text and its recipe, a row as written and
    // its recipe — so that it is the same whatever other recipes are listed, and in whatever order; no two lines share
    // one.
    @Test
    fun aLinesTickIsTheSameWhateverOtherRecipesAreListed() {
        val soup = recipe(
            "Soup", 2, row(ONION, r(1), PIECE), row(FLOUR, r(300), GRAM), free("bay leaf", r(1), PIECE), free("bay leaf", r(2), PIECE),
            // Words that read as the second bay leaf's would, were its number only appended.
            free("bay leaf#1", r(3), PIECE), row(SALT, null, null), free("parsley", null, PIECE), row(FLOUR, r(1), PINCH),
        )
        val pie = recipe("Pie", 4, row(ONION, r(2), PIECE), row(FLOUR, r(2), CUP), free("bay leaf", r(1), PIECE), row(SALT, r(5), GRAM))
        val alone = listedOf(at(soup)).map { it.tick.key }
        assertEquals(alone.toSet().size, alone.size)
        val withPie = listedOf(at(soup), at(pie)).map { it.tick.key }
        val pieFirst = listedOf(at(pie), at(soup)).map { it.tick.key }
        assertEquals(withPie.toSet().size, withPie.size)
        // Salt to taste alone is a line of its kind; with the pie's 5 g it is the salt line, "5 g + to taste".
        val stays = alone.filterNot { it == "e:$SALT:taste" }
        assertTrue(withPie.containsAll(stays))
        assertTrue(pieFirst.containsAll(stays))
        assertEquals(withPie.toSet(), pieFirst.toSet())
        // The same twice over: the keys read the same each time.
        assertEquals(alone, listedOf(at(soup)).map { it.tick.key })
    }

    // T-231: a ticked line whose amount grows — a recipe added, its count raised — is unticked; one whose amount shrinks
    // or stays keeps its tick, at the amount it asks for now; a line that goes drops its tick.
    @Test
    fun aTickedLineThatGrowsIsUnticked() {
        val soup = recipe("Soup", 2, row(ONION, r(1), PIECE), row(FLOUR, r(300), GRAM))
        val pie = recipe("Pie", 4, row(ONION, r(2), PIECE), row(FLOUR, r(2), CUP), row(SUGAR, r(1), TABLESPOON))
        val all = listOf("onion — 3 pc", "flour — ≈ 560 g", "sugar — 1 tbsp")
        val list = ticked(at(soup), at(pie), texts = all)
        assertEquals(all.toSet(), tickedTexts(list, listOf(soup, pie)))

        val keys = listOf(ONION, FLOUR, SUGAR).map { TickedLine(POOL, "e:$it") }.toSet()
        // The pie for two: every line shrinks and keeps its tick, at the amount it asks for now.
        val fewer = list.withServings(POOL, pie.id, r(2)).settledOver(soup, pie)
        assertEquals(keys, fewer.ticks.keys)
        assertEquals(ShoppingAmount(GRAM, r(430)), fewer.ticks[TickedLine(POOL, "e:$FLOUR")])
        assertEquals(setOf("onion — 2 pc", "flour — ≈ 430 g", "sugar — ½ tbsp"), tickedTexts(fewer, listOf(soup, pie)))
        // The soup for one besides: the sugar stays as it was and keeps its tick.
        val soupFewer = fewer.withServings(POOL, soup.id, r(1)).settledOver(soup, pie)
        assertEquals(fewer.ticks[TickedLine(POOL, "e:$SUGAR")], soupFewer.ticks[TickedLine(POOL, "e:$SUGAR")])
        assertEquals(keys, soupFewer.ticks.keys)
        // The pie for four again: every line grows from what it last asked for, and is unticked.
        val more = soupFewer.withServings(POOL, pie.id, r(4)).settledOver(soup, pie)
        assertEquals(emptyMap<TickedLine, ShoppingAmount?>(), more.ticks)

        // The pie removed: the sugar line goes and drops its tick; onion and flour shrink and keep theirs.
        val without = list.without(POOL, pie.id).settledOver(soup, pie)
        assertEquals(setOf(TickedLine(POOL, "e:$ONION"), TickedLine(POOL, "e:$FLOUR")), without.ticks.keys)
        assertEquals(setOf("onion — 1 pc", "flour — 300 g"), tickedTexts(without, listOf(soup)))
        // Added again, the pie raises them both, and the sugar comes back unticked.
        val again = without.with(listed(pie)).settledOver(soup, pie)
        assertEquals(emptyMap<TickedLine, ShoppingAmount?>(), again.ticks)
        assertEquals(emptySet<String>(), tickedTexts(again, listOf(soup, pie)))
    }

    // An approximate line is compared on its total: one whose largest part changes is compared in grams.
    @Test
    fun anApproximateLineIsComparedOnItsTotal() {
        val bread = recipe("Bread", 4, row(FLOUR, r(300), GRAM))
        val cake = recipe("Cake", 4, row(FLOUR, r(2), CUP))
        val list = ticked(at(bread), at(cake), texts = listOf("flour — ≈ 560 g"))
        // The bread for one: 75 g and 2 cups is ≈ 2½ cups (≈ 335 g), less than the 560 g ticked — the tick stays.
        val fewer = list.withServings(POOL, bread.id, r(1)).settledOver(bread, cake)
        assertEquals(setOf("flour — ≈ 335 g"), tickedTexts(fewer, listOf(bread, cake)))
        // The cake for eight: ≈ 4½ cups, more than ≈ 2½ ticked — unticked.
        val more = fewer.withServings(POOL, cake.id, r(8)).settledOver(bread, cake)
        assertEquals(emptyMap<TickedLine, ShoppingAmount?>(), more.ticks)
    }

    // v0.35 T-250: a cup recipe and a gram recipe in the chosen system — the merged total converted once, the note naming
    // what was counted in the shown unit; spoons and pieces as written.
    @Test
    fun aCupRecipeAndAGramRecipeReadInTheChosenSystem() {
        val cake = recipe("Cake", 4, row(FLOUR, r(2), CUP), row(SUGAR, r(1), TABLESPOON), row(ONION, r(1), PIECE))
        val bread = recipe("Bread", 4, row(FLOUR, r(100), GRAM))
        assertEquals(
            listOf(
                ShownLine("flour — ≈ 360 g", "2 cup counted as ≈ 260 g", "Cake, Bread"),
                ShownLine("sugar — 1 tbsp", null, "Cake"),
                ShownLine("onion — 1 pc", null, "Cake"),
            ),
            lines(at(cake), at(bread)),
        )
        assertEquals(
            listOf(
                ShownLine("flour — ≈ 2¾ cup", "100 g counted as ≈ ¾ cup", "Cake, Bread"),
                ShownLine("sugar — 1 tbsp", null, "Cake"),
                ShownLine("onion — 1 pc", null, "Cake"),
            ),
            lines(at(cake), at(bread), system = UnitSystem.US),
        )
        assertEquals("мука — ≈ 2¾ стакан", lines(at(cake), at(bread), language = InterfaceLanguage.RUSSIAN, system = UnitSystem.US).first().text)
    }

    // v0.35 T-250: a line's key and the amount its tick holds stand as the recipes are written, so a tick taken in one
    // system holds in the other, and still goes once the line grows.
    @Test
    fun aTickSurvivesAChangeOfTheSystem() {
        val cake = recipe("Cake", 4, row(FLOUR, r(2), CUP))
        val bread = recipe("Bread", 4, row(FLOUR, r(100), GRAM))
        val list = ticked(at(cake), at(bread), texts = listOf("flour — ≈ 360 g"))
        assertEquals(ticksOf(at(cake), at(bread)).values.map { it.key }, ticksOf(at(cake), at(bread), system = UnitSystem.US).values.map { it.key })
        assertEquals(setOf("flour — ≈ 2¾ cup"), tickedTexts(list, listOf(cake, bread), UnitSystem.US))
        assertEquals(list, list.settledOver(cake, bread))
        val more = list.withServings(POOL, bread.id, r(8)).settledOver(cake, bread)
        assertEquals(emptySet<String>(), tickedTexts(more, listOf(cake, bread), UnitSystem.US))
    }

    // A line with no amount keeps its tick while it stays one; "salt — to taste" that gains an amount is unticked.
    @Test
    fun aLineToTasteThatGainsAnAmountIsUnticked() {
        val soup = recipe("Soup", 2, row(SALT, null, null), free("parsley", null, PIECE))
        val bread = recipe("Bread", 4, row(SALT, r(5), GRAM))
        val list = ticked(at(soup), texts = listOf("salt — to taste"))
        assertEquals(list, list.withServings(POOL, soup.id, r(6)).settledOver(soup).withServings(POOL, soup.id, r(2)))
        val added = list.with(listed(bread)).settledOver(soup, bread)
        assertEquals(emptyMap<TickedLine, ShoppingAmount?>(), added.ticks)
    }

    // The ticks of another book's lines are left as they are; while a listed recipe is not read, nothing is settled —
    // not even the tick of a line that recipe alone makes.
    @Test
    fun onlyTheBooksTicksAreSettledOnceEveryRecipeIsRead() {
        val soup = recipe("Soup", 2, row(ONION, r(1), PIECE))
        val pie = recipe("Pie", 4, row(ONION, r(2), PIECE), row(SUGAR, r(1), TABLESPOON))
        val elsewhere = TickedLine("other.recipefork", "e:$ONION")
        val list = ticked(at(soup), at(pie), texts = listOf("sugar — 1 tbsp")).let { it.copy(ticks = it.ticks + (elsewhere to null)) }
        val sugar = TickedLine(POOL, "e:$SUGAR")
        // The pie not read yet: the list as it is, the sugar's tick kept.
        assertEquals(list, list.settledOver(soup))
        assertTrue(sugar in list.settledOver(soup).ticks)
        // Both read: the sugar stays as it was; the other book's tick is untouched.
        assertEquals(list, list.settledOver(soup, pie))
        // The pie gone from the list: the sugar's line goes and drops its tick; the other book's stays.
        assertEquals(mapOf(elsewhere to null), list.without(POOL, pie.id).settledOver(soup, pie).ticks)
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        val bread = recipe("Хлеб", 4, row(FLOUR, r(300), GRAM), row(SALT, r(5), GRAM))
        val cake = recipe("", 4, row(FLOUR, r(2), CUP), row(SALT, null, null), free("лавровый лист", r(1), PIECE))
        assertEquals(
            listOf(
                ShownLine("мука — ≈ 560 г", "2 стакан — посчитано как ≈ 260 г", "Хлеб, Рецепт без названия"),
                ShownLine("соль — 5 г + по вкусу", null, "Хлеб, Рецепт без названия"),
                ShownLine("лавровый лист — 1 шт", null, "Рецепт без названия"),
            ),
            lines(at(bread), at(cake), language = InterfaceLanguage.RUSSIAN),
        )
    }

    // v0.35 T-246: ounces and grams of an entry with no measures are one exact line, its tick named by the dimension; a
    // ticked line keeps its tick as a metric recipe of the entry leaves, or joins while the line still shrinks.
    @Test
    fun aTickedOunceLineKeepsItsTickAsAMetricRecipeOfItsEntryLeavesOrJoins() {
        val butter = 77L
        val cake = recipe("Cake", 4, row(butter, r(12), OUNCE))
        val toast = recipe("Toast", 2, row(butter, r(100), GRAM))
        val key = TickedLine(POOL, "e:$butter:GRAM")
        // Both: 12 oz + 100 g = 440 g, one line, ticked.
        val both = ticked(at(cake), at(toast), texts = ticksOf(at(cake), at(toast)).keys.toList())
        assertEquals(setOf(key), both.ticks.keys)
        assertEquals(ShoppingAmount(GRAM, r(440)), both.ticks[key])
        // The toast removed: 12 oz (340 g) is less, and the line keeps its tick.
        val alone = both.without(POOL, toast.id).settledOver(cake, toast)
        assertEquals(ShoppingAmount(OUNCE, r(12)), alone.ticks[key])
        assertEquals(1, tickedTexts(alone, listOf(cake)).size)
        // The cake for two (6 oz, 170 g) and the toast back: 270 g is less than 12 oz, and the tick stays.
        val joined = alone.withServings(POOL, cake.id, r(2)).with(listed(toast)).settledOver(cake, toast)
        assertEquals(ShoppingAmount(GRAM, r(270)), joined.ticks[key])
        assertEquals(1, tickedTexts(joined, listOf(cake, toast)).size)
    }
}
