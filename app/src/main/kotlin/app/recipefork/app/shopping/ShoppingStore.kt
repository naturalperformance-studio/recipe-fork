package app.recipefork.app.shopping

import android.content.Context
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
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

/**
 * The reader's shopping list (FLOW-074), as kept on the device: its [recipes] in the order they were added, each once.
 * Its own lines and its ticks (T-231, T-232) are parts of their own beside them.
 */
data class ShoppingList(val recipes: List<ListedRecipe> = emptyList()) {
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
 * How the list is written in the store: one JSON object, each part of the list under a key of its own — "recipes" now,
 * its own lines and its ticks under keys of their own once they come — so that a part added later breaks no list kept
 * before it. A reader takes the keys it knows and passes over the others; a key missing reads as an empty part, and
 * whatever does not read back — another type, a damaged text, a recipe without its identity — reads as nothing.
 */
internal object ShoppingListCodec {
    private const val RECIPES = "recipes"
    private const val BOOK = "book"
    private const val RECIPE = "recipe"
    private const val SERVINGS = "servings"

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
    }.toString()

    fun decoded(text: String?): ShoppingList {
        val root = text?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return ShoppingList()
        val recipes = (root[RECIPES] as? JsonArray).orEmpty().mapNotNull { element ->
            val fields = element as? JsonObject ?: return@mapNotNull null
            val book = fields.text(BOOK)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val recipe = fields.text(RECIPE)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ListedRecipe(book, RecipeId(recipe), servingsOf(fields.text(SERVINGS)))
        }.distinctBy { it.book to it.recipe }
        return ShoppingList(recipes)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.contentOrNull

    /** A kept count, written as `n` or `n/d`; anything else — or a count no reader can request — reads as none. */
    private fun servingsOf(text: String?): Rational? {
        val parts = text?.split('/')?.takeIf { it.size in 1..2 } ?: return null
        return try {
            val denominator = if (parts.size == 2) BigInteger(parts[1]) else BigInteger.ONE
            if (denominator.signum() == 0) null else Rational.of(BigInteger(parts[0]), denominator).takeIf(RequestedServings::holds)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
