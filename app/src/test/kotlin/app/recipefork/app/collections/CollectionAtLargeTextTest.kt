package app.recipefork.app.collections

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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
import app.recipefork.app.editor.isDragHandle
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.library.awaitRemovalNotice
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
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
 * A collection and its sheets at large text (v0.38, T-268, scenarios 1 and 3), on a 320 dp phone: the collection's
 * name keeps to one line inside the bar — whole where it fits, drawn smaller as far as it may, ending with an ellipsis
 * where it is longer still — over nothing of the list; a notice puts its action on a line of its own at large text; the
 * card's "Add to collection" sheet and the selection's have Material's drag handle, and the selection's is titled with
 * how many recipes it adds, its words whole. In English and Russian.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class CollectionAtLargeTextTest {
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
        listOf(book.idling, device.idling, timers.idling).forEach(compose::registerIdlingResource)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        sessions.clear()
        book.close()
        device.close()
        timers.close()
    }

    private fun own(title: String, minutesAgo: Long = 0): RecipeId = runBlocking {
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, servings = 2).also { recipes.save(it) }.id
    }

    private fun collection(name: String, members: List<RecipeId> = emptyList()): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it, listOf(id)) } }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun bounds(tag: String, unmerged: Boolean = false): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot

    private fun dp(px: Float) = px / context.resources.displayMetrics.density

    private fun layoutOf(node: SemanticsNode): TextLayoutResult =
        mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()

    /** The book, and above it the screen of the collection [id], its recipes shown. */
    private fun showCollection(id: CollectionId) {
        session.choose(LibraryView.OWN)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment(marks = DoneMarks.inMemory()) { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, Mode.Standard, editor = editor, card = card, collections = collections) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { session.openCollection(id.value) }
        compose.waitUntil(PATIENCE_MS) { exists(CollectionScreenTags.LIST) && !exists(LibraryTags.PAGER) }
        compose.waitForIdle()
    }

    // --- The bar's name ---------------------------------------------------------------------------------------------

    /**
     * The collection's name reads [name] on one line inside the bar, beside its actions — whole where [whole], else
     * ending with an ellipsis — and the list starts below the bar.
     */
    private fun assertNameFits(name: String, whole: Boolean) {
        val soup = own("Soup")
        showCollection(collection(name, listOf(soup)))
        assertEquals(listOf(name), compose.textsOf(CollectionScreenTags.TITLE))
        val node = compose.onNodeWithTag(CollectionScreenTags.TITLE, useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(node)
        assertEquals("$name takes ${layout.lineCount} lines", 1, layout.lineCount)
        assertEquals("$name is cut, or not", !whole, layout.isLineEllipsized(0))
        // Made smaller where it must, but never below its size at a font scale of 1 (22 sp).
        val drawn = layout.layoutInput.style.fontSize.value * context.resources.configuration.fontScale
        assertTrue("$name is drawn at $drawn dp", drawn >= 22f - 0.5f)
        val bar = bounds(CollectionScreenTags.BAR)
        val shown = node.boundsInRoot
        assertTrue("$name ($shown) spills out of the bar ($bar)", shown.top >= bar.top - 0.5f && shown.bottom <= bar.bottom + 0.5f)
        listOf(CollectionScreenTags.BACK, CollectionScreenTags.MENU).forEach { assertFalse("$name overlaps $it", shown.overlaps(bounds(it))) }
        assertTrue("the list starts under the bar", bounds(CollectionScreenTags.LIST).top >= bar.bottom - 0.5f)
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleAShortNameStandsWholeOnOneLine() = assertNameFits("Weeknight dinners", whole = true)

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleALongNameKeepsToOneLineWithAnEllipsis() =
        assertNameFits("A long New Year's Eve dinner menu for the family", whole = false)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleAShortNameStandsWholeOnOneLine() = assertNameFits("Ужины в будни", whole = true)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleALongNameKeepsToOneLineWithAnEllipsis() =
        assertNameFits("Новогодний ужин для всей большой семьи", whole = false)

    // --- A notice's action ------------------------------------------------------------------------------------------

    /** At large text the notice's Undo stands below its words, on a line of its own; at the usual scale, beside them. */
    private fun assertUndoOnItsOwnLine(said: String, undo: String, ownLine: Boolean) {
        val chicken = own("Chicken with potatoes in the oven", minutesAgo = 1)
        val soup = own("Soup")
        showCollection(collection("Dinners", listOf(chicken, soup)))
        compose.onNodeWithTag(LibraryTags.actions(chicken.value)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        await(CollectionScreenTags.remove(chicken.value))
        compose.onNodeWithTag(CollectionScreenTags.remove(chicken.value)).performSemanticsAction(SemanticsActions.OnClick)
        compose.awaitRemovalNotice(said, undo)
        compose.waitForIdle()
        val inNotice = hasAnyAncestor(hasTestTag(LibraryTags.NOTICE))
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
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” removed from the collection", "Undo", ownLine = true)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoOnItsOwnLine("Рецепт «Chicken with potatoes in the oven» убран из коллекции", "Вернуть", ownLine = true)

    @Test
    @Config(fontScale = 1.5f)
    fun atOneAndAHalfTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” removed from the collection", "Undo", ownLine = true)

    @Test
    fun atTheUsualFontScaleTheNoticesUndoStandsBesideItsWords() =
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” removed from the collection", "Undo", ownLine = false)

    // --- The sheets -------------------------------------------------------------------------------------------------

    private val handle = SemanticsMatcher("Material's drag handle", ::isDragHandle)

    /**
     * The sheet tagged [sheet] has Material's drag handle, as the editor's sheets have: a 48 dp target centred above
     * its title, whose own Dismiss closes the sheet — [closed] says it did.
     */
    private fun assertHandle(sheet: String, closed: () -> Boolean) {
        val inSheet = handle and hasAnyAncestor(hasTestTag(sheet))
        val area = compose.onNode(inSheet).fetchSemanticsNode().boundsInRoot
        assertTrue("the handle is under 48 dp: $area", dp(area.width) >= 47.5f && dp(area.height) >= 47.5f)
        assertEquals("the handle is not centred", bounds(sheet).center.x, area.center.x, 1f)
        assertTrue("the handle is not above the title", area.bottom <= bounds(AddToCollectionTags.TITLE, unmerged = true).top + 0.5f)
        compose.onNode(inSheet).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitUntil(PATIENCE_MS) { closed() }
    }

    /** No word of the text tagged [tag] is broken across lines, and none is cut. */
    private fun assertWordsWhole(tag: String) {
        val layout = layoutOf(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())
        val text = layout.layoutInput.text.text
        (0 until layout.lineCount).forEach { line ->
            assertFalse("“$text” is cut", layout.isLineEllipsized(line))
            val end = layout.getLineEnd(line)
            if (line < layout.lineCount - 1) assertTrue("“$text” breaks a word at $end", text[end - 1] == ' ' || text[end] == ' ')
        }
        assertFalse(layout.didOverflowHeight)
    }

    // The card's sheet: the handle above "Add to collection".
    private fun theCardsSheetHasAHandle() {
        val soup = own("Soup")
        collection("Dinners")
        var open by mutableStateOf(true)
        compose.setContent { AppTheme { if (open) AddToCollectionSheet(soup, collections) { open = false } } }
        await(AddToCollectionTags.NEW)
        compose.waitForIdle()
        assertWordsWhole(AddToCollectionTags.TITLE)
        assertHandle(AddToCollectionTags.SHEET) { !open }
    }

    @Test
    fun theCardsSheetHasMaterialsHandle() = theCardsSheetHasAHandle()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheCardsSheetHasMaterialsHandle() = theCardsSheetHasAHandle()

    // The selection's sheet: titled with how many it adds, its words whole, under the handle.
    private fun theSelectionsSheetCountsAndHasAHandle(count: Int, title: String) {
        collection("Dinners")
        var open by mutableStateOf(true)
        compose.setContent {
            AppTheme {
                if (open) ChooseCollectionSheet(count, collections, onChoose = { _, _ -> }, onCreate = {}, onDismiss = { open = false })
            }
        }
        await(AddToCollectionTags.NEW)
        compose.waitForIdle()
        assertEquals(listOf(title), compose.textsOf(AddToCollectionTags.TITLE))
        val heading = compose.onNodeWithTag(AddToCollectionTags.TITLE, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Heading in heading.config)
        assertWordsWhole(AddToCollectionTags.TITLE)
        assertHandle(AddToCollectionTags.CHOOSE_SHEET) { !open }
    }

    @Test
    fun theSelectionsSheetSaysHowManyItAdds() = theSelectionsSheetCountsAndHasAHandle(3, "Add 3 recipes to…")

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleTheSelectionsSheetSaysHowManyItAdds() = theSelectionsSheetCountsAndHasAHandle(1, "Add 1 recipe to…")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheSelectionsSheetSaysHowManyItAdds() =
        theSelectionsSheetCountsAndHasAHandle(5, "Добавить 5 рецептов в…")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAFewRecipesAreCountedSo() = theSelectionsSheetCountsAndHasAHandle(3, "Добавить 3 рецепта в…")
}
