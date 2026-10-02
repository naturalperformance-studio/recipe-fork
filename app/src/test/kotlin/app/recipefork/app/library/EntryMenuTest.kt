package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
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
import java.time.Instant

/**
 * The book's entries keep their actions in a menu at the end of the title line: an own entry's Export (serverless
 * build only) and Delete (where offered), a found entry's Export (serverless build only) and Remove. Nothing stands under
 * an entry, and an entry with no action carries no menu. The ⋮ is 48 dp, named by the recipe it acts on, and shows
 * that name in a tooltip on a long press. Each test passes the mode it is about.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EntryMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(title: String, state: RecipeState = RecipeState.LOCAL, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title).inState(state)
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun found(title: String): Recipe {
        val recipe = Recipe.create(now, owner = null).copy(title = title).inState(RecipeImport.FOUND_STATE)
        runBlocking { assertTrue(recipes.addFound(recipe, now)) }
        return recipe
    }

    /** A recipe marked in the book whose content the book does not hold: its entry has no action. */
    private fun markedOnly(id: String, title: String) =
        runBlocking { book.database.favourites().put(FavouriteEntity(id, title, now)) }

    private fun show(view: LibraryView, mode: Mode) {
        compose.setContent { MaterialTheme { LibraryScreen(session, recipes, mode) } }
        compose.onNodeWithTag(LibraryTags.tab(view)).performClick()
        compose.waitForIdle()
    }

    private fun bringIntoView(list: String, id: String) = compose.waitUntil(PATIENCE_MS) {
        try {
            compose.onNodeWithTag(list).performScrollToNode(hasTestTag(LibraryTags.entry(id)))
            true
        } catch (e: AssertionError) {
            false
        }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun dp(value: Float) = with(compose.density) { value.dp.toPx() }

    private fun items(id: String) = listOf(LibraryTags.export(id), LibraryTags.delete(id), LibraryTags.remove(id))

    /**
     * The entry of [id] carries a menu, 48 dp, beside its text and level with it, and no offer stands outside it; opened,
     * it holds [expected] in that order, from the top.
     */
    private fun assertMenu(list: String, id: String, expected: List<Pair<String, String>>) {
        bringIntoView(list, id)
        assertTrue("$id has a menu", exists(LibraryTags.actions(id)))
        items(id).forEach { assertFalse("$it stands outside the menu", exists(it)) }
        val entry = node(LibraryTags.entry(id)).boundsInRoot
        val menu = node(LibraryTags.actions(id)).boundsInRoot
        assertEquals(dp(48f), menu.width, 0.5f)
        assertEquals(dp(48f), menu.height, 0.5f)
        assertTrue("the ⋮ ends the entry's line: $menu, $entry", menu.left >= entry.right)
        assertTrue("nothing stands under the entry: $menu, $entry", menu.top >= entry.top && menu.bottom <= entry.bottom)
        compose.openEntryMenu(id)
        val shown = items(id).filter(::exists).sortedBy { node(it).boundsInRoot.top }
        assertEquals(expected.map { it.first }, shown)
        expected.forEach { (tag, label) -> assertEquals(listOf(label), compose.textsOf(tag)) }
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = expected.size)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = expected.size)
        compose.closeEntryMenu(id)
    }

    private fun assertNoMenu(list: String, id: String) {
        bringIntoView(list, id)
        assertFalse("$id has a menu", exists(LibraryTags.actions(id)))
        items(id).forEach { assertFalse(exists(it)) }
        val width = node(list).boundsInRoot.width
        assertEquals("the entry takes the whole width", width, node(LibraryTags.entry(id)).boundsInRoot.width, 0.5f)
    }

    @Test
    fun inStandardModeAnOwnEntryHoldsDeleteWhereOfferedAndOtherwiseNoMenu() {
        val draft = own("Pilaf")
        val published = own("Borscht", RecipeState.PUBLISHED, minutesAgo = 1)
        show(LibraryView.OWN, Mode.Standard)
        assertMenu(LibraryTags.OWN_LIST, draft.id.value, listOf(LibraryTags.delete(draft.id.value) to "Delete"))
        assertNoMenu(LibraryTags.OWN_LIST, published.id.value)
    }

    @Test
    fun inServerlessModeAnOwnEntryHoldsExportAndDeleteWhereOffered() {
        val draft = own("Pilaf")
        val published = own("Borscht", RecipeState.PUBLISHED, minutesAgo = 1)
        show(LibraryView.OWN, Mode.Serverless)
        assertMenu(
            LibraryTags.OWN_LIST, draft.id.value,
            listOf(LibraryTags.export(draft.id.value) to "Export", LibraryTags.delete(draft.id.value) to "Delete"),
        )
        assertMenu(LibraryTags.OWN_LIST, published.id.value, listOf(LibraryTags.export(published.id.value) to "Export"))
    }

    @Test
    fun inStandardModeAFoundEntryHoldsRemoveAndAMarkedOnlyEntryNoMenu() {
        val recipe = found("Pilaf")
        markedOnly("m", "Marked pie")
        show(LibraryView.MARKED, Mode.Standard)
        assertMenu(LibraryTags.MARKED_LIST, recipe.id.value, listOf(LibraryTags.remove(recipe.id.value) to "Remove"))
        assertNoMenu(LibraryTags.MARKED_LIST, "m")
    }

    @Test
    fun inServerlessModeAFoundEntryHoldsExportAndRemove() {
        val recipe = found("Pilaf")
        markedOnly("m", "Marked pie")
        show(LibraryView.MARKED, Mode.Serverless)
        assertMenu(
            LibraryTags.MARKED_LIST, recipe.id.value,
            listOf(LibraryTags.export(recipe.id.value) to "Export", LibraryTags.remove(recipe.id.value) to "Remove"),
        )
        assertNoMenu(LibraryTags.MARKED_LIST, "m")
    }

    @Test
    @Config(qualifiers = "ru")
    fun theMenuReadsInRussian() {
        val own = own("Плов")
        val found = found("Шакшука")
        show(LibraryView.OWN, Mode.Serverless)
        assertMenu(
            LibraryTags.OWN_LIST, own.id.value,
            listOf(LibraryTags.export(own.id.value) to "Экспорт", LibraryTags.delete(own.id.value) to "Удалить"),
        )
        assertNamed(own.id.value, "Действия с рецептом «Плов»", "Показать действия с рецептом")
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.actions(found.id.value)), PATIENCE_MS)
        assertMenu(
            LibraryTags.MARKED_LIST, found.id.value,
            listOf(LibraryTags.export(found.id.value) to "Экспорт", LibraryTags.remove(found.id.value) to "Убрать"),
        )
        assertNamed(found.id.value, "Действия с рецептом «Шакшука»", "Показать действия с рецептом")
    }

    @Test
    fun theMenuIsNamedByItsRecipeAndShowsItsNameOnALongPress() {
        val own = own("Pilaf")
        val found = found("Shakshuka")
        show(LibraryView.OWN, Mode.Standard)
        bringIntoView(LibraryTags.OWN_LIST, own.id.value)
        assertNamed(own.id.value, "Actions for “Pilaf”", "Show the recipe's actions")
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.actions(found.id.value)), PATIENCE_MS)
        assertNamed(found.id.value, "Actions for “Shakshuka”", "Show the recipe's actions")
    }

    /** The ⋮ of [id] speaks [label] and announces its click as [action]; a long press shows [label] in a tooltip. */
    private fun assertNamed(id: String, label: String, action: String) {
        val tag = LibraryTags.actions(id)
        assertEquals(label, AccessibilityChecks.announcedLabel(node(tag)))
        assertEquals(action, AccessibilityChecks.clickActionLabel(node(tag)))
        compose.onNodeWithTag(tag).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.tooltip(tag)) }
        fun texts(node: SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::texts)
        val tooltip = compose.onNodeWithTag(EditorTags.tooltip(tag), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the tooltip reads \"$label\"", texts(tooltip).contains(label))
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.tooltip(tag)) }
        // A long press opens no menu.
        items(id).forEach { assertFalse(exists(it)) }
    }
}
