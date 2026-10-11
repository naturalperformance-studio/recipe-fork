package app.recipefork.app.shopping

import android.content.res.Resources
import app.recipefork.app.R
import app.recipefork.app.card.ingredientLine
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.UnitLabels
import app.recipefork.core.shopping.CountedPart
import app.recipefork.core.shopping.EntryMeasures
import app.recipefork.core.shopping.ShoppingLine
import app.recipefork.core.shopping.ShoppingName
import app.recipefork.core.shopping.ShoppingQuantity
import app.recipefork.core.shopping.ShoppingRecipe
import app.recipefork.core.shopping.ShoppingTotals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** A recipe of the list that the book holds: as listed, and as the book holds it now. */
data class ShoppingEntry(val listed: ListedRecipe, val recipe: Recipe) {
    /** The count the recipe was written for; null for a recipe with none. */
    val authoredServings: Int? get() = recipe.servings?.takeIf { it >= 1 }

    /** The count the list holds it at: as listed, the authored one where none is; null for a recipe with no count. */
    val servings: Rational? get() = authoredServings?.let { listed.servings ?: Rational.of(it.toLong()) }
}

/**
 * The recipes of the list the open book of [recipes] holds, in the list's order, as both change. A recipe the book no
 * longer holds — deleted, or removed — leaves the list by itself, so that none of its lines stays; one that cannot be
 * read is left unshown and kept. Nothing is emitted until every listed recipe has been read once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun shoppingEntries(store: ShoppingStore, recipes: RecipeRepository): Flow<List<ShoppingEntry>> {
    val book = recipes.bookName().fileName
    val read: Flow<Map<RecipeId, Result<Recipe?>>> = store.list
        .map { list -> list.recipes.filter { it.book == book }.map { it.recipe } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(ids.map { id -> recipes.observe(id).map { id to Result.success(it) }.catch { emit(id to Result.failure(it)) } }) { it.toMap() }
            }
        }
        .onEach { held ->
            val gone = held.filterValues { it.isSuccess && it.getOrNull() == null }.keys
            if (gone.isNotEmpty()) store.update { list -> gone.fold(list) { left, id -> left.without(book, id) } }
        }
    return combine(store.list, read) { list, held ->
        list.recipes.mapNotNull { listed ->
            if (listed.book != book) return@mapNotNull null
            held[listed.recipe]?.getOrNull()?.let { ShoppingEntry(listed, it) }
        }
    }
}

/**
 * A line of the list as the screen shows it: [text] — what to buy and how much, in the interface language — the
 * [counted] parts of an approximate line, null for none, and the titles of the recipes it [comes from][from].
 */
data class ShownLine(val text: String, val counted: String?, val from: String)

/**
 * The lines of the list of [entries], each at its count, added up by [ShoppingTotals] over the measures of the
 * ingredient [dictionary], and written in [language] with the words of [resources].
 *
 * A line is named by its entry's label, or the author's own words for free text, never with a qualifier; an entry the
 * dictionary no longer holds is an unnamed ingredient. Its amount is written as the line model writes it — "≈" before
 * an approximate one, "+ to taste" after one a recipe also takes to taste, "to taste" alone — and a row that cannot be
 * scaled as the card writes it unscaled.
 */
fun shownLines(
    entries: List<ShoppingEntry>,
    dictionary: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    resources: Resources,
): List<ShownLine> {
    val measures = dictionary.mapValues { (_, entry) -> EntryMeasures.of(entry.toDto()) }
    val recipes = entries.map { entry ->
        ShoppingRecipe(entry.recipe.id, entry.recipe.ingredients.sortedBy { it.position }, entry.authoredServings, entry.servings)
    }
    val titles = entries.associate { entry ->
        entry.recipe.id to entry.recipe.title.trim().ifEmpty { resources.getString(R.string.common_untitled_recipe) }
    }
    return ShoppingTotals.of(recipes, measures).map { line ->
        val name = nameOf(line, dictionary, language, resources)
        val amount = amountOf(line, dictionary, language, resources)
        ShownLine(
            text = amount?.let { resources.getString(R.string.shopping_line, name, it) } ?: name,
            counted = line.counted.takeIf { line.approximate && it.isNotEmpty() }
                ?.joinToString(", ") { countedOf(it, language, resources) },
            from = line.recipes.mapNotNull(titles::get).joinToString(", "),
        )
    }
}

private fun nameOf(line: ShoppingLine, dictionary: Map<Long, IngredientEntryEntity>, language: InterfaceLanguage, resources: Resources): String =
    when (val name = line.name) {
        is ShoppingName.Entry -> dictionary[name.id]?.let { if (language == InterfaceLanguage.RUSSIAN) it.labelRu else it.labelEn }
        is ShoppingName.FreeText -> name.text
    } ?: resources.getString(R.string.card_unnamed_ingredient)

private fun amountOf(line: ShoppingLine, dictionary: Map<Long, IngredientEntryEntity>, language: InterfaceLanguage, resources: Resources): String? =
    when (val quantity = line.quantity) {
        is ShoppingQuantity.Measured -> {
            val amount = quantity.amount.text(language).let { if (line.approximate) resources.getString(R.string.shopping_approximate, it) else it }
            if (quantity.plusToTaste) resources.getString(R.string.shopping_plus_to_taste, amount, UnitLabels.of(QuantityUnit.TO_TASTE, language)) else amount
        }
        ShoppingQuantity.ToTaste -> UnitLabels.of(QuantityUnit.TO_TASTE, language)
        is ShoppingQuantity.AsWritten -> ingredientLine(quantity.row, dictionary, language).let { it.quantity ?: it.toTaste }
    }

private fun countedOf(part: CountedPart, language: InterfaceLanguage, resources: Resources): String =
    resources.getString(
        if (part.belowFloor) R.string.shopping_counted_below else R.string.shopping_counted,
        part.original.text(language),
        part.countedText(language),
    )
