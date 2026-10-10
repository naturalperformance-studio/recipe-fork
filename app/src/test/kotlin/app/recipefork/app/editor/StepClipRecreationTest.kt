package app.recipefork.app.editor

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
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
import app.recipefork.app.media.ClipConverter
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
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

/** A screen holding the editor as its own content, so that it comes back whole when recreated, as MainActivity does. */
class EditorHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = current!!
        setContent {
            MaterialTheme { EditorScreen(host.recipeId, host.recipes, host.environment, host.saving, onClose = {}) }
        }
    }

    /** What the host shows: set by the test before the host is launched; [saving] outlives the activity. */
    class Hosted(val recipeId: RecipeId, val recipes: RecipeRepository, val environment: EditorEnvironment, val saving: CoroutineScope)

    companion object {
        var current: Hosted? = null
    }
}

/**
 * The phone turned while a step's clip is converted (FLOW-026, MEDIA-005): the activity is recreated, and the step
 * still shows the conversion with its Cancel, which still stops it; or, left to finish, the clip is attached and
 * "attached" is said once, on the new screen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepClipRecreationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var book: TestBook
    private lateinit var device: TestDevice
    private lateinit var recipes: RecipeRepository
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = CompletableDeferred<Unit>()
    private val recipe = Recipe.create(Instant.parse("2026-10-03T12:00:00Z"), null)
        .copy(title = "Borscht", steps = listOf(Step(0, "Boil."), Step(1, "Fry."), Step(2, "Serve.")))
    private val converter = ClipConverter { _, target ->
        gate.await()
        target.writeBytes(ByteArray(2000) { 4 })
    }

    /** The host is the test's own, in no manifest; the book and the editor's environment are made before it starts. */
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
                clipStore = { ClipStore(it.filesDir, converter, { 10.seconds }) },
            ) { Instant.parse("2026-10-03T12:00:00Z") }
            EditorHostActivity.current = EditorHostActivity.Hosted(recipe.id, recipes, environment, saving)
        }

        override fun after() {
            gate.cancel()
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

    private fun held(): List<String> = PhotoStore(context.filesDir).directory(BookName.Pool).list()?.sorted().orEmpty()

    private fun statements() = compose.onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes()

    /** Chooses a clip at the step at [position], then turns the phone while it is being converted. */
    private fun chooseThenTurn(position: Int) {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        compose.inStep(position).onNodeWithTag(EditorTags.stepPhoto(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepClipChoose(position)), PATIENCE_MS)
        compose.inStep(position).onNodeWithTag(EditorTags.stepClipChoose(position)).performClick()
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val intent = activity.nextStartedActivityForResult.intent
        val file = File(context.cacheDir, "turned.mp4").apply { writeBytes(ByteArray(3000) { 2 }) }
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(position)) }

        compose.activityRule.scenario.recreate()

        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    // The conversion shows again on the new screen, with its Cancel; once done, the clip is attached and said once.
    @Test
    fun theConversionOutlivesTurningThePhoneAndIsSaidOnce() {
        chooseThenTurn(1)

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(1)) }
        assertTrue(exists(EditorTags.stepClipCancel(1)))

        gate.complete(Unit)

        val said = "Clip attached. It was converted on this phone to at most 1280 pixels and 6 MB, and its sound was removed."
        compose.awaitStatement(said)
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipConverting(1)) && exists(EditorTags.stepClipImage(1)) }
        assertEquals(MediaKind.VIDEO, stored().stepMedia(1)!!.kind)
        assertEquals(1, stored().media.size)
        compose.onAllNodes(hasText(said), useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(1, it.size) }
        compose.swipeAwayStatement()
        compose.waitForIdle()
        assertTrue(statements().isEmpty())
    }

    // Cancel on the new screen stops the conversion begun on the old one: nothing attached, nothing kept, nothing said.
    @Test
    fun theConversionCanBeCancelledAfterTurningThePhone() {
        chooseThenTurn(0)

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipCancel(0)) }
        compose.inStep(0).onNodeWithTag(EditorTags.stepClipCancel(0)).performScrollTo().performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipConverting(0)) }
        compose.waitUntil(PATIENCE_MS) { held().isEmpty() }
        assertTrue(stored().media.isEmpty())
        assertFalse(exists(EditorTags.stepClipImage(0)))
        assertTrue(statements().isEmpty())
    }
}
