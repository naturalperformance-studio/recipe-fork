package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mark2b
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.shopping.HeldShopping
import app.recipefork.app.shopping.ShoppingEnvironment
import app.recipefork.app.shopping.ShoppingScreen
import app.recipefork.app.shopping.ShoppingTags
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.app.ui.appColorScheme
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The engraved empty states of the redesign (v0.42, T-306; redesign-1 § 5 "Empty state": 96–112 dp, Literata 22).
 * The book's empty sections show mark 2b with its light hairline cuts (mark-2.html, "Empty state, engraved mark"),
 * 112 dp and decorative, over a headline in Literata 22, with the words and the button they had; the empty shopping
 * list shows the engraved cart of redesign-1 § 4.5, 96 dp, the same way. The no-result states keep their own small
 * picture, their words and their one way out.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h780dp-xxhdpi")
class EngravedEmptyStatesTest {
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

    private fun scheme(dark: Boolean): ColorScheme = appColorScheme(context.resources, dark)

    private fun showBook(view: LibraryView, mode: Mode, dark: Boolean = false) {
        session.choose(view)
        compose.setContent { AppTheme(dark = dark) { AppNavHost(session, recipes, mode) } }
        compose.waitForIdle()
    }

    private fun showShopping(dark: Boolean = false) {
        val environment = ShoppingEnvironment(HeldShopping(), ingredients = IngredientEntries { emptyList() })
        compose.setContent { AppTheme(dark = dark) { ShoppingScreen(environment, recipes, onBack = {}) } }
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.EMPTY) }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun layoutOf(tag: String): TextLayoutResult = mutableListOf<TextLayoutResult>().also {
        node(tag).config[SemanticsActions.GetTextLayoutResult].action!!.invoke(it)
    }.single()

    private fun sizeOf(tag: String): Pair<Dp, Dp> = with(compose.density) {
        val bounds = node(tag).boundsInRoot
        bounds.width.toDp() to bounds.height.toDp()
    }

    /** Nothing inside the picture tagged [tag] is read out: no description, no text — the words say what is empty. */
    private fun assertDecorative(tag: String) {
        val inside = compose.onAllNodes(
            hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(inside.isNotEmpty())
        for (each in inside) {
            assertEquals(null, each.config.getOrNull(SemanticsProperties.ContentDescription))
            assertEquals(null, each.config.getOrNull(SemanticsProperties.Text))
        }
    }

    /** The headline of the state tagged [state] is set in Literata (the reading family) at 22 sp, read as a heading. */
    private fun assertHeadlineIsLiterata22(state: String, headline: String) {
        val head = LibraryTags.emptyHeadline(state)
        assertEquals(listOf(headline), compose.textsOf(head))
        val style = layoutOf(head).layoutInput.style
        assertEquals(22.sp, style.fontSize)
        assertEquals(ReadingFamily, style.fontFamily)
        assertTrue(SemanticsProperties.Heading in node(head).config)
    }

    private fun distance(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    /** The colour of the picture [pixels] at the point ([x], [y]) of its [box]-unit drawing. */
    private fun at(pixels: PixelMap, box: Double, x: Double, y: Double): Color =
        pixels[(pixels.width * x / box).toInt(), (pixels.height * y / box).toInt()]

    /** Of the pixels around the point ([x], [y]) of the [box]-unit drawing, the one nearest to [colour]. */
    private fun nearestAround(pixels: PixelMap, box: Double, x: Double, y: Double, colour: Color): Color {
        val cx = (pixels.width * x / box).toInt()
        val cy = (pixels.height * y / box).toInt()
        return (-2..2).flatMap { dx -> (-2..2).map { dy -> pixels[cx + dx, cy + dy] } }.minBy { distance(it, colour) }
    }

    private fun assertColour(what: String, expected: Color, actual: Color) =
        assertTrue("$what is $actual, not $expected", distance(expected, actual) < 3f / 255)

    /**
     * The engraved mark in the state tagged [state]: 112 dp, decorative, drawn in the scheme's colours — the fork in
     * ink, its bent tine in beet, and light hairlines cut into the handle and the left tine in the paper's colour.
     */
    private fun assertEngravedMark(state: String, colours: ColorScheme) {
        val picture = LibraryTags.emptyPicture(state)
        compose.waitUntil(PATIENCE_MS) { exists(picture) }
        assertEquals(112.dp to 112.dp, sizeOf(picture))
        assertDecorative(picture)
        val pixels = compose.onNodeWithTag(picture, useUnmergedTree = true).captureToImage().toPixelMap()
        val (hx, hy) = Mark2b.inBox(Mark2b.HANDLE.first, Mark2b.HANDLE.second)
        assertColour("the handle", colours.onSurface, at(pixels, 48.0, hx, hy))
        val (tx, ty) = Mark2b.inBox(Mark2b.BENT_TINE.first, Mark2b.BENT_TINE.second)
        assertColour("the bent tine", colours.tertiary, at(pixels, 48.0, tx, ty))
        // The middle of the hairline cut down the left tine (m2be's fourth cut, "M-4.6 -22.4Q-5.3 -17.5 -5.3 -12.8").
        val (cx, cy) = Mark2b.inBox(0.25 * -4.6 + 0.5 * -5.3 + 0.25 * -5.3, 0.25 * -22.4 + 0.5 * -17.5 + 0.25 * -12.8)
        assertColour("the cut in the left tine", colours.background, nearestAround(pixels, 48.0, cx, cy, colours.background))
        // The middle of the third cut along the handle ("M.75 6.5C.9 12 .4 17 .4 22.4"), near its point at t = 0.5.
        val (kx, ky) = Mark2b.inBox(0.125 * 0.75 + 0.375 * 0.9 + 0.375 * 0.4 + 0.125 * 0.4, 0.125 * 6.5 + 0.375 * 12 + 0.375 * 17 + 0.125 * 22.4)
        assertColour("the cut in the handle", colours.background, nearestAround(pixels, 48.0, kx, ky, colours.background))
    }

    // Criterion 1: the own section of an empty book — the engraved mark, a Literata 22 headline, the same words and button.
    @Test
    fun theEmptyOwnSectionShowsTheEngravedMarkOverALiterataHeadline() {
        showBook(LibraryView.OWN, Mode.Standard)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_EMPTY), PATIENCE_MS)
        assertEngravedMark(LibraryTags.OWN_EMPTY, scheme(dark = false))
        assertHeadlineIsLiterata22(LibraryTags.OWN_EMPTY, "No recipes of yours yet")
        assertEquals(listOf("Recipes you write in this book are kept here."), compose.textsOf(LibraryTags.emptySentence(LibraryTags.OWN_EMPTY)))
        assertEquals(listOf("New recipe"), compose.textsOf(LibraryTags.WRITE))
    }

    // Criterion 1, in the dark scheme: the mark takes the dark scheme's ink, beet and paper.
    @Test
    fun inTheDarkSchemeTheMarkTakesTheDarkColours() {
        showBook(LibraryView.OWN, Mode.Standard, dark = true)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_EMPTY), PATIENCE_MS)
        assertEngravedMark(LibraryTags.OWN_EMPTY, scheme(dark = true))
    }

    // Criterion 1: the section of recipes from files, empty, shows the same mark, with its own words and button.
    @Test
    fun theEmptyFilesSectionShowsTheEngravedMarkWithItsWordsAndButton() {
        showBook(LibraryView.MARKED, Mode.Serverless)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.MARKED_EMPTY), PATIENCE_MS)
        assertEngravedMark(LibraryTags.MARKED_EMPTY, scheme(dark = false))
        assertHeadlineIsLiterata22(LibraryTags.MARKED_EMPTY, "No imported recipes yet")
        assertEquals(listOf("Recipes you import from files are kept here."), compose.textsOf(LibraryTags.emptySentence(LibraryTags.MARKED_EMPTY)))
        assertEquals(listOf("Import a recipe file"), compose.textsOf(LibraryTags.IMPORT))
    }

    // Criterion 1, in the standard build: the marked section's empty state shows the mark, and still no button.
    @Test
    fun theEmptyMarkedSectionInTheStandardBuildShowsTheMarkAndNoButton() {
        showBook(LibraryView.MARKED, Mode.Standard)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.MARKED_EMPTY), PATIENCE_MS)
        assertEngravedMark(LibraryTags.MARKED_EMPTY, scheme(dark = false))
        assertHeadlineIsLiterata22(LibraryTags.MARKED_EMPTY, "No marked recipes yet")
        assertTrue(!exists(LibraryTags.IMPORT))
    }

    // Criterion 2: the empty shopping list shows the engraved cart, 96 dp, decorative, in the muted ink, over a Literata
    // 22 headline, with its words as before.
    @Test
    fun theEmptyShoppingListShowsTheEngravedCart() {
        showShopping()
        val picture = LibraryTags.emptyPicture(ShoppingTags.EMPTY)
        assertEquals(96.dp to 96.dp, sizeOf(picture))
        assertDecorative(picture)
        val colours = scheme(dark = false)
        val pixels = compose.onNodeWithTag(picture, useUnmergedTree = true).captureToImage().toPixelMap()
        // The middle of the basket's bottom line, "l5 24h26" from (17, 36) to (43, 36) of the 56-unit drawing.
        assertColour("the cart's line", colours.onSurfaceVariant, nearestAround(pixels, 56.0, 30.0, 36.0, colours.onSurfaceVariant))
        // Inside the basket, between its lines, the paper shows.
        assertColour("the basket's inside", colours.background, at(pixels, 56.0, 30.0, 28.5))
        assertHeadlineIsLiterata22(ShoppingTags.EMPTY, "Your shopping list is empty")
        assertEquals(
            listOf(
                "Choose “Add to shopping list” in a recipe's ⋮ menu — in the book or on its card — or long-press to select " +
                    "several recipes in the book; “Add item” adds anything else.",
            ),
            compose.textsOf(LibraryTags.emptySentence(ShoppingTags.EMPTY)),
        )
        assertTrue(exists(ShoppingTags.ADD))
    }

    // Criterion 3: a search that finds nothing keeps its small picture, its words in the usual headline and its one way
    // out; it takes no engraving.
    @Test
    fun aSearchThatFindsNothingKeepsItsPictureWordsAndOneWayOut() {
        runBlocking { recipes.save(Recipe.create(Instant.parse("2026-10-04T12:00:00Z"), owner = null).copy(title = "Soup")) }
        showBook(LibraryView.OWN, Mode.Standard)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.SEARCH), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("zzz")
        val head = LibraryTags.emptyHeadline(LibraryTags.NOTHING_FOUND)
        compose.waitUntil(PATIENCE_MS) { exists(head) && compose.textsOf(head) == listOf("Nothing found for “zzz”") }
        assertEquals(56.dp to 56.dp, sizeOf(LibraryTags.emptyPicture(LibraryTags.NOTHING_FOUND)))
        assertEquals(16.sp, layoutOf(head).layoutInput.style.fontSize)
        val acts = SemanticsMatcher("acts on a tap") { SemanticsActions.OnClick in it.config }
        val ways = compose.onAllNodes(acts and hasAnyAncestor(hasTestTag(LibraryTags.NOTHING_FOUND))).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertEquals(listOf(LibraryTags.NOTHING_FOUND_CLEAR), ways)
        assertEquals(listOf("Clear the search"), compose.textsOf(LibraryTags.NOTHING_FOUND_CLEAR))
    }
}
