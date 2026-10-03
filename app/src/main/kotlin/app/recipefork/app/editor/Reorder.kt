package app.recipefork.app.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.recipefork.app.R
import kotlinx.coroutines.flow.first

/** How far from the editor's top or bottom edge a dragged row starts scrolling it. */
private val SCROLL_EDGE = 56.dp

/** How far the editor scrolls in one frame at most while a row is dragged against its edge. */
private val SCROLL_STEP = 16.dp

/** The editor's scrolling as a dragged row needs it: the scroll itself and where its viewport sits on the screen. */
@Stable
class EditorScroll(val state: ScrollState) {
    /** The scrolling viewport, laid out; set by the screen. */
    internal var viewport: LayoutCoordinates? = null
}

/**
 * The dragging of one list of rows — the steps or the ingredients (FLOW-025). Only the dragged row follows the finger,
 * and the rows it passes make room for it; the list itself is changed once, on drop, by one [onDrop] from the place
 * the row was picked up to the place it was dropped — the same move the row menu makes (A11Y-006).
 */
@Stable
class RowDrag internal constructor(
    private val scroll: EditorScroll,
    private val spacing: Float,
    private val edge: Float,
    private val step: Float,
    private val onDrop: (from: Int, to: Int) -> Unit,
) {
    /** The position of the row being dragged, or null. */
    var dragged by mutableStateOf<Int?>(null)
        private set

    /** How far the dragged row is from its place, scrolling included. */
    private var offset by mutableFloatStateOf(0f)

    /** Where the finger is on the screen, while it drags. */
    private var pointer by mutableStateOf<Float?>(null)

    /** Where the finger was on the screen when the drag began. */
    private var origin = 0f

    /** Where each row sits in its list, top to bottom, as last laid out at rest. */
    private val placed = mutableMapOf<Int, ClosedFloatingPointRange<Float>>()

    /** The places of the rows as they were when the drag began. */
    private var places: Map<Int, ClosedFloatingPointRange<Float>> = emptyMap()
    private var count = 0

    /** Where the dragged row would land now: past every row whose middle its leading edge has crossed. */
    private val target by derivedStateOf {
        val from = dragged ?: return@derivedStateOf -1
        val own = places[from] ?: return@derivedStateOf from
        fun middle(row: Int) = places[row]?.let { (it.start + it.endInclusive) / 2 }
        if (offset > 0) {
            val bottom = own.endInclusive + offset
            from + (from + 1 until count).count { row -> middle(row)?.let { it < bottom } == true }
        } else {
            val top = own.start + offset
            from - (0 until from).count { row -> middle(row)?.let { it > top } == true }
        }
    }

    /** How far the row at [position] is drawn from its place: the dragged row with the finger, the rows it passed aside. */
    private fun translationOf(position: Int): Float {
        val from = dragged ?: return 0f
        if (position == from) return offset
        val to = target
        val shift = (places[from]?.let { it.endInclusive - it.start } ?: 0f) + spacing
        return when (position) {
            in from + 1..to -> -shift
            in to until from -> shift
            else -> 0f
        }
    }

    /**
     * What the row at [position] wears: where it is laid out, and, while dragged, lifted above the others in [lifted],
     * its shadow and its lifted colour taking the row's [shape].
     */
    fun row(position: Int, lifted: Color, shape: Shape = RectangleShape): Modifier {
        val isDragged = dragged == position
        return Modifier
            .onGloballyPositioned { coordinates ->
                if (dragged == null) {
                    val top = coordinates.positionInParent().y
                    placed[position] = top..top + coordinates.size.height
                }
            }
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = translationOf(position)
                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                this.shape = shape
            }
            .then(if (isDragged) Modifier.background(lifted, shape) else Modifier)
    }

    internal fun start(position: Int, rows: Int) {
        places = placed.toMap()
        count = rows
        offset = 0f
        dragged = position
    }

    internal fun moveBy(distance: Float, pointerY: Float) {
        if (pointer == null) origin = pointerY - distance
        offset += distance
        pointer = pointerY
    }

    internal fun drop() {
        val from = dragged ?: return
        val to = target
        if (to in 0 until count && to != from) onDrop(from, to)
        end()
    }

    internal fun end() {
        dragged = null
        offset = 0f
        pointer = null
    }

    /**
     * How far to scroll in one frame: towards the edge the finger is near, faster the nearer it is — and only once the
     * finger has gone towards that edge, so a row picked up near an edge and dragged away from it does not scroll.
     */
    private fun edgeSpeed(): Float {
        val y = pointer ?: return 0f
        val view = scroll.viewport?.takeIf { it.isAttached }?.boundsInRoot() ?: return 0f
        return when {
            y < origin && y < view.top + edge -> -step * ((view.top + edge - y) / edge).coerceAtMost(1f)
            y > origin && y > view.bottom - edge -> step * ((y - (view.bottom - edge)) / edge).coerceAtMost(1f)
            else -> 0f
        }
    }

    /**
     * While a row is dragged against the editor's top or bottom edge, scrolls the editor a frame at a time and keeps
     * the row under the finger, so a row travels the whole recipe. Waits, without asking for frames, while the finger
     * is away from the edges or the editor can scroll no further.
     */
    internal suspend fun scrollAlong() {
        while (true) {
            val speed = snapshotFlow { edgeSpeed() }.first { it != 0f }
            withFrameNanos { }
            val consumed = scroll.state.scrollBy(speed)
            offset += consumed
            if (consumed == 0f) {
                val at = pointer
                snapshotFlow { pointer }.first { it != at }
            }
        }
    }
}

