package app.recipefork.app.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.recipefork.app.Mode
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardScreen
import app.recipefork.app.card.ForkAsked
import app.recipefork.app.card.HeldForkDialog
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.card.forkUnlessHeld
import app.recipefork.app.card.keepScreenAwake
import app.recipefork.app.card.rememberCardEnvironment
import app.recipefork.app.data.DragHandleSide
import app.recipefork.app.data.UsualServings
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorScreen
import app.recipefork.app.editor.rememberEditorEnvironment
import app.recipefork.app.file.FileMessageDialog
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.library.CoverThumbnails
import app.recipefork.app.library.LibraryScreen
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.rememberCoverThumbnails
import app.recipefork.app.library.rememberRecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsScreen
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.launch

/** The application's destinations: the recipe book, a recipe's card, the editor on a recipe, and the settings. */
object Destinations {
    const val LIBRARY_ROUTE = "library"
    const val SETTINGS_ROUTE = "settings"

    /** The argument the card and the editor carry: the identity of their recipe. */
    const val RECIPE_ID_KEY = "recipeId"
    const val CARD_BASE_ROUTE = "card"
    const val EDITOR_BASE_ROUTE = "editor"
    const val CARD_ROUTE = CARD_BASE_ROUTE + "/{" + RECIPE_ID_KEY + "}"
    const val EDITOR_ROUTE = EDITOR_BASE_ROUTE + "/{" + RECIPE_ID_KEY + "}"

    fun card(recipeId: String) = CARD_BASE_ROUTE + "/" + Uri.encode(recipeId)
    fun editor(recipeId: String) = EDITOR_BASE_ROUTE + "/" + Uri.encode(recipeId)
}

/**
 * The navigation host: the recipe book is where the application opens; a recipe's card lies above it, and the editor
 * on a recipe above that recipe's card, so that back — the system's, its predictive gesture, or a screen's own back
 * action — goes one destination back: from the editor to the card of the recipe just edited (FLOW-029 (1)), and from
 * the card to the book, which is where it was left. The settings lie above the book, opened from its top bar; back
 * from them returns to the book where it was left.
 *
 * What is open is the [session]'s: its saved state says which card and which editor are open (FLOW-024, FLOW-029), and
 * whether the settings are; the back stack follows it. Back taken through the host — a destination left by the system's back — is told to the
 * session in turn. The book's list is a destination of its own, kept beneath the card and the editor, so it keeps its
 * place and what it has loaded while they are open.
 *
 * A file that could not be imported or exported is said in a dialog over whatever is shown, until it is closed; a
 * recipe a file brought is said in the book's snackbar.
 */
@Composable
fun AppNavHost(
    session: LibrarySession,
    recipes: RecipeRepository,
    mode: Mode = Mode.Standard,
    modifier: Modifier = Modifier,
    editor: EditorEnvironment = rememberEditorEnvironment(),
    card: CardEnvironment = rememberCardEnvironment(),
    files: RecipeImport = rememberRecipeImport(recipes),
    thumbnails: CoverThumbnails = rememberCoverThumbnails(),
    usualServings: UsualServings = card.usualServings,
    dragHandleSide: DragHandleSide = editor.dragHandleSide,
    navController: NavHostController = rememberNavController(),
) {
    val message by session.message.collectAsState()
    message?.takeUnless { it.isNotice }?.let { FileMessageDialog(it, onDone = session::told) }
    val recipeArgument = listOf(navArgument(Destinations.RECIPE_ID_KEY) { type = NavType.StringType })
    NavHost(
        navController,
        startDestination = Destinations.LIBRARY_ROUTE,
        modifier = modifier,
        enterTransition = { slideInHorizontally(tween(MOTION_MS)) { it } },
        exitTransition = { slideOutHorizontally(tween(MOTION_MS)) { -it / BENEATH_SHIFT } },
        popEnterTransition = { slideInHorizontally(tween(MOTION_MS)) { -it / BENEATH_SHIFT } },
        popExitTransition = { slideOutHorizontally(tween(MOTION_MS)) { it } },
    ) {
        composable(Destinations.LIBRARY_ROUTE) {
            LibraryScreen(
                session, recipes, mode,
                editor = editor, card = card, files = files, thumbnails = thumbnails,
                onOpenSettings = session::openSettings,
            )
        }
        composable(Destinations.SETTINGS_ROUTE) {
            SettingsScreen(
                usualServings, dragHandleSide, recipes, mode, session.viewModelScope,
                onBack = session::closeSettings,
            )
        }
        composable(Destinations.CARD_ROUTE, arguments = recipeArgument) { entry ->
            RecipeCard(RecipeId(entry.recipeId()), session, recipes, editor, card)
        }
        composable(Destinations.EDITOR_ROUTE, arguments = recipeArgument) { entry ->
            EditorScreen(RecipeId(entry.recipeId()), recipes, editor, session.viewModelScope, onClose = session::closeEditor)
        }
    }
    val sync = remember(navController, session) { BackStackSync(navController, session) }
    LaunchedEffect(sync) { navController.currentBackStack.collect { sync.changed() } }
    val viewing by session.viewing.collectAsState()
    val editing by session.editing.collectAsState()
    val settings by session.settings.collectAsState()
    LaunchedEffect(sync, viewing, editing, settings) { sync.show(shownFor(viewing, editing, settings)) }
}

