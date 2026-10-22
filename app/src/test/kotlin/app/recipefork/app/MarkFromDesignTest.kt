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
 * The icons' paths are the design's own: each path of the launcher icon's foreground and monochrome layers is the
 * matching path of symbol `m2bb` in `docs/brand/identity/mark-2.html`, and each path of the notification's small icon
 * the matching path of `m2bbs` (the thick drawing for 16–20 px), with the symbol's group transform and the icon's
 * placement ([Mark2b]) applied to every point — flattened, not redrawn.
 */
@RunWith(AndroidJUnit4::class)
class MarkFromDesignTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val design = File("../docs/brand/identity/mark-2.html").readText()

    private class DesignPath(val data: String, val strokeWidth: Double)

    private fun symbol(id: String): List<DesignPath> {
        val body = Regex("""<symbol id="$id"[^>]*>(.*?)</symbol>""").find(design)?.groupValues?.get(1)
            ?: throw AssertionError("mark-2.html has no symbol $id")
        val group = Regex("""<g transform="translate\(([-\d.]+) ([-\d.]+)\) rotate\(([-\d.]+)\) scale\(([-\d.]+)\)">""").find(body)
            ?: throw AssertionError("$id has no group transform")
        assertEquals(
            "$id's group transform",
            listOf(Mark2b.TRANSLATE_X, Mark2b.TRANSLATE_Y, Mark2b.ROTATE, Mark2b.SCALE),
            group.groupValues.drop(1).map { it.toDouble() },
        )
        return Regex("""<path ([^>]*)/>""").findAll(body).map { path ->
            val attributes = Regex("""([\w-]+)="([^"]*)"""").findAll(path.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            DesignPath(attributes.getValue("d"), if ("stroke" in attributes) attributes["stroke-width"]?.toDouble() ?: 1.0 else 0.0)
        }.toList()
    }

    private fun assertFlattened(
        drawable: Int,
        symbol: String,
        place: (Pair<Double, Double>) -> Pair<Double, Double>,
        scale: Double,
    ) {
        val name = context.resources.getResourceEntryName(drawable)
        val expected = symbol(symbol)
        val actual = readVector(context, drawable).paths
        assertEquals("$name's paths", expected.size, actual.size)
        for ((index, pair) in expected.zip(actual).withIndex()) {
            val (designPath, xmlPath) = pair
            val designTokens = pathTokens(designPath.data)
            val xmlTokens = pathTokens(xmlPath.pathData)
            assertEquals("$name path $index: tokens", designTokens.size, xmlTokens.size)
            var i = 0
            while (i < designTokens.size) {
                val token = designTokens[i]
                if (token[0].isLetter()) {
                    assertTrue("$name path $index: '$token' is not an absolute command", token in setOf("M", "L", "Q", "C", "Z"))
                    assertEquals("$name path $index: command $i", token, xmlTokens[i])
                    i++
                    continue
                }
                val (x, y) = place(Mark2b.inBox(token.toDouble(), designTokens[i + 1].toDouble()))
                for ((offset, value) in listOf(0 to x, 1 to y)) {
                    val got = xmlTokens[i + offset].toDouble()
                    assertTrue("$name path $index: number ${i + offset} is $got, not $value", abs(got - value) <= 0.001)
                }
                i += 2
            }
            assertEquals("$name path $index: stroke width", designPath.strokeWidth * Mark2b.SCALE * scale, xmlPath.strokeWidth.toDouble(), 0.0001)
        }
    }

    @Test
    fun theLauncherIconsLayersAreTheDesignsMark() {
        val onCanvas = { p: Pair<Double, Double> ->
            (p.first * Mark2b.LAUNCHER_SCALE + Mark2b.LAUNCHER_OFFSET) to (p.second * Mark2b.LAUNCHER_SCALE + Mark2b.LAUNCHER_OFFSET)
        }
        assertFlattened(R.drawable.ic_launcher_foreground, "m2bb", onCanvas, Mark2b.LAUNCHER_SCALE)
        assertFlattened(R.drawable.ic_launcher_monochrome, "m2bb", onCanvas, Mark2b.LAUNCHER_SCALE)
    }

    @Test
    fun theNotificationIconIsTheDesignsThickDrawing() {
        val vector = readVector(context, R.drawable.ic_timer_notification)
        assertEquals("the viewport is the symbol's box", 48f to 48f, vector.viewportWidth to vector.viewportHeight)
        assertFlattened(R.drawable.ic_timer_notification, "m2bbs", { it }, 1.0)
    }
}
