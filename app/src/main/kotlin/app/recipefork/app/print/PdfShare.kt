package app.recipefork.app.print

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.recipefork.app.R
import app.recipefork.app.file.RecipeExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.Normalizer

/**
 * "Share as PDF" (FLOW-075): a document written as an A4 PDF under the application's cache and handed to the system's
 * share sheet through the [FileProvider] the export uses; where it goes is the person's choice, and the product sends
 * it nowhere itself. Only the latest such file is kept.
 */
object PdfShare {
    /** The directory under the cache that the [FileProvider] shares for PDFs (res/xml/file_paths.xml). */
    private const val DIRECTORY = "prints"
    const val MIME_TYPE = "application/pdf"
    private const val EXTENSION = ".pdf"

    /** The prefix of a file being written, before it takes the shared one's place. */
    private const val PARTIAL = ".writing-"

    /** The most UTF-8 bytes of a name before its extension: well inside the 255 a file system allows. */
    private const val MAX_NAME_BYTES = 200

    /**
     * The file's name: [title] as written, composed (NFC), each character a file name cannot hold — a path separator,
     * `: * ? " < > |`, a control character — made `_`, runs of white space one space, leading dots taken off, cut to
     * fit; [fallback] when nothing is left; then `.pdf`.
     */
    fun fileName(title: String, fallback: String): String {
        val cleaned = Normalizer.normalize(title, Normalizer.Form.NFC)
            .map { if (it in FORBIDDEN || Character.isISOControl(it) && !it.isWhitespace()) '_' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('.')
            .trim()
        return cut(cleaned).ifEmpty { fallback } + EXTENSION
    }

    private const val FORBIDDEN = "/\\:*?\"<>|"

    /** [name] cut, by whole characters, to [MAX_NAME_BYTES] bytes in UTF-8. */
    private fun cut(name: String): String {
        val out = StringBuilder()
        var bytes = 0
        var index = 0
        while (index < name.length) {
            val point = name.codePointAt(index)
            val size = String(Character.toChars(point)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > MAX_NAME_BYTES) break
            out.appendCodePoint(point)
            bytes += size
            index += Character.charCount(point)
        }
        return out.toString().trimEnd()
    }

    /**
     * Writes [document] as an A4 PDF named by its title and returns its shareable address. The file is written beside
     * the one shared before and only once whole put in its place; then everything else in the directory goes — earlier
     * files and any left half-written when the process ended. A failure leaves the earlier file as it was.
     */
    suspend fun write(context: Context, document: PrintDocument, writer: PdfWriter = PdfWriter.Platform): Uri {
        val name = fileName(document.title, context.getString(R.string.common_untitled_recipe))
        val file = withContext(PageWork.dispatcher) {
            val directory = File(context.cacheDir, DIRECTORY)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot make $directory")
            val written = File.createTempFile(PARTIAL, EXTENSION, directory)
            try {
                val pages = PrintPages.layOut(document, PageSize.A4)
                written.outputStream().buffered().use { writer.write(pages, it, (0 until pages.count).toList()) }
                val shared = File(directory, name)
                if (!written.renameTo(shared)) throw IOException("cannot move to $shared")
                directory.listFiles().orEmpty().filter { it != shared }.forEach { it.deleteRecursively() }
                shared
            } finally {
                written.delete()
            }
        }
        return FileProvider.getUriForFile(context, RecipeExport.authority(context), file)
    }

    /** The sheet that lets the person send or save the PDF at [uri], read-only; [title] heads it. */
    fun shareSheet(uri: Uri, title: CharSequence): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, title)
    }

    /**
     * Writes [document] and opens the share sheet on it. Returns false when no file could be written or no sheet
     * opened, so that the person can be told.
     */
    suspend fun share(context: Context, document: PrintDocument, writer: PdfWriter = PdfWriter.Platform): Boolean =
        try {
            val uri = write(context, document, writer)
            RecipeExport.open(context, shareSheet(uri, document.title))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
}
