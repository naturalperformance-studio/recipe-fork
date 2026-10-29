package app.recipefork.app.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import app.recipefork.app.R

/**
 * The one way out of an empty state: its words, its icon when it has one, and what it does. A filled tonal button, or —
 * [outlined] — an outlined one with words in the action colour, as the redesign draws the way out of a state in which
 * nothing was found (`.btn.o`, T-309).
 */
internal class EmptyAction(
    val label: String,
    @DrawableRes val icon: Int?,
    val tag: String,
    val outlined: Boolean,
    val onClick: () -> Unit,
) {
    /** A filled tonal way out. */
    constructor(label: String, @DrawableRes icon: Int?, tag: String, onClick: () -> Unit) : this(label, icon, tag, false, onClick)
}

/**
 * The engraved drawings of the redesign (redesign-1 § 5, "Empty state"): drawn large, 96–112 dp, above a headline in
 * Literata 22. Each is decorative — the words say what is empty.
 */
internal enum class Engraving {
    /** Mark 2b with light hairline cuts (mark-2.html, "Empty state, engraved mark"): the book's empty sections. */
    MARK,

    /** The thin-line cart (redesign-1 § 4.5, "Shopping list, empty"): the empty shopping list. */
    CART,
}

/**
 * A listing that holds nothing: a picture, a one-line headline naming what is empty, at most one sentence, and at most
 * one action that leads out of it (FLOW-042). The headline is read as a heading; the picture is left to the words.
 * The state stands near the top of its view, so that its action is in reach above the keyboard — or, [centred], in the
 * middle of the space it is given, as the book's states stand (T-309). Its action is at least [actionHeight] tall:
 * 48 dp, or the card's own target on the card.
 */
@Composable
internal fun EmptyState(
    @DrawableRes picture: Int,
    headline: String,
    sentence: String?,
    action: EmptyAction?,
    tag: String,
    modifier: Modifier = Modifier,
    actionHeight: Dp = 48.dp,
    centred: Boolean = false,
) {
    EmptyLayout(
        picture = {
            Icon(
                painterResource(picture),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(PICTURE_SIDE).testTag(LibraryTags.emptyPicture(tag)),
            )
        },
        pictureSide = PICTURE_SIDE,
        headlineStyle = MaterialTheme.typography.titleMedium,
        headline = headline,
        sentence = sentence,
        action = action,
        tag = tag,
        modifier = modifier,
        actionHeight = actionHeight,
        centred = centred,
    )
}

/**
 * An empty section shown with one of the redesign's [Engraving]s: the same layout, words and action as the other
 * empty states, with the engraving above a headline in Literata 22 (titleLarge); [centred] as the other states are.
 */
@Composable
internal fun EmptyState(
    engraving: Engraving,
    headline: String,
    sentence: String?,
    action: EmptyAction?,
    tag: String,
    modifier: Modifier = Modifier,
    centred: Boolean = false,
) {
    EmptyLayout(
        picture = { Engraved(engraving, Modifier.testTag(LibraryTags.emptyPicture(tag))) },
        pictureSide = engraving.side,
        headlineStyle = MaterialTheme.typography.titleLarge,
        // The design sets the headline 16 dp under the engraving.
        headlineGap = 8.dp,
        headline = headline,
        sentence = sentence,
        action = action,
        tag = tag,
        modifier = modifier,
        actionHeight = 48.dp,
        centred = centred,
    )
}

/** The side of an empty state's small picture. */
private val PICTURE_SIDE = 56.dp

/** The size of each engraving, as the design draws it: the mark at 112 dp, the cart at 96 dp. */
private val Engraving.side: Dp get() = when (this) {
    Engraving.MARK -> 112.dp
    Engraving.CART -> 96.dp
}

/**
 * The engraving [engraving], decorative. The mark is drawn in its own colours, in layers: the fork in ink, its bent
 * tine in beet, and the hairline cuts in the colour of the paper behind it. The cart is one thin line, muted.
 */
@Composable
private fun Engraved(engraving: Engraving, modifier: Modifier) {
    val colours = MaterialTheme.colorScheme
    Box(modifier.size(engraving.side)) {
        when (engraving) {
            Engraving.MARK -> {
                Layer(R.drawable.engraving_mark_ink, tint = colours.onSurface)
                Layer(R.drawable.engraving_mark_beet, tint = colours.tertiary)
                Layer(R.drawable.engraving_mark_cuts, tint = colours.background)
            }
            Engraving.CART -> Layer(R.drawable.engraving_cart, tint = colours.onSurfaceVariant)
        }
    }
}

/** One layer of an engraving, filling it, in [tint]; decorative. */
@Composable
private fun Layer(@DrawableRes drawable: Int, tint: Color) =
    Icon(painterResource(drawable), contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())

