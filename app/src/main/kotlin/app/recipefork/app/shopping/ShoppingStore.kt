package app.recipefork.app.shopping

import android.content.Context
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import app.recipefork.core.shopping.ShoppingAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigInteger

/**
 * A recipe on the shopping list: [recipe] of the book whose file is [book], at the serving count [servings] — null for
 * a recipe with no count, which the list takes as authored.
 */
data class ListedRecipe(val book: String, val recipe: RecipeId, val servings: Rational?) {
    /** Whether this is the recipe [recipe] of [book], whatever its count. */
    fun isOf(book: String, recipe: RecipeId): Boolean = this.book == book && this.recipe == recipe
}

/** A line of the list as its tick names it: the line [line] ([LineTick.key]) of the recipes of the book whose file is [book]. */
data class TickedLine(val book: String, val line: String)

/**
 * The reader's shopping list (FLOW-074), as kept on the device: its [recipes] in the order they were added, each once,
 * and its [ticks] — the lines ticked as bought, each with the amount it asks for as it stands (null for a line with
 * none: to taste, or as written). Its own lines (T-232) are a part of their own beside them.
 */
data class ShoppingList(
    val recipes: List<ListedRecipe> = emptyList(),
    val ticks: Map<TickedLine, ShoppingAmount?> = emptyMap(),
) {
    val isEmpty: Boolean get() = recipes.isEmpty()

    /** [recipe] added at the end, or, already listed, set to its count where it stands. */
    fun with(recipe: ListedRecipe): ShoppingList =
        if (recipes.any { it.isOf(recipe.book, recipe.recipe) }) {
            copy(recipes = recipes.map { if (it.isOf(recipe.book, recipe.recipe)) recipe else it })
        } else {
            copy(recipes = recipes + recipe)
        }

    /** The recipe [recipe] of [book] at the count [servings]; the list as it is where it is not listed. */
    fun withServings(book: String, recipe: RecipeId, servings: Rational): ShoppingList =
        copy(recipes = recipes.map { if (it.isOf(book, recipe)) it.copy(servings = servings) else it })

    /** The list without the recipe [recipe] of [book]. */
    fun without(book: String, recipe: RecipeId): ShoppingList = copy(recipes = recipes.filterNot { it.isOf(book, recipe) })

    /** [line] ticked as bought, asking for [amount]; or, ticked, unticked. */
    fun toggled(line: TickedLine, amount: ShoppingAmount?): ShoppingList =
        copy(ticks = if (line in ticks) ticks - line else ticks + (line to amount))

    /**
     * The list with nothing of [book] ticked any more; its recipes stay. This is where the ticked own lines (T-232) leave
     * the list as well.
     */
    fun withoutTicked(book: String): ShoppingList = copy(ticks = ticks.filterKeys { it.book != book })
}

/**
 * Where the shopping list is kept: on the device alone (RET-010), never in a book, a recipe or anything sent. The
 * screen observes [list]; every change is kept at once.
 */
interface ShoppingStore {
    /** The list, as it changes. */
    val list: StateFlow<ShoppingList>

    /** Replaces the list with what [change] makes of it. */
    fun update(change: (ShoppingList) -> ShoppingList)
}

/** The list held in memory alone, as long as the object lives. */
class HeldShopping(initial: ShoppingList = ShoppingList()) : ShoppingStore {
    private val state = MutableStateFlow(initial)
    override val list: StateFlow<ShoppingList> = state.asStateFlow()

    @Synchronized
    override fun update(change: (ShoppingList) -> ShoppingList) {
        state.value = change(state.value)
    }
}

/** The list kept in a small preferences file of the device, as My kitchen and the done marks are. */
class PreferencesShopping(context: Context) : ShoppingStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(ShoppingListCodec.decoded(runCatching { preferences.getString(KEY, null) }.getOrNull()))
    override val list: StateFlow<ShoppingList> = state.asStateFlow()

    @Synchronized
    override fun update(change: (ShoppingList) -> ShoppingList) {
        val now = change(state.value)
        if (now == state.value) return
        preferences.edit().putString(KEY, ShoppingListCodec.encoded(now)).commit()
        state.value = now
    }

    internal companion object {
        const val FILE = "shopping"
        const val KEY = "list"
    }
}

/**
 * How the list is written in the store: one JSON object, each part of the list under a key of its own — "recipes",
 * "ticks", and its own lines under a key of their own once they come — so that a part added later breaks no list kept
 * before it. A reader takes the keys it knows and passes over the others; a key missing reads as an empty part, and
 * whatever does not read back — another type, a damaged text, a recipe or a tick without its identity — reads as
 * nothing.
 */
internal object ShoppingListCodec {
    private const val RECIPES = "recipes"
    private const val BOOK = "book"
    private const val RECIPE = "recipe"
    private const val SERVINGS = "servings"
    private const val TICKS = "ticks"
    private const val LINE = "line"
    private const val UNIT = "unit"
    private const val AMOUNT = "amount"

    fun encoded(list: ShoppingList): String = buildJsonObject {
        put(RECIPES, buildJsonArray {
            list.recipes.forEach { listed ->
                add(buildJsonObject {
                    put(BOOK, listed.book)
                    put(RECIPE, listed.recipe.value)
                    listed.servings?.let { put(SERVINGS, it.toString()) }
                })
            }
        })
        put(TICKS, buildJsonArray {
            list.ticks.forEach { (line, amount) ->
                add(buildJsonObject {
                    put(BOOK, line.book)
                    put(LINE, line.line)
                    amount?.let {
                        put(UNIT, it.unit.name)
                        put(AMOUNT, it.value.toString())
                    }
                })
            }
        })
    }.toString()

    fun decoded(text: String?): ShoppingList {
        val root = text?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return ShoppingList()
        val recipes = (root[RECIPES] as? JsonArray).orEmpty().mapNotNull { element ->
            val fields = element as? JsonObject ?: return@mapNotNull null
            val book = fields.text(BOOK)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val recipe = fields.text(RECIPE)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ListedRecipe(book, RecipeId(recipe), servingsOf(fields.text(SERVINGS)))
        }.distinctBy { it.book to it.recipe }
        val ticks = (root[TICKS] as? JsonArray).orEmpty().mapNotNull { element ->
            val fields = element as? JsonObject ?: return@mapNotNull null
            val book = fields.text(BOOK)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val line = fields.text(LINE)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            TickedLine(book, line) to amountOf(fields.text(UNIT), fields.text(AMOUNT))
        }.toMap()
        return ShoppingList(recipes, ticks)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.contentOrNull

    /** A kept count, written as `n` or `n/d`; anything else — or a count no reader can request — reads as none. */
    private fun servingsOf(text: String?): Rational? = rationalOf(text)?.takeIf(RequestedServings::holds)

    /** A ticked line's amount, its unit by name and its value as `n` or `n/d`; anything else reads as none. */
    private fun amountOf(unit: String?, value: String?): ShoppingAmount? {
        val known = QuantityUnit.entries.firstOrNull { it.name == unit } ?: return null
        return rationalOf(value)?.let { ShoppingAmount(known, it) }
    }

    private fun rationalOf(text: String?): Rational? {
        val parts = text?.split('/')?.takeIf { it.size in 1..2 } ?: return null
        return try {
            val denominator = if (parts.size == 2) BigInteger(parts[1]) else BigInteger.ONE
            if (denominator.signum() == 0) null else Rational.of(BigInteger(parts[0]), denominator)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
