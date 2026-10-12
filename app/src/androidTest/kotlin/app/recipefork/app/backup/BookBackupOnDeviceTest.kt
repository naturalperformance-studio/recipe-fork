package app.recipefork.app.backup

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.StepMediaItem
import app.recipefork.app.card.StepMediaOnCard
import app.recipefork.app.card.StepsList
import app.recipefork.app.card.animationsOff
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.media.ClipOutcome
import app.recipefork.app.media.PhotoOutcome
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestClips
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.RecipeForkApplication
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * FLOW-073 on a real device, by its real database, codecs and content resolver: a book of an own recipe with photos
 * and a converted clip, a found one with a photo, and a fork of the own one is saved to a file — through a content
 * address, as the system's picker hands one — and the book is then lost as a reset phone loses it: its database and
 * media deleted. Restored from the file into that empty book, it comes back whole: the same recipes, states, parent and
 * media bytes, and My kitchen; the clip plays on the card's own player. How long the save and the restore took, and how
 * big the file was, is written to the log under [TAG].
 *
 * The backup is assembled as the application's container assembles it, over a book of its own, so that the device's
 * own book is neither read nor changed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BookBackupOnDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val name = BookName.OfAccount("backup-on-device")
    private val store = PhotoStore(context.filesDir)
    // In whole milliseconds, as the book keeps times.
    private val now = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.MILLIS)
    private val kitchenHeld = setOf(10001L, 10203L)
    private val kitchen = HeldKitchen(kitchenHeld)
    private val book = MutableStateFlow(ActiveBook.Open(name, BookDatabase.open(context, name)))
    private val active = ActiveBook(book)
    private val recipes = RecipeRepository(active, context.filesDir)

    /** Where the book is saved: a file of the application's cache, reached by its content address. */
    private val saved = File(context.cacheDir, "exports/backup-on-device/Recipe book.recipefork-book")
    private val source = File(TestClips.directory(context), "backup-on-device.mp4")

    private val backup = BookBackup(
        active,
        kitchen,
        ResolverDocuments(context),
        BookFileWriter(context),
        BookFileReader(context),
        BookRestore(context.filesDir, kitchen),
    )

    @After
    fun delete() {
        book.value.database.close()
        context.deleteDatabase(name.fileName)
        store.directory(name).deleteRecursively()
        saved.parentFile?.deleteRecursively()
        source.delete()
    }

    private fun jpeg(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun photo(color: Int, attachment: MediaAttachment): Media {
        val outcome = runBlocking { store.add(name) { ByteArrayInputStream(jpeg(1600, 1200, color)) } }
        check(outcome is PhotoOutcome.Stored) { "$outcome" }
        return Media(outcome.mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    /**
     * A clip as the editor keeps one: a phone's 14 seconds of 720p — near the longest taken, 15, which a clip with its
     * sound track just passes — converted by the application's own converter.
     */
    private fun clip(attachment: MediaAttachment): Media {
        TestClips.write(source, 1280, 720, seconds = 14)
        val clips = (context as RecipeForkApplication).container.clipStore(context)
        val outcome = runBlocking { clips.add(name, TestClips.uri(context, source)) }
        check(outcome is ClipOutcome.Stored) { "$outcome" }
        return Media(outcome.mediaId, null, MediaKind.VIDEO, attachment, MediaVisibility.PRIVATE)
    }

    /** An own recipe with a cover, a step photo and a step's clip; a found one with a cover; a fork of the own one. */
    private fun fill(): Triple<RecipeId, RecipeId, RecipeId> = runBlocking {
        val own = Recipe.create(now, null).copy(
            title = "Borscht", servings = 4, state = RecipeState.PRIVATE,
            steps = listOf(Step(0, "Boil the beets."), Step(1, "Fry the onion."), Step(2, "Serve.")),
            media = listOf(photo(Color.RED, MediaAttachment.Cover), photo(Color.BLUE, MediaAttachment.ToStep(0)), clip(MediaAttachment.ToStep(1))),
        )
        recipes.save(own)
        val found = Recipe.create(now, null).copy(
            title = "Found pie", statedAuthor = "anna", state = RecipeState.LINK_SHARED,
            steps = listOf(Step(0, "Bake.")),
            media = listOf(photo(Color.YELLOW, MediaAttachment.Cover)),
        )
        assertTrue(recipes.addFound(found, now))
        val fork = forkRecipe(recipes, active, own.id, now)!!
        Triple(own.id, found.id, fork)
    }

    /** Waits until [backup] ends, and returns what it ended with. */
    private fun ended(): BackupNotice {
        val deadline = SystemClock.uptimeMillis() + PATIENCE_MS
        while (true) {
            val state = backup.state.value
            if (state is BackupState.Ended) return state.notice.also(backup::noticeSaid)
            check(state !is BackupState.Asking && state !is BackupState.ConfirmingReplace) { "asked into an empty book: $state" }
            check(SystemClock.uptimeMillis() < deadline) { "still $state" }
            Thread.sleep(20)
        }
    }

    @Test
    fun aBookWithAClipSavedToAFileComesBackWholeIntoAnEmptiedBook() {
        val (own, found, fork) = fill()
        val before = listOf(own, found, fork).associateWith { runBlocking { recipes.get(it) }!! }
        val media = store.directory(name).listFiles()!!.associate { it.name to it.readBytes() }
        // The fork holds copies of its parent's three under names of its own.
        assertEquals(7, media.size)
        val clipBytes = media.getValue(before.getValue(own).media.single { it.kind == MediaKind.VIDEO }.mediaId).size

        // Saved through a content address, as the create-document picker hands one.
        saved.parentFile!!.mkdirs()
        saved.writeBytes(ByteArray(0))
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", saved)
        val saving = SystemClock.elapsedRealtime()
        assertTrue(backup.save(uri))
        assertEquals(BackupNotice.Saved(3), ended())
        val savedIn = SystemClock.elapsedRealtime() - saving
        val size = saved.length()
        assertTrue("the file holds the media: $size bytes", size > media.values.sumOf { it.size })

        // The phone is reset: the book's database and its media are gone, and My kitchen is empty.
        book.value.database.close()
        assertTrue(context.deleteDatabase(name.fileName))
        assertTrue(store.directory(name).deleteRecursively())
        kitchen.replace(emptySet())
        book.value = ActiveBook.Open(name, BookDatabase.open(context, name))
        assertEquals(0, runBlocking { recipes.count() })

        // Restored from the file: into an empty book, without asking.
        val restoring = SystemClock.elapsedRealtime()
        assertTrue(backup.restoreFrom(uri))
        assertEquals(BackupNotice.Restored(3), ended())
        val restoredIn = SystemClock.elapsedRealtime() - restoring
        Log.i(TAG, "a book of 3 recipes and ${media.size} media files, ${media.values.sumOf { it.size }} bytes, two clips of $clipBytes bytes: file $size bytes, saved in $savedIn ms, restored in $restoredIn ms")

        before.forEach { (id, recipe) -> assertEquals(recipe, runBlocking { recipes.get(id) }) }
        assertEquals(3, runBlocking { recipes.count() })
        assertEquals(RecipeState.PRIVATE, runBlocking { recipes.get(own) }!!.state)
        assertTrue(runBlocking { recipes.isMarked(found) })
        assertFalse(runBlocking { recipes.isMarked(own) })
        assertFalse(runBlocking { recipes.isMarked(fork) })
        assertEquals(own, runBlocking { recipes.get(fork) }!!.parent)
        val back = store.directory(name).listFiles()!!.associate { it.name to it.readBytes() }
        assertEquals(media.keys, back.keys)
        media.forEach { (id, bytes) -> assertArrayEquals(id, bytes, back.getValue(id)) }
        assertEquals(kitchenHeld, kitchen.held.value)

        // The restored clip plays on the card's own player.
        val clip = store.file(name, before.getValue(own).media.single { it.kind == MediaKind.VIDEO }.mediaId)
        assertPlays(before.getValue(own), clip)
    }

    /**
     * Shows [recipe]'s steps as the card does, its step 2 with [clip] on the platform's player: the picture is drawn in
     * its box, and it moves — the test clip pans steadily, so a box that changes is a clip playing.
     */
    private fun assertPlays(recipe: Recipe, clip: File) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White).padding(16.dp)) {
                    StepsList(recipe.steps, recipe.id, media = StepMediaOnCard { position -> if (position == 1) StepMediaItem.Clip(clip) else null })
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(1)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { box().let { box -> box.width() > 0 && screen().let { shot -> drawn(shot, box).also { shot.recycle() } } > 0.5f } }
        // With the system's animations off a clip waits for its control; the reader plays it.
        if (animationsOff(context)) compose.onNodeWithTag(CardTags.stepVideoControl(1), useUnmergedTree = true).performClick()
        val first = screen()
        try {
            compose.waitUntil(PATIENCE_MS) { screen().let { shot -> moved(first, shot, box()).also { shot.recycle() } } }
        } finally {
            first.recycle()
        }
    }

    private fun box(): Rect {
        val root = IntArray(2).also { compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0).getLocationOnScreen(it) }
        val bounds = compose.onNodeWithTag(CardTags.stepVideo(1), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return Rect(bounds.left.toInt() + root[0], bounds.top.toInt() + root[1], bounds.right.toInt() + root[0], bounds.bottom.toInt() + root[1])
    }

    private fun screen(): Bitmap = instrumentation.uiAutomation.takeScreenshot()!!.let { taken ->
        taken.copy(Bitmap.Config.ARGB_8888, false).also { if (it !== taken) taken.recycle() }
    }

    private fun near(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) <= 8 && abs(Color.green(a) - Color.green(b)) <= 8 && abs(Color.blue(a) - Color.blue(b)) <= 8

    /** The share of [box]'s pixels on [shot] that are not the white background: the clip's picture. */
    private fun drawn(shot: Bitmap, box: Rect): Float {
        var count = 0
        var total = 0
        for (y in box.top + 2 until box.bottom - 2 step 4) {
            for (x in box.left + 2 until box.right - 2 step 4) {
                total++
                if (!near(shot.getPixel(x, y), Color.WHITE)) count++
            }
        }
        return count.toFloat() / total
    }

    /** Whether a tenth of [box]'s pixels at least differ between [a] and [b]: the picture moved. */
    private fun moved(a: Bitmap, b: Bitmap, box: Rect): Boolean {
        var count = 0
        var total = 0
        for (y in box.top + 2 until box.bottom - 2 step 4) {
            for (x in box.left + 2 until box.right - 2 step 4) {
                total++
                if (!near(a.getPixel(x, y), b.getPixel(x, y))) count++
            }
        }
        return total > 0 && count >= total / 10
    }

    private companion object {
        const val TAG = "BookBackupOnDevice"
        const val PATIENCE_MS = 180_000L
    }
}
