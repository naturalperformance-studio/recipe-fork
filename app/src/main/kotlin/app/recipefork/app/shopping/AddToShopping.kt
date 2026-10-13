package app.recipefork.app.shopping

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.recipefork.app.R
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.HeldScale
import app.recipefork.app.card.cardLanguage
import app.recipefork.app.card.openingServings
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Puts a recipe on the shopping list [store] (FLOW-074) — at the end, or, already there, at the new count where it
 * stands, never twice — and says so in [snackbars], in place of whatever they said: "Added to shopping list", or
 * "Shopping list updated", with Open, which opens the list ([onOpen]). The card's ⋮ and an entry's ⋮ in the book.
 */
@Composable
internal fun rememberAddToShopping(store: ShoppingStore, snackbars: SnackbarHostState, onOpen: () -> Unit): (ListedRecipe) -> Unit {
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpen)
    val added = stringResource(R.string.card_shopping_added)
    val updated = stringResource(R.string.card_shopping_updated)
    val openLabel = stringResource(R.string.card_shopping_open)
    return remember(store, scope, snackbars, added, updated, openLabel) {
        { recipe ->
            val already = store.list.value.recipes.any { it.isOf(recipe.book, recipe.recipe) }
            store.update { it.with(recipe) }
            scope.launch {
                snackbars.currentSnackbarData?.dismiss()
                val result = snackbars.showSnackbar(if (already) updated else added, actionLabel = openLabel, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) open()
            }
        }
    }
}

/**
 * Puts several recipes on the shopping list of [card] together — a collection's "Add all to shopping list" (v0.36,
 * T-257), or the book's selection (v0.37, T-263) — in their order, each at the count its card would open with
 * ([openingServings]), one listed already keeping its count; and says how many were added in [snackbars], in place of
 * whatever they said, with Open ([onOpen]) — or, none added, that every one of them is listed already, or that none could
 * be read. The function returned is given the recipes, read when it runs. One at a time: a second call while the first
 * is read adds nothing.
 */
@Composable
internal fun rememberAddAllToShopping(
    recipes: RecipeRepository,
    card: CardEnvironment,
    heldScale: (RecipeId) -> HeldScale?,
    snackbars: SnackbarHostState,
    onOpen: () -> Unit,
): (members: suspend () -> List<RecipeId>) -> Unit {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val language = cardLanguage(LocalConfiguration.current)
    val allListed = stringResource(R.string.collection_all_in_shopping)
    val unread = stringResource(R.string.collection_shopping_unread)
    val open = stringResource(R.string.collection_shopping_open)
    val opened by rememberUpdatedState(onOpen)
    var adding by remember { mutableStateOf(false) }
    return { members ->
        if (!adding) {
            adding = true
            scope.launch {
                val outcome = try {
                    addAllToShopping(members(), recipes, card, heldScale, language)
                } finally {
                    adding = false
                }
                val added = outcome.added
                val said = when {
                    added > 0 -> resources.getQuantityString(R.plurals.collection_added_to_shopping, added, added)
                    outcome.allListed -> allListed
                    else -> unread
                }
                snackbars.currentSnackbarData?.dismiss()
                // Nothing to open when nothing could be read.
                val action = open.takeIf { added > 0 || outcome.allListed }
                val result = snackbars.showSnackbar(said, actionLabel = action, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) opened()
            }
        }
    }
}

/** What adding several recipes came to: how many were [added], and whether every one of them is listed now. */
internal class AddedAll(val added: Int, val allListed: Boolean)

/** Puts [members] on the list as [rememberAddAllToShopping] says. */
internal suspend fun addAllToShopping(
    members: List<RecipeId>,
    recipes: RecipeRepository,
    card: CardEnvironment,
    heldScale: (RecipeId) -> HeldScale?,
    language: InterfaceLanguage,
): AddedAll {
    val book = recipes.bookName().fileName
    val opening = OpeningCount(card, heldScale, language)
    val wanted = members.mapNotNull { opening.listed(it, recipes) }
    var added = 0
    card.shopping.update { list -> list.withAll(wanted).also { added = it.recipes.size - list.recipes.size } }
    val listed = card.shopping.list.value.recipes
    return AddedAll(added, members.all { member -> listed.any { it.isOf(book, member) } })
}

/**
 * The recipe [id] as the shopping list would take it, at the count its card would open with ([openingServings]); null
 * where it does not read back — read as its card reads it.
 */
internal suspend fun listedAtOpening(
    id: RecipeId,
    recipes: RecipeRepository,
    card: CardEnvironment,
    heldScale: (RecipeId) -> HeldScale?,
    language: InterfaceLanguage,
): ListedRecipe? = OpeningCount(card, heldScale, language).listed(id, recipes)

/** What the count a card opens with is read from, read once: the usual count and the ingredient dictionary. */
private class OpeningCount(
    private val card: CardEnvironment,
    private val heldScale: (RecipeId) -> HeldScale?,
    private val language: InterfaceLanguage,
) {
    // Read once, as the card reads it on opening.
    private val usual = card.usualServings.count.value
    private var entries: Map<Long, IngredientEntryEntity>? = null

    // Rows whose entry cannot be read are counted as the card counts them: without it.
    private suspend fun entries(): Map<Long, IngredientEntryEntity> = entries ?: try {
        card.ingredients.entries()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }.associateBy { it.id }.also { entries = it }

    suspend fun listed(id: RecipeId, recipes: RecipeRepository): ListedRecipe? {
        val recipe = try {
            recipes.observe(id).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return ListedRecipe(recipes.bookName().fileName, recipe.id, openingServings(recipe, heldScale(recipe.id), usual, entries(), language))
    }
}
