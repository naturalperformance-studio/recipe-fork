package app.recipefork.app.print

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The PDF the pages are written to, read back by the platform's own renderer on a device (Robolectric draws no PDF):
 * a two-page document makes a two-page file at the page's size, and a print job's subset of pages only those.
 */
@RunWith(AndroidJUnit4::class)
class PdfWrittenOnDeviceTest {
    private val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "print-test").apply { mkdirs() }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun aTwoPageDocumentIsATwoPagePdf() {
        // Three pictures each a third of the page tall: two fit on a page, the third moves whole to the second.
        val photo = picture()
        val document = PrintDocument(
            "Борщ",
            listOf(PrintBlock.Picture(photo), PrintBlock.Picture(photo), PrintBlock.Picture(photo), PrintBlock.Paragraph("Свёкла, капуста, лук.")),
        )
        val pages = PrintPages.layOut(document, PageSize.A4)
        assertEquals(2, pages.count)

        val file = File(directory, "borscht.pdf")
        assertEquals(2, file.outputStream().use { pages.writePdf(it) })

        read(file) { renderer ->
            assertEquals(2, renderer.pageCount)
            renderer.openPage(0).use { page ->
                assertEquals(595, page.width)
                assertEquals(842, page.height)
                val shown = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(shown, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                assertEquals(Color.WHITE, shown.getPixel(4, 4))
                val inPicture = shown.getPixel(297, 120)
                assertTrue("%08x".format(inPicture), Color.red(inPicture) > 200 && Color.green(inPicture) < 60)
            }
        }
    }

    @Test
    fun aPrintJobCanWriteOnlySomeOfThePages() {
        val photo = picture()
        val pages = PrintPages.layOut(PrintDocument("Борщ", List(5) { PrintBlock.Picture(photo) }), PageSize.ofMils(8500, 11000))
        assertEquals(3, pages.count)

        val file = File(directory, "some.pdf")
        assertEquals(1, file.outputStream().use { pages.writePdf(it, listOf(2)) })
        read(file) { renderer ->
            assertEquals(1, renderer.pageCount)
            renderer.openPage(0).use { assertEquals(612, it.width) }
        }
    }

    private fun picture(): File {
        val bitmap = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return File(directory, "photo.png").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun read(file: File, check: (PdfRenderer) -> Unit) {
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use(check)
    }
}
