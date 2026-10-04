package app.recipefork.app.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeExport
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.file.awaitFileMessage
import app.recipefork.app.file.awaitFileNotice
import app.recipefork.app.file.message
import app.recipefork.app.media.ClipDuration
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.file.FileMedia
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Scenario 4 of v0.19: importing a file shows its progress in place, and afterwards a snackbar offers Open while the
 * list is brought to the imported recipe; a recipe held already is offered too; exporting shows progress as well; a
 * failure still says why, in its dialog with its one way out.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FileProgressTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private var now = Instant.parse("2026-10-02T12:00:00Z")
    private val files = File(context.cacheDir, "progress-test-files").also { it.deleteRecursively() }
    private val incoming = TestRecipeFiles.incoming()

    /** A clip's duration read only once [reading] is let go: the import stands still meanwhile, its file read. */
    private val reading = CountDownLatch(1)
    private val heldClip = ClipDuration {
        reading.await(PATIENCE_MS, TimeUnit.MILLISECONDS)
        10.seconds
    }

    // Each recipe added a minute after the one before, so that the newest is listed first.
    private val importer = RecipeImport(recipes, files, clock = { now.also { now = now.plusSeconds(60) } }, clipDuration = heldClip)

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        FileProviderCache.clear()
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        reading.countDown()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
        files.deleteRecursively()
        incoming.deleteRecursively()
    }

    private fun found(count: Int): List<RecipeId> = (0 until count).map { i ->
        val content = TestRecipeFiles.soup(id = "found-$i")
        val outcome = runBlocking { importer.import { TestRecipeFiles.file(content).inputStream() } }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        content.id
    }

    private fun show() {
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            MaterialTheme { app.recipefork.app.ui.AppNavHost(session, recipes, Mode.Serverless, editor = editor, card = card, files = importer) }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun progress() = compose.onAllNodes(hasTestTag(LibraryTags.FILE_PROGRESS)).fetchSemanticsNodes()

    private fun dialogs() = compose.onAllNodes(isDialog()).fetchSemanticsNodes().size

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** Picks [file] in the document picker the import action opens. */
    private fun pick(file: File) {
        compose.onNodeWithTag(LibraryTags.IMPORT).performClick()
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val started = activity.nextStartedActivityForResult
        compose.runOnUiThread { activity.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
    }

    /** A recipe file holding a step's clip, whose duration is read once the file has been read through. */
    private fun fileWithAClip(id: String, title: String): File {
        val content = TestRecipeFiles.soup(id = id).copy(
            title = title,
            media = listOf(FileMedia("m-1", MediaKind.VIDEO, MediaAttachment.ToStep(0), "media/boil.mp4")),
        )
        return TestRecipeFiles.write(incoming, "$title.recipefork", TestRecipeFiles.file(content, "media/boil.mp4" to TestRecipeFiles.mp4(200_000)))
    }

    // While a picked file is read, its progress shows in place — how far, its size being known — over the list, which
    // does not move, and no dialog blocks the screen. Then the snackbar says it with Open, the list is brought to the
    // recipe from far down, and Open opens its card.
    @Test
    fun anImportShowsItsProgressThenOffersOpenAndBringsTheListToTheRecipe() {
        val earlier = found(30)
        show()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(earlier.last().value)) }
        compose.onNodeWithTag(LibraryTags.MARKED_LIST).performScrollToIndex(earlier.size - 1)
        compose.waitForIdle()
        val firstShown = LibraryTags.entry(earlier.first().value)
        assertTrue("the list stands far from its top", exists(firstShown))
        val before = top(firstShown)

        pick(fileWithAClip("fresh", "Fresh soup"))
        compose.waitUntil(PATIENCE_MS) { progress().isNotEmpty() }
        compose.waitUntil(PATIENCE_MS) {
            val range = progress().single().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
            range != null && range != ProgressBarRangeInfo.Indeterminate && range.current > 0f
        }
        val shown = progress().single()
        assertEquals(listOf("Importing the recipe file"), shown.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(0f..1f, shown.config[SemanticsProperties.ProgressBarRangeInfo].range)
        assertEquals("no dialog while the file is read", 0, dialogs())
        assertEquals("the list does not move", before, top(firstShown))
        assertTrue("shown over the top of the list", shown.boundsInRoot.top <= top(LibraryTags.MARKED_LIST).top + 1f)

        reading.countDown()
        compose.awaitFileNotice("“Fresh soup” is in your recipe book now")
        compose.waitUntil(PATIENCE_MS) { progress().isEmpty() }
        val fresh = LibraryTags.entry("fresh")
        compose.waitUntil(PATIENCE_MS) {
            exists(fresh) && top(fresh).top >= top(LibraryTags.MARKED_LIST).top - 1f && top(fresh).bottom <= top(LibraryTags.MARKED_LIST).bottom
        }
        // The snackbar's Open is large enough; it is found by its words below.
        AccessibilityChecks.assertTouchTargets(compose)
        compose.onNode(hasText("Open") and hasAnyAncestor(hasTestTag(LibraryTags.SNACKBAR)), useUnmergedTree = true).performClick()
        compose.awaitCard()
        assertEquals("fresh", session.viewing.value)
        assertNull("the snackbar's message is closed", session.message.value)
    }

    // A failed import still says why, in its dialog with its one way out, and no progress is left showing.
    @Test
    fun aFailedImportStillSaysWhy() {
        found(1)
        show()
        reading.countDown()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.IMPORT) }
        pick(TestRecipeFiles.write(incoming, "photo.jpg", TestRecipeFiles.jpeg()))
        compose.awaitFileMessage("This file is not a recipe file, so nothing was added")
        assertEquals(0, compose.onAllNodes(hasText("Open"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(progress().isEmpty())
    }

    // A file whose recipe the book holds already says so in the snackbar, and its Open opens that recipe's card.
    @Test
    fun aRecipeHeldAlreadyIsOfferedToOpen() {
        val held = found(1).single()
        show()
        compose.runOnIdle { session.tell(ImportOutcome.AlreadyInBook(held, "Bean soup").message()) }
        compose.awaitFileNotice("“Bean soup” is already in your recipe book, so nothing was added")
        compose.onNode(hasText("Open") and hasAnyAncestor(hasTestTag(LibraryTags.SNACKBAR)), useUnmergedTree = true).performClick()
        compose.awaitCard()
        assertEquals(held.value, session.viewing.value)
    }

    // An export shows its progress in place as well, how far once it is known, and blocks nothing.
    @Test
    fun anExportShowsItsProgressInPlace() {
        val earlier = found(3)
        show()
        reading.countDown()
        val first = LibraryTags.entry(earlier.last().value)
        compose.waitUntil(PATIENCE_MS) { exists(first) }
        val before = top(first)
        var reported: ((Float) -> Unit)? = null
        val finish = CompletableDeferred<Unit>()
        val work = session.viewModelScope.launch {
            session.withProgress(FileWork.Kind.EXPORT) { progress ->
                reported = progress
                finish.await()
            }
        }
        compose.waitUntil(PATIENCE_MS) { progress().isNotEmpty() }
        val exporting = hasTestTag(LibraryTags.FILE_PROGRESS) and hasContentDescription("Exporting the recipe")
        assertEquals(ProgressBarRangeInfo.Indeterminate, compose.onNode(exporting).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo])
        compose.runOnIdle { reported!!(0.5f) }
        compose.waitUntil(PATIENCE_MS) {
            progress().singleOrNull()?.config?.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current == 0.5f
        }
        assertEquals(0, dialogs())
        assertEquals("the list does not move", before, top(first))
        finish.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { work.isCompleted && progress().isEmpty() }
    }

    // Export from an entry's menu runs as a file work of its own, its progress told as the file is written.
    @Test
    fun anExportFromTheEntryShowsItsProgress() {
        val recipe = Recipe.create(now, owner = null).copy(title = "Soup")
        runBlocking { recipes.save(recipe) }
        show()
        reading.countDown()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(recipe.id.value)) }
        val seen = mutableListOf<FileWork>()
        val watch = CoroutineScope(Dispatchers.Unconfined)
        watch.launch { session.work.filterNotNull().collect { synchronized(seen) { seen += it } } }
        try {
            compose.chooseEntryAction(recipe.id.value, LibraryTags.export(recipe.id.value))
            val activity = shadowOf(compose.activity)
            compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivity() != null }
            compose.waitUntil(PATIENCE_MS) { session.work.value == null }
        } finally {
            watch.cancel()
        }
        synchronized(seen) {
            assertTrue("$seen", seen.isNotEmpty() && seen.all { it.kind == FileWork.Kind.EXPORT })
            assertTrue("$seen", seen.any { (it.done ?: 0f) > 0f })
        }
    }

    // The import tells how far it has read a file of a known size, never going back and never past the whole.
    @Test
    fun anImportTellsHowFarItHasRead() {
        val bytes = TestRecipeFiles.file(TestRecipeFiles.soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg"))),
            "media/cover.jpg" to TestRecipeFiles.jpeg(100_000))
        val told = mutableListOf<Float>()
        val outcome = runBlocking { RecipeImport(recipes, files).import(bytes.size.toLong(), { told += it }) { bytes.inputStream() } }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        assertTrue(told.isNotEmpty())
        assertEquals(told.sorted(), told)
        assertTrue("$told", told.last() in 0.9f..1f)
    }

    // The export tells how far it has written the file.
    @Test
    fun anExportTellsHowFarItHasWritten() {
        val recipe = Recipe.create(now, owner = null).copy(title = "Soup")
        runBlocking { recipes.save(recipe) }
        val told = mutableListOf<Float>()
        assertTrue(runBlocking { RecipeExport.write(context, recipes, recipe.id) { told += it } } != null)
        assertTrue(told.isNotEmpty())
        assertEquals(told.sorted(), told)
        assertTrue("$told", told.first() > 0f && told.last() <= 1f)
    }
}
