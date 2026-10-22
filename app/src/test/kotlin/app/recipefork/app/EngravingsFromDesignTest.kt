package app.recipefork.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The engravings of the empty states (v0.42, T-306) are the design's drawings, flattened, not redrawn:
 * - the engraved mark's ink and beet layers are the two paths of symbol `m2bb` in `docs/brand/identity/mark-2.html`, and
 *   its cuts the hairlines of symbol `m2be`, each with the symbol's group transform ([Mark2b]) applied to every point,
 *   in the 48-unit box, drawn at 112 dp;
 * - the engraved cart is symbol `il-cart` of `docs/brand/identity/redesign-1-recipe-fork.html`, in its 56-unit box,
 *   drawn at 96 dp, its paths as given and each wheel's circle as two half-circle arcs.
 */
@RunWith(AndroidJUnit4::class)
class EngravingsFromDesignTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mark = File("../docs/brand/identity/mark-2.html").readText()
    private val redesign = File("../docs/brand/identity/redesign-1-recipe-fork.html").readText()

    private fun symbol(html: String, id: String): String =
        Regex("""<symbol id="$id"[^>]*>(.*?)</symbol>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: throw AssertionError("no symbol $id")

    private fun attributes(tag: String): Map<String, String> =
        Regex("""([\w-]+)="([^"]*)"""").findAll(tag).associate { it.groupValues[1] to it.groupValues[2] }

    /** The `<path>` elements of [body], each its attributes. */
    private fun paths(body: String): List<Map<String, String>> =
        Regex("""<path ([^>]*)/>""").findAll(body).map { attributes(it.groupValues[1]) }.toList()

    /** [xml]'s path data is [design]'s with every point moved by [place], within a thousandth. */
    private fun assertSamePath(what: String, design: String, xml: String, place: (Double, Double) -> Pair<Double, Double>) {
        val expected = pathTokens(design)
        val actual = pathTokens(xml)
        assertEquals("$what: tokens", expected.size, actual.size)
        var i = 0
        while (i < expected.size) {
            if (expected[i][0].isLetter()) {
                assertEquals("$what: command $i", expected[i], actual[i])
                i++
                continue
            }
            val (x, y) = place(expected[i].toDouble(), expected[i + 1].toDouble())
            assertEquals("$what: number $i", x, actual[i].toDouble(), 0.001)
            assertEquals("$what: number ${i + 1}", y, actual[i + 1].toDouble(), 0.001)
            i += 2
        }
    }

    private fun assertDrawnAt(drawable: Int, side: Float, box: Float) {
        val vector = readVector(context, drawable)
        assertEquals(side to side, vector.widthDp to vector.heightDp)
        assertEquals(box to box, vector.viewportWidth to vector.viewportHeight)
    }

    @Test
    fun theMarksInkAndBeetAreTheDesignsMarkFlattened() {
        val design = paths(symbol(mark, "m2bb"))
        assertEquals(2, design.size)
        for ((drawable, path) in listOf(R.drawable.engraving_mark_ink to design[0], R.drawable.engraving_mark_beet to design[1])) {
            val name = context.resources.getResourceEntryName(drawable)
            assertDrawnAt(drawable, 112f, 48f)
            val xml = readVector(context, drawable).paths.single()
            assertSamePath(name, path.getValue("d"), xml.pathData, Mark2b::inBox)
            assertEquals("$name: stroke width", path.getValue("stroke-width").toDouble() * Mark2b.SCALE, xml.strokeWidth.toDouble(), 0.0001)
            assertTrue("$name is filled", xml.fillColor != null)
        }
    }

    @Test
    fun theMarksCutsAreTheDesignsHairlinesFlattened() {
        val body = symbol(mark, "m2be")
        val group = Regex("""<g ([^>]*)>(.*)</g>""", RegexOption.DOT_MATCHES_ALL).find(body) ?: throw AssertionError("m2be has no cuts")
        val transform = attributes(group.groupValues[1])
        assertEquals("translate(21.9 25.5) rotate(45) scale(.94)", transform.getValue("transform"))
        val design = paths(group.groupValues[2])
        val xml = readVector(context, R.drawable.engraving_mark_cuts)
        assertDrawnAt(R.drawable.engraving_mark_cuts, 112f, 48f)
        assertEquals("the cuts", design.size, xml.paths.size)
        for ((index, pair) in design.zip(xml.paths).withIndex()) {
            assertSamePath("cut $index", pair.first.getValue("d"), pair.second.pathData, Mark2b::inBox)
            val width = transform.getValue("stroke-width").toDouble() * Mark2b.SCALE
            assertEquals("cut $index: stroke width", width, pair.second.strokeWidth.toDouble(), 0.0001)
            assertEquals("cut $index is a line, not a fill", null, pair.second.fillColor)
        }
    }

    @Test
    fun theCartIsTheDesignsCart() {
        val body = symbol(redesign, "il-cart")
        val group = Regex("""<g ([^>]*)>(.*)</g>""", RegexOption.DOT_MATCHES_ALL).find(body) ?: throw AssertionError("il-cart has no group")
        val width = attributes(group.groupValues[1]).getValue("stroke-width").toDouble()
        assertEquals("""<symbol id="il-cart" viewBox="0 0 56 56">""", Regex("""<symbol id="il-cart"[^>]*>""").find(redesign)!!.value)
        assertDrawnAt(R.drawable.engraving_cart, 96f, 56f)
        val xml = readVector(context, R.drawable.engraving_cart).paths
        // The design's paths, then its circles in place, each as two arcs.
        val design = Regex("""<(path|circle) ([^>]*)/>""").findAll(group.groupValues[2])
            .map { it.groupValues[1] to attributes(it.groupValues[2]) }.toList()
        assertEquals("the cart's lines", design.size, xml.size)
        for ((index, pair) in design.zip(xml).withIndex()) {
            val (element, at) = pair.first
            val drawn = pair.second
            assertEquals("line $index: stroke width", at["stroke-width"]?.toDouble() ?: width, drawn.strokeWidth.toDouble(), 0.0001)
            assertEquals("line $index is a line, not a fill", null, drawn.fillColor)
            if (element == "path") {
                assertEquals("line $index", pathTokens(at.getValue("d")), pathTokens(drawn.pathData))
            } else {
                val cx = at.getValue("cx").toDouble()
                val cy = at.getValue("cy").toDouble()
                val r = at.getValue("r").toDouble()
                val arcs = listOf("M", cx - r, cy, "A", r, r, 0, 1, 0, cx + r, cy, "A", r, r, 0, 1, 0, cx - r, cy)
                val tokens = pathTokens(drawn.pathData)
                assertEquals("wheel $index", arcs.size, tokens.size)
                for ((t, want) in arcs.withIndex()) {
                    if (want is String) assertEquals("wheel $index: token $t", want, tokens[t])
                    else assertTrue("wheel $index: token $t", abs((want as Number).toDouble() - tokens[t].toDouble()) < 0.0001)
                }
            }
        }
    }
}
