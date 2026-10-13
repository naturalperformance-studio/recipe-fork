package app.recipefork.app.shopping

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.isDragHandle
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppTheme
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
import kotlin.math.abs

/**
 * The shopping list at large text, with check boxes (v0.38, T-268, scenarios 1 and 3), on a 320 dp phone: the bar's
 * title keeps to one line inside the bar, over nothing of the list; "Add item" shrinks to its icon on the way down and
 * shows its words again on the way up, and at large text starts and stays shrunk, covering no line the list cannot
 * scroll clear of it; each line shows a check box drawn as it is ticked, the whole line still the one target the screen
 * reader hears as a check box; the empty list names every way onto it; "Add item"'s sheet has Material's drag handle; a
 * notice puts its action on a line of its own at large text. In English and Russian.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class ShoppingAtLargeTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val store = HeldShopping()
    private val shopping = ShoppingEnvironment(store, EquipmentSource { SHOPPING_EQUIPMENT }, ingredients = IngredientEntries { SHOPPING_DICTIONARY })

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String, unmerged: Boolean = false): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot

    private fun dp(px: Float) = px / context.resources.displayMetrics.density

    /** The list holding [own] lines of the reader's own, in their order, shown and read. */
    private fun show(vararg own: String) {
        store.update { list -> own.foldIndexed(list) { i, kept, text -> kept.with(OwnLine(POOL, "$i", text)) } }
        compose.setContent { AppTheme { ShoppingScreen(shopping, recipes, onBack = {}) } }
        compose.waitUntil(PATIENCE_MS) { exists(if (own.isEmpty()) ShoppingTags.EMPTY else ShoppingTags.line(0)) }
        compose.waitForIdle()
    }

    private fun many(count: Int) = Array(count) { "item ${it + 1}" }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult =
        mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()

    // --- The bar's title --------------------------------------------------------------------------------------------

    /** The title reads [title] on one line, whole, inside the bar and beside its actions; the list starts below the bar. */
    private fun assertTitleFits(title: String) {
        show("batteries")
        val node = compose.onNodeWithTag(ShoppingTags.TITLE, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(listOf(title), compose.textsOf(ShoppingTags.TITLE))
        val layout = layoutOf(node)
        assertEquals("$title takes ${layout.lineCount} lines", 1, layout.lineCount)
        assertFalse("$title is cut", layout.isLineEllipsized(0) || layout.didOverflowHeight)
        // Made smaller where it must, but never below its size at a font scale of 1 (22 sp).
        val drawn = layout.layoutInput.style.fontSize.value * context.resources.configuration.fontScale
        assertTrue("$title is drawn at $drawn dp", drawn >= 22f - 0.5f)
        val bar = bounds(ShoppingTags.BAR)
        val shown = node.boundsInRoot
        assertTrue("$title ($shown) spills out of the bar ($bar)", shown.top >= bar.top - 0.5f && shown.bottom <= bar.bottom + 0.5f)
        listOf(ShoppingTags.BACK, ShoppingTags.MENU).forEach { assertFalse("$title overlaps $it", shown.overlaps(bounds(it))) }
        assertTrue("the list starts under the bar", bounds(ShoppingTags.LIST).top >= bar.bottom - 0.5f)
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleTheTitleKeepsToOneLineInsideTheBar() = assertTitleFits("Shopping list")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheTitleKeepsToOneLineInsideTheBar() = assertTitleFits("Список покупок")

    // --- "Add item" -------------------------------------------------------------------------------------------------

    private fun addWidth(): Float = dp(bounds(ShoppingTags.ADD).width)

    private fun swipeListUp() = compose.onNodeWithTag(ShoppingTags.LIST).performTouchInput { swipeUp(durationMillis = 1_000) }

    private fun swipeListDown() = compose.onNodeWithTag(ShoppingTags.LIST).performTouchInput {
        swipeDown(startY = centerY, endY = centerY + 200f, durationMillis = 1_000)
    }

    // At the usual font scale it shows its words at the top, shrinks to its icon as the list goes down, and shows them
    // again as the reader drags the list back up.
    @Test
    fun atTheUsualFontScaleAddItemShrinksOnTheWayDownAndGrowsOnTheWayUp() {
        show(*many(30))
        assertTrue("Add item is ${addWidth()} dp wide at the top", addWidth() > 80f)
        swipeListUp()
        compose.waitUntil(PATIENCE_MS) { addWidth() <= 64f }
        compose.onNodeWithTag(ShoppingTags.ADD).assertTextContains("Add item")
        swipeListDown()
        compose.waitUntil(PATIENCE_MS) { addWidth() > 80f }
    }

    /** At large text "Add item" starts shrunk to its icon and stays so either way the list goes; its words still read. */
    private fun assertAddItemStaysShrunk(words: String) {
        show(*many(30))
        assertTrue("Add item is ${addWidth()} dp wide", addWidth() <= 64f)
        compose.onNodeWithTag(ShoppingTags.ADD).assertTextContains(words)
        swipeListUp()
        compose.waitForIdle()
        assertTrue("Add item is ${addWidth()} dp wide going down", addWidth() <= 64f)
        swipeListDown()
        compose.waitForIdle()
        assertTrue("Add item is ${addWidth()} dp wide going up", addWidth() <= 64f)
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleAddItemStartsAndStaysShrunk() = assertAddItemStaysShrunk("Add item")

    @Test
    @Config(fontScale = 1.5f)
    fun atOneAndAHalfAddItemStartsAndStaysShrunk() = assertAddItemStaysShrunk("Add item")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleAddItemStartsAndStaysShrunk() = assertAddItemStaysShrunk("Добавить пункт")

    // It covers no line the list cannot scroll clear of it: a short list's lines stand clear of it.
    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleAddItemCoversNoLineOfAShortList() {
        show("batteries", "baking paper")
        val add = bounds(ShoppingTags.ADD)
        (0..1).forEach { assertFalse("line $it lies under Add item", bounds(ShoppingTags.line(it)).overlaps(add)) }
    }

    // And a long list's last line, the list at its end, too.
    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleAddItemCoversNotTheLastLineOfALongList() {
        show(*many(30))
        // The heading and the 30 lines: the list brought to its end.
        compose.onNodeWithTag(ShoppingTags.LIST).performScrollToIndex(30)
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.line(29)) }
        compose.waitForIdle()
        val last = bounds(ShoppingTags.line(29))
        val add = bounds(ShoppingTags.ADD)
        assertTrue("the last line is not shown", last.height > 0f)
        assertFalse("the last line ($last) lies under Add item ($add)", last.overlaps(add))
    }

    // --- Check boxes ------------------------------------------------------------------------------------------------

    /** The check box drawn at the start of the line tagged [tag], in the tree the screen reader is not given. */
    private fun box(tag: String): SemanticsNode =
        compose.onNodeWithTag(ShoppingTags.lineBox(tag), useUnmergedTree = true).fetchSemanticsNode()

    /**
     * Whether the check box of the line tagged [tag] is drawn ticked: filled with its colour, not just outlined — the
     * share of its pixels unlike the line's background beside it, which the outline alone keeps well under a third.
     */
    private fun drawnTicked(tag: String): Boolean {
        val pixels = compose.onNodeWithTag(ShoppingTags.lineBox(tag), useUnmergedTree = true).captureToImage().toPixelMap()
        val background = pixels[0, 0]
        fun far(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.3f
        var unlike = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) if (far(pixels[x, y], background)) unlike++
        val share = unlike.toFloat() / (pixels.width * pixels.height)
        assertTrue("the box of $tag is neither outlined nor filled: $share", share > 0.1f)
        return share > 0.33f
    }

    /**
     * Each line shows a check box at its start, before its words, drawn as the line is ticked; the box is no target of
     * its own and no node of its own to the screen reader, which hears the whole line as one check box; a tap on the box
     * ticks the line as a tap anywhere on it does.
     */
    private fun assertLinesHaveCheckBoxes(first: String, second: String) {
        show(first, second)
        listOf(first, second).forEachIndexed { index, text ->
            val tag = ShoppingTags.line(index)
            val line = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals(Role.Checkbox, line.config.getOrNull(SemanticsProperties.Role))
            assertEquals(ToggleableState.Off, line.config.getOrNull(SemanticsProperties.ToggleableState))
            val drawn = box(tag)
            assertFalse("the box of $text is drawn ticked", drawnTicked(tag))
            assertFalse("the box is a target of its own", SemanticsActions.OnClick in drawn.config)
            assertTrue("the box is a node of its own", compose.onAllNodes(hasTestTag(ShoppingTags.lineBox(tag))).fetchSemanticsNodes().isEmpty())
            val words = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("the box (${drawn.boundsInRoot}) is not before the words ($words)", drawn.boundsInRoot.right <= words.left)
            assertTrue("the box lies outside its line", line.boundsInRoot.contains(drawn.boundsInRoot.center))
            // The line is the target from its start to its removal.
            assertTrue(dp(line.boundsInRoot.left) < 0.5f)
            assertTrue(dp(line.boundsInRoot.height) >= 48f - 0.5f)
        }
        // A tap on the box ticks its line, which goes to the basket, its box ticked.
        val tag = ShoppingTags.line(0)
        val origin = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.topLeft
        val at = box(tag).boundsInRoot.center - origin
        compose.onNodeWithTag(tag).performTouchInput { click(Offset(at.x, at.y)) }
        compose.waitUntil(PATIENCE_MS) { store.list.value.ticks.isNotEmpty() && compose.textsOf(ShoppingTags.line(1)) == listOf(first) }
        compose.waitForIdle()
        assertFalse(drawnTicked(ShoppingTags.line(0)))
        assertTrue(drawnTicked(ShoppingTags.line(1)))
        assertEquals(ToggleableState.On, compose.onNodeWithTag(ShoppingTags.line(1)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
        // And a tap on its words unticks it.
        compose.onNode(hasText(first) and hasAnyAncestor(hasTestTag(ShoppingTags.line(1))), useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { store.list.value.ticks.isEmpty() && compose.textsOf(ShoppingTags.line(0)) == listOf(first) }
        compose.waitForIdle()
        assertFalse(drawnTicked(ShoppingTags.line(0)))
    }

    @Test
    fun eachLineShowsACheckBoxAsItIsTicked() = assertLinesHaveCheckBoxes("batteries", "baking paper")

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleEachLineShowsACheckBox() = assertLinesHaveCheckBoxes("batteries", "baking paper")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleEachLineShowsACheckBox() = assertLinesHaveCheckBoxes("батарейки", "пергамент")

    // --- The empty list ---------------------------------------------------------------------------------------------

    private fun assertEmptySays(headline: String, sentence: String) {
        show()
        assertEquals(listOf(headline), compose.textsOf(LibraryTags.emptyHeadline(ShoppingTags.EMPTY)))
        assertEquals(listOf(sentence), compose.textsOf(LibraryTags.emptySentence(ShoppingTags.EMPTY)))
        assertTrue(exists(ShoppingTags.ADD))
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianTheEmptyListNamesEveryWayOntoIt() = assertEmptySays(
        "Список покупок пуст",
        "Выберите «Добавить в список покупок» в меню ⋮ рецепта — в книге или на его карточке — или выделите несколько рецептов " +
            "долгим нажатием; «Добавить пункт» добавит что-то своё.",
    )

    // --- "Add item"'s sheet -----------------------------------------------------------------------------------------

    // Its sheet has Material's drag handle, as the editor's sheets have: a 48 dp target centred above the title, whose
    // own Dismiss closes the sheet.
    private fun assertTheSheetHasAHandle() {
        show("batteries")
        compose.onNodeWithTag(ShoppingTags.ADD).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.ADD_FIELD) }
        compose.waitForIdle()
        val handle = SemanticsMatcher("Material's drag handle", ::isDragHandle) and hasAnyAncestor(hasTestTag(ShoppingTags.ADD_SHEET))
        val area = compose.onNode(handle).fetchSemanticsNode().boundsInRoot
        assertTrue("the handle is under 48 dp: $area", dp(area.width) >= 47.5f && dp(area.height) >= 47.5f)
        assertEquals("the handle is not centred", bounds(ShoppingTags.ADD_SHEET).center.x, area.center.x, 1f)
        assertTrue("the handle is not above the title", area.bottom <= bounds(ShoppingTags.ADD_TITLE, unmerged = true).top + 0.5f)
        compose.onNode(handle).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitUntil(PATIENCE_MS) { !exists(ShoppingTags.ADD_SHEET) }
        assertEquals(listOf("batteries"), store.list.value.own.map { it.text })
    }

    @Test
    fun addItemsSheetHasMaterialsHandle() = assertTheSheetHasAHandle()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleAddItemsSheetHasMaterialsHandle() = assertTheSheetHasAHandle()

    // --- A notice's action ------------------------------------------------------------------------------------------

    /** At large text the notice's Undo stands below its words, on a line of its own; at the usual scale, beside them. */
    private fun assertUndoOnItsOwnLine(said: String, undo: String, ownLine: Boolean) {
        show("Chicken with potatoes in the oven")
        compose.onNodeWithTag(ShoppingTags.removeLine(ShoppingTags.line(0))).performClick()
        val inNotice = hasAnyAncestor(hasTestTag(ShoppingTags.SNACKBAR))
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasText(said) and inNotice, useUnmergedTree = true).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodes(hasText(undo) and inNotice, useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
        compose.waitForIdle()
        val words = compose.onNode(hasText(said) and inNotice, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val action = compose.onNode(hasText(undo) and inNotice, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        if (ownLine) {
            assertTrue("$undo ($action) stands below the words ($words)", action.top >= words.bottom - 0.5f)
        } else {
            assertTrue("$undo ($action) stands beside the words ($words)", action.left >= words.right - 0.5f && action.top < words.bottom)
        }
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” removed from the list", "Undo", ownLine = true)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoOnItsOwnLine("«Chicken with potatoes in the oven» убрано из списка", "Вернуть", ownLine = true)

    @Test
    @Config(fontScale = 1.5f)
    fun atOneAndAHalfTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” removed from the list", "Undo", ownLine = true)

    @Test
    fun atTheUsualFontScaleTheNoticesUndoStandsBesideItsWords() =
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” removed from the list", "Undo", ownLine = false)
}
