package app.recipefork.app.editor

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.media.AnimationClips
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.crop.CropTags
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * The phone turned while a picture picked for a step is told apart (scenario 3): the activity is recreated, and the
 * pick still ends where it would have — an animation attached as a clip and said once, a still picture in the crop on
 * the new screen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepAnimationRecreationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var book: TestBook
    private lateinit var device: TestDevice
    private lateinit var recipes: RecipeRepository
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val images = WatchedAnimatedImages(context).apply { gate = CompletableDeferred() }
    private val recipe = Recipe.create(Instant.parse("2026-10-09T12:00:00Z"), null)
        .copy(title = "Borscht", steps = listOf(Step(0, "Boil."), Step(1, "Fry."), Step(2, "Serve.")))

    @get:Rule(order = 0)
    val hosted = object : ExternalResource() {
        override fun before() {
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, EditorHostActivity::class.java))
            FileProviderCache.clear()
            book = TestBook(context)
            device = TestDevice(context)
            recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
            runBlocking { recipes.save(recipe) }
            val environment = EditorEnvironment(
                ActiveBook(BookName.Pool, book.database),
                device.catalog(context),
                clipStore = { ClipStore(it.filesDir, { _, target -> target.writeBytes(ByteArray(2000)) }, { 10.seconds }) },
                animatedImages = { images },
                animationStore = { context, shared ->
                    AnimationClips(shared) { _, target -> target.writeBytes(ByteArray(1500) { 5 }) }.let { ClipStore(context.filesDir, it, it) }
                },
            ) { Instant.parse("2026-10-09T12:00:00Z") }
            EditorHostActivity.current = EditorHostActivity.Hosted(recipe.id, recipes, environment, saving)
        }

        override fun after() {
            images.gate?.cancel()
            saving.cancel()
            EditorHostActivity.current = null
            book.close()
            device.close()
            PhotoStore(context.filesDir).directory(BookName.Pool).deleteRecursively()
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<EditorHostActivity>()

    /** Whether the node tagged [tag] is there — for a part of a step's sheet, in that step's sheet, opened for it. */
    private fun exists(tag: String): Boolean {
        compose.reachStep(tag)
        return compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun onRow(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun stored(): Recipe {
        compose.waitForIdle()
        return runBlocking { recipes.get(recipe.id) }!!
    }

    /** Picks the fixture [fixture] as the photo of the step at [position], then turns the phone while it is told apart. */
    private fun pickThenTurn(position: Int, fixture: String) {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        compose.inStep(position).onNodeWithTag(EditorTags.stepPhoto(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepPhotoChoose(position)), PATIENCE_MS)
        compose.inStep(position).onNodeWithTag(EditorTags.stepPhotoChoose(position)).performClick()
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val intent = activity.nextStartedActivityForResult.intent
        val file = File(context.cacheDir, "turned-$fixture").apply {
            writeBytes(checkNotNull(StepAnimationRecreationTest::class.java.getResourceAsStream("/animation/$fixture")).use { it.readBytes() })
        }
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoWorking(position)) && images.asked.isNotEmpty() }

        compose.activityRule.scenario.recreate()

        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    // An animation told apart across the turn is attached as a clip on the new screen and said once.
    @Test
    fun anAnimationPickedBeforeTurningThePhoneIsAttachedAsAClip() {
        pickThenTurn(1, "animated.gif")

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoWorking(1)) }
        images.gate!!.complete(Unit)

        val said = "Clip attached. It was converted on this phone to at most 1280 pixels and 6 MB, and its sound was removed."
        compose.awaitStatementOnceSheetCloses(said)
        compose.waitUntil(PATIENCE_MS) { stored().stepMedia(1)?.kind == MediaKind.VIDEO }
        assertEquals(1, compose.onAllNodes(hasText(said), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertFalse(exists(CropTags.SCREEN))
    }

    // A still picture told apart across the turn opens the crop on the new screen, and is attached once cropped.
    @Test
    fun aStillPickedBeforeTurningThePhoneOpensTheCropOnTheNewScreen() {
        pickThenTurn(2, "still.png")

        images.gate!!.complete(Unit)

        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.onNodeWithTag(CropTags.DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { stored().stepMedia(2)?.kind == MediaKind.PHOTO }
        assertTrue(images.converted.isEmpty())
        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
    }
}
