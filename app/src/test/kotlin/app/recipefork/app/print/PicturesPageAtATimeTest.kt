package app.recipefork.app.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ceil

/**
 * v0.37, T-266 — a long document holds its pictures a page at a time: laying out reads only their sizes, a page's
 * pictures are decoded when it is drawn, at the size they are printed, and recycled once it is drawn; a picture that
 * cannot be decoded then leaves its space empty.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PicturesPageAtATimeTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Decodes as the platform does and keeps every bitmap it hands out, to count those not yet recycled. */
    private class Counting(private val inner: PictureDecoder = PictureDecoder.Platform) : PictureDecoder {
        val decoded = mutableListOf<Bitmap>()
        val asked = mutableListOf<Pair<Int, Int>>()
        var mostAlive = 0

        fun alive() = decoded.count { !it.isRecycled }

        override fun decode(file: File, width: Int, height: Int): Bitmap? {
            asked += width to height
            return inner.decode(file, width, height)?.also {
                decoded += it
                mostAlive = maxOf(mostAlive, alive())
            }
        }
    }

    private val a4 = PageSize.A4

    /** The pixels a side of [points] is decoded at. */
    private fun pixels(points: Float) = ceil(points * PICTURE_DPI / 72f).toInt()

    /** The pixels of the frame a picture of [width] by [height] is printed in on A4. */
    private fun frame(width: Int, height: Int) = pictureSize(width, height, a4.textWidth, a4.height / 3).let { pixels(it.width) to pixels(it.height) }

    private fun page() = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)

    @Test
    fun aDocumentOfManyPicturesHoldsNoneAfterLayoutAndOnePagesWorthWhileWriting() {
        val photo = picture(800, 450)
        val other = picture(450, 800)
        // Forty recipes, each on its own page with two pictures: eighty pictures in all.
        val blocks = (1..40).flatMap { n ->
            listOf(PrintBlock.Title("Рецепт $n"), PrintBlock.Picture(photo), PrintBlock.Paragraph("Шаг $n"), PrintBlock.Picture(other), PrintBlock.PageBreak)
        }
        val decoder = Counting()
        val pages = PrintPages.layOut(PrintDocument("Сборник", blocks), a4, decoder)

        assertEquals(40, pages.count)
        assertEquals("decoded while laying out", 0, decoder.decoded.size)

        var drawn = 0
        pages.writePages((0 until pages.count).toList()) { number, draw ->
            assertEquals("held when page $number begins", 0, decoder.alive())
            draw(Canvas(page()))
            assertEquals("held while page $number is finished", 2, decoder.alive())
            drawn++
        }

        assertEquals(40, drawn)
        assertEquals(80, decoder.decoded.size)
        assertEquals(2, decoder.mostAlive)
        assertEquals("held after writing", 0, decoder.alive())
    }

    @Test
    fun aPageDrawnOnItsOwnLetsItsPicturesGo() {
        val decoder = Counting()
        val pages = PrintPages.layOut(PrintDocument("Фото", listOf(PrintBlock.Picture(picture(800, 450)))), a4, decoder)
        val page = page()
        pages.draw(Canvas(page), 0)

        assertEquals(1, decoder.decoded.size)
        assertEquals(0, decoder.alive())
        assertEquals(Color.RED, page.getPixel(297, a4.margin.toInt() + 100))
    }

    @Test
    fun aPictureIsDecodedAtNoMorePixelsThanItsFrameNeeds() {
        val decoder = Counting()
        val wide = picture(1600, 900)
        val tall = picture(900, 1600)
        val pages = PrintPages.layOut(PrintDocument("Фото", listOf(PrintBlock.Picture(wide), PrintBlock.Picture(tall))), a4, decoder)
        pages.writePages((0 until pages.count).toList()) { _, draw -> draw(Canvas(page())) }

        // The frames the pictures are printed in, at 150 pixels to the inch: about 1040 of the wide one's 1600.
        val wideFrame = frame(1600, 900)
        val tallFrame = frame(900, 1600)
        assertEquals(listOf(wideFrame, tallFrame), decoder.asked)
        decoder.decoded.zip(decoder.asked).forEach { (bitmap, frame) ->
            assertTrue("${bitmap.width} x ${bitmap.height} for $frame", bitmap.width <= frame.first && bitmap.height <= frame.second)
            assertTrue("${bitmap.width} x ${bitmap.height} for $frame", bitmap.width >= frame.first - 2 || bitmap.height >= frame.second - 2)
        }
    }

    /** What the decoder is asked for, page by page, for a document of [pictures]; it hands back a stand-in pixel. */
    private fun asksFor(pictures: List<File>): List<Pair<Int, Int>> {
        val decoder = Counting { _, _, _ -> Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
        val pages = PrintPages.layOut(PrintDocument("Сборник", pictures.map { PrintBlock.Picture(it) }), a4, decoder)
        val tiny = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        pages.writePages((0 until pages.count).toList()) { _, draw -> draw(Canvas(tiny)) }
        assertEquals(pictures.size, decoder.asked.size)
        return decoder.asked
    }

    private fun total(asked: List<Pair<Int, Int>>) = asked.sumOf { (w, h) -> w.toLong() * h }

    @Test
    fun aRecipesPicturesAreAskedForAtTheirPrintSizeUnchanged() {
        // A recipe of a dozen photos, 4:3 and 3:4, stored at the app's 1600 pixels: well inside the budget.
        val landscape = picture(1600, 1200)
        val portrait = picture(1200, 1600)
        val asked = asksFor(List(12) { if (it % 2 == 0) landscape else portrait })

        assertEquals(List(6) { listOf(frame(1600, 1200), frame(1200, 1600)) }.flatten(), asked)
        assertTrue(total(asked) <= PICTURE_BUDGET)
    }

    @Test
    fun aDocumentOfManyPicturesIsAskedForNoMoreThanTheBudgetAltogether() {
        // Forty recipes of six photos: about 146 million pixels at the print size, nine times the budget.
        val photo = picture(1600, 900)
        val asked = asksFor(List(240) { photo })
        val (fullW, fullH) = frame(1600, 900)

        assertTrue("${total(asked)} pixels", total(asked) <= PICTURE_BUDGET)
        assertTrue("${total(asked)} pixels", total(asked) > PICTURE_BUDGET * 0.98)
        for ((w, h) in asked) {
            assertTrue("$w x $h", w < fullW && h < fullH && w >= PICTURE_FLOOR)
            assertEquals(fullW.toFloat() / fullH, w.toFloat() / h, 0.02f)
        }
    }

    @Test
    fun aPictureIsNeverShrunkBelowTheFloorNorEnlarged() {
        // So many pictures that the budget would leave each under the floor; a small one stays its own size.
        val photo = picture(1600, 900)
        val small = picture(200, 120)
        val asked = asksFor(List(600) { photo } + small)

        val (fullW, fullH) = frame(1600, 900)
        for ((w, h) in asked.dropLast(1)) {
            assertEquals(PICTURE_FLOOR, w)
            assertEquals(fullH.toFloat() / fullW * PICTURE_FLOOR, h.toFloat(), 1f)
        }
        assertEquals(200 to 120, asked.last())
    }

    @Test
    fun aTurnedPictureIsAskedForAtItsStoredProportions() {
        // Stored 900 x 1600 and turned a quarter: printed upright as 1600 x 900, as wide as the text.
        val turned = tag(jpeg(900, 1600), ExifInterface.ORIENTATION_ROTATE_90)
        val decoder = Counting()
        val pages = PrintPages.layOut(PrintDocument("Фото", listOf(PrintBlock.Picture(turned))), a4, decoder)
        pages.draw(Canvas(page()), 0)

        assertEquals(pictureSize(1600, 900, a4.textWidth, a4.height / 3).height, pages.lineHeights(0).single(), 0.01f)
        assertEquals(listOf(frame(1600, 900).let { (w, h) -> h to w }), decoder.asked)
    }

    @Test
    fun theDecoderSamplesThenScalesDownAndNeverEnlarges() {
        val big = picture(3200, 1800)
        val sampled = decodeAtMost(big, 700, 394)!!
        assertTrue("${sampled.width} x ${sampled.height}", sampled.width in 698..700 && sampled.height <= 394)

        val exact = decodeAtMost(big, 800, 450)!!
        assertEquals(800 to 450, exact.width to exact.height)

        val small = decodeAtMost(picture(400, 225), 1063, 598)!!
        assertEquals(400 to 225, small.width to small.height)

        // Limited by its height: a tall frame narrower than the picture's proportions.
        val byHeight = decodeAtMost(big, 2000, 500)!!
        assertTrue("${byHeight.width} x ${byHeight.height}", byHeight.height in 498..500 && byHeight.width <= 2000)

        val broken = folder.newFile("broken.jpg").apply { writeText("not a picture") }
        assertEquals(null, decodeAtMost(broken, 100, 100))
        assertEquals(null, decodeAtMost(File(folder.root, "missing.jpg"), 100, 100))
    }

    @Test
    fun aPictureThatCannotBeDecodedWhenDrawnLeavesItsSpaceEmpty() {
        val photo = picture(800, 450)
        val blocks = listOf(PrintBlock.Picture(photo), PrintBlock.Paragraph("Текст под фотографией, который всё равно печатается."))
        val pages = PrintPages.layOut(PrintDocument("Фото", blocks), a4)
        // Readable when laid out, spoiled before its page is drawn.
        photo.writeText("not a picture any more")

        assertPictureSpaceEmptyAndTextDrawn(pages)
    }

    @Test
    fun aDecoderThatRunsOutOfMemoryOrFailsLeavesTheSpaceEmpty() {
        val blocks = listOf(PrintBlock.Picture(picture(800, 450)), PrintBlock.Paragraph("Текст под фотографией, который всё равно печатается."))
        for (failing in listOf(
            PictureDecoder { _, _, _ -> throw OutOfMemoryError("no room") },
            PictureDecoder { _, _, _ -> throw IllegalStateException("broken") },
            PictureDecoder { _, _, _ -> null },
        )) {
            assertPictureSpaceEmptyAndTextDrawn(PrintPages.layOut(PrintDocument("Фото", blocks), a4, failing))
        }
    }

    /** One page of a picture then a paragraph, written whole: nothing where the picture was, the text under it. */
    private fun assertPictureSpaceEmptyAndTextDrawn(pages: PrintPages) {
        assertEquals(1, pages.count)
        val page = page()
        pages.writePages(listOf(0)) { _, draw -> draw(Canvas(page)) }

        val margin = a4.margin.toInt()
        val pictureHeight = pages.lineHeights(0).single().toInt()
        val inPicture = (margin until margin + pictureHeight step 7).sumOf { y -> (margin until 595 - margin step 7).count { x -> page.getPixel(x, y) != Color.WHITE } }
        assertEquals(0, inPicture)
        val textTop = margin + pictureHeight + 8
        val dark = (textTop until textTop + 40).sumOf { y -> (margin until 400).count { x -> Color.red(page.getPixel(x, y)) < 100 } }
        assertTrue("$dark dark pixels under the picture's space", dark > 100)
    }

    private fun picture(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return folder.newFile("picture-$width-$height.png").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun jpeg(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return folder.newFile("photo-$width-$height.jpg").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
    }

    private fun tag(file: File, orientation: Int): File {
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }
}
