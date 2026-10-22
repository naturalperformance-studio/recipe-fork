package app.recipefork.app.library

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.ui.FittedText
import app.recipefork.app.ui.fitWords
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An entry of the book as a line of a cookbook's table of contents (v0.42, T-301): at its start the recipe's cover,
 * 60 dp, or — without one — the title's first letter as a drop cap ([DropCap]); beside it the entry's [lines], the
 * first of them its title; at its end, when there are any [actions], their ⋮, level with the title's first line. No
 * card: the row lies on the page, at least [ROW_MIN_HEIGHT] high, a hairline under it. [onOpen], when given, opens the
 * recipe. [inner] goes on the row itself, and [wrap] lays its content out — beside a drag column, in a collection's
 * screen.
 *
 * Where [onSelect] is given, a long press on the entry calls it: the book's selection starts with it. While the selection
 * is on, [selected] says whether the entry is chosen (null while it is off): a tap, or a long press, calls [onSelect] in
 * place of opening the recipe, the entry's start shows its mark of being chosen ([SelectionMark]) in place of its
 * cover, a chosen entry is tinted (`secondaryContainer`), its menu is not offered, and the screen reader hears
 * "Selected" or "Not selected" (A11Y-008).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookEntryRow(
    recipeId: String,
    title: String,
    coverId: String?,
    cover: EntryCover,
    actions: List<EntryAction>,
    onOpen: (() -> Unit)?,
    modifier: Modifier,
    inner: Modifier = Modifier,
    wrap: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    selected: Boolean? = null,
    onSelect: (() -> Unit)? = null,
    lines: @Composable ColumnScope.() -> Unit,
) {
    val openLabel = stringResource(R.string.library_entry_open_action)
    val selectLabel = stringResource(R.string.library_entry_select_action)
    val toggleLabel = if (selected == true) stringResource(R.string.library_entry_deselect_action) else selectLabel
    val state = stringResource(if (selected == true) R.string.library_entry_selected else R.string.library_entry_not_selected)
    val open = when {
        selected != null && onSelect != null -> Modifier
            // The views do not page while the selection is on; a sideways swipe across the row is no tap on it either.
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume() } }
            .combinedClickable(
                onClickLabel = toggleLabel, onLongClickLabel = toggleLabel, onLongClick = onSelect, onClick = onSelect,
                // Felt only through the application's haptics: starting the selection plays its confirmation.
                hapticFeedbackEnabled = false,
            )
            .semantics {
                this.selected = selected
                stateDescription = state
            }
        onSelect != null && onOpen != null ->
            Modifier.combinedClickable(
                onClickLabel = openLabel, role = Role.Button, onLongClickLabel = selectLabel, onLongClick = onSelect, onClick = onOpen,
                hapticFeedbackEnabled = false,
            )
        // An entry that does not open is still chosen by a long press.
        onSelect != null -> Modifier
            .pointerInput(onSelect) { detectTapGestures(onLongPress = { onSelect() }) }
            .semantics {
                onLongClick(label = selectLabel) {
                    onSelect()
                    true
                }
            }
        onOpen != null -> Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
        else -> Modifier
    }
    val marked = selected != null && onSelect != null
    val tint = if (selected == true) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier
    Column(modifier.fillMaxWidth().then(inner).then(tint)) {
        wrap {
            Layout(
                content = {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ROW_MIN_HEIGHT)
                            .semantics(mergeDescendants = true) {}
                            .then(open)
                            .testTag(LibraryTags.entry(recipeId))
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (actions.isEmpty()) 16.dp else MENU_ROOM),
                        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
                    ) {
                        Box(Modifier.size(LEADING_SIZE), contentAlignment = Alignment.Center) {
                            if (marked) {
                                SelectionMark(recipeId, selected == true)
                            } else {
                                cover(coverId, LibraryTags.cover(recipeId)) { DropCap(recipeId, title) }
                            }
                        }
                        Column(
                            Modifier.weight(1f).padding(top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            content = lines,
                        )
                    }
                    // The menu is not offered while the selection is on; its actions are the selection's.
                    if (!marked && actions.isNotEmpty()) EntryMenu(recipeId, title, actions)
                },
            ) { measurables, constraints ->
                val entry = measurables[0].measure(constraints)
                val menu = measurables.getOrNull(1)?.measure(Constraints())
                layout(entry.width, entry.height) {
                    entry.place(0, 0)
                    if (menu != null) {
                        // Level with the middle of the title's first line, at the row's end.
                        val line = entry[TitleLine].takeIf { it != AlignmentLine.Unspecified } ?: (entry.height / 2)
                        menu.placeRelative(entry.width - menu.width, (line - menu.height / 2).coerceAtLeast(0))
                    }
                }
            }
        }
        // A hairline between this row and the next.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The least height of an entry's row. */
