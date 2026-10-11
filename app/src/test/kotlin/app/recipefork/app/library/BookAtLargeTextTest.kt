package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.appColorScheme
import app.recipefork.app.ui.widestWord
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

/**
 * The book at large text on a narrow phone (v0.30, T-219), in English and Russian: the tabs never break inside a word,
 * and in a build without the server the marked view is named for the imported recipes it holds; the search field's
 * short label stays on one line and is never clipped under the tabs; an empty or narrowed view's action is never
 * covered by the view's floating action; an entry's Delete and Remove are drawn in the error colour; the quick filters
 * are drawn 32 dp tall within a 48 dp touch target.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class BookAtLargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private var mode by mutableStateOf(Mode.Standard)
    private var tabStyle: TextStyle? = null

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(title: String, language: RecipeLanguage? = RecipeLanguage.EN): Recipe = runBlocking {
        Recipe.create(now, owner = null).copy(title = title, language = language).also { recipes.save(it) }
    }

    private fun found(title: String): Recipe = runBlocking {
        Recipe.create(now, owner = null).copy(title = title).inState(RecipeImport.FOUND_STATE).also { recipes.addFound(it, now) }
    }

    private fun show(view: LibraryView, shownMode: Mode = Mode.Standard) {
        mode = shownMode
        session.choose(view)
        compose.setContent {
            AppTheme {
                tabStyle = MaterialTheme.typography.titleSmall
                AppNavHost(session, recipes, mode)
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    /** The layout of the one text found by [matcher] in the unmerged tree. */
    private fun layoutOf(matcher: SemanticsMatcher): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    private fun layoutOf(tag: String) = layoutOf(hasTestTag(tag))

    private fun tabLabel(view: LibraryView) = hasTestTag(LibraryTags.TAB_LABEL) and hasAnyAncestor(hasTestTag(LibraryTags.tab(view)))

    private fun unclipped(matcher: SemanticsMatcher): Rect = compose.onNode(matcher, useUnmergedTree = true).getUnclippedBoundsInRoot().let {
        with(compose.density) { Rect(it.left.toPx(), it.top.toPx(), it.right.toPx(), it.bottom.toPx()) }
    }

    private fun clipped(tag: String): Rect = compose.onNodeWithTag(tag).getBoundsInRoot().let {
        with(compose.density) { Rect(it.left.toPx(), it.top.toPx(), it.right.toPx(), it.bottom.toPx()) }
    }

    // --- The tabs ---------------------------------------------------------------------------------------------------

    /**
     * Each tab of the book, in the standard build and then in the build without the server, reads [standard] and
     * [serverless] (the marked view's name, then the own view's); its words break, if at all, between words, and stand
     * whole inside the tab; a word drawn smaller to fit is never drawn smaller than the tabs' style at a font scale of 1
     * (T-217): one that does not fit even so ends the label with an ellipsis, on one line, unbroken.
     */
    private fun assertTabs(standard: List<String>, serverless: List<String>) {
        show(LibraryView.OWN)
        await(LibraryTags.SWITCH)
        listOf(Mode.Standard to standard, Mode.Serverless to serverless).forEach { (shownMode, names) ->
            compose.runOnIdle { mode = shownMode }
            compose.waitForIdle()
            assertOneSize(shownMode)
            listOf(LibraryView.MARKED, LibraryView.OWN).zip(names).forEach { (view, name) ->
                val layout = layoutOf(tabLabel(view))
                val text = layout.layoutInput.text.text
                assertEquals("$shownMode, the $view tab", name, text)
                for (line in 0 until layout.lineCount - 1) {
                    val end = layout.getLineEnd(line)
                    val between = text[end - 1].isWhitespace() || text.getOrNull(end)?.isWhitespace() == true
                    assertTrue("$shownMode, “$text” breaks inside a word after line $line", between)
                }
                // Each line, laid out, is no wider than the words' room.
                for (line in 0 until layout.lineCount) {
                    val width = layout.getLineRight(line) - layout.getLineLeft(line)
                    assertTrue("$shownMode, line $line of “$text” is $width wide", width <= layout.layoutInput.constraints.maxWidth + 0.5f)
                }
                // Never smaller than the tabs' style at a font scale of 1: as many pixels as its size in dp.
                val drawn = with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() }
                val least = with(layout.layoutInput.density) { tabStyle!!.fontSize.value.dp.toPx() }
                assertTrue("$shownMode, “$text” drawn at $drawn px, under the least $least px", drawn >= least - 0.5f)
                if (layout.hasVisualOverflow) assertEquals("$shownMode, “$text” cut short on one line", 1, layout.lineCount)
                val words = unclipped(tabLabel(view))
                val tab = unclipped(hasTestTag(LibraryTags.tab(view)))
                assertTrue("$shownMode, “$text” ($words) stands inside its tab ($tab)", tab.contains(words.topLeft) && words.right <= tab.right + 0.5f && words.bottom <= tab.bottom + 0.5f)
            }
        }
    }

    /**
     * The tabs' words are all drawn at one size (T-223): the tabs' style, unless a word of some tab would not fit its
     * room in it, and then the largest size at which every tab's widest word fits — or the least size, at a font scale
     * of 1, should none fit even there.
     */
    private fun assertOneSize(shownMode: Mode) {
        val layouts = listOf(LibraryView.MARKED, LibraryView.OWN).map { layoutOf(tabLabel(it)) }
        val sizes = layouts.map { with(it.layoutInput.density) { it.layoutInput.style.fontSize.toPx() } }
        assertEquals("$shownMode, the tabs' sizes $sizes", 1, sizes.map { Math.round(it * 10) }.distinct().size)
        val input = layouts.first().layoutInput
        val full = with(input.density) { tabStyle!!.fontSize.toPx() }
        val least = with(input.density) { tabStyle!!.fontSize.value.dp.toPx() }
        val drawn = sizes.first()
        if (drawn >= full - 0.5f || drawn <= least + 0.5f) return
        // Made smaller only because some tab's widest word does not fit its room at the tabs' style...
        val measurer = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
        fun widest(layout: TextLayoutResult, style: TextStyle) = widestWord(listOf(layout.layoutInput.text.text), style, measurer)
        val atFull = input.style.copy(fontSize = tabStyle!!.fontSize, lineHeight = tabStyle!!.lineHeight)
        assertTrue(
            "$shownMode, the tabs drawn at $drawn px though every word fits at $full px",
            layouts.any { widest(it, atFull) > it.layoutInput.constraints.maxWidth },
        )
        // ...and no smaller than the largest size at which every tab's widest word fits, but for fitWords' margin: a
        // word's width grows with its size in pixels.
        val fits = layouts.minOf { drawn * it.layoutInput.constraints.maxWidth / widest(it, it.layoutInput.style) }
        assertTrue("$shownMode, the tabs drawn at $drawn px where $fits px fit", drawn >= fits * 0.95f)
    }

    @Test
    fun theTabsNeverBreakInsideAWord() = assertTabs(listOf("Marked", "Written here"), listOf("Imported", "Written here"))

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun theTabsNeverBreakInsideAWordAtTwiceTheFontScale() =
        assertTabs(listOf("Marked", "Written here"), listOf("Imported", "Written here"))

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun inRussianTheTabsNeverBreakInsideAWord() =
        assertTabs(listOf("Отмеченные", "Мои рецепты"), listOf("Импортированные", "Мои рецепты"))

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianTheTabsNeverBreakInsideAWordAtTwiceTheFontScale() =
        assertTabs(listOf("Отмеченные", "Мои рецепты"), listOf("Импортированные", "Мои рецепты"))

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun onAWiderPhoneTheTabsAreDrawnAtOneSize() =
        assertTabs(listOf("Marked", "Written here"), listOf("Imported", "Written here"))

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun onAWiderPhoneTheTabsAreDrawnAtOneSizeAtTwiceTheFontScale() =
        assertTabs(listOf("Marked", "Written here"), listOf("Imported", "Written here"))

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianOnAWiderPhoneTheTabsAreDrawnAtOneSize() =
        assertTabs(listOf("Отмеченные", "Мои рецепты"), listOf("Импортированные", "Мои рецепты"))

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp", fontScale = 2f)
    fun inRussianOnAWiderPhoneTheTabsAreDrawnAtOneSizeAtTwiceTheFontScale() =
        assertTabs(listOf("Отмеченные", "Мои рецепты"), listOf("Импортированные", "Мои рецепты"))

    // T-217: on a very narrow window the words are still never drawn smaller than at a font scale of 1, nor broken.
    @Test
    @Config(qualifiers = "ru-w240dp-h640dp", fontScale = 2f)
    fun inRussianOnAVeryNarrowWindowTheTabsStayReadable() =
        assertTabs(listOf("Отмеченные", "Мои рецепты"), listOf("Импортированные", "Мои рецепты"))

    @Test
    @Config(qualifiers = "w240dp-h640dp", fontScale = 2f)
    fun onAVeryNarrowWindowTheTabsStayReadable() =
        assertTabs(listOf("Marked", "Written here"), listOf("Imported", "Written here"))

    // In the build without the server the screen reader is told the view shows the imported recipes, and its empty
    // state names them; the standard build keeps "marked".
    @Test
    fun withoutTheServerTheMarkedViewIsNamedForTheImportedRecipes() {
        show(LibraryView.MARKED, Mode.Serverless)
        await(LibraryTags.MARKED_EMPTY)
        assertEquals(listOf("No imported recipes yet"), compose.textsOf(LibraryTags.emptyHeadline(LibraryTags.MARKED_EMPTY)))
        assertEquals(
            "Show the recipes imported from files",
            compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        compose.runOnIdle { mode = Mode.Standard }
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(LibraryTags.emptyHeadline(LibraryTags.MARKED_EMPTY)) == listOf("No marked recipes yet") }
        assertEquals(
            "Show the recipes marked in this book",
            compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun inRussianWithoutTheServerTheMarkedViewIsNamedForTheImportedRecipes() {
        show(LibraryView.MARKED, Mode.Serverless)
        await(LibraryTags.MARKED_EMPTY)
        assertEquals(listOf("Импортированных рецептов пока нет"), compose.textsOf(LibraryTags.emptyHeadline(LibraryTags.MARKED_EMPTY)))
        assertEquals(
            "Показать рецепты, импортированные из файлов",
            compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
    }

    // --- The search field -------------------------------------------------------------------------------------------

    /**
     * The search field's label reads [label] on one line; focused, it rises onto the outline and stands whole below the
     * tabs, and the field shows [placeholder] on one line.
     */
    private fun assertSearchField(label: String, placeholder: String) {
        own("Soup")
        show(LibraryView.OWN)
        await(LibraryTags.SEARCH_LABEL)
        assertEquals(label, layoutOf(LibraryTags.SEARCH_LABEL).layoutInput.text.text)
        assertEquals(1, layoutOf(LibraryTags.SEARCH_LABEL).lineCount)
        compose.onNodeWithTag(LibraryTags.SEARCH).performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasTestTag(LibraryTags.SEARCH_PLACEHOLDER), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val shown = layoutOf(LibraryTags.SEARCH_PLACEHOLDER)
        assertEquals(placeholder, shown.layoutInput.text.text)
        assertEquals(1, shown.lineCount)
        assertEquals(1, layoutOf(LibraryTags.SEARCH_LABEL).lineCount)
        // The pages of the views clip what stands above them: the risen label stands wholly below their top, under the tabs.
        val risen = unclipped(hasTestTag(LibraryTags.SEARCH_LABEL))
        val pages = unclipped(hasTestTag(LibraryTags.PAGER))
        val tabs = unclipped(hasTestTag(LibraryTags.SWITCH))
        assertTrue("the label ($risen) stands below the top of the page ($pages)", risen.top >= pages.top - 0.5f)
        assertTrue("the label ($risen) stands below the tabs ($tabs)", risen.top >= tabs.bottom - 0.5f)
    }

    @Test
    fun theSearchFieldHasAShortLabel() = assertSearchField("Search", "Search by title or ingredient")

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun theSearchFieldHasAShortLabelAtTwiceTheFontScale() = assertSearchField("Search", "Search by title or ingredient")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun inRussianTheSearchFieldHasAShortLabel() = assertSearchField("Поиск", "Поиск по названию или ингредиенту")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianTheSearchFieldHasAShortLabelAtTwiceTheFontScale() =
        assertSearchField("Поиск", "Поиск по названию или ингредиенту")

    // --- The empty and nothing-found states and the view's action ---------------------------------------------------

    /**
     * The narrowed view's state tagged [LibraryTags.NOTHING_FOUND] keeps clear of the view's floating action tagged
     * [fab]: nothing of the state stands under it, and its action tagged [action], scrolled into view, stands whole
     * above it.
     */
    private fun assertClearOfTheAction(action: String, fab: String) {
        await(LibraryTags.NOTHING_FOUND)
        await(action)
        await(fab)
        compose.waitForIdle()
        val floating = clipped(fab)
        val state = clipped(LibraryTags.NOTHING_FOUND)
        assertTrue("the state ($state) ends above the floating action ($floating)", state.bottom <= floating.top + 0.5f)
        compose.onNodeWithTag(action).performScrollTo()
        compose.waitForIdle()
        val shown = clipped(action)
        val whole = unclipped(hasTestTag(action))
        assertEquals("the action is shown whole", whole.height, shown.height, 0.5f)
        assertFalse("the action ($shown) is covered by the floating action ($floating)", shown.overlaps(floating))
        val headline = clipped(LibraryTags.emptyHeadline(LibraryTags.NOTHING_FOUND))
        assertFalse("the headline ($headline) is covered by the floating action ($floating)", headline.overlaps(floating))
    }

    private fun aSearchThatFindsNothing() {
        own("Soup")
        show(LibraryView.OWN)
        await(LibraryTags.SEARCH)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("a long query that finds nothing at all")
        assertClearOfTheAction(LibraryTags.NOTHING_FOUND_CLEAR, LibraryTags.NEW)
    }

    private fun filtersThatMatchNothing() {
        own("Soup")
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.RU))
        show(LibraryView.OWN)
        assertClearOfTheAction(LibraryTags.NOTHING_FOUND_CLEAR_FILTERS, LibraryTags.NEW)
    }

    private fun anImportedSearchThatFindsNothing() {
        found("Found soup")
        show(LibraryView.MARKED, Mode.Serverless)
        await(LibraryTags.SEARCH)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("a long query that finds nothing at all")
        assertClearOfTheAction(LibraryTags.NOTHING_FOUND_CLEAR, LibraryTags.IMPORT)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun aSearchThatFindsNothingKeepsItsActionClearOfNewRecipe() = aSearchThatFindsNothing()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianASearchThatFindsNothingKeepsItsActionClearOfNewRecipe() = aSearchThatFindsNothing()

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun filtersThatMatchNothingKeepTheirActionClearOfNewRecipe() = filtersThatMatchNothing()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianFiltersThatMatchNothingKeepTheirActionClearOfNewRecipe() = filtersThatMatchNothing()

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun anImportedSearchThatFindsNothingKeepsItsActionClearOfImport() = anImportedSearchThatFindsNothing()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAnImportedSearchThatFindsNothingKeepsItsActionClearOfImport() = anImportedSearchThatFindsNothing()

    // --- The import --------------------------------------------------------------------------------------------------

    /**
     * In the build without the server, with a recipe imported, the marked view's floating action reads [short], stands
     * wholly inside the screen, and tells the screen reader it does [action] (T-223).
     */
    private fun assertTheImportFits(short: String, action: String) {
        found("Found soup")
        show(LibraryView.MARKED, Mode.Serverless)
        await(LibraryTags.MARKED_LIST)
        await(LibraryTags.IMPORT)
        compose.waitForIdle()
        val node = compose.onNodeWithTag(LibraryTags.IMPORT).fetchSemanticsNode()
        assertEquals(listOf(short), node.config[SemanticsProperties.Text].map { it.text })
        assertEquals(action, node.config[SemanticsActions.OnClick].label)
        val screen = compose.onRoot().getBoundsInRoot()
        val button = compose.onNodeWithTag(LibraryTags.IMPORT).getUnclippedBoundsInRoot()
        assertTrue(
            "the import ($button) lies inside the screen ($screen)",
            button.left >= screen.left - 0.5.dp && button.right <= screen.right + 0.5.dp &&
                button.top >= screen.top - 0.5.dp && button.bottom <= screen.bottom + 0.5.dp,
        )
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun theImportIsWholeOnScreenAtTwiceTheFontScale() = assertTheImportFits("Import", "Import a recipe file")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianTheImportIsWholeOnScreenAtTwiceTheFontScale() = assertTheImportFits("Импорт", "Импортировать файл рецепта")

    // --- The entry's menu -------------------------------------------------------------------------------------------

    /** The colour the text of the menu item tagged [tag] is drawn in. */
    private fun colourOf(tag: String): Color = layoutOf(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .layoutInput.style.color

    // Taking a recipe out of the book is destructive: Delete and Remove are drawn in the theme's error colour, as on the
    // card; Export keeps the menu's own colour.
    @Test
    @Config(qualifiers = "w320dp-h2000dp")
    fun deleteAndRemoveAreDrawnInTheErrorColour() {
        val colours = appColorScheme(context.resources, dark = false)
        val soup = own("Soup").id.value
        val pie = found("Found pie").id.value
        show(LibraryView.OWN, Mode.Serverless)
        await(LibraryTags.entry(soup))
        compose.openEntryMenu(soup)
        await(LibraryTags.delete(soup))
        assertEquals("Delete", colours.error, colourOf(LibraryTags.delete(soup)))
        assertEquals("Export", colours.onSurface, colourOf(LibraryTags.export(soup)))
        compose.closeEntryMenu(soup)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        await(LibraryTags.entry(pie))
        compose.openEntryMenu(pie)
        await(LibraryTags.remove(pie))
        assertEquals("Remove", colours.error, colourOf(LibraryTags.remove(pie)))
    }

    // --- The quick filters ------------------------------------------------------------------------------------------

    /** How tall the chip tagged [tag] is drawn: the rows of its picture where anything differs from the background. */
    private fun drawnHeight(tag: String): Dp {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val background = pixels[0, 0]
        val rows = (0 until pixels.height).count { y -> (0 until pixels.width).any { x -> pixels[x, y] != background } }
        return with(compose.density) { rows.toDp() }
    }

    // A quick filter is drawn at the chip's own 32 dp, its touch target 48 dp tall around it.
    @Test
    @Config(qualifiers = "w320dp-h2000dp")
    fun theQuickFiltersAreDrawn32DpTallWithA48DpTarget() {
        own("Soup")
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.EN))
        show(LibraryView.OWN)
        await(LibraryTags.LANGUAGE_FILTER)
        compose.waitForIdle()
        listOf(LibraryTags.FILTER_ADD, LibraryTags.LANGUAGE_FILTER).forEach { chip ->
            // The element the screen reader and a touch find is the touch target around the chip drawn.
            val target = compose.onNodeWithTag(chip).getUnclippedBoundsInRoot()
            val height = target.bottom - target.top
            val width = target.right - target.left
            assertTrue("$chip's target is $height tall", height >= 48.dp - 0.5.dp)
            assertTrue("$chip's target is $width wide", width >= 48.dp - 0.5.dp)
            val drawn = drawnHeight(chip)
            assertTrue("$chip is drawn $drawn tall", drawn in 31.dp..34.dp)
        }
    }
}
