package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.TagOffer
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.DictionariesDto
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
import kotlin.math.abs

/**
 * Scenario 6 of v0.23: many tags wrapping onto several lines in the editor keep an 8 dp gap between the lines, as
 * between the tags of a line — the recipe's tags and the offered ones alike, at 1× and 2× font, in English and Russian.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TagLineGapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-09T12:00:00Z")
    private val shipped: DictionariesDto =
        DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText())

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(shipped) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun wrappedTagLinesKeepAGapInEnglish() = wrappedLinesKeepTheGap(typed = "s")

    @Test
    @Config(qualifiers = "en-w360dp-h640dp", fontScale = 2.0f)
    fun wrappedTagLinesKeepAGapInEnglishAtTwiceTheFont() = wrappedLinesKeepTheGap(typed = "s")

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun wrappedTagLinesKeepAGapInRussian() = wrappedLinesKeepTheGap(typed = "с")

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp", fontScale = 2.0f)
    fun wrappedTagLinesKeepAGapInRussianAtTwiceTheFont() = wrappedLinesKeepTheGap(typed = "с")

    private fun wrappedLinesKeepTheGap(typed: String) {
        val recipe = Recipe.create(created, null).copy(title = "Борщ", tags = CHOSEN.toSet())
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent {
            MaterialTheme { EditorScreen(recipe.id, recipes, environment, session.viewModelScope, onClose = {}) }
        }
        compose.waitUntil(PATIENCE_MS) { CHOSEN.all { exists(EditorTags.chosen(it)) } }
        assertLinesKeepTheGap("the recipe's tags", CHOSEN.map { bounds(EditorTags.chosen(it)) })

        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performScrollTo().performTextReplacement(typed)
        val offered = shipped.tags.map { it.id }.filter { it !in CHOSEN }
        compose.waitUntil(PATIENCE_MS) { offered.count { exists(EditorTags.offered(it)) } == TagOffer.LIMIT }
        compose.onNodeWithTag(EditorTags.OFFERED).performScrollTo()
        val shown = offered.filter { exists(EditorTags.offered(it)) }
        assertLinesKeepTheGap("the offered tags", shown.map { bounds(EditorTags.offered(it)) })
    }

    /**
     * The chips laid out in [chips] fall into at least two lines; within a line no two touch or overlap and each is at
     * least 8 dp from the next, and each line starts 8 dp below the bottom of the line above it.
     */
    private fun assertLinesKeepTheGap(what: String, chips: List<Rect>) {
        val gap = with(compose.density) { 8.dp.toPx() }
        val tolerance = with(compose.density) { 0.5.dp.toPx() }
        val lines = chips.sortedBy { it.top }.fold(mutableListOf<MutableList<Rect>>()) { lines, chip ->
            val line = lines.lastOrNull()
            if (line != null && abs(line.first().top - chip.top) <= tolerance) line += chip else lines += mutableListOf(chip)
            lines
        }
        assertTrue("$what do not wrap: $chips", lines.size >= 2)
        lines.forEach { line ->
            line.sortedBy { it.left }.zipWithNext().forEach { (left, right) ->
                assertTrue("$what touch within a line: $left, $right", right.left - left.right >= gap - tolerance)
            }
        }
        lines.zipWithNext().forEach { (above, below) ->
            val bottom = above.maxOf { it.bottom }
            val top = below.minOf { it.top }
            assertEquals("$what: the gap between lines, in px", gap, top - bottom, tolerance)
        }
    }

    private fun exists(tag: String): Boolean =
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    // Laid out bounds, not clipped by the scrolling form: a chip below the fold still has its place.
    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private companion object {
        // Enough of the shipped dictionary's tags to wrap onto several lines on a 360 dp screen.
        val CHOSEN = listOf(100L, 101, 102, 103, 104, 105, 106, 108, 111, 112, 114, 120)
    }
}
