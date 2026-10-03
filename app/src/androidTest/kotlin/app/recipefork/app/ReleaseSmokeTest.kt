package app.recipefork.app

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The smoke test of the build the owner installs by hand (scripts/device-test-release.sh runs it against the minified
 * serverless release): every part R8 could break by removing what is reached reflectively — Room, kotlinx.serialization
 * with the recipe file's document, the camera's capture provider, the timers' service and receivers, Compose — is gone
 * through once on a real device. It runs against the debug build too, where it proves the test itself.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as RecipeForkApplication
    private val container = application.container
    private val store = PhotoStore(application.filesDir)
    private val otherBook = BookName.OfAccount("release-smoke")
    // Earlier than the device clock, by which the editor dates the cover it takes.
    private val now = Instant.now().minusSeconds(3600)
    private var recipe: Recipe? = null

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).readBytes() }
    }

    @Before
    fun setUp() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
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
            // The phone's camera application takes a new cover for it, cropped and stored without its metadata.
            takeACover(written)
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

    /**
     * A JPEG as a phone's camera writes one: [width] x [height] in one colour, tagged with place and device, its pixels
     * stored a quarter turned with EXIF saying so.
     */
    private fun cameraShot(width: Int, height: Int): ByteArray {
        val file = File(application.cacheDir, "smoke-shot.jpg")
        file.writeBytes(jpeg(width, height, Color.GREEN))
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "55/1,45/1,2100/100")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_MAKE, "PhoneMaker")
            setAttribute(ExifInterface.TAG_MODEL, "Phone 12 Pro")
            saveAttributes()
        }
        return file.readBytes().also { file.delete() }
    }

    /**
     * From the card, the editor's "Take a photo" launches the phone's camera application through the shrunk build's
     * capture provider. The device's camera application is there to answer; the test stands in for it, as its shutter
     * cannot be pressed from here, writing the shot through the address it is handed. The shot is cropped — Done — and
     * becomes the recipe's cover, holding no metadata, the shot's own file deleted.
     */
    private fun takeACover(written: Recipe) {
        // No CAMERA permission is declared: declared and not granted, it would make the platform refuse the camera.
        val requested = application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()
        assertFalse(Manifest.permission.CAMERA in requested)
        var launched: Intent? = null
        val camera = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != MediaStore.ACTION_IMAGE_CAPTURE) return null
                launched = intent
                @Suppress("DEPRECATION")
                val output = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
                application.contentResolver.openOutputStream(output)!!.use { it.write(cameraShot(1200, 1600)) }
                return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
        }
        instrumentation.addMonitor(camera)
        try {
            click(EDIT)
            awaitShown(TAKE_A_PHOTO)
            click(TAKE_A_PHOTO)
            // The crop, in a window of its own, shows the shot under its frame: its Done is the one on screen.
            awaitShown(CROP_FRAME)
            val intent = launched!!
            assertNotNull("a camera application answers", application.packageManager.resolveActivity(Intent(intent.action), 0))
            @Suppress("DEPRECATION")
            val output = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
            assertEquals(application.packageName + ".capture", output.authority)
            assertEquals(output, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            click(CROP_DONE)
            fun coverOf(recipe: Recipe?) = recipe?.media?.firstOrNull { it.attachment == MediaAttachment.Cover }
            val before = coverOf(written)!!.mediaId
            awaitUntil(30.seconds) { coverOf(runBlocking { container.recipes.get(written.id) })?.mediaId.let { it != null && it != before } }
            val cover = coverOf(runBlocking { container.recipes.get(written.id) })!!
            val exif = ExifInterface(store.file(BookName.Pool, cover.mediaId).path)
            listOf(ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL).forEach { assertNull(it, exif.getAttribute(it)) }
            assertNotEquals(ExifInterface.ORIENTATION_ROTATE_90, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            awaitUntil(10.seconds) { File(application.cacheDir, "capture").list().orEmpty().isEmpty() }
        } finally {
            instrumentation.removeMonitor(camera)
        }
    }

    private companion object {
        /** The library's tab of the recipes written on this device. */
        const val WRITTEN_HERE = "Written here"
        const val EDIT = "Edit"
        const val TAKE_A_PHOTO = "Take a photo"
        const val CROP_FRAME = "Crop frame: drag the photo to move it, pinch to zoom"
        const val CROP_DONE = "Done"
    }
}
