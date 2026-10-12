package app.recipefork.app.shopping

import android.content.res.Resources
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.print.PrintBlock
import app.recipefork.app.print.PrintDocument
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings

/**
 * The shopping list of [book] as a document to print or share (FLOW-075), as its screen shows it now: the title
 * "Shopping list"; under "Recipes" each recipe of [entries] with its serving count, where it has one; under "To buy"
 * the lines to buy — the recipes' lines added up over [dictionary], then the reader's own lines, each in the list's
 * order — each with an empty box to tick by pen, and under it, muted, what an approximate line counted and the recipes
 * it comes from; then, where any line is ticked, "In the basket" with those lines ticked and struck through. Every
 * word is written in [language] with the words of [resources], as the screen writes it.
 *
 * Null for a list with neither recipes nor own lines: there is nothing to print. A plain function, so that it can be
 * called off the main thread.
 */
fun shoppingDocument(
    entries: List<ShoppingEntry>,
    dictionary: Map<Long, IngredientEntryEntity>,
    list: ShoppingList,
    book: String,
    language: InterfaceLanguage,
    resources: Resources,
): PrintDocument? {
    val own = ownLines(list, book)
    if (entries.isEmpty() && own.isEmpty()) return null
    val lines = listedLines(entries, dictionary, language, resources) + own
    val ticked = tickedKeys(lines, list, book)
    val (bought, toBuy) = lines.partition { it.tick.key in ticked }
    val title = resources.getString(R.string.shopping_title)
    val untitled = resources.getString(R.string.common_untitled_recipe)
    val blocks = buildList {
        add(PrintBlock.Title(title))
        if (entries.isNotEmpty()) {
            add(PrintBlock.Heading(resources.getString(R.string.shopping_recipes)))
            entries.forEach { entry ->
                val name = shownTitle(entry.recipe, untitled)
                add(PrintBlock.Bullet(entry.servings?.let { resources.getString(R.string.shopping_document_recipe, name, servingsText(it, language, resources)) } ?: name))
            }
        }
        add(PrintBlock.Heading(resources.getString(R.string.shopping_to_buy)))
        toBuy.forEach { add(boxOf(it, ticked = false)) }
        if (bought.isNotEmpty()) {
            add(PrintBlock.Heading(resources.getString(R.string.shopping_basket)))
            bought.forEach { add(boxOf(it, ticked = true)) }
        }
    }
    return PrintDocument(title, blocks)
}

/** A recipe's serving count as the list's serving control writes it: "Servings: 4". */
fun servingsText(count: Rational, language: InterfaceLanguage, resources: Resources): String =
    resources.getString(R.string.card_servings, RequestedServings.text(count, language))

/** A line as a box line: its text, and under it the muted lines the screen shows — what it counted, its recipes. */
private fun boxOf(line: ListedLine, ticked: Boolean): PrintBlock.Box {
    val shown = line.shown
    val note = listOfNotNull(shown.counted, shown.from.takeIf { it.isNotEmpty() }).joinToString("\n").ifEmpty { null }
    return PrintBlock.Box(shown.text, ticked, note)
}
