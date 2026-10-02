package app.recipefork.app.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.AppContainer
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * MEDIA-012 in a batch: opening a book sweeps its media directory of files no recipe holds and of half-written `.part`
 * files, once they are older than an attachment, a fork or an import in flight could be; what a recipe holds stays.
 */
@RunWith(AndroidJUnit4::class)
class MediaSweepTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val open = ActiveBook.Open(BookName.Pool, book.database)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-02T12:00:00Z").toEpochMilli()
    private val sweep = MediaSweep(context.filesDir, now = { now })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun close() {
        scope.cancel()
        book.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    private fun file(name: String, ageMinutes: Int): File =
        File(store.directory(BookName.Pool).apply { mkdirs() }, name).apply {
            writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(setLastModified(now - ageMinutes.minutes.inWholeMilliseconds))
        }

    private fun holding(vararg ids: String) = runBlocking {
        recipes.save(
            Recipe.create(Instant.ofEpochMilli(now), null).copy(
                title = "Soup",
                media = ids.map { Media(it, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE) },
            ),
        )
    }

    private fun left() = store.directory(BookName.Pool).list()!!.sorted()

    @Test
    fun filesNoRecipeHoldsAndHalfWrittenFilesOlderThanTenMinutesAreSwept() = runBlocking {
        holding("held")
        file("held", ageMinutes = 600)
        file("orphan", ageMinutes = 11)
        file(".orphan.part", ageMinutes = 11)
        file("young-orphan", ageMinutes = 9)
        file(".young.part", ageMinutes = 9)

        val swept = sweep.sweep(open)

        assertEquals(listOf(".orphan.part", "orphan"), swept.map { it.name }.sorted())
        assertEquals(listOf(".young.part", "held", "young-orphan"), left())
    }

    // An attachment or import in flight writes its file before the book holds it: a fresh file is never swept.
    @Test
    fun theAgeIsTenMinutes() {
        assertEquals(10.minutes, MediaSweep.AGE)
    }

    @Test
    fun aBookWithNoMediaDirectorySweepsNothing() = runBlocking {
        assertEquals(emptyList<File>(), sweep.sweep(open))
        assertFalse(store.directory(BookName.Pool).exists())
    }

    // Once per book opened: the active book as the sweep starts, then each book as it is opened.
    @Test
    fun eachBookIsSweptAsItIsOpened() = runBlocking {
        file("pool-orphan", ageMinutes = 60)
        val other = BookName.OfAccount("sweep-other")
        val otherBook = TestBook(context)
        try {
            File(store.directory(other).apply { mkdirs() }, "other-orphan").apply {
                writeBytes(byteArrayOf(1))
                setLastModified(now - 60.minutes.inWholeMilliseconds)
            }
            val books = MutableStateFlow(open)
            sweep.everyOpened(books, scope)
            withTimeout(10_000) { while (left().isNotEmpty()) yield() }
            assertTrue(store.file(other, "other-orphan").exists())

            books.value = ActiveBook.Open(other, otherBook.database)
            withTimeout(10_000) { while (store.file(other, "other-orphan").exists()) yield() }
        } finally {
            otherBook.close()
            store.directory(other).deleteRecursively()
        }
    }

    // The application sweeps the active book as it starts, through its composition root.
    @Test
    fun theCompositionRootSweepsTheActiveBook() = runBlocking {
        val container = AppContainer(context, Mode.Serverless)
        File(store.directory(BookName.Pool).apply { mkdirs() }, "left-over").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(System.currentTimeMillis() - 60.minutes.inWholeMilliseconds)
        }
        container.sweepMedia(scope)
        withTimeout(10_000) { while (left().isNotEmpty()) yield() }
    }
}
