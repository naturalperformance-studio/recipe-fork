package app.recipefork.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.VectorDrawable
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
 * The application's launcher icon: the manifest names an adaptive icon (and a round one) whose background, foreground
 * and monochrome (themed icon, Android 13+) layers are all there. The background is paper; the foreground is mark 2б
 * in ink with its bent tine in beet; the monochrome layer is the same paths in one colour; and the picture stays
 * inside the 66 dp safe circle of the 108 dp canvas, so no launcher mask cuts it.
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
    fun bothLauncherIconsNameTheBackgroundTheForegroundAndTheMonochrome() {
        val expected = mapOf(
            "background" to R.color.ic_launcher_background,
            "foreground" to R.drawable.ic_launcher_foreground,
            "monochrome" to R.drawable.ic_launcher_monochrome,
        )
        for (icon in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val layers = mutableMapOf<String, Int>()
            context.resources.getXml(icon).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name in expected) {
                        layers[parser.name] = parser.getAttributeResourceValue(ANDROID, "drawable", 0)
                    }
                }
            }
            assertEquals(context.resources.getResourceEntryName(icon), expected, layers)
        }
    }

    @Test
    fun theBackgroundIsPaper() {
        assertEquals(PAPER, context.getColor(R.color.ic_launcher_background))
    }

    @Test
    fun theForegroundIsTheMarkInInkWithItsBentTineInBeet() {
        val foreground = readVector(context, R.drawable.ic_launcher_foreground)
        assertEquals(listOf(INK, BEET), foreground.paths.map { it.fillColor })
        assertEquals(listOf(INK, BEET), foreground.paths.map { it.strokeColor })

        val icon = renderDrawable(context, R.drawable.ic_launcher_foreground, RENDER)
        assertSameColour("the handle", INK, icon.getPixel(renderPixel(Mark2b.HANDLE, 0), renderPixel(Mark2b.HANDLE, 1)))
        assertSameColour("the bent tine", BEET, icon.getPixel(renderPixel(Mark2b.BENT_TINE, 0), renderPixel(Mark2b.BENT_TINE, 1)))
    }

    @Test
    fun theMonochromeIsTheForegroundsPathsInOneColour() {
        val foreground = readVector(context, R.drawable.ic_launcher_foreground)
        val monochrome = readVector(context, R.drawable.ic_launcher_monochrome)
        assertEquals(foreground.copy(paths = emptyList()), monochrome.copy(paths = emptyList()))
        assertEquals(foreground.paths.map { it.pathData }, monochrome.paths.map { it.pathData })
        assertEquals(foreground.paths.map { it.strokeWidth }, monochrome.paths.map { it.strokeWidth })
        val colours = monochrome.paths.flatMap { listOfNotNull(it.fillColor, it.strokeColor) }.toSet()
        assertEquals("the monochrome layer's colours", 1, colours.size)
        assertEquals("the monochrome layer is not opaque", 0xFF, colours.single() ushr 24)
    }

    @Test
    fun theForegroundAndTheMonochromeSitInsideTheSafeCircle() {
        for (drawable in listOf(R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)) {
            val name = context.resources.getResourceEntryName(drawable)
            val vector = readVector(context, drawable)
            assertEquals("$name width", CANVAS, vector.widthDp, 0f)
            assertEquals("$name viewport", CANVAS to CANVAS, vector.viewportWidth to vector.viewportHeight)

            val icon = renderDrawable(context, drawable, RENDER)
            val pixelsPerDp = RENDER / CANVAS
            var farthest = 0f
            for (y in 0 until RENDER) for (x in 0 until RENDER) {
                if (icon.getPixel(x, y) ushr 24 == 0) continue
                val distance = hypot((x + 0.5f) / pixelsPerDp - CENTRE, (y + 0.5f) / pixelsPerDp - CENTRE)
                // Antialiasing may spread an edge by up to one pixel.
                assertTrue("$name: pixel ($x, $y) is drawn ${distance}dp from the centre", distance <= SAFE_RADIUS + 1 / pixelsPerDp)
                farthest = maxOf(farthest, distance)
            }
            // As large as the safe circle allows: the handle's end comes up to it.
            assertTrue("$name reaches only ${farthest}dp from the centre", farthest > SAFE_RADIUS - 1)
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
        for ((x, y) in listOf(0 to 0, PLAY_SIZE - 1 to 0, 0 to PLAY_SIZE - 1, PLAY_SIZE - 1 to PLAY_SIZE - 1)) {
            assertSameColour("corner ($x, $y)", PAPER, icon.getPixel(x, y))
        }
        assertSameColour("the handle", INK, icon.getPixel(playPixel(Mark2b.HANDLE, 0), playPixel(Mark2b.HANDLE, 1)))
        assertSameColour("the bent tine", BEET, icon.getPixel(playPixel(Mark2b.BENT_TINE, 0), playPixel(Mark2b.BENT_TINE, 1)))
        for (y in 0 until PLAY_SIZE) for (x in 0 until PLAY_SIZE) {
            if (icon.getPixel(x, y) ushr 24 != 0xFF) throw AssertionError("pixel ($x, $y) is not opaque")
        }
    }

    private fun mergedManifestApplication(): org.w3c.dom.Element {
        val config = Properties().apply {
            LauncherIconTest::class.java.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties")!!.use { load(it) }
        }
        val manifest = File(config.getProperty("android_merged_manifest"))
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        return document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
    }

    /** The pixel, in a [RENDER]-pixel rendering of the canvas, of the mark's [point]'s [axis] (0 for x, 1 for y). */
    private fun renderPixel(point: Pair<Double, Double>, axis: Int): Int {
        val (x, y) = Mark2b.onLauncherCanvas(point.first, point.second)
        return ((if (axis == 0) x else y) * RENDER / CANVAS).toInt()
    }

    /** The pixel, in the Play icon, of the mark's [point]'s [axis] (0 for x, 1 for y). */
    private fun playPixel(point: Pair<Double, Double>, axis: Int): Int {
        val (x, y) = Mark2b.onLauncherCanvas(point.first, point.second)
        val canvasDp = (if (axis == 0) x else y).toFloat()
        return ((canvasDp - INSET) / (CANVAS - 2 * INSET) * PLAY_SIZE).toInt()
    }

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
        private const val RENDER = 1080
        private const val PAPER = 0xFFF6F0E4.toInt()
        private const val INK = 0xFF2B241E.toInt()
        private const val BEET = 0xFF8E3B46.toInt()

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
