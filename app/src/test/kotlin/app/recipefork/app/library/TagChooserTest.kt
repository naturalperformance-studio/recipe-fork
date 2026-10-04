package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
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
import java.io.File
import java.time.Instant

/**
 * FLOW-035: the book's filter by tags over the tag dictionary the application ships, in the sheet of every filter. Its
 * hundreds of tags are searched, never listed: the sheet's tag section finds a tag by the beginning of a word, and the
 * sheet stays within a 360 dp screen at twice the font size — the narrowest and largest the application supports. The
 * font scale is the device's own, set by the test's configuration: a sheet is a window of its own, and takes its density
 * from the device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TagChooserTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val shipped: List<TagEntity> =
        DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText()).tags
            .map { TagEntity(it.id, it.labelRu, it.labelEn) }
    private val airFryer = shipped.single { it.labelRu == "аэрогриль" }
    private val editor = EditorEnvironment(active, object : TagSource {
        override suspend fun tags() = shipped
    }) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun openTheChooser() {
        runBlocking {
            recipes.save(Recipe.create(now, owner = null).copy(title = "Wings", tags = setOf(airFryer.id)))
            recipes.save(Recipe.create(now.minusSeconds(60), owner = null).copy(title = "Lasagne", tags = setOf(10L)))
        }
        session.choose(LibraryView.OWN)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = editor) } }
        click(LibraryTags.FILTER_ADD)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.TAG_FIELD), PATIENCE_MS)
    }

    private fun click(tag: String) {
        compose.waitUntilExactlyOneExists(hasTestTag(tag), PATIENCE_MS)
        compose.onNodeWithTag(tag).scrolledTo().performClick()
        compose.waitForIdle()
    }

    private fun type(text: String) {
        compose.onNodeWithTag(LibraryTags.TAG_FIELD).scrolledTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    /** The tags the chooser shows, by id. */
    private fun choices(): List<Long> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(LibraryTags.TAG_CHOOSER))).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            .filter { it.startsWith(LibraryTags.tagChoice(0).removeSuffix("0")) }
            .map { it.substringAfterLast('-').toLong() }

    private fun screen(): Rect {
        val metrics = context.resources.displayMetrics
        return Rect(0f, 0f, metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    }

    private fun assertWithinTheScreen(tag: String) {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val screen = screen()
        assertTrue("$tag at $bounds leaves the screen $screen", bounds.left >= screen.left - 1 && bounds.right <= screen.right + 1)
        assertTrue("$tag at $bounds leaves the screen $screen", bounds.top >= screen.top - 1 && bounds.bottom <= screen.bottom + 1)
    }

    private fun findsByTypingAndStaysWithinTheScreen(typed: String) {
        openTheChooser()
        // Nothing is listed until a word is typed.
        assertEquals(emptyList<Long>(), choices())
        assertWithinTheScreen(LibraryTags.FILTERS_SHEET)
        // A beginning many tags share offers at most eight, and the sheet stays on the screen.
        type("с")
        assertEquals(8, choices().size)
        assertWithinTheScreen(LibraryTags.FILTERS_SHEET)
        compose.onNodeWithTag(LibraryTags.FILTERS_SHEET_DONE).assertIsDisplayed()
        assertWithinTheScreen(LibraryTags.FILTERS_SHEET_DONE)
        // The tag sought is found by the beginning of its word, and picked.
        type(typed)
        assertEquals(listOf(airFryer.id), choices())
        compose.onNodeWithTag(LibraryTags.tagChoice(airFryer.id)).performScrollTo().assertIsDisplayed()
        assertWithinTheScreen(LibraryTags.tagChoice(airFryer.id))
        click(LibraryTags.tagChoice(airFryer.id))
        // Picked, it stands checked at the head of the chooser and the field is cleared for the next.
        assertEquals(listOf(airFryer.id), choices())
        val state = compose.onNodeWithTag(LibraryTags.tagChoice(airFryer.id)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ToggleableState)
        assertEquals(ToggleableState.On, state)
        // The chooser's own controls keep their 48 dp (A11Y-003).
        listOf(LibraryTags.tagChoice(airFryer.id), LibraryTags.FILTERS_SHEET_DONE).forEach { tag ->
            val size = compose.onNodeWithTag(tag).fetchSemanticsNode().size
            val minimum = with(Density(context)) { 48.dp.toPx() } - 1
            assertTrue("$tag is $size", size.height >= minimum && size.width >= minimum)
        }
        click(LibraryTags.FILTERS_SHEET_DONE)
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.FILTERS_SHEET), PATIENCE_MS)
        // The chosen tag is shown as a chip, and the view keeps the recipes carrying it.
        compose.onNodeWithTag(LibraryTags.tagFilter(airFryer.id)).assertExists()
        assertEquals(setOf(airFryer.id), session.filters(LibraryView.OWN).value.tags)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp", fontScale = 2f)
    fun inRussianTheChooserFindsATagByTypingAndStaysWithinTheScreen() = findsByTypingAndStaysWithinTheScreen("аэро")

    @Test
    @Config(qualifiers = "en-w360dp-h640dp", fontScale = 2f)
    fun inEnglishTheChooserFindsATagByTypingAndStaysWithinTheScreen() = findsByTypingAndStaysWithinTheScreen("air")

    // Typed text that names no tag offers nothing and says so; it never becomes a filter.
    @Test
    @Config(qualifiers = "en")
    fun textThatNamesNoTagOffersNothing() {
        openTheChooser()
        type("zzz")
        assertEquals(emptyList<Long>(), choices())
        compose.onNodeWithTag(LibraryTags.TAG_NO_MATCH).assertExists()
        click(LibraryTags.FILTERS_SHEET_DONE)
        assertEquals(emptySet<Long>(), session.filters(LibraryView.OWN).value.tags)
    }
}