/** A [RowDrag] for one list, held for the screen; [onDrop] makes the move. */
@Composable
internal fun rememberRowDrag(scroll: EditorScroll, spacing: Dp, onDrop: (from: Int, to: Int) -> Unit): RowDrag {
    val density = LocalDensity.current
    val drop by rememberUpdatedState(onDrop)
    val drag = remember(scroll, density) {
        with(density) { RowDrag(scroll, spacing.toPx(), SCROLL_EDGE.toPx(), SCROLL_STEP.toPx()) { from, to -> drop(from, to) } }
    }
    val dragging = drag.dragged != null
    LaunchedEffect(drag, dragging) { if (dragging) drag.scrollAlong() }
    return drag
}

/** How wide the drag column at a block's edge is: a 48 dp touch target and no wider (A11Y-003). */
internal val DRAG_COLUMN_WIDTH = 48.dp

/** Whether the drag column stands at a block's right edge, for a left hand, instead of its left; the left by default. */
internal val LocalDragColumnOnRight = staticCompositionLocalOf { false }

/**
 * A row's block with the column it is dragged by down its left edge — or its right edge, where [LocalDragColumnOnRight]
 * says so — the full height of the block, and [content] beside it (FLOW-025). Without a [drag] — a frozen recipe —
 * the block is [content] alone, with no column.
 */
@Composable
internal fun DragColumnBlock(
    drag: RowDrag?,
    position: Int,
    count: Int,
    tag: String,
    iconTag: String,
    content: @Composable () -> Unit,
) {
    if (drag == null) {
        content()
        return
    }
    val onRight = LocalDragColumnOnRight.current
    Layout(
        contents = listOf({ DragColumn(drag, position, count, tag, iconTag) }, content),
    ) { (column, body), constraints ->
        val width = DRAG_COLUMN_WIDTH.roundToPx().coerceAtMost(constraints.maxWidth)
        val rest = (constraints.maxWidth - width).coerceAtLeast(0)
        val placedBody = body.map {
            it.measure(constraints.copy(minWidth = (constraints.minWidth - width).coerceAtLeast(0), maxWidth = rest, minHeight = 0))
        }
        val height = maxOf(placedBody.maxOfOrNull { it.height } ?: 0, DRAG_COLUMN_WIDTH.roundToPx(), constraints.minHeight)
            .let { if (constraints.hasBoundedHeight) it.coerceAtMost(constraints.maxHeight) else it }
        val placedColumn = column.map { it.measure(Constraints.fixed(width, height)) }
        val bodyWidth = placedBody.maxOfOrNull { it.width } ?: 0
        layout(width + bodyWidth, height) {
            // The content keeps one left edge either way: beside the column, or at the block's own left edge. On the
            // right means the physical right, whatever the layout direction; by default the column takes the start.
            if (onRight) {
                placedColumn.forEach { it.place(bodyWidth, 0) }
                placedBody.forEach { it.place(0, 0) }
            } else {
                placedColumn.forEach { it.placeRelative(0, 0) }
                placedBody.forEach { it.placeRelative(width, 0) }
            }
        }
    }
}

/**
 * The column the row at [position] of [count] is dragged by: lightly tinted so the area to drag is seen, and marked by
 * the handle icon at its centre, so it is not told by colour alone. A drag started anywhere on it moves the row; a
 * touch anywhere else in the block never does. The screen reader does not see it: the row menu's "Move up" and
 * "Move down" do what it does (A11Y-006).
 */
@Composable
private fun DragColumn(drag: RowDrag, position: Int, count: Int, tag: String, iconTag: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics {
                testTag = tag
                hideFromAccessibility()
            }
            .rowDragGesture(drag, position, count),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_drag_handle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                testTag = iconTag
                hideFromAccessibility()
            },
        )
    }
}

/**
 * Picks the row at [position] of [count] up once the finger has gone past the touch slop vertically, moves it with the
 * finger and drops it where the finger lifts.
 */
private fun Modifier.rowDragGesture(drag: RowDrag, position: Int, count: Int): Modifier = composed {
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun onScreen(local: Offset): Float = placed[0]?.takeIf { it.isAttached }?.localToRoot(local)?.y ?: 0f
    onGloballyPositioned { placed[0] = it }
        .pointerInput(drag, position, count) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    ?: return@awaitEachGesture
                drag.start(position, count)
                drag.moveBy(slop.position.y - down.position.y, onScreen(slop.position))
                val dropped = try {
                    verticalDrag(slop.id) { change ->
                        drag.moveBy(change.positionChange().y, onScreen(change.position))
                        change.consume()
                    }
                } catch (e: Throwable) {
                    drag.end()
                    throw e
                }
                if (dropped) drag.drop() else drag.end()
            }
        }
}
