package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.app.ui.WholeWords
import app.recipefork.app.ui.appColors
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * T-315, pencil margin notes: on View the reader's own note on a step stands in the margin in pencil — the reading face's
 * real italic at 14.5 sp, a 1.5 dp dashed rule down its start, a 16 dp pencil mark, all in the theme's graphite (#6A5B48
 * light, #CDBB9F dark) — after the step's duration labels, and breaks only between words at twice the font scale on a
 * 320 dp phone. Cooking keeps its note as it was; so does a found recipe's note, its author's, on View.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardMarginNoteTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val now = Instant.parse("2026-10-29T12:00:00Z")

    private val simmer = "Add the peppers and the tomatoes and simmer the sauce uncovered until it thickens."
    private val english = "Tinned tomatoes in winter, in their own juice: tastier."
    private val russian = "Зимой беру консервированные помидоры в собственном соку — вкуснее."

    private fun recipe(note: String) = Recipe.create(now, null).copy(
        title = "Shakshuka",
        steps = listOf(
            Step(0, "Fry the onion until soft."),
            Step(1, simmer, timers = listOf(Timer(15.minutes, "Sauce"), Timer(6.minutes, "Eggs")), note = note),
            Step(2, "Serve."),
        ),
    )

    private var scheme: ColorScheme? = null
    private var graphite: Color? = null

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(timers.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(timers.idling)
        book.close()
        timers.close()
    }

    /**
     * The card of a recipe of the book's own — or, [found], one the book holds from a file — its second step carrying
     * [note], on [mode].
     */
    private fun open(note: String = english, mode: CardMode = CardMode.VIEW, dark: Boolean = false, fontScale: Float = 1f, found: Boolean = false): Recipe {
        val recipe = recipe(note).let { if (found) it.inState(RecipeImport.FOUND_STATE) else it }
        runBlocking {
            if (found) assertTrue(recipes.addFound(recipe, now)) else recipes.save(recipe)
        }
        showCard(recipe.id, note, mode, dark, fontScale)
        return recipe
    }

    /** The recipe whose card is shown; the card's content is set once, and shows another recipe as this changes. */
    private var shownId by mutableStateOf<RecipeId?>(null)

    /** Shows [id]'s card on [mode], and waits until its second step's note reads [note]. */
    private fun showCard(id: RecipeId, note: String, mode: CardMode = CardMode.VIEW, dark: Boolean = false, fontScale: Float = 1f) {
        val first = shownId == null
        shownId = id
        if (first) {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalCardTimers provides timers.card,
                    LocalAwakeClock provides TestAwakeClock(),
                ) {
                    AppTheme(dark = dark) {
                        scheme = MaterialTheme.colorScheme
                        graphite = MaterialTheme.appColors.graphite
                        shownId?.let { shown ->
                            key(shown) {
                                CardScreen(shown, recipes, CardEnvironment(lastTab = HeldLastCardTab(mode)) { emptyList() }, onBack = {}, onEdit = {})
                            }
                        }
                    }
                }
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(StepRowTags.note(1)) && compose.textsOf(StepRowTags.note(1)) == listOf(note) }
        scroll(StepRowTags.note(1))
    }

    /** A step note shown as the found recipe's author wrote it: muted under the text, upright, read with the step. */
    private fun assertMuted(note: String) {
        val style = layoutOf(StepRowTags.note(1)).layoutInput.style
        assertEquals(scheme!!.onSurfaceVariant, style.color)
        assertNotEquals(FontStyle.Italic, style.fontStyle)
        assertFalse("no pencil for the author's note", exists(StepRowTags.margin(1)))
        assertFalse(exists(StepRowTags.mark(1)))
        assertEquals(listOf("2.", simmer, note), compose.textsOf(CardTags.step(1)))
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun layoutOf(tag: String): TextLayoutResult = mutableListOf<TextLayoutResult>().also {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(it)
    }.single()

    private fun px(dp: Dp) = with(compose.density) { dp.toPx() }

    private fun scroll(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
    }

    private fun assertNear(message: String, expected: Float, actual: Float) =
        assertTrue("$message: expected $expected px, was $actual px", abs(expected - actual) <= 1.5f)

    private fun close(a: Color, b: Color, within: Float) =
        listOf(a.red - b.red, a.green - b.green, a.blue - b.blue).all { abs(it) < within }

    /** The note's words in the reading face's italic, at 14.5 sp, in graphite; its mark 16 dp and saying nothing. */
    private fun assertPencil(expected: Color) {
        assertEquals("the theme's graphite", expected, graphite)
        val style = layoutOf(StepRowTags.note(1)).layoutInput.style
        assertEquals("the reading face", ReadingFamily, style.fontFamily)
        assertEquals("its italic", FontStyle.Italic, style.fontStyle)
        assertEquals(14.5.sp, style.fontSize)
        assertEquals("in graphite", expected, style.color)
        val mark = bounds(StepRowTags.mark(1))
        assertNear("the mark 16 dp wide", px(16.dp), mark.width)
        assertNear("the mark 16 dp high", px(16.dp), mark.height)
        val node = compose.onNodeWithTag(StepRowTags.mark(1), useUnmergedTree = true).fetchSemanticsNode()
        assertNull("the mark is decorative", node.config.getOrNull(SemanticsProperties.ContentDescription))
        // The mark before the words, the words after the rule's inset and the mark's gap.
        val margin = bounds(StepRowTags.margin(1))
        assertNear("the mark inside the rule's inset", margin.left + px(MARGIN_NOTE_INSET), mark.left)
        assertNear("the words after the mark", mark.right + px(MARGIN_NOTE_MARK_GAP), bounds(StepRowTags.note(1)).left)
    }

    @Test
    fun onViewOnesOwnNoteStandsInPencil() {
        open()
        assertPencil(Color(0xFF6A5B48))
    }

    @Test
    fun inTheDarkSchemeThePencilIsTheLightGraphite() {
        open(dark = true)
        assertPencil(Color(0xFFCDBB9F))
    }

    // The rule down the note's start is 1.5 dp of graphite, broken into dashes; the mark is drawn in graphite too.
    @Test
    fun theRuleIsDashedAndTheMarkIsGraphite() {
        open()
        assertEquals(1.5.dp, MARGIN_NOTE_RULE)
        val margin = bounds(StepRowTags.margin(1))
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        fun at(x: Float, y: Int) = Color(image.getPixel(x.toInt(), y))
        val column = (margin.top.toInt() + 1 until margin.bottom.toInt() - 1).map { at(margin.left + px(MARGIN_NOTE_RULE) / 2, it) }
        val dashes = column.count { close(it, graphite!!, 0.12f) }
        val gaps = column.count { close(it, scheme!!.surface, 0.02f) }
        assertTrue("the rule draws in graphite down the note: $dashes of ${column.size}", dashes >= column.size / 3)
        assertTrue("the rule is dashed, not solid: $gaps gaps of ${column.size}", gaps >= column.size / 6)
        // Clear of the rule, before the mark: the page.
        assertTrue(close(at(margin.left + px(MARGIN_NOTE_RULE) + px(3.dp), (margin.top + margin.bottom).toInt() / 2), scheme!!.surface, 0.02f))
        // The mark's strokes are graphite.
        val mark = bounds(StepRowTags.mark(1))
        val drawn = (mark.left.toInt() until mark.right.toInt()).flatMap { x -> (mark.top.toInt() until mark.bottom.toInt()).map { y -> at(x.toFloat(), y) } }
        assertTrue("the mark is pencilled in graphite", drawn.any { close(it, graphite!!, 0.08f) })
    }

    // As the mock draws it: in the text's column, under the step's duration labels; the screen reader reads the step's
    // line, then the labels, then the note.
    @Test
    fun theNoteStandsAfterTheDurationLabels() {
        open()
        val margin = bounds(StepRowTags.margin(1))
        val labels = bounds(StepBlockTags.labels(1))
        val text = bounds(StepTags.text(1))
        assertTrue("the note at ${margin.top} under the labels ending ${labels.bottom}", margin.top >= labels.bottom)
        assertNear("the note 8 dp under the labels", labels.bottom + px(VIEW_LABEL_GAP), margin.top)
        assertNear("the note in the text's column", text.left, margin.left)
        assertTrue("the note within the column", margin.right <= text.right + 0.5f)
        assertEquals(listOf("2.", simmer), compose.textsOf(CardTags.step(1)))
        assertEquals(listOf(english), compose.textsOf(StepRowTags.note(1)))
        val read = compose.onAllNodes(hasAnyAncestor(hasTestTag(StepRowTags.entry(1))) and hasTestTag(StepRowTags.note(1))).fetchSemanticsNodes()
        assertEquals("the note is read once", 1, read.size)
    }

    // A design check saw the Russian note break «консервированн|ые» at twice the font scale on a 320 dp phone.
    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun atTwiceTheFontScaleOnA320dpPhoneTheNoteBreaksOnlyBetweenWords() {
        open(note = russian, fontScale = 2f)
        compose.assertWhole(StepRowTags.note(1))
        WholeWords.assertWhole(compose, hasTestTag(StepRowTags.note(1)), "the margin note")
        val layout = layoutOf(StepRowTags.note(1))
        assertTrue("the note wraps over several lines", layout.lineCount > 1)
        val text = layout.layoutInput.text.text
        assertTrue("«консервированные» stands whole on one line", (0 until layout.lineCount).any {
            text.substring(layout.getLineStart(it), layout.getLineEnd(it)).contains("консервированные")
        })
        assertTrue("the note within the card", bounds(StepRowTags.margin(1)).right <= bounds(CardTags.CONTENT).right + 0.5f)
    }

    // Cooking keeps its note as it was: muted under the text, upright, read with the step; no pencil.
    @Test
    fun cookingKeepsItsNote() {
        open(mode = CardMode.COOKING)
        val style = layoutOf(StepRowTags.note(1)).layoutInput.style
        assertEquals(scheme!!.onSurfaceVariant, style.color)
        assertNotEquals(FontStyle.Italic, style.fontStyle)
        assertFalse("no margin note on Cooking", exists(StepRowTags.margin(1)))
        assertFalse(exists(StepRowTags.mark(1)))
        assertEquals(listOf("2.", simmer, english), compose.textsOf(CardTags.step(1)))
    }

    // T-316: the card itself tells them apart — a found recipe (RecipeRepository.isMarked: the book holds it from a
    // file) has its author's notes, which keep the muted look under the text on View, read with the step; no pencil.
    @Test
    fun onTheCardAFoundRecipesNoteKeepsItsLookOnView() {
        val recipe = open(found = true)
        assertTrue(runBlocking { recipes.isMarked(recipe.id) })
        assertMuted(english)
        assertTrue("under the text, above the labels", bounds(StepRowTags.note(1)).bottom <= bounds(StepBlockTags.labels(1)).top)
    }

    // A fork of a found recipe is the reader's own: the fork leaves the author's notes behind (FORK-002), the note the
    // reader then writes on it is pencilled on the fork's card, while the found parent's card keeps its author's note
    // muted.
    @Test
    fun onTheCardAForkOfAFoundRecipeIsOnesOwn() {
        val parent = recipe(english).inState(RecipeImport.FOUND_STATE)
        assertTrue(runBlocking { recipes.addFound(parent, now) })
        val forkId = requireNotNull(runBlocking { forkRecipe(recipes, active, parent.id, now.plusSeconds(60), store = null) })
        val fork = requireNotNull(runBlocking { recipes.get(forkId) })
        assertEquals(parent.id, fork.parent)
        assertNull("the fork leaves the author's note behind", fork.steps[1].note)
        assertFalse(runBlocking { recipes.isMarked(forkId) })
        // The reader's own note on the fork, as the editor writes it.
        val mine = "Less salt; the feta is salty."
        runBlocking { recipes.save(fork.copy(steps = fork.steps.map { if (it.position == 1) it.copy(note = mine) else it })) }

        showCard(forkId, mine)
        assertTrue("the fork's own note in the margin", exists(StepRowTags.margin(1)))
        assertPencil(Color(0xFF6A5B48))
        assertEquals(listOf(mine), compose.textsOf(StepRowTags.note(1)))

        showCard(parent.id, english)
        assertMuted(english)
        assertTrue("under the text, above the labels", bounds(StepRowTags.note(1)).bottom <= bounds(StepBlockTags.labels(1)).top)
    }

    // The same recipe as the book's own is pencilled on the card: the flag is the book's, not the recipe's content.
    @Test
    fun onTheCardTheSameRecipeAsOnesOwnIsPencilled() {
        val recipe = open()
        assertFalse(runBlocking { recipes.isMarked(recipe.id) })
        assertTrue(exists(StepRowTags.margin(1)))
        assertPencil(Color(0xFF6A5B48))
    }

    // A found recipe's notes are its author's: on View they keep the muted look under the text, read with the step.
    @Test
    fun aFoundRecipesNoteKeepsItsLookOnView() {
        val recipe = recipe(english)
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card) {
                AppTheme {
                    scheme = MaterialTheme.colorScheme
                    Box(Modifier.width(360.dp).padding(horizontal = 16.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            StepsList(recipe.steps, recipe.id, recipe.title, mode = CardMode.VIEW, ownNotes = false)
                        }
                    }
                }
            }
        }
        compose.waitUntil(PATIENCE_MS) { exists(StepRowTags.note(1)) }
        val style = layoutOf(StepRowTags.note(1)).layoutInput.style
        assertEquals(scheme!!.onSurfaceVariant, style.color)
        assertNotEquals(FontStyle.Italic, style.fontStyle)
        assertFalse("no pencil for the author's note", exists(StepRowTags.margin(1)))
        assertEquals(listOf("2.", simmer, english), compose.textsOf(CardTags.step(1)))
        assertTrue("under the text, above the labels", bounds(StepRowTags.note(1)).bottom <= bounds(StepBlockTags.labels(1)).top)
    }
}
