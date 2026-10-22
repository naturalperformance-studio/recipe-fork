package app.recipefork.app.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recipefork.app.R
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.WordFitText
import app.recipefork.app.ui.fitWords
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
 * One action of the selection's bar at the bottom, on the recipes chosen: the word under its icon ([label]), what the
 * screen reader hears it does ([action]), its tag and icon; whether it applies to one of the recipes chosen at least
 * ([enabled]) — one that applies to none stands in its place, not responding; and whether it takes recipes out of the
 * book, which draws it in the error colour.
 */
internal class SelectionAction(
    @StringRes val label: Int,
    @StringRes val action: Int,
    val tag: String,
    @DrawableRes val icon: Int,
    val enabled: Boolean,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** The space that keeps the words beside it together. */
private const val NO_BREAK = '\u00A0'

/** How much of the bar's width "Select all" takes at most, so that the count keeps room beside it at large text. */
private const val SELECT_ALL_SHARE = 0.4f

/** The count's size: the redesign's 18 sp, in the working type (redesign-1 § 4.1 «выбрано несколько рецептов»). */
private val COUNT_SIZE = 18.sp
private val COUNT_LINE = 24.sp

/**
 * The book's bar while its selection is on, in place of its title (redesign-1 § 4.1, decisions.md #5): ✕, which ends
 * the selection, how many entries are chosen ("3 selected"), and "Select all", which chooses every entry the view lists.
 * The selection's actions stand at the bottom ([SelectionActionBar]). The count is drawn on one line, made smaller as
 * far as it needs to fit at a large font scale on a narrow phone, never below its size at a font scale of 1; "Select
 * all" keeps to one line the same way, taking at most [SELECT_ALL_SHARE] of the bar. The bar is tinted — the theme's
 * secondary container — so that it never reads as the book's own bar (v0.38).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) = BoxWithConstraints {
    // One word to fitting: the count never wraps away from what it counts; nor "Select all" onto two lines.
    val text = pluralStringResource(R.plurals.library_selection_count, count, count).replace(' ', NO_BREAK)
    val all = stringResource(R.string.library_selection_all).replace(' ', NO_BREAK)
    val colours = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleSmall.copy(fontSize = COUNT_SIZE, lineHeight = COUNT_LINE)
    val allWidth = maxWidth * SELECT_ALL_SHARE
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
                    style,
                    constraints.maxWidth,
                    Modifier.semantics { heading() }.testTag(LibraryTags.SELECTION_COUNT),
                )
            }
        },
        actions = {
            TextButton(
                onClick = onSelectAll,
                colors = ButtonDefaults.textButtonColors(contentColor = colours.onSecondaryContainer),
                modifier = Modifier.padding(end = 4.dp).widthIn(max = allWidth).heightIn(min = 48.dp).testTag(LibraryTags.SELECT_ALL),
            ) {
                BoxWithConstraints { WordFitText(all, LocalTextStyle.current, constraints.maxWidth) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colours.secondaryContainer,
            scrolledContainerColor = colours.secondaryContainer,
            navigationIconContentColor = colours.onSecondaryContainer,
            titleContentColor = colours.onSecondaryContainer,
            actionIconContentColor = colours.onSecondaryContainer,
        ),
        modifier = modifier.testTag(LibraryTags.SELECTION_BAR),
    )
}

/** The width of one action at the bottom, as the redesign draws five on a 360 dp phone (§ 5 «Панель действий выбора»). */
internal val SELECTION_ACTION_WIDTH = 72.dp

/** The least height of one action at the bottom: the redesign's 64 dp. */
internal val SELECTION_ACTION_HEIGHT = 64.dp

/** Where an action's icon stands below its top: its icon and a one-line word centred in its 64 dp. */
private val ACTION_TOP = 10.dp

/**
 * The selection's actions, at the bottom of the book under the thumb (decisions.md #5; redesign-1 § 4.1, § 5): each an
 * icon over its word in Golos 12 ([SelectionAction]), [SELECTION_ACTION_WIDTH] wide — narrower, sharing the width
 * equally, on a phone too narrow for them all — on the secondary container, a hairline above. A word too wide for its
 * action is made smaller, never cut ([fitLabel]), and wraps only between words; every word takes the size the one most
 * made smaller needs, so that the bar reads even. It stands above the navigation bar.
 */
@Composable
internal fun SelectionActionBar(actions: List<SelectionAction>, modifier: Modifier = Modifier) {
    val colours = MaterialTheme.colorScheme
    Surface(color = colours.secondaryContainer, contentColor = colours.onSecondaryContainer, modifier = modifier.testTag(LibraryTags.SELECTION_ACTIONS)) {
        Column {
            HorizontalDivider(color = colours.outlineVariant)
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(top = 6.dp),
            ) {
                val width = minOf(SELECTION_ACTION_WIDTH, maxWidth / actions.size.coerceAtLeast(1))
                val labels = actions.map { stringResource(it.label) }
                // Set close, as the redesign draws it, so that «коллекцию» stands whole in its 72 dp.
                val base = MaterialTheme.typography.labelMedium.copy(textAlign = TextAlign.Center, letterSpacing = 0.sp)
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val room = with(density) { width.roundToPx() }
                // Every word at one size, the least any of them needs, so that the bar reads even.
                val style = remember(labels, base, room, measurer, density) {
                    labels.map { fitLabel(it, base, room, measurer, density) }.minBy { it.fontSize.value }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    actions.zip(labels).forEach { (action, label) -> SelectionActionButton(action, label, style, Modifier.width(width)) }
                }
            }
        }
    }
}

