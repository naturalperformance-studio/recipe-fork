package app.recipefork.app.file

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardScreen
import app.recipefork.app.card.CardTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.cover
import app.recipefork.app.media.stepPhoto
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * FLOW-063 with media: export writes every photograph the device holds for the recipe into `media/`, byte for byte as
 * the device holds it, and the document names each; imported on another device, the photographs show at the cover and
 * at their steps. A photograph whose file is gone is left out of the file and of the card, and alarms nobody.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportWithPhotosTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val friendsBook = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database), context.filesDir)
    private val friend = BookName.OfAccount("friend")
    private val friendsRecipes = RecipeRepository(ActiveBook(friend, friendsBook.database), context.filesDir)
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Before
    fun setUp() {
        FileProviderCache.clear()
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(friendsBook.idling)
        compose.registerIdlingResource(device.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(friendsBook.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        friendsBook.close()
        device.close()
        store.directory(BookName.Pool).deleteRecursively()
        store.directory(friend).deleteRecursively()
    }

    private fun photo(mediaId: String, attachment: MediaAttachment, write: Boolean = true): Media {
        if (write) TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 1600, 1200)
        return Media(mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    private fun borscht(vararg media: Media) = Recipe.create(now, null).copy(
        title = "Borscht",
        steps = listOf(Step(0, "Boil."), Step(1, "Fry."), Step(2, "")),
        media = media.toList(),
    ).also { runBlocking { recipes.save(it) } }

    private fun export(recipe: Recipe): Uri = runBlocking { RecipeExport.write(context, recipes, recipe.id) }!!

    private fun entries(uri: Uri): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun document(uri: Uri) = RecipeFile.decode(entries(uri).getValue(RecipeFile.DOCUMENT).toString(Charsets.UTF_8))

    private fun card(recipes: RecipeRepository, id: RecipeId) {
        compose.setContent { MaterialTheme { CardScreen(id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.TITLE), PATIENCE_MS)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    // FLOW-063: every photograph the recipe holds is in media/ as the device holds it, and the document names each.
    @Test
    fun exportWritesEveryPhotoAsTheDeviceHoldsItAndTheDocumentNamesEach() {
        val recipe = borscht(photo("c-1", MediaAttachment.Cover), photo("s-1", MediaAttachment.ToStep(1)))

        val uri = export(recipe)

        val entries = entries(uri)
        assertEquals(listOf("recipe.json", "media/", "media/c-1", "media/s-1"), entries.keys.toList())
        assertArrayEquals(store.file(BookName.Pool, "c-1").readBytes(), entries.getValue("media/c-1"))
        assertArrayEquals(store.file(BookName.Pool, "s-1").readBytes(), entries.getValue("media/s-1"))
        assertEquals(
            listOf(
                FileMedia("c-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/c-1"),
                FileMedia("s-1", MediaKind.PHOTO, MediaAttachment.ToStep(1), "media/s-1"),
            ),
            document(uri).media,
        )
        // The file the exporting device writes is one its own importer believes.
        assertEquals(document(uri), RecipeArchiveReader.document({ context.contentResolver.openInputStream(uri)!! }))
    }

    // FLOW-063, FLOW-064 end to end: exported with photos and imported into another device's book, the recipe's card
    // shows the cover and the step's photograph — held there under that book's own names.
    @Test
    fun aFileExportedWithPhotosShowsThemOnceImportedOnAnotherDevice() {
        val recipe = borscht(photo("c-1", MediaAttachment.Cover), photo("s-1", MediaAttachment.ToStep(1)))
        val uri = export(recipe)
        // The other device holds nothing of this one's files.
        assertTrue(store.directory(BookName.Pool).deleteRecursively())

        val outcome = runBlocking {
            RecipeImport(friendsRecipes, context.filesDir, clock = { now }).import { context.contentResolver.openInputStream(uri)!! }
        }

        assertEquals(ImportOutcome.Added(recipe.id, "Borscht"), outcome)
        val imported = runBlocking { friendsRecipes.get(recipe.id) }!!
        assertTrue(store.file(friend, imported.cover!!.mediaId).isFile)
        assertTrue(store.file(friend, imported.stepPhoto(1)!!.mediaId).isFile)
        card(friendsRecipes, recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.COVER), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(1)), PATIENCE_MS)
        assertEquals(listOf("Cover photo of “Borscht”"), compose.descriptionOf(CardTags.COVER))
        assertEquals(listOf("Photo of step 2"), compose.descriptionOf(CardTags.stepPhoto(1)))
    }

    // A photograph whose file is gone: the recipe still opens, and its file is exported without it — the document
    // does not name it — and is still one an importer takes.
    @Test
    fun aPhotoWhoseFileIsMissingIsLeftOutOfTheExport() {
        val recipe = borscht(photo("c-gone", MediaAttachment.Cover, write = false), photo("s-1", MediaAttachment.ToStep(1)))

        val uri = export(recipe)

        assertEquals(listOf("recipe.json", "media/", "media/s-1"), entries(uri).keys.toList())
        assertEquals(listOf("s-1"), document(uri).media.map { it.mediaId })
        val outcome = runBlocking {
            RecipeImport(friendsRecipes, context.filesDir, clock = { now }).import { context.contentResolver.openInputStream(uri)!! }
        }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        // The exported recipe is unchanged by the export: it still names its photograph.
        assertEquals(recipe, runBlocking { recipes.get(recipe.id) })
    }

    @Test
    fun aRecipeWhosePhotoFilesAreMissingStillOpens() {
        val recipe = borscht(photo("c-gone", MediaAttachment.Cover, write = false), photo("s-gone", MediaAttachment.ToStep(0), write = false))
        card(recipes, recipe.id)
        compose.waitForIdle()
        assertFalse(exists(CardTags.COVER))
        assertFalse(exists(CardTags.stepPhoto(0)))
        assertTrue(exists(CardTags.step(0)))
    }

    @Test
    fun theExportOfARecipeWithNoPhotoFileWritesAnEmptyMediaDirectory() {
        val recipe = borscht(photo("c-gone", MediaAttachment.Cover, write = false))
        val uri = export(recipe)
        assertEquals(listOf("recipe.json", "media/"), entries(uri).keys.toList())
        assertTrue(document(uri).media.isEmpty())
        assertFalse(File(context.cacheDir, "exports").walkTopDown().none { it.isFile })
    }
}
