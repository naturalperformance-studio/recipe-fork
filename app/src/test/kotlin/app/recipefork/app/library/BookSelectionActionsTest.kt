package app.recipefork.app.library

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.WorkingFamily
import app.recipefork.app.ui.appColorScheme
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
 * v0.42, T-305 (scenario 6; decisions.md #5): while recipes are chosen, the top bar shows "N selected", "Select all" and
 * ✕, and a bar at the bottom holds five actions with words — To collection, To shopping, Print, PDF, Delete — each an
 * icon over a Golos 12 label, 72 dp wide on a 360 dp phone, at least 56 dp high, Delete in the error colour, each heard
 * as what it does; the view's floating action hides while the bar shows and returns once the selection ends.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookSelectionActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val scheme = appColorScheme(context.resources, dark = false)

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private val actions = listOf(
        LibraryTags.SELECTION_ADD_TO_COLLECTION, LibraryTags.SELECTION_ADD_TO_SHOPPING, LibraryTags.SELECTION_PRINT,
        LibraryTags.SELECTION_SHARE_PDF, LibraryTags.SELECTION_DELETE,
    )

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private fun textWithin(tag: String) = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(tag)),
        useUnmergedTree = true,
    )

    private fun layoutWithin(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        textWithin(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    private fun textBoundsWithin(tag: String): Rect = textWithin(tag).getUnclippedBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    /** What the node tagged [tag] reads, its spaces that never break read as plain ones. */
    private fun words(tag: String) = compose.textsOf(tag).joinToString("").replace('\u00A0', ' ')

    private fun clickLabel(tag: String) = AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(tag).fetchSemanticsNode())

    /** [count] own recipes in the book, shown; the first chosen by a long press. Returns them, newest first. */
    private fun selectOne(count: Int = 3): List<String> {
        val made = (1..count).map { n ->
            Recipe.create(now.minusSeconds(n * 60L), owner = null).copy(title = "Dish $n").also { runBlocking { recipes.save(it) } }
        }
        session.choose(LibraryView.OWN)
        compose.setContent { AppTheme { AppNavHost(session, recipes) } }
        val first = made.first().id.value
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(LibraryTags.entry(first)))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        // The view's action floats at the bottom before the selection.
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.NEW) }
        compose.onNodeWithTag(LibraryTags.entry(first)).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_ACTIONS) && exists(LibraryTags.SELECT_ALL) }
        compose.waitForIdle()
        return made.map { it.id.value }
    }

    private fun assertTheBars(count: String, selectAll: String, words: List<String>, said: List<String>) {
        selectOne()

        // Above: the count, "Select all" and ✕.
        assertEquals(count, words(LibraryTags.SELECTION_COUNT))
        assertEquals(selectAll, words(LibraryTags.SELECT_ALL))
        assertTrue(exists(LibraryTags.SELECTION_CLOSE))
        assertTrue(bounds(LibraryTags.SELECTION_CLOSE).right <= bounds(LibraryTags.SELECTION_COUNT).left)
        assertTrue(bounds(LibraryTags.SELECTION_COUNT).right <= bounds(LibraryTags.SELECT_ALL).left)

        // Below, at the bottom of the screen: the five, start to end, named by their words, heard as what they do.
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val bar = bounds(LibraryTags.SELECTION_ACTIONS)
        assertEquals("the bar at the bottom", root.bottom.value, bar.bottom, 0.5f)
        assertEquals(words, actions.map { compose.textsOf(it).single() })
        assertEquals(said, actions.map { clickLabel(it) })
        val boxes = actions.map { bounds(it) }
        boxes.zipWithNext().forEach { (a, b) -> assertTrue("$a before $b", a.right <= b.left + 0.5f) }
        // Their words stand level along the bar, whether a word takes one line or two.
        val tops = actions.map { textBoundsWithin(it).top }
        tops.forEach { assertEquals("the words' tops $tops", tops.first(), it, 0.5f) }
        actions.zip(boxes).forEach { (tag, box) ->
            compose.onNodeWithTag(tag).assertIsEnabled()
            assertEquals("$tag 72 dp wide", 72f, box.width, 0.5f)
            assertTrue("$tag at least 56 dp high: $box", box.height >= 55.5f)
            assertTrue("$tag in the bar", box.top >= bar.top - 0.5f && box.bottom <= bar.bottom + 0.5f)
            // Its word under its 24 dp icon, in Golos 12, whole.
            val words = textBoundsWithin(tag)
            assertTrue("$tag's word ($words) under its icon", words.top >= box.top + 24f)
            assertTrue("$tag's word ($words) within it ($box)", words.left >= box.left - 0.5f && words.right <= box.right + 0.5f)
            val layout = layoutWithin(tag)
            assertEquals("$tag's word in Golos", WorkingFamily, layout.layoutInput.style.fontFamily)
            assertEquals("$tag's word 12 sp", 12.sp, layout.layoutInput.style.fontSize)
            assertFalse("$tag's word cut short", layout.didOverflowWidth || layout.didOverflowHeight)
            val colour = layout.layoutInput.style.color
            if (tag == LibraryTags.SELECTION_DELETE) {
                assertEquals("Delete in the error colour", scheme.error, colour)
            } else {
                assertEquals("$tag in the bar's content colour", scheme.onSecondaryContainer, colour)
            }
        }
        // The bar is tinted as the selection's bar above.
        val pixels = compose.onNodeWithTag(LibraryTags.SELECTION_ACTIONS).captureToImage().toPixelMap()
        assertEquals(scheme.secondaryContainer, pixels[1, pixels.height / 2])
        AccessibilityChecks.assertTouchTargets(compose)

        // The floating action hides while the bar shows, and returns once the selection ends.
        assertFalse("the view's action shows over the selection's", exists(LibraryTags.NEW))
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.SELECTION_ACTIONS) && session.selection.value == null }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.NEW) }
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp")
    fun theSelectionsActionsStandAtTheBottomWithWords() = assertTheBars(
        "1 selected",
        "Select all",
        listOf("To collection", "To shopping", "Print", "PDF", "Delete"),
        listOf("Add to collection", "Add to shopping list", "Print", "Share as PDF", "Delete"),
    )

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun inRussianTheSelectionsActionsStandAtTheBottomWithWords() = assertTheBars(
        "Выбран 1",
        "Выбрать все",
        listOf("В коллекцию", "В покупки", "Печать", "PDF", "Удалить"),
        listOf("Добавить в коллекцию", "Добавить в список покупок", "Распечатать", "Поделиться PDF", "Удалить"),
    )

    // "Select all" in the top bar chooses every entry the view lists, as the ⋮'s did.
    @Test
    @Config(qualifiers = "w360dp-h780dp")
    fun selectAllInTheTopBarChoosesEveryEntry() {
        selectOne()
        compose.onNodeWithTag(LibraryTags.SELECT_ALL).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 3 }
        compose.waitUntil(PATIENCE_MS) { words(LibraryTags.SELECTION_COUNT) == "3 selected" }
    }

    // While choosing, the list keeps no room for the hidden floating action: at the end of a long list, the last row
    // stands just above the selection's actions, 16 dp at most.
    @Test
    @Config(qualifiers = "w360dp-h780dp")
    fun theLastRowStandsJustAboveTheActions() {
        val ids = selectOne(count = 20)
        val last = ids.last()
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(LibraryTags.entry(last)))
        repeat(3) { compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val row = bounds(LibraryTags.entry(last))
        val bar = bounds(LibraryTags.SELECTION_ACTIONS)
        val gap = bar.top - row.bottom
        assertTrue("the last row ($row) clear of the actions ($bar)", gap >= -0.5f)
        assertTrue("the last row $gap dp above the actions, no room for the hidden action", gap <= 16.5f)
    }

    // At twice the font scale the five words share one size — the least any of them needs — so that the bar reads
    // even; every word stands whole.
    @Test
    @Config(qualifiers = "w360dp-h780dp", fontScale = 2f)
    fun atTwiceTheFontScaleTheWordsShareOneSize() = assertEvenWords()

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheWordsShareOneSize() = assertEvenWords()

    private fun assertEvenWords() {
        selectOne()
        val layouts = actions.map { layoutWithin(it) }
        val sizes = layouts.map { it.layoutInput.style.fontSize }
        assertEquals("one size: $sizes", 1, sizes.distinct().size)
        actions.zip(layouts).forEach { (tag, layout) ->
            assertFalse("$tag's word cut short", layout.didOverflowWidth || layout.didOverflowHeight)
            val words = textBoundsWithin(tag)
            val box = bounds(tag)
            assertTrue("$tag's word ($words) within it ($box)", words.left >= box.left - 0.5f && words.right <= box.right + 0.5f)
        }
    }
}
