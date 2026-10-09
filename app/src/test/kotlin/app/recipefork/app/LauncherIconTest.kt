package app.recipefork.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.VectorDrawable
import androidx.core.graphics.PathParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The application's launcher icon: the manifest names an adaptive icon (and a round one) whose foreground, background
 * and monochrome (themed icon, Android 13+) layers are all there, and whose picture stays inside the 66 dp safe zone
 * of the 108 dp canvas, so no launcher mask cuts it.
 *
 * It also renders the icon for Google Play: [rendersThePlayStoreIcon] writes `app/build/play/icon-512.png`, a 512×512
 * opaque full-bleed square (Play rounds its corners itself) of the background and foreground layers, the canvas's
 * middle 72 dp — what a launcher shows — filling it. Reproduce it with
 * `./gradlew :app:testDebugUnitTest --tests app.recipefork.app.LauncherIconTest`; `./gradlew testAll` writes it too.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theManifestNamesTheAdaptiveIconAndTheRoundOne() {
        val application = mergedManifestApplication()
        for ((attribute, expected) in listOf("icon" to R.mipmap.ic_launcher, "roundIcon" to R.mipmap.ic_launcher_round)) {
            val reference = application.getAttributeNS(ANDROID, attribute)
            assertTrue("android:$attribute is '$reference', not a mipmap", reference.startsWith("@mipmap/"))
            val id = context.resources.getIdentifier(reference.removePrefix("@mipmap/"), "mipmap", context.packageName)
            assertEquals("android:$attribute", expected, id)

            val icon = context.getDrawable(id)
            assertTrue("android:$attribute resolves to ${icon?.javaClass}", icon is AdaptiveIconDrawable)
            icon as AdaptiveIconDrawable
            assertTrue("$attribute: background is ${icon.background?.javaClass}", icon.background is ColorDrawable)
            assertTrue("$attribute: foreground is ${icon.foreground?.javaClass}", icon.foreground is VectorDrawable)
            assertNotNull("$attribute: no monochrome layer", icon.monochrome)
        }
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        assertEquals("Recipe Fork", context.applicationInfo.loadLabel(context.packageManager).toString())
    }

    @Test
    fun theBackgroundIsTheThemesPrimaryContainer() {
        assertEquals(context.getColor(R.color.theme_light_primaryContainer), context.getColor(R.color.ic_launcher_background))
    }

    @Test
    fun theForegroundAndTheMonochromeSitInsideTheSafeZone() {
        for (drawable in listOf(R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)) {
            val name = context.resources.getResourceEntryName(drawable)
            val vector = vectorOf(drawable)
            assertEquals("$name viewport width", CANVAS, vector.viewportWidth, 0f)
            assertEquals("$name viewport height", CANVAS, vector.viewportHeight, 0f)
            assertTrue("$name has no path", vector.paths.isNotEmpty())
            for (data in vector.paths) {
                val bounds = RectF().also { PathParser.createPathFromPathData(data).computeBounds(it, true) }
                val zone = RectF(CENTRE - SAFE_RADIUS, CENTRE - SAFE_RADIUS, CENTRE + SAFE_RADIUS, CENTRE + SAFE_RADIUS)
                assertTrue("$name: $bounds leaves the safe zone $zone", zone.contains(bounds))
                // The safe zone is a circle: even the bounds' corners stay inside it.
                for ((x, y) in listOf(bounds.left to bounds.top, bounds.right to bounds.top, bounds.left to bounds.bottom, bounds.right to bounds.bottom)) {
                    assertTrue("$name: corner ($x, $y) of $bounds leaves the safe circle", hypot(x - CENTRE, y - CENTRE) <= SAFE_RADIUS)
                }
            }
        }
    }

    @Test
    fun rendersThePlayStoreIcon() {
        val icon = renderPlayIcon(context)
        val file = File("build/play/icon-512.png").absoluteFile
        file.parentFile!!.mkdirs()
        file.outputStream().use { assertTrue("the PNG was not written", icon.compress(Bitmap.CompressFormat.PNG, 100, it)) }

        assertEquals(PLAY_SIZE, icon.width)
        assertEquals(PLAY_SIZE, icon.height)
        val background = context.getColor(R.color.ic_launcher_background)
        val card = context.getColor(R.color.theme_light_primary)
        for ((x, y) in listOf(0 to 0, PLAY_SIZE - 1 to 0, 0 to PLAY_SIZE - 1, PLAY_SIZE - 1 to PLAY_SIZE - 1)) {
            assertSameColour("corner ($x, $y)", background, icon.getPixel(x, y))
        }
        assertSameColour("the card", card, icon.getPixel(playPixel(40f), playPixel(60f)))
        assertSameColour("the fork's handle, cut out of the card", background, icon.getPixel(playPixel(54f), playPixel(62f)))
        for (y in 0 until PLAY_SIZE) for (x in 0 until PLAY_SIZE) {
            if (icon.getPixel(x, y) ushr 24 != 0xFF) throw AssertionError("pixel ($x, $y) is not opaque")
        }
    }

    private class Vector(val viewportWidth: Float, val viewportHeight: Float, val paths: List<String>)

    private fun vectorOf(drawable: Int): Vector {
        var width = 0f
        var height = 0f
        val paths = mutableListOf<String>()
        context.resources.getXml(drawable).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "vector" -> {
                        width = parser.getAttributeFloatValue(ANDROID, "viewportWidth", 0f)
                        height = parser.getAttributeFloatValue(ANDROID, "viewportHeight", 0f)
                    }
                    "path" -> paths += parser.getAttributeValue(ANDROID, "pathData")
                }
            }
        }
        return Vector(width, height, paths)
    }

    private fun mergedManifestApplication(): org.w3c.dom.Element {
        val config = Properties().apply {
            LauncherIconTest::class.java.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties")!!.use { load(it) }
        }
        val manifest = File(config.getProperty("android_merged_manifest"))
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        return document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
    }

    private fun playPixel(canvasDp: Float): Int = ((canvasDp - INSET) / (CANVAS - 2 * INSET) * PLAY_SIZE).toInt()

    private fun assertSameColour(what: String, expected: Int, actual: Int) {
        val close = listOf(16, 8, 0).all { abs((expected shr it and 0xFF) - (actual shr it and 0xFF)) <= 2 }
        assertTrue("$what is #%08X, not #%08X".format(actual, expected), close)
    }

    companion object {
        private const val ANDROID = "http://schemas.android.com/apk/res/android"
        private const val CANVAS = 108f
        private const val CENTRE = CANVAS / 2
        private const val SAFE_RADIUS = 33f
        private const val INSET = 18f
        private const val PLAY_SIZE = 512

        /**
         * The launcher icon as Google Play takes it: the adaptive icon's background and foreground layers, the 108 dp
         * canvas's middle 72 dp (the part a launcher shows, the outer 18 dp being room for its motion) scaled to fill a
         * [size]-pixel square, with no mask — Play rounds the corners itself.
         */
        fun renderPlayIcon(context: Context, size: Int = PLAY_SIZE): Bitmap {
            val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
            val canvasPx = Math.round(size * CANVAS / (CANVAS - 2 * INSET))
            val insetPx = (canvasPx - size) / 2f
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(-insetPx, -insetPx)
            for (layer in listOf(icon.background, icon.foreground)) {
                layer.setBounds(0, 0, canvasPx, canvasPx)
                layer.draw(canvas)
            }
            return bitmap
        }
    }
}