internal val ROW_MIN_HEIGHT = 84.dp

/** The side of the cover, the drop cap and the selection mark's place at an entry's start. */
internal val LEADING_SIZE = 60.dp

/** The space between an entry's cover and its text, and between its text and its ⋮. */
private val ROW_GAP = 12.dp

/** The room kept at the end of an entry's text for its ⋮: the gap and the ⋮ itself, 48 dp (A11Y-003). */
private val MENU_ROOM = ROW_GAP + 48.dp

/** The size the drop cap's letter is set in. It is a picture in a fixed frame, so it does not follow the font scale. */
private const val DROP_CAP_SIZE = 30f

/** The test tags of an entry's row that [LibraryTags] does not hold. */
internal object BookEntryRowTags {
    /** The drop cap at the start of the entry of [recipeId]. */
    fun dropCap(recipeId: String) = "library-drop-cap-$recipeId"

    /** The line under the title of the entry of [recipeId] that names its parent. */
    fun provenance(recipeId: String) = "library-provenance-$recipeId"

    /** The provenance glyph at the start of that line. */
    fun provenanceGlyph(recipeId: String) = "library-provenance-glyph-$recipeId"
}

/** The letter a drop cap shows for [title]: its first letter or digit, in capitals; none for a title without one. */
internal fun dropCapLetter(title: String): String = title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()

/**
 * The start of an entry whose recipe has no cover (decisions.md #10): the title's first letter in Literata, 30, on
 * `surfaceContainer` in a 60 dp square. It is decorative — the row already reads its title — so the screen reader does
 * not hear it.
 */
@Composable
internal fun DropCap(recipeId: String, title: String) {
    val letter = dropCapLetter(title.ifBlank { stringResource(R.string.common_untitled_recipe) })
    val letterSize = with(LocalDensity.current) { DROP_CAP_SIZE.dp.toSp() }
    val style = MaterialTheme.typography.headlineSmall.copy(
        fontSize = letterSize,
        lineHeight = letterSize,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val measurer = rememberTextMeasurer()
    Box(
        Modifier
            .size(LEADING_SIZE)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
            // Drawn, not laid out as text: the screen reader and the row's label never meet the letter.
            .drawWithCache {
                val laid = measurer.measure(letter, style, maxLines = 1)
                onDrawBehind {
                    drawText(laid, topLeft = Offset((size.width - laid.size.width) / 2, (size.height - laid.size.height) / 2))
                }
            }
            .clearAndSetSemantics { testTag = BookEntryRowTags.dropCap(recipeId) },
    )
}

/**
 * An entry's title, in Literata (titleLarge), which tells the entry's menu where its first line stands ([TitleLine]).
 * At a large font scale it is made smaller just as far as its widest word needs to fit its room, never below its size at
 * a font scale of 1 ([fitWords]): it wraps between words, never inside one.
 */
@Composable
internal fun EntryTitle(recipeId: String, title: String) {
    // The middle of the first line, as the title was last laid out; the text is laid out while it is measured.
    val firstLine = remember { FirstLine() }
    val text = title.ifBlank { stringResource(R.string.common_untitled_recipe) }
    val style = MaterialTheme.typography.titleLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val centre = firstLine.centre.coerceIn(0, placeable.height)
            layout(placeable.width, placeable.height, mapOf(TitleLine to centre)) { placeable.place(0, 0) }
        },
    ) {
        val width = constraints.maxWidth
        val fitted = remember(text, style, width, measurer, density) { fitWords(text, style, width, measurer, density) }
        FittedText(
            text,
            fitted,
            modifier = Modifier.testTag(LibraryTags.entryTitle(recipeId)),
            onTextLayout = { firstLine.centre = ((it.getLineTop(0) + it.getLineBottom(0)) / 2).roundToInt() },
        )
    }
}

