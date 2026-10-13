package app.recipefork.app.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.recipefork.app.R
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.print.PrintWay
import app.recipefork.app.print.RecipesDocument
import app.recipefork.app.print.rememberPrintOut
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.RecipeId

/**
 * What the book prints, or writes as a PDF and shares (v0.37, T-264, scenarios 4 and 6), from an entry's ⋮ and from the
 * selection's bar; each takes recipes by their identities, at the count each one's card would open with.
 *
 * - [one]: one recipe, the document its card prints ([RecipesDocument.one]).
 * - [all]: the recipes in the order given, under the title "Recipes": a first page listing them, then each from a new
 *   page ([RecipesDocument.read]); once it has run to its end, handed over or failed, the selection it was taken from
 *   ends, if it is still the one on — not when it was cancelled.
 * - [busy]: whether a document is being made or written; meanwhile a tap on either starts nothing.
 *
 * A failure — the recipes do not read back, no PDF written — is said as the card says it ([rememberPrintOut]).
 */
internal class BookPrinting(
    val one: (recipeId: String, way: PrintWay) -> Unit,
    val all: (recipeIds: List<String>, way: PrintWay) -> Unit,
    val busy: Boolean,
)

/** The book's [BookPrinting], saying a failure in [snackbars]. */
@Composable
internal fun rememberBookPrinting(
    session: LibrarySession,
    recipes: RecipeRepository,
    card: CardEnvironment,
    snackbars: SnackbarHostState,
): BookPrinting {
    var busy by remember { mutableStateOf(false) }
    // The selection the action under way was taken from, if it was: it ends once the action has run to its end, if it
    // is still the selection then — not when the action was cancelled, nor a later selection.
    val takenFrom = remember { arrayOfNulls<BookSelection>(1) }
    val printOut = rememberPrintOut(
        snackbars,
        onBusy = { busy = it },
        onOver = {
            val selection = takenFrom[0]
            takenFrom[0] = null
            if (selection != null && session.selection.value == selection) session.endSelection()
        },
    )
    val context = LocalContext.current
    val title = stringResource(R.string.library_selection_document)
    return BookPrinting(
        one = { recipeId, way ->
            val resources = context.resources
            val photos = PhotoStore(context.applicationContext.filesDir)
            printOut(way) { RecipesDocument.one(RecipeId(recipeId), recipes, card, session::heldScale, resources, photos) }
        },
        all = { recipeIds, way ->
            val resources = context.resources
            val photos = PhotoStore(context.applicationContext.filesDir)
            if (!busy) takenFrom[0] = session.selection.value
            printOut(way) { RecipesDocument.read(title, recipeIds.map(::RecipeId), recipes, card, session::heldScale, resources, photos) }
        },
        busy = busy,
    )
}

/**
 * The progress of a document being made to print or share, in place, over the top of the view, shifting nothing; the
 * screen reader names it.
 */
@Composable
internal fun PrintProgress(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.library_print_preparing)
    LinearProgressIndicator(
        modifier.fillMaxWidth().semantics { contentDescription = label }.testTag(LibraryTags.PRINT_PROGRESS),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}
