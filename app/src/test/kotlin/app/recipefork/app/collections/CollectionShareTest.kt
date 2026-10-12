package app.recipefork.app.collections

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.FileMessageTags
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.CollectionFileContent
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * The collection's ⋮ → Share (v0.36, T-255): offered first on own and imported collections alike, it opens the share
 * sheet on the collection's file; its progress shows over the top of the screen while the file is written; a failure
 * says so in the file message's dialog, and opens no sheet.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CollectionShareTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private var ticks = 0L
    private val collections = CollectionRepository(active) { now.plusSeconds(ticks++) }
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val timers = TestTimers(context)
    private val cardTimers = CardTimers(timers.store, timers.scope, timers.access, timers.ringing) { BookName.Pool }

    @Before
    fun setUp() {
        FileProviderCache.clear()
        listOf(book.idling, device.idling, timers.idling).forEach(compose::registerIdlingResource)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        context.cacheDir.setWritable(true)
        listOf(book.idling, device.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        sessions.clear()
        book.close()
        device.close()
        timers.close()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    private fun own(title: String): RecipeId = runBlocking {
        Recipe.create(now, owner = null).copy(title = title, servings = 2).also { recipes.save(it) }.id
    }

    private fun found(title: String): RecipeId = runBlocking {
        Recipe.create(now, owner = null).copy(title = title, servings = 2, statedAuthor = "anna").inState(RecipeImport.FOUND_STATE)
            .also { assertTrue(recipes.addFound(it, now)) }.id
    }

    private fun show(view: LibraryView, mode: Mode = Mode.Serverless) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment(marks = DoneMarks.inMemory()) { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, mode, editor = editor, card = card, collections = collections) }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    private fun open(id: CollectionId, view: LibraryView) {
        val card = CollectionScreenTags.card(id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(list(view)).performScrollToNode(hasTestTag(card))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(card).performSemanticsAction(SemanticsActions.OnClick)
        await(CollectionScreenTags.SCREEN)
        compose.waitUntil(PATIENCE_MS) { exists(CollectionScreenTags.LIST) && !exists(LibraryTags.PAGER) }
    }

    /** The menu's items, top to bottom. */
    private fun menuItems(): List<String> {
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        await(CollectionScreenTags.DELETE)
        val tags = listOf(CollectionShareTags.SHARE, CollectionScreenTags.RENAME, CollectionScreenTags.DELETE).filter(::exists)
        return tags.sortedBy { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top }
    }

    private fun startedSheet(): Intent? = shadowOf(compose.activity).nextStartedActivity

    private fun awaitSheet(): Intent {
        var sheet: Intent? = null
        compose.waitUntil(PATIENCE_MS) {
            sheet = startedSheet()
            sheet != null
        }
        return sheet!!
    }

    private fun documentOf(sheet: Intent): Pair<Uri, CollectionFileContent> {
        @Suppress("DEPRECATION")
        val send = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(CollectionFile.MIME_TYPE, send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == CollectionFile.DOCUMENT) return uri to CollectionFile.decode(zip.readBytes().toString(Charsets.UTF_8))
            }
        }
        error("no document")
    }

    @Test
    fun anOwnCollectionsShareComesFirstAndOpensTheSheetOnItsFile() {
        val soup = own("Soup")
        val borscht = found("Borscht")
        val menu = runBlocking { collections.create("New Year's menu")!!.also { collections.addTo(soup, listOf(it)); collections.addTo(borscht, listOf(it)) } }
        show(LibraryView.OWN)
        open(menu, LibraryView.OWN)

        assertEquals(listOf(CollectionShareTags.SHARE, CollectionScreenTags.RENAME, CollectionScreenTags.DELETE), menuItems())
        compose.onNodeWithTag(CollectionShareTags.SHARE).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(CollectionShareTags.SHARE).assertTextEquals("Share")
        compose.onNodeWithTag(CollectionShareTags.SHARE).performClick()

        val sheet = awaitSheet()
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        assertEquals("Share “New Year's menu”", sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        val (uri, document) = documentOf(sheet)
        assertEquals("New-Year-s-menu.recipefork-collection", uri.lastPathSegment)
        assertEquals(CollectionFileContent(menu, "New Year's menu", null, listOf(soup, borscht)), document)
        // The menu closed, the screen stays, and the progress is gone once the sheet opened.
        compose.waitUntil(PATIENCE_MS) { !exists(CollectionShareTags.PROGRESS) && !exists(CollectionScreenTags.DELETE) }
        assertTrue(exists(CollectionScreenTags.SCREEN))
        assertNull(session.message.value)
    }

    @Test
    fun anImportedCollectionOffersShareAndIsSharedOnwardWithItsStatedAuthor() {
        val borscht = found("Borscht")
        val annas = CollectionId.mint()
        runBlocking { collections.putImported(annas, "Anna's dinners", "anna", listOf(borscht)) }
        show(LibraryView.MARKED)
        open(annas, LibraryView.MARKED)

        assertEquals(listOf(CollectionShareTags.SHARE, CollectionScreenTags.DELETE), menuItems())
        compose.onNodeWithTag(CollectionShareTags.SHARE).performClick()

        val sheet = awaitSheet()
        assertEquals("Share “Anna's dinners”", sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        assertEquals(CollectionFileContent(annas, "Anna's dinners", "anna", listOf(borscht)), documentOf(sheet).second)
    }

    // Files are a serverless-mode feature (FLOW-061): in the standard mode no collection offers Share.
    @Test
    fun theStandardModeOffersNoShare() {
        val soup = own("Soup")
        val borscht = found("Borscht")
        val menu = runBlocking { collections.create("Menu")!!.also { collections.addTo(soup, listOf(it)) } }
        val annas = CollectionId.mint()
        runBlocking { collections.putImported(annas, "Anna's dinners", "anna", listOf(borscht)) }
        show(LibraryView.OWN, Mode.Standard)
        open(menu, LibraryView.OWN)
        assertEquals(listOf(CollectionScreenTags.RENAME, CollectionScreenTags.DELETE), menuItems())
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CollectionScreenTags.DELETE) }
        compose.runOnIdle { session.closeCollection() }
        compose.runOnIdle { session.choose(LibraryView.MARKED) }
        await(LibraryTags.PAGER)
        open(annas, LibraryView.MARKED)
        assertEquals(listOf(CollectionScreenTags.DELETE), menuItems())
        assertNull(startedSheet())
    }

    @Test
    fun aFailedShareSaysSoAndOpensNoSheet() {
        val soup = own("Soup")
        val menu = runBlocking { collections.create("Menu")!!.also { collections.addTo(soup, listOf(it)) } }
        show(LibraryView.OWN)
        open(menu, LibraryView.OWN)
        File(context.cacheDir, "exports-staging").deleteRecursively()
        assertTrue(context.cacheDir.setWritable(false))

        menuItems()
        compose.onNodeWithTag(CollectionShareTags.SHARE).performClick()

        await(FileMessageTags.DIALOG)
        assertEquals(FileMessage(FileMessage.Kind.COLLECTION_SHARE_FAILED), session.message.value)
        val text = compose.onNodeWithTag(FileMessageTags.TEXT, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        assertEquals("Couldn't share the collection", text)
        assertNull(startedSheet())
        assertFalse(exists(CollectionShareTags.PROGRESS))
        compose.onNodeWithTag(FileMessageTags.OK).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(FileMessageTags.DIALOG) }
        assertEquals(listOf(soup), runBlocking { collections.get(menu) }!!.recipes)
    }

    // The progress over the screen: under way, then how far; a second tap starts no second file; gone once done.
    @Test
    fun progressShowsWhileTheFileIsWrittenAndASecondTapStartsNothing() {
        val release = CompletableDeferred<Boolean>()
        val told = CompletableDeferred<(Float) -> Unit>()
        var runs = 0
        var failures = 0
        lateinit var share: CollectionShare
        compose.setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                share = remember {
                    CollectionShare(scope, run = { progress ->
                        runs++
                        told.complete(progress)
                        release.await()
                    }, failed = { failures++ })
                }
                CollectionShareProgress(share)
            }
        }
        assertFalse(exists(CollectionShareTags.PROGRESS))

        compose.runOnIdle { share.start() }
        await(CollectionShareTags.PROGRESS)
        val node = compose.onNodeWithTag(CollectionShareTags.PROGRESS)
        assertEquals(ProgressBarRangeInfo.Indeterminate, node.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo])
        assertEquals(listOf("Preparing the collection's file"), node.fetchSemanticsNode().config[SemanticsProperties.ContentDescription])

        val progress = runBlocking { told.await() }
        compose.runOnIdle { progress(0.5f) }
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(CollectionShareTags.PROGRESS).fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo].current == 0.5f
        }
        compose.runOnIdle { share.start() }
        compose.waitForIdle()
        assertEquals(1, runs)

        release.complete(true)
        compose.waitUntil(PATIENCE_MS) { !exists(CollectionShareTags.PROGRESS) }
        assertEquals(0, failures)
        assertFalse(share.running)
    }

    @Test
    fun aShareThatFailsIsToldOnce() {
        var failures = 0
        lateinit var share: CollectionShare
        compose.setContent {
            val scope = rememberCoroutineScope()
            share = remember { CollectionShare(scope, run = { false }, failed = { failures++ }) }
        }
        compose.runOnIdle { share.start() }
        compose.waitUntil(PATIENCE_MS) { !share.running }
        assertEquals(1, failures)
    }
}
