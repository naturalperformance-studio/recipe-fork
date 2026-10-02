package app.recipefork.app.card

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
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
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * FORK-007 for steps: the owner's fork holds its own copy of each step's photograph, under a new identity, at the same
 * step. FORK-002: a found recipe's fork carries none, and no file is copied for it.
 */
@RunWith(AndroidJUnit4::class)
class ForkStepPhotoTest {
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

    private fun withStepPhotos(): Recipe {
        val media = listOf(0 to "step-0", 2 to "step-2").map { (position, id) ->
            store.file(BookName.Pool, id).apply { parentFile!!.mkdirs() }.writeBytes(id.toByteArray())
            Media(id, null, MediaKind.PHOTO, MediaAttachment.ToStep(position), MediaVisibility.PRIVATE)
        }
        return Recipe.create(now, null).copy(title = "Soup", steps = listOf(Step(0, "A."), Step(1, "B."), Step(2, "C.")), media = media)
    }

    @Test
    fun theOwnersForkCopiesEachStepPhotoUnderANewIdentity() = runBlocking {
        val parent = withStepPhotos().also { recipes.save(it) }

        val fork = recipes.get(forkRecipe(recipes, active, parent.id, now)!!)!!

        listOf(0 to "step-0", 2 to "step-2").forEach { (position, id) ->
            val copy = fork.stepPhoto(position)!!.mediaId
            assertNotEquals(id, copy)
            assertArrayEquals(id.toByteArray(), store.file(BookName.Pool, copy).readBytes())
        }
        assertEquals(null, fork.stepPhoto(1))
        assertEquals(4, store.directory(BookName.Pool).list()!!.size)
    }

    @Test
    fun aFoundRecipesForkCarriesNoStepPhoto() = runBlocking {
        val parent = withStepPhotos().inState(RecipeImport.FOUND_STATE)
        recipes.addFound(parent, now)

        val fork = recipes.get(forkRecipe(recipes, active, parent.id, now)!!)!!

        assertEquals(emptyList<Media>(), fork.media)
        assertEquals(listOf("step-0", "step-2"), store.directory(BookName.Pool).list()!!.sorted())
    }
}
