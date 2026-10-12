package app.recipefork.app.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookMigrationTest
import app.recipefork.app.book.BookName
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.file.TestRecipeFiles.jpeg
import app.recipefork.app.file.TestRecipeFiles.mp4
import app.recipefork.app.kitchen.KitchenJson
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

/**
 * The whole book saved to a file and opened from it (FLOW-073; decisions/20261012-a-book-file-holds-the-books-database.md):
 * the opened copy holds the book exactly; an older book is migrated; a file that is no saved book, a damaged one and
 * one from a newer application are each refused for what they are, leaving nothing behind; a cancelled save writes
 * nothing.
 */
@RunWith(AndroidJUnit4::class)
class BookFileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = BookName.OfAccount("book-file-test")
    private val database = BookDatabase.open(context, name)
    private val active = ActiveBook(name, database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-12T09:30:00Z")
    private val unpacked = File(context.cacheDir, "opened-books")
    private val kitchen = setOf(10001L, 10203L)

    @After
    fun delete() {
        database.close()
        context.deleteDatabase(name.fileName)
        store.directory(name).deleteRecursively()
    }

    private fun media(id: String, kind: MediaKind, attachment: MediaAttachment, bytes: ByteArray): Media {
        store.file(name, id).apply { parentFile!!.mkdirs() }.writeBytes(bytes)
        return Media(id, null, kind, attachment, MediaVisibility.PRIVATE)
    }

    /** A book of an own recipe with a cover and a step's clip, a found one with a photograph, and a fork of the own one. */
    private fun fill(): List<RecipeId> = runBlocking {
        val own = Recipe.create(now, null).copy(
            title = "Soup", state = RecipeState.PRIVATE, steps = listOf(Step(0, "Boil."), Step(1, "Fold.")),
            media = listOf(
                media("own-cover", MediaKind.PHOTO, MediaAttachment.Cover, jpeg(3_000)),
                media("own-clip", MediaKind.VIDEO, MediaAttachment.ToStep(1), mp4(9_000).also { it[5_000] = 7 }),
            ),
        )
        recipes.save(own)
        val found = Recipe.create(now, null).copy(
            title = "Found pie", statedAuthor = "anna", state = RecipeState.LINK_SHARED,
            media = listOf(media("found-cover", MediaKind.PHOTO, MediaAttachment.Cover, jpeg(2_000).also { it[100] = 3 })),
        )
        assertTrue(recipes.addFound(found, now))
        listOf(own.id, found.id, forkRecipe(recipes, active, own.id, now)!!)
    }

    private fun save(progress: (Long, Long) -> Unit = { _, _ -> }): Pair<BookManifest, ByteArray> = runBlocking {
        val out = ByteArrayOutputStream()
        BookFileWriter(context, clock = { now }).write(active.open.value, kitchen, out, progress) to out.toByteArray()
    }

    private fun open(bytes: ByteArray): BookFileOpening = runBlocking { BookFileReader(context).open(ByteArrayInputStream(bytes)) }

    private fun assertNothingUnpacked() = assertEquals(emptyList<String>(), unpacked.list()?.toList().orEmpty())

    @Test
    fun aBookComesBackFromItsFileExactly() = runBlocking {
        val (own, found, fork) = fill()
        // What was written last is in the book's write-ahead log yet, which the copy must carry.
        assertTrue(File(context.getDatabasePath(name.fileName).path + "-wal").length() > 0)
        val (manifest, file) = save()
        assertEquals(BookManifest(appVersion = manifest.appVersion, savedAt = now, recipes = 3, media = 5), manifest)

        val opened = (open(file) as BookFileOpening.Opened).book
        opened.use {
            assertEquals(manifest, it.manifest)
            val copy = RecipeRepository(ActiveBook(BookName.OfAccount("opened"), it.database))
            listOf(own, found, fork).forEach { id -> assertEquals(recipes.get(id), copy.get(id)) }
            assertEquals(RecipeState.PRIVATE, copy.get(own)!!.state)
            assertTrue(copy.isMarked(found))
            assertFalse(copy.isMarked(own))
            assertEquals(own, copy.get(fork)!!.parent)
            val files = store.directory(name).list()!!.sorted()
            assertEquals(5, files.size)
            assertEquals(files, it.mediaDirectory.list()!!.sorted())
            files.forEach { media -> assertArrayEquals(store.file(name, media).readBytes(), File(it.mediaDirectory, media).readBytes()) }
            assertEquals(kitchen, it.kitchen)
        }
        assertNothingUnpacked()
    }

    // The copy is the book as it was when the save began; the book is written to meanwhile, and keeps what was written.
    @Test
    fun theBookIsWrittenToWhileItIsSaved() = runBlocking {
        fill()
        val later = Recipe.create(now, null).copy(title = "Written meanwhile")
        var written = false
        val (_, file) = save { _, _ ->
            if (!written) runBlocking { recipes.save(later) }
            written = true
        }
        assertEquals(later, recipes.get(later.id))
        (open(file) as BookFileOpening.Opened).book.use {
            assertEquals(3, it.database.recipes().count())
            assertNull(RecipeRepository(ActiveBook(name, it.database)).get(later.id))
        }
    }

    @Test
    fun aBookOfThePreviousSchemaOpensMigrated() = runBlocking {
        val opened = (open(version7File()) as BookFileOpening.Opened).book
        opened.use {
            assertEquals(BookDatabase.VERSION, it.database.openHelper.readableDatabase.version)
            val pancakes = RecipeRepository(ActiveBook(name, it.database)).get(RecipeId("r7"))!!
            assertEquals("Pancakes", pancakes.title)
            assertEquals(listOf(null, null), pancakes.steps.map { step -> step.note })
            assertEquals(listOf("m7"), it.mediaDirectory.list()!!.toList())
        }
        assertNothingUnpacked()
    }

    // Nothing is written outside the directory unpacked into, whatever the archive names.
    @Test
    fun anEntryClimbingOutIsNeverWritten() = runBlocking {
        val escaped = listOf("../escaped", "media/../../escaped", "/escaped")
        (open(version7File(escaped.map { it to jpeg() })) as BookFileOpening.Opened).book.use {
            assertEquals(listOf("m7"), it.mediaDirectory.list()!!.toList())
        }
        assertFalse(File(context.cacheDir, "escaped").exists())
        assertFalse(File(unpacked, "escaped").exists())
        assertNothingUnpacked()
    }

    @Test
    fun aFileThatIsNoSavedBookIsRefusedAsSuch() {
        assertEquals(refused(BookFileRefusal.NOT_A_BOOK_FILE), open("Not a zip at all, only text".toByteArray()))
        assertNothingUnpacked()
        val recipeFile = TestRecipeFiles.file(TestRecipeFiles.soup())
        assertEquals(refused(BookFileRefusal.NOT_A_BOOK_FILE), open(recipeFile))
        assertEquals(refused(BookFileRefusal.NOT_A_BOOK_FILE), open(ByteArray(0)))
        assertNothingUnpacked()
    }

    @Test
    fun aFileCutShortIsRefusedAsDamaged() {
        fill()
        val (_, file) = save()
        listOf(file.size / 4, file.size / 2, file.size * 3 / 4).forEach { size ->
            assertEquals(refused(BookFileRefusal.DAMAGED), open(file.copyOf(size)))
            assertNothingUnpacked()
        }
        assertEquals(refused(BookFileRefusal.DAMAGED), open(version7File(kitchen = null)))
        assertEquals(refused(BookFileRefusal.DAMAGED), open(version7File(manifest = version7Manifest.copy(media = 2))))
        assertNothingUnpacked()
    }

    @Test
    fun aFileFromANewerApplicationIsRefusedAsSuch() {
        val laterFormat = version7Manifest.encode().replace("\"format\":1", "\"format\":2,\"anything\":\"new\"")
        assertEquals(refused(BookFileRefusal.NEWER), open(version7File(manifestText = laterFormat)))
        assertEquals(
            refused(BookFileRefusal.NEWER),
            open(version7File(manifest = version7Manifest.copy(bookSchema = BookDatabase.VERSION + 1))),
        )
        assertNothingUnpacked()
    }

    // A cancelled save leaves the place chosen untouched and nothing of its work behind.
    @Test
    fun aCancelledSaveWritesNothing() = runBlocking {
        fill()
        val out = ByteArrayOutputStream()
        val saving = launch(Dispatchers.IO) {
            BookFileWriter(context).write(active.open.value, kitchen, out) { _, _ -> cancel() }
        }
        saving.join()
        assertTrue(saving.isCancelled)
        assertEquals(0, out.size())
        assertFalse(File(context.cacheDir, "saving-book").exists())
    }

    @Test
    fun aCancelledOpenLeavesNothingUnpacked() = runBlocking {
        val file = version7File()
        val opening = launch(Dispatchers.IO) { BookFileReader(context).open(ByteArrayInputStream(file)) { cancel() } }
        opening.join()
        assertTrue(opening.isCancelled)
        assertNothingUnpacked()
    }

    private fun refused(refusal: BookFileRefusal) = BookFileOpening.Refused(refusal)

    private val version7Manifest = BookManifest(bookSchema = 7, appVersion = "0.33", savedAt = now, recipes = 1, media = 1)

    /**
     * A saved book of schema 7, written by hand: one own recipe of two steps with a photograph, and [kitchen]; [extra]
     * entries follow the rest.
     */
    private fun version7File(
        extra: List<Pair<String, ByteArray>> = emptyList(),
        manifest: BookManifest = version7Manifest,
        manifestText: String = manifest.encode(),
        kitchen: Set<Long>? = this.kitchen,
    ): ByteArray {
        val file = File(context.cacheDir, "version-7.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            BookMigrationTest.VERSION_7.forEach(db::execSQL)
            db.execSQL(
                "insert into recipe values ('r7', null, null, 'NEVER', null, null, null, 'Pancakes', 'Thin', 'EN', 3, " +
                    "'LOCAL', 0, 0, 0, 1000, 6000)",
            )
            db.execSQL("insert into step values ('r7', 0, 'Whisk')")
            db.execSQL("insert into step values ('r7', 1, 'Fry thin')")
            db.execSQL("insert into media values ('m7', 'r7', 0, null, 'PHOTO', 1, 'PRIVATE')")
            db.version = 7
        }
        val entries = listOfNotNull(
            BookFile.MANIFEST to manifestText.toByteArray(),
            BookFile.DATABASE to file.readBytes(),
            BookFile.MEDIA + "m7" to jpeg(),
            kitchen?.let { BookFile.KITCHEN to KitchenJson.encode(it).toByteArray() },
        ) + extra
        return TestRecipeFiles.zip(*entries.toTypedArray()).also { file.delete() }
    }
}
