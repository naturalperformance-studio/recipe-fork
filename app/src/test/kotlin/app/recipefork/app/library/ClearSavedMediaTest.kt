package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
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
 * Scenario 5 of the increment, FLOW-060 (4): "Clear saved media" in the book's menu states first what it deletes —
 * in serverless mode that it cannot be fetched again, in the standard build that it is fetched again when needed — and
 * deletes nothing until confirmed; Cancel changes nothing; confirmed, the found recipe's files are gone, with its rows,
 * while a draft keeps its own. With nothing to clear, it says so in place of asking.
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
    fun serverlessWarnsThatTheMediaCannotBeFetchedAgainAndClearsOnlyOnceConfirmed() =
        warnsThenClears(Mode.Serverless, R.string.library_clear_media_statement_serverless)

    @Test
    @Config(qualifiers = "ru")
    fun serverlessWarnsInRussian() = warnsThenClears(Mode.Serverless, R.string.library_clear_media_statement_serverless)

    @Test
    fun theStandardBuildSaysTheMediaIsFetchedAgainAndClearsOnlyOnceConfirmed() =
        warnsThenClears(Mode.Standard, R.string.library_clear_media_statement)

    @Test
    fun withNothingToClearItSaysSoInServerlessMode() = saysNothingToClear(Mode.Serverless)

    @Test
    fun withNothingToClearItSaysSoInTheStandardBuild() = saysNothingToClear(Mode.Standard)

    private fun warnsThenClears(mode: Mode, statement: Int) {
        val found = recipe("Found soup", SentStatus.NEVER, photo("found-cover"), photo("found-step", MediaAttachment.ToStep(0)))
            .inState(RecipeImport.FOUND_STATE)
        val draft = recipe("Draft bread", SentStatus.NEVER, photo("draft-cover"))
        runBlocking {
            assertTrue(recipes.addFound(found, now))
            recipes.save(draft)
        }
        show(mode)

        // Asked, and declined: nothing is deleted.
        chooseClear()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.CLEAR_MEDIA_STATEMENT), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasText(context.getString(statement)), PATIENCE_MS)
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
        compose.waitUntil(PATIENCE_MS) { onDisk() == listOf("draft-cover") }
        runBlocking {
            assertEquals(found.media, recipes.get(found.id)!!.media)
            assertEquals(draft.media, recipes.get(draft.id)!!.media)
            assertTrue(recipes.isMarked(found.id))
        }

        // Now nothing is left to clear, and the action says so.
        chooseClear()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.CLEAR_MEDIA_NOTHING), PATIENCE_MS)
    }

    private fun saysNothingToClear(mode: Mode) {
        runBlocking { recipes.save(recipe("Draft bread", SentStatus.NEVER, photo("draft-cover"))) }
        show(mode)
        chooseClear()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.CLEAR_MEDIA_NOTHING), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasText(context.getString(R.string.library_clear_media_nothing)), PATIENCE_MS)
        assertFalse(exists(LibraryTags.CLEAR_MEDIA_STATEMENT))
        compose.onNodeWithTag(LibraryTags.CLEAR_MEDIA_OK).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.CLEAR_MEDIA_NOTHING), PATIENCE_MS)
        assertEquals(listOf("draft-cover"), onDisk())
    }

    private fun chooseClear() {
        compose.onNodeWithTag(LibraryTags.MENU).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.CLEAR_MEDIA), PATIENCE_MS)
        assertTrue(exists(LibraryTags.CLEAR_MEDIA))
        compose.onNodeWithTag(LibraryTags.CLEAR_MEDIA).performClick()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun show(mode: Mode) {
        val editor = EditorEnvironment(active, device.catalog(context), mode = mode, clock = { now })
        compose.setContent { MaterialTheme { LibraryScreen(session, recipes, mode, editor = editor) } }
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