/**
 * A recipe's card: its edit action opens the editor on it; its fork action writes a fork and opens the editor on it,
 * so that leaving the editor shows the fork's card (FLOW-029 (1)), or first offers an unchanged fork the book holds
 * already (FLOW-015); a fork's provenance opens its parent's card in place of the fork's. The screen stays on while
 * the card is shown, and is left to the device once it is not (FLOW-014).
 */
@Composable
private fun RecipeCard(
    id: RecipeId,
    session: LibrarySession,
    recipes: RecipeRepository,
    editor: EditorEnvironment,
    card: CardEnvironment,
) {
    // One fork per tap: a second tap while the first is being written adds none.
    var forking by remember(id) { mutableStateOf(false) }
    // An unchanged fork the book already holds, offered in place of a new one (FLOW-015).
    var held by rememberSaveable(id.value) { mutableStateOf<String?>(null) }
    fun fork(another: Boolean) {
        if (forking) return
        forking = true
        session.viewModelScope.launch {
            // The guard stays up once a fork is written: a tap landing before the editor replaces the card must not
            // write a second one. Only a fork that was not written lets the card try again.
            val asked = if (another) {
                forkRecipe(recipes, editor.book, id, editor.clock())?.let(ForkAsked::Made)
            } else {
                forkUnlessHeld(recipes, editor.book, id, editor.clock())
            }
            when (asked) {
                is ForkAsked.Made -> session.openEditor(asked.fork.value)
                is ForkAsked.Held -> { held = asked.fork.value; forking = false }
                null -> forking = false
            }
        }
    }
    held?.let { offered ->
        HeldForkDialog(
            onOpen = { held = null; session.openEditor(offered) },
            onAnother = { held = null; fork(another = true) },
            onDismiss = { held = null },
        )
    }
    CardScreen(
        id, recipes, card,
        onBack = session::closeCard,
        onEdit = { session.openEditor(id.value) },
        modifier = Modifier.keepScreenAwake(),
        onFork = { fork(another = false) },
        onOpenParent = { parent -> session.openCard(parent.value) },
        session = session.cards,
    )
}

private fun NavBackStackEntry.recipeId(): String = requireNotNull(arguments?.getString(Destinations.RECIPE_ID_KEY))

/** A destination on the back stack, with the recipe it is open on. */
internal sealed interface Shown {
    val route: String

    data object Book : Shown {
        override val route = Destinations.LIBRARY_ROUTE
    }

    data class Card(val recipeId: String) : Shown {
        override val route get() = Destinations.card(recipeId)
    }

    data class Editor(val recipeId: String) : Shown {
        override val route get() = Destinations.editor(recipeId)
    }

    data object Settings : Shown {
        override val route = Destinations.SETTINGS_ROUTE
    }
}

/**
 * The back stack that shows what the session holds open: the book, beneath the card of the recipe open, beneath the
 * editor on it. The editor lies above the card of the recipe it edits — the one leaving it shows. The [settings] lie
 * above the book alone: a card or the editor open is shown in their place.
 */
internal fun shownFor(viewing: String?, editing: String?, settings: Boolean = false): List<Shown> = buildList {
    add(Shown.Book)
    (editing ?: viewing)?.let { add(Shown.Card(it)) }
    editing?.let { add(Shown.Editor(it)) }
    if (settings && size == 1) add(Shown.Settings)
}

/** The destinations on the back stack, the book first. */
internal fun NavController.shown(): List<Shown> = currentBackStack.value.mapNotNull { entry ->
    when (entry.destination.route) {
        Destinations.LIBRARY_ROUTE -> Shown.Book
        Destinations.CARD_ROUTE -> Shown.Card(entry.recipeId())
        Destinations.EDITOR_ROUTE -> Shown.Editor(entry.recipeId())
        Destinations.SETTINGS_ROUTE -> Shown.Settings
        else -> null
    }
}

/**
 * Keeps the back stack and the session in step: [show] brings the back stack to what the session holds open, keeping
 * what both share; a destination popped through the host — back — is told to the session as the editor, the card or
 * the settings closed, as their back actions close them.
 */
private class BackStackSync(private val nav: NavController, private val session: LibrarySession) {
    /** The back stack as last seen. */
    private var before: List<Shown> = emptyList()

    fun show(target: List<Shown>) {
        val now = nav.shown()
        if (now.isEmpty() || now == target) return
        val kept = now.zip(target).takeWhile { (a, b) -> a == b }.size
        repeat(now.size - kept) { nav.popBackStack() }
        target.drop(kept).forEach { nav.navigate(it.route) }
        before = nav.shown()
    }

    /** The back stack changed: one shorter than before, and otherwise the same, was popped by back. */
    fun changed() {
        val now = nav.shown()
        val was = before
        before = now
        val popped = now.size < was.size && was.take(now.size) == now
        if (!popped) return
        if (now.none { it is Shown.Editor } && session.editing.value != null) session.closeEditor()
        if (now.none { it is Shown.Card } && session.viewing.value != null) session.closeCard()
        if (now.none { it is Shown.Settings } && session.settings.value) session.closeSettings()
    }
}

/** How long a destination takes to slide in or out. */
private const val MOTION_MS = 300

/** The destination beneath moves by this fraction of the width while the one above slides over it. */
private const val BENEATH_SHIFT = 4
