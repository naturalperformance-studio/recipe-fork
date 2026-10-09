package app.recipefork.app.play

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.LauncherIconTest
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.BookName
import app.recipefork.app.card.CardTags
import org.junit.Assert.assertFalse
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.DoneMark
import app.recipefork.app.data.ThemeChoice
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * The Google Play store's phone screenshots and feature graphic, drawn as the device draws them (Robolectric's native
 * graphics) from the application's own screens over the demo book ([PlayDemoBook]): the book, a card mid-cook on its View tab, the
 * editor, the card's steps on its Cooking tab and the settings, in English and in Russian, in the light theme, 1080×1920 opaque PNGs. The
 * screen is 432 × 768 dp at 400 dpi, close to a Pixel's 412 dp, on which the editor shows both of the traybake's ingredient
 * groups at once.
 *
 * They are written only when asked: `scripts/play-screenshots.sh` passes `-Precipefork.playScreenshots=<directory>`,
 * which the build hands the tests as the system property [PROPERTY]; without it every test here is skipped, so the
 * full suite writes nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayScreenshotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out: File? = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }?.let(::File)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container get() = (context as RecipeForkApplication).container
    private var timers: TestTimers? = null

    /** Whether the status bar's place takes the colour of a top bar scrolled under, as the card's is mid-recipe. */
    private var tinted by mutableStateOf(false)

    @Before
    fun onlyWhenAsked() = assumeTrue("store screenshots are written only when $PROPERTY names a directory", out != null)

    @After
    fun close() {
        timers?.let {
            compose.unregisterIdlingResource(it.idling)
            it.close()
        }
    }

    @Test
    @Config(qualifiers = "en-w432dp-h768dp-400dpi")
    fun english() = shoot(RecipeLanguage.EN)

    @Test
    @Config(qualifiers = "ru-w432dp-h768dp-400dpi")
    fun russian() = shoot(RecipeLanguage.RU)

    /** The feature graphic: the icon and the application's name on the theme's primary container, nothing else. */
    @Test
    fun featureGraphic() {
        val bitmap = Bitmap.createBitmap(GRAPHIC_WIDTH, GRAPHIC_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(context.getColor(R.color.theme_light_primaryContainer))
        val icon = LauncherIconTest.renderPlayIcon(context, GRAPHIC_ICON)
        val name = context.getString(R.string.app_name)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.theme_light_onPrimaryContainer)
            textSize = GRAPHIC_TEXT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val gap = GRAPHIC_ICON * 0.16f
        val left = (GRAPHIC_WIDTH - (GRAPHIC_ICON + gap + text.measureText(name))) / 2
        canvas.drawBitmap(icon, left, (GRAPHIC_HEIGHT - GRAPHIC_ICON) / 2f, null)
        val metrics = text.fontMetrics
        canvas.drawText(name, left + GRAPHIC_ICON + gap, GRAPHIC_HEIGHT / 2f - (metrics.ascent + metrics.descent) / 2, text)
        write(bitmap, "feature-graphic.png", GRAPHIC_WIDTH, GRAPHIC_HEIGHT)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun shoot(language: RecipeLanguage) {
        val prefix = language.name.lowercase()
        val demo = PlayDemoBook(language)
        val traybake = demo.traybake
        val timers = TestTimers(context).also { timers = it }
        compose.registerIdlingResource(timers.idling)
        demo.pictures(context.filesDir)
        runBlocking { demo.recipes.forEach { container.recipes.save(it.recipe) } }
        // The settings as a reader of the light screenshots would have them: the light theme, four servings usually.
        container.themeSetting.set(ThemeChoice.LIGHT)
        container.usualServings.set(Rational.of(4))
        container.doneMarks.hold(BookName.Pool, traybake.id, demo.doneIngredients.mapTo(HashSet()) { DoneMark.of(traybake.ingredients[it]) })
        val session = LibrarySession(SavedStateHandle()).apply { choose(LibraryView.OWN) }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card) {
                AppTheme(dark = false) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        // The status bar's place: Robolectric draws no system bar, and the screens lay no inset out. It
                        // is drawn in the top bar's colour, as an edge-to-edge screen's bar runs under the status bar.
                        val barScrolledUnder = TopAppBarDefaults.topAppBarColors().scrolledContainerColor
                        val band = if (tinted) barScrolledUnder else MaterialTheme.colorScheme.surface
                        Spacer(Modifier.fillMaxWidth().height(STATUS_BAR).background(band))
                        Surface(Modifier.fillMaxWidth().weight(1f)) { AppNavHost(session, container.recipes, Mode.Serverless) }
                    }
                }
            }
        }

        // (1) The book: its newest recipes first — the traybake with its cover, then the two without — their tags named
        // and their covers read. It is scrolled as a finger scrolls it: the top bar goes first, then the list goes as
        // far as brings the third entry whole above the view's action, which shrinks to its icon.
        val book = demo.recipes.map { it.recipe }.reversed()
        await(LibraryTags.entryTags(book.first().id.value))
        val third = LibraryTags.entry(book[2].id.value)
        await(third)
        scrollBy(LibraryTags.OWN_LIST, bottomOf(LibraryTags.TOP_BAR) - topOf(LibraryTags.TOP_BAR))
        val clear = bottomOf(third) - (topOf(LibraryTags.NEW) - px(8))
        if (clear > 0) scrollBy(LibraryTags.OWN_LIST, clear)
        // Three titles readable on the first screen.
        val list = compose.onNodeWithTag(LibraryTags.OWN_LIST).fetchSemanticsNode().boundsInRoot
        book.take(3).forEach { recipe ->
            val title = LibraryTags.entryTitle(recipe.id.value)
            assertTrue("${recipe.title} on the book's first screen", topOf(title) >= list.top && bottomOf(title) <= list.bottom)
        }
        book.filter { it.media.isNotEmpty() && shown(LibraryTags.entry(it.id.value)) }.forEach { await(LibraryTags.cover(it.id.value)) }
        capture("$prefix-1-book.png")

        // (2) The traybake's card on its View tab, mid-cook: six servings in place of four, the marinade marked done,
        // the oven running — started on the Cooking tab, where timers start.
        session.openCard(traybake.id.value, LibraryView.OWN)
        await(CardTags.COVER)
        assertTrue("the card opens on View", selected(CardMode.VIEW))
        repeat(2) {
            compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
            compose.waitForIdle()
        }
        choose(CardMode.COOKING)
        compose.onNodeWithTag(TimerTags.start(demo.runningStep)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { timers.clock.advance(RUN_FOR) }
        compose.waitForIdle()
        choose(CardMode.VIEW)
        await(CardTags.COVER)
        await(CardTags.SUMMARY)
        scrollToTop(CardTags.CONTENT, CardTags.TITLE)
        await(TimerTags.BAR)
        assertWhole(TimerTags.barStatus(demo.runningStep), "the running timer in the bar")
        assertTrue("fork above the bar of running timers", bottomOf(CardTags.FORK) <= topOf(TimerTags.BAR))
        tinted = true
        capture("$prefix-2-card.png")

        // (4) The card's steps on its Cooking tab: references read at six servings, a step's photo, and the running step
        // whole with its timer running in it — the bar steps aside while the running step is in view.
        choose(CardMode.COOKING)
        assertFalse("no fork on Cooking", shown(CardTags.FORK))
        scrollToTop(CardTags.CONTENT, CardTags.STEPS)
        await(CardTags.stepPhoto(1))
        val running = TimerTags.cancel(demo.runningStep)
        await(running)
        // The running step ends clear above the content's end.
        val chipEnd = bottomOf(running) + px(12)
        val frameEnd = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().boundsInRoot.bottom
        if (chipEnd > frameEnd) scrollBy(CardTags.CONTENT, chipEnd - frameEnd)
        // No step cut at the top edge: where the step of the photo no longer stands whole, the shot begins at its photo.
        val frameTop = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().boundsInRoot.top
        if (topOf(CardTags.step(1)) < frameTop) scrollBy(CardTags.CONTENT, topOf(CardTags.stepPhoto(1)) - frameTop - px(8))
        assertWhole(TimerTags.status(demo.runningStep), "the running step's timer")
        assertWhole(running, "the running step's timer")
        assertWhole(CardTags.stepPhoto(1), "the step's photo")
        assertTrue("Cooking's tab chosen", selected(CardMode.COOKING))
        capture("$prefix-4-steps.png")

        // (3) The editor on the same recipe, at its ingredient groups and their notes: both groups on the screen.
        tinted = false
        session.openEditor(traybake.id.value)
        await(EditorTags.FORM)
        await(EditorTags.group(0))
        scrollToTop(EditorTags.FORM, EditorTags.group(0))
        await(EditorTags.group(1))
        val form = compose.onNodeWithTag(EditorTags.FORM).fetchSemanticsNode().boundsInRoot
        assertTrue("both groups in the editor's shot", topOf(EditorTags.group(0)) >= form.top && bottomOf(EditorTags.group(1)) <= form.bottom)
        capture("$prefix-3-editor.png")

        // (5) The settings.
        session.closeEditor()
        compose.waitForIdle()
        session.closeCard()
        compose.waitForIdle()
        session.openSettings()
        await(SettingsTags.LIST)
        capture("$prefix-5-settings.png")
    }

    private fun selected(mode: CardMode) =
        compose.onNodeWithTag(CardTags.tab(mode)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    /** Turns the card to its tab [mode] and waits until it has come to rest there. */
    private fun choose(mode: CardMode) {
        compose.onNodeWithTag(CardTags.tab(mode)).performClick()
        compose.waitUntil(PATIENCE_MS) { selected(mode) && compose.onAllNodes(hasTestTag(CardTags.CONTENT)).fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }

    /** Asserts that the node tagged [tag] stands whole on the screen, between the status bar's place and the bottom. */
    private fun assertWhole(tag: String, what: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("$what whole on the screen", shown(tag) && topOf(tag) >= px(STATUS_BAR_DP) && bottomOf(tag) <= root.bottom)
    }

    private fun scrollBy(scrolling: String, by: Float) {
        compose.onNodeWithTag(scrolling).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, by) }
        compose.waitForIdle()
    }

    private fun px(dp: Int) = with(compose.density) { dp.dp.toPx() }

    /** Where the node tagged [tag] begins and ends in the root, wherever its scrolling container cuts it. */
    private fun topOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    private fun bottomOf(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) {
        compose.waitUntil(PATIENCE_MS) { shown(tag) }
        compose.waitForIdle()
    }

    /** Scrolls [scrolling] so that the node tagged [target] stands at its top, a little below its edge. */
    private fun scrollToTop(scrolling: String, target: String) {
        compose.onNodeWithTag(target).performScrollTo()
        compose.waitForIdle()
        val top = compose.onNodeWithTag(scrolling).fetchSemanticsNode().boundsInRoot.top
        val at = compose.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot.top
        val margin = with(compose.density) { 8.dp.toPx() }
        compose.onNodeWithTag(scrolling).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, at - top - margin) }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        // The status bar's place is one with the top bar beneath it: no strip of another colour above the bar.
        val band = px(STATUS_BAR_DP).toInt()
        assertEquals(
            "$name: the status bar's place in the top bar's colour",
            Integer.toHexString(bitmap.getPixel(1, band + 1)),
            Integer.toHexString(bitmap.getPixel(1, band / 2)),
        )
        write(bitmap, name, SCREEN_WIDTH, SCREEN_HEIGHT)
    }

    /** Writes [bitmap] as an opaque 24-bit PNG [name] in the directory asked for, once it has the size Play asks for. */
    private fun write(bitmap: Bitmap, name: String, width: Int, height: Int) {
        assertEquals("$name width", width, bitmap.width)
        assertEquals("$name height", height, bitmap.height)
        val directory = out!!.also { it.mkdirs() }
        File(directory, name).writeBytes(opaquePng(bitmap))
    }

    companion object {
        /**
         * [bitmap] as a PNG of 8-bit RGB with no alpha channel, what Play takes for a screenshot. Written by hand: Android's
         * encoder gives no say over the colour type, and the JDK's loads AWT, which aborted the native graphics' process.
         */
        fun opaquePng(bitmap: Bitmap): ByteArray {
            val width = bitmap.width
            val height = bitmap.height
            val row = IntArray(width)
            val raw = ByteArrayOutputStream()
            DeflaterOutputStream(raw).use { deflated ->
                val line = ByteArray(1 + width * 3)
                for (y in 0 until height) {
                    bitmap.getPixels(row, 0, width, 0, y, width, 1)
                    for (x in 0 until width) {
                        val pixel = row[x]
                        line[1 + x * 3] = (pixel shr 16).toByte()
                        line[2 + x * 3] = (pixel shr 8).toByte()
                        line[3 + x * 3] = pixel.toByte()
                    }
                    deflated.write(line)
                }
            }
            val png = ByteArrayOutputStream()
            val data = DataOutputStream(png)
            data.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
            fun chunk(type: String, body: ByteArray) {
                data.writeInt(body.size)
                val typed = type.toByteArray(Charsets.US_ASCII) + body
                data.write(typed)
                data.writeInt(CRC32().apply { update(typed) }.value.toInt())
            }
            val header = ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).apply {
                    writeInt(width)
                    writeInt(height)
                    write(byteArrayOf(8, 2, 0, 0, 0)) // 8 bits a channel, RGB, deflate, no filter method, no interlace
                }
            }.toByteArray()
            chunk("IHDR", header)
            chunk("IDAT", raw.toByteArray())
            chunk("IEND", ByteArray(0))
            return png.toByteArray()
        }

        const val PROPERTY = "recipefork.playScreenshots"
        private const val STATUS_BAR_DP = 24
        private val STATUS_BAR = STATUS_BAR_DP.dp
        private const val SCREEN_WIDTH = 1080
        private const val SCREEN_HEIGHT = 1920
        private const val GRAPHIC_WIDTH = 1024
        private const val GRAPHIC_HEIGHT = 500
        private const val GRAPHIC_ICON = 280
        private const val GRAPHIC_TEXT = 104f
    }
}
