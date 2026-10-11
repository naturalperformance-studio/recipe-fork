package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * A step's author's note on the card: under the step's text, before its timers, muted and a size smaller, on View and on
 * Cooking; read by the screen reader after the step's text; and a step holding only a note is still a step.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardStepNoteTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val scheme = lightColorScheme()
    private val note = "Бабушка всегда добавляла щепотку сахара"

    private val recipe = Recipe.create(now, null).copy(
        title = "Borscht",
        steps = listOf(
            Step(0, "Fry the beet.", timer = Timer(12.minutes)).withNote(note),
            Step(1, "").withNote("Taste as you go."),
            Step(2, "Serve."),
        ),
    )

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

    private fun show(mode: CardMode) {
        runBlocking { recipes.save(recipe) }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card, LocalAwakeClock provides TestAwakeClock()) {
                MaterialTheme(colorScheme = scheme) {
                    CardScreen(recipe.id, recipes, CardEnvironment(lastTab = HeldLastCardTab(mode)) { emptyList() }, onBack = {}, onEdit = {})
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.CONTENT), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { exists(StepRowTags.note(0)) }
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun layoutOf(tag: String): TextLayoutResult = mutableListOf<TextLayoutResult>().also {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(it)
    }.single()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** What [mode] shows of the note: under the text, before the first timer, muted and smaller, read after the text. */
    private fun assertTheNoteStands(mode: CardMode, timerTag: String) {
        show(mode)
        // Read by the screen reader as part of the step, after its text.
        assertEquals(listOf("1.", "Fry the beet.", note), compose.textsOf(CardTags.step(0)))
        // Under the text, before the timer.
        compose.onNodeWithTag(timerTag).performScrollTo()
        val text = bounds(StepTags.text(0))
        val shown = bounds(StepRowTags.note(0))
        assertTrue("the note at ${shown.top} under the text ending ${text.bottom}", shown.top >= text.bottom)
        assertTrue("the note ending ${shown.bottom} above the timer at ${bounds(timerTag).top}", shown.bottom <= bounds(timerTag).top)
        assertTrue(shown.left >= text.left - 1f)
        // Muted, and smaller than the step's text.
        val noteLayout = layoutOf(StepRowTags.note(0))
        assertEquals(scheme.onSurfaceVariant, noteLayout.layoutInput.style.color)
        assertTrue(noteLayout.layoutInput.style.fontSize < layoutOf(StepTags.text(0)).layoutInput.style.fontSize)
        // A step holding only a note is shown, numbered, and reads as its note; a step with none shows none.
        assertTrue("Taste as you go." in compose.textsOf(CardTags.step(1)))
        assertEquals("2.", compose.textsOf(CardTags.step(1)).first())
        assertEquals(listOf("3.", "Serve."), compose.textsOf(CardTags.step(2)))
        assertTrue(!exists(StepRowTags.note(2)))
    }

    @Test
    fun onCookingTheNoteStandsUnderTheTextMutedAndSmaller() = assertTheNoteStands(CardMode.COOKING, TimerTags.start(0))

    @Test
    fun onViewTheNoteStandsUnderTheTextMutedAndSmaller() = assertTheNoteStands(CardMode.VIEW, ViewTimerTags.line(0))

    // The note is the author's words, shown as written whatever the interface language.
    @Test
    @Config(qualifiers = "ru")
    fun inRussianTheNoteReadsAsWritten() {
        show(CardMode.COOKING)
        assertEquals(listOf("1.", "Fry the beet.", note), compose.textsOf(CardTags.step(0)))
    }

    // A step with neither text nor note nor media is still left out.
    @Test
    fun aStepOfNothingIsStillLeftOut() {
        runBlocking { recipes.save(recipe.copy(steps = recipe.steps + Step(3, "  "))) }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card, LocalAwakeClock provides TestAwakeClock()) {
                MaterialTheme(colorScheme = scheme) {
                    CardScreen(recipe.id, recipes, CardEnvironment(lastTab = HeldLastCardTab(CardMode.COOKING)) { emptyList() }, onBack = {}, onEdit = {})
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.step(2)), PATIENCE_MS)
        assertTrue(!exists(CardTags.step(3)))
    }
}
