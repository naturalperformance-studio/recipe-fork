package app.recipefork.app.print

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.RecipeExport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

/**
 * v0.33, T-237 — "Share as PDF" (FLOW-075): an A4 PDF under the cache, named by the document's title as written, shared
 * read-only through the export's FileProvider; only the latest file kept; a failure leaves everything as it was. The
 * JVM cannot run the platform's PdfDocument, so the pages here are written by a stand-in; the device test reads a real
 * one back (T-238).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfShareTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bytes = "%PDF-stand-in".toByteArray()
    private val sizes = mutableListOf<PageSize>()
    private val writer = PdfWriter { pages, out, _ ->
        sizes += pages.size
        out.write(bytes)
    }

    private fun document(title: String) = PrintDocument(title, listOf(PrintBlock.Title(title), PrintBlock.Paragraph("Bake it.")))

    private fun write(title: String): Uri = runBlocking { PdfShare.write(context, document(title), writer) }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }

    @Before
    fun forgetEarlierDirectories() = FileProviderCache.clear()

    private fun prints() = File(context.cacheDir, "prints").listFiles().orEmpty().map { it.name }

    @Test
    fun theFileIsNamedByTheTitleAsWritten() {
        assertEquals("Лимонный кекс.pdf", PdfShare.fileName("Лимонный кекс", "Untitled recipe"))
        assertEquals("Crème brûlée.pdf", PdfShare.fileName("Crème brûlée", "Untitled recipe"))
        assertEquals("Soup 1_2_ _best_ _ok_.pdf", PdfShare.fileName("Soup 1/2: \"best\" <ok>", "Untitled recipe"))
        assertEquals("a_b_c_d.pdf", PdfShare.fileName("a\\b*c|d", "x"))
        assertEquals("Two lines.pdf", PdfShare.fileName("  Two\n\tlines ", "x"))
        assertEquals("hidden.pdf", PdfShare.fileName("..hidden", "x"))
        assertEquals("Untitled recipe.pdf", PdfShare.fileName("   ", "Untitled recipe"))
        assertEquals("Untitled recipe.pdf", PdfShare.fileName("...", "Untitled recipe"))
        val long = PdfShare.fileName("Щ".repeat(300), "x")
        assertTrue(long.toByteArray().size <= 255)
        assertEquals("Щ".repeat(100) + ".pdf", long)
    }

    @Test
    fun theSharedFileIsAnA4PdfReadableThroughTheProvider() {
        val uri = write("Лимонный кекс")
        assertEquals(RecipeExport.authority(context), uri.authority)
        assertEquals("Лимонный кекс.pdf", displayName(uri))
        assertEquals("application/pdf", context.contentResolver.getType(uri))
        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        assertEquals(listOf(PageSize.A4), sizes)
    }

    @Test
    fun onlyTheLatestFileIsKept() {
        write("Soup")
        write("Pie")
        assertEquals(listOf("Pie.pdf"), prints())
        // The export's own files are another directory's, untouched.
        assertTrue(runCatching { FileProvider.getUriForFile(context, RecipeExport.authority(context), File(context.cacheDir, "exports/a/b.recipefork")) }.isSuccess)
        assertTrue(runCatching { FileProvider.getUriForFile(context, RecipeExport.authority(context), File(context.cacheDir, "other.pdf")) }.isFailure)
    }

    @Test
    fun theShareSheetSendsThePdfReadOnly() {
        val uri = write("Soup")
        val sheet = PdfShare.shareSheet(uri, "Soup")
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        @Suppress("DEPRECATION")
        val send = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // No recipient chosen for the person: the product sends nothing itself.
        assertNull(send.`package`)
        assertNull(send.component)
    }

    @Test
    fun aFailureToWriteChangesNothing() {
        write("Soup")
        val failing = PdfWriter { _, out, _ ->
            out.write(1)
            throw IOException("disk full")
        }
        assertFalse(runBlocking { PdfShare.share(context, document("Pie"), failing) })
        assertEquals(listOf("Soup.pdf"), prints())
        assertArrayEquals(bytes, File(context.cacheDir, "prints/Soup.pdf").readBytes())
        // Nothing half-written is left behind in the cache either.
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("print") && it.isFile })
    }

    @Test
    fun theProviderDeclaresNoExport() {
        val info = context.packageManager.getProviderInfo(ComponentName(context, FileProvider::class.java), 0)
        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
    }
}
