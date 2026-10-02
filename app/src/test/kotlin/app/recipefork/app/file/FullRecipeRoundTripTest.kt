package app.recipefork.app.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.cover
import app.recipefork.app.media.stepPhoto
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.RecipeFileCheck
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.Random
import java.util.zip.ZipInputStream

/**
 * The fullest recipe a file can carry — a cover and a photograph on each of the most steps a recipe holds, each at the
 * importer's cap for a photograph — survives export and import whole: its archive stands exactly at the importer's
 * cap on entries, and every photograph arrives byte for byte at its place.
 */
@RunWith(AndroidJUnit4::class)
class FullRecipeRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val friendsBook = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database), context.filesDir)
    private val friend = BookName.OfAccount("round-trip-friend")
    private val friendsRecipes = RecipeRepository(ActiveBook(friend, friendsBook.database), context.filesDir)
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Before
    fun forgetEarlierDirectories() = FileProviderCache.clear()

    @After
    fun close() {
        book.close()
        friendsBook.close()
        store.directory(BookName.Pool).deleteRecursively()
        store.directory(friend).deleteRecursively()
    }

    /** A photograph of the importer's greatest weight: a JPEG's leading bytes, then noise no deflation shrinks. */
    private fun heaviest(mediaId: String, attachment: MediaAttachment, seed: Long): Media {
        val bytes = ByteArray(ArchiveLimits().photo.toInt()).also { Random(seed).nextBytes(it) }
        bytes[0] = 0xFF.toByte(); bytes[1] = 0xD8.toByte(); bytes[2] = 0xFF.toByte()
        store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(bytes)
        return Media(mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    @Test
    fun aCoverAndAPhotoOnEachOfTheMostStepsSurviveExportAndImport() = runBlocking {
        val steps = (0 until RecipeFileCheck.MAX_STEPS).map { Step(it, "Step ${it + 1}.") }
        val media = listOf(heaviest("cover", MediaAttachment.Cover, 0)) +
            steps.map { heaviest("step-${it.position}", MediaAttachment.ToStep(it.position), it.position + 1L) }
        val recipe = Recipe.create(now, null).copy(title = "Feast", steps = steps, media = media)
        recipes.save(recipe)

        val uri = RecipeExport.write(context, recipes, recipe.id)!!

        val names = ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            generateSequence { zip.nextEntry?.name }.toList()
        }
        assertEquals(ArchiveLimits().entries, names.size)
        val outcome = RecipeImport(friendsRecipes, context.filesDir, clock = { now }).import { context.contentResolver.openInputStream(uri)!! }
        assertEquals(ImportOutcome.Added(recipe.id, "Feast"), outcome)
        val imported = friendsRecipes.get(recipe.id)!!
        assertEquals(media.size, imported.media.size)
        assertArrayEquals(store.file(BookName.Pool, "cover").readBytes(), store.file(friend, imported.cover!!.mediaId).readBytes())
        steps.forEach { step ->
            assertArrayEquals(
                store.file(BookName.Pool, "step-${step.position}").readBytes(),
                store.file(friend, imported.stepPhoto(step.position)!!.mediaId).readBytes(),
            )
        }
    }
}
