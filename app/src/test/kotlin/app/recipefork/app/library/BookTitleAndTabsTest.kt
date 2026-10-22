package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
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
import app.recipefork.app.data.HeldLastBookView
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.app.ui.WorkingFamily
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
 * The book's title and tabs (v0.42, T-300): the title «Моя книга рецептов» in Literata 28 sp on a row of its own under
 * the shopping list's and the settings' actions, 48 dp, the whole scrolling away and returning; the tabs «Мои рецепты»
 * first and «Из файлов» second, the pages in the same order, a 2.5 dp `primary` indicator under the tab shown, the words
 * in Golos (titleSmall). The book opens on «Мои рецепты» the first time and on the view last left after (FLOW-058);
 * while entries are chosen the selection's bar stands in place of the title bar.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h780dp")
class BookTitleAndTabsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val lastView = HeldLastBookView()
    private val session = LibrarySession(SavedStateHandle(), lastView)
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private lateinit var scheme: ColorScheme
    private lateinit var type: Typography

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(title: String, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title)
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun found(title: String): Recipe {
        val recipe = Recipe.create(now, owner = null).copy(title = title).inState(RecipeImport.FOUND_STATE)
        runBlocking { assertTrue(recipes.addFound(recipe, now)) }
        return recipe
    }

    private fun show(view: LibraryView? = null, mode: Mode = Mode.Serverless) {
        view?.let(session::choose)
        compose.setContent {
            AppTheme {
                scheme = MaterialTheme.colorScheme
                type = MaterialTheme.typography
                AppNavHost(session, recipes, mode)
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun dp(value: Float) = with(compose.density) { value.dp.toPx() }

    private fun layoutOf(matcher: SemanticsMatcher): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private fun tabLabel(view: LibraryView) = hasTestTag(LibraryTags.TAB_LABEL) and hasAnyAncestor(hasTestTag(LibraryTags.tab(view)))

    private fun assertColour(message: String, x: Float, y: Float, colour: Color) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixel = Color(image.getPixel(x.toInt(), y.toInt()))
        val close = listOf(pixel.red - colour.red, pixel.green - colour.green, pixel.blue - colour.blue).all { abs(it) < 0.02f }
        assertTrue("$message: $pixel, not $colour", close)
    }

    // --- The title bar ---------------------------------------------------------------------------------------------

    @Test
    fun theTitleStandsInLiterata28OnItsOwnRowUnderTheActions() {
        own("Soup")
        show(LibraryView.OWN)
        await(LibraryTags.TITLE)
        val bar = bounds(LibraryTags.TOP_BAR)
        val title = bounds(LibraryTags.TITLE)
        val shopping = bounds(LibraryTags.SHOPPING)
        val settings = bounds(LibraryTags.SETTINGS)
        // The actions, 48 dp, at the end of the first row: the shopping list, then the settings.
        listOf(shopping, settings).forEach {
            assertEquals("an action 48 dp wide", dp(48f), it.width, 1f)
            assertEquals("an action 48 dp high", dp(48f), it.height, 1f)
            assertEquals("on the bar's first row", bar.top, it.top, 1f)
        }
        assertEquals("the settings at the bar's end", bar.right - dp(4f), settings.right, 1f)
        assertEquals("the shopping list before them", settings.left, shopping.right, 1f)
        // The title on its own row under them, at the screen's margin, the tabs under it.
        assertTrue("the title under the actions ($title, $settings)", title.top >= settings.bottom - 0.5f)
        assertEquals("at the start, 20 dp in", bar.left + dp(20f), title.left, 1f)
        assertEquals("8 dp above the bar's end", bar.bottom - dp(8f), title.bottom, 1f)
        assertTrue("the tabs under the bar", bounds(LibraryTags.SWITCH).top >= bar.bottom - 0.5f)
        val layout = layoutOf(hasTestTag(LibraryTags.TITLE))
        assertEquals("My recipe book", layout.layoutInput.text.text)
        assertEquals("in Literata", ReadingFamily, layout.layoutInput.style.fontFamily)
        assertEquals("28 sp", 28.sp, layout.layoutInput.style.fontSize)
        assertEquals(FontWeight.Medium, layout.layoutInput.style.fontWeight)
        assertTrue("a heading", SemanticsProperties.Heading in compose.onNodeWithTag(LibraryTags.TITLE).fetchSemanticsNode().config)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun inRussianTheTitleIsTheBooksName() {
        show(LibraryView.OWN)
        await(LibraryTags.TITLE)
        assertEquals("Моя книга рецептов", layoutOf(hasTestTag(LibraryTags.TITLE)).layoutInput.text.text)
        assertEquals("Мои рецепты", layoutOf(tabLabel(LibraryView.OWN)).layoutInput.text.text)
        assertEquals("Из файлов", layoutOf(tabLabel(LibraryView.MARKED)).layoutInput.text.text)
        assertTrue("«Мои рецепты» first", bounds(LibraryTags.tab(LibraryView.OWN)).right <= bounds(LibraryTags.tab(LibraryView.MARKED)).left + 0.5f)
    }

    // At twice the font scale the title wraps rather than being cut: the bar grows to hold it.
    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleTheTitleIsWhole() {
        show(LibraryView.OWN)
        await(LibraryTags.TITLE)
        val layout = layoutOf(hasTestTag(LibraryTags.TITLE))
        assertTrue("not cut", !layout.hasVisualOverflow)
        val title = bounds(LibraryTags.TITLE)
        val bar = bounds(LibraryTags.TOP_BAR)
        assertTrue("inside the bar ($title, $bar)", title.top >= bar.top && title.bottom <= bar.bottom + 0.5f && title.right <= bar.right + 0.5f)
    }

    @Test
    fun theTitleBarScrollsAwayWithItsTitleAndReturns() {
        (0 until 40).forEach { own("Recipe $it", minutesAgo = it.toLong()) }
        show(LibraryView.OWN)
        await(LibraryTags.NEW)
        val whole = bounds(LibraryTags.TOP_BAR).height
        assertTrue(whole > dp(48f))
        repeat(2) { compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput { swipeUp(durationMillis = 600) } }
        compose.waitUntil(PATIENCE_MS) { bounds(LibraryTags.TOP_BAR).height < 1f }
        compose.onNodeWithTag(LibraryTags.TITLE).assertIsNotDisplayed()
        compose.onNodeWithTag(LibraryTags.SETTINGS).assertIsNotDisplayed()
        compose.onNodeWithTag(LibraryTags.SWITCH).assertIsDisplayed()
        assertTrue("the tabs at the top", bounds(LibraryTags.SWITCH).top < 1f)
        // A little way back up, not to the list's top: the bar comes back whole, its title with it.
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput {
            val span = height * 0.2f
            swipeDown(startY = centerY - span / 2, endY = centerY + span / 2, durationMillis = 600)
        }
        compose.waitUntil(PATIENCE_MS) { abs(bounds(LibraryTags.TOP_BAR).height - whole) < 1f }
        compose.onNodeWithTag(LibraryTags.TITLE).assertIsDisplayed()
    }

    @Test
    fun whileSelectingTheSelectionsBarStandsInPlaceOfTheTitleBar() {
        val soup = own("Soup")
        show(LibraryView.OWN)
        await(LibraryTags.entry(soup.id.value))
        compose.onNodeWithTag(LibraryTags.entry(soup.id.value)).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_BAR) && !exists(LibraryTags.TITLE) && !exists(LibraryTags.SETTINGS) }
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.TITLE) && !exists(LibraryTags.SELECTION_BAR) }
    }

    // --- The tabs --------------------------------------------------------------------------------------------------

    private fun assertOwnFirst() {
        val own = bounds(LibraryTags.tab(LibraryView.OWN))
        val marked = bounds(LibraryTags.tab(LibraryView.MARKED))
        assertTrue("the own recipes' tab first ($own, $marked)", own.right <= marked.left + 0.5f)
        assertEquals(own.top, marked.top, 0.5f)
    }

    @Test
    fun myRecipesComesFirstInEitherMode() {
        show(LibraryView.OWN, Mode.Standard)
        await(LibraryTags.SWITCH)
        assertOwnFirst()
        assertEquals("My recipes", layoutOf(tabLabel(LibraryView.OWN)).layoutInput.text.text)
    }

    @Test
    fun myRecipesComesFirstBeforeFromFiles() {
        show(LibraryView.OWN)
        await(LibraryTags.SWITCH)
        assertOwnFirst()
        assertEquals("Imported", layoutOf(tabLabel(LibraryView.MARKED)).layoutInput.text.text)
        // The tabs within the screen's margins, 48 dp high.
        val switch = bounds(LibraryTags.SWITCH)
        val bar = bounds(LibraryTags.TOP_BAR)
        assertEquals("16 dp in at the start", bar.left + dp(16f), switch.left, 1f)
        assertEquals("16 dp in at the end", bar.right - dp(16f), switch.right, 1f)
        assertTrue(bounds(LibraryTags.tab(LibraryView.OWN)).height >= dp(48f) - 0.5f)
    }

    // The pages follow the tabs: from «My recipes» the next page, to the left, is «From files».
    @Test
    fun thePagesFollowTheTabs() {
        val soup = own("Soup")
        val pie = found("Pie")
        show(LibraryView.OWN)
        await(LibraryTags.entry(soup.id.value))
        // Past the first view, a swipe goes nowhere.
        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(LibraryView.OWN, session.view.value)
        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeLeft() }
        compose.waitUntil(PATIENCE_MS) { session.view.value == LibraryView.MARKED && exists(LibraryTags.entry(pie.id.value)) }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeRight() }
        compose.waitUntil(PATIENCE_MS) { session.view.value == LibraryView.OWN }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsSelected()
    }

    @Test
    fun theIndicatorIsAPrimaryBar2Point5DpUnderTheTabShown() {
        show(LibraryView.OWN)
        await(LibraryTags.TAB_INDICATOR)
        fun assertUnder(view: LibraryView) {
            compose.waitForIdle()
            val tab = bounds(LibraryTags.tab(view))
            val mark = bounds(LibraryTags.TAB_INDICATOR)
            assertEquals("2.5 dp high", dp(2.5f), mark.height, 1f)
            assertEquals("across the middle of its tab", tab.center.x, mark.center.x, 1f)
            assertEquals("68 % of the tab's width", tab.width * 0.68f, mark.width, 1.5f)
            assertEquals("at the foot of the tabs", bounds(LibraryTags.SWITCH).bottom, mark.bottom, 1f)
            assertColour("in primary", mark.center.x, mark.center.y, scheme.primary)
        }
        assertUnder(LibraryView.OWN)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitUntil(PATIENCE_MS) { session.view.value == LibraryView.MARKED }
        assertUnder(LibraryView.MARKED)
    }

    @Test
    fun theTabsWordsAreGolosTitleSmallTheShownInInk() {
        show(LibraryView.OWN)
        await(LibraryTags.SWITCH)
        LibraryView.entries.forEach { view ->
            val style = layoutOf(tabLabel(view)).layoutInput.style
            assertEquals("$view in Golos", WorkingFamily, style.fontFamily)
            assertEquals("$view as titleSmall", type.titleSmall.fontSize, style.fontSize)
            assertEquals(type.titleSmall.fontWeight, style.fontWeight)
        }
        assertEquals("the tab shown in the ink", scheme.onSurface, layoutOf(tabLabel(LibraryView.OWN)).layoutInput.style.color)
        assertEquals("the other muted", scheme.onSurfaceVariant, layoutOf(tabLabel(LibraryView.MARKED)).layoutInput.style.color)
    }

    // --- Where the book opens (FLOW-034 (6), FLOW-058) -------------------------------------------------------------

    @Test
    fun theBookOpensOnMyRecipesTheFirstTimeAndOnTheViewLastLeftAfter() {
        val soup = own("Soup")
        val pie = found("Pie")
        var shown by mutableStateOf(session)
        compose.setContent {
            AppTheme { key(shown) { AppNavHost(shown, recipes, Mode.Serverless) } }
        }
        // The first time: «My recipes», the first tab, its page shown.
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(soup.id.value)) }
        assertEquals(LibraryView.OWN, session.view.value)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsNotSelected()
        compose.onNodeWithTag(LibraryTags.OWN_LIST).assertIsDisplayed()
        // Left on «From files», a new session opens there: its tab, the second, and its page.
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitUntil(PATIENCE_MS) { session.view.value == LibraryView.MARKED }
        assertEquals(LibraryView.MARKED, lastView.read())
        val next = LibrarySession(SavedStateHandle(), lastView)
        compose.runOnUiThread { shown = next }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(pie.id.value)) }
        assertEquals(LibraryView.MARKED, next.view.value)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.MARKED_LIST).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.entry(pie.id.value)).assertIsDisplayed()
        assertOwnFirst()
        // And left on «My recipes» again, the next opens there.
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntil(PATIENCE_MS) { next.view.value == LibraryView.OWN }
        val third = LibrarySession(SavedStateHandle(), lastView)
        compose.runOnUiThread { shown = third }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(soup.id.value)) }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.OWN_LIST).assertIsDisplayed()
    }
}
