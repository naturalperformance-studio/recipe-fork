package app.recipefork.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeExport
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.importSafely
import app.recipefork.app.kitchen.Kitchen
import app.recipefork.app.media.ClipOutcome
import app.recipefork.app.media.PhotoOutcome
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestClips
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.TimerChannels
import app.recipefork.app.timer.TimerKey
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
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
 * with the recipe file's document, the camera's capture provider, the timers' service and receivers, Compose, Media3
 * Transformer converting a clip — is gone through once on a real device. It runs against the debug build too, where it
 * proves the test itself.
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
    // What the kitchen held before a case changed it, given back after.
    private var kitchenBefore: Set<Long>? = null

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).readBytes() }
    }

    @Before
    fun setUp() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        // The keyboard is a window of its own, which the test reads among the screen's windows.
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    @After
    fun tearDown() = runBlocking {
        recipe?.let { container.timers.cancelRecipe(BookName.Pool, it.id); container.recipes.delete(it.id) }
        application.getSystemService(NotificationManager::class.java).cancelAll()
        application.deleteDatabase(otherBook.fileName)
        store.directory(otherBook).deleteRecursively()
        kitchenBefore?.let { before ->
            val now = container.kitchen.held.value
            container.kitchen.hold(before - now, has = true)
            container.kitchen.hold(now - before, has = false)
        }
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

    /**
     * Waits until [condition] holds. A system dialog saying another application isn't responding — the emulator's own
     * launcher, stalled — is answered "Wait" first, and the wait begins anew, at most [ANSWERED_DIALOGS] times: that
     * dialog covers the application without being its fault. The same dialog for this application fails the test.
     */
    private fun awaitUntil(within: Duration, condition: () -> Boolean) {
        var deadline = System.currentTimeMillis() + within.inWholeMilliseconds
        var answered = 0
        while (true) {
            if (answerAnotherNotResponding() && answered < ANSWERED_DIALOGS) {
                answered++
                deadline = System.currentTimeMillis() + within.inWholeMilliseconds
            }
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "not met within $within" }
            Thread.sleep(200)
        }
    }

    /** The application's name, as the system's dialogs name it. */
    private val label = application.applicationInfo.loadLabel(application.packageManager).toString()

    /**
     * Answers "Wait" to the system's dialog saying another application isn't responding, if one is on screen, and says
     * whether it did; fails the test where the dialog names this application.
     */
    private fun answerAnotherNotResponding(): Boolean {
        val dialogs = instrumentation.uiAutomation.windows.mapNotNull { it.root }.filter { it.refresh() && it.packageName == "android" }
        for (dialog in dialogs) {
            val title = dialog.findAccessibilityNodeInfosByText(NOT_RESPONDING).firstOrNull()?.text?.toString() ?: continue
            if (label in title || application.packageName in title) throw AssertionError("the application itself is not responding: “$title”")
            val wait = dialog.findAccessibilityNodeInfosByText(WAIT).firstOrNull { it.text?.toString() == WAIT } ?: continue
            if (generateSequence(wait) { it.parent }.firstOrNull { it.isClickable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
    }

    /** Waits until [text] is on screen; failing, says what is. */
    private fun awaitShown(text: String) = try {
        awaitUntil(30.seconds) { shown(text) != null }
    } catch (e: IllegalStateException) {
        throw AssertionError("“$text” not shown; on screen: ${onScreen()}", e)
    }

    /**
     * Every node on screen, walked through accessibility as a reader's tools walk it. Each is read anew from the screen,
     * never from the test's cache of nodes: Compose tells a change only to a reader it counts as enabled, and the test's
     * automation is none, so a cached node keeps what it showed when first read — a tab chosen since, a place scrolled
     * from — and one gone from the screen is left out.
     */
    private fun nodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || !node.refresh()) return
            nodes += node
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(instrumentation.uiAutomation.rootInActiveWindow)
        return nodes
    }

    /** Every text and description on screen. */
    private fun onScreen(): List<String> =
        nodes().flatMap { listOfNotNull(it.text, it.contentDescription) }.map { it.toString() }

    /** What holds this node, read anew from the screen as [nodes] reads it. */
    private fun AccessibilityNodeInfo.holder(): AccessibilityNodeInfo? = parent?.takeIf { it.refresh() }

    /** The node on screen showing [text], or described by it. */
    private fun shown(text: String): AccessibilityNodeInfo? =
        nodes().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }

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
                // A part of the beets: kept by the book and carried by the file through R8 too.
                Step(
                    1, "Simmer the beets with salt.",
                    listOf(IngredientReference(0, 11, IngredientShare(Rational.of(250), QuantityUnit.GRAM)), IngredientReference(1, 22)),
                    Timer(40.minutes, "Simmer"),
                ),
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
            // The book opens on the view last left; the recipe is under what is written here.
            choose(WRITTEN_HERE)
            awaitShown(written.title)
            tap(written.title)
            // The card, on its View tab — it opens on the tab last chosen: its servings, and its cover decoded from
            // the book's media.
            choose(VIEW)
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

    // The clip converter (MEDIA-004) as R8 leaves it, reached as the editor reaches it: Media3 Transformer reading a picked clip by its content address,
    // its codecs and its own muxer, in the minified build.
    @Test
    fun theBuildConvertsAClip() = runBlocking {
        val source = TestClips.write(File(TestClips.directory(application), "release-smoke.mp4"), 640, 360, seconds = 2, location = 1f to 2f)
        try {
            // The converter as the application wires it for the editor's steps.
            val clips = container.clipStore(application)
            val outcome = clips.add(otherBook, TestClips.uri(application, source))
            check(outcome is ClipOutcome.Stored) { "$outcome" }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(outcome.file.path)
                assertEquals(1, extractor.trackCount)
                assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME))
            } finally {
                extractor.release()
            }
        } finally {
            source.delete()
        }
    }

    /**
     * From the card, the editor's cover opens its source sheet, whose "Take a photo" launches the phone's camera application
     * through the shrunk build's capture provider. The device's camera application is there to answer; the test stands in for it, as its shutter
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
            tap(EDIT)
            // The cover opens its source sheet, the camera on it (FLOW-026).
            awaitShown(CHANGE_COVER)
            tap(CHANGE_COVER)
            awaitShown(TAKE_A_PHOTO)
            tap(TAKE_A_PHOTO)
            // The crop, in a window of its own, shows the shot under its frame: its Done is the one on screen.
            awaitShown(CROP_FRAME)
            val intent = launched!!
            assertNotNull("a camera application answers", application.packageManager.resolveActivity(Intent(intent.action), 0))
            @Suppress("DEPRECATION")
            val output = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
            assertEquals(application.packageName + ".capture", output.authority)
            assertEquals(output, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            tap(CROP_DONE)
            fun coverOf(recipe: Recipe?) = recipe?.media?.firstOrNull { it.attachment == MediaAttachment.Cover }
            val before = coverOf(written)!!.mediaId
            awaitUntil(30.seconds) { coverOf(runBlocking { container.recipes.get(written.id) })?.mediaId.let { it != null && it != before } }
            val cover = coverOf(runBlocking { container.recipes.get(written.id) })!!
            val exif = ExifInterface(store.file(BookName.Pool, cover.mediaId).path)
            listOf(ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL).forEach { assertNull(it, exif.getAttribute(it)) }
            // Stored upright: no turn left for a viewer to apply.
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            assertTrue("orientation $orientation", orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL)
            awaitUntil(10.seconds) { File(application.cacheDir, "capture").list().orEmpty().isEmpty() }
        } finally {
            instrumentation.removeMonitor(camera)
        }
    }

    /**
     * A recipe that is all text: two ingredients, two steps, a named timer on the first, and the item of equipment
     * [needs], from the equipment dictionary.
     */
    private fun soup(needs: Long): Recipe = Recipe.create(now, null, null, RecipeId.mint()).copy(
        title = "Smoke soup ${System.currentTimeMillis() % 100_000}",
        servings = 2,
        equipment = listOf(EquipmentItem.entry(needs)),
        ingredients = listOf(
            Ingredient(0, null, null, "Onions", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200), null, null, null),
            Ingredient(1, null, null, "Water", UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(800), null, null, null),
        ),
        steps = listOf(
            Step(0, "Simmer the onions in the water.", listOf(IngredientReference(0, 11), IngredientReference(1, 25)), Timer(30.minutes, "Simmer")),
            Step(1, "Season and serve."),
        ),
    )

    /**
     * The device's kitchen made to lack an item of the equipment dictionary while it has another, so that a recipe
     * needing the first says so; the kitchen as it was is given back after the case. Returns the item lacked.
     */
    private fun lackedEquipment(): Long {
        val kitchen = container.kitchen
        kitchenBefore = kitchen.held.value
        val items = runBlocking { container.dictionaries.equipment() }.filter { it.parentId != null && Kitchen.counts(it) }
        check(items.size >= 2) { "the equipment dictionary lists ${items.size} items" }
        val (had, lacked) = items.take(2).map { it.id }
        kitchen.hold(listOf(had), has = true)
        kitchen.hold(listOf(lacked), has = false)
        return lacked
    }

    /**
     * The field on screen that takes text and is labelled [label] — the label a text of its own, read after the field
     * it names; the last such where several are.
     */
    private fun field(label: String): AccessibilityNodeInfo? {
        val all = nodes()
        val at = all.indexOfLast { node -> listOfNotNull(node.text, node.contentDescription).any { it.toString() == label } }
        if (at < 0) return null
        return all.subList(0, at + 1).lastOrNull { it.isEditable }
    }

    /**
     * Waits until [find] finds a node on screen, scrolling the screen's largest scrolling container meanwhile — on, and
     * back once it goes no further — as a reader scrolls to what they look for; failing, says what is on screen. What
     * acts on the node finds it anew: a screen still settling may take it away a moment, and bring it back as another.
     */
    private fun reveal(what: String, find: () -> AccessibilityNodeInfo?) {
        var forward = true
        try {
            awaitUntil(30.seconds) {
                val found = find()
                if (found == null || !found.isVisibleToUser) {
                    // What scrolls up and down: never the card's tabs' pager, which a forward scroll would turn.
                    val scrolled = nodes().filter { it.isScrollable && it.actionList.none { action -> action.id in SIDEWAYS } }
                        .maxByOrNull { node -> Rect().also(node::getBoundsInScreen).let { it.width() * it.height() } }
                        ?.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    if (scrolled == false) forward = !forward
                }
                found != null && found.isVisibleToUser
            }
        } catch (e: IllegalStateException) {
            val fields = nodes().filter { it.isEditable }.map { "field “${it.text}” hint “${it.hintText}” described “${it.contentDescription}”" }
            throw AssertionError("“$what” not found; on screen: ${onScreen()} and $fields", e)
        }
    }

    /** What holds [text] and answers a click, if anything on screen does. */
    private fun clickable(text: String): AccessibilityNodeInfo? =
        generateSequence(shown(text)) { it.holder() }.firstOrNull { it.isClickable }

    /**
     * Performs [action] on what [find] finds on screen, once it takes it: a screen coming in may show a node before it
     * answers, and one still settling may refuse it — the node moved on as it was found — so each try finds it anew.
     * Failing, says where what was last found stands, if still on screen.
     */
    private fun perform(what: String, action: Int, arguments: Bundle? = null, find: () -> AccessibilityNodeInfo?) {
        var last: AccessibilityNodeInfo? = null
        try {
            awaitUntil(30.seconds) { find()?.also { last = it }?.performAction(action, arguments) == true }
        } catch (e: IllegalStateException) {
            val node = last
            val now = when {
                node == null -> "nothing on screen takes it"
                node.refresh() -> "refused, visible ${node.isVisibleToUser}, enabled ${node.isEnabled}, at ${Rect().also(node::getBoundsInScreen)}"
                else -> "refused, gone from the screen"
            }
            throw AssertionError("$what: $now; on screen: ${onScreen()}", e)
        }
    }

    /** Clicks what holds [text] once something clickable does and takes the click. */
    private fun tap(text: String) = perform("a click on “$text”", AccessibilityNodeInfo.ACTION_CLICK) { clickable(text) }

    /** Whether the tab showing [text] is the one chosen. */
    private fun selected(text: String): Boolean = generateSequence(shown(text)) { it.holder() }.any { it.isSelected }

    /**
     * Brings forward the tab showing [text]. A screen of tabs opens on the one last chosen on the device — kept there
     * by an earlier case or run — and the tab already chosen answers no click: it is tapped only when it is not.
     */
    private fun choose(text: String) {
        try {
            awaitUntil(30.seconds) { selected(text) || clickable(text) != null }
            if (!selected(text)) tap(text)
            awaitUntil(30.seconds) { selected(text) }
        } catch (e: IllegalStateException) {
            throw AssertionError("the tab “$text” is not chosen; on screen: ${onScreen()}", e)
        }
    }

    /** Clicks what shows [text], scrolled to first. */
    private fun clickRevealed(text: String) {
        reveal(text) { shown(text) }
        tap(text)
    }

    /**
     * Writes [text] into the field labelled [label], scrolled to first, as the author does: a tap on the field brings the
     * keyboard up with the field in sight above it, and the text goes in as a screen reader's text entry puts it.
     */
    private fun type(label: String, text: String) {
        reveal(label) { field(label) }
        perform("a tap on “$label”", AccessibilityNodeInfo.ACTION_CLICK) { field(label) }
        awaitKeyboard(label) { field(label) }
        enter(label, text) { field(label) }
    }

    /**
     * Writes [text] into the field that has the focus, as [what] gives it — a row just added — once the keyboard is up
     * with the field in sight above it.
     */
    private fun typeFocused(what: String, text: String) {
        awaitKeyboard(what) { focusedField() }
        enter(what, text) { focusedField() }
    }

    /** The field on screen that takes text and has the focus. */
    private fun focusedField(): AccessibilityNodeInfo? = nodes().firstOrNull { it.isEditable && it.isFocused }

    private fun enter(what: String, text: String, find: () -> AccessibilityNodeInfo?) {
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        perform("“$what” taking “$text”", AccessibilityNodeInfo.ACTION_SET_TEXT, arguments, find)
    }

    /** The keyboard's window, while the keyboard is up. */
    private fun keyboard(): AccessibilityWindowInfo? =
        instrumentation.uiAutomation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    /**
     * Waits until the keyboard is up and the field [find] finds has the focus and stands in sight above the keyboard,
     * none of it behind; failing, says where both are.
     */
    private fun awaitKeyboard(what: String, find: () -> AccessibilityNodeInfo?) {
        var seen = "no field"
        try {
            awaitUntil(30.seconds) {
                val field = find()
                val keys = keyboard()?.let { window -> Rect().also(window::getBoundsInScreen) }
                val at = field?.let { node -> Rect().also(node::getBoundsInScreen) }
                seen = "the field at $at, focused ${field?.isFocused}, visible ${field?.isVisibleToUser}; the keyboard at $keys"
                field != null && at != null && field.isFocused && field.isVisibleToUser && keys != null && !keys.isEmpty &&
                    !at.isEmpty && at.bottom <= keys.top
            }
        } catch (e: IllegalStateException) {
            throw AssertionError("“$what” is not typed in with the keyboard up: $seen; on screen: ${onScreen()}", e)
        }
    }

    /** What holds the text on screen that has [part] in it and answers a click: a row read as text, as one line. */
    private fun lineWith(part: String): AccessibilityNodeInfo? =
        generateSequence(nodes().firstOrNull { node -> listOfNotNull(node.text, node.contentDescription).any { part in it } }) { it.holder() }
            .firstOrNull { it.isClickable }

    /** Opens the row whose text has [part] in it, scrolled to first: its sheet, which shows [sheet] and the editor does not. */
    private fun openRow(part: String, sheet: String) {
        reveal(part) { lineWith(part) }
        perform("a click on the row with “$part”", AccessibilityNodeInfo.ACTION_CLICK) { lineWith(part) }
        awaitShown(sheet)
    }

    /** Closes the sheet showing [sheet] by its Done, back to the editor, the keyboard gone with it. */
    private fun closeSheet(sheet: String) {
        tap(DONE)
        try {
            awaitUntil(30.seconds) { shown(sheet) == null && keyboard() == null && shown(DONE) != null }
        } catch (e: IllegalStateException) {
            throw AssertionError("the sheet showing “$sheet” did not close; on screen: ${onScreen()}", e)
        }
    }

    /** The recipe [written] as the book holds it now. */
    private fun stored(written: Recipe): Recipe? = runBlocking { container.recipes.get(written.id) }

    /**
     * The pre-launch crash sweep of the build Play receives, the way a stranger first goes through it: the application
     * starts and opens a recipe's card, whose ⋮ opens the system's print dialog on it, left by Back; a timer is started
     * from it; the editor opens on the recipe, its rows read as
     * text, and in the sheets they open — each typed in with the keyboard up — a note, a group, a step's note and timer
     * and a new step are written and saved, which closes the editor; the item of equipment the kitchen lacks opens My
     * kitchen and Back returns to the card; the settings are gone through; the recipe is deleted and brought back with
     * Undo, exported to a file and the file opened back into the book. Nothing crashes, and each change is in the book.
     *
     * Export and the opening of a file belong to the serverless build (FLOW-063, FLOW-064): the standard one, which
     * offers neither, writes and reads the file through the same code the menu and the opened file reach.
     */
    @Test
    fun theBuildGoesThroughEveryMainScreenWithoutACrash() {
        val written = soup(needs = lackedEquipment()).also { recipe = it }
        runBlocking { container.recipes.save(written) }
        // Started as the launcher starts it, in a task of its own, so that a file opened later from outside comes to
        // that task, as on the phone.
        application.startActivity(
            application.packageManager.getLaunchIntentForPackage(application.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            // The book, and the recipe's card.
            // The book opens where it was left: on the recipes written here, or on the marked ones; the card on the
            // tab last chosen.
            choose(WRITTEN_HERE)
            tap(written.title)
            choose(VIEW)
            awaitShown("Servings: 2")

            // The recipe printed from the card's ⋮: the system's print dialog opens on it, and Back leaves it.
            printFromTheCard()

            // A timer started from the card's Cooking tab runs.
            choose(COOKING)
            clickRevealed("Simmer")
            awaitUntil(30.seconds) { runBlocking { container.timers.all.first() }.any { it.key.recipeId == written.id.value } }

            // The editor, from the View tab: the rows read as text, each opening in its sheet on a tap, where it is
            // written with the keyboard up; closed by Done. A note, a group, a step's note and timer, and a step added.
            choose(VIEW)
            tap(EDIT)
            awaitShown(DONE)
            // In the order the editor lays them out: the first ingredient's note...
            openRow("Onions", EDIT_INGREDIENT)
            type(NOTE, "thinly sliced")
            closeSheet(EDIT_INGREDIENT)
            // ...a group under the list, its sheet opened with its name taking the keyboard...
            clickRevealed(ADD_GROUP)
            awaitShown(EDIT_GROUP)
            typeFocused(GROUP, "Base")
            closeSheet(EDIT_GROUP)
            // ...the second step's note and a timer of five minutes...
            openRow("Season and serve.", AUTHORS_NOTE)
            awaitShown(STEP_2)
            type(AUTHORS_NOTE, "Taste first.")
            clickRevealed(ADD_TIMER)
            type(MINUTES, "5")
            closeSheet(AUTHORS_NOTE)
            // ...and a step added at the end, its sheet opened with its text taking the keyboard.
            clickRevealed(ADD_STEP)
            awaitShown(AUTHORS_NOTE)
            awaitShown(STEP_3)
            typeFocused(STEP_3, "Serve hot.")
            closeSheet(AUTHORS_NOTE)
            // Done saves and closes the editor, back to the recipe's card.
            tap(DONE)
            try {
                awaitUntil(30.seconds) { shown(DONE) == null && shown("Servings: 2") != null }
            } catch (e: IllegalStateException) {
                throw AssertionError("the editor did not close to the card; on screen: ${onScreen()}", e)
            }
            fun edited() = stored(written)?.let { kept ->
                val steps = kept.steps.sortedBy { it.position }
                listOf(kept.ingredientGroups.map { it.name }, kept.ingredients.map { it.note }, steps.map { it.timers.size }, steps.map { it.note }, steps.map { it.text }.drop(2))
            }
            val expected = listOf(listOf("Base"), listOf("thinly sliced", null), listOf(1, 1, 0), listOf(null, "Taste first.", null), listOf("Serve hot."))
            try {
                awaitUntil(30.seconds) { edited() == expected }
            } catch (e: IllegalStateException) {
                throw AssertionError("the book holds ${edited()}, not $expected; on screen: ${onScreen()}", e)
            }

            // The item of equipment the kitchen lacks says so on the View tab; a tap opens My kitchen at it, and its
            // Back returns to the card.
            clickRevealed(NOT_IN_KITCHEN)
            try {
                awaitUntil(30.seconds) { shown(NEEDED_FOR_THE_RECIPE) != null && shown(KITCHEN) != null && shown("Servings: 2") == null }
            } catch (e: IllegalStateException) {
                throw AssertionError("My kitchen did not open at the item; on screen: ${onScreen()}", e)
            }
            tap(BACK)
            try {
                awaitUntil(30.seconds) { shown(KITCHEN) == null && shown("Servings: 2") != null }
            } catch (e: IllegalStateException) {
                throw AssertionError("My kitchen did not return to the card; on screen: ${onScreen()}", e)
            }
            awaitShown(NOT_IN_KITCHEN)
            // Back from the card to the book.
            tap(BACK)
            awaitShown(WRITTEN_HERE)
            awaitShown(written.title)

            // The settings: the theme and the language changed and set back, the usual servings' dialog left.
            throughTheSettings()
            awaitShown(written.title)

            // The shopping list, opened from the book's bar: read from its store, its "Add item" shown; its Back returns
            // to the book.
            tap(SHOPPING_LIST)
            awaitShown(ADD_ITEM)
            tap(BACK)
            try {
                awaitUntil(30.seconds) { shown(ADD_ITEM) == null && shown(WRITTEN_HERE) != null }
            } catch (e: IllegalStateException) {
                throw AssertionError("the shopping list did not return to the book; on screen: ${onScreen()}", e)
            }
            awaitShown(written.title)

            // Deleted, and brought back with Undo.
            tap(actionsFor(written))
            awaitShown(DELETE)
            tap(DELETE)
            awaitShown(deleted(written))
            awaitUntil(30.seconds) { stored(written) == null }
            tap(UNDO)
            awaitUntil(30.seconds) { stored(written) != null }
            awaitShown(written.title)
            // Exported to a file and the file opened back into the book, where it is listed as a recipe found.
            exportAndImport(written)
            assertTrue("the application is in front, as it was left", activities(Stage.RESUMED).any { it is MainActivity })
        } finally {
            finishTheApplication()
        }
    }

    /**
     * The interface's words the settings' walk reads, in English and in Russian, written out: the shrunk build's
     * resources are reached by the application alone.
     */
    private class Words(val language: String, val cancel: String, val device: String, val usual: String, val set: String, val back: String, val book: String)

    private val words = mapOf(
        "en" to Words("Language", "Cancel", AS_ON_THE_DEVICE, "I usually cook for…", "Set", "Back", "My recipe book"),
        "ru" to Words("Язык", "Отмена", "Как на устройстве", "Обычно готовлю на…", "Задать", "Назад", "Моя книга рецептов"),
    )

    /** Of the [choices] an open choice dialog offers, the one marked chosen. */
    private fun chosen(choices: List<String>): String = choices.single { choice ->
        generateSequence(shown(choice)) { it.holder() }.any { it.isChecked || it.isSelected }
    }

    /**
     * The settings opened from the book's bar: a theme other than the one in force chosen in the theme's dialog, and
     * the one in force chosen back; the language switched in its dialog — the application shown again in it — and
     * switched back; the usual servings' dialog opened and left by back; and back to the book, the application still
     * in front. The walk starts in English, as the emulator's: the device's language, or English chosen.
     */
    private fun throughTheSettings() {
        tap(SETTINGS)
        awaitShown(THEME)

        tap(THEME)
        awaitShown(THEME_CANCEL)
        val theme = chosen(THEMES)
        val otherTheme = if (theme == DARK) LIGHT else DARK
        tap(otherTheme)
        awaitUntil(30.seconds) { shown(THEME_CANCEL) == null }
        tap(THEME)
        awaitShown(THEME_CANCEL)
        assertEquals(otherTheme, chosen(THEMES))
        tap(theme)
        awaitUntil(30.seconds) { shown(THEME_CANCEL) == null }

        tap(LANGUAGE)
        awaitShown(LANGUAGE_CANCEL)
        // English, chosen or the device's.
        val language = chosen(listOf(ENGLISH, AS_ON_THE_DEVICE))
        val before = words.getValue("en")
        val after = words.getValue("ru")
        tap(RUSSIAN)
        // The application shows itself again in the language taken, the settings still open.
        awaitShown(after.language)
        tap(after.language)
        awaitShown(after.cancel)
        tap(if (language == AS_ON_THE_DEVICE) after.device else language)
        awaitShown(before.language)

        tap(before.usual)
        awaitShown(before.set)
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        awaitUntil(30.seconds) { shown(before.set) == null }

        tap(before.back)
        awaitShown(before.book)
        assertTrue("the application is in front", activities(Stage.RESUMED).any { it is MainActivity })
    }

    /** The root of the system's print dialog, while one of its windows is on screen. */
    private fun printDialog(): AccessibilityNodeInfo? =
        instrumentation.uiAutomation.windows.mapNotNull { it.root }.firstOrNull { it.refresh() && it.packageName == PRINT_SPOOLER }

    /**
     * The card's ⋮ → Print hands the recipe to the system's print service (FLOW-075 (1)): its dialog opens — the pages
     * laid out and drawn by this build for its preview — and Back leaves it, the job cancelled, back on the card.
     */
    private fun printFromTheCard() {
        tap(MORE_ACTIONS)
        awaitShown(PRINT)
        tap(PRINT)
        // The dialog's preview counts the pages the application's adapter laid out: "Page 1 of 1".
        try {
            awaitUntil(60.seconds) { printDialog() != null && onScreen().any { PAGES_LAID_OUT.matches(it) } }
        } catch (e: IllegalStateException) {
            throw AssertionError("the system's print dialog did not open on the laid-out pages; on screen: ${onScreen()}", e)
        }
        Log.i("ReleaseSmokeTest", "the print dialog shows: ${onScreen()}")
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        try {
            awaitUntil(30.seconds) { printDialog() == null && shown("Servings: 2") != null }
        } catch (e: IllegalStateException) {
            throw AssertionError("Back did not leave the print dialog for the card; on screen: ${onScreen()}", e)
        }
    }

    /** The application's activities in [stage]. */
    private fun activities(stage: Stage): List<Activity> {
        var found = emptyList<Activity>()
        instrumentation.runOnMainSync { found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).toList() }
        return found
    }

    /** Finishes every activity of the application still alive, and waits until each is gone. */
    private fun finishTheApplication() {
        val alive = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED)
        instrumentation.runOnMainSync { alive.forEach { stage -> ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).forEach(Activity::finish) } }
        awaitUntil(30.seconds) { alive.all { activities(it).isEmpty() } }
    }

    private fun actionsFor(recipe: Recipe) = "Actions for “${recipe.title}”"

    /** The book's notice of [recipe] deleted, which names it. */
    private fun deleted(recipe: Recipe) = "“${recipe.title}” deleted"

    /**
     * Exports [written] and opens the file back into the book: in the serverless build from the entry's menu — the
     * system's share sheet answered by the test, which takes the file it is handed — and then as another application
     * opens a file; in the standard build through the code those reach. The recipe is in the book either way.
     */
    private fun exportAndImport(written: Recipe) {
        if (!BuildConfig.SERVERLESS) {
            val uri = runBlocking { RecipeExport.write(application, container.recipes, written.id) }!!
            val database = BookDatabase.open(application, otherBook)
            try {
                val other = RecipeRepository(ActiveBook(otherBook, database), application.filesDir)
                assertEquals(ImportOutcome.Added(written.id, written.title), runBlocking { RecipeImport(other, application.filesDir).importSafely(application, uri) })
            } finally {
                database.close()
            }
            return
        }
        var shared: Uri? = null
        val sheet = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                @Suppress("DEPRECATION")
                val send = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                @Suppress("DEPRECATION")
                shared = send.getParcelableExtra(Intent.EXTRA_STREAM)
                return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
        }
        instrumentation.addMonitor(sheet)
        try {
            tap(actionsFor(written))
            awaitShown(EXPORT)
            tap(EXPORT)
            awaitUntil(30.seconds) { shared != null }
        } finally {
            instrumentation.removeMonitor(sheet)
        }
        // The recipe leaves the book, so that the file brings it back.
        tap(actionsFor(written))
        awaitShown(DELETE)
        tap(DELETE)
        awaitShown(deleted(written))
        awaitUntil(30.seconds) { stored(written) == null }
        val open = Intent(Intent.ACTION_VIEW)
            .setDataAndType(shared, RecipeFile.MIME_TYPE)
            .setPackage(application.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        application.startActivity(open)
        val added = "“${written.title}” is in your recipe book now"
        val held = "“${written.title}” is already in your recipe book, so nothing was added"
        awaitUntil(30.seconds) { shown(added) != null || shown(held) != null }
        awaitUntil(30.seconds) { stored(written) != null }
        awaitShown(written.title)
    }

    private companion object {
        /** The system's dialog of an application not responding, in the emulator's English, and its button that waits. */
        const val NOT_RESPONDING = "isn't responding"
        const val WAIT = "Wait"
        /** How many such dialogs one wait answers, beginning anew, before it no longer waits longer for them. */
        const val ANSWERED_DIALOGS = 5
        /** The library's tab of the recipes written on this device. */
        const val WRITTEN_HERE = "Written here"
        const val EDIT = "Edit"
        /** The card's two tabs. */
        const val VIEW = "View"
        const val COOKING = "Cooking"
        /** The accessibility actions of something that scrolls sideways. */
        val SIDEWAYS = listOf(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT,
        ).map { it.id }
        /** The editor's set cover, a tap on which opens the sheet of the camera, the gallery and its removal. */
        const val CHANGE_COVER = "Replace or remove the cover photo"
        /** The sheet's item that opens the phone's camera application. */
        const val TAKE_A_PHOTO = "Take a photo"
        const val CROP_FRAME = "Crop frame: drag the photo to move it, pinch to zoom"
        const val CROP_DONE = "Done"
        const val BACK = "Back"
        const val DONE = "Done"
        const val ADD_GROUP = "Add group"
        const val ADD_STEP = "Add step"
        /** The titles of an ingredient row's sheet and of a group heading's. */
        const val EDIT_INGREDIENT = "Edit ingredient"
        const val EDIT_GROUP = "Edit group"
        /** The label of a group heading's name field, and of an ingredient's note field. */
        const val GROUP = "Group"
        const val NOTE = "How to prepare"
        /** A step's sheet: its title, the label of the author's note, which only the sheet shows, and its timers. */
        const val STEP_2 = "Step 2"
        const val STEP_3 = "Step 3"
        const val AUTHORS_NOTE = "Author's note"
        const val ADD_TIMER = "Add timer"
        const val MINUTES = "Minutes"
        /** Under an item of equipment the kitchen lacks, on the card; My kitchen, and its row of the item. */
        const val NOT_IN_KITCHEN = "Not in your kitchen"
        const val KITCHEN = "My kitchen"
        const val NEEDED_FOR_THE_RECIPE = "Needed for the recipe"
        const val EXPORT = "Export"
        /** The book's bar action that opens the settings, and the settings' theme and language with their dialogs. */
        const val SETTINGS = "Settings"
        const val THEME = "Theme"
        const val THEME_CANCEL = "Cancel"
        const val LIGHT = "Light"
        const val DARK = "Dark"
        val THEMES = listOf(LIGHT, DARK, AS_ON_THE_DEVICE)
        const val LANGUAGE = "Language"
        const val LANGUAGE_CANCEL = "Cancel"
        const val ENGLISH = "English"
        const val RUSSIAN = "Русский"
        const val AS_ON_THE_DEVICE = "As on the device"
        const val DELETE = "Delete"
        const val UNDO = "Undo"
        /** The book's bar action that opens the shopping list, and the list's floating action. */
        const val SHOPPING_LIST = "Shopping list"
        const val ADD_ITEM = "Add item"
        /** The card's ⋮, its item that prints the recipe, and the package of the system's print dialog. */
        const val MORE_ACTIONS = "More actions"
        const val PRINT = "Print"
        const val PRINT_SPOOLER = "com.android.printspooler"
        /** The print dialog's count of the pages its preview holds, in the emulator's English. */
        val PAGES_LAID_OUT = Regex("Page 1 of [1-9][0-9]*")
    }
}
