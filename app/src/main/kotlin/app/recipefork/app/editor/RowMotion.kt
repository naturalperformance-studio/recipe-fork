package app.recipefork.app.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import app.recipefork.app.ui.Motion
import kotlin.math.roundToInt

/**
 * The motion of a list of editor rows — ingredient rows or steps. A row added, inserted or put back by Undo unfolds
 * into its place and fades in; a row removed leaves its picture behind, which folds up and fades out, so the rows after
 * it close up gradually. Rows on screen when the list is first shown, and rows moved, do not animate. A row unfolding
 * is composed whole from the start: it takes the focus and the keyboard as at once.
 */
@Stable
internal class RowMotion(private val graphics: GraphicsContext) {
    /** A removed row's picture where the row stood: before the row now at [index]. */
    class Leaving(val key: Any, val index: Int, val picture: GraphicsLayer, val height: Int)

    private var known: List<Any>? = null
    private val entering = HashSet<Any>()
    private val pictures = HashMap<Any, GraphicsLayer>()
    private val heights = HashMap<Any, Int>()
    private val leaving = mutableStateListOf<Leaving>()

    /** Called as the list is composed, with its rows' [keys] in order. */
    fun update(keys: List<Any>) {
        val before = known
        known = keys
        if (before == null || before == keys) return
        val now = keys.toHashSet()
        // A row put back while its picture still folds comes back as itself.
        leaving.filter { it.key in now }.forEach(::finish)
        val was = before.toHashSet()
        before.forEachIndexed { at, key ->
            if (key in now) return@forEachIndexed
            val picture = pictures.remove(key) ?: return@forEachIndexed
            leaving += Leaving(key, before.take(at).count { it in now }, picture, heights.remove(key) ?: 0)
        }
        keys.filterTo(entering) { it !in was }
    }

    private fun finish(gone: Leaving) {
        if (leaving.remove(gone)) graphics.releaseGraphicsLayer(gone.picture)
    }

    /** What the row of [key] wears: its unfolding where it is new, and the picture it leaves behind once removed. */
    @Composable
    fun row(key: Any): Modifier {
        val picture = remember(key) { pictures.getOrPut(key) { graphics.createGraphicsLayer() } }
        val shown = remember(key) { Animatable(if (entering.remove(key)) 0f else 1f) }
        LaunchedEffect(shown) { shown.animateTo(1f, Motion.appearing()) }
        return Modifier
            .graphicsLayer {
                alpha = shown.value
                clip = shown.value < 1f
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                heights[key] = placeable.height
                layout(placeable.width, (placeable.height * shown.value).roundToInt()) { placeable.place(0, 0) }
            }
            .drawWithContent {
                picture.record { this@drawWithContent.drawContent() }
                drawLayer(picture)
            }
    }

    /** The pictures of rows removed from before the row now at [index] — past the last row where [index] is the count. */
    @Composable
    fun LeavingAt(index: Int, last: Boolean = false) {
        leaving.filter { if (last) it.index >= index else it.index == index }.forEach { gone -> key(gone) { Folding(gone) } }
    }

    @Composable
    private fun Folding(gone: Leaving) {
        val shown = remember(gone) { Animatable(1f) }
        LaunchedEffect(gone) {
            shown.animateTo(0f, Motion.going())
            finish(gone)
        }
        Layout(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = shown.value
                    clip = true
                }
                .drawBehind { drawLayer(gone.picture) },
        ) { _, constraints -> layout(constraints.maxWidth, (gone.height * shown.value).roundToInt()) {} }
    }

    fun release() {
        pictures.values.forEach(graphics::releaseGraphicsLayer)
        pictures.clear()
        leaving.toList().forEach(::finish)
    }
}

/** A [RowMotion] for one list, held while the list is shown. */
@Composable
internal fun rememberRowMotion(): RowMotion {
    val graphics = LocalGraphicsContext.current
    val motion = remember(graphics) { RowMotion(graphics) }
    DisposableEffect(motion) { onDispose { motion.release() } }
    return motion
}

/**
 * A column of [content] with [gap] between its children — except that a child shorter than the gap, a row unfolding
 * or folding away, takes only as much gap before it as it is high, so the column grows and shrinks with it without a
 * jump. Children start at its start edge, as in a [androidx.compose.foundation.layout.Column].
 */
@Composable
internal fun RowsColumn(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)) }
        val space = gap.roundToPx()
        val tops = IntArray(placeables.size)
        var y = 0
        placeables.forEachIndexed { i, placeable ->
            if (i > 0) y += minOf(space, placeable.height)
            tops[i] = y
            y += placeable.height
        }
        val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
        layout(width, constraints.constrainHeight(y)) { placeables.forEachIndexed { i, it -> it.placeRelative(0, tops[i]) } }
    }
}
