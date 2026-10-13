package app.recipefork.app.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.WordFitText
import app.recipefork.app.ui.rememberHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/** The entries chosen in one [view] of the book while its selection is on: never none. */
data class BookSelection(val view: LibraryView, val ids: Set<String>) {
    val count: Int get() = ids.size
}

/**
 * An entry of a view as the selection sees it: the recipe it lists; whether the book's removal of it — Delete for an
 * own recipe its owner may delete (ST-010), Remove for a found one the book holds — applies to it; and whether the book
 * [holds] its content, which adding it to a collection or the shopping list needs. An entry no action applies to is
 * chosen all the same; an action takes only those it applies to.
 */
data class Selectable(val recipeId: String, val removable: Boolean, val holds: Boolean = true)

/**
 * One action of the selection's bar, on the recipes chosen: what it reads, its tag, its icon — an action with one stands
 * in the bar while there is room, the rest in the bar's ⋮ — and whether it takes recipes out of the book, which keeps it
 * in the ⋮, drawn in the error colour, as an entry's menu draws it.
 */
internal class SelectionAction(
    @StringRes val label: Int,
    val tag: String,
    @DrawableRes val icon: Int? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** How many actions stand in the bar as icons at most; the others go in its ⋮. */
private const val BAR_ICONS = 2

/**
 * What the bar holds besides its count and its icons: ✕ and ⋮ with their margins, the title's insets and its clearance
 * ([TITLE_CLEARANCE]) — a little more than Material lays out, so that the count is never short of it.
 */
private val BAR_FIXED = 132.dp

/** The room an icon takes in the bar. */
private val BAR_ICON = 48.dp

/**
 * The book's bar while its selection is on, in place of its title: ✕, which ends the selection, how many entries are
 * chosen ("3 selected"), and the [actions] on them — those with an icon in the bar, as far as [BAR_ICONS] allows and the
 * count leaves room for, every other in its ⋮ after "Select all", which chooses every entry the view lists. The count is
 * drawn on one line, made smaller as far as it needs to fit at a large font scale on a narrow phone, never below its
 * size at a font scale of 1: where it would not fit even so beside the icons, an icon goes to the ⋮ instead. The bar is
 * tinted — the theme's secondary container — so that it never reads as the book's own bar (v0.38).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionBar(
    count: Int,
    actions: List<SelectionAction>,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) = BoxWithConstraints {
    // One word to fitting: the count never wraps away from what it counts.
    val text = pluralStringResource(R.plurals.library_selection_count, count, count).replace(' ', ' ')
    val style = MaterialTheme.typography.titleLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val width = maxWidth
    val icons = remember(text, style, width, measurer, density) {
        // The count at the least size it is drawn at: as many pixels as its size in dp.
        val least = with(density) { style.fontSize.value.dp.toSp() }
        val drawn = with(density) { measurer.measure(text, style.copy(fontSize = least), softWrap = false, maxLines = 1).size.width.toDp() }
        ((width - BAR_FIXED - drawn) / BAR_ICON).toInt().coerceIn(0, BAR_ICONS)
    }
    val (inBar, inMenu) = actions.partition { it.icon != null && !it.destructive }.let { (iconed, rest) ->
        iconed.take(icons) to (iconed.drop(icons) + rest)
    }
    TopAppBar(
        navigationIcon = {
            BlockAction(
                R.drawable.ic_close,
                stringResource(R.string.library_selection_close),
                LibraryTags.SELECTION_CLOSE,
                onClick = onClose,
                modifier = Modifier.padding(start = 4.dp),
                action = stringResource(R.string.library_selection_close_action),
            )
        },
        title = {
            BoxWithConstraints(Modifier.padding(end = TITLE_CLEARANCE)) {
                WordFitText(
                    text,
                    MaterialTheme.typography.titleLarge,
                    constraints.maxWidth,
                    Modifier.semantics { heading() }.testTag(LibraryTags.SELECTION_COUNT),
                )
            }
        },
        actions = {
            inBar.forEach { action ->
                BlockAction(action.icon!!, stringResource(action.label), action.tag, onClick = action.onClick)
            }
            SelectionMenu(inMenu, onSelectAll, Modifier.padding(end = 4.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = modifier.testTag(LibraryTags.SELECTION_BAR),
    )
}

/**
 * Starting the book's selection — a long press on an entry — is felt: the application's confirmation ([Haptic.CONFIRM],
 * v0.38). Choosing more entries, or ending the selection, is not.
 */
@Composable
internal fun SelectionStartFeedback(session: LibrarySession) {
    val haptics = rememberHaptics()
    LaunchedEffect(session, haptics) {
        // The selection as it stands when the book is drawn is not a start: only one begun since is.
        session.selection.map { it != null }.distinctUntilChanged().drop(1).collect { on ->
            if (on) haptics.perform(Haptic.CONFIRM)
        }
    }
}

/**
 * The selection bar's ⋮: "Select all", then [actions], a destructive one in the error colour — by their words alone, as
 * every other ⋮'s items are: an action's icon is for the bar, and the ⋮'s labels line up whichever went there (v0.38).
 */
@Composable
private fun SelectionMenu(actions: List<SelectionAction>, onSelectAll: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        BlockAction(
            R.drawable.ic_row_menu,
            stringResource(R.string.library_selection_more),
            LibraryTags.SELECTION_MENU,
            onClick = { expanded = !expanded },
            action = stringResource(R.string.library_selection_more_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_selection_all)) },
                onClick = {
                    expanded = false
                    onSelectAll()
                },
                modifier = Modifier.testTag(LibraryTags.SELECT_ALL),
            )
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    colors = if (action.destructive) {
                        MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                    } else {
                        MenuDefaults.itemColors()
                    },
                    modifier = Modifier.testTag(action.tag),
                )
            }
        }
    }
}

/**
 * Where an entry's ⋮ stands, while the selection is on: whether the entry is chosen, drawn — a filled circle with a
 * check, or an empty circle — so that it never rests on colour alone (A11Y-008). The entry itself says it to the screen
 * reader; the mark is a picture of it, said by nothing.
 */
@Composable
internal fun SelectionMark(recipeId: String, selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).clearAndSetSemantics { testTag = LibraryTags.selectionMark(recipeId) }, contentAlignment = Alignment.Center) {
        Icon(
            painterResource(if (selected) R.drawable.ic_selected else R.drawable.ic_not_selected),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The book's bar while it fades out, the other bar fading in in its place ([leaving]): it takes no touch — a press
 * begun on it comes to nothing — and the screen reader no longer finds it, so that neither the selection's actions on
 * a selection just ended nor the title's actions as a selection starts can be taken from it.
 */
internal fun Modifier.leaving(leaving: Boolean): Modifier = if (!leaving) {
    this
} else {
    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
        .clearAndSetSemantics { testTag = LibraryTags.BAR_LEAVING }
}
