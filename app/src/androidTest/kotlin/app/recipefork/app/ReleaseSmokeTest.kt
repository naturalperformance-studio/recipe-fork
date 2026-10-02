package app.recipefork.app

import android.Manifest
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeExport
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.importSafely
import app.recipefork.app.media.PhotoOutcome
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.camera.CameraXAccess
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.TimerChannels
import app.recipefork.app.timer.TimerKey
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.scaling.Rational
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The smoke test of the build the owner installs by hand (scripts/device-test-release.sh runs it against the minified
 * serverless release): every part R8 could break by removing what is reached reflectively — Room, kotlinx.serialization
 * with the recipe file's document, CameraX, the timers' service and receivers, Compose — is gone through once on a
 * real device. It runs against the debug build too, where it proves the test itself.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as RecipeForkApplication
    private val container = application.container
    private val store = PhotoStore(application.filesDir)
    private val otherBook = BookName.OfAccount("release-smoke")
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private var recipe: Recipe? = null

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).readBytes() }
    }

    @Before
    fun setUp() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        instrumentation.uiAutomation.grantRuntimePermission(application.packageName, Manifest.permission.CAMERA)
    }

    @After
    fun tearDown() = runBlocking {
        recipe?.let { container.timers.cancelRecipe(BookName.Pool, it.id); container.recipes.delete(it.id) }
        application.getSystemService(NotificationManager::class.java).cancelAll()
        application.deleteDatabase(otherBook.fileName)
        store.directory(otherBook).deleteRecursively()
        Unit
    }

    /** A JPEG of [width] x [height] in one colour, as a gallery would hand it. */
    private fun jpeg(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun photo(bytes: ByteArray, attachment: MediaAttachment): Media {
        val outcome = runBlocking { store.add(BookName.Pool) { ByteArrayInputStream(bytes) } }
        check(outcome is PhotoOutcome.Stored) { "$outcome" }
        return Media(outcome.mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    private fun awaitUntil(within: Duration, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + within.inWholeMilliseconds
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "not met within $within" }
            Thread.sleep(200)
        }
    }

    /** Waits until [text] is on screen; failing, says what is. */
    private fun awaitShown(text: String) = try {
        awaitUntil(30.seconds) { shown(text) != null }
    } catch (e: IllegalStateException) {
        throw AssertionError("“$text” not shown; on screen: ${onScreen()}", e)
    }

    /** Every node on screen, walked through accessibility as a reader's tools walk it. */
    private fun nodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            nodes += node
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(instrumentation.uiAutomation.rootInActiveWindow)
        return nodes
    }

    /** Every text and description on screen. */
    private fun onScreen(): List<String> =
        nodes().flatMap { listOfNotNull(it.text, it.contentDescription) }.map { it.toString() }

    /** The node on screen showing [text], or described by it. */
    private fun shown(text: String): AccessibilityNodeInfo? =
        nodes().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }

    private fun click(text: String) {
        var node: AccessibilityNodeInfo? = shown(text)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("nothing clickable holds “$text”", node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun borscht(): Recipe {
        val cover = photo(jpeg(1600, 1200, Color.RED), MediaAttachment.Cover)
        val stepPhoto = photo(jpeg(1200, 1600, Color.MAGENTA), MediaAttachment.ToStep(1))
        return Recipe.create(now, null, null, RecipeId.mint()).copy(
            // A title of its own each run, so that nothing an earlier run left in the book is taken for it.
            title = "Smoke borscht ${System.currentTimeMillis() % 100_000}",
            description = "Written by the release smoke test.",
            servings = 4,
            ingredients = listOf(
                Ingredient(0, null, null, "Beets", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500), null, null, null),
                Ingredient(1, null, null, "Salt", UnitType.TASTE_UNMEASURED, null, null, null, null, null),
            ),
            steps = listOf(
                Step(0, "Peel the beets.", listOf(IngredientReference(0, 9))),
                Step(1, "Simmer the beets with salt.", listOf(IngredientReference(0, 11), IngredientReference(1, 22)), Timer(40.minutes, "Simmer")),
            ),
            media = listOf(cover, stepPhoto),
        )
    }

    @Test
    fun theBuildStartsKeepsExportsImportsTakesAPictureAndRingsATimer() {
        // A recipe with a cover, a step photo, references and a timer, written to the book on disk...
        val written = borscht().also { recipe = it }
        runBlocking { container.recipes.save(written) }
        // ...and read back through a database opened anew on the same file.
        val reopened = BookDatabase.open(application, BookName.Pool)
        try {
            assertEquals(written, runBlocking { RecipeRepository(ActiveBook(BookName.Pool, reopened), application.filesDir).get(written.id) })
        } finally {
            reopened.close()
        }

        // The application starts, lists the recipe and opens its card; the camera takes a picture for it.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            // The serverless book opens on what is marked; the recipe is under what is written here.
            awaitShown(WRITTEN_HERE)
            click(WRITTEN_HERE)
            awaitShown(written.title)
            click(written.title)
            // The card: its servings, and its cover decoded from the book's media.
            awaitShown("Servings: 4")
            awaitShown("Cover photo of “${written.title}”")
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            // The camera takes a picture through CameraX, for the application's own screen.
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            takeAPicture(activity)
        } finally {
            scenario.close()
        }

        // Exported to a file, and the file imported into another book: the recipe arrives whole, photographs and all.
        val uri = runBlocking { RecipeExport.write(application, container.recipes, written.id) }!!
        val database = BookDatabase.open(application, otherBook)
        try {
            val other = RecipeRepository(ActiveBook(otherBook, database), application.filesDir)
            val outcome = runBlocking { RecipeImport(other, application.filesDir).importSafely(application, uri) }
            assertEquals(ImportOutcome.Added(written.id, written.title), outcome)
            val imported = runBlocking { other.get(written.id) }!!
            assertEquals(written.ingredients, imported.ingredients)
            assertEquals(written.steps, imported.steps)
            assertEquals(written.servings, imported.servings)
            assertEquals(written.media.map { it.attachment }, imported.media.map { it.attachment })
            imported.media.forEach { assertTrue(store.file(otherBook, it.mediaId).isFile) }
        } finally {
            database.close()
        }

        // A timer is started; it announces itself and rings until it is stopped.
        val manager = application.getSystemService(NotificationManager::class.java)
        val timer = runBlocking { container.timers.start(TimerKey(BookName.Pool, written.id, 1), "Simmer", written.title, 5.seconds, null) }
        awaitUntil(60.seconds) { manager.activeNotifications.any { it.notification.channelId == TimerChannels.ENDED } }
        awaitUntil(10.seconds) { timer.key in container.timerRinging.ringing.value }
        // The alarm sound the build ships (res/raw, kept through resource shrinking) plays on the alarm stream.
        val audio = application.getSystemService(AudioManager::class.java)
        awaitUntil(10.seconds) { audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM } }
        manager.activeNotifications.single { it.notification.channelId == TimerChannels.ENDED }
            .notification.actions.single().actionIntent.send()
        awaitUntil(10.seconds) { timer.key !in container.timerRinging.ringing.value }
        assertTrue(runBlocking { container.timers.all.first() }.none { it.key == timer.key && !it.announced })
    }

    /** Opens the back camera for [activity] through CameraX and takes a picture: a JPEG comes back. */
    private fun takeAPicture(activity: MainActivity) = runBlocking {
        val camera = withContext(Dispatchers.Main) { CameraXAccess.open(activity, activity) }
        assertNotNull("the device has a back camera", camera)
        try {
            val jpeg = withTimeout(60_000) { camera!!.takePicture() }.jpeg
            assertTrue(jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())
        } finally {
            withContext(Dispatchers.Main) { camera!!.close() }
        }
    }

    private companion object {
        /** The library's tab of the recipes written on this device. */
        const val WRITTEN_HERE = "Written here"
    }
}
