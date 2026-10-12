package app.recipefork.app.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Documents laid out by the platform's own text measuring (FLOW-075 (3), (4)). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrintPagesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val step = "Обжарьте лук на среднем огне до золотистого цвета, помешивая, затем добавьте морковь и тушите ещё пять минут."

    @Test
    fun aLongDocumentBreaksBetweenMeasuredLinesAndNoLineIsCut() {
        val blocks = listOf(PrintBlock.Title("Борщ")) + (1..40).flatMap { n ->
            listOf(PrintBlock.Numbered(n, step), PrintBlock.Muted("Заметка автора к шагу $n"))
        }
        val pages = PrintPages.layOut(PrintDocument("Борщ", blocks))

        assertTrue("${pages.count} pages", pages.count >= 3)
        val placed = mutableMapOf<Int, MutableList<Int>>()
        for (index in 0 until pages.count) {
            val placements = pages.placements(index)
            for (placement in placements) {
                val heights = pages.lineHeights(placement.block)
                assertTrue(heights.all { it > 8f && it < 40f })
                val bottom = placement.top + heights.subList(placement.from, placement.to).sum()
                assertTrue("page ${index + 1}: $placement ends at $bottom", bottom <= pages.bodyHeight)
                placed.getOrPut(placement.block) { mutableListOf() } += (placement.from until placement.to)
            }
            // The page is full: the next line would not have fit on it.
            if (index < pages.count - 1) {
                val last = placements.last()
                val bottom = last.top + pages.lineHeights(last.block).subList(last.from, last.to).sum()
                val next = if (last.to < pages.lineHeights(last.block).size) {
                    pages.lineHeights(last.block)[last.to]
                } else {
                    pages.lineHeights(last.block + 1).first()
                }
                assertTrue("page ${index + 1} ends at $bottom with room for $next", bottom + next > pages.bodyHeight - 8f)
            }
        }
        // Every line of every block placed once, in order.
        for (block in blocks.indices) assertEquals((0 until pages.lineHeights(block).size).toList(), placed[block])
        assertTrue("a step wraps", pages.lineHeights(1).size >= 2)
    }

    @Test
    fun aHeadingIsNeverTheLastThingOnAPage() {
        // However much comes before it, the heading shares its page with the first line after it.
        for (filler in 40..80) {
            val blocks = List(filler) { PrintBlock.Bullet("Строка $it") } +
                listOf(PrintBlock.Heading("Шаги"), PrintBlock.Paragraph(step))
            val pages = PrintPages.layOut(PrintDocument("Суп", blocks))
            val page = (0 until pages.count).first { index -> pages.placements(index).any { it.block == filler } }
            assertTrue(
                "with $filler lines before it",
                pages.placements(page).any { it.block == filler + 1 && it.from == 0 },
            )
        }
    }

    @Test
    fun aPictureIsScaledToTheTextWidthOrAThirdOfThePage() {
        val wide = picture(1600, 800)
        val tall = picture(600, 1200)
        val pages = PrintPages.layOut(PrintDocument("Фото", listOf(PrintBlock.Picture(wide), PrintBlock.Picture(tall))))

        val a4 = PageSize.A4
        assertEquals(a4.textWidth / 2, pages.lineHeights(0).single(), 0.01f)
        assertEquals(a4.height / 3, pages.lineHeights(1).single(), 0.01f)
    }

    @Test
    fun aPictureThatDoesNotFitMovesWholeToTheNextPage() {
        // About 17 points a line: thirty fill well over half the page, leaving less than the picture's 287.
        val blocks = List(30) { PrintBlock.Bullet("Строка $it") } + PrintBlock.Picture(picture(1600, 900))
        val pages = PrintPages.layOut(PrintDocument("Фото", blocks))

        assertEquals(2, pages.count)
        val last = pages.placements(0).last()
        assertEquals(29, last.block)
        assertTrue(last.top + pages.lineHeights(29).single() + pages.lineHeights(30).single() > pages.bodyHeight)
        assertEquals(listOf(Placement(30, 0, 1, 0f)), pages.placements(1))
    }

    @Test
    fun aPictureThatCannotBeReadIsLeftOut() {
        val broken = folder.newFile("broken.jpg").apply { writeText("not a picture") }
        val missing = File(folder.root, "missing.jpg")
        val blocks = listOf(PrintBlock.Picture(broken), PrintBlock.Paragraph("Текст"), PrintBlock.Picture(missing))
        val pages = PrintPages.layOut(PrintDocument("Фото", blocks))

        assertEquals(listOf(Placement(1, 0, 1, 0f)), pages.placements(0))
    }

    @Test
    fun eachPagesFooterNamesTheTitleAndItsNumber() {
        val blocks = List(150) { PrintBlock.Box("Строка $it", ticked = it % 2 == 0, note = "Для борща") }
        val pages = PrintPages.layOut(PrintDocument("Список\nпокупок", blocks))

        assertTrue(pages.count >= 3)
        assertEquals(Footer("Список покупок", "1 / ${pages.count}"), pages.footer(0))
        assertEquals("${pages.count} / ${pages.count}", pages.footer(pages.count - 1).number)
        // A box line and its note stay on one page.
        for (index in 0 until pages.count) pages.placements(index).forEach { assertEquals(Placement(it.block, 0, 2, it.top), it) }
    }

    @Test
    fun aLongTitleIsCutToOneLineInTheFooter() {
        val title = "Очень длинное название рецепта ".repeat(10).trim()
        val footer = PrintPages.layOut(PrintDocument(title, listOf(PrintBlock.Paragraph("Текст")))).footer(0)

        assertTrue(footer.title, footer.title.endsWith("…"))
        assertTrue(footer.title.length < title.length)
        val muted = TextPaint().apply { textSize = 9.5f; typeface = Typeface.SANS_SERIF }
        assertTrue(muted.measureText(footer.title) + muted.measureText(footer.number) < PageSize.A4.textWidth)
    }

    @Test
    fun theSansFontHoldsCyrillic() {
        val paint = TextPaint().apply { typeface = Typeface.SANS_SERIF }
        assertTrue("Ёжик щёлкает".all { it == ' ' || paint.hasGlyph(it.toString()) })
    }

    @Test
    fun aPageIsDrawnBlackOnWhiteWithItsPicture() {
        val blocks = listOf(
            PrintBlock.Picture(picture(1600, 800)),
            PrintBlock.Title("Борщ"),
            PrintBlock.Heading("Ингредиенты"),
            PrintBlock.Box("Свёкла 2 шт", ticked = true, note = "Для борща"),
        )
        val pages = PrintPages.layOut(PrintDocument("Борщ", blocks))
        val page = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        pages.draw(Canvas(page), 0)

        assertEquals(Color.WHITE, page.getPixel(5, 5))
        val margin = PageSize.A4.margin.toInt()
        assertEquals(Color.RED, page.getPixel(297, margin + 100))
        val textTop = margin + pages.lineHeights(0).single().toInt() + 10
        val dark = (textTop until textTop + 80).sumOf { y -> (margin until 300).count { x -> Color.red(page.getPixel(x, y)) < 100 } }
        assertTrue("$dark dark pixels under the picture", dark > 200)
        val footer = (842 - margin - 12 until 842 - margin).sumOf { y -> (margin until 595 - margin).count { x -> page.getPixel(x, y) != Color.WHITE } }
        assertTrue("$footer pixels in the footer", footer > 20)
    }

    private fun picture(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return folder.newFile("picture-$width-$height.png").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
