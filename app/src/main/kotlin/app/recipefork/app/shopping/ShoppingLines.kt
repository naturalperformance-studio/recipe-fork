package app.recipefork.app.shopping

import android.content.res.Resources
import app.recipefork.app.R
import app.recipefork.app.card.ingredientLine
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.comparisonFactor
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.UnitLabels
import app.recipefork.core.shopping.CountedPart
import app.recipefork.core.shopping.EntryMeasures
import app.recipefork.core.shopping.ShoppingAmount
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

/** A line of the list: as its tick knows it, and as the screen shows it; [own] for a line of the reader's own. */
data class ListedLine(val tick: LineTick, val shown: ShownLine, val own: Boolean = false)

/** The reader's own lines of [book] on the list, in the order they were added: as written, with no recipe under them. */
fun ownLines(list: ShoppingList, book: String): List<ListedLine> =
    list.own.filter { it.book == book }.map { ListedLine(LineTick(it.key, null), ShownLine(it.text, null, ""), own = true) }

/**
 * A line of the list as its tick knows it. [key] names what the line is, the same however other recipes come and go: an
 * entry's line by its entry and its kind — the line of its measured units, a unit it has no measure for, "to taste" —
 * a free-text line by its recipe and words, a line as written by its recipe and row; a second line of the same name
 * in one recipe by its order among them. [amount] is what it asks for, in its unit (on the total, for an approximate
 * line); null for a line with none.
 */
data class LineTick(val key: String, val amount: ShoppingAmount?, private val factors: Map<String, Double> = emptyMap()) {
    /**
     * Whether a line ticked when it asked for [ticked] stays ticked now: its amount stayed or shrank. An amount now in
     * another unit — an approximate line's largest part changed — is compared by the entry's grams per unit, and
     * without them taken as grown; a line that gained or lost an amount has grown too.
     */
    fun keeps(ticked: ShoppingAmount?): Boolean {
        val now = amount
        if (now == null || ticked == null) return now == null && ticked == null
        if (now.unit == ticked.unit) return now.value <= ticked.value
        val before = gramsOf(ticked) ?: return false
        val after = gramsOf(now) ?: return false
        return after <= before * (1 + 1e-9)
    }

    private fun gramsOf(amount: ShoppingAmount): Double? =
        usable(comparisonFactor(factors, amount.unit))
            ?.let { it * amount.value.numerator.toDouble() / amount.value.denominator.toDouble() }
}

/**
 * The lines of the list of [entries], each at its count, added up by [ShoppingTotals] over the measures of the
 * ingredient [dictionary], and written in [language] with the words of [resources].
 *
 * A line is named by its entry's label, or the author's own words for free text, never with a qualifier; an entry the
 * dictionary no longer holds is an unnamed ingredient. Its amount is written as the line model writes it — "≈" before
 * an approximate one, "+ to taste" after one a recipe also takes to taste, "to taste" alone — and a row that cannot be
 * scaled as the card writes it unscaled.
 */
fun listedLines(
    entries: List<ShoppingEntry>,
    dictionary: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    resources: Resources,
): List<ListedLine> {
    val untitled = resources.getString(R.string.common_untitled_recipe)
    val titles = entries.associate { entry -> entry.recipe.id to shownTitle(entry.recipe, untitled) }
    val (lines, ticks) = totalsOf(entries, dictionary)
    return lines.zip(ticks) { line, tick ->
        val name = nameOf(line, dictionary, language, resources)
        val amount = amountOf(line, dictionary, language, resources)
        ListedLine(
            tick,
            ShownLine(
                text = amount?.let { resources.getString(R.string.shopping_line, name, it) } ?: name,
                counted = line.counted.takeIf { line.approximate && it.isNotEmpty() }
                    ?.joinToString(", ") { countedOf(it, language, resources) },
                from = line.recipes.mapNotNull(titles::get).joinToString(", "),
            ),
        )
    }
}

/** A recipe's title as the list shows it: as written, trimmed; [untitled] where it has none. */
fun shownTitle(recipe: Recipe, untitled: String): String = recipe.title.trim().ifEmpty { untitled }

/**
 * The keys of the [lines] of [book] the list ticks as bought: ticked, and not grown since ([LineTick.keeps]). The
 * screen shows them under "In the basket".
 */