/**
 * One of the selection's actions: a text button holding its icon over its word; named by its word, its click named for
 * what it does. Not applying to any recipe chosen, it is drawn and announced as not responding.
 */
@Composable
private fun SelectionActionButton(action: SelectionAction, label: String, style: TextStyle, modifier: Modifier = Modifier) {
    val said = stringResource(action.action)
    val colour = if (action.destructive) MaterialTheme.colorScheme.error else LocalContentColor.current
    TextButton(
        onClick = action.onClick,
        enabled = action.enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = colour),
        // The icons stand level along the bar, where they stand in the redesign's 64 dp, whether a word takes one line
        // or two.
        contentPadding = PaddingValues(top = ACTION_TOP, bottom = 4.dp),
        modifier = modifier
            .heightIn(min = SELECTION_ACTION_HEIGHT)
            .semantics { if (action.enabled) onClick(label = said, action = null) }
            .testTag(action.tag),
    ) {
        Column(Modifier.align(Alignment.Top), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(action.icon), contentDescription = null, modifier = Modifier.size(24.dp))
            // As wide as the action, the word centred in it.
            Text(label, style = style, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * [style] for an action's word [text] in [width] pixels, centred, wrapping only between words: made smaller as far as
 * its widest word needs to stand whole ([fitWords]) — and, on a phone so narrow that the actions are less than
 * [SELECTION_ACTION_WIDTH] wide, below its size at a font scale of 1 as far as that word needs, never cut.
 */
private fun fitLabel(text: String, style: TextStyle, width: Int, measurer: TextMeasurer, density: Density): TextStyle {
    val words = fitWords(text, style, width, measurer, density)
    if (!words.overflows) return words.style
    val widest = text.split(' ').maxOf { measurer.measure(it, words.style, softWrap = false, maxLines = 1).size.width }
    val ratio = width.toFloat() / widest * LABEL_MARGIN
    return words.style.copy(
        fontSize = words.style.fontSize * ratio,
        lineHeight = if (words.style.lineHeight.isSp) words.style.lineHeight * ratio else words.style.lineHeight,
    )
}

/** How much of the width a word made smaller to fit takes, so that rounding never leaves it a pixel short. */
private const val LABEL_MARGIN = 0.97f

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

/** How opaque a control of the book that does not respond while entries are chosen is drawn: the redesign's `.mute`. */
internal const val MUTED_ALPHA = 0.5f

/**
 * A control of the book — a tab, its indicator, the shelf of collections — that does not respond while entries are
 * chosen, drawn muted when [muted] (redesign-1 § 4.1 «выбрано несколько рецептов»: `.tabs.mute div{opacity:.5}`).
 */
internal fun Modifier.muted(muted: Boolean): Modifier = alpha(if (muted) MUTED_ALPHA else 1f)

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
