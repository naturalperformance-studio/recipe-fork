package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.CollectionScreenTags
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.LocalHaptics
import app.recipefork.app.ui.RecordingHaptics
import app.recipefork.app.ui.appColorScheme
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs

/**
 * While recipes are chosen, the selection holds (v0.38, T-267): the pages are not swiped, and the tabs, the search,
 * the filters, the order and the collections do not respond, so neither a swipe nor a tab drops it; it ends only as
 * before — ✕, Back, an action, the last mark taken off. Its bar is tinted — the secondary container, not the book's own
 * bar's colour — and fades in and out with the book's bar; starting it is felt as the application's confirmation.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class BookSelectionHoldsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val collections = CollectionRepository(active)
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val marks = DoneMarks.inMemory()
    private val haptics = RecordingHaptics()
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    private val timers = TestTimers(context)
    private val cardTimers = CardTimers(timers.store, timers.scope, timers.access, timers.ringing) { BookName.Pool }

    @Before
    fun setUp() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::registerIdlingResource)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        sessions.clear()
        book.close()
        device.close()
        timers.close()
    }

    private fun own(title: String, minutesAgo: Long = 0): Recipe =
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, language = RecipeLanguage.EN)
            .also { runBlocking { recipes.save(it) } }

    private fun found(title: String): Recipe =
        Recipe.create(now, owner = null).copy(title = title).inState(RecipeImport.FOUND_STATE)
            .also { assertTrue(runBlocking { recipes.addFound(it, now) }) }

    private fun collection(name: String, vararg members: Recipe): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it.id, listOf(id)) } }
    }

    private fun show(view: LibraryView, platform: HapticFeedback? = null) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment(marks = marks) { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers, LocalHaptics provides haptics) {
                val content = @androidx.compose.runtime.Composable {
                    AppTheme { AppNavHost(session, recipes, Mode.Standard, editor = editor, card = card, collections = collections) }
                }
                if (platform == null) content() else CompositionLocalProvider(LocalHapticFeedback provides platform) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitEntry(recipe: Recipe, list: String = LibraryTags.OWN_LIST) = compose.waitUntil(PATIENCE_MS) {
        try {
            compose.onNodeWithTag(list).performScrollToNode(hasTestTag(LibraryTags.entry(recipe.id.value)))
            true
        } catch (e: AssertionError) {
            false
        }
    }

    private fun longPress(recipe: Recipe) {
        awaitEntry(recipe)
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_BAR) && !exists(LibraryTags.TITLE) }
    }

    /** The selection is still on, in the own view, holding [ids], and the own view is still shown. */
    private fun assertHolds(vararg ids: String) {
        compose.waitForIdle()
        assertEquals(LibraryView.OWN, session.view.value)
        assertEquals(ids.toSet(), session.selection.value?.ids)
        assertTrue(exists(LibraryTags.SELECTION_BAR))
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsSelected()
    }

    private fun pressSystemBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    // --- Neither a swipe nor a tab drops it -------------------------------------------------------------------------

    @Test
    fun aSwipeDoesNotChangeTheViewWhileSelecting() {
        val soup = own("Soup")
        found("Borscht")
        show(LibraryView.OWN)
        longPress(soup)

        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeRight() }
        assertHolds(soup.id.value)
        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeLeft() }
        assertHolds(soup.id.value)

        // Once it ends, a swipe shows the other view again.
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value == null }
        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeLeft() }
        compose.waitUntil(PATIENCE_MS) { session.view.value == LibraryView.MARKED }
    }

    @Test
    fun theTabsDoNotRespondWhileSelecting() {
        val soup = own("Soup")
        found("Borscht")
        show(LibraryView.OWN)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsEnabled()
        longPress(soup)

        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsNotEnabled()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsNotEnabled()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        assertHolds(soup.id.value)

        pressSystemBack()
        compose.waitUntil(PATIENCE_MS) { session.selection.value == null }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { session.view.value == LibraryView.MARKED }
    }

    // The search field, the order, the filters and the collections do not respond either: nothing opens, nothing is
    // typed, nothing narrows the view the selection is made in.
    @Test
    fun theSearchTheFiltersAndTheCollectionsDoNotRespondWhileSelecting() {
        val soup = own("Soup")
        val pie = own("Pie", minutesAgo = 1)
        val dinner = collection("Dinner", soup)
        show(LibraryView.OWN)
        await(CollectionScreenTags.card(dinner.value))
        listOf(LibraryTags.SEARCH, LibraryTags.ORDER, LibraryTags.FILTER_ADD, CollectionScreenTags.card(dinner.value)).forEach {
            compose.onNodeWithTag(it).assertIsEnabled()
        }
        longPress(soup)

        listOf(LibraryTags.SEARCH, LibraryTags.ORDER, LibraryTags.FILTER_ADD, CollectionScreenTags.card(dinner.value)).forEach {
            compose.onNodeWithTag(it).assertIsNotEnabled()
        }
        compose.onNodeWithTag(LibraryTags.FILTER_ADD).performClick()
        compose.waitForIdle()
        assertFalse("the filters' sheet opened", exists(LibraryTags.FILTERS_SHEET))
        compose.onNodeWithTag(LibraryTags.ORDER).performClick()
        compose.waitForIdle()
        assertFalse("the order's menu opened", exists(LibraryTags.order(app.recipefork.core.search.BookOrder.TITLE)))
        compose.onNodeWithTag(CollectionScreenTags.card(dinner.value)).performClick()
        compose.waitForIdle()
        assertNull("a collection opened", session.collection.value)
        assertHolds(soup.id.value)

        // The entries still respond: the selection goes on as before.
        awaitEntry(pie)
        compose.onNodeWithTag(LibraryTags.entry(pie.id.value)).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 2 }
        // Ended, everything responds again.
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value == null }
        listOf(LibraryTags.SEARCH, LibraryTags.ORDER, LibraryTags.FILTER_ADD, CollectionScreenTags.card(dinner.value)).forEach {
            compose.onNodeWithTag(it).assertIsEnabled()
        }
    }

    // --- Felt, tinted, faded ----------------------------------------------------------------------------------------

    // A long press that starts the selection is felt as a confirmation; choosing more, or ending it, is not.
    @Test
    fun startingTheSelectionPlaysTheConfirmation() {
        val soup = own("Soup")
        val pie = own("Pie", minutesAgo = 1)
        show(LibraryView.OWN)
        assertEquals(emptyList<Haptic>(), haptics.all)

        longPress(soup)
        compose.waitUntil(PATIENCE_MS) { haptics.all == listOf(Haptic.CONFIRM) }
        awaitEntry(pie)
        compose.onNodeWithTag(LibraryTags.entry(pie.id.value)).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 2 }
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value == null }
        assertEquals(listOf(Haptic.CONFIRM), haptics.all)

        longPress(pie)
        compose.waitUntil(PATIENCE_MS) { haptics.all == listOf(Haptic.CONFIRM, Haptic.CONFIRM) }
    }

    /** The colour the bar tagged [tag] is drawn in, at its end, clear of its icons. */
    private fun barColour(tag: String): Color {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[pixels.width - 2, pixels.height / 2]
    }

    // The selection's bar is drawn in the secondary container, apart from the book's own bar.
    @Test
    fun theSelectionsBarIsTinted() {
        val colours = appColorScheme(context.resources, dark = false)
        val soup = own("Soup")
        show(LibraryView.OWN)
        await(LibraryTags.TOP_BAR)
        val plain = barColour(LibraryTags.TOP_BAR)
        longPress(soup)
        compose.waitForIdle()
        val tinted = barColour(LibraryTags.SELECTION_BAR)
        assertEquals(colours.secondaryContainer, tinted)
        assertNotEquals("the selection's bar is drawn as the book's", plain, tinted)
        assertNotEquals(colours.surface, tinted)
        assertNotEquals(colours.surfaceContainer, tinted)
    }

    /** The node tagged [tag] — or, with [within], the one tagged [tag] inside the node tagged [within] — as drawn. */
    private fun drawn(tag: String, within: String? = null) = compose.onNode(
        if (within == null) hasTestTag(tag) else hasTestTag(tag) and hasAnyAncestor(hasTestTag(within)),
        useUnmergedTree = true,
    ).captureToImage().toPixelMap()

    /** Where [pixels] are darkest: the ink of the words they hold. */
    private fun darkest(pixels: PixelMap): Pair<Int, Int> {
        var at = 0 to 0
        var least = Float.MAX_VALUE
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val c = pixels[x, y]
            val sum = c.red + c.green + c.blue
            if (sum < least) {
                least = sum
                at = x to y
            }
        }
        return at
    }

    /** [colour] drawn at half its opacity over [page], as the redesign's `.mute` draws it. */
    private fun assertMuted(what: String, colour: Color, page: Color, muted: Color) {
        val expected = Color(
            (colour.red + page.red) / 2, (colour.green + page.green) / 2, (colour.blue + page.blue) / 2,
        )
        val off = maxOf(abs(expected.red - muted.red), abs(expected.green - muted.green), abs(expected.blue - muted.blue))
        assertTrue("$what: $colour over $page drawn $muted, not at half its opacity ($expected)", off < 0.06f)
    }

    /** [colour] drawn over [page] at half its opacity or fainter: never nearer its full colour than half-way. */
    private fun assertMutedAtLeast(what: String, colour: Color, page: Color, muted: Color) {
        val half = (colour.red + page.red + colour.green + page.green + colour.blue + page.blue) / 2
        val drawn = muted.red + muted.green + muted.blue
        val full = colour.red + colour.green + colour.blue
        assertTrue("$what: $colour over $page drawn $muted, stronger than at half its opacity", if (full < half) drawn >= half - 0.15f else drawn <= half + 0.15f)
    }

    // While entries are chosen, the controls that do not respond read as muted, at half their opacity (redesign-1
    // § 4.1, `.tabs.mute`): the tabs' words and indicator, and the shelf of collections; once it ends, they read whole.
    @Test
    fun theTabsAndTheShelfReadMutedWhileSelecting() {
        val soup = own("Soup")
        val dinner = collection("Dinner", soup)
        show(LibraryView.OWN)
        val spine = CollectionScreenTags.card(dinner.value)
        await(spine)
        awaitEntry(soup)
        compose.waitForIdle()
        val label = drawn(LibraryTags.TAB_LABEL, within = LibraryTags.tab(LibraryView.OWN))
        val ink = darkest(label)
        val labelPage = label[0, 0]
        val indicator = drawn(LibraryTags.TAB_INDICATOR)
        val accent = indicator[indicator.width / 2, indicator.height / 2]
        val shelf = drawn(spine)
        val name = darkest(shelf)
        val spineBounds = compose.onNodeWithTag(spine).getBoundsInRoot()
        val root = compose.onRoot().captureToImage().toPixelMap()
        val shelfPage = with(compose.density) {
            root[(spineBounds.left - 8.dp).roundToPx(), ((spineBounds.top + spineBounds.bottom) / 2).roundToPx()]
        }

        longPress(soup)
        compose.waitForIdle()
        assertMuted("a tab's words", label[ink.first, ink.second], labelPage, drawn(LibraryTags.TAB_LABEL, within = LibraryTags.tab(LibraryView.OWN))[ink.first, ink.second])
        assertMuted("the tabs' indicator", accent, labelPage, drawn(LibraryTags.TAB_INDICATOR).let { it[it.width / 2, it.height / 2] })
        // A spine not responding may be drawn fainter still by its own disabled colours; at half its opacity at least.
        assertMutedAtLeast("a collection's spine", shelf[name.first, name.second], shelfPage, drawn(spine)[name.first, name.second])

        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value == null }
        compose.waitForIdle()
        assertEquals("the tab's words whole again", label[ink.first, ink.second], drawn(LibraryTags.TAB_LABEL, within = LibraryTags.tab(LibraryView.OWN))[ink.first, ink.second])
        assertEquals("the spine whole again", shelf[name.first, name.second], drawn(spine)[name.first, name.second])
    }

    // The one bar fades into the other: midway, both are drawn; then only the one shown.
    @Test
    fun theBarsCrossfade() {
        val soup = own("Soup")
        show(LibraryView.OWN)
        awaitEntry(soup)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { session.select(LibraryView.OWN, soup.id.value) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(60)
        assertTrue("the selection's bar fades in", exists(LibraryTags.SELECTION_BAR))
        assertTrue("the book's bar fades out", exists(LibraryTags.BAR_LEAVING))
        // Leaving, the book's bar is no longer found by the screen reader (the merged tree, as it reads it).
        assertTrue(compose.onAllNodes(hasTestTag(LibraryTags.TITLE)).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag(LibraryTags.SETTINGS)).fetchSemanticsNodes().isEmpty())
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(exists(LibraryTags.SELECTION_BAR))
        assertFalse(exists(LibraryTags.BAR_LEAVING))
    }

    // The selection's bar fading out after it ends takes no touch: with the book's bar scrolled away, nothing stands
    // over the leaving bar, and its "Select all" must not choose anything; nor may its actions at the bottom, fading out
    // with it, take anything out of the book (v0.42).
    @Test
    fun theBarFadingOutTakesNoTouch() {
        val all = (0 until 20).map { own("Recipe $it", minutesAgo = it.toLong()) }
        show(LibraryView.OWN)
        awaitEntry(all.first())
        repeat(2) { compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput { swipeUp(durationMillis = 1_000) } }
        compose.waitUntil(PATIENCE_MS) { compose.onNodeWithTag(LibraryTags.TOP_BAR).getBoundsInRoot().let { it.bottom - it.top } < 1.dp }
        val chosen = all[10]
        longPress(chosen)
        await(LibraryTags.SELECT_ALL)
        await(LibraryTags.SELECTION_DELETE)
        compose.waitForIdle()
        fun centre(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().let { bounds ->
            with(compose.density) { Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx()) }
        }
        val selectAll = centre(LibraryTags.SELECT_ALL)
        val delete = centre(LibraryTags.SELECTION_DELETE)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { session.endSelection() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(30)
        assertTrue("the selection's bar fades out", exists(LibraryTags.SELECT_ALL))
        assertTrue("its actions fade out", exists(LibraryTags.SELECTION_DELETE))
        compose.onRoot().performTouchInput { click(selectAll) }
        compose.onRoot().performTouchInput { click(delete) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertNull("the leaving bar's Select all chose", session.selection.value)
        assertFalse(exists(LibraryTags.SELECTION_ACTIONS))
        assertNull("the leaving actions deleted", session.removal.value)
        assertNotNull(runBlocking { recipes.get(chosen.id) })
    }

    // A long press is felt once, as the application's confirmation: the platform's own long-press feedback is off.
    @Test
    fun aLongPressIsFeltOnlyAsTheConfirmation() {
        val platform = mutableListOf<HapticFeedbackType>()
        val soup = own("Soup")
        show(LibraryView.OWN, object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                platform += hapticFeedbackType
            }
        })
        longPress(soup)
        compose.waitUntil(PATIENCE_MS) { haptics.all == listOf(Haptic.CONFIRM) }
        assertEquals(emptyList<HapticFeedbackType>(), platform)
    }

    // --- In Russian, at twice the font scale ------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheSelectionHolds() {
        val soup = own("Суп")
        found("Борщ")
        show(LibraryView.OWN)
        longPress(soup)
        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeLeft() }
        assertHolds(soup.id.value)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsNotEnabled().performClick()
        assertHolds(soup.id.value)
        compose.onNodeWithTag(LibraryTags.SEARCH).assertIsNotEnabled()
        compose.waitUntil(PATIENCE_MS) { haptics.all == listOf(Haptic.CONFIRM) }
        pressSystemBack()
        compose.waitUntil(PATIENCE_MS) { session.selection.value == null }
    }
}
