package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
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
 * A step's clip keeps to its box (MEDIA-004): the box fills the step's media width; a landscape or square clip is
 * shown at its own proportions, a taller one — upright by its pixels or by a turn the file states — in a square, as a
 * photograph is; the step's text lies where it lies beside a photograph and is never under the clip. The round 56 dp
 * control sits in the clip's bottom-end corner, and a tap on the clip pauses and plays it too.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class CardStepClipBoxTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val store = PhotoStore(context.filesDir)
    private val players = FakeClipPlayers()
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private var recipe by mutableStateOf<Recipe?>(null)

    @Before
    fun setUp() {
        FileProviderCache.clear()
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalClipPlayers provides players) {
                    recipe?.let { shown -> key(shown.id) { CardScreen(shown.id, recipes, environment, onBack = {}, onEdit = {}) } }
                }
            }
        }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    private fun media(mediaId: String, kind: MediaKind) = Media(mediaId, null, kind, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE)

    private fun clip(mediaId: String): Media {
        store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(TestRecipeFiles.mp4(4_000))
        return media(mediaId, MediaKind.VIDEO)
    }

    private fun photo(mediaId: String, width: Int, height: Int): Media {
        TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, width, height)
        return media(mediaId, MediaKind.PHOTO)
    }

    private fun threeSteps(media: Media) = Recipe.create(now, null).copy(
        title = "Borscht",
        steps = listOf(Step(0, "Boil the beets."), Step(1, "Fry the onion and the carrot until soft."), Step(2, "Serve.")),
        media = listOf(media),
    )

    private fun show(shown: Recipe, mediaTag: String) {
        runBlocking { recipes.save(shown) }
        compose.runOnIdle { recipe = shown }
        compose.waitUntilExactlyOneExists(hasTestTag(mediaTag), PATIENCE_MS)
        // Its size is known once the player has read the clip, or the photograph is decoded.
        compose.waitForIdle()
    }

    private fun showClip(width: Int, height: Int, rotation: Int) {
        players.width = width
        players.height = height
        players.rotation = rotation
        show(threeSteps(clip("c-$width-$height-$rotation")), CardTags.stepVideo(1))
        compose.waitUntil(PATIENCE_MS) { players.made.lastOrNull()?.let { !it.released && it.playing } == true }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun overlaps(a: Rect, b: Rect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** The steps' rows and the media of the middle one. */
    private class Layout(val above: Rect, val media: Rect, val below: Rect)

    private fun layout(mediaTag: String) = Layout(bounds(CardTags.step(1)), bounds(mediaTag), bounds(CardTags.step(2)))

    /** The clip's box is clear of the step texts about it, and holds its control. */
    private fun assertKeepsToItsBox(layout: Layout) {
        assertFalse("over its step's text", overlaps(layout.media, layout.above))
        assertFalse("over the next step's text", overlaps(layout.media, layout.below))
        assertTrue("under its step's text", layout.media.top >= layout.above.bottom)
        assertTrue("before the next step", layout.media.bottom <= layout.below.top)
        val control = bounds(CardTags.stepVideoControl(1))
        val least = with(compose.density) { CardTarget.toPx() }
        assertEquals(least, control.width, 0.5f)
        assertEquals(least, control.height, 0.5f)
        assertTrue("inside the clip", control.left >= layout.media.left && control.right <= layout.media.right)
        assertTrue("inside the clip", control.top >= layout.media.top && control.bottom <= layout.media.bottom)
        assertTrue("in the bottom-end corner", control.center.x > layout.media.center.x && control.center.y > layout.media.center.y)
    }

    private fun assertSame(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f)
        assertEquals(expected.bottom, actual.bottom, 1f)
    }

    // A portrait photograph's place: the rule a portrait clip follows.
    private fun portraitPhoto(): Layout {
        show(threeSteps(photo("p-portrait", 720, 1280)), CardTags.stepPhoto(1))
        return layout(CardTags.stepPhoto(1))
    }

    // Shot upright and stored as landscape pixels turned by 90 degrees, as the converted clip of a phone's camera is.
    @Test
    fun aPortraitClipByItsTurnIsASquareWhereAPortraitPhotographIs() {
        val photo = portraitPhoto()
        showClip(1280, 720, rotation = 90)
        val clip = layout(CardTags.stepVideo(1))
        assertKeepsToItsBox(clip)
        assertEquals("a square, no taller than 4:5", clip.media.width, clip.media.height, 1f)
        assertTrue(clip.media.height <= 1.25f * clip.media.width)
        assertSame(photo.media, clip.media)
        assertSame(photo.above, clip.above)
        assertSame(photo.below, clip.below)
    }

    @Test
    fun aPortraitClipByItsPixelsIsASquareWhereAPortraitPhotographIs() {
        val photo = portraitPhoto()
        showClip(720, 1280, rotation = 0)
        val clip = layout(CardTags.stepVideo(1))
        assertKeepsToItsBox(clip)
        assertEquals(clip.media.width, clip.media.height, 1f)
        assertSame(photo.media, clip.media)
        assertSame(photo.above, clip.above)
    }

    @Test
    fun aLandscapeClipIsShownAtItsOwnProportionsWhereALandscapePhotographIs() {
        show(threeSteps(photo("p-landscape", 1280, 720)), CardTags.stepPhoto(1))
        val photo = layout(CardTags.stepPhoto(1))
        showClip(1280, 720, rotation = 0)
        val clip = layout(CardTags.stepVideo(1))
        assertKeepsToItsBox(clip)
        assertEquals(clip.media.width * 720f / 1280f, clip.media.height, 1f)
        assertSame(photo.media, clip.media)
        assertSame(photo.above, clip.above)
    }

    // Portrait pixels turned by 270 are seen landscape, and shown whole at their own proportions.
    @Test
    fun aTurnedPortraitPictureIsALandscapeClip() {
        showClip(720, 1280, rotation = 270)
        val clip = layout(CardTags.stepVideo(1))
        assertKeepsToItsBox(clip)
        assertEquals(clip.media.width * 720f / 1280f, clip.media.height, 1f)
    }

    @Test
    fun aSquareClipIsASquare() {
        showClip(720, 720, rotation = 0)
        val clip = layout(CardTags.stepVideo(1))
        assertKeepsToItsBox(clip)
        assertEquals(clip.media.width, clip.media.height, 1f)
        // The box fills the step's media width: from under the step's text to the card's edge, as a photograph's.
        val step = bounds(CardTags.step(1))
        assertEquals(step.right, clip.media.right, 1f)
        assertEquals(step.left + with(compose.density) { 32.dp.toPx() }, clip.media.left, 1f)
    }

    private fun controlName() = compose.onNodeWithTag(CardTags.stepVideoControl(1)).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    // WCAG 2.2.2: a tap on the clip itself pauses it and plays it again, as the control does.
    @Test
    fun aTapOnTheClipPausesAndPlaysIt() {
        showClip(1280, 720, rotation = 90)
        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.stepVideo(1)))
        compose.onNodeWithTag(CardTags.stepVideo(1)).performTouchInput { click(center.copy(x = center.x / 2)) }
        // The tap is heard once the composition has taken it in.
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { !players.made.last().playing }
        assertEquals("Play video", controlName())
        compose.onNodeWithTag(CardTags.stepVideo(1)).performTouchInput { click(center.copy(x = center.x / 2)) }
        // The tap is heard once the composition has taken it in.
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { players.made.last().playing }
        assertEquals("Pause video", controlName())
        // The control still does it, as a button.
        compose.onNodeWithTag(CardTags.stepVideoControl(1)).performClick()
        compose.waitUntil(PATIENCE_MS) { !players.made.last().playing }
        assertEquals(Role.Button, compose.onNodeWithTag(CardTags.stepVideoControl(1)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun theControlIsNamedInTheInterfaceLanguage() {
        showClip(1280, 720, rotation = 90)
        assertEquals("Остановить видео", controlName())
        compose.onNodeWithTag(CardTags.stepVideoControl(1)).performClick()
        compose.waitUntil(PATIENCE_MS) { !players.made.last().playing }
        assertEquals("Воспроизвести видео", controlName())
    }
}
