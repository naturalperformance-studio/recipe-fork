package app.recipefork.app.recipe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * FLOW-060 (2): clearing saved media deletes the files of every found recipe and of every sent own recipe, and never
 * those of an own recipe never sent, nor of one sent with changes not sent; every media row stays.
 */
@RunWith(AndroidJUnit4::class)
class ClearSavedMediaTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val files = File(context.cacheDir, "clear-saved-media").also { it.deleteRecursively() }
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database), files)
    private val store = PhotoStore(files)
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    @After
    fun close() {
        book.close()
        files.deleteRecursively()
    }

    private fun photo(mediaId: String, attachment: MediaAttachment = MediaAttachment.Cover): Media {
        store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(-1, -40, -1, 1))
        return Media(mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    private fun recipe(title: String, sent: SentStatus, vararg media: Media) =
        Recipe.create(now, null).copy(title = title, sent = sent, steps = listOf(Step(0, "Boil.")), media = media.toList())

    private fun onDisk() = store.directory(BookName.Pool).list().orEmpty().sorted()

    @Test
    fun clearingDeletesTheFilesOfFoundAndSentRecipesAndKeepsEveryRow() = runBlocking {
        val found = recipe("Found", SentStatus.NEVER, photo("found-cover"), photo("found-step", MediaAttachment.ToStep(0)))
            .inState(RecipeImport.FOUND_STATE)
        assertTrue(recipes.addFound(found, now))
        val sent = recipe("Sent", SentStatus.SENT, photo("sent-cover"))
        val draft = recipe("Draft", SentStatus.NEVER, photo("draft-cover"))
        val changed = recipe("Changed", SentStatus.SENT_WITH_CHANGES, photo("changed-cover"))
        listOf(sent, draft, changed).forEach { recipes.save(it) }

        assertEquals(
            listOf("found-cover", "found-step", "sent-cover"),
            recipes.clearableMedia().map { it.name }.sorted(),
        )
        assertEquals(3, recipes.clearMedia())

        assertEquals(listOf("changed-cover", "draft-cover"), onDisk())
        // The rows stay: each recipe still names the media it held, so its card can say the media is unavailable.
        assertEquals(found.media, recipes.get(found.id)!!.media)
        assertEquals(sent.media, recipes.get(sent.id)!!.media)
        assertEquals(draft.media, recipes.get(draft.id)!!.media)
        assertEquals(found.title, recipes.get(found.id)!!.title)
        assertTrue(recipes.isMarked(found.id))
        // Nothing is left to clear.
        assertEquals(emptyList<File>(), recipes.clearableMedia())
        assertEquals(0, recipes.clearMedia())
    }

    @Test
    fun aBookOfDraftsAloneHasNothingToClear() = runBlocking {
        recipes.save(recipe("Draft", SentStatus.NEVER, photo("draft-cover")))
        // A found recipe whose files are already gone has nothing to clear either.
        val found = recipe("Found", SentStatus.NEVER, photo("gone")).inState(RecipeImport.FOUND_STATE)
        assertTrue(recipes.addFound(found, now))
        store.file(BookName.Pool, "gone").delete()

        assertEquals(emptyList<File>(), recipes.clearableMedia())
        assertEquals(listOf("draft-cover"), onDisk())
    }
}
