package app.recipefork.app.card

import android.content.Context
import android.provider.Settings
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeExport
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
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
import java.io.File
import java.time.Instant

/**
 * A player that plays nothing and remembers what the card asked of it; it reports a clip of [width] by [height]
 * pixels turned by [rotation] degrees.
 */
internal class FakeClipPlayer(private val fails: Boolean, private val width: Int = 1280, private val height: Int = 720, private val rotation: Int = 0) : ClipPlayer {
    var file: File? = null
    var playing = false
    var released = false
    private var listener: ClipPlayer.Listener? = null

    override suspend fun open(file: File, listener: ClipPlayer.Listener) {
        this.file = file
        this.listener = listener
        if (fails) listener.failed() else listener.ready(width, height, rotation)
    }

    override fun surface(surface: Surface?) = Unit

    override fun play() {
        check(!released)
        playing = true
    }

    override fun pause() {
        check(!released)
        playing = false
    }

    override fun release() {
        playing = false
        released = true
    }

    /** The clip stops being playable while shown. */
    fun breaks() = listener!!.failed()
}

internal class FakeClipPlayers(var fails: Boolean = false, var width: Int = 1280, var height: Int = 720, var rotation: Int = 0) : ClipPlayers {
    val made = mutableListOf<FakeClipPlayer>()
    override fun create(): ClipPlayer = FakeClipPlayer(fails, width, height, rotation).also { made += it }
}

