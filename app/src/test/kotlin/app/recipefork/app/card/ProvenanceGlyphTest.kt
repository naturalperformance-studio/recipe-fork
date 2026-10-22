package app.recipefork.app.card

import android.content.Context
import android.graphics.RectF
import androidx.core.graphics.PathParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * The provenance glyph (scenario 4 of v0.41): a 24 dp vector of the redesign's «Происхождение» glyph — a hollow node,
 * a line with one knee, a filled node — in one colour, so it tints with its container's content colour. It replaces
 * the old fork icon, which is gone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProvenanceGlyphTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class Part(val name: String?, val data: String, val fill: String?, val stroke: String?, val strokeWidth: Float, val cap: String?)

    private fun parts(): Pair<Map<String, String?>, List<Part>> {
        val vector = mutableMapOf<String, String?>()
        val parts = mutableListOf<Part>()
        context.resources.getXml(R.drawable.ic_provenance).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "vector" -> listOf("width", "height", "viewportWidth", "viewportHeight").forEach {
                        vector[it] = parser.getAttributeValue(ANDROID, it)
                    }
                    "path" -> parts += Part(
                        parser.getAttributeValue(ANDROID, "name"),
                        parser.getAttributeValue(ANDROID, "pathData"),
                        parser.getAttributeValue(ANDROID, "fillColor"),
                        parser.getAttributeValue(ANDROID, "strokeColor"),
                        parser.getAttributeFloatValue(ANDROID, "strokeWidth", 0f),
                        parser.getAttributeValue(ANDROID, "strokeLineCap"),
                    )
                    "group", "clip-path" -> error("the glyph is flat paths only, found <${parser.name}>")
                }
            }
        }
        return vector to parts
    }

    /** The bounds of the points the path passes through (not of its curves' control points). */
    private fun bounds(data: String): RectF {
        val points = PathParser.createPathFromPathData(data).approximate(0.001f)
        val xs = points.indices.filter { it % 3 == 1 }.map { points[it] }
        val ys = points.indices.filter { it % 3 == 2 }.map { points[it] }
        return RectF(xs.min(), ys.min(), xs.max(), ys.max())
    }

    private fun assertBounds(what: String, expected: RectF, actual: RectF) {
        for ((side, e, a) in listOf(
            Triple("left", expected.left, actual.left), Triple("top", expected.top, actual.top),
            Triple("right", expected.right, actual.right), Triple("bottom", expected.bottom, actual.bottom),
        )) assertEquals("$what $side of $actual", e, a, TOLERANCE)
    }

    @Test
    fun theGlyphIsA24DpVector() {
        val (vector, _) = parts()
        for (side in listOf("width", "height")) {
            assertTrue("$side is ${vector[side]}", vector[side]?.let { it.startsWith("24") && it.endsWith("dip") || it == "24dp" } == true)
        }
        for (side in listOf("viewportWidth", "viewportHeight")) assertEquals(side, 24f, vector[side]!!.toFloat(), 0f)
        val density = context.resources.displayMetrics.density
        val drawable = context.getDrawable(R.drawable.ic_provenance)!!
        assertEquals((24 * density).toInt(), drawable.intrinsicWidth)
        assertEquals((24 * density).toInt(), drawable.intrinsicHeight)
    }

    // The design's g-prov symbol: hollow node r 3 at (6, 5), stroke 1.8; the line from (6, 8) down, one 4.5 knee, on to
    // (14, 19), stroke 1.8 with round caps; filled node r 3.4 at (18, 19).
    @Test
    fun theGlyphIsAHollowNodeALineWithOneKneeAndAFilledNode() {
        val (_, parts) = parts()
        assertEquals(listOf("parent", "line", "recipe"), parts.map { it.name })
        val (parent, line, recipe) = parts

        assertNull("the parent's node is hollow", parent.fill)
        assertEquals(1.8f, parent.strokeWidth, 0f)
        assertBounds("parent", RectF(3f, 2f, 9f, 8f), bounds(parent.data))

        assertNull("the line is a line", line.fill)
        assertEquals(1.8f, line.strokeWidth, 0f)
        assertTrue("the line's caps are ${line.cap}", line.cap == "round" || line.cap == "1")
        assertBounds("line", RectF(6f, 8f, 14f, 19f), bounds(line.data))
        // One knee: a vertical run, one arc, a horizontal run.
        val commands = line.data.filter { it.isLetter() }
        assertEquals("MVAH", commands)

        assertNull("this recipe's node is filled, not stroked", recipe.stroke)
        assertBounds("recipe", RectF(14.6f, 15.6f, 21.4f, 22.4f), bounds(recipe.data))
    }

    @Test
    fun theGlyphHasOneColourSoItTints() {
        val (_, parts) = parts()
        val colours = parts.flatMap { listOfNotNull(it.fill, it.stroke) }.map { it.lowercase() }.toSet()
        assertEquals("colours $colours", 1, colours.size)
    }

    @Test
    fun theOldForkIconIsGoneAndNamedNowhere() {
        assertEquals(0, context.resources.getIdentifier("ic_fork", "drawable", context.packageName))
        assertTrue(R.drawable::class.java.fields.none { it.name == "ic_fork" })
        // Unit tests of `:app` run with the module directory as the working directory.
        val sources = File("src/main").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }
        val naming = sources.filter { file -> FORK_ICON.containsMatchIn(file.readText()) }.map { it.path }.toList()
        assertEquals(emptyList<String>(), naming)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        // The platform draws an arc as cubic curves, a hundredth of a unit off the circle at most.
        const val TOLERANCE = 0.02f
        val FORK_ICON = Regex("""\bic_fork\b""")
    }
}
