package app.recipefork.app.card

import app.recipefork.app.data.HeldLastCardTab
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * On the card, a step's timer stands clearly apart from the step: its row sits a gap of about 12 dp below the step's
 * text, or below its photograph where the step carries one.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardStepTimerGapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment(lastTab = HeldLastCardTab(CardMode.COOKING)) { emptyList() }
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-04T12:00:00Z")

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

    private fun photo(mediaId: String, step: Int): Media {
        TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 1600, 1200)
        return Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.ToStep(step), MediaVisibility.PRIVATE)
    }

    private fun show(recipe: Recipe) {
        runBlocking { recipes.save(recipe) }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card, LocalAwakeClock provides TestAwakeClock()) {
                MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.CONTENT), PATIENCE_MS)
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun assertGap(expected: Dp, gap: Float) {
        val px = with(compose.density) { expected.toPx() }
        assertEquals("the timer sits $gap px below, expected $px px", px, gap, 1f)
    }

    @Test
    fun aTimerSitsAClearGapBelowItsStepsText() {
        show(Recipe.create(now, null).copy(title = "Rice", steps = listOf(Step(0, "Simmer the rice covered.", timer = Timer(20.minutes)))))
        compose.waitUntilExactlyOneExists(hasTestTag(TimerTags.start(0)), PATIENCE_MS)
        compose.onNodeWithTag(TimerTags.start(0)).performScrollTo()
        assertGap(12.dp, bounds(TimerTags.start(0)).top - bounds(CardTags.step(0)).bottom)
    }

    @Test
    fun aTimerSitsAClearGapBelowItsStepsPhoto() {
        show(
            Recipe.create(now, null).copy(
                title = "Rice",
                steps = listOf(Step(0, "Rinse."), Step(1, "Simmer the rice covered.", timer = Timer(20.minutes))),
                media = listOf(photo("p1", 1)),
            ),
        )
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(1)), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(TimerTags.start(1)), PATIENCE_MS)
        compose.onNodeWithTag(TimerTags.start(1)).performScrollTo()
        assertGap(12.dp, bounds(TimerTags.start(1)).top - bounds(CardTags.stepPhoto(1)).bottom)
    }
}
