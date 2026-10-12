package app.recipefork.app.print

import android.os.CancellationSignal
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentInfo
import android.print.RecordedLayout
import android.print.RecordedWrite
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executor

/**
 * v0.33, T-237 — the adapter the system's print dialog drives (FLOW-075): the pages laid out at the media size the
 * dialog asks for, again only when it changes, and the page ranges it asks for written.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PagesPrintAdapterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val step = "Обжарьте лук на среднем огне до золотистого цвета, помешивая, затем добавьте морковь и тушите ещё пять минут."

    /** A recipe long enough for several pages. */
    private val document = PrintDocument("Борщ", listOf(PrintBlock.Title("Борщ")) + (1..60).map { PrintBlock.Numbered(it, step) })

    /** The pages each write was asked for, by index; the file gets a stand-in for the PDF. */
    private val asked = mutableListOf<List<Int>>()
    private val writer = PdfWriter { _, out, which ->
        asked += which
        out.write("%PDF-stand-in".toByteArray())
    }

    /** The work is done where it is handed over; its answer still comes on the main thread. */
    private val adapter = PagesPrintAdapter(document, "Борщ.pdf", writer, Executor(Runnable::run))

    private fun attributes(media: PrintAttributes.MediaSize) = PrintAttributes.Builder()
        .setMediaSize(media)
        .setResolution(PrintAttributes.Resolution("r", "300 dpi", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build()

    private fun layOut(media: PrintAttributes.MediaSize, signal: CancellationSignal = CancellationSignal()): RecordedLayout {
        val answer = RecordedLayout()
        adapter.onLayout(null, attributes(media), signal, answer, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(answer.answered)
        return answer
    }

    private fun write(vararg ranges: PageRange): Pair<RecordedWrite, ByteArray> {
        val file = folder.newFile()
        val answer = RecordedWrite()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE).use {
            adapter.onWrite(arrayOf(*ranges), it, CancellationSignal(), answer)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue(answer.answered)
        return answer to file.readBytes()
    }

    @Test
    fun thePagesAreLaidOutAtTheMediaSizeAndAgainOnlyWhenItChanges() {
        val a4 = layOut(PrintAttributes.MediaSize.ISO_A4)
        val info = a4.info!!
        assertEquals("Борщ.pdf", info.name)
        assertEquals(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT, info.contentType)
        assertEquals(PrintPages.layOut(document, PageSize.A4).count, info.pageCount)
        assertTrue("${info.pageCount} pages", info.pageCount >= 3)
        assertEquals(true, a4.changed)

        // The same size asked again: nothing laid out anew.
        assertEquals(false, layOut(PrintAttributes.MediaSize.ISO_A4).changed)

        // A smaller page holds less: more pages, said to have changed.
        val a5 = layOut(PrintAttributes.MediaSize.ISO_A5)
        assertEquals(true, a5.changed)
        val expected = PrintPages.layOut(document, PageSize.ofMils(PrintAttributes.MediaSize.ISO_A5.widthMils, PrintAttributes.MediaSize.ISO_A5.heightMils))
        assertEquals(expected.count, a5.info!!.pageCount)
        assertTrue(a5.info!!.pageCount > info.pageCount)
    }

    @Test
    fun theRangesAskedForAreWritten() {
        val count = layOut(PrintAttributes.MediaSize.ISO_A4).info!!.pageCount

        val (all, bytes) = write(PageRange.ALL_PAGES)
        assertEquals(listOf(PageRange.ALL_PAGES), all.written)
        assertEquals((0 until count).toList(), asked.last())
        assertEquals("%PDF-stand-in", String(bytes))

        val (some, _) = write(PageRange(0, 0), PageRange(2, 2))
        assertEquals(listOf(PageRange(0, 0), PageRange(2, 2)), some.written)
        assertEquals(listOf(0, 2), asked.last())
    }

    @Test
    fun aCancelledLayoutIsAnsweredAsCancelled() {
        val signal = CancellationSignal().apply { cancel() }
        val answer = layOut(PrintAttributes.MediaSize.ISO_A4, signal)
        assertTrue(answer.cancelled)
        assertFalse(answer.failed)
    }

    @Test
    fun theFinishedJobDropsItsPages() {
        layOut(PrintAttributes.MediaSize.ISO_A4)
        adapter.onFinish()
        val (answer, _) = write(PageRange.ALL_PAGES)
        assertTrue(answer.failed)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun rangesAreMappedToPageIndices() {
        assertEquals(listOf(0, 1, 2), PagesPrintAdapter.pageIndices(listOf(PageRange.ALL_PAGES), 3))
        assertEquals(listOf(1, 2, 4), PagesPrintAdapter.pageIndices(listOf(PageRange(4, 9), PageRange(1, 2)), 5))
        assertEquals(listOf(PageRange.ALL_PAGES), PagesPrintAdapter.writtenRanges(listOf(0, 1, 2), 3).toList())
        assertEquals(listOf(PageRange(1, 2), PageRange(4, 4)), PagesPrintAdapter.writtenRanges(listOf(1, 2, 4), 5).toList())
    }
}
