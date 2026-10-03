package app.recipefork.app.file

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.file.TestRecipeFiles.file
import app.recipefork.app.file.TestRecipeFiles.mp4
import app.recipefork.app.file.TestRecipeFiles.soup
import app.recipefork.app.media.ClipDuration
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.RetrieverClipDuration
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.FileMedia
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A step's clip travels as a photograph does: an own fork carries it under its own identity, so it outlives its parent
 * (FORK-007, MEDIA-012); a recipe file carries it, so export, deletion and import bring it back (FLOW-063, FLOW-064).
 * An importer holds a clip to MEDIA-004's length before it believes the recipe (MEDIA-007): longer, or of a length
 * that cannot be read, refuses the file, says the limit, and keeps nothing.
 */
@RunWith(AndroidJUnit4::class)
class StepClipTravelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Before
    fun forgetEarlierDirectories() = FileProviderCache.clear()

    @After
    fun close() {
        book.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    private fun clip(mediaId: String, position: Int): Media {
        store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(mp4(4_000).also { it[3000] = 7 })
        return Media(mediaId, null, MediaKind.VIDEO, MediaAttachment.ToStep(position), MediaVisibility.PRIVATE)
    }

    private fun withClip(): Recipe = Recipe.create(now, null)
        .copy(title = "Soup", steps = listOf(Step(0, "Boil."), Step(1, "Fold.")), media = listOf(clip("p-clip", 1)))

    private fun files() = store.directory(BookName.Pool).list()!!.sorted()

    private fun importer(duration: ClipDuration) = RecipeImport(recipes, context.filesDir, clock = { now }, clipDuration = duration)

    private fun importClip(duration: Duration?): ImportOutcome = runBlocking {
        val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/fold.mp4")))
        importer { duration }.import { ByteArrayInputStream(file(content, "media/fold.mp4" to mp4(3_000))) }
    }

    private fun assertNothingAdded() = runBlocking {
        assertEquals(0, recipes.count())
        assertEquals(emptyList<Any>(), recipes.marked().first())
        assertEquals(emptyList<String>(), store.directory(BookName.Pool).list()?.toList().orEmpty())
    }

    // FORK-007, MEDIA-012: the owner's fork carries the clip under its own identity; deleting the parent leaves it.
    @Test
    fun anOwnForksClipSurvivesItsParentsDeletion() = runBlocking {
        val parent = withClip()
        recipes.save(parent)
        val bytes = store.file(BookName.Pool, "p-clip").readBytes()

        val fork = recipes.get(forkRecipe(recipes, active, parent.id, now)!!)!!
        assertTrue(recipes.delete(parent.id))

        val carried = fork.stepMedia(1)!!
        assertEquals(MediaKind.VIDEO, carried.kind)
        assertTrue(carried.mediaId != "p-clip")
        assertEquals(listOf(carried.mediaId), files())
        assertArrayEquals(bytes, store.file(BookName.Pool, carried.mediaId).readBytes())
    }

    // FLOW-063, FLOW-064, FORK-007: a recipe with a clip, exported, deleted and imported, holds the clip at its step,
    // byte for byte.
    @Test
    fun aClipSurvivesExportDeletionAndImport() = runBlocking {
        val recipe = withClip()
        recipes.save(recipe)
        val bytes = store.file(BookName.Pool, "p-clip").readBytes()
        val uri = RecipeExport.write(context, recipes, recipe.id)!!
        assertTrue(recipes.delete(recipe.id))
        assertEquals(emptyList<String>(), files())

        val outcome = importer(TestRecipeFiles.SHORT_CLIP).import { context.contentResolver.openInputStream(uri)!! }

        assertEquals(ImportOutcome.Added(recipe.id, "Soup"), outcome)
        val imported = recipes.get(recipe.id)!!
        val clip = imported.stepMedia(1)!!
        assertEquals(MediaKind.VIDEO, clip.kind)
        assertNull(imported.stepMedia(0))
        assertArrayEquals(bytes, store.file(BookName.Pool, clip.mediaId).readBytes())
    }

    // MEDIA-004, MEDIA-007: a clip at the limit is accepted; one past it, or of no readable length, refuses the file
    // and nothing of it is kept.
    @Test
    fun aClipIsHeldToItsLengthAtImport() {
        assertEquals(ImportOutcome.Refused(Refusal.CLIP_TOO_LONG), importClip(VideoLimits.MAX_DURATION + 1.milliseconds))
        assertNothingAdded()
        assertEquals(ImportOutcome.Refused(Refusal.CLIP_TOO_LONG), importClip(40.seconds))
        assertNothingAdded()
        assertEquals(ImportOutcome.Refused(Refusal.DAMAGED), importClip(null))
        assertNothingAdded()
        assertEquals(ImportOutcome.Refused(Refusal.DAMAGED), importClip(Duration.ZERO))
        assertNothingAdded()
        assertTrue(importClip(15.seconds) is ImportOutcome.Added)
    }

    // A photograph is never held to a length: the duration is read of clips alone.
    @Test
    fun aPhotographIsNotAskedItsLength() = runBlocking {
        val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/c.jpg")))
        val outcome = importer { error("asked the length of a photograph") }
            .import { ByteArrayInputStream(file(content, "media/c.jpg" to TestRecipeFiles.jpeg())) }
        assertTrue(outcome is ImportOutcome.Added)
    }

    // MEDIA-007: the refusal states the limit, in the import's own wording.
    @Test
    fun aLongClipsRefusalStatesTheLimit() = assertLimitStated("A clip in this recipe file is longer than 15 seconds, so nothing was added")

    @Test
    @Config(qualifiers = "ru")
    fun aLongClipsRefusalStatesTheLimitInRussian() = assertLimitStated("Видео в этом файле рецепта длиннее 15 секунд, ничего не добавлено")

    private fun assertLimitStated(expected: String) {
        val message = ImportOutcome.Refused(Refusal.CLIP_TOO_LONG).message()
        assertEquals(FileMessage.Kind.CLIP_TOO_LONG, message.kind)
        assertEquals(expected, message.text(context.resources))
        assertEquals(message, FileMessage.decoded(message.encoded()))
        assertEquals(context.getString(R.string.file_import_clip_too_long, 15), expected)
    }

    // On the device, a clip's length is read by the media framework from the file; a file it cannot read states none.
    @Test
    fun theDeviceReadsAClipsLengthFromItsFile() {
        val known = File(context.cacheDir, "known.mp4").apply { writeBytes(mp4()) }
        ShadowMediaMetadataRetriever.addMetadata(
            DataSource.toDataSource(known.absolutePath), MediaMetadataRetriever.METADATA_KEY_DURATION, "15000",
        )
        val unknown = File(context.cacheDir, "unknown.mp4").apply { writeBytes(mp4()) }

        assertEquals(15.seconds, RetrieverClipDuration.of(known))
        assertNull(RetrieverClipDuration.of(unknown))
        known.delete()
        unknown.delete()
    }
}
