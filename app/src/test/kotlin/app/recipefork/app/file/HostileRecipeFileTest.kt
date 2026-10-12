package app.recipefork.app.file

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.TestRecipeFiles.file
import app.recipefork.app.file.TestRecipeFiles.jpeg
import app.recipefork.app.file.TestRecipeFiles.lying
import app.recipefork.app.file.TestRecipeFiles.soup
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.time.Instant
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * decisions/20260928-recipe-file-is-a-zip.md — the importer trusts nothing: a file from anyone is read only as far as a
 * recipe needs, stopped at its caps and when the import is cancelled, refused when it holds two documents or a document
 * that is not UTF-8, never read from the application's own data, and added once, to the book the import began in.
 */
@RunWith(AndroidJUnit4::class)
class HostileRecipeFileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val files = File(context.cacheDir, "hostile-test-files").also { it.deleteRecursively() }
    private val importer = RecipeImport(recipes, files, clock = { now })
    private val mediaDirectory = RecipeImport.mediaDirectory(files, BookName.Pool)
    private val incoming = TestRecipeFiles.incoming()

    @After
    fun close() {
        book.close()
        files.deleteRecursively()
        incoming.deleteRecursively()
    }

    private fun import(bytes: ByteArray, limits: ArchiveLimits = ArchiveLimits()) = runBlocking {
        RecipeImport(recipes, files, clock = { now }, limits = limits).import { ByteArrayInputStream(bytes) }
    }

    private fun assertNothingAdded() = runBlocking {
        assertEquals(0, recipes.count())
        assertEquals(emptyList<Any>(), recipes.marked().first())
        assertEquals(emptyList<String>(), mediaDirectory.list()?.toList().orEmpty())
    }

    private fun assertRefused(refusal: Refusal, bytes: ByteArray, limits: ArchiveLimits = ArchiveLimits()) {
        assertEquals(ImportOutcome.Refused(refusal), import(bytes, limits))
        assertNothingAdded()
    }

    private fun refusalOf(block: () -> Unit): String? = (runCatching(block).exceptionOrNull() as? RefusedFile)?.message

    /** Bytes that do not compress: an entry of them is as large in the archive as inflated. */
    private fun noise(size: Int, seed: Long = 7) = ByteArray(size).also { Random(seed).nextBytes(it) }

    /** [bytes] read through a stream that counts what was read from it into [read], and tells [onRead] each time. */
    private class Counting(bytes: ByteArray, val read: AtomicLong = AtomicLong(), val onRead: (Long) -> Unit = {}) :
        FilterInputStream(ByteArrayInputStream(bytes)) {
        override fun read(): Int = super.read().also { if (it >= 0) onRead(read.incrementAndGet()) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) onRead(read.addAndGet(it.toLong())) }
    }

    // A recipe needs at most its document, the media directory, and an item for each media slot: the cover and one
    // per step. One entry more refuses the file before that entry is read.
    @Test
    fun moreEntriesThanARecipeNeedsIsRefused() {
        val cap = ArchiveLimits().entries
        assertEquals(2 + 1 + 50, cap)
        val most = file(soup(), *Array(cap - 2) { "notes/$it" to ByteArray(1) })
        assertTrue(import(most) is ImportOutcome.Added)
        runBlocking { importer.remove(soup().id) }
        val tooMany = file(soup(), *Array(cap - 1) { "notes/$it" to ByteArray(1) })
        assertEquals("more entries than a recipe needs", refusalOf { RecipeArchiveReader.document({ ByteArrayInputStream(tooMany) }) })
        assertRefused(Refusal.DAMAGED, tooMany)
    }

    // Once every media item the document names has been read, nothing more of the archive is.
    @Test
    fun mediaReadingStopsAtTheLastItemNamed() {
        val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg")))
        val trailing = 3 * ArchiveLimits.MIB.toInt()
        val bytes = file(content, "media/cover.jpg" to jpeg(), "notes/after.bin" to noise(trailing))
        val read = AtomicLong()
        RecipeArchiveReader.media({ Counting(bytes, read) }, content, target = { File(incoming, it.mediaId) })
        assertTrue("read ${read.get()} of ${bytes.size}", read.get() < trailing / 2)
    }

    // An import cancelled while it copies stops reading at once, and keeps nothing of the file.
    @Test
    fun aCancelledImportStopsReading() = runBlocking {
        val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.VIDEO, MediaAttachment.ToStep(0), "media/boil.mp4")))
        val video = noise(5 * ArchiveLimits.MIB.toInt()).also { TestRecipeFiles.mp4(12).copyInto(it) }
        val bytes = file(content, "media/boil.mp4" to video)
        val opens = AtomicInteger()
        val mediaRead = AtomicLong()
        lateinit var work: Deferred<ImportOutcome>
        work = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            importer.import {
                // The document is read first; the import is cancelled while the video is being copied.
                if (opens.incrementAndGet() == 1) ByteArrayInputStream(bytes)
                else Counting(bytes, mediaRead) { if (it > 256 * 1024) work.cancel() }
            }
        }
        work.start()
        val thrown = runCatching { work.await() }.exceptionOrNull()
        assertTrue("$thrown", thrown is CancellationException)
        assertEquals(2, opens.get())
        assertTrue("read ${mediaRead.get()} of ${bytes.size}", mediaRead.get() < ArchiveLimits.MIB)
        assertNothingAdded()
    }

    // An ignored entry that inflates past the archive's cap while declaring a few bytes is stopped at the cap.
    @Test
    fun anIgnoredEntryLyingAboutItsSizeIsStoppedAtTheCap() {
        val limits = ArchiveLimits(archive = 64 * 1024)
        val big = noise(4 * ArchiveLimits.MIB.toInt())
        val document = TestRecipeFiles.document(soup())
        val bytes = lying(RecipeFile.DOCUMENT, document, document.size) + lying("notes/big.bin", big, declaredSize = 10)
        val read = AtomicLong()
        assertEquals("entry over its cap", refusalOf { RecipeArchiveReader.document({ Counting(bytes, read) }, limits) })
        assertTrue("read ${read.get()} of ${bytes.size}", read.get() < 256 * 1024)
        assertRefused(Refusal.DAMAGED, bytes, limits)
    }

    // Tags are numbers, and a set of them is held many times larger than the text that lists them: a recipe file of more
    // distinct tags than a recipe carries — real ones, no padding — is refused as breaking the rules; the most is taken.
    @Test
    fun aRecipeFileOfManyDistinctTagsIsRefused() {
        val many = TestRecipeFiles.document(soup().copy(tags = (0L until 40_000L).toSet()))
        assertTrue("within the document's cap: ${many.size}", many.size < ArchiveLimits().document)
        val bytes = TestRecipeFiles.zip(RecipeFile.DOCUMENT to many)
        assertEquals("more than 1000 tags", refusalOf { RecipeArchiveReader.document({ ByteArrayInputStream(bytes) }) })
        assertRefused(Refusal.BREAKS_RULES, bytes)
        assertRefused(Refusal.BREAKS_RULES, file(soup().copy(tags = (0L until 1_001L).toSet())))
        assertTrue(import(file(soup().copy(tags = (0L until 1_000L).toSet()))) is ImportOutcome.Added)
    }

    // A file holding two documents cannot say which recipe it is.
    @Test
    fun twoDocumentsAreRefused() {
        val first = TestRecipeFiles.document(soup())
        val second = TestRecipeFiles.document(soup().copy(title = "Other soup"))
        val bytes = lying(RecipeFile.DOCUMENT, first, first.size) + lying(RecipeFile.DOCUMENT, second, second.size)
        assertEquals("two documents", refusalOf { RecipeArchiveReader.document({ ByteArrayInputStream(bytes) }) })
        assertRefused(Refusal.DAMAGED, bytes)
    }

    // A document that parses as JSON once its bad bytes are replaced is still not UTF-8, and is refused.
    @Test
    fun aDocumentThatIsNotUtf8IsRefusedThoughItParses() {
        val parts = RecipeFile.encode(soup()).split("Bean soup")
        assertEquals(2, parts.size)
        val document = parts[0].toByteArray() + "Bean ".toByteArray() + byteArrayOf(0xFF.toByte()) + "soup".toByteArray() +
            parts[1].toByteArray()
        assertEquals("the document is not UTF-8", refusalOf {
            RecipeArchiveReader.document({ ByteArrayInputStream(TestRecipeFiles.zip(RecipeFile.DOCUMENT to document)) })
        })
        assertRefused(Refusal.DAMAGED, TestRecipeFiles.zip(RecipeFile.DOCUMENT to document))
    }

    // Two imports of one recipe racing each other: the book's store adds it once, and tells the other it held it.
    @Test
    fun aRecipeAddedTwiceAtOnceIsAddedOnce() = runBlocking {
        val recipe = RecipeImport.foundRecipe(soup(), emptyList(), now)
        val added = List(2) { async(Dispatchers.IO) { recipes.addFound(recipe, now) } }.awaitAll()
        assertEquals(listOf(false, true), added.sorted())
        assertEquals(1, recipes.count())
        assertEquals(1, recipes.marked().first().size)
    }

    // A `file:` address into the application's own data — however spelt — is refused unread; one outside is read.
    @Test
    fun aFileInTheApplicationsOwnDataIsRefused() = runBlocking {
        val bytes = file(soup())
        val own = listOf(
            File(context.filesDir, "a.recipefork"),
            File(context.cacheDir, "a.recipefork"),
            File(context.cacheDir, "exports/recipe/Soup.recipefork"),
            context.getDatabasePath(BookName.Pool.fileName),
            File(context.dataDir, "shared_prefs/a.recipefork"),
        )
        own.forEach { target ->
            TestRecipeFiles.write(target.parentFile!!, target.name, bytes)
            assertEquals("$target", ImportOutcome.Refused(Refusal.CANNOT_OPEN), importer.import(context, Uri.fromFile(target)))
            target.delete()
        }
        // Climbing into it from outside is the same address.
        val inside = TestRecipeFiles.write(context.filesDir, "b.recipefork", bytes)
        val up = "../".repeat(incoming.canonicalPath.count { it == '/' })
        val climbing = Uri.parse("file://" + incoming.canonicalPath + "/" + up + inside.canonicalPath.removePrefix("/"))
        assertEquals("$climbing", ImportOutcome.Refused(Refusal.CANNOT_OPEN), importer.import(context, climbing))
        inside.delete()
        assertNothingAdded()
        val outside = TestRecipeFiles.write(incoming, "Bean soup.recipefork", bytes)
        assertFalse(RecipeImport.isPrivateFile(context, Uri.fromFile(outside)))
        assertTrue(importer.import(context, Uri.fromFile(outside)) is ImportOutcome.Added)
    }

    // The media and the rows go to the book open when the import began, though another becomes active meanwhile.
    @Test
    fun anImportKeepsToTheBookItBeganIn() = runBlocking {
        val other = TestBook(context)
        try {
            val open = MutableStateFlow(ActiveBook.Open(BookName.Pool, book.database))
            val switching = RecipeImport(RecipeRepository(ActiveBook(open)), files, clock = { now })
            val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg")))
            val bytes = file(content, "media/cover.jpg" to jpeg())
            val outcome = switching.import {
                open.value = ActiveBook.Open(BookName.OfAccount("other"), other.database)
                ByteArrayInputStream(bytes)
            }
            assertTrue("$outcome", outcome is ImportOutcome.Added)
            val found = recipes.get(content.id)
            assertNotNull(found)
            assertEquals(listOf(found!!.media.single().mediaId), mediaDirectory.list()!!.toList())
            val otherBook = RecipeRepository(ActiveBook(BookName.OfAccount("other"), other.database))
            assertNull(otherBook.get(content.id))
            assertEquals(0, otherBook.count())
            assertFalse(RecipeImport.mediaDirectory(files, BookName.OfAccount("other")).exists())
        } finally {
            other.close()
        }
    }
}
