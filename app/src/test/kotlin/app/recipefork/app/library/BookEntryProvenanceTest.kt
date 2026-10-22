package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.book.PARENT_OF_R
import app.recipefork.app.book.PARENT_OF_R_JOIN
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardTags
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.fork
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.Collections
import kotlin.math.abs

/**
 * A version's entry names the recipe it was made from (v0.42, T-302; FORK-010, decisions.md #3): under its title the
 * provenance glyph, 16 dp, and «по рецепту «…»» / "after “…”" in beet. The title is the one the version recorded, so it
 * stays when the parent leaves the book; a recipe without a parent has no such line. The line is part of the row's
 * spoken label and does nothing of its own. The listings read the parent in their one query, through the recipe's key.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h780dp")
class BookEntryProvenanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = emptyList<TagEntity>()
        },
    ) { now }

    private lateinit var scheme: ColorScheme

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

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

    /** A version of [parent], saved in the book: the owner's own unless [byAuthor] is false. */
    private fun versionOf(parent: Recipe, title: String, byAuthor: Boolean = true): Recipe {
        val version = parent.fork(now, owner = null, byAuthor = byAuthor).copy(title = title)
        runBlocking { recipes.save(version) }
        return version
    }

    private fun found(title: String, author: String?): Recipe {
        val recipe = Recipe.create(now, owner = null).copy(title = title, statedAuthor = author).inState(RecipeImport.FOUND_STATE)
        runBlocking { assertTrue(recipes.addFound(recipe, now)) }
        return recipe
    }

    private fun show(view: LibraryView) {
        session.choose(view)
        compose.setContent {
            AppTheme {
                scheme = MaterialTheme.colorScheme
                AppNavHost(session, recipes, Mode.Serverless, editor = editor)
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun dp(value: Float) = with(compose.density) { value.dp.toPx() }

    /** The words of the provenance line of [recipe]'s entry, as laid out. */
    private fun lineOf(recipe: Recipe): TextLayoutResult {
        val line = compose.onNodeWithTag(BookEntryRowTags.provenance(recipe.id.value), useUnmergedTree = true)
            .fetchSemanticsNode().children.single { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
        val layouts = mutableListOf<TextLayoutResult>()
        line.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private fun near(a: Color, b: Color) = listOf(a.red - b.red, a.green - b.green, a.blue - b.blue).all { abs(it) < 0.06f }

    @Test
    fun aVersionNamesItsParentUnderItsTitleWithTheGlyphInBeet() {
        val parent = own("Shakshuka", minutesAgo = 5)
        val version = versionOf(parent, "My shakshuka with feta")
        show(LibraryView.OWN)
        val id = version.id.value
        await(BookEntryRowTags.provenance(id))
        assertFalse("a recipe without a parent has no such line", exists(BookEntryRowTags.provenance(parent.id.value)))
        val layout = lineOf(version)
        assertEquals("after “Shakshuka”", layout.layoutInput.text.text)
        assertEquals("in beet, a provenance mark", scheme.tertiary, layout.layoutInput.style.color)
        assertEquals("in Literata", ReadingFamily, layout.layoutInput.style.fontFamily)
        assertEquals("in italics", FontStyle.Italic, layout.layoutInput.style.fontStyle)
        val title = bounds(LibraryTags.entryTitle(id))
        val line = bounds(BookEntryRowTags.provenance(id))
        assertTrue("under the title", line.top >= title.bottom)
        assertEquals("level with the title's start", title.left, line.left, 1f)
        val glyph = bounds(BookEntryRowTags.provenanceGlyph(id))
        assertEquals("the glyph 16 dp", dp(16f), glyph.width, 1f)
        assertEquals(dp(16f), glyph.height, 1f)
        assertEquals("at the line's start", line.left, glyph.left, 1f)
        // The glyph is drawn in beet.
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val inked = (glyph.left.toInt() until glyph.right.toInt()).sumOf { x ->
            (glyph.top.toInt() until glyph.bottom.toInt()).count { y -> near(Color(image.getPixel(x, y)), scheme.tertiary) }
        }
        assertTrue("the glyph is drawn in beet ($inked px)", inked > dp(8f))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h780dp")
    fun inRussianTheLineReadsPoReceptu() {
        val parent = own("Шакшука", minutesAgo = 5)
        val version = versionOf(parent, "Моя шакшука с фетой")
        show(LibraryView.OWN)
        await(BookEntryRowTags.provenance(version.id.value))
        assertEquals("по рецепту «Шакшука»", lineOf(version).layoutInput.text.text)
    }

    @Test
    fun theLineIsPartOfTheRowsLabelAndATapOnItOpensTheRecipe() {
        val parent = own("Shakshuka", minutesAgo = 5)
        val version = versionOf(parent, "My shakshuka")
        show(LibraryView.OWN)
        val id = version.id.value
        await(BookEntryRowTags.provenance(id))
        val read = compose.onNodeWithTag(LibraryTags.entry(id)).fetchSemanticsNode().config
        assertEquals(listOf("My shakshuka", "after “Shakshuka”"), read[SemanticsProperties.Text].map { it.text })
        assertEquals("the row opens the recipe", "Open the recipe", read[SemanticsActions.OnClick].label)
        // Nothing of its own: the line and its glyph are no buttons, and the glyph is not read.
        listOf(BookEntryRowTags.provenance(id), BookEntryRowTags.provenanceGlyph(id)).forEach { tag ->
            val config = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
            assertNull("$tag has no click of its own", config.getOrNull(SemanticsActions.OnClick))
            assertNull("$tag has no role", config.getOrNull(SemanticsProperties.Role))
        }
        val glyph = compose.onNodeWithTag(BookEntryRowTags.provenanceGlyph(id), useUnmergedTree = true).fetchSemanticsNode().config
        assertNull(glyph.getOrNull(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag(BookEntryRowTags.provenance(id), useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.SCREEN) }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.TITLE) }
        assertEquals("My shakshuka", compose.onNodeWithTag(CardTags.TITLE, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text })
    }

    @Test
    fun theRecordedTitleStaysWhenTheParentLeavesTheBook() {
        // A version of a recipe found in a file records its parent's title (FLOW-065 (4)).
        val parent = found("Shakshuka", "Marina")
        val version = versionOf(parent, "My shakshuka", byAuthor = false)
        runBlocking { assertTrue(recipes.removeMarked(parent.id, files = null)) }
        show(LibraryView.OWN)
        await(BookEntryRowTags.provenance(version.id.value))
        assertEquals("after “Shakshuka”", lineOf(version).layoutInput.text.text)
    }

    @Test
    fun anOwnVersionWhoseParentIsGoneSaysSoAsTheCardDoes() {
        // The owner's own version records no title of its parent; the book no longer holds the parent to read one.
        val parent = own("Shakshuka", minutesAgo = 5)
        val version = versionOf(parent, "My shakshuka")
        runBlocking { assertTrue(recipes.delete(parent.id)) }
        show(LibraryView.OWN)
        await(BookEntryRowTags.provenance(version.id.value))
        assertEquals("after a recipe not in this book", lineOf(version).layoutInput.text.text)
    }

    @Test
    fun aFoundVersionNamesItsParentAboveItsStatedAuthor() {
        val parent = found("Borscht", "Marina")
        val version = parent.fork(now, owner = null, byAuthor = false).copy(title = "Borscht with beans", statedAuthor = "Oleg")
            .inState(RecipeImport.FOUND_STATE)
        runBlocking {
            assertTrue(recipes.removeMarked(parent.id, files = null))
            assertTrue(recipes.addFound(version, now))
        }
        show(LibraryView.MARKED)
        val id = version.id.value
        await(BookEntryRowTags.provenance(id))
        assertEquals("after “Borscht”", lineOf(version).layoutInput.text.text)
        assertTrue("the author under the line", bounds(LibraryTags.statedAuthor(id)).top >= bounds(BookEntryRowTags.provenance(id)).bottom)
    }

    /** A book of 300 recipes, every other one a version — of one held, of one gone, or of one found — and its queries. */
    private class LargeBook(context: Context) {
        val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val database: BookDatabase = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries += sql }, Runnable::run)
            .build()

        /** The reads of the book's own rows among [queries], leaving out Room's bookkeeping. */
        fun reads() = queries.filter { it.trimStart().startsWith("select", ignoreCase = true) && "room_" !in it }
    }

    @Test
    fun aBookOf300ListsEveryParentInOneQueryThroughTheRecipesKey() {
        val large = LargeBook(context)
        try {
            val repository = RecipeRepository(ActiveBook(BookName.Pool, large.database))
            val expected = mutableMapOf<String, String?>()
            runBlocking {
                (0 until 150).forEach { n ->
                    val parent = Recipe.create(now.minusSeconds(n * 120L + 60), owner = null).copy(title = "Soup $n")
                    repository.save(parent)
                    expected[parent.id.value] = null
                    val version = parent.fork(now.minusSeconds(n * 120L), owner = null, byAuthor = n % 3 != 2)
                        .copy(title = "My soup $n")
                    repository.save(version)
                    if (n % 3 == 1) repository.delete(parent.id).also { expected.remove(parent.id.value) }
                    expected[version.id.value] = if (n % 3 == 1) NO_TITLE else "Soup $n"
                }
                large.queries.clear()
                val rows = large.database.recipes().own().first()
                assertEquals("one query for the whole listing: ${large.reads()}", 1, large.reads().size)
                assertEquals(expected.size, rows.size)
                rows.forEach { row ->
                    val wanted = expected.getValue(row.recipeId)
                    assertEquals(row.title, wanted != null, row.forked)
                    assertEquals(row.title, wanted?.takeUnless { it == NO_TITLE }, row.parentTitle)
                }
                // The other listings of the book read it the same way.
                large.queries.clear()
                large.database.favourites().marked().first()
                assertEquals(1, large.reads().size)
                val collections = CollectionRepository(ActiveBook(BookName.Pool, large.database)) { now }
                val collection = collections.create("Soups")!!
                rows.take(10).forEach { collections.addTo(RecipeId(it.recipeId), setOf(collection)) }
                large.queries.clear()
                val listed = large.database.collections().recipes(collection.value).first()
                assertEquals(1, large.reads().size)
                assertEquals(rows.take(10).map { it.recipeId to it.parentTitle }.toSet(), listed.map { it.recipeId to it.parentTitle }.toSet())
            }
            // The parent is found through the recipe's key, never by reading the table through for each entry.
            val plan = large.database.openHelper.readableDatabase
                .query("explain query plan select r.recipe_id, $PARENT_OF_R from recipe r $PARENT_OF_R_JOIN")
                .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) } }
            assertTrue("the parent by its key: $plan", plan.any { Regex("""SEARCH (TABLE recipe AS )?p USING .*(INDEX|PRIMARY KEY)""").containsMatchIn(it) })
        } finally {
            large.database.close()
        }
    }

    private companion object {
        /** An own version whose parent is gone: it has a parent, and no title of it to show. */
        const val NO_TITLE = "\u0000"
    }
}