/**
 * The layout every empty state shares: the picture, [pictureSide] tall, above the words and the way out. Where the whole
 * state does not fit the space it is given — at a large font scale on a small phone (v0.42, F-3 option B) — the
 * picture is left out, and only the picture: the words and the way out stay, and they scroll only if even they do not
 * fit. The headline is set as a book entry's title is ([HyphenFitText]).
 */
@Composable
private fun EmptyLayout(
    picture: @Composable () -> Unit,
    pictureSide: Dp,
    headlineStyle: TextStyle,
    headline: String,
    sentence: String?,
    action: EmptyAction?,
    tag: String,
    modifier: Modifier,
    actionHeight: Dp,
    centred: Boolean,
    headlineGap: Dp = 0.dp,
) {
    // Centred, the state fills the space it is given and stands in its middle, as the redesign draws it (`.empty`,
    // T-309), kept above the keyboard where one is open so that its way out stays in reach. The middle needs no margin
    // above and below, only a narrow one where a large font fills the space.
    val placed = if (centred) modifier.fillMaxSize().imePadding() else modifier.fillMaxWidth()
    val margin = if (centred) 8.dp else 24.dp
    val density = LocalDensity.current
    BoxWithConstraints(placed) {
        // The height the state may take without scrolling: its space less its margins; unbounded in a list.
        val room = if (constraints.hasBoundedHeight) constraints.maxHeight - with(density) { (margin * 2).roundToPx() } else Constraints.Infinity
        Column(
            // Should even the words not fit, the state scrolls rather than clipping its action; the scroll keeps the
            // space's height as the least the state takes, so that a short one is centred in it.
            Modifier
                .then(if (centred) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = margin)
                .testTag(tag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (centred) Arrangement.Center else Arrangement.Top,
        ) {
            // The design sets the words 8 dp under the picture, and the headline [headlineGap] further.
            PictureWhereItFits(room, pictureSide, gap = 8.dp + headlineGap, picture) {
                EmptyWords(headlineStyle, headline, sentence, action, tag, actionHeight)
            }
        }
    }
}

/** The slots of [PictureWhereItFits]: the words are measured first, the picture composed only where it fits. */
private enum class EmptySlot { WORDS, PICTURE }

/**
 * [words], centred, with [picture] — [pictureSide] tall — [gap] above them, as long as the two fit in [room] pixels;
 * else the words alone, the picture not composed at all.
 */
@Composable
private fun PictureWhereItFits(room: Int, pictureSide: Dp, gap: Dp, picture: @Composable () -> Unit, words: @Composable () -> Unit) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val laidWords = subcompose(EmptySlot.WORDS, words).map { it.measure(loose) }
        val wordsHeight = laidWords.sumOf { it.height }
        val gapPx = gap.roundToPx()
        val fits = room == Constraints.Infinity || pictureSide.roundToPx() + gapPx + wordsHeight <= room
        val laidPicture = if (fits) subcompose(EmptySlot.PICTURE, picture).map { it.measure(loose) } else emptyList()
        val above = if (laidPicture.isEmpty()) 0 else laidPicture.maxOf { it.height } + gapPx
        val width = constraints.constrainWidth((laidWords + laidPicture).maxOfOrNull { it.width } ?: 0)
        layout(width, constraints.constrainHeight(above + wordsHeight)) {
            laidPicture.forEach { it.place((width - it.width) / 2, 0) }
            var y = above
            laidWords.forEach {
                it.place((width - it.width) / 2, y)
                y += it.height
            }
        }
    }
}

/** An empty state's words and its way out: the headline, read as a heading; the sentence, if any; the action, if any. */
@Composable
private fun EmptyWords(headlineStyle: TextStyle, headline: String, sentence: String?, action: EmptyAction?, tag: String, actionHeight: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HyphenFitText(
            headline,
            headlineStyle,
            Modifier.semantics { heading() }.testTag(LibraryTags.emptyHeadline(tag)),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (sentence != null) {
            Text(
                sentence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(LibraryTags.emptySentence(tag)),
            )
        }
        if (action != null) {
            val padding = if (action.icon == null) ButtonDefaults.ContentPadding else ButtonDefaults.ButtonWithIconContentPadding
            val placedAction = Modifier.padding(top = 8.dp).heightIn(min = actionHeight).testTag(action.tag)
            val content: @Composable RowScope.() -> Unit = {
                if (action.icon != null) {
                    Icon(painterResource(action.icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(action.label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                } else {
                    Text(action.label)
                }
            }
            if (action.outlined) {
                // The redesign's outlined button: a hairline in the outline colour and words in the action colour.
                OutlinedButton(
                    onClick = action.onClick,
                    contentPadding = padding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = placedAction,
                    content = content,
                )
            } else {
                FilledTonalButton(onClick = action.onClick, contentPadding = padding, modifier = placedAction, content = content)
            }
        }
    }
}
