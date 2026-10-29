package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
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
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.Recipe
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
 * The book's empty states at large text (v0.43, T-310; v0.42 fork F-3, option B). An empty state that does not fit its
 * space leaves out its decorative picture — the engraving or the search glyph — and only then: its words and its way
 * out stay, and at 320 × 711 dp with twice the font scale the way out shows without scrolling, in English and in
 * Russian — the book's first launch, the empty view of imported recipes and a search that finds nothing. Where the state
 * fits, the picture stays. The headline breaks between words and after a hyphen, as a book entry's title does.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyStatesAtLargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun show(view: LibraryView, mode: Mode) {
        session.choose(view)
        compose.setContent { AppTheme { AppNavHost(session, recipes, mode) } }
        compose.waitForIdle()
    }

    /** The book's first launch: nothing written, the own view shown. */
    private fun firstLaunch() {
        show(LibraryView.OWN, Mode.Serverless)
        await(LibraryTags.WRITE)
    }

    /** The view of recipes imported from files («Из файлов»), empty, in the build without the server. */
    private fun noImportedRecipes() {
        show(LibraryView.MARKED, Mode.Serverless)
        await(LibraryTags.IMPORT)
    }

    /** A search in the own view that finds nothing. */
    private fun aSearchThatFindsNothing() {
        runBlocking { recipes.save(Recipe.create(Instant.parse("2026-10-10T12:00:00Z"), owner = null).copy(title = "Soup")) }
        show(LibraryView.OWN, Mode.Serverless)
        await(LibraryTags.SEARCH)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("pilaf")
        await(LibraryTags.NOTHING_FOUND_CLEAR)
        compose.waitForIdle()
    }

    private fun rect(bounds: androidx.compose.ui.unit.DpRect): Rect = with(compose.density) {
        Rect(bounds.left.toPx(), bounds.top.toPx(), bounds.right.toPx(), bounds.bottom.toPx())
    }

    /**
     * The state tagged [state] keeps its headline, its sentence when [sentence], and its way out tagged [action]; it
     * has nothing to scroll, and its way out stands whole on the screen, unscrolled. Its picture, [side] dp tall and
     * [gap] dp above the headline, is left out only where it would not have fit: the words with the picture above them
     * would be taller than the state's space within its 8 dp margins. Returns whether the picture was left out.
     */
    private fun assertTheWayOutShowsWithoutScrolling(state: String, action: String, sentence: Boolean, side: Int, gap: Int): Boolean {
        assertTrue("$state keeps its headline", exists(LibraryTags.emptyHeadline(state)))
        assertEquals("$state keeps its sentence", sentence, exists(LibraryTags.emptySentence(state)))
        val range = compose.onNodeWithTag(state).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("$state has nothing to scroll", 0f, range.maxValue(), 0.5f)
        assertEquals("$state is not scrolled", 0f, range.value(), 0.5f)
        val shown = rect(compose.onNodeWithTag(action).getBoundsInRoot())
        val whole = rect(compose.onNodeWithTag(action).getUnclippedBoundsInRoot())
        val screen = rect(compose.onRoot().getBoundsInRoot())
        assertEquals("$action is shown whole", whole.height, shown.height, 0.5f)
        assertTrue("$action ($whole) stands on the screen ($screen)", whole.top >= screen.top - 0.5f && whole.bottom <= screen.bottom + 0.5f)
        val dropped = !exists(LibraryTags.emptyPicture(state))
        if (dropped) {
            val space = rect(compose.onNodeWithTag(state).getBoundsInRoot()).height
            val headline = rect(compose.onNodeWithTag(LibraryTags.emptyHeadline(state), useUnmergedTree = true).getUnclippedBoundsInRoot())
            val withPicture = with(compose.density) { whole.bottom - headline.top + (side + gap + 2 * 8).dp.toPx() }
            assertTrue("$state left its picture out though it fit ($withPicture in $space)", withPicture > space + 0.5f)
        }
        return dropped
    }

    /** The picture of the state tagged [state] stays, [side] dp square, above the headline. */
    private fun assertThePictureStays(state: String, side: Int) {
        await(LibraryTags.emptyPicture(state))
        val picture = rect(compose.onNodeWithTag(LibraryTags.emptyPicture(state), useUnmergedTree = true).getBoundsInRoot())
        val headline = rect(compose.onNodeWithTag(LibraryTags.emptyHeadline(state), useUnmergedTree = true).getBoundsInRoot())
        with(compose.density) {
            assertEquals("$state's picture width", side.dp.toPx(), picture.width, 1f)
            assertEquals("$state's picture height", side.dp.toPx(), picture.height, 1f)
        }
        assertTrue("$state's picture ($picture) above its headline ($headline)", picture.bottom <= headline.top + 0.5f)
    }

    private fun layoutOf(tag: String): TextLayoutResult = mutableListOf<TextLayoutResult>().also {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(it)
    }.single()

    /**
     * The headline of the state tagged [state] reads [words] and breaks only between words or after a hyphen; drawn no
     * smaller than [style] at a font scale of 1, and hyphenated across lines only when [hyphenated].
     */
    private fun assertTheHeadlineBreaksBetweenWords(state: String, words: String, style: TextStyle, hyphenated: Boolean = false) {
        val tag = LibraryTags.emptyHeadline(state)
        assertEquals(listOf(words), compose.textsOf(tag))
        val layout = layoutOf(tag)
        val drawn = layout.layoutInput.text.text
        assertEquals(words, drawn.replace("​", ""))
        assertEquals("$words: hyphenated across lines", hyphenated, layout.layoutInput.style.hyphens == Hyphens.Auto)
        assertFalse("$words: nothing beyond the last line", layout.hasVisualOverflow)
        if (!hyphenated) {
            (0 until layout.lineCount - 1).forEach {
                val before = drawn[layout.getLineEnd(it) - 1]
                assertTrue("$words: line $it ends inside a word ('$before')", before == ' ' || before == '​' || before == '-')
            }
        }
        with(layout.layoutInput.density) {
            val least = style.fontSize.value.dp.toPx()
            assertTrue("$words: drawn under its size at a font scale of 1", layout.layoutInput.style.fontSize.toPx() >= least - 0.5f)
        }
    }

    // --- Criterion 1: at 320 × 711 dp, twice the font scale, the way out shows without scrolling ---------------------

    @Test
    @Config(qualifiers = "w320dp-h711dp", fontScale = 2f)
    fun atFirstLaunchTheWayOutShowsWithoutScrolling() {
        firstLaunch()
        assertTheWayOutShowsWithoutScrolling(LibraryTags.OWN_EMPTY, LibraryTags.WRITE, sentence = true, side = 112, gap = 16)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h711dp", fontScale = 2f)
    fun inRussianAtFirstLaunchTheWayOutShowsWithoutScrolling() {
        firstLaunch()
        assertTheWayOutShowsWithoutScrolling(LibraryTags.OWN_EMPTY, LibraryTags.WRITE, sentence = true, side = 112, gap = 16)
    }

    // The first launch was reported at 640 dp in Russian, too: there the engraving does not fit, and is left out.
    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtFirstLaunchOn640dpTheEngravingMakesWayForTheWayOut() {
        firstLaunch()
        assertTrue(assertTheWayOutShowsWithoutScrolling(LibraryTags.OWN_EMPTY, LibraryTags.WRITE, sentence = true, side = 112, gap = 16))
    }

    @Test
    @Config(qualifiers = "w320dp-h711dp", fontScale = 2f)
    fun withNoImportedRecipesTheWayOutShowsWithoutScrolling() {
        noImportedRecipes()
        assertTheWayOutShowsWithoutScrolling(LibraryTags.MARKED_EMPTY, LibraryTags.IMPORT, sentence = true, side = 112, gap = 16)
    }

    // «Из файлов» in Russian: the engraving does not fit, and is left out.
    @Test
    @Config(qualifiers = "ru-w320dp-h711dp", fontScale = 2f)
    fun inRussianWithNoImportedRecipesTheEngravingMakesWayForTheWayOut() {
        noImportedRecipes()
        assertTrue(assertTheWayOutShowsWithoutScrolling(LibraryTags.MARKED_EMPTY, LibraryTags.IMPORT, sentence = true, side = 112, gap = 16))
    }

    @Test
    @Config(qualifiers = "w320dp-h711dp", fontScale = 2f)
    fun whenASearchFindsNothingTheWayOutShowsWithoutScrolling() {
        aSearchThatFindsNothing()
        assertTheWayOutShowsWithoutScrolling(LibraryTags.NOTHING_FOUND, LibraryTags.NOTHING_FOUND_CLEAR, sentence = false, side = 56, gap = 8)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h711dp", fontScale = 2f)
    fun inRussianWhenASearchFindsNothingTheWayOutShowsWithoutScrolling() {
        aSearchThatFindsNothing()
        assertTheWayOutShowsWithoutScrolling(LibraryTags.NOTHING_FOUND, LibraryTags.NOTHING_FOUND_CLEAR, sentence = false, side = 56, gap = 8)
    }

    // --- Criterion 2: where the state fits, the picture stays ----------------------------------------------------------

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun atFirstLaunchOnAnOrdinaryPhoneTheEngravingStays() {
        firstLaunch()
        assertThePictureStays(LibraryTags.OWN_EMPTY, 112)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp")
    fun inRussianWithNoImportedRecipesOnAnOrdinaryPhoneTheEngravingStays() {
        noImportedRecipes()
        assertThePictureStays(LibraryTags.MARKED_EMPTY, 112)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp")
    fun inRussianWhenASearchFindsNothingOnAnOrdinaryPhoneTheGlyphStays() {
        aSearchThatFindsNothing()
        assertThePictureStays(LibraryTags.NOTHING_FOUND, 56)
    }

    // --- Criterion 3: the headline never breaks inside a word without a hyphen ----------------------------------------

    @Test
    @Config(qualifiers = "ru-w320dp-h711dp", fontScale = 2f)
    fun inRussianTheHeadlinesBreakBetweenWords() {
        var large: TextStyle? = null
        session.choose(LibraryView.OWN)
        compose.setContent {
            AppTheme {
                large = MaterialTheme.typography.titleLarge
                AppNavHost(session, recipes, Mode.Serverless)
            }
        }
        await(LibraryTags.WRITE)
        assertTheHeadlineBreaksBetweenWords(LibraryTags.OWN_EMPTY, "Своих рецептов пока нет", large!!)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!()
        await(LibraryTags.IMPORT)
        compose.waitForIdle()
        assertTheHeadlineBreaksBetweenWords(LibraryTags.MARKED_EMPTY, "Импортированных рецептов пока нет", large!!)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h711dp", fontScale = 2f)
    fun inRussianASearchHeadlineBreaksBetweenWords() {
        var style: TextStyle? = null
        runBlocking { recipes.save(Recipe.create(Instant.parse("2026-10-10T12:00:00Z"), owner = null).copy(title = "Суп")) }
        session.choose(LibraryView.OWN)
        compose.setContent {
            AppTheme {
                style = MaterialTheme.typography.titleMedium
                AppNavHost(session, recipes, Mode.Serverless)
            }
        }
        await(LibraryTags.SEARCH)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("картофельно-грибная")
        await(LibraryTags.NOTHING_FOUND_CLEAR)
        compose.waitForIdle()
        assertTheHeadlineBreaksBetweenWords(LibraryTags.NOTHING_FOUND, "По запросу «картофельно-грибная» ничего не найдено", style!!)
    }

    /** An empty state with [headline], shown alone in the theme; the headline's style at a font scale of 1 is returned. */
    private fun showAlone(headline: String): TextStyle {
        var style: TextStyle? = null
        compose.setContent {
            AppTheme {
                style = MaterialTheme.typography.titleLarge
                EmptyState(Engraving.MARK, headline, sentence = null, action = null, tag = ALONE, centred = true)
            }
        }
        compose.waitForIdle()
        return style!!
    }

    // A word too wide for the line even at its size at a font scale of 1 is the one case hyphenated across lines.
    @Test
    @Config(qualifiers = "ru-w320dp-h711dp", fontScale = 2f)
    fun onlyAWordTooWideEvenAtItsLeastSizeIsHyphenated() {
        val words = "Рентгеноэлектрокардиографических нет"
        val style = showAlone(words)
        assertTheHeadlineBreaksBetweenWords(ALONE, words, style, hyphenated = true)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h711dp", fontScale = 2f)
    fun aWordThatFitsAtALesserSizeIsMadeSmallerNotBroken() {
        val words = "Своих рецептов пока нет"
        val style = showAlone(words)
        assertTheHeadlineBreaksBetweenWords(ALONE, words, style)
    }

    private companion object {
        const val ALONE = "alone"
    }
}
