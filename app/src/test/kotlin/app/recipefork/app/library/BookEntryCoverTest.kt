package app.recipefork.app.library

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.decodeThumbnail
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.flow.first
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The cover on the book's entries: an own and a found recipe's entry show the recipe's cover as a small square at its
 * start, decoded off the main thread at thumbnail size and once per screen; an entry without one — or whose cover file
 * is missing — shows none and its text keeps the whole width. The picture is decorative, and the entry stays readable.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookEntryCoverTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    /** Every decode the screen asked for: the file, and whether it ran off the main thread. */
    private val decodes = CopyOnWriteArrayList<Pair<String, Boolean>>()
    /** Holds every decode until it is counted down; open unless a test closes it. */
    @Volatile private var gate = CountDownLatch(0)
    private val thumbnails = CoverThumbnails(decode = { file, size ->
        decodes += file.name to (Looper.myLooper() != Looper.getMainLooper())
        gate.await(PATIENCE_MS, TimeUnit.MILLISECONDS)
        decodeThumbnail(file, size)
    })

    @Before
    fun setUp() {
        // Decoding a JPEG first on the test thread keeps the native graphics from aborting when this class runs first.
        val warmUp = TestPhotos.jpeg(File(context.cacheDir, "warm-up.jpg"), 16, 16)
        decodeThumbnail(warmUp, 16)
        warmUp.delete()
        compose.registerIdlingResource(book.idling)
    }

    @After
    fun close() {
        gate.countDown()
        compose.unregisterIdlingResource(book.idling)
        book.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    private fun photo(mediaId: String, attachment: MediaAttachment = MediaAttachment.Cover, width: Int = 1600, height: Int = 1200): Media {
        TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, width, height)
        return Media(mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    private fun own(title: String, vararg media: Media, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, steps = listOf(Step(0, "Boil.")), media = media.toList())
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun found(title: String, vararg media: Media): Recipe {
        val recipe = Recipe.create(now, owner = null).copy(title = title, media = media.toList())
            .inState(RecipeImport.FOUND_STATE)
        runBlocking { assertTrue(recipes.addFound(recipe, now)) }
        return recipe
    }

    private fun show(view: LibraryView, mode: Mode = Mode.Standard, fontScale: Float? = null) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                MaterialTheme { LibraryScreen(session, recipes, mode, thumbnails = thumbnails) }
            }
        }
        compose.onNodeWithTag(LibraryTags.tab(view)).performClick()
        compose.waitForIdle()
    }

    /** Waits for the cover of [recipe], its entry brought into view first: the list's header may push it below. */
    private fun awaitCover(recipe: Recipe) = compose.waitUntil(PATIENCE_MS) {
        val list = listOf(LibraryTags.OWN_LIST, LibraryTags.MARKED_LIST).firstOrNull(::exists) ?: return@waitUntil false
        try {
            compose.onNodeWithTag(list).performScrollToNode(hasTestTag(LibraryTags.entry(recipe.id.value)))
        } catch (e: AssertionError) {
            return@waitUntil false
        }
        exists(LibraryTags.cover(recipe.id.value))
    }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun dp(value: Float) = with(compose.density) { value.dp.toPx() }

    /** The cover stands at the entry's start as a small square, before the title, inside the entry's touch target. */
    private fun assertCoverAtStart(recipe: Recipe) {
        val id = recipe.id.value
        val cover = bounds(LibraryTags.cover(id))
        val entry = bounds(LibraryTags.entry(id))
        val title = bounds(LibraryTags.entryTitle(id))
        assertEquals("a 56 dp square", dp(56f), cover.width, 1f)
        assertEquals("a 56 dp square", cover.width, cover.height, 1f)
        assertEquals("at the entry's start, past its padding", entry.left + dp(16f), cover.left, 1f)
        assertTrue("within the entry: $cover in $entry", cover.top >= entry.top && cover.bottom <= entry.bottom)
        assertTrue("before the title: $cover, $title", cover.right <= title.left)
    }

    /** The picture says nothing to the screen reader: the entry reads as it did, by its title. */
    private fun assertDecorative(recipe: Recipe) {
        val id = recipe.id.value
        val cover = compose.onNodeWithTag(LibraryTags.cover(id), useUnmergedTree = true).fetchSemanticsNode()
        assertNull(cover.config.getOrNull(SemanticsProperties.ContentDescription))
        val entry = compose.onNodeWithTag(LibraryTags.entry(id)).fetchSemanticsNode()
        assertNull(entry.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(recipe.title, entry.config[SemanticsProperties.Text].first().text)
    }

    @Test
    fun theRowsCarryTheCoverAndNeverAStepPhoto() {
        own("Borscht", photo("s0", MediaAttachment.ToStep(0)), photo("c1"))
        own("Plain", photo("s1", MediaAttachment.ToStep(0)), minutesAgo = 1)
        found("Shakshuka", photo("c2"))
        runBlocking {
            assertEquals(listOf("c1", null), recipes.ownNewest(10).first().map { it.coverId })
            assertEquals(listOf("c2"), recipes.marked().first().map { it.coverId })
        }
    }

    @Test
    fun anOwnEntryShowsItsCoverAtItsStart() {
        val recipe = own("Borscht", photo("c1", width = 900, height = 1600))
        show(LibraryView.OWN)
        awaitCover(recipe)
        assertCoverAtStart(recipe)
        assertDecorative(recipe)
        // The entry still opens the card, and its offers keep their size and names.
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 2)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 2)
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).performClick()
        compose.waitUntil(PATIENCE_MS) { session.viewing.value == recipe.id.value }
    }

    @Test
    fun aFoundEntryShowsItsCoverAtItsStart() {
        val recipe = found("Shakshuka", photo("c2"))
        show(LibraryView.MARKED, Mode.Serverless)
        awaitCover(recipe)
        assertCoverAtStart(recipe)
        assertDecorative(recipe)
        compose.openEntryMenu(recipe.id.value)
        assertTrue(exists(LibraryTags.remove(recipe.id.value)))
        assertTrue(exists(LibraryTags.export(recipe.id.value)))
    }

    @Test
    fun anEntryWithoutACoverOrWithItsFileMissingShowsNoneAndKeepsTheWholeWidth() {
        val bare = own("Borscht")
        val gone = own("Shchi", Media("gone", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE), minutesAgo = 1)
        val pictured = own("Okroshka", photo("c3"), minutesAgo = 2)
        show(LibraryView.OWN)
        awaitCover(pictured)
        listOf(bare, gone).forEach { recipe ->
            val id = recipe.id.value
            compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(LibraryTags.entry(id)))
            assertFalse(exists(LibraryTags.cover(id)))
            assertFalse("no place is held for it", exists(LibraryTags.coverSlot(LibraryTags.cover(id))))
            val entry = bounds(LibraryTags.entry(id))
            val title = compose.onNodeWithTag(LibraryTags.entryTitle(id), useUnmergedTree = true).fetchSemanticsNode()
            assertEquals("the text starts at the entry's padding", entry.left + dp(16f), title.boundsInRoot.left, 1f)
            assertTrue(layoutOf(title.config).layoutInput.constraints.maxWidth + 1f >= entry.width - dp(32f))
        }
    }

    @Test
    fun eachCoverIsDecodedOnceOffTheMainThreadAtThumbnailSize() {
        val all = (0 until 3).map { own("Soup $it", photo("c$it"), minutesAgo = it.toLong()) }
        show(LibraryView.OWN)
        all.forEach(::awaitCover)
        // Leaving the view and coming back composes every entry anew; nothing is read again.
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        all.forEach(::awaitCover)
        compose.waitForIdle()
        assertEquals(listOf("c0", "c1", "c2"), decodes.map { it.first }.sorted())
        assertTrue("decoded off the main thread", decodes.all { it.second })
    }

    // A cover whose file is there holds its place before its picture is read: the title stands where it will stay.
    @Test
    fun theTitleStandsBesideTheCoversPlaceBeforeThePictureIsRead() {
        gate = CountDownLatch(1)
        val recipe = own("Borscht", photo("c1"))
        show(LibraryView.OWN)
        val id = recipe.id.value
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entryTitle(id)) && decodes.isNotEmpty() }
        assertFalse("the picture is not read yet", exists(LibraryTags.cover(id)))
        val entry = bounds(LibraryTags.entry(id))
        val before = bounds(LibraryTags.entryTitle(id)).left
        assertEquals("the title starts past the cover's place", entry.left + dp(16f + 56f + 12f), before, 1f)
        gate.countDown()
        awaitCover(recipe)
        assertEquals("and does not move once it is read", before, bounds(LibraryTags.entryTitle(id)).left, 0.5f)
    }

    @Test
    fun aThumbnailIsDecodedDownToItsSizeNeverWhole() {
        val file = TestPhotos.jpeg(File(context.cacheDir, "big.jpg"), 1600, 1200)
        try {
            val image: ImageBitmap = runBlocking { CoverThumbnails().load(file, 150) }!!
            assertEquals("scaled to the side asked for, keeping its shape", listOf(200, 150), listOf(image.width, image.height))
            assertNull(runBlocking { CoverThumbnails().load(File(context.cacheDir, "missing.jpg"), 150) })
        } finally {
            file.delete()
        }
    }

    // At 360 dp and font scale 2.0 the title keeps the entry's width less its padding and the cover, beside the ⋮ at
    // the end of its line, and the items of the entry's menu each keep their size.
    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun theTitleStillHasRoomBesideTheCoverAtLargeFontScale() {
        val recipe = own("Shakshuka with feta", photo("c1"))
        show(LibraryView.OWN, Mode.Serverless, fontScale = 2f)
        awaitCover(recipe)
        val id = recipe.id.value
        val list = compose.onNodeWithTag(LibraryTags.OWN_LIST).fetchSemanticsNode().boundsInRoot
        val entry = bounds(LibraryTags.entry(id))
        val menu = bounds(LibraryTags.actions(id))
        assertEquals("the entry takes the list's width less the ⋮", list.width - dp(48f + 4f), entry.width, 1f)
        assertTrue("the ⋮ lies within the list, after the entry", menu.left >= entry.right && menu.right <= list.right)
        val title = compose.onNodeWithTag(LibraryTags.entryTitle(id), useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(title.config)
        val room = layout.layoutInput.constraints.maxWidth
        assertTrue("the title has the entry's width less its padding and the cover ($room px of ${entry.width} px)",
            room + 1f >= entry.width - dp(32f + 56f + 12f))
        assertTrue("the title is not cut short", !layout.hasVisualOverflow)
        assertTrue("each word of the title fits its line", layout.lineCount <= 3)
        assertTrue(title.boundsInRoot.left >= bounds(LibraryTags.cover(id)).right)
        assertTrue("the title does not reach under the ⋮", title.boundsInRoot.right <= menu.left)
        val firstLine = title.boundsInRoot.top + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2
        assertEquals("the ⋮ stands level with the title's first line", firstLine, menu.center.y, dp(2f))
        compose.openEntryMenu(id)
        listOf(LibraryTags.export(id), LibraryTags.delete(id)).forEach { tag ->
            assertTrue(bounds(tag).height >= dp(48f) - 0.5f)
        }
    }

    private fun layoutOf(config: androidx.compose.ui.semantics.SemanticsConfiguration): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }
}
