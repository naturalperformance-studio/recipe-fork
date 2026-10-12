package app.recipefork.app.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.RecipeRows
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.file.TestRecipeFiles.jpeg
import app.recipefork.app.file.TestRecipeFiles.mp4
import app.recipefork.app.kitchen.HeldKitchen
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * A saved book restored into the active book (FLOW-073; decisions/20261012-a-book-file-holds-the-books-database.md):
 * Add copies what the book does not hold and touches nothing it holds; Replace makes the book exactly the file's; a
 * failure or a cancel before the transaction leaves the book and its media as they were.
 */
@RunWith(AndroidJUnit4::class)
class BookRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-12T09:30:00Z")

    private val sourceName = BookName.OfAccount("restore-source")
    private val sourceDatabase = BookDatabase.open(context, sourceName)
    private val source = ActiveBook(sourceName, sourceDatabase)
    private val sourceRecipes = RecipeRepository(source, context.filesDir)

    private val bookName = BookName.OfAccount("restore-book")
    private val bookDatabase = BookDatabase.open(context, bookName)
    private val book = ActiveBook(bookName, bookDatabase)
    private val bookRecipes = RecipeRepository(book, context.filesDir)

    private val savedKitchen = setOf(10001L, 10203L)
    private val kitchen = HeldKitchen(setOf(7L))

    @After
    fun delete() {
        listOf(sourceDatabase to sourceName, bookDatabase to bookName).forEach { (database, name) ->
            database.close()
            context.deleteDatabase(name.fileName)
            store.directory(name).deleteRecursively()
        }
    }

    private fun media(name: BookName, id: String, kind: MediaKind, attachment: MediaAttachment, bytes: ByteArray): Media {
        store.file(name, id).apply { parentFile!!.mkdirs() }.writeBytes(bytes)
        return Media(id, null, kind, attachment, MediaVisibility.PRIVATE)
    }

    private class Saved(val own: RecipeId, val found: RecipeId, val fork: RecipeId, val orphan: RecipeId, val gone: RecipeId) {
        val all get() = listOf(own, found, fork, orphan)
    }

    /**
     * The saved book: an own recipe with a cover and a step's clip, a found one with a photograph, a fork of the own
     * one, and a fork whose parent the book no longer holds.
     */
    private fun fill(): Saved = runBlocking {
        val own = Recipe.create(now, null).copy(
            title = "Soup", state = RecipeState.PRIVATE, steps = listOf(Step(0, "Boil."), Step(1, "Fold.")),
            media = listOf(
                media(sourceName, "own-cover", MediaKind.PHOTO, MediaAttachment.Cover, jpeg(3_000)),
                media(sourceName, "own-clip", MediaKind.VIDEO, MediaAttachment.ToStep(1), mp4(9_000).also { it[5_000] = 7 }),
            ),
        )
        sourceRecipes.save(own)
        val found = Recipe.create(now, null).copy(
            title = "Found pie", statedAuthor = "anna", state = RecipeState.LINK_SHARED,
            media = listOf(media(sourceName, "found-cover", MediaKind.PHOTO, MediaAttachment.Cover, jpeg(2_000).also { it[100] = 3 })),
        )
        assertTrue(sourceRecipes.addFound(found, now))
        val fork = forkRecipe(sourceRecipes, source, own.id, now)!!
        val gone = Recipe.create(now, null).copy(title = "Gone")
        sourceRecipes.save(gone)
        val orphan = forkRecipe(sourceRecipes, source, gone.id, now)!!
        assertTrue(sourceRecipes.delete(gone.id))
        Saved(own.id, found.id, fork, orphan, gone.id)
    }

    /** Saves the source book and opens its file. */
    private fun opened(): OpenedBookFile = runBlocking {
        val out = ByteArrayOutputStream()
        BookFileWriter(context, clock = { now }).write(source.open.value, savedKitchen, out)
        (BookFileReader(context).open(ByteArrayInputStream(out.toByteArray())) as BookFileOpening.Opened).book
    }

    private fun restore(
        mode: RestoreMode,
        written: (Int) -> Unit = {},
        progress: (Long, Long) -> Unit = { _, _ -> },
    ): Restored = runBlocking {
        opened().use { BookRestore(context.filesDir, kitchen, written).restore(it, book.open.value, mode, progress) }
    }

    /** Everything the book holds: each recipe's rows, its marks, and its media files with their bytes. */
    private data class Held(val rows: Map<String, RecipeRows>, val marks: List<FavouriteEntity>, val files: Map<String, List<Byte>>)

    private fun held(active: ActiveBook, name: BookName): Held = runBlocking {
        val recipes = active.database.recipes()
        Held(
            recipes.ids().associateWith { recipes.get(it)!! },
            active.database.favourites().list(),
            store.directory(name).listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readBytes().toList() },
        )
    }

    /** A recipe of the book's own, not in the file, with a photograph. */
    private fun ownOfTheBook(): RecipeId = runBlocking {
        val recipe = Recipe.create(now, null).copy(
            title = "The book's own", media = listOf(media(bookName, "book-photo", MediaKind.PHOTO, MediaAttachment.Cover, jpeg(1_000))),
        )
        bookRecipes.save(recipe)
        recipe.id
    }

    @Test
    fun addIntoAnEmptyBookCopiesEveryRecipeExactly() = runBlocking {
        val saved = fill()
        var told = 0L to 0L
        assertEquals(Restored(added = 4, alreadyHeld = 0), restore(RestoreMode.ADD) { done, total -> told = done to total })

        assertEquals(held(source, sourceName), held(book, bookName))
        val own = bookRecipes.get(saved.own)!!
        assertTrue(own.state.editable)
        assertFalse(bookRecipes.isMarked(saved.own))
        assertTrue(bookRecipes.isMarked(saved.found))
        assertFalse(bookRecipes.get(saved.found)!!.state.editable)
        assertEquals(saved.own, bookRecipes.get(saved.fork)!!.parent)
        // A fork whose parent is in neither book still names it.
        assertNull(bookRecipes.get(saved.gone))
        assertEquals(saved.gone, bookRecipes.get(saved.orphan)!!.parent)
        assertEquals(sourceRecipes.get(saved.orphan), bookRecipes.get(saved.orphan))
        assertEquals(setOf(7L) + savedKitchen, kitchen.held.value)
        val bytes = store.directory(sourceName).listFiles()!!.sumOf { it.length() }
        assertEquals(bytes to bytes, told)
    }

    @Test
    fun addIntoABookSharingSomeTouchesNothingItHolds() = runBlocking {
        val saved = fill()
        val mine = ownOfTheBook()
        // The book holds the own recipe already, changed since the file was saved, and with none of its media.
        bookRecipes.save(sourceRecipes.get(saved.own)!!.copy(title = "Soup, changed", media = emptyList()))
        // It holds the found one too, as its own.
        bookRecipes.save(sourceRecipes.get(saved.found)!!.copy(media = emptyList(), state = RecipeState.LOCAL))
        val before = held(book, bookName)

        assertEquals(Restored(added = 2, alreadyHeld = 2), restore(RestoreMode.ADD))

        val after = held(book, bookName)
        listOf(mine.value, saved.own.value, saved.found.value).forEach { assertEquals(before.rows[it], after.rows[it]) }
        assertFalse(bookRecipes.isMarked(saved.found))
        val file = held(source, sourceName)
        listOf(saved.fork, saved.orphan).forEach { assertEquals(file.rows[it.value], after.rows[it.value]) }
        // Only the media of what was added came in; nothing of the book's went.
        val added = file.rows.filterKeys { it in listOf(saved.fork.value, saved.orphan.value) }.values.flatMap { it.media }.map { it.mediaId }
        assertEquals(before.files + file.files.filterKeys { it in added }, after.files)
        assertEquals(setOf(7L) + savedKitchen, kitchen.held.value)
    }

    @Test
    fun replaceMakesTheBookExactlyTheFiles() = runBlocking {
        val saved = fill()
        ownOfTheBook()
        // The book's own recipe of the file's identity holds a photograph the file's does not.
        bookRecipes.save(
            sourceRecipes.get(saved.own)!!.copy(
                title = "Soup, changed",
                media = listOf(media(bookName, "old-cover", MediaKind.PHOTO, MediaAttachment.Cover, jpeg(1_500))),
            ),
        )

        assertEquals(Restored(added = 4, alreadyHeld = 0), restore(RestoreMode.REPLACE))

        assertEquals(held(source, sourceName), held(book, bookName))
        assertFalse(store.file(bookName, "book-photo").exists())
        assertFalse(store.file(bookName, "old-cover").exists())
        assertTrue(bookRecipes.get(saved.own)!!.state.editable)
        assertTrue(bookRecipes.isMarked(saved.found))
        assertEquals(savedKitchen, kitchen.held.value)
    }

    @Test
    fun aFailureInsideTheTransactionLeavesTheBookAsItWas() {
        val saved = fill()
        ownOfTheBook()
        runBlocking { bookRecipes.save(sourceRecipes.get(saved.own)!!.copy(title = "Soup, changed", media = emptyList())) }
        val before = held(book, bookName)
        RestoreMode.entries.forEach { mode ->
            try {
                restore(mode, written = { if (it == 2) throw IllegalStateException("injected") })
                fail("$mode did not fail")
            } catch (e: IllegalStateException) {
                assertEquals("injected", e.message)
            }
            assertEquals(before, held(book, bookName))
            assertEquals(setOf(7L), kitchen.held.value)
        }
    }

    @Test
    fun aFailureWhileMediaIsCopiedLeavesTheBookAsItWas() {
        fill()
        ownOfTheBook()
        // The last file copied cannot be written: a directory no file can replace stands where it is written aside.
        File(store.directory(bookName), ".own-cover.part/blocked").apply { parentFile!!.mkdirs() }.writeText("x")
        val before = held(book, bookName)
        try {
            restore(RestoreMode.REPLACE)
            fail("the restore did not fail")
        } catch (e: IOException) {
            // Expected.
        }
        assertEquals(before, held(book, bookName))
        assertEquals(setOf(".own-cover.part", "book-photo"), store.directory(bookName).list()!!.toSet())
    }

    @Test
    fun aCancelBeforeTheTransactionChangesNothing() = runBlocking {
        fill()
        ownOfTheBook()
        val before = held(book, bookName)
        val file = opened()
        file.use {
            val restoring = launch(Dispatchers.IO) {
                BookRestore(context.filesDir, kitchen).restore(it, book.open.value, RestoreMode.REPLACE) { done, _ -> if (done > 0) cancel() }
            }
            restoring.join()
            assertTrue(restoring.isCancelled)
        }
        assertEquals(before, held(book, bookName))
        assertEquals(setOf(7L), kitchen.held.value)
    }

    // Once the transaction has begun, a cancel does not stop it: the book is restored and the caller told so.
    @Test
    fun aCancelInsideTheTransactionLetsItFinish() = runBlocking {
        fill()
        var restored: Restored? = null
        lateinit var restoring: Job
        opened().use { file ->
            restoring = launch(Dispatchers.IO) {
                restored = BookRestore(context.filesDir, kitchen, written = { restoring.cancel() })
                    .restore(file, book.open.value, RestoreMode.ADD)
            }
            restoring.join()
        }
        assertEquals(Restored(added = 4, alreadyHeld = 0), restored)
        assertEquals(held(source, sourceName), held(book, bookName))
    }
}
