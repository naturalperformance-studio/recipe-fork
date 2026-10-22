package app.recipefork.app.library

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.decodeFilling
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.app.ui.WorkingFamily
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
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
import kotlin.math.abs

/**
 * The book's entries as the rows of a cookbook's contents (v0.42, T-301): a row at least 84 dp high with a 60 dp cover
 * thumbnail at its start, or — without a cover — the title's first letter as a decorative 60 × 60 drop cap; the title
 * in Literata, the tags under it in Golos, the ⋮ 48 dp at its end; a hairline between rows, no card. Four rows fit the
 * first screen of a 360 × 780 dp phone; an imported recipe's stated author is set in italics; a chosen row shows a
 * round check in place of its cover on a tint.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h780dp")
class BookEntryRowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private val soup = TagEntity(10, "суп", "Soup")
    private val quick = TagEntity(11, "быстро", "Quick")
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = listOf(soup, quick)
        },
    ) { now }

    private lateinit var scheme: ColorScheme
    private lateinit var type: Typography

    @Before
    fun setUp() {
        // Decoding a JPEG first on the test thread keeps the native graphics from aborting when this class runs first.
        val warmUp = TestPhotos.jpeg(File(context.cacheDir, "warm-up.jpg"), 16, 16)
        decodeFilling(warmUp, 16, 12)
        warmUp.delete()
        compose.registerIdlingResource(book.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    private fun cover(mediaId: String): Media {
        TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 800, 600)
        return Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)
    }

    private fun own(title: String, vararg tags: TagEntity, media: List<Media> = emptyList(), minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, tags = tags.mapTo(HashSet()) { it.id }, media = media)
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun found(title: String, author: String?): Recipe {
        val recipe = Recipe.create(now, owner = null).copy(title = title, statedAuthor = author).inState(RecipeImport.FOUND_STATE)
        runBlocking { assertTrue(recipes.addFound(recipe, now)) }
        return recipe
    }

    private fun show(view: LibraryView, mode: Mode = Mode.Serverless) {
        session.choose(view)
        compose.setContent {
            AppTheme {
                scheme = MaterialTheme.colorScheme
                type = MaterialTheme.typography
                AppNavHost(session, recipes, mode, editor = editor)
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun dp(value: Float) = with(compose.density) { value.dp.toPx() }

    private fun layoutOf(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private fun screen(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private fun close(pixel: Color, colour: Color, within: Float = 0.02f) =
        listOf(pixel.red - colour.red, pixel.green - colour.green, pixel.blue - colour.blue).all { abs(it) < within }

    /** Whether the pixel at [x], [y] of [image] is [colour], within a little of rounding. */
    private fun assertPixel(message: String, image: Bitmap, x: Float, y: Float, colour: Color) {
        val pixel = Color(image.getPixel(x.toInt(), y.toInt()))
        assertTrue("$message: $pixel, not $colour", close(pixel, colour))
    }

    private fun bringIntoView(list: String, recipe: Recipe) {
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag(LibraryTags.entry(recipe.id.value)))
        compose.waitForIdle()
    }

    @Test
    fun aRowCarriesItsCoverOrADropCapTheTitleTagsAndMenuWithHairlinesAndNoCard() {
        val pictured = own("Borscht", soup, quick, media = listOf(cover("c1")))
        val bare = own("shchi", soup, minutesAgo = 1)
        show(LibraryView.OWN)
        await(LibraryTags.cover(pictured.id.value))
        await(BookEntryRowTags.dropCap(bare.id.value))
        val list = bounds(LibraryTags.OWN_LIST)
        listOf(pictured, bare).forEach { recipe ->
            val id = recipe.id.value
            val entry = bounds(LibraryTags.entry(id))
            assertEquals("the row takes the list's whole width: no card", list.left, entry.left, 1f)
            assertEquals(list.width, entry.width, 1f)
            assertTrue("at least 84 dp high (${entry.height} px)", entry.height >= dp(84f) - 0.5f)
            val start = if (recipe == pictured) bounds(LibraryTags.coverSlot(LibraryTags.cover(id))) else bounds(BookEntryRowTags.dropCap(id))
            assertEquals("60 dp wide", dp(60f), start.width, 1f)
            assertEquals("60 dp high", dp(60f), start.height, 1f)
            assertEquals("at the row's start", entry.left + dp(16f), start.left, 1f)
            assertEquals(entry.top + dp(12f), start.top, 1f)
            val title = bounds(LibraryTags.entryTitle(id))
            assertEquals("the title beside it", start.right + dp(12f), title.left, 1f)
            val titleStyle = layoutOf(LibraryTags.entryTitle(id)).layoutInput.style
            assertEquals("the title in Literata", ReadingFamily, titleStyle.fontFamily)
            assertEquals("Medium", type.titleLarge.fontWeight, titleStyle.fontWeight)
            assertEquals("at the redesign's 18 sp", 18.sp, titleStyle.fontSize)
            assertEquals("on 24 sp lines", 24.sp, titleStyle.lineHeight)
            val tags = bounds(LibraryTags.entryTags(id))
            assertTrue("the tags under the title", tags.top >= title.bottom)
            assertEquals("the tags in Golos", WorkingFamily, layoutOf(LibraryTags.entryTags(id)).layoutInput.style.fontFamily)
            val menu = bounds(LibraryTags.actions(id))
            assertEquals("the ⋮ 48 dp", dp(48f), menu.width, 1f)
            assertEquals(dp(48f), menu.height, 1f)
            assertEquals("at the row's end", entry.right, menu.right, 1f)
            assertTrue("the title keeps clear of the ⋮", title.right <= menu.left)
        }
        // The picture and the letter are decorative: the row reads its title and tags, and nothing else.
        val pictureNode = compose.onNodeWithTag(LibraryTags.cover(pictured.id.value), useUnmergedTree = true).fetchSemanticsNode()
        assertNull(pictureNode.config.getOrNull(SemanticsProperties.ContentDescription))
        val capNode = compose.onNodeWithTag(BookEntryRowTags.dropCap(bare.id.value), useUnmergedTree = true).fetchSemanticsNode()
        assertNull(capNode.config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(capNode.config.getOrNull(SemanticsProperties.Text))
        val read = compose.onNodeWithTag(LibraryTags.entry(bare.id.value)).fetchSemanticsNode().config
        assertEquals(listOf("shchi", "Soup"), read[SemanticsProperties.Text].map { it.text })
        assertNull(read.getOrNull(SemanticsProperties.ContentDescription))
        assertFalse("a recipe with a cover shows no drop cap", exists(BookEntryRowTags.dropCap(pictured.id.value)))
        assertFalse("a recipe without one shows no picture", exists(LibraryTags.cover(bare.id.value)))
        // On the page itself, with a hairline in the outline variant under each row.
        val image = screen()
        listOf(pictured, bare).forEach { recipe ->
            val entry = bounds(LibraryTags.entry(recipe.id.value))
            assertPixel("the row's ground is the page's", image, entry.left + dp(4f), entry.top + dp(4f), scheme.surface)
            assertPixel("a hairline under the row", image, entry.center.x, entry.bottom + dp(0.5f), scheme.outlineVariant)
            assertPixel("one hairline, no more", image, entry.center.x, entry.bottom + dp(1.5f), scheme.surface)
        }
        // The drop cap's square is the container's, and its letter is drawn on it in the muted ink.
        val cap = bounds(BookEntryRowTags.dropCap(bare.id.value))
        assertPixel("the drop cap's square", image, cap.left + dp(4f), cap.top + dp(4f), scheme.surfaceContainer)
        val inked = (cap.left.toInt() until cap.right.toInt()).sumOf { x ->
            (cap.top.toInt() until cap.bottom.toInt()).count { y ->
                val pixel = Color(image.getPixel(x, y))
                abs(pixel.red - scheme.onSurfaceVariant.red) < 0.05f && abs(pixel.green - scheme.onSurfaceVariant.green) < 0.05f &&
                    abs(pixel.blue - scheme.onSurfaceVariant.blue) < 0.05f
            }
        }
        assertTrue("its letter is drawn ($inked px)", inked > dp(20f))
    }

    @Test
    fun theDropCapIsTheTitlesFirstLetter() {
        assertEquals("S", dropCapLetter("shchi"))
        assertEquals("Н", dropCapLetter("«Наполеон»"))
        assertEquals("1", dropCapLetter(" 1-2-3 cake"))
        assertEquals("", dropCapLetter("…"))
    }

    /** How many entries of the list tagged [list] stand whole on the first screen. */
    private fun wholeOnTheFirstScreen(list: String, all: List<Recipe>): Int {
        val shown = bounds(list)
        val window = compose.onRoot().fetchSemanticsNode().size.height
        return all.count { recipe ->
            val tag = LibraryTags.entry(recipe.id.value)
            exists(tag) && bounds(tag).let { it.top >= shown.top - 0.5f && it.bottom <= minOf(shown.bottom, window.toFloat()) + 0.5f }
        }
    }

    private fun assertFourFit(mode: Mode) {
        val all = (0 until 8).map { own("Soup number $it", soup, quick, minutesAgo = it.toLong()) }
        show(LibraryView.OWN, mode)
        await(LibraryTags.entryTags(all.first().id.value))
        // Below the title, the tabs, the search and the filter row.
        listOf(LibraryTags.TITLE, LibraryTags.tab(LibraryView.OWN), LibraryTags.SEARCH, LibraryTags.FILTERS).forEach {
            assertTrue("$it shows", exists(it))
            assertTrue("$it above the rows", bounds(it).bottom <= bounds(LibraryTags.entry(all.first().id.value)).top)
        }
        val whole = wholeOnTheFirstScreen(LibraryTags.OWN_LIST, all)
        assertTrue("at least four rows fit the first screen ($whole)", whole >= 4)
    }

    // The build people install is the serverless one: a draft's row names no state and no sending mark.
    @Test
    fun atLeastFourRowsFitTheFirstScreenOfA360By780Phone() = assertFourFit(Mode.Serverless)

    @Test
    fun anImportedRowStatesItsAuthorInItalics() {
        val recipe = found("Shakshuka", "Marina")
        show(LibraryView.MARKED)
        val tag = LibraryTags.statedAuthor(recipe.id.value)
        await(tag)
        val layout = layoutOf(tag)
        assertEquals("Author: Marina (stated in the file, not verified)", layout.layoutInput.text.text)
        assertEquals("in italics", FontStyle.Italic, layout.layoutInput.style.fontStyle)
        assertEquals("in Literata", ReadingFamily, layout.layoutInput.style.fontFamily)
        assertTrue("under the title", bounds(tag).top >= bounds(LibraryTags.entryTitle(recipe.id.value)).bottom)
        await(BookEntryRowTags.dropCap(recipe.id.value))
    }

    @Test
    fun aChosenRowShowsARoundCheckOverItsCoverOnATint() {
        val pictured = own("Borscht", soup, media = listOf(cover("c1")))
        val bare = own("Shchi", minutesAgo = 1)
        show(LibraryView.OWN)
        await(LibraryTags.cover(pictured.id.value))
        val id = pictured.id.value
        val before = bounds(LibraryTags.entryTitle(id))
        compose.onNodeWithTag(LibraryTags.entry(id)).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_BAR) && exists(LibraryTags.selectionMark(id)) }
        compose.waitForIdle()
        listOf(pictured to true, bare to false).forEach { (recipe, chosen) ->
            val rid = recipe.id.value
            bringIntoView(LibraryTags.OWN_LIST, recipe)
            val entry = bounds(LibraryTags.entry(rid))
            val mark = bounds(LibraryTags.selectionMark(rid))
            assertEquals("a 26 dp circle", dp(26f), mark.width, 1f)
            assertEquals(dp(26f), mark.height, 1f)
            // As the redesign's .selmk draws it (T-305): its end edge 6 dp inside the cover's, its bottom 4 dp below it.
            assertEquals("its end edge 6 dp inside the cover's", entry.left + dp(16f + 60f - 6f), mark.right, 1f)
            assertEquals("its bottom 4 dp below the cover's", entry.top + dp(12f + 60f + 4f), mark.bottom, 1f)
            val picture = if (recipe == pictured) LibraryTags.coverSlot(LibraryTags.cover(rid)) else BookEntryRowTags.dropCap(rid)
            assertTrue("the cover or drop cap stays under it", exists(picture))
            assertFalse("no ⋮ while choosing", exists(LibraryTags.actions(rid)))
            val config = compose.onNodeWithTag(LibraryTags.entry(rid)).fetchSemanticsNode().config
            assertEquals(chosen, config[SemanticsProperties.Selected])
            assertEquals(if (chosen) "Selected" else "Not selected", config[SemanticsProperties.StateDescription])
            val action = if (chosen) "Take the recipe off the selection" else "Select the recipe"
            assertEquals("the same click label", action, config[SemanticsActions.OnClick].label)
            assertEquals("and long press", action, config[SemanticsActions.OnLongClick].label)
            val image = screen()
            val ground = if (chosen) scheme.secondaryContainer else scheme.surface
            assertPixel("the row's ground", image, entry.left + dp(4f), entry.top + dp(4f), ground)
            assertPixel("across the row", image, entry.right - dp(4f), entry.bottom - dp(4f), ground)
            if (chosen) {
                assertPixel("a chosen mark is filled in the primary colour", image, mark.left + dp(4f), mark.center.y, scheme.primary)
                assertPixel("a plain disc, no ring", image, mark.left + dp(1.5f), mark.center.y, scheme.primary)
                assertPixel("its top as well", image, mark.center.x, mark.top + dp(1.5f), scheme.primary)
                val ticked = (mark.left.toInt() until mark.right.toInt()).sumOf { x ->
                    (mark.top.toInt() until mark.bottom.toInt()).count { y -> close(Color(image.getPixel(x, y)), scheme.onPrimary) }
                }
                assertTrue("its check drawn in onPrimary ($ticked px)", ticked > dp(10f))
            } else {
                assertPixel("a mark not chosen is the page's colour", image, mark.center.x, mark.center.y, scheme.surface)
                assertPixel("outlined in onSurfaceVariant", image, mark.left + dp(1f), mark.center.y, scheme.onSurfaceVariant)
            }
        }
        assertEquals("the text does not move", before.left, bounds(LibraryTags.entryTitle(id)).left, 0.5f)
        // A tap lets it go: the mark goes, and the selection with no entry left ends.
        compose.onNodeWithTag(LibraryTags.entry(id)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.SELECTION_BAR) && !exists(LibraryTags.selectionMark(id)) }
        assertTrue(exists(LibraryTags.cover(id)))
    }

    /** Every word of the title of [recipe] is laid out whole: nothing cut short, nothing past its room. */
    private fun assertEveryWordShows(recipe: Recipe) {
        val tag = LibraryTags.entryTitle(recipe.id.value)
        await(tag)
        bringIntoView(LibraryTags.OWN_LIST, recipe)
        val layout = layoutOf(tag)
        val laid = layout.layoutInput.text.text
        assertEquals("the title, a break place after its hyphens aside", recipe.title, laid.replace("\u200B", ""))
        val last = layout.lineCount - 1
        assertTrue("${recipe.title}: no line cut short", (0..last).none { layout.isLineEllipsized(it) })
        assertEquals("${recipe.title}: the last line ends the title", laid.length, layout.getLineEnd(last))
        assertFalse("${recipe.title}: nothing past its room", layout.hasVisualOverflow)
        val words = recipe.title.split(' ').flatMap { word -> Regex("""[^-]*-+|[^-]+""").findAll(word).map { it.value }.toList() }
        words.forEach { word ->
            val start = laid.indexOf(word)
            assertTrue("«$word» is laid out", start >= 0)
            val line = layout.getLineForOffset(start)
            assertEquals("«$word» stays on one line", line, layout.getLineForOffset(start + word.length - 1))
        }
    }

    private fun longTitles(fontScale: Float) {
        val potato = own("Картофельно-грибная запеканка")
        val crumble = own("Strawberry-rhubarb crumble", minutesAgo = 1)
        session.choose(LibraryView.OWN)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    scheme = MaterialTheme.colorScheme
                    type = MaterialTheme.typography
                    AppNavHost(session, recipes, Mode.Serverless, editor = editor)
                }
            }
        }
        compose.waitForIdle()
        listOf(potato, crumble).forEach(::assertEveryWordShows)
    }

    @Test
    fun longTitlesKeepEveryWord() = longTitles(1f)

    @Test
    fun longTitlesKeepEveryWordAtTwiceTheFontSize() = longTitles(2f)
}