/** Where the middle of a title's first line stands, in pixels from the title's top. */
private class FirstLine(var centre: Int = 0)

/** The middle of an entry's first title line, which the entry's menu stands level with. */
private val TitleLine = HorizontalAlignmentLine { a, b -> min(a, b) }

/**
 * The tags a recipe carries, as its entry shows them: one muted line of their labels in Golos, cut short where it does
 * not fit; nothing for a recipe that carries none.
 */
@Composable
internal fun EntryTags(recipeId: String, tags: String) {
    if (tags.isEmpty()) return
    Text(
        tags,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(LibraryTags.entryTags(recipeId)),
    )
}

/**
 * The line under a version's title that names the recipe it was made from (FORK-010, decisions.md #3): the provenance
 * glyph, 16 dp, and «по рецепту «…»» in Literata italics, both in beet — a provenance mark. [parentTitle] is the title
 * the version recorded, else that of the parent the book holds; null when the parent is gone and the version recorded
 * none, which the line then says, as the card does. One line, cut short where it does not fit. It is part of the row's
 * spoken label and does nothing of its own: a tap on it opens the recipe, as anywhere on the row.
 */
@Composable
internal fun EntryProvenance(recipeId: String, parentTitle: String?) {
    val text = if (parentTitle == null) {
        stringResource(R.string.library_entry_parent_unavailable)
    } else {
        stringResource(R.string.library_entry_parent, parentTitle.ifBlank { stringResource(R.string.common_untitled_recipe) })
    }
    val beet = MaterialTheme.colorScheme.tertiary
    Row(
        Modifier.testTag(BookEntryRowTags.provenance(recipeId)),
        horizontalArrangement = Arrangement.spacedBy(PROVENANCE_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_provenance),
            contentDescription = null,
            tint = beet,
            modifier = Modifier.size(PROVENANCE_GLYPH).testTag(BookEntryRowTags.provenanceGlyph(recipeId)),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = beet,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The side of the provenance glyph on an entry (redesign-1 § 5, «Глиф «Происхождения»»). */
internal val PROVENANCE_GLYPH = 16.dp

/** The space between the provenance glyph and its words. */
private val PROVENANCE_GAP = 6.dp

/**
 * The author a found recipe's file states, under its title, marked as stated and not verified: in Literata italics, as
 * a byline in a book.
 */
@Composable
internal fun EntryAuthor(recipeId: String, author: String) {
    Text(
        stringResource(R.string.file_stated_author, author),
        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(LibraryTags.statedAuthor(recipeId)),
    )
}

/**
 * One item of an entry's menu: what it reads, its tag, what choosing it does, and whether it takes the recipe out of
 * the book — such an item is drawn in the theme's error colour, as on the card.
 */
internal class EntryAction(@StringRes val label: Int, val tag: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * The ⋮ of an entry, 48 by 48 dp (A11Y-003), named for the screen reader by the recipe it acts on (A11Y-005), with
 * the same name in a tooltip on a long press; it opens the menu of the entry's [actions].
 */
@Composable
private fun EntryMenu(recipeId: String, title: String, actions: List<EntryAction>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val name = title.ifBlank { stringResource(R.string.common_untitled_recipe) }
    Box(modifier) {
        BlockAction(
            R.drawable.ic_row_menu,
            stringResource(R.string.library_entry_actions, name),
            LibraryTags.actions(recipeId),
            onClick = { expanded = !expanded },
            action = stringResource(R.string.library_entry_actions_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
