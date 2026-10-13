package app.recipefork.app.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * How far a view's header — its search field and its filters — has scrolled away above its list (v0.38): it goes as the
 * list goes down and comes back as soon as the list goes up, wherever the list stands, as the book's top bar does
 * (Material's "enter always"). Put on the view's page as a [connection] for nested scrolling, around the list; the
 * header itself is drawn by [ScrollAwayHeader].
 */
@Stable
internal class HeaderScroll(initial: Float = 0f) {
    /** How far the header stands above its place, in pixels: 0 when shown whole, down to minus its height when gone. */
    var offset by mutableFloatStateOf(initial)
        private set

    /** The header's whole height, as last laid out. */
    internal var height = 0
        set(value) {
            field = value
            if (offset < -value) offset = -value.toFloat()
        }

    /** Shows the header whole again: where no list is shown, nothing could scroll it back. */
    fun reveal() {
        offset = 0f
    }

    val connection = object : NestedScrollConnection {
        // Before the list moves, the header takes the scroll — going while the list goes down, coming back while it goes
        // up — so that what is under the finger keeps with it.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val before = offset
            offset = (offset + available.y).coerceIn(-height.toFloat(), 0f)
            return Offset(0f, offset - before)
        }
    }
}

/**
 * A header's scroll, kept as its list's place is — across a card or the editor opened above the book, a configuration
 * change and the end of the process — so that the list comes back showing what it showed.
 */
@Composable
internal fun rememberHeaderScroll(): HeaderScroll =
    rememberSaveable(saver = Saver(save = { it.offset }, restore = { HeaderScroll(it) })) { HeaderScroll() }

/**
 * Lays out [content] — a view's header — as far as [scroll] leaves it shown: it is measured whole, and takes as much
 * height as stands below its scrolled-away part, so that the list beneath moves up with it.
 */
@Composable
internal fun ScrollAwayHeader(scroll: HeaderScroll, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.clipToBounds().testTag(LibraryTags.HEADER_AREA)) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val whole = placeables.sumOf { it.height }
        scroll.height = whole
        val offset = scroll.offset.roundToInt().coerceIn(-whole, 0)
        layout(constraints.maxWidth, whole + offset) {
            var y = offset
            placeables.forEach {
                it.place(0, y)
                y += it.height
            }
        }
    }
}
