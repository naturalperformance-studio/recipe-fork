package app.recipefork.app.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.cover
import app.recipefork.app.media.stepPhoto
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * MEDIA-012 on the device: deleting an own recipe, or removing a found one, deletes the photograph files it held that
 * nothing else holds — through one path — and a photograph an own fork copied is the fork's, so it outlives the parent.
 */
@RunWith(AndroidJUnit4::class)
class RecipeMediaDeletionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @After
    fun close() {
        book.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    private fun photo(mediaId: String, attachment: MediaAttachment): Media {
        store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(-1, -40, -1, 1, 2, 3))
        return Media(mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    private fun recipe(title: String, vararg media: Media) =
        Recipe.create(now, null).copy(title = title, steps = listOf(Step(0, "Boil."), Step(1, "Serve.")), media = media.toList())

    private fun files() = store.directory(BookName.Pool).list()!!.sorted()

    @Test
    fun deletingAnOwnRecipeDeletesItsPhotoFilesAndNoOthers() = runBlocking {
        val soup = recipe("Soup", photo("soup-cover", MediaAttachment.Cover), photo("soup-step", MediaAttachment.ToStep(1)))
        val bread = recipe("Bread", photo("bread-cover", MediaAttachment.Cover))
        recipes.save(soup)
        recipes.save(bread)

        assertTrue(recipes.delete(soup.id))

        assertEquals(listOf("bread-cover"), files())
        assertFalse(recipes.delete(soup.id))
    }

    @Test
    fun removingAFoundRecipeDeletesItsPhotoFilesAndNoOthers() = runBlocking {
        val found = recipe("Found", photo("found-cover", MediaAttachment.Cover), photo("found-step", MediaAttachment.ToStep(0)))
            .inState(RecipeImport.FOUND_STATE)
        assertTrue(recipes.addFound(found, now))
        recipes.save(recipe("Own", photo("own-cover", MediaAttachment.Cover)))

        assertTrue(RecipeImport(recipes, context.filesDir).remove(found.id))

        assertEquals(listOf("own-cover"), files())
    }

    // MEDIA-012: a photograph a fork copied (FORK-007) is held by the fork under its own identity, so the parent's
    // deletion leaves it — the cover and the step's photograph alike.
    @Test
    fun aPhotoAForkCopiedSurvivesItsParentsDeletion() = runBlocking {
        val parent = recipe("Soup", photo("p-cover", MediaAttachment.Cover), photo("p-step", MediaAttachment.ToStep(1)))
        recipes.save(parent)
        val forkId = forkRecipe(recipes, active, parent.id, now)!!
        val fork = recipes.get(forkId)!!
        val cover = store.file(BookName.Pool, fork.cover!!.mediaId)
        val step = store.file(BookName.Pool, fork.stepPhoto(1)!!.mediaId)
        val coverBytes = cover.readBytes()

        assertTrue(recipes.delete(parent.id))

        assertEquals(listOf(cover.name, step.name).sorted(), files())
        assertArrayEquals(coverBytes, cover.readBytes())
    }
}
