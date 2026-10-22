package app.recipefork.app

import android.content.Context
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.xmlpull.v1.XmlPullParser
import kotlin.math.cos
import kotlin.math.sin

/** One `<path>` of a vector drawable as its XML gives it: the path data and its paint. */
data class VectorPathXml(val pathData: String, val fillColor: Int?, val strokeColor: Int?, val strokeWidth: Float)

/** A vector drawable as its XML gives it: its size in dp, its viewport and its paths in drawing order. */
data class VectorXml(val widthDp: Float, val heightDp: Float, val viewportWidth: Float, val viewportHeight: Float, val paths: List<VectorPathXml>)

private const val ANDROID = "http://schemas.android.com/apk/res/android"

/** Reads the vector drawable [drawable]'s XML, colours resolved. */
fun readVector(context: Context, drawable: Int): VectorXml {
    var width = 0f
    var height = 0f
    var viewportWidth = 0f
    var viewportHeight = 0f
    val paths = mutableListOf<VectorPathXml>()
    context.resources.getXml(drawable).use { parser ->
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "vector" -> {
                    width = dimensionDp(parser.getAttributeValue(ANDROID, "width"))
                    height = dimensionDp(parser.getAttributeValue(ANDROID, "height"))
                    viewportWidth = parser.getAttributeFloatValue(ANDROID, "viewportWidth", 0f)
                    viewportHeight = parser.getAttributeFloatValue(ANDROID, "viewportHeight", 0f)
                }
                "path" -> paths += VectorPathXml(
                    parser.getAttributeValue(ANDROID, "pathData"),
                    colour(context, parser, "fillColor"),
                    colour(context, parser, "strokeColor"),
                    parser.getAttributeFloatValue(ANDROID, "strokeWidth", 0f),
                )
            }
        }
    }
    return VectorXml(width, height, viewportWidth, viewportHeight, paths)
}

private fun dimensionDp(value: String): Float = value.removeSuffix("dip").removeSuffix("dp").toFloat()

private fun colour(context: Context, parser: XmlResourceParser, attribute: String): Int? {
    val reference = parser.getAttributeResourceValue(ANDROID, attribute, 0)
    if (reference != 0) return context.getColor(reference)
    val value = parser.getAttributeValue(ANDROID, attribute) ?: return null
    return Color.parseColor(value)
}

/** Renders the drawable [drawable] into a transparent [size]-pixel square. */
fun renderDrawable(context: Context, drawable: Int, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val picture = context.getDrawable(drawable)!!
    picture.setBounds(0, 0, size, size)
    picture.draw(Canvas(bitmap))
    return bitmap
}

/** Splits SVG or VectorDrawable path data into its commands and numbers. */
fun pathTokens(data: String): List<String> =
    Regex("""[A-Za-z]|-?(?:\d+\.?\d*|\.\d+)""").findAll(data).map { it.value }.toList()

/**
 * Mark 2б as `docs/brand/identity/mark-2.html` draws it: its symbols `m2bb` (the drawing from 24 px up) and `m2bbs`
 * (the thick one for 16–20 px) put their paths, drawn about the fork's axis, into a 48-unit box with the group
 * transform `translate(21.9 25.5) rotate(45) scale(.94)`. The launcher icon places that box, 58 dp wide, in the middle
 * of its 108 dp canvas; the notification icon draws the box as its viewport.
 */
object Mark2b {
    const val TRANSLATE_X = 21.9
    const val TRANSLATE_Y = 25.5
    const val ROTATE = 45.0
    const val SCALE = 0.94

    /** The 48-unit box's side on the launcher icon's canvas, in dp. */
    const val LAUNCHER_BOX = 58.0
    const val LAUNCHER_OFFSET = (108.0 - LAUNCHER_BOX) / 2
    const val LAUNCHER_SCALE = LAUNCHER_BOX / 48

    /** A point given about the fork's axis, in the 48-unit box. */
    fun inBox(x: Double, y: Double): Pair<Double, Double> {
        val angle = Math.toRadians(ROTATE)
        val sx = x * SCALE
        val sy = y * SCALE
        return (sx * cos(angle) - sy * sin(angle) + TRANSLATE_X) to (sx * sin(angle) + sy * cos(angle) + TRANSLATE_Y)
    }

    /** A point given about the fork's axis, on the launcher icon's 108 dp canvas. */
    fun onLauncherCanvas(x: Double, y: Double): Pair<Double, Double> {
        val (bx, by) = inBox(x, y)
        return (bx * LAUNCHER_SCALE + LAUNCHER_OFFSET) to (by * LAUNCHER_SCALE + LAUNCHER_OFFSET)
    }

    /** A point on the handle, inside the ink and away from its edges. */
    val HANDLE = 0.0 to 12.0

    /** A point on the bent tine, inside the beet and away from its edges. */
    val BENT_TINE = 5.1 to -15.3
}
