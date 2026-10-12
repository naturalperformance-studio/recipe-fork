package app.recipefork.app.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The one worker thread every page is laid out and drawn on, for a print job and a shared file alike: the pages' type
 * shares its paints, so two documents are never measured or drawn at once.
 */
internal object PageWork {
    val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "print-pages").apply { isDaemon = true } }
    val dispatcher = executor.asCoroutineDispatcher()
}

/** Writes pages [which] (by index) of laid-out [pages] to a stream as a PDF; the platform's `PdfDocument` by default. */
fun interface PdfWriter {
    fun write(pages: PrintPages, out: OutputStream, which: List<Int>)

    companion object {
        val Platform = PdfWriter { pages, out, which -> pages.writePdf(out, which) }
    }
}

/**
 * Hands [document] to the system's print service (FLOW-075): laid out at the media size the dialog asks for, again only
 * when that size changes, and written page range by page range as the dialog asks, as the PDF [fileName]. The work runs
 * on [PageWork]'s thread and is told back on the main one; a cancelled request is answered as cancelled. The pages laid
 * out are dropped when the job finishes.
 */
class PagesPrintAdapter(
    private val document: PrintDocument,
    private val fileName: String,
    private val writer: PdfWriter = PdfWriter.Platform,
    private val worker: Executor = PageWork.executor,
) : PrintDocumentAdapter() {
    private val main = Handler(Looper.getMainLooper())

    /** The pages laid out for the size asked last; touched on the main thread only. */
    private var pages: PrintPages? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
        val media = newAttributes.mediaSize ?: return callback.onLayoutFailed(null)
        val size = PageSize.ofMils(media.widthMils, media.heightMils)
        val laid = pages
        if (laid != null && laid.size == size) return callback.onLayoutFinished(info(laid.count), false)
        worker.execute {
            val result = if (cancellationSignal?.isCanceled == true) null else runCatching { PrintPages.layOut(document, size) }
            main.post {
                when {
                    result == null || cancellationSignal?.isCanceled == true -> callback.onLayoutCancelled()
                    result.isSuccess -> result.getOrThrow().let {
                        pages = it
                        callback.onLayoutFinished(info(it.count), true)
                    }
                    else -> callback.onLayoutFailed(null)
                }
            }
        }
    }

    override fun onWrite(
        ranges: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val laid = pages ?: return callback.onWriteFailed(null)
        worker.execute {
            val result = if (cancellationSignal?.isCanceled == true) null else runCatching {
                val which = pageIndices(ranges.toList(), laid.count)
                // The stream is the dialog's descriptor, which the print service closes itself.
                val out = FileOutputStream(destination.fileDescriptor)
                writer.write(laid, out, which)
                out.flush()
                writtenRanges(which, laid.count)
            }
            main.post {
                when {
                    result == null || cancellationSignal?.isCanceled == true -> callback.onWriteCancelled()
                    result.isSuccess -> callback.onWriteFinished(result.getOrThrow())
                    else -> callback.onWriteFailed(null)
                }
            }
        }
    }

    override fun onFinish() {
        pages = null
    }

    private fun info(count: Int) = PrintDocumentInfo.Builder(fileName)
        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
        .setPageCount(count)
        .build()

    companion object {
        /** The indices of the pages [ranges] ask for, of [count] pages, in order, each once. */
        fun pageIndices(ranges: List<PageRange>, count: Int): List<Int> = ranges
            .flatMap { range -> range.start.coerceAtLeast(0)..minOf(range.end, count - 1) }
            .distinct()
            .sorted()

        /** The ranges of pages written, [which] of [count]: all of them as [PageRange.ALL_PAGES]. */
        fun writtenRanges(which: List<Int>, count: Int): Array<PageRange> {
            if (which == (0 until count).toList()) return arrayOf(PageRange.ALL_PAGES)
            val ranges = mutableListOf<PageRange>()
            var start = -1
            var end = -1
            for (index in which) {
                if (start >= 0 && index == end + 1) {
                    end = index
                } else {
                    if (start >= 0) ranges += PageRange(start, end)
                    start = index
                    end = index
                }
            }
            if (start >= 0) ranges += PageRange(start, end)
            return ranges.toTypedArray()
        }
    }
}
