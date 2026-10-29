package app.recipefork.app.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.StatedParent
import kotlinx.coroutines.flow.map

/** What the provenance line knows of the parent: nothing yet, its title, or that the book does not hold it. */
private sealed interface ParentContent {
    data object Loading : ParentContent
    data object Unavailable : ParentContent
    data class Held(val title: String) : ParentContent
}

/**
 * The provenance line of a version and the title it stands above («Происхождение», decisions.md 2026-10-15 and its
 * fixes; FORK-004, FORK-010, FLOW-065): the one direct parent, named in one line «по рецепту «…»» in the working face
 * and beet, beside a 26 dp column that draws the parent as a hollow node, a line with one knee and this recipe as a
 * filled node level with the first line of the title. The [heading] — the title and what stands under it — is laid out
 * beside the column under the line; it reports its title's layout so that the filled node finds the title's first line.
 *
 * While the book holds the parent, the line and its column are one button that opens the parent, with a chevron at the
 * line's end; the parent's author is named on the parent's own card, not here. A parent the book does not hold is drawn
 * with a dashed node, opens nothing, and the author the file states, if any, stands under the line, small and muted,
 * marked as not verified. No descendant is ever listed or counted (FORK-004 (3)).
 *
 * The screen reader reads the title first and then the line, as the redesign decided (§ 6, question 2).
 */
@Composable
internal fun ProvenanceLine(
    parent: RecipeId,
    stated: StatedParent?,
    recipes: RecipeRepository,
    onOpenParent: (RecipeId) -> Unit,
    heading: @Composable (onTitleLaidOut: (TextLayoutResult) -> Unit) -> Unit,
) {
    val content by remember(parent, recipes) {
        recipes.observe(parent).map { it?.let { held -> ParentContent.Held(held.title) } ?: ParentContent.Unavailable }
    }.collectAsState(initial = ParentContent.Loading)
    val beet = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    // Where, within the whole, the middle of the line's first line and of the title's first line stand, in pixels.
    val place = remember { WholePlace() }
    var parentY by remember { mutableFloatStateOf(Float.NaN) }
    var titleMiddle by remember { mutableFloatStateOf(Float.NaN) }
    var headingTop by remember { mutableFloatStateOf(Float.NaN) }
    val shown = content
    val drawing = if (shown == ParentContent.Loading || parentY.isNaN() || headingTop.isNaN() || titleMiddle.isNaN()) {
        null
    } else {
        with(density) { ProvenanceDrawing.of(parentY.toDp(), (headingTop + titleMiddle).toDp(), held = shown is ParentContent.Held) }
    }
    Box(Modifier.fillMaxWidth().onPlaced { place.whole = it }.semantics { isTraversalGroup = true }) {
        Column(Modifier.fillMaxWidth()) {
            Words(shown, stated, beet, onOpen = { onOpenParent(parent) }) { coordinates, middle ->
                place.within(coordinates)?.let { parentY = it + middle }
            }
            Box(
                Modifier
                    .padding(start = WORDS_START)
                    .onGloballyPositioned { coordinates -> place.within(coordinates)?.let { headingTop = it } }
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = TITLE_FIRST
                    },
            ) {
                heading { layout -> titleMiddle = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2 }
            }
        }
        Box(Modifier.matchParentSize()) {
            Spacer(
                Modifier
                    .width(PROVENANCE_COLUMN)
                    .fillMaxHeight()
                    .then(drawing?.let { Modifier.semantics { provenanceDrawing = it } } ?: Modifier)
                    .drawBehind { drawing?.let { draw(it, beet) } }
                    .testTag(ProvenanceTags.COLUMN),
            )
        }
    }
}

/** The coordinates of the whole, to place its parts' middles within it. */
private class WholePlace {
    var whole: LayoutCoordinates? = null

    /** How far below the top of the whole the top of [part] stands, in pixels; null while either is not laid out. */
    fun within(part: LayoutCoordinates): Float? =
        whole?.takeIf { it.isAttached && part.isAttached }?.localPositionOf(part, Offset.Zero)?.y
}

/**
 * The line's words, and under them the stated author where the book does not hold the parent: a place at least the
 * card's target high — the 48 dp the redesign asks, raised to the card's 56 — that opens the parent while the book
 * holds it. [onFirstLine] learns where the words stand and
 * the middle of their first line within them, in pixels.
 */
