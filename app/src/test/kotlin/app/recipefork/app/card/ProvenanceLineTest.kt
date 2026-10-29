package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.fork
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The provenance line of v0.43 (T-311; decisions.md 2026-10-15 and its fixes; FLOW-065, FORK-010): above a version's
 * title, a 26 dp column draws the parent as a hollow node and the version as a filled node level with the title, joined
 * by a 1.6 dp line with one 6 dp knee, beside one line «по рецепту «…»» in the working face and beet. Held, the whole
 * is one button to the parent and names no author; not held, the node is dashed, nothing opens, and the author a file
 * states stands under the line, small and muted. The screen reader reads the title before the line.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProvenanceLineTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private val opened = mutableListOf<RecipeId>()
    private var colours = lightColorScheme()
    private var type = Typography()

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun save(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun recipe(title: String) = Recipe.create(now, null).copy(title = title, servings = 2)

    private var shown by mutableStateOf<RecipeId?>(null)
    private var composed = false

    /** The card of [recipe], once its provenance column is drawn. */
    private fun show(recipe: Recipe) {
        if (!composed) {
            composed = true
            compose.setContent {
                AppTheme {
                    colours = MaterialTheme.colorScheme
                    type = MaterialTheme.typography
                    shown?.let {
                        key(it) { CardScreen(it, recipes, environment, onBack = {}, onEdit = {}, onOpenParent = { parent -> opened += parent }) }
                    }
                }
            }
        }
        compose.runOnIdle { shown = recipe.id }
        compose.waitForIdle()
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasTestTag(ProvenanceTags.COLUMN), useUnmergedTree = true).fetchSemanticsNodes()
                .any { it.config.getOrNull(ProvenanceDrawn) != null }
        }
        compose.waitForIdle()
    }

    private fun unmerged(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun drawing(): ProvenanceDrawing = unmerged(ProvenanceTags.COLUMN).config[ProvenanceDrawn]

    private fun density(): Float = unmerged(CardTags.TITLE).layoutInfo.density.density

    /** The words of the line as laid out, and the node that holds them. */
    /**
     * The words of the line read as [spoken], and the text drawn for them: the node the screen reader reads holds
     * [spoken], the text within it is drawn and not read.
     */
    private fun words(spoken: String): Pair<SemanticsNode, TextLayoutResult> {
        val read = compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.PROVENANCE)) and hasText(spoken), useUnmergedTree = true)
            .onFirst().fetchSemanticsNode()
        val drawn = generateSequence(listOf(read)) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }.flatten()
            .first { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
        val layouts = mutableListOf<TextLayoutResult>()
        drawn.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return drawn to layouts.single()
    }

    /** Where the middle of the first line laid out in [layout], within [node], stands on screen, in pixels. */
    private fun firstLineMiddle(node: SemanticsNode, layout: TextLayoutResult) =
        node.boundsInRoot.top + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2

    private fun title(): Pair<SemanticsNode, TextLayoutResult> =
        unmerged(CardTags.TITLE) to compose.textLayoutsWithin(CardTags.TITLE).single()

    // Criterion 1: held — one line in Golos and beet above the title, the column drawing hollow node, knee and filled
    // node beside it, the whole one 48 dp button (the card's larger target) to the parent with the existing label, and
    // no author even where the file states one.
    @Test
    fun aHeldParentIsOneBeetLineAboveTheTitleDrawnWithItsNodesAndOneButtonToTheParent() {
        val parent = save(recipe("Shakshuka"))
        val fork = save(parent.fork(now, owner = null).copy(title = "My shakshuka with feta", statedParent = StatedParent("Shakshuka", "anna")))
        show(fork)
        assertEquals(listOf("after “Shakshuka”"), compose.textsOf(CardTags.PROVENANCE))
        val (wordsNode, laid) = words("after “Shakshuka”")
        assertEquals("the chevron ends the line, unread", "after “Shakshuka” ›", laid.layoutInput.text.text)
        val style = laid.layoutInput.style
        assertEquals(colours.tertiary, style.color)
        assertEquals(type.titleSmall.fontFamily, style.fontFamily)
        assertEquals(type.titleSmall.fontSize, style.fontSize)
        assertEquals(type.titleSmall.fontWeight, style.fontWeight)
        // No author line: the parent's own card names its author.
        compose.onNodeWithTag(ProvenanceTags.AUTHOR, useUnmergedTree = true).assertDoesNotExist()
        assertTrue(compose.textsOf(CardTags.PROVENANCE).none { "anna" in it })

        // One button, at least 48 dp — the card's 56 — high, across the column and the words, above the title.
        val button = compose.onNodeWithTag(CardTags.PROVENANCE).fetchSemanticsNode()
        assertEquals(context.getString(R.string.card_parent_open_action), AccessibilityChecks.clickActionLabel(button))
        assertEquals(Role.Button, button.config.getOrNull(SemanticsProperties.Role))
        compose.assertCardTarget(CardTags.PROVENANCE)
        val column = unmerged(ProvenanceTags.COLUMN).boundsInRoot
        assertEquals(column.left, button.boundsInRoot.left, 0.5f)
        assertTrue("the line above the title", button.boundsInRoot.bottom <= unmerged(CardTags.TITLE).boundsInRoot.top + 0.5f)

        // The column: 26 dp wide, the parent's hollow node level with the line's first line, this recipe's filled node
        // level with the title's, joined by a 1.6 dp line with one 6 dp knee.
        val density = density()
        assertEquals(26f, column.width / density, 0.5f)
        val drawn = drawing()
        assertFalse(drawn.dashed)
        assertEquals(4.8f, drawn.parentRadius.value, 0.01f)
        assertEquals(5f, drawn.ownRadius.value, 0.01f)
        assertEquals(1.6f, drawn.stroke.value, 0.01f)
        assertEquals(6f, drawn.knee.value, 0.01f)
        assertEquals(7f, drawn.parent.x.value, 0.01f)
        assertEquals(20.5f, drawn.own.x.value, 0.01f)
        assertEquals(firstLineMiddle(wordsNode, laid), column.top + drawn.parent.y.value * density, 1f)
        val (titleNode, titleLaid) = title()
        assertEquals(firstLineMiddle(titleNode, titleLaid), column.top + drawn.own.y.value * density, 1f)
        assertTrue("the title stands beside the column", titleNode.boundsInRoot.left >= column.right)

        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        assertEquals(listOf(parent.id), opened)
    }

    // Criterion 2: not held — the hollow node dashed, no chevron, not a button; under the line, small and muted, the
    // author the file states, marked as not verified.
    @Test
    fun aParentNotInTheBookIsDashedOpensNothingAndNamesTheStatedAuthorUnderTheLine() {
        val orphan = save(
            recipe("My shakshuka with feta").copy(parent = RecipeId("gone"), statedParent = StatedParent("Shakshuka", "Kitchen on Vasilyevsky")),
        )
        show(orphan)
        val line = "after “Shakshuka”, which is not in this book"
        val author = "Kitchen on Vasilyevsky · author from the file, not verified"
        assertEquals(listOf(line, author), compose.textsOf(CardTags.PROVENANCE))
        val node = compose.onNodeWithTag(CardTags.PROVENANCE).fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsActions.OnClick))
        assertNull(node.config.getOrNull(SemanticsProperties.Role))
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        assertEquals(emptyList<RecipeId>(), opened)
        val (wordsNode, laid) = words(line)
        assertEquals("no chevron", line, laid.layoutInput.text.text)
        assertEquals(colours.tertiary, laid.layoutInput.style.color)
        assertTrue(drawing().dashed)
        assertEquals(firstLineMiddle(wordsNode, laid), unmerged(ProvenanceTags.COLUMN).boundsInRoot.top + drawing().parent.y.value * density(), 1f)

        // The author: under the words, in the small working face, muted, read with a comma.
        val authorNode = unmerged(ProvenanceTags.AUTHOR)
        assertTrue(authorNode.boundsInRoot.top >= wordsNode.boundsInRoot.bottom - 0.5f)
        val authorStyle = compose.textLayoutsWithin(ProvenanceTags.AUTHOR).single().layoutInput.style
        assertEquals(colours.onSurfaceVariant, authorStyle.color)
        assertEquals(type.bodySmall.fontSize, authorStyle.fontSize)
        assertEquals(type.bodySmall.fontFamily, authorStyle.fontFamily)
        assertEquals(
            listOf("Kitchen on Vasilyevsky, author from the file, not verified"),
            authorNode.config.getOrNull(SemanticsProperties.ContentDescription),
        )
        assertTrue(unmerged(CardTags.PROVENANCE).boundsInRoot.bottom <= unmerged(CardTags.TITLE).boundsInRoot.top + 0.5f)
    }

    // Criterion 2, the rest: a file that states no author, or a parent the version names no title of, shows no author.
    @Test
    fun aParentNotInTheBookWithNoStatedAuthorShowsNone() {
        show(save(recipe("Toast").copy(parent = RecipeId("gone"), statedParent = StatedParent("Bread", ""))))
        assertEquals(listOf("after “Bread”, which is not in this book"), compose.textsOf(CardTags.PROVENANCE))
        compose.onNodeWithTag(ProvenanceTags.AUTHOR, useUnmergedTree = true).assertDoesNotExist()
        show(save(recipe("Toast again").copy(parent = RecipeId("gone too"))))
        assertEquals(listOf("after a recipe not in this book"), compose.textsOf(CardTags.PROVENANCE))
        compose.onNodeWithTag(ProvenanceTags.AUTHOR, useUnmergedTree = true).assertDoesNotExist()
        assertTrue(drawing().dashed)
    }

    // Scenario 1 in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun theLineReadsInRussian() {
        val parent = save(recipe("Шакшука"))
        show(save(parent.fork(now, owner = null).copy(title = "Моя шакшука с фетой")))
        assertEquals(listOf("по рецепту «Шакшука»"), compose.textsOf(CardTags.PROVENANCE))
        show(save(recipe("Моя шакшука").copy(parent = RecipeId("gone"), statedParent = StatedParent("Шакшука", "Кухня на Васильевском"))))
        assertEquals(
            listOf("по рецепту «Шакшука», которого нет в книге", "Кухня на Васильевском · автор из файла, не проверен"),
            compose.textsOf(CardTags.PROVENANCE),
        )
    }

    // Criterion 3: the screen reader reads the title, then the line — the line follows the title within the group they
    // make, although it is drawn above it.
    @Test
    fun theScreenReaderReadsTheTitleBeforeTheLine() {
        val parent = save(recipe("Shakshuka"))
        show(save(parent.fork(now, owner = null).copy(title = "My shakshuka with feta")))
        val title = unmerged(CardTags.TITLE)
        val line = unmerged(CardTags.PROVENANCE)
        fun ancestors(node: SemanticsNode) = generateSequence(node) { it.parent }.toList()
        val titlePath = ancestors(title)
        val linePath = ancestors(line)
        val group = titlePath.first { candidate -> linePath.any { it.id == candidate.id } }
        assertEquals(true, group.config.getOrNull(SemanticsProperties.IsTraversalGroup))
        fun indexUnder(path: List<SemanticsNode>): Float {
            val child = path[path.indexOfFirst { it.id == group.id } - 1]
            return child.config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f
        }
        assertTrue("the title's part is read before the line", indexUnder(titlePath) < indexUnder(linePath))
        // Drawn above it all the same.
        assertTrue(line.boundsInRoot.top < title.boundsInRoot.top)
        assertNotNull(compose.onNodeWithTag(CardTags.TITLE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Heading))
    }

    // At twice the font on 320 dp the line, the author under it and the title read whole, and the nodes still stand level
    // with the first lines of the line and of the title.
    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontOn320dpTheLineIsWholeAndItsNodesKeepTheirPlaces() {
        val parent = save(recipe("Шакшука с печёными перцами и копчёной паприкой"))
        show(save(parent.fork(now, owner = null).copy(title = "Моя шакшука с фетой и зеленью")))
        assertLevel("по рецепту «Шакшука с печёными перцами и копчёной паприкой»")
        show(
            save(
                recipe("Моя шакшука с фетой и зеленью").copy(
                    parent = RecipeId("gone"),
                    statedParent = StatedParent("Шакшука с печёными перцами", "Кухня на Васильевском острове"),
                ),
            ),
        )
        assertLevel("по рецепту «Шакшука с печёными перцами», которого нет в книге")
        compose.assertWhole(ProvenanceTags.AUTHOR)
    }

    private fun assertLevel(line: String) {
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.PROVENANCE).firstOrNull() == line }
        compose.waitForIdle()
        compose.assertWhole(CardTags.PROVENANCE)
        compose.assertWhole(CardTags.TITLE)
        val (wordsNode, laid) = words(line)
        assertTrue("the line wraps at this size", laid.lineCount > 1)
        val column = unmerged(ProvenanceTags.COLUMN).boundsInRoot
        val (titleNode, titleLaid) = title()
        assertEquals(firstLineMiddle(wordsNode, laid), column.top + drawing().parent.y.value * density(), 1f)
        assertEquals(firstLineMiddle(titleNode, titleLaid), column.top + drawing().own.y.value * density(), 1f)
    }

    // A recipe with no parent has no line and no column; its title stands at the gutter.
    @Test
    fun aRecipeWithNoParentHasNoColumn() {
        val recipe = save(recipe("Toast"))
        compose.setContent { AppTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.PROVENANCE).assertDoesNotExist()
        compose.onNodeWithTag(ProvenanceTags.COLUMN, useUnmergedTree = true).assertDoesNotExist()
    }
}
