package app.recipefork.app.library

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import app.recipefork.app.R
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.cardLanguage
import app.recipefork.app.collections.AddToCollectionSheet
import app.recipefork.app.collections.ChooseCollectionSheet
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.shopping.listedAtOpening
import app.recipefork.app.shopping.rememberAddAllToShopping
import app.recipefork.app.shopping.rememberAddToShopping
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * What the book puts into collections and on the shopping list (v0.37, T-263), from an entry's ⋮ and from the selection's
 * bar; each takes recipes by their identities.
 *
 * - [addToCollection]: the card's sheet of the reader's collections with checkboxes ([AddToCollectionSheet]), for one
 *   recipe; what the sheet says on closing is said in the book's snackbar.
 * - [addToShopping]: one recipe on the list as the card's ⋮ puts it, at the count its card would open with — at the end,
 *   or, listed already, set to that count — "Added to shopping list" or "Shopping list updated", with Open.
 * - [listed]: whether a recipe is on the shopping list, so that its ⋮ offers to update it rather than add it, as the
 *   card's does.
 * - [addAllToCollection]: the sheet of the reader's collections and "New collection…" ([ChooseCollectionSheet]); the
 *   collection chosen, or made, takes every one of the recipes it does not hold yet, in their order, at its end, and the
 *   selection ends: "3 recipes added to “Dinner”", or that they were all in it already, or — they left the book
 *   meanwhile — that nothing was added.
 * - [addAllToShopping]: every one on the list at the count its card would open with, one listed already keeping its
 *   count, as a collection's "Add all to shopping list" puts them and says so, with Open; the selection ends.
 */
internal class BookAdding(
    val addToCollection: (recipeId: String) -> Unit,
    val addToShopping: (recipeId: String) -> Unit,
    val listed: (recipeId: String) -> Boolean,
    val addAllToCollection: (recipeIds: List<String>) -> Unit,
    val addAllToShopping: (recipeIds: List<String>) -> Unit,
)

/**
 * The book's [BookAdding], saying what it did in [snackbars] in place of whatever they said; the shopping list's Open
 * calls [onOpenShopping]. The sheets it opens are shown from here, while they are open.
 */
@Composable
internal fun rememberBookAdding(
    session: LibrarySession,
    recipes: RecipeRepository,
    card: CardEnvironment,
    collections: CollectionRepository,
    snackbars: SnackbarHostState,
    onOpenShopping: () -> Unit,
): BookAdding {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val language = cardLanguage(LocalConfiguration.current)

    fun say(text: String) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    // The recipe whose card's sheet is open, and the recipes the selection's sheet would add, while either is open.
    var collecting by rememberSaveable { mutableStateOf<String?>(null) }
    var gathering by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    collecting?.let { recipeId ->
        AddToCollectionSheet(RecipeId(recipeId), collections) { notice ->
            collecting = null
            notice?.let(::say)
        }
    }

    // Every one of [recipeIds] the collection [id], named [name], does not hold yet goes in at its end.
    fun into(id: CollectionId, name: String, recipeIds: List<String>) {
        scope.launch {
            val ids = recipeIds.map(::RecipeId)
            val added = attempt { collections.addAll(id, ids) }
            // None went in: because it holds them all already, or because they left the book meanwhile.
            val allThere = added == 0 && attempt { collections.get(id)?.recipes?.containsAll(ids) } == true
            say(
                when {
                    added == null -> resources.getString(R.string.collection_sheet_change_failed)
                    allThere && ids.size == 1 -> resources.getString(R.string.library_one_in_collection, name)
                    allThere -> resources.getString(R.string.library_all_in_collection, name)
                    added == 0 -> resources.getString(R.string.library_none_added_to_collection, name)
                    else -> resources.getQuantityString(R.plurals.library_added_to_collection, added, added, name)
                },
            )
        }
    }

    gathering?.let { recipeIds ->
        ChooseCollectionSheet(
            collections,
            onChoose = { id, name ->
                gathering = null
                session.endSelection()
                into(id, name, recipeIds)
            },
            onCreate = { name ->
                gathering = null
                session.endSelection()
                scope.launch {
                    val id = attempt { collections.create(name) }
                    if (id == null) say(resources.getString(R.string.collection_sheet_create_failed)) else into(id, name, recipeIds)
                }
            },
            onDismiss = { gathering = null },
        )
    }

    val addOne = rememberAddToShopping(card.shopping, snackbars, onOpenShopping)
    val addAll = rememberAddAllToShopping(recipes, card, session::heldScale, snackbars, onOpenShopping)
    val list by card.shopping.list.collectAsState()
    val book = recipes.bookName().fileName
    val unread = resources.getString(R.string.library_shopping_unread)
    return BookAdding(
        addToCollection = { collecting = it },
        addToShopping = { recipeId ->
            scope.launch {
                val listed = listedAtOpening(RecipeId(recipeId), recipes, card, session::heldScale, language)
                if (listed == null) say(unread) else addOne(listed)
            }
        },
        listed = { recipeId -> list.recipes.any { it.isOf(book, RecipeId(recipeId)) } },
        addAllToCollection = { gathering = ArrayList(it) },
        addAllToShopping = { recipeIds ->
            session.endSelection()
            addAll { recipeIds.map(::RecipeId) }
        },
    )
}

/** What [change] came to; null where it failed. */
private suspend fun <T> attempt(change: suspend () -> T): T? =
    try {
        change()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