fun tickedKeys(lines: List<ListedLine>, list: ShoppingList, book: String): Set<String> =
    lines.filter { line ->
        TickedLine(book, line.tick.key).let { it in list.ticks && line.tick.keeps(list.ticks[it]) }
    }.map { it.tick.key }.toSet()

/** The lines of [listedLines], as the screen shows them. */
fun shownLines(
    entries: List<ShoppingEntry>,
    dictionary: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    resources: Resources,
): List<ShownLine> = listedLines(entries, dictionary, language, resources).map { it.shown }

/**
 * The list with its ticks of [book] settled against the lines its recipes make now, each read from [held] at the count
 * the list holds it at: a ticked line gone drops its tick, one whose amount grew is unticked, and one whose amount
 * stayed or shrank keeps its tick, at the amount it asks for now — so that it is unticked as soon as it grows again.
 * An own line's tick stays while the line is on the list. Ticks of other books are left as they are; while a listed recipe of [book] is not among [held] — not read yet, or
 * unreadable — the list is left as it is.
 */
fun ShoppingList.settled(book: String, held: Map<RecipeId, Recipe>, dictionary: Map<Long, IngredientEntryEntity>): ShoppingList {
    if (ticks.keys.none { it.book == book }) return this
    val entries = recipes.filter { it.book == book }.map { ShoppingEntry(it, held[it.recipe] ?: return this) }
    val now = totalsOf(entries, dictionary).second.associateBy { it.key }
    val settled = ticks.mapNotNull { (line, amount) ->
        when {
            line.book != book -> line to amount
            // An own line asks for nothing that can grow: its tick stays while the line does.
            line.line.startsWith(OwnLine.OWN_KEY) -> (line to amount).takeIf { own.any { it.book == book && it.key == line.line } }
            else -> now[line.line]?.takeIf { it.keeps(amount) }?.let { line to it.amount }
        }
    }.toMap()
    return if (settled == ticks) this else copy(ticks = settled)
}

/** The lines [ShoppingTotals] adds up from [entries], each beside its [LineTick]. */
private fun totalsOf(entries: List<ShoppingEntry>, dictionary: Map<Long, IngredientEntryEntity>): Pair<List<ShoppingLine>, List<LineTick>> {
    val measures = dictionary.mapValues { (_, entry) -> EntryMeasures.of(entry.toDto()) }
    val recipes = entries.map { entry ->
        ShoppingRecipe(entry.recipe.id, entry.recipe.ingredients.sortedBy { it.position }, entry.authoredServings, entry.servings)
    }
    val lines = ShoppingTotals.of(recipes, measures)
    val used = HashSet<String>()
    val ticks = lines.map { line ->
        val factors = (line.name as? ShoppingName.Entry)?.let { measures[it.id]?.factors }.orEmpty()
        // A second line of the same — and any that would read as it — takes the first number free after it.
        val key = keyOf(line, factors)
        val unique = generateSequence(0) { it + 1 }.map { if (it == 0) key else "$key#$it" }.first(used::add)
        LineTick(unique, (line.quantity as? ShoppingQuantity.Measured)?.amount, factors)
    }
    return lines to ticks
}

/**
 * What [line] is, for its tick ([LineTick.key]), read with the entry's [factors]; a second line of the same is told
 * apart by the caller.
 */
private fun keyOf(line: ShoppingLine, factors: Map<String, Double>): String {
    val recipe = line.recipes.firstOrNull()?.value.orEmpty()
    return when (val quantity = line.quantity) {
        // Never added to: one row of one recipe, taken of what the row holds wherever it stands.
        is ShoppingQuantity.AsWritten -> "w:$recipe:${quantity.row.copy(position = 0).toString().hashCode()}"
        else -> when (val name = line.name) {
            is ShoppingName.FreeText -> "f:$recipe:${name.text}"
            is ShoppingName.Entry -> when {
                quantity !is ShoppingQuantity.Measured -> "e:${name.id}:taste"
                // The one line every measured unit of the entry merges into, whichever unit it is in.
                line.approximate || usable(comparisonFactor(factors, quantity.amount.unit)) != null -> "e:${name.id}"
                else -> "e:${name.id}:${quantity.amount.unit.name}"
            }
        }
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

/** A factor [ShoppingTotals] counts by: finite and above zero; null for any other. */
private fun usable(factor: Double?): Double? = factor?.takeIf { it.isFinite() && it > 0 }
