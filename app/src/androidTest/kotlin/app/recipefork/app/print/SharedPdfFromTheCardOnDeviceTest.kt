package app.recipefork.app.print

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.card.CardScale
import app.recipefork.app.card.CardTags
import app.recipefork.app.media.PhotoOutcome
import app.recipefork.app.media.PhotoStore
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * "Share as PDF" from a recipe's card on a real device (FLOW-075 (2)), by the path the card takes — its ⋮, the
 * platform's printing, the platform's PdfDocument writing the pages, the files' FileProvider — up to the share sheet,
 * which the test answers itself: the file it is handed is named after the recipe, opens with the platform's
 * PdfRenderer and holds the pages the recipe makes on A4, its cover drawn at the top of the first. How long the share
 * takes on the device is written to the log under [TIMING].
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SharedPdfFromTheCardOnDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as RecipeForkApplication
    private val container = application.container
    private val store = PhotoStore(application.filesDir)
    private val book = container.recipes.bookName()
    private val now = Instant.now().minusSeconds(3600)
    private var recipe: Recipe? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() = runBlocking {
        scenario?.close()
        recipe?.let { written ->
            container.recipes.delete(written.id)
            written.media.forEach { store.file(book, it.mediaId).delete() }
        }
        File(application.cacheDir, "prints").deleteRecursively()
        Unit
    }

    /** A cover as a phone's camera takes one: 4000 x 3000, red, stored by the book as the editor stores it. */
    private fun cover(): Media {
        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        bitmap.recycle()
        val outcome = runBlocking { store.add(book) { ByteArrayInputStream(bytes) } }
        check(outcome is PhotoOutcome.Stored) { "$outcome" }
        return Media(outcome.mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)
    }

    /** A recipe with a cover photo, three ingredients and [steps] steps, the first two with a timer. */
    private fun recipe(steps: List<String>): Recipe = Recipe.create(now, null, null, RecipeId.mint()).copy(
        // A title of its own each run, so that nothing an earlier run left in the book is taken for it.
        title = "Лимонный кекс ${System.currentTimeMillis() % 100_000}",
        description = "A lemon loaf, moist and sharp.",
        servings = 8,
        ingredients = listOf(
            Ingredient(0, null, null, "Flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(250), null, null, null),
            Ingredient(1, null, null, "Sugar", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200), null, null, null),
            Ingredient(2, null, null, "Lemons", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2), null, null, null),
        ),
        steps = steps.mapIndexed { at, text ->
            if (at < 2) Step(at, text, emptyList(), Timer(10.minutes * (at + 1))) else Step(at, text)
        },
        media = listOf(cover()),
    )

    /** Writes [written] to the book and opens its card from the recipes written here. */
    private fun openCard(written: Recipe) {
        recipe = written
        runBlocking { container.recipes.save(written) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodesWithText(WRITTEN_HERE).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(WRITTEN_HERE)[0].performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodesWithText(written.title).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(written.title)[0].performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.MENU), PATIENCE_MS)
    }

    /**
     * Takes the card's ⋮ → Share as PDF and returns the address the share sheet is handed with the milliseconds from
     * the tap to the sheet; the sheet itself is answered by the test.
     */
    private fun shareFromTheMenu(): Pair<Uri, Long> {
        var sheet: Intent? = null
        var opened = 0L
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                opened = SystemClock.elapsedRealtime()
                sheet = intent
                return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithTag(CardTags.MENU).performClick()
            // The items for printing come once the recipe is read with its scale.
            compose.waitUntilExactlyOneExists(hasTestTag(CardTags.SHARE_PDF) and hasText(SHARE_AS_PDF), PATIENCE_MS)
            val tapped = SystemClock.elapsedRealtime()
            compose.onNodeWithTag(CardTags.SHARE_PDF).performClick()
            compose.waitUntil(PATIENCE_MS) { sheet != null }
            val chooser = sheet!!
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("application/pdf", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals(application.packageName + ".files", uri.authority)
            return uri to opened - tapped
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** The name the provider gives the file at [uri]. */
    private fun nameOf(uri: Uri): String =
        application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }

    private fun <T> read(uri: Uri, check: (PdfRenderer) -> T): T =
        PdfRenderer(application.contentResolver.openFileDescriptor(uri, "r")!!).use(check)

    @Test
    fun aRecipeWithACoverAndAFewStepsIsSharedAsAOnePagePdf() {
        val written = recipe(
            listOf(
                "Rub the lemon zest into the sugar.",
                "Beat in the eggs one at a time, then fold in the flour.",
                "Bake for 50 minutes; drizzle with the lemon juice while warm.",
            ),
        )
        openCard(written)

        val (uri, first) = shareFromTheMenu()
        assertEquals("${written.title}.pdf", nameOf(uri))
        read(uri) { renderer ->
            assertEquals(1, renderer.pageCount)
            renderer.openPage(0).use { page ->
                assertEquals(595, page.width)
                assertEquals(842, page.height)
                val shown = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                shown.eraseColor(Color.WHITE)
                page.render(shown, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                // The margin is paper; the cover, a third of the page at most, is drawn across the top.
                assertEquals(Color.WHITE, shown.getPixel(4, 4))
                val inCover = shown.getPixel(297, 120)
                assertTrue("%08x".format(inCover), Color.red(inCover) > 200 && Color.green(inCover) < 60)
            }
        }

        // Once more, the page drawing warmed up: the second share replaces the first file.
        val (again, second) = shareFromTheMenu()
        assertEquals(1, read(again) { it.pageCount })
        assertEquals(listOf("${written.title}.pdf"), File(application.cacheDir, "prints").list().orEmpty().toList())

        // The writer alone — laying the pages out, the cover decoded, and writing them — as the card's share calls it.
        val document = documentOf(written)
        val writes = List(5) {
            val start = SystemClock.elapsedRealtime()
            runBlocking { PdfShare.write(application, document) }
            SystemClock.elapsedRealtime() - start
        }
        Log.i(TIMING, "Share as PDF, tap to share sheet: first ${first} ms, again ${second} ms; PdfShare.write alone: ${writes.joinToString()} ms (median ${writes.sorted()[writes.size / 2]} ms)")
    }

    @Test
    fun aLongRecipeIsSharedWithEveryPageItMakes() {
        val written = recipe(
            List(40) { "Step ${it + 1}: stir the batter gently with a wooden spoon, scraping the sides of the bowl, until it is smooth and glossy." },
        )
        openCard(written)

        val (uri, took) = shareFromTheMenu()
        val expected = PrintPages.layOut(documentOf(written), PageSize.A4).count
        assertTrue("$expected pages laid out", expected >= 2)
        read(uri) { renderer ->
            assertEquals(expected, renderer.pageCount)
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page -> assertEquals(595 to 842, page.width to page.height) }
            }
        }
        Log.i(TIMING, "Share as PDF of $expected pages, tap to share sheet: $took ms")
    }

    /** The document the card makes of [written] read as its author wrote it, in the Activity's language. */
    private fun documentOf(written: Recipe): PrintDocument {
        var resources = application.resources
        scenario!!.onActivity { resources = it.resources }
        return RecipeDocument.of(written, emptyMap(), emptyMap(), CardScale(null), resources) { store.file(book, it) }
    }

    private companion object {
        const val TIMING = "PdfShareTiming"
        const val PATIENCE_MS = 30_000L
        const val WRITTEN_HERE = "Written here"
        const val SHARE_AS_PDF = "Share as PDF"
    }
}