/**
 * A step's clip on the card (MEDIA-004, A11Y-001, A11Y-007, FLOW-061): it loops silently while on screen, a 56 dp
 * control pauses it, it is named as the author's video; it does not start by itself with animations off; it pauses
 * off screen and when the card leaves, and is let go of with the card; a clip the device does not hold or cannot play
 * says it is unavailable. A forked or imported recipe's clip plays alike.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class CardStepVideoTest {
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

    @Before
    fun setUp() {
        FileProviderCache.clear()
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
        store.directory(BookName.Pool).deleteRecursively()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun clip(mediaId: String, step: Int, held: Boolean = true): Media {
        if (held) store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(TestRecipeFiles.mp4(4_000))
        return Media(mediaId, null, MediaKind.VIDEO, MediaAttachment.ToStep(step), MediaVisibility.PRIVATE)
    }

    private fun steps(count: Int, vararg media: Media) =
        Recipe.create(now, null).copy(title = "Soup", steps = (0 until count).map { Step(it, "Step text number $it.") }, media = media.toList())

    private var shown by mutableStateOf(true)

    private fun show(recipe: Recipe, save: Boolean = true) {
        if (save) runBlocking { recipes.save(recipe) }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalClipPlayers provides players) {
                    if (shown) CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {})
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.TITLE), PATIENCE_MS)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** The player made last: the one the clip holds, or held last. */
    private fun player() = players.made.last()

    /** Waits until a live player plays the clip, or until none plays it. */
    private fun awaitPlaying(playing: Boolean) = compose.waitUntil(PATIENCE_MS) {
        if (playing) players.made.lastOrNull()?.let { !it.released && it.playing } == true else players.made.none { it.playing }
    }

    private fun textOf(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    /** The control's accessible name: an icon button, named by its description. */
    private fun controlText(position: Int) =
        compose.onNodeWithTag(CardTags.stepVideoControl(position)).fetchSemanticsNode().let { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        }

    // MEDIA-004, A11Y-001: at its step, it plays and loops (the player loops), named as the author's video.
    @Test
    fun aClipPlaysAtItsStepNamedAsTheAuthorsVideo() {
        show(steps(3, clip("c1", 1)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(1)), PATIENCE_MS)
        assertEquals(listOf("Video of step 2"), compose.descriptionOf(CardTags.stepVideo(1)))
        awaitPlaying(true)
        assertEquals(store.file(BookName.Pool, "c1"), player().file)
        assertEquals("Pause video", controlText(1))
        val video = compose.onNodeWithTag(CardTags.stepVideo(1)).fetchSemanticsNode().boundsInRoot
        assertTrue("under its step's text", video.top >= compose.onNodeWithTag(CardTags.step(1)).fetchSemanticsNode().boundsInRoot.bottom)
        assertTrue("before the next step", video.bottom <= compose.onNodeWithTag(CardTags.step(2)).fetchSemanticsNode().boundsInRoot.top)
        // Never described: the name is the step's number, nothing of what it shows.
        assertFalse(exists(CardTags.stepPhoto(1)))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun theClipIsNamedInTheInterfaceLanguage() {
        show(steps(3, clip("c1", 2)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(2)), PATIENCE_MS)
        assertEquals(listOf("Видео шага 3"), compose.descriptionOf(CardTags.stepVideo(2)))
        awaitPlaying(true)
        assertEquals("Остановить видео", controlText(2))
    }

    // A11Y-007, WCAG 2.2.2: a 56 dp control pauses it, and plays it again; nothing on the card offers sound.
    @Test
    fun theControlPausesAndPlaysAndNothingOffersSound() {
        show(steps(3, clip("c1", 0)))
        awaitPlaying(true)
        val control = compose.onNodeWithTag(CardTags.stepVideoControl(0)).fetchSemanticsNode().boundsInRoot
        val least = with(compose.density) { CardTarget.toPx() }
        assertTrue(control.width >= least - 0.5f && control.height >= least - 0.5f)

        compose.onNodeWithTag(CardTags.stepVideoControl(0)).performClick()
        awaitPlaying(false)
        assertEquals("Play video", controlText(0))
        compose.onNodeWithTag(CardTags.stepVideoControl(0)).performClick()
        awaitPlaying(true)

        val actions = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { node ->
            (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")
        }
        assertTrue(actions.toString(), actions.none { Regex("sound|mute|volume|audio|звук", RegexOption.IGNORE_CASE).containsMatchIn(it) })
    }

    // MEDIA-004: scrolled off screen it pauses; back on screen it plays again.
    @Test
    fun aClipPausesOffScreenAndPlaysBackOnIt() {
        show(steps(30, clip("c1", 0)))
        awaitPlaying(true)
        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.step(29)))
        awaitPlaying(false)
        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.stepVideo(0)))
        awaitPlaying(true)
    }

    // MEDIA-004: the card leaving the screen pauses it; the card gone lets the player go (no leak).
    @Test
    fun aClipPausesWhenTheCardLeavesAndIsReleasedWithIt() {
        show(steps(3, clip("c1", 1)))
        awaitPlaying(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        awaitPlaying(false)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitPlaying(true)

        // The card goes: a change of state the test makes is seen once the composition is next idle.
        shown = false
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { players.made.all { it.released } }
        assertFalse(players.made.any { it.playing })
        assertFalse(exists(CardTags.stepVideo(1)))
    }

    // MEDIA-004, WCAG 2.2.2: with the system's animations off it does not start by itself; it shows its first frame,
    // with the control offering to play.
    @Test
    fun withAnimationsOffAClipWaitsToBePlayed() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show(steps(3, clip("c1", 1)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(1)), PATIENCE_MS)
        compose.waitForIdle()
        assertFalse(player().playing)
        assertEquals("Play video", controlText(1))
        compose.onNodeWithTag(CardTags.stepVideoControl(1)).performClick()
        awaitPlaying(true)
    }

    // FLOW-061: a clip whose file the device does not hold says so, in place of the clip; a step that is only that
    // clip is still shown, numbered.
    @Test
    fun aClipWhoseFileIsGoneSaysItIsUnavailable() {
        val recipe = steps(3, clip("gone", 0, held = false)).let { it.copy(steps = listOf(Step(0, ""), it.steps[1], it.steps[2])) }
        show(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepMediaUnavailable(0)), PATIENCE_MS)
        assertEquals("The video of step 1 is unavailable on this phone.", textOf(CardTags.stepMediaUnavailable(0)))
        assertTrue(exists(CardTags.step(0)))
        assertFalse(exists(CardTags.stepVideo(0)))
        assertTrue(players.made.isEmpty())
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun theUnavailableLineIsInTheInterfaceLanguage() {
        show(steps(3, clip("gone", 1, held = false)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepMediaUnavailable(1)), PATIENCE_MS)
        assertEquals("Видео шага 2 недоступно на этом телефоне.", textOf(CardTags.stepMediaUnavailable(1)))
    }

    // A file the player cannot play says the same, without a crash — at once, or once it breaks while shown.
    @Test
    fun anUnplayableClipSaysItIsUnavailable() {
        players.fails = true
        show(steps(3, clip("c1", 1)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepMediaUnavailable(1)), PATIENCE_MS)
        assertEquals("The video of step 2 is unavailable on this phone.", textOf(CardTags.stepMediaUnavailable(1)))
        assertFalse(exists(CardTags.stepVideo(1)))
        assertFalse(exists(CardTags.stepVideoControl(1)))
    }

    @Test
    fun aClipThatBreaksWhileShownSaysItIsUnavailable() {
        show(steps(3, clip("c1", 1)))
        awaitPlaying(true)
        compose.runOnIdle { player().breaks() }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepMediaUnavailable(1)), PATIENCE_MS)
        assertFalse(exists(CardTags.stepVideoControl(1)))
        // The card goes: a change of state the test makes is seen once the composition is next idle.
        shown = false
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { players.made.all { it.released } }
    }

    // Layout holds at 360 dp and twice the font size: the control is the card's target and inside the card, the line
    // saying the clip is unavailable wraps within it.
    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun theClipHoldsAt360dpAndTwiceTheFontSize() {
        show(steps(3, clip("c1", 0), clip("gone", 1, held = false)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideoControl(0)), PATIENCE_MS)
        assertEquals(2f, compose.density.fontScale, 0.001f)
        val content = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().boundsInRoot
        val control = compose.onNodeWithTag(CardTags.stepVideoControl(0)).fetchSemanticsNode().boundsInRoot
        val video = compose.onNodeWithTag(CardTags.stepVideo(0)).fetchSemanticsNode().boundsInRoot
        val least = with(compose.density) { CardTarget.toPx() }
        assertTrue(control.height >= least - 0.5f && control.width >= least - 0.5f)
        assertTrue(control.right <= content.right && video.right <= content.right)
        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.stepMediaUnavailable(1)))
        val line = compose.onNodeWithTag(CardTags.stepMediaUnavailable(1), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(line.right <= content.right && line.left >= content.left)
    }

    // Scenario 3, FORK-007: an own fork's clip still plays once the original is deleted.
    @Test
    fun anOwnForksClipPlaysOnceTheOriginalIsDeleted() {
        val parent = steps(2, clip("p-clip", 1))
        val fork = runBlocking {
            recipes.save(parent)
            val id = forkRecipe(recipes, active, parent.id, now)!!
            assertTrue(recipes.delete(parent.id))
            recipes.get(id)!!
        }
        show(fork, save = false)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(1)), PATIENCE_MS)
        awaitPlaying(true)
        assertEquals(store.file(BookName.Pool, fork.stepMedia(1)!!.mediaId), player().file)
        assertTrue(player().file!!.isFile)
    }

    // Scenario 4, FLOW-063, FLOW-064: a recipe with a clip, exported, deleted and imported, plays its clip on the card.
    @Test
    fun anImportedClipPlaysOnTheCard() {
        val recipe = steps(2, clip("c1", 1))
        val imported = runBlocking {
            recipes.save(recipe)
            val uri = RecipeExport.write(context, recipes, recipe.id)!!
            assertTrue(recipes.delete(recipe.id))
            val outcome = RecipeImport(recipes, context.filesDir, clock = { now }, clipDuration = TestRecipeFiles.SHORT_CLIP)
                .import { context.contentResolver.openInputStream(uri)!! }
            assertTrue(outcome is ImportOutcome.Added)
            recipes.get(recipe.id)!!
        }
        assertEquals(RecipeImport.FOUND_STATE, imported.state)
        show(imported, save = false)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(1)), PATIENCE_MS)
        awaitPlaying(true)
        assertEquals(store.file(BookName.Pool, imported.stepMedia(1)!!.mediaId), player().file)
    }

    private fun onScreen(tag: String): Boolean {
        // Bounds clipped by the card's scrolling: nothing of it left means off screen.
        return compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().any { node ->
            node.boundsInWindow.let { it.height > 0f && it.width > 0f }
        }
    }

    private fun live() = players.made.filterNot { it.released }.map { it.file!!.name }.toSet()

    /** The clips any part of which — the picture or its control — is on screen. */
    private fun visibleClips(count: Int) =
        (0 until count).filter { onScreen(CardTags.stepVideo(it)) || onScreen(CardTags.stepVideoControl(it)) }.map { "c$it" }.toSet()

    // MEDIA-004: only the clips on screen hold a player; one scrolled off lets its player go, one scrolled in gets one.
    @Test
    fun onlyTheClipsOnScreenHoldAPlayer() {
        val count = 8
        show(steps(count, *(0 until count).map { clip("c$it", it) }.toTypedArray()))
        compose.waitUntil(PATIENCE_MS) { live().isNotEmpty() && live() == visibleClips(count) }
        val first = live()
        assertTrue("c0" in first)
        assertTrue(first.size < count)

        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.stepVideoControl(count - 1)))
        compose.waitUntil(PATIENCE_MS) {  live().isNotEmpty() && live() == visibleClips(count) }
        assertTrue("c${count - 1}" in live())
        assertTrue("c0" !in live())
        assertTrue(players.made.filter { it.file!!.name == "c0" }.all { it.released })

        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.stepVideo(0)))
        compose.waitUntil(PATIENCE_MS) { "c0" in live() && live() == visibleClips(count) }
    }

    // A clip that failed is tried again when it next comes into view, and the reader's pause holds across.
    @Test
    fun aClipThatFailedIsTriedAgainWhenItComesBackIntoView() {
        players.fails = true
        show(steps(30, clip("c1", 0)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepMediaUnavailable(0)), PATIENCE_MS)
        players.fails = false
        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.step(29)))
        compose.waitUntil(PATIENCE_MS) { players.made.all { it.released } }
        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.step(0)))
        compose.waitUntil(PATIENCE_MS) { players.made.lastOrNull()?.let { !it.released && it.playing } == true }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideoControl(0)), PATIENCE_MS)

        // Paused by the reader, scrolled away and back: still paused.
        compose.onNodeWithTag(CardTags.stepVideoControl(0)).performClick()
        compose.waitUntil(PATIENCE_MS) { players.made.last().playing.not() }
        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.step(29)))
        compose.waitUntil(PATIENCE_MS) { players.made.all { it.released } }
        val made = players.made.size
        compose.onNodeWithTag(CardTags.CONTENT).performScrollToNode(hasTestTag(CardTags.stepVideo(0)))
        compose.waitUntil(PATIENCE_MS) { players.made.size > made && !players.made.last().released }
        compose.waitForIdle()
        assertFalse(players.made.last().playing)
        assertEquals("Play video", controlText(0))
    }

    // A frozen recipe shows its clip as any other.
    @Test
    fun aFrozenRecipePlaysItsClip() {
        show(steps(2, clip("c1", 0)).inState(RecipeState.PUBLISHED))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(0)), PATIENCE_MS)
        awaitPlaying(true)
    }
}