@Composable
private fun Words(
    content: ParentContent,
    stated: StatedParent?,
    beet: Color,
    onOpen: () -> Unit,
    onFirstLine: (LayoutCoordinates, Float) -> Unit,
) {
    val area = Modifier
        .fillMaxWidth()
        .heightIn(min = CardTarget)
        .semantics { traversalIndex = LINE_AFTER }
    if (content == ParentContent.Loading) {
        // The place the line takes, kept while the book is asked for the parent, so that the title does not move down.
        Spacer(area)
        return
    }
    val held = content as? ParentContent.Held
    val untitled = stringResource(R.string.common_untitled_recipe)
    val title = (stated?.title ?: held?.title)?.ifBlank { untitled }
    val words = when {
        held != null -> stringResource(R.string.card_provenance, title ?: untitled)
        title != null -> stringResource(R.string.card_provenance_unavailable, title)
        else -> stringResource(R.string.card_provenance_unknown)
    }
    // Held, a chevron after the last word says the line leads somewhere; it is drawn, not read out.
    val shown = if (held != null) stringResource(R.string.card_provenance_open, title ?: untitled) else words
    val author = stated?.nickname?.takeIf { held == null && it.isNotBlank() }
    Box(
        area
            .then(
                if (held != null) {
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.card_parent_open_action),
                        role = Role.Button,
                        onClick = onOpen,
                    )
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .testTag(CardTags.PROVENANCE),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.padding(start = WORDS_START)) {
            val laid = remember { LaidWords() }
            Box(Modifier.semantics { text = AnnotatedString(words) }) {
                Box(Modifier.clearAndSetSemantics {}) {
                    Text(
                        shown,
                        color = beet,
                        style = MaterialTheme.typography.titleSmall,
                        onTextLayout = { laid.result = it },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            laid.result?.let { onFirstLine(coordinates, (it.getLineTop(0) + it.getLineBottom(0)) / 2) }
                        },
                    )
                }
            }
            author?.let {
                // Read with a comma where it shows a "·".
                val spoken = stringResource(R.string.card_provenance_author_spoken, it)
                Text(
                    stringResource(R.string.card_provenance_author, it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = AUTHOR_GAP)
                        .semantics { contentDescription = spoken }
                        .testTag(ProvenanceTags.AUTHOR),
                )
            }
        }
    }
}

/** The words as they were last laid out, to find the middle of their first line once they are placed. */
private class LaidWords {
    var result: TextLayoutResult? = null
}

/**
 * What the provenance column draws, in dp from its top start corner: the parent's hollow node centred at [parent] —
 * [dashed] when the book does not hold the parent — a line of [stroke] down from it that turns with one knee of radius
 * [knee] towards this recipe's filled node centred at [own].
 */
internal data class ProvenanceDrawing(
    val parent: DpOffset,
    val parentRadius: Dp,
    val dashed: Boolean,
    val own: DpOffset,
    val ownRadius: Dp,
    val stroke: Dp,
    val knee: Dp,
) {
    companion object {
        /** The drawing whose parent node stands level with [parentY] and own node level with [ownY]. */
        fun of(parentY: Dp, ownY: Dp, held: Boolean) = ProvenanceDrawing(
            parent = DpOffset(PARENT_X, parentY),
            parentRadius = PARENT_RADIUS,
            dashed = !held,
            own = DpOffset(OWN_X, ownY),
            ownRadius = OWN_RADIUS,
            stroke = STROKE,
            knee = KNEE,
        )
    }
}

private fun DrawScope.draw(drawing: ProvenanceDrawing, colour: Color) {
    val stroke = drawing.stroke.toPx()
    val parent = Offset(drawing.parent.x.toPx(), drawing.parent.y.toPx())
    val own = Offset(drawing.own.x.toPx(), drawing.own.y.toPx())
    val parentRadius = drawing.parentRadius.toPx()
    val knee = drawing.knee.toPx()
    val dash = DASH.toPx()
    drawCircle(
        colour,
        radius = parentRadius,
        center = parent,
        style = Stroke(stroke, pathEffect = if (drawing.dashed) PathEffect.dashPathEffect(floatArrayOf(dash, dash)) else null),
    )
    val line = Path().apply {
        moveTo(parent.x, parent.y + parentRadius)
        lineTo(parent.x, (own.y - knee).coerceAtLeast(parent.y + parentRadius))
        arcTo(Rect(parent.x, own.y - 2 * knee, parent.x + 2 * knee, own.y), 180f, -90f, false)
        lineTo(own.x, own.y)
    }
    drawPath(line, colour, style = Stroke(stroke))
    drawCircle(colour, radius = drawing.ownRadius.toPx(), center = own)
}

/** What the provenance column draws, kept in the semantics tree for tests, never read aloud. */
internal val ProvenanceDrawn = SemanticsPropertyKey<ProvenanceDrawing>(PROVENANCE_DRAWN_KEY)
private const val PROVENANCE_DRAWN_KEY = "card.provenance-drawn"
private var SemanticsPropertyReceiver.provenanceDrawing by ProvenanceDrawn

object ProvenanceTags {
    const val COLUMN = "card-provenance-column"
    const val AUTHOR = "card-provenance-author"
}

/** The column the nodes and the knee are drawn in, and where the words and the title start beside it. */
internal val PROVENANCE_COLUMN = 26.dp
private val WORDS_START = PROVENANCE_COLUMN + 8.dp

/** The nodes and the line, as the redesign draws them (§ 5 «Происхождение»). */
private val PARENT_X = 7.dp
private val PARENT_RADIUS = 4.8.dp
private val OWN_X = 20.5.dp
private val OWN_RADIUS = 5.dp
private val STROKE = 1.6.dp
private val KNEE = 6.dp
private val DASH = 2.5.dp

/** The stated author under the words. */
private val AUTHOR_GAP = 2.dp

/** Within the whole, the title is read before the line above it. */
private const val TITLE_FIRST = 0f
private const val LINE_AFTER = 1f

