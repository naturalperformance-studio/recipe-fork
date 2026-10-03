package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
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
 * A book's entry as a card: beneath its title the tags its recipe carries, on one muted line cut short where it does not
 * fit, labelled in the interface language as the tag filter labels them; when an entry leaves its list, the entries
 * after it move up animated rather than jumping into place.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "h2000dp")
class BookEntryCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-04T12:00:00Z")

    private val soup = TagEntity(10, "суп", "Soup")
    private val quick = TagEntity(11, "быстро", "Quick")
    private val georgian = TagEntity(12, "грузинская кухня", "Georgian cuisine")
    private val vegetarian = TagEntity(13, "вегетарианское", "Vegetarian")
    private val holiday = TagEntity(14, "праздничный стол", "Holiday table")
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = listOf(soup, quick, georgian, vegetarian, holiday)
        },
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(title: String, vararg tags: TagEntity, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, tags = tags.mapTo(HashSet()) { it.id })
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun found(title: String, vararg tags: TagEntity, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now, owner = null).copy(title = title, tags = tags.mapTo(HashSet()) { it.id })
            .inState(RecipeImport.FOUND_STATE)
        runBlocking { assertTrue(recipes.addFound(recipe, now.minusSeconds(minutesAgo * 60))) }
        return recipe
    }

    private fun show(view: LibraryView, mode: Mode = Mode.Standard) {
        session.choose(view)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, mode, editor = editor) } }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun awaitTags(recipe: Recipe, text: String) {
        val tag = LibraryTags.entryTags(recipe.id.value)
        runCatching { compose.waitUntil(PATIENCE_MS) { exists(tag) && textOf(tag) == text } }
        assertEquals(text, textOf(tag))
    }

    private fun layoutOf(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    @Test
    fun anOwnEntryShowsItsTagsInEnglishBeneathItsTitleWithItsStateAndSentMark() {
        val tagged = own("Borscht", soup, quick)
        val bare = own("Plain", minutesAgo = 1)
        show(LibraryView.OWN)
        awaitTags(tagged, "Quick · Soup")
        val id = tagged.id.value
        assertTrue(
            "beneath the title",
            compose.onNodeWithTag(LibraryTags.entryTags(id), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y >=
                compose.onNodeWithTag(LibraryTags.entryTitle(id), useUnmergedTree = true).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height },
        )
        // The entry still names its state and its sending mark (FLOW-036, FLOW-005).
        val read = compose.onNodeWithTag(LibraryTags.entry(id)).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Borscht", "Quick · Soup", "Draft"), read.take(3))
        assertTrue(read.drop(3).single().startsWith("Not sent"))
        assertFalse("an entry with no tag shows no line of them", exists(LibraryTags.entryTags(bare.id.value)))
    }

    @Test
    @Config(qualifiers = "ru-h2000dp")
    fun aFoundEntryShowsItsTagsInRussian() {
        val recipe = found("Харчо", soup, georgian)
        show(LibraryView.MARKED, Mode.Serverless)
        awaitTags(recipe, "грузинская кухня · суп")
    }

    @Test
    @Config(qualifiers = "w320dp-h2000dp")
    fun manyTagsKeepToOneLineCutShort() {
        val recipe = own("Borscht", soup, quick, georgian, vegetarian, holiday)
        show(LibraryView.OWN)
        awaitTags(recipe, "Georgian cuisine · Holiday table · Quick · Soup · Vegetarian")
        val layout = layoutOf(LibraryTags.entryTags(recipe.id.value))
        assertEquals("one line", 1, layout.lineCount)
        assertTrue("cut short", layout.isLineEllipsized(0))
    }

    /**
     * Waits, the clock held, until the entry of [stays] begins to move once [leave] has taken [goes] out of the list
     * above it, and checks that it moves up gradually — animated — into the place [goes] left.
     */
    private fun assertLeavesAnimated(goes: Recipe, stays: Recipe, leave: suspend () -> Unit) {
        val staying = LibraryTags.entry(stays.id.value)
        val going = LibraryTags.entry(goes.id.value)
        compose.waitUntil(PATIENCE_MS) { exists(staying) && exists(going) }
        compose.waitForIdle()
        fun top(tag: String = staying) = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y
        val before = top()
        // Where the entry staying ends: where the one leaving stood, above it.
        val destination = top(going)
        compose.mainClock.autoAdvance = false
        runBlocking { leave() }
        compose.waitUntil(PATIENCE_MS) {
            compose.mainClock.advanceTimeByFrame()
            top() != before
        }
        // Without the animation it would stand in its new place at once.
        assertTrue("the next has not yet reached its place", top() > destination + 1f)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(PATIENCE_MS) { !exists(going) && top() == destination }
    }

    @Test
    fun theNextEntryMovesUpGraduallyWhenOneIsDeleted() {
        val older = own("Shchi", minutesAgo = 1)
        val newer = own("Borscht")
        show(LibraryView.OWN)
        assertLeavesAnimated(newer, older) { recipes.delete(newer.id) }
    }

    @Test
    fun theNextEntryMovesUpGraduallyWhenAFoundOneIsRemoved() {
        val older = found("Kharcho", minutesAgo = 1)
        val newer = found("Shakshuka")
        show(LibraryView.MARKED, Mode.Serverless)
        assertLeavesAnimated(newer, older) { recipes.removeMarked(newer.id) }
    }
}
