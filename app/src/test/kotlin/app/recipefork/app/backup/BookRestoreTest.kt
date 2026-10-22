package app.recipefork.app.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.book.CollectionEntity
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.RecipeRows
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.ImportedCollection
import app.recipefork.app.file.TestRecipeFiles.jpeg
import app.recipefork.app.file.TestRecipeFiles.mp4
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeCollection
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
    private val sourceCollections = CollectionRepository(source) { now }

    private val bookName = BookName.OfAccount("restore-book")
    private val bookDatabase = BookDatabase.open(context, bookName)
    private val book = ActiveBook(bookName, bookDatabase)
    private val bookRecipes = RecipeRepository(book, context.filesDir)
    private val bookCollections = CollectionRepository(book) { now.plusSeconds(60) }

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

    private class Saved(
        val own: RecipeId,
        val found: RecipeId,
        val fork: RecipeId,
        val orphan: RecipeId,
        val gone: RecipeId,
        val menu: CollectionId,
        val annas: CollectionId,
    ) {
        val all get() = listOf(own, found, fork, orphan)
    }

    /**
     * The saved book: an own recipe with a cover and a step's clip, a found one with a photograph, a fork of the own
     * one, and a fork whose parent the book no longer holds; an own collection of three of them, and an imported one of
     * two.
     */
    private fun fill(): Saved = runBlocking {
        val own = Recipe.create(now, null).copy(
            title = "Soup", state = RecipeState.PRIVATE, steps = listOf(Step(0, "Boil."), Step(1, "Fold.")),
            media = listOf(
                media(sourceName, OWN_COVER, MediaKind.PHOTO, MediaAttachment.Cover, jpeg(3_000)),
                media(sourceName, OWN_CLIP, MediaKind.VIDEO, MediaAttachment.ToStep(1), mp4(9_000).also { it[5_000] = 7 }),
            ),
        )
        sourceRecipes.save(own)
        val found = Recipe.create(now, null).copy(
            title = "Found pie", statedAuthor = "anna", state = RecipeState.LINK_SHARED,
            media = listOf(media(sourceName, FOUND_COVER, MediaKind.PHOTO, MediaAttachment.Cover, jpeg(2_000).also { it[100] = 3 })),
        )
        assertTrue(sourceRecipes.addFound(found, now))
        val fork = forkRecipe(sourceRecipes, source, own.id, now)!!
        val gone = Recipe.create(now, null).copy(title = "Gone")
        sourceRecipes.save(gone)
        val orphan = forkRecipe(sourceRecipes, source, gone.id, now)!!
        assertTrue(sourceRecipes.delete(gone.id))
        val menu = sourceCollections.create("New Year's menu")!!
        listOf(fork, own.id, found.id).forEach { assertEquals(setOf(menu), sourceCollections.addTo(it, listOf(menu))) }
        val annas = CollectionId.mint()
        assertEquals(ImportedCollection.CREATED, sourceCollections.putImported(annas, "Anna's pies", "anna", listOf(found.id, orphan)))
        Saved(own.id, found.id, fork, orphan, gone.id, menu, annas)
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

    /**
     * Everything the book holds: each recipe's rows, its marks, its media files with their bytes, and its collections
     * with the recipes of each in order.
     */
    private data class Held(
        val rows: Map<String, RecipeRows>,
        val marks: List<FavouriteEntity>,
        val files: Map<String, List<Byte>>,
        val collections: Map<CollectionEntity, List<String>>,
    )

    private fun held(active: ActiveBook, name: BookName): Held = runBlocking {
        val recipes = active.database.recipes()
        Held(
            recipes.ids().associateWith { recipes.get(it)!! },
            active.database.favourites().list(),
            store.directory(name).listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readBytes().toList() },
            active.database.collections().let { dao -> dao.all().associateWith { dao.members(it.collectionId) } },
        )
    }

    /** A recipe of the book's own, not in the file, with a photograph. */
    private fun ownOfTheBook(): RecipeId = runBlocking {
        val recipe = Recipe.create(now, null).copy(
            title = "The book's own", media = listOf(media(bookName, BOOK_PHOTO, MediaKind.PHOTO, MediaAttachment.Cover, jpeg(1_000))),
        )
        bookRecipes.save(recipe)
        recipe.id
    }

    // v0.39, T-279: a saved book carries the text each recipe was made from, exactly, and its restore brings it back.
    @Test
    fun aSavedBookCarriesTheTextARecipeWasMadeFrom() = runBlocking {
        val pasted = "Блины\n\n  Мука — 200 г\r\nМолоко 500 мл\t(тёплое)\n" + "ж".repeat(19_000)
        val recipe = Recipe.create(now, null).copy(title = "Блины", steps = listOf(Step(0, "Жарить."))).withSourceText(pasted)
        sourceRecipes.save(recipe)
        val plain = Recipe.create(now, null).copy(title = "Plain")
        sourceRecipes.save(plain)
        assertEquals(Restored(added = 2, alreadyHeld = 0), restore(RestoreMode.ADD))
        assertEquals(pasted, bookRecipes.get(recipe.id)!!.sourceText)
        assertEquals(recipe, bookRecipes.get(recipe.id))
        assertNull(bookRecipes.get(plain.id)!!.sourceText)
    }

    // v0.44, T-318: a saved book carries what each recipe's finished dish weighs, and its restore brings it back.
    @Test
    fun aSavedBookCarriesTheFinishedWeight() = runBlocking {
        val recipe = Recipe.create(now, null).copy(title = "Pilaf", servings = 2, steps = listOf(Step(0, "Simmer.")), finishedWeightGrams = 560)
        sourceRecipes.save(recipe)
        val plain = Recipe.create(now, null).copy(title = "Plain")
        sourceRecipes.save(plain)
        assertEquals(Restored(added = 2, alreadyHeld = 0), restore(RestoreMode.ADD))
        assertEquals(560, bookRecipes.get(recipe.id)!!.finishedWeightGrams)
        assertEquals(recipe, bookRecipes.get(recipe.id))
        assertNull(bookRecipes.get(plain.id)!!.finishedWeightGrams)
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
                media = listOf(media(bookName, OLD_COVER, MediaKind.PHOTO, MediaAttachment.Cover, jpeg(1_500))),
            ),
        )

        assertEquals(Restored(added = 4, alreadyHeld = 0), restore(RestoreMode.REPLACE))

        assertEquals(held(source, sourceName), held(book, bookName))
        assertFalse(store.file(bookName, BOOK_PHOTO).exists())
        assertFalse(store.file(bookName, OLD_COVER).exists())
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
        // The last file copied — the media are copied in the order of their names — cannot be written: a directory no file can replace stands where it is written aside.
        File(store.directory(bookName), ".$OWN_COVER.part/blocked").apply { parentFile!!.mkdirs() }.writeText("x")
        val before = held(book, bookName)
        try {
            restore(RestoreMode.REPLACE)
            fail("the restore did not fail")
        } catch (e: IOException) {
            // Expected.
        }
        assertEquals(before, held(book, bookName))
        assertEquals(setOf(".$OWN_COVER.part", BOOK_PHOTO), store.directory(bookName).list()!!.toSet())
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

    // Every media identity of the file is one this application makes: anything else — a climb out of the media
    // directory, an absolute path, a path at all — refuses the whole restore before anything is written.
    @Test
    fun aMediaIdentityThatIsNoPlainNameRefusesTheFile() {
        fill()
        ownOfTheBook()
        val outside = File(context.filesDir, "book.db")
        listOf("../book.db", "/book.db", "sub/book.db", "..", ".hidden").forEach { id ->
            val crafted = Recipe.create(now, null).copy(
                title = "Crafted", media = listOf(Media(id, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)),
            )
            runBlocking { sourceRecipes.save(crafted) }
            val before = held(book, bookName)
            RestoreMode.entries.forEach { mode ->
                try {
                    restore(mode)
                    fail("$id was restored by $mode")
                } catch (e: RestoreRefused) {
                    assertEquals(BookFileRefusal.DAMAGED, e.refusal)
                }
                assertEquals(before, held(book, bookName))
                assertEquals(listOf(BOOK_PHOTO), store.directory(bookName).list()!!.toList())
                assertFalse(outside.exists())
                assertEquals(setOf(7L), kitchen.held.value)
            }
            // Its row alone is deleted: the files of the source's media are no concern here.
            runBlocking { assertTrue(sourceDatabase.recipes().delete(crafted.id.value)) }
        }
    }

    // A file of the book's own already where a media file is to go is kept as it is — never written over, and never
    // deleted when the restore fails.
    @Test
    fun aFileAlreadyInPlaceIsKept() {
        fill()
        val orphan = store.file(bookName, OWN_COVER).apply { parentFile!!.mkdirs() }.also { it.writeText("the book's own bytes") }
        try {
            restore(RestoreMode.ADD, written = { if (it == 2) throw IllegalStateException("injected") })
            fail("the restore did not fail")
        } catch (e: IllegalStateException) {
            // Expected.
        }
        assertEquals("the book's own bytes", orphan.readText())
        assertEquals(listOf(OWN_COVER), store.directory(bookName).list()!!.toList())
        assertEquals(Restored(added = 4, alreadyHeld = 0), restore(RestoreMode.ADD))
        assertEquals("the book's own bytes", orphan.readText())
        assertTrue(store.file(bookName, OWN_CLIP).isFile)
    }

    @Test
    fun addIntoAnEmptyBookBringsTheFilesCollectionsBack() = runBlocking {
        val saved = fill()

        restore(RestoreMode.ADD)

        assertEquals(
            RecipeCollection(saved.menu, "New Year's menu", CollectionOrigin.OWN, null, now, listOf(saved.fork, saved.own, saved.found)),
            bookCollections.get(saved.menu),
        )
        assertEquals(
            RecipeCollection(saved.annas, "Anna's pies", CollectionOrigin.IMPORTED, "anna", now, listOf(saved.found, saved.orphan)),
            bookCollections.get(saved.annas),
        )
        assertEquals(held(source, sourceName).collections, held(book, bookName).collections)
    }

    // Add gives the book each collection it lacks, and each of the file's it holds the places it lacks, after its own,
    // under its own name; a collection the file does not carry is left as it is.
    @Test
    fun addGivesTheBookTheCollectionsAndPlacesItLacks() = runBlocking {
        val saved = fill()
        val mine = ownOfTheBook()
        bookRecipes.save(sourceRecipes.get(saved.own)!!.copy(media = emptyList()))
        bookRecipes.save(sourceRecipes.get(saved.orphan)!!)
        val kept = bookCollections.create("The book's own")!!
        bookCollections.addTo(mine, listOf(kept))
        bookCollections.addTo(saved.own, listOf(kept))
        // The book holds the file's own collection already, renamed, with a recipe the file's does not hold.
        bookDatabase.collections().insert(CollectionEntity(saved.menu.value, "Menu", CollectionOrigin.OWN, null, now.plusSeconds(5)))
        bookCollections.addTo(mine, listOf(saved.menu))
        bookCollections.addTo(saved.own, listOf(saved.menu))
        // And the file's imported one, holding part of what the file puts in it.
        assertEquals(ImportedCollection.CREATED, bookCollections.putImported(saved.annas, "Pies", null, listOf(saved.orphan)))
        val keptBefore = bookCollections.get(kept)

        assertEquals(Restored(added = 2, alreadyHeld = 2), restore(RestoreMode.ADD))

        assertEquals(keptBefore, bookCollections.get(kept))
        assertEquals(
            RecipeCollection(saved.menu, "Menu", CollectionOrigin.OWN, null, now.plusSeconds(5), listOf(mine, saved.own, saved.fork, saved.found)),
            bookCollections.get(saved.menu),
        )
        assertEquals(
            RecipeCollection(saved.annas, "Pies", CollectionOrigin.IMPORTED, null, now.plusSeconds(60), listOf(saved.orphan, saved.found)),
            bookCollections.get(saved.annas),
        )
        assertEquals(3, bookDatabase.collections().all().size)
    }

    @Test
    fun replaceMakesTheBooksCollectionsExactlyTheFiles() = runBlocking {
        val saved = fill()
        val mine = ownOfTheBook()
        bookRecipes.save(sourceRecipes.get(saved.own)!!.copy(media = emptyList()))
        val gone = bookCollections.create("The book's own")!!
        bookCollections.addTo(mine, listOf(gone))
        bookDatabase.collections().insert(CollectionEntity(saved.menu.value, "Menu", CollectionOrigin.OWN, null, now.plusSeconds(5)))
        bookCollections.addTo(saved.own, listOf(saved.menu))

        restore(RestoreMode.REPLACE)

        assertNull(bookCollections.get(gone))
        assertEquals(
            RecipeCollection(saved.menu, "New Year's menu", CollectionOrigin.OWN, null, now, listOf(saved.fork, saved.own, saved.found)),
            bookCollections.get(saved.menu),
        )
        assertEquals(held(source, sourceName).collections, held(book, bookName).collections)
    }

    private companion object {
        // Media identities are UUIDs, as the application makes them; the file's sort after any a fork makes.
        const val FOUND_COVER = "ffffffff-0000-4000-8000-000000000001"
        const val OWN_CLIP = "ffffffff-0000-4000-8000-000000000002"
        const val OWN_COVER = "ffffffff-0000-4000-8000-000000000003"
        const val BOOK_PHOTO = "b0000000-0000-4000-8000-000000000001"
        const val OLD_COVER = "b0000000-0000-4000-8000-000000000002"
    }
}
