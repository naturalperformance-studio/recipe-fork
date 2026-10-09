package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.waitUntilWithMainThread
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * Scenario 5 of the increment, FLOW-060 (4): "Clear saved media" in the settings shows how much the device holds that
 * it would clear, and states first what it deletes —
 * in serverless mode that it cannot be fetched again, in the standard build that it is fetched again when needed — and
 * deletes nothing until confirmed; Cancel changes nothing; confirmed, the found recipe's files are gone, with its rows,
 * while a draft keeps its own. With nothing to clear, it says so in place of asking.
 *
 * Scenario 6 of v0.26: in serverless mode all the device can clear is the photos and videos of recipes imported from
 * files — no recipe is sent there — and the item, its statement and its message of nothing to clear say so in those
 * words; the standard build keeps "saved media".
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ClearSavedMediaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val files = File(context.cacheDir, "clear-saved-media-screen").also { it.deleteRecursively() }
    private val recipes = RecipeRepository(active, files)
    private val store = PhotoStore(files)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
        files.deleteRecursively()
    }

    @Test
    fun serverlessWarnsThatTheMediaCannotBeFetchedAgainAndClearsOnlyOnceConfirmed() = warnsThenClears(
        Mode.Serverless,
        Wording(
            item = "Clear photos and videos of imported recipes",
            title = "Clear photos and videos of imported recipes?",
            statement = "The photos and videos of the recipes you imported from files are deleted from this device. " +
                "To have them again, remove the recipe and import its file again. Every recipe keeps its text, and the " +
                "recipes you wrote here keep their photos and videos.",
            nothing = "The recipes you imported have no photos or videos to clear.",
        ),
    )

    @Test
    @Config(qualifiers = "ru")
    fun serverlessWarnsInRussian() = warnsThenClears(
        Mode.Serverless,
        Wording(
            item = "Очистить фото и видео импортированных рецептов",
            title = "Очистить фото и видео импортированных рецептов?",
            statement = "Фото и видео рецептов, импортированных из файлов, удалятся с этого устройства. Чтобы вернуть их, " +
                "уберите рецепт и импортируйте его файл снова. Текст каждого рецепта останется, а написанные здесь " +
                "рецепты сохранят свои фото и видео.",
            nothing = "У импортированных рецептов нет фото и видео, которые можно очистить.",
        ),
    )

    @Test
    fun theStandardBuildSaysTheMediaIsFetchedAgainAndClearsOnlyOnceConfirmed() = warnsThenClears(
        Mode.Standard,
        Wording(
            item = "Clear saved media",
            title = "Clear saved media?",
            statement = context.getString(R.string.library_clear_media_statement),
            nothing = "There is no saved media to clear.",
        ),
    )

    @Test
    fun withNothingToClearItSaysSoInServerlessMode() =
        saysNothingToClear(Mode.Serverless, "The recipes you imported have no photos or videos to clear.")

    @Test
    @Config(qualifiers = "ru")
    fun withNothingToClearItSaysSoInServerlessModeInRussian() =
        saysNothingToClear(Mode.Serverless, "У импортированных рецептов нет фото и видео, которые можно очистить.")

    @Test
    fun withNothingToClearItSaysSoInTheStandardBuild() = saysNothingToClear(Mode.Standard, "There is no saved media to clear.")

    /** What the item, its statement's title and text, and its message of nothing to clear say. */
    private data class Wording(val item: String, val title: String, val statement: String, val nothing: String)

    private fun warnsThenClears(mode: Mode, wording: Wording) {
        val found = recipe("Found soup", SentStatus.NEVER, photo("found-cover"), photo("found-step", MediaAttachment.ToStep(0)))
            .inState(RecipeImport.FOUND_STATE)
        val draft = recipe("Draft bread", SentStatus.NEVER, photo("draft-cover"))
        runBlocking {
            assertTrue(recipes.addFound(found, now))
            recipes.save(draft)
        }
        show(mode)

        // The item, named for what it clears, shows the found recipe's two files as what it would clear.
        openSettings()
        awaitValue(context.resources.getQuantityString(R.plurals.settings_media_count, 2, 2))
        compose.onNodeWithTag(SettingsTags.CLEAR_MEDIA).assert(hasText(wording.item))

        // Asked, and declined: nothing is deleted.
        chooseClear()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.CLEAR_MEDIA_STATEMENT), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasText(wording.statement), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.CLEAR_MEDIA_STATEMENT).assert(hasAnyDescendant(hasText(wording.title)))
        assertEquals(listOf("draft-cover", "found-cover", "found-step"), onDisk())
        compose.onNodeWithTag(LibraryTags.CLEAR_MEDIA_DECLINE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.CLEAR_MEDIA_STATEMENT), PATIENCE_MS)
        compose.waitForIdle()
        assertEquals(listOf("draft-cover", "found-cover", "found-step"), onDisk())

        // Asked again, and confirmed: the found recipe's files go, its rows stay, the draft keeps its own.
        chooseClear()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.CLEAR_MEDIA_STATEMENT), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.CLEAR_MEDIA_CONFIRM).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.CLEAR_MEDIA_STATEMENT), PATIENCE_MS)
        // The disk is read, not the screen: the clearing resumes on the main thread, which is run meanwhile.
        compose.waitUntilWithMainThread { onDisk() == listOf("draft-cover") }
        awaitValue(context.getString(R.string.settings_media_none))
        runBlocking {
            assertEquals(found.media, recipes.get(found.id)!!.media)
            assertEquals(draft.media, recipes.get(draft.id)!!.media)
            assertTrue(recipes.isMarked(found.id))
        }

        // Now nothing is left to clear, and the action says so.
        chooseClear()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.CLEAR_MEDIA_NOTHING), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasText(wording.nothing), PATIENCE_MS)
    }

    private fun saysNothingToClear(mode: Mode, said: String) {
        runBlocking { recipes.save(recipe("Draft bread", SentStatus.NEVER, photo("draft-cover"))) }
        show(mode)
        chooseClear()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.CLEAR_MEDIA_NOTHING), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasText(said), PATIENCE_MS)
        assertFalse(exists(LibraryTags.CLEAR_MEDIA_STATEMENT))
        compose.onNodeWithTag(LibraryTags.CLEAR_MEDIA_OK).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.CLEAR_MEDIA_NOTHING), PATIENCE_MS)
        assertEquals(listOf("draft-cover"), onDisk())
    }

    private fun openSettings() {
        if (exists(SettingsTags.SCREEN)) return
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(SettingsTags.CLEAR_MEDIA), PATIENCE_MS)
    }

    private fun awaitValue(value: String) = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(hasTestTag(SettingsTags.value(SettingsTags.CLEAR_MEDIA)) and hasText(value), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun chooseClear() {
        openSettings()
        assertTrue(exists(SettingsTags.CLEAR_MEDIA))
        compose.onNodeWithTag(SettingsTags.CLEAR_MEDIA).performScrollTo().performClick()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun show(mode: Mode) {
        val editor = EditorEnvironment(active, device.catalog(context), mode = mode, clock = { now })
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, mode, editor = editor) } }
        compose.waitForIdle()
    }

    private fun photo(mediaId: String, attachment: MediaAttachment = MediaAttachment.Cover): Media {
        store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(-1, -40, -1, 1))
        return Media(mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    private fun recipe(title: String, sent: SentStatus, vararg media: Media) =
        Recipe.create(now, null).copy(title = title, sent = sent, steps = listOf(Step(0, "Boil.")), media = media.toList())

    private fun onDisk() = store.directory(BookName.Pool).list().orEmpty().sorted()
}
