package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
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
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * A step marked done on Cooking folds over several frames, and unfolds as it is unmarked; with the system's animator
 * duration scale at 0 it stands folded, or unfolded, in the frame after the tap. The scale is handed to the card as a
 * real window hands on the system's ([app.recipefork.app.ui.SystemAnimationScaleTest]).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class CardStepFoldMotionTest {
    private var scale = 1f

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        object : MotionDurationScale {
            override val scaleFactor: Float get() = scale
        },
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))

    private val recipe = Recipe.create(Instant.parse("2026-10-11T12:00:00Z"), null).copy(
        title = "Stew",
        steps = listOf(
            Step(
                0,
                "Brown the beef, stirring the pot slowly with a wooden spoon so that nothing catches or burns.",
                timers = listOf(Timer(5.minutes)),
                note = "Grandma used lard.",
            ),
            Step(1, "Add the onions."),
        ),
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(CardMode.COOKING)) { emptyList() }
        compose.setContent { MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(0))).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(CardTags.step(0)).performScrollTo()
        compose.waitForIdle()
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun height() = compose.onNodeWithTag(StepRowTags.entry(0), useUnmergedTree = true).fetchSemanticsNode().size.height

    /** The step's height before a tap on it, a few frames after, and once at rest. */
    private fun aTap(): Triple<Int, Int, Int> {
        val before = height()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(CardTags.step(0)).performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        val during = height()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return Triple(before, during, height())
    }

    @Test
    fun atAScaleOfOneTheStepFoldsAndUnfoldsOverSeveralFrames() {
        scale = 1f
        val (open, folding, folded) = aTap()
        assertTrue("folding: $open > $folding > $folded", folding in (folded + 1) until open)
        val (_, unfolding, unfolded) = aTap()
        assertTrue("unfolding: $folded < $unfolding < $unfolded", unfolding in (folded + 1) until unfolded)
        assertEquals(open, unfolded)
    }

    @Test
    fun atAScaleOfZeroTheStepStandsFoldedOrUnfoldedInTheFrameAfterTheTap() {
        scale = 0f
        val (open, folding, folded) = aTap()
        assertTrue(folded < open)
        assertEquals(folded, folding)
        val (_, unfolding, unfolded) = aTap()
        assertEquals(open, unfolded)
        assertEquals(unfolded, unfolding)
    }
}
