package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.appColorScheme
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs

/**
 * The book against the redesign's mock (v0.42, T-309; the whole-book design QA, rows W1–W5):
 * - W1: the search field at rest shows the mock's magnifier (`i-search`, 22 dp, decorative) at its start and the whole
 *   hint as its placeholder, with no floating label; the screen reader hears it named "Search"; it is 56 dp high.
 * - W2: the book's floating actions ("New recipe", "Import") are `primary` with `onPrimary`, as the mock's `.fab`.
 * - W4: the book's empty sections and its no-result states stand centred in the space below the header, clear of the
 *   floating action; an empty section that shows no floating action keeps no room for one.
 * - W5: a no-result state's way out is an outlined button with its words in `primary`.
 * (W3, the spine's name in Literata Medium 15 sp, is asserted in CollectionSpinesTest.)
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h780dp")
class BookAgainstTheMockTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(title: String) = runBlocking {
        recipes.save(Recipe.create(now, owner = null).copy(title = title, language = RecipeLanguage.EN))
    }

    private fun found(title: String) = runBlocking {
        Recipe.create(now, owner = null).copy(title = title).inState(RecipeImport.FOUND_STATE).also { recipes.addFound(it, now) }
    }

    private fun scheme(dark: Boolean): ColorScheme = appColorScheme(context.resources, dark)

    private fun show(view: LibraryView, mode: Mode = Mode.Serverless, dark: Boolean = false) {
        session.choose(view)
        compose.setContent { AppTheme(dark = dark) { AppNavHost(session, recipes, mode) } }
        compose.waitForIdle()
    }

    private fun exists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) {
        compose.waitUntil(PATIENCE_MS) { exists(hasTestTag(tag)) }
        compose.waitForIdle()
    }

    private fun bounds(matcher: SemanticsMatcher): Rect =
        compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun bounds(tag: String) = bounds(hasTestTag(tag))

    private fun dp(value: Float) = value * compose.density.density

    private fun layoutOf(matcher: SemanticsMatcher): TextLayoutResult = mutableListOf<TextLayoutResult>().also {
        compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(it)
    }.single()

    private fun pixelAt(x: Float, y: Float): Color =
        Color(compose.onRoot().captureToImage().asAndroidBitmap().getPixel(x.toInt(), y.toInt()))

    private fun assertColour(message: String, expected: Color, shown: Color, within: Float = 0.02f) {
        val close = listOf(shown.red - expected.red, shown.green - expected.green, shown.blue - expected.blue).all { abs(it) < within }
        assertTrue("$message: $shown, not $expected", close)
    }

    // --- W1: the search field ---------------------------------------------------------------------------------------

    private fun assertTheSearchFieldAtRest(name: String, hint: String) {
        own("Soup")
        show(LibraryView.OWN)
        await(LibraryTags.SEARCH_PLACEHOLDER)
        val field = compose.onNodeWithTag(LibraryTags.SEARCH).fetchSemanticsNode()
        assertFalse("at rest, not focused", field.config[SemanticsProperties.Focused])
        assertEquals("named for the screen reader", listOf(name), field.config[SemanticsProperties.ContentDescription])

        // The whole hint stands inside the empty field at rest, on one line, not cut.
        val shown = layoutOf(hasTestTag(LibraryTags.SEARCH_PLACEHOLDER))
        assertEquals(hint, shown.layoutInput.text.text)
        assertEquals(1, shown.lineCount)
        assertFalse("the hint is cut short", shown.isLineEllipsized(0))
        // No floating label: nothing in the field reads the short name.
        val inside = hasAnyAncestor(hasTestTag(LibraryTags.SEARCH))
        assertFalse("a label is drawn", exists(inside and hasText(name)))

        // The magnifier: 22 dp, at the field's start before the hint, decorative.
        val search = bounds(LibraryTags.SEARCH)
        val magnifier = bounds(LibraryTags.SEARCH_ICON)
        assertEquals("the magnifier 22 dp wide", dp(22f), magnifier.width, 0.5f)
        assertEquals("the magnifier 22 dp high", dp(22f), magnifier.height, 0.5f)
        val words = bounds(LibraryTags.SEARCH_PLACEHOLDER)
        assertTrue("the magnifier ($magnifier) at the start, before the hint ($words)", magnifier.left > search.left && magnifier.right <= words.left)
        assertTrue("the magnifier within 16 dp of the field's start", magnifier.left - search.left <= dp(16f))
        assertEquals("the magnifier centred on the field's line", search.center.y, magnifier.center.y, dp(1f))
        val icon = compose.onNode(hasTestTag(LibraryTags.SEARCH_ICON), useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(null, icon.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(null, icon.getOrNull(SemanticsProperties.Text))

        assertEquals("the field 56 dp high", dp(56f), search.height, 0.5f)
    }

    @Test
    fun theSearchFieldAtRestShowsTheMagnifierAndTheWholeHint() =
        assertTheSearchFieldAtRest("Search", "Search by title or ingredient")

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun inRussianTheSearchFieldAtRestShowsTheMagnifierAndTheWholeHint() =
        assertTheSearchFieldAtRest("Поиск", "Поиск по названию или ингредиенту")

    // --- W2: the floating actions -----------------------------------------------------------------------------------

    /** The floating action tagged [tag] is filled with `primary` and its words are in `onPrimary`. */
    private fun assertInTheActionColour(tag: String, words: String, dark: Boolean) {
        val colours = scheme(dark)
        val fab = bounds(tag)
        // Inside its start padding, clear of its icon, its words and its corners.
        assertColour("the floating action's fill", colours.primary, pixelAt(fab.left + dp(6f), fab.center.y))
        val label = layoutOf(hasAnyAncestor(hasTestTag(tag)) and hasText(words))
        assertEquals("the floating action's words", colours.onPrimary, label.layoutInput.style.color)
    }

    private fun assertNewRecipeInTheActionColour(dark: Boolean) {
        own("Soup")
        show(LibraryView.OWN, dark = dark)
        await(LibraryTags.NEW)
        assertInTheActionColour(LibraryTags.NEW, "New recipe", dark)
    }

    @Test
    fun newRecipeIsInTheActionColour() = assertNewRecipeInTheActionColour(dark = false)

    @Test
    fun inTheDarkThemeNewRecipeIsInTheActionColour() = assertNewRecipeInTheActionColour(dark = true)

    @Test
    fun importIsInTheActionColour() {
        found("Found soup")
        show(LibraryView.MARKED, Mode.Serverless)
        await(LibraryTags.IMPORT)
        assertInTheActionColour(LibraryTags.IMPORT, "Import", dark = false)
    }

    // --- W4: empty states centred -----------------------------------------------------------------------------------

    /**
     * The state tagged [state] fills the space below [above] — the header or the tabs — down to the room kept for the
     * floating action, and its picture, words and way out (tagged [last]) stand in the middle of it.
     */
    private fun assertCentred(state: String, last: String, above: String) {
        await(state)
        await(last)
        val region = bounds(state)
        val first = bounds(LibraryTags.emptyPicture(state))
        val end = bounds(last)
        val over = first.top - region.top
        val under = region.bottom - end.bottom
        assertTrue("the state stands clear of the top ($over above it)", over > dp(48f))
        assertEquals("as much space above the state ($over) as under it ($under)", over, under, dp(1f))
        // The space starts right under what stands above it — the header's own margin and hairline, no more.
        val gap = region.top - bounds(above).bottom
        assertTrue("the space starts right under ${bounds(above)} ($gap below it)", gap >= -0.5f && gap <= dp(10f))
        val root = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
        assertTrue("the space reaches down to the floating action's room (${region.bottom} of $root)", region.bottom >= root - dp(160f))
    }

    @Test
    fun aBookWithNothingWrittenStandsCentred() {
        show(LibraryView.OWN)
        assertCentred(LibraryTags.OWN_EMPTY, LibraryTags.WRITE, above = LibraryTags.SWITCH)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun anEmptyImportedViewStandsCentred() {
        show(LibraryView.MARKED, Mode.Serverless)
        assertCentred(LibraryTags.MARKED_EMPTY, LibraryTags.IMPORT, above = LibraryTags.SWITCH)
    }

    @Test
    fun aSearchThatFindsNothingStandsCentredClearOfNewRecipe() {
        own("Soup")
        show(LibraryView.OWN)
        await(LibraryTags.SEARCH)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("couscous")
        compose.waitForIdle()
        assertCentred(LibraryTags.NOTHING_FOUND, LibraryTags.NOTHING_FOUND_CLEAR, above = LibraryTags.HEADER)
        await(LibraryTags.NEW)
        assertTrue("the state ends above New recipe", bounds(LibraryTags.NOTHING_FOUND).bottom <= bounds(LibraryTags.NEW).top)
    }

    /**
     * An empty section that shows no floating action keeps no room for one: it reaches down to 8 dp above the screen's
     * bottom edge (no navigation bar in these tests), and its way out tagged [last] stands within it unscrolled.
     */
    private fun assertNoRoomForAnAction(state: String, last: String, absent: String) {
        await(state)
        await(last)
        assertFalse("no floating action is shown", exists(hasTestTag(absent)))
        val root = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
        val region = bounds(state)
        assertEquals("the section reaches 8 dp above the bottom (${region.bottom} of $root)", root - dp(8f), region.bottom, 1f)
        val way = bounds(last)
        assertTrue("the way out ($way) stands within the section ($region) without scrolling", way.bottom <= region.bottom + 0.5f)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleNothingWrittenKeepsNoRoomForAnAction() {
        show(LibraryView.OWN)
        assertNoRoomForAnAction(LibraryTags.OWN_EMPTY, LibraryTags.WRITE, absent = LibraryTags.NEW)
    }

    /**
     * In Russian at twice the font scale on a 320 × 640 dp phone the words of a book with nothing written are taller
     * than the space: the section still reaches the bottom, and its way out is reached by scrolling it.
     */
    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleNothingWrittenReachesTheBottomAndScrollsToItsWayOut() {
        show(LibraryView.OWN)
        await(LibraryTags.OWN_EMPTY)
        await(LibraryTags.WRITE)
        val root = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
        val region = bounds(LibraryTags.OWN_EMPTY)
        assertEquals("the section reaches 8 dp above the bottom", root - dp(8f), region.bottom, 1f)
        compose.onNodeWithTag(LibraryTags.WRITE).performScrollTo()
        compose.waitForIdle()
        val way = bounds(LibraryTags.WRITE)
        assertTrue("scrolled, the way out ($way) stands within the section ($region)", way.bottom <= region.bottom + 0.5f)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleAnEmptyImportedViewKeepsNoRoomForAnAction() {
        show(LibraryView.MARKED, Mode.Serverless)
        await(LibraryTags.MARKED_EMPTY)
        val root = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
        assertEquals("the section reaches 8 dp above the bottom", root - dp(8f), bounds(LibraryTags.MARKED_EMPTY).bottom, 1f)
    }

    @Test
    fun anEmptyImportedViewKeepsNoRoomForAnAction() {
        show(LibraryView.MARKED, Mode.Serverless)
        await(LibraryTags.MARKED_EMPTY)
        val imports = compose.onAllNodes(hasTestTag(LibraryTags.IMPORT), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("only the section's own import, no floating one", 1, imports.size)
        assertNoRoomForAnAction(LibraryTags.MARKED_EMPTY, LibraryTags.IMPORT, absent = LibraryTags.NEW)
    }

    // --- W5: the way out of a no-result state -----------------------------------------------------------------------

    /** The way out tagged [action] is an outlined button: no fill — the page shows through — and words in `primary`. */
    private fun assertOutlined(action: String, words: String, dark: Boolean = false) {
        await(action)
        val colours = scheme(dark)
        val button = bounds(action)
        val page = pixelAt(bounds(LibraryTags.NOTHING_FOUND).left + dp(2f), button.center.y)
        // Inside its start padding, clear of its outline and its words.
        val fill = pixelAt(button.left + dp(8f), button.center.y)
        assertColour("the button is not filled", page, fill)
        assertColour("the page is the page", colours.background, page)
        // A hairline in the outline colour at its edge (smoothed a little where it is drawn).
        assertColour("the button's outline", colours.outline, pixelAt(button.left + 0.5f, button.center.y), within = 0.05f)
        val label = layoutOf(hasAnyAncestor(hasTestTag(action)) and hasText(words))
        assertEquals("the words in the action colour", colours.primary, label.layoutInput.style.color)
        val role = compose.onNodeWithTag(action).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
        assertEquals(Role.Button, role)
    }

    @Test
    fun clearingTheSearchIsAnOutlinedButton() {
        own("Soup")
        show(LibraryView.OWN)
        await(LibraryTags.SEARCH)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("couscous")
        assertOutlined(LibraryTags.NOTHING_FOUND_CLEAR, "Clear the search")
    }

    @Test
    fun inTheDarkThemeClearingTheSearchIsAnOutlinedButton() {
        own("Soup")
        show(LibraryView.OWN, dark = true)
        await(LibraryTags.SEARCH)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("couscous")
        assertOutlined(LibraryTags.NOTHING_FOUND_CLEAR, "Clear the search", dark = true)
    }

    @Test
    fun clearingTheFiltersIsAnOutlinedButton() {
        own("Soup")
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.RU))
        show(LibraryView.OWN)
        assertOutlined(LibraryTags.NOTHING_FOUND_CLEAR_FILTERS, "Clear filters")
    }

    @Test
    fun clearingTheSearchAndTheFiltersIsAnOutlinedButton() {
        own("Soup")
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.RU))
        show(LibraryView.OWN)
        await(LibraryTags.SEARCH)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("couscous")
        compose.waitForIdle()
        await(LibraryTags.NOTHING_FOUND_CLEAR_ALL)
        val words = compose.onNodeWithTag(LibraryTags.NOTHING_FOUND_CLEAR_ALL).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        assertOutlined(LibraryTags.NOTHING_FOUND_CLEAR_ALL, words)
    }
}
