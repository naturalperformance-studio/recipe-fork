package app.recipefork.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A11Y-002 measured on a rendered screen, as Play's pre-launch report measures it: every text drawn, in each window, is
 * at 4.5:1 or better against what is drawn behind it — 3:1 for large text (24 dp, or 18.7 dp bold, as laid out) — by
 * WCAG 2.2's formula. Needs Robolectric's native graphics (`@GraphicsMode(NATIVE)`), as it reads the pixels.
 *
 * The text's colour is the one it is laid out in — and each coloured span's — laid over what is behind it where it is
 * not opaque; what is behind it is the colour most of the text's own box is drawn in, its glyphs aside. Text of a
 * disabled control is exempt, as A11Y-002 exempts it; so is text nothing of which is on screen, and text a snackbar
 * is drawn over.
 *
 * The colour a text is laid out in is read from its last layout; a theme changed under a composition already laid out
 * may be drawn without a new layout, so each theme is measured on a screen composed in it.
 */
object TextContrastChecks {
    private const val TEXT = 4.5
    private const val LARGE_TEXT = 3.0
    private const val NOT_MEASURED = "not measured"

    fun assertTextContrast(screen: SemanticsNodeInteractionsProvider, expectAtLeast: Int = 1) {
        val failures = mutableListOf<String>()
        var measured = 0
        val roots = screen.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val texts = screen.onAllNodes(hasTextLayout, useUnmergedTree = true).fetchSemanticsNodes()
        // A snackbar is drawn over the screen: what lies beneath it is not what is read there.
        val snackbars = screen.onAllNodes(AccessibilityChecks.snackbarPane, useUnmergedTree = true).fetchSemanticsNodes()
        fun beneathASnackbar(node: SemanticsNode) = snackbars.any { pane ->
            rootOf(pane).id == rootOf(node).id && pane.boundsInRoot.overlaps(node.boundsInRoot) &&
                generateSequence(node) { it.parent }.none { it.id == pane.id }
        }
        roots.forEach { root ->
            val inRoot = texts.filter { rootOf(it).id == root.id && !disabled(it) && !beneathASnackbar(it) }
            if (inRoot.isEmpty()) return@forEach
            val image = drawn(root)
            inRoot.forEach { node ->
                when (val found = failure(node, image)) {
                    NOT_MEASURED -> Unit
                    null -> measured++
                    else -> { measured++; failures += found }
                }
            }
        }
        assertTrue("expected at least $expectAtLeast texts measured, measured $measured", measured >= expectAtLeast)
        if (failures.isNotEmpty()) fail("text below its contrast threshold: $failures")
    }

    /**
     * What the window of [root] draws: its view drawn whole into a bitmap. (`captureToImage` reads a popup's — a menu's —
     * and a dialog's root from the Activity's window, not their own.)
     */
    private fun drawn(root: SemanticsNode): Bitmap {
        val view = (root.root as ViewRootForTest).view
        val bitmap = Bitmap.createBitmap(max(1, view.width), max(1, view.height), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private val hasTextLayout = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)

    private fun rootOf(node: SemanticsNode): SemanticsNode = generateSequence(node) { it.parent }.last()

    private fun disabled(node: SemanticsNode): Boolean =
        generateSequence(node) { it.parent }.any { SemanticsProperties.Disabled in it.config }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? =
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
            mutableListOf<TextLayoutResult>().also { action(it) }.firstOrNull()
        }

    /** What fails in [node]'s text as drawn in [image], its window's view; null if nothing, [NOT_MEASURED] if not measured. */
    private fun failure(node: SemanticsNode, image: Bitmap): String? {
        val layout = layoutOf(node) ?: return NOT_MEASURED
        val text = layout.layoutInput.text
        if (text.isBlank()) return NOT_MEASURED
        val bounds = node.boundsInRoot
        val x0 = max(0, bounds.left.roundToInt())
        val y0 = max(0, bounds.top.roundToInt())
        val x1 = min(image.width, bounds.right.roundToInt())
        val y1 = min(image.height, bounds.bottom.roundToInt())
        if (x1 - x0 < 2 || y1 - y0 < 2) return NOT_MEASURED
        val colours = (listOf(layout.layoutInput.style.color) + text.spanStyles.map { it.item.color }).filter { it.isSpecified }
        if (colours.isEmpty()) return NOT_MEASURED
        val counts = HashMap<Int, Int>()
        for (y in y0 until y1) for (x in x0 until x1) counts.merge(image.getPixel(x, y), 1, Int::plus)
        val ranked = counts.entries.sortedByDescending { it.value }.map { Color(it.key) }
        val density = layout.layoutInput.density
        // A text given no size is drawn at the platform's 14 sp.
        val size = layout.layoutInput.style.fontSize.takeIf { it.isSp } ?: 14.sp
        val sizeDp = with(density) { size.toPx() } / density.density
        val bold = (layout.layoutInput.style.fontWeight ?: FontWeight.Normal) >= FontWeight.Bold
        val least = if (sizeDp >= 24f || (bold && sizeDp >= 18.66f)) LARGE_TEXT else TEXT
        return colours.firstNotNullOfOrNull { colour ->
            // Behind the text: the colour most of its box is drawn in, unless that is the text's own (a box all glyph).
            val background = ranked.firstOrNull { ratio(it, colour.compositeOver(it)) > 1.05 } ?: ranked.first()
            val foreground = colour.compositeOver(background)
            val ratio = ratio(foreground, background)
            if (ratio + 0.005 >= least) null
            else "\"${text.text.take(40)}\" (${AccessibilityChecks.nameOf(node)}): ${hex(foreground)} on ${hex(background)} " +
                "%.2f:1, less than %.1f:1".format(ratio, least)
        }
    }

    private fun hex(colour: Color) = "#%06X".format(colour.copy(alpha = 1f).let { (it.red * 255).roundToInt() shl 16 or ((it.green * 255).roundToInt() shl 8) or (it.blue * 255).roundToInt() })

    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** The relative luminance of an sRGB colour (WCAG 2.2), its alpha ignored. */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
