package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
import java.time.Instant

/**
 * The card's top bar (scenarios 6 and 7 of the book-and-card increment): back, the recipe's title and its actions as
 * icons in an app bar that draws under the status bar and collapses as the recipe scrolls under it; the content kept
 * clear of the navigation bar; and, while the recipe is still being read, an indicator after a moment.
 */
@RunWith(AndroidJUnit4::class)
class CardTopBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private var backs = 0
    private var edits = 0
    private var forks = 0

    /** Held shut, a read of the recipe waits on it, so that the card can be seen while it loads. */
    @Volatile
    private var gate: CompletableDeferred<Unit>? = null

    private val recipes = object : RecipeRepository(active) {
        override fun observe(id: RecipeId): Flow<Recipe?> {
            val real = super.observe(id)
            return flow {
                gate?.await()
                emitAll(real)
            }
        }
    }

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    /** A recipe long enough to scroll, in [state]. */
    private fun save(title: String = "Roast chicken", state: RecipeState = RecipeState.LOCAL): Recipe =
        Recipe.create(now, null).copy(
            title = title,
            description = "A weeknight dinner.",
            sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT,
            steps = (0 until 30).map { Step(it, "Step number ${it + 1} of the roast.") },
        ).inState(state).also { runBlocking { recipes.save(it) } }

    private fun setCard(id: RecipeId) = compose.setContent {
        AppTheme { CardScreen(id, recipes, environment, onBack = { backs++ }, onEdit = { edits++ }, onFork = { forks++ }) }
    }

    private fun show(id: RecipeId) {
        setCard(id)
        compose.awaitCard()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun inTheBar(tag: String) =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.BAR)) and hasTestTag(tag)).fetchSemanticsNodes().size == 1

    private fun layoutOf(tag: String): TextLayoutResult =
        compose.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.let { action ->
                mutableListOf<TextLayoutResult>().also { action(it) }.first()
            }

    // Scenario 6: the bar holds the back arrow and, as icons named for the screen reader, fork and edit; each does
    // what the bar's actions did. The title is the content's heading, under the bar, which repeats it only later.
    @Test
    fun theBarHoldsBackTheTitleAndTheRecipesActionsAsNamedIcons() = theBar("Back", "Fork", "Edit")

    @Test
    @Config(qualifiers = "ru")
    fun theBarHoldsBackTheTitleAndTheRecipesActionsAsNamedIconsInRussian() = theBar("Назад", "Своя версия", "Изменить")

    private fun theBar(back: String, fork: String, edit: String) {
        show(save().id)
        listOf(CardTags.BACK, CardTags.FORK, CardTags.EDIT).forEach { assertTrue("$it in the bar", inTheBar(it)) }
        assertFalse("no title in the expanded bar", exists(CardTags.BAR_TITLE))
        assertTrue(compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.CONTENT)) and hasTestTag(CardTags.TITLE)).fetchSemanticsNodes().size == 1)
        assertEquals(listOf("Roast chicken"), compose.textsOf(CardTags.TITLE))
        assertTrue(compose.onAllNodes(hasTestTag(CardTags.TITLE) and isHeading()).fetchSemanticsNodes().size == 1)
        // Icons, not words: each is named by its content description alone.
        mapOf(CardTags.BACK to back, CardTags.FORK to fork, CardTags.EDIT to edit).forEach { (tag, name) ->
            assertEquals(listOf(name), compose.descriptionOf(tag))
            assertEquals(emptyList<String>(), compose.textsOf(tag))
            compose.assertCardTarget(tag)
        }
        val (b, f, e) = listOf(CardTags.BACK, CardTags.FORK, CardTags.EDIT).map(::bounds)
        val title = bounds(CardTags.TITLE)
        assertTrue("back at the start, fork then edit at the end", b.right <= f.left && f.right <= e.left)
        assertTrue("the title under the bar", title.top >= bounds(CardTags.BAR).bottom)
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.onNodeWithTag(CardTags.FORK).performClick()
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        assertEquals(listOf(1, 1, 1), listOf(backs, forks, edits))
    }

    // Scenario 6: a frozen recipe's bar offers fork and no edit, as before.
    @Test
    fun aFrozenRecipesBarOffersForkAndNoEdit() {
        show(save(state = RecipeState.PUBLISHED).id)
        assertTrue(inTheBar(CardTags.FORK))
        assertFalse(exists(CardTags.EDIT))
    }

    // Scenario 6: the bar collapses as the recipe scrolls; once the content's heading is out of view the bar shows the
    // title on one line, ellipsized; it opens again, with no title, as the reader scrolls back to the top.
    @Test
    fun theBarCollapsesAsTheRecipeScrollsWithTheTitleOnOneLineEllipsized() {
        val long = "Slow-roasted chicken with lemon, garlic, thyme and a crisp potato crust for a long Sunday lunch " +
            "with the whole family, the neighbours, their dog and anyone else who happens to drop by the kitchen " +
            "while it roasts slowly in the oven"
        show(save(title = long).id)
        val expanded = bounds(CardTags.BAR).height
        assertFalse("no title in the bar while the heading is in view", exists(CardTags.BAR_TITLE))
        compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() }
        compose.waitUntil(PATIENCE_MS) { bounds(CardTags.BAR).height < expanded - 1f }
        repeat(3) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
        compose.onNodeWithTag(CardTags.TITLE).assertIsNotDisplayed()
        val collapsed = bounds(CardTags.BAR)
        // Collapsed: one row, the title beside back on it.
        val back = bounds(CardTags.BACK)
        val title = bounds(CardTags.BAR_TITLE)
        assertTrue("the title on the row of back: $title, $back", title.top >= collapsed.top && title.bottom <= collapsed.bottom && title.left >= back.right)
        val layout = layoutOf(CardTags.BAR_TITLE)
        assertEquals(long, layout.layoutInput.text.text)
        assertEquals(1, layout.lineCount)
        assertTrue("the long title is longer than its one line", layout.multiParagraph.intrinsics.maxIntrinsicWidth > layout.size.width)
        assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
        assertEquals(listOf(long), compose.textsOf(CardTags.BAR_TITLE))
        // Back at the top, the bar opens again, with no title.
        repeat(6) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeDown() } }
        compose.waitUntil(PATIENCE_MS) { bounds(CardTags.BAR).height > expanded - 1f }
        assertFalse(exists(CardTags.BAR_TITLE))
    }

    // A11Y-004 with scenario 6: a title of 120 characters at twice the font scale on a narrow phone reads whole in the
    // content, on as many lines as it takes and never ellipsized; the bar shows it only once it has scrolled away.
    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun aLongTitleAtTwiceTheFontScaleReadsWholeInTheContent() {
        val long = "Slow-roasted chicken with lemon, garlic and thyme, a crisp potato crust and a green salad for Sunday lunch at home"
            .padEnd(120, '!')
        assertEquals(120, long.length)
        val recipe = save(title = long)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AppTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
            }
        }
        compose.awaitCard()
        val layout = layoutOf(CardTags.TITLE)
        assertEquals(long, layout.layoutInput.text.text)
        assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines)
        compose.assertWhole(CardTags.TITLE)
        assertEquals(long.length, layout.getLineEnd(layout.lineCount - 1))
        (0 until layout.lineCount).forEach { assertFalse("line $it is not ellipsized", layout.isLineEllipsized(it)) }
        assertFalse("no title in the bar while the heading is in view", exists(CardTags.BAR_TITLE))
        repeat(4) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
        assertEquals(listOf(long), compose.textsOf(CardTags.BAR_TITLE))
    }

    // A card opened at a place further down — a held place, a timer's step — opens with the bar collapsed and the
    // title in it.
    @Test
    fun aCardOpenedFurtherDownOpensWithTheBarCollapsedAndTheTitleInIt() {
        val recipe = save()
        val session = TextCardSession.inMemory()
        var open by mutableStateOf(true)
        compose.setContent {
            AppTheme { if (open) CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}, session = session) }
        }
        compose.awaitCard()
        val expanded = bounds(CardTags.BAR).height
        repeat(4) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
        compose.runOnIdle { open = false }
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.SCREEN) }
        compose.runOnIdle { open = true }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
        compose.waitUntil(PATIENCE_MS) { bounds(CardTags.BAR).height < expanded - 1f }
    }

    // The keyboard shrinks the content: a fixing field focused low on the card is brought above it.
    @Test
    fun aFocusedFixingFieldIsBroughtAboveTheKeyboard() {
        val recipe = Recipe.create(now, null).copy(
            title = "Stew",
            servings = 4,
            ingredients = (0 until 20).map {
                Ingredient(it, null, freeTextName = "Thing ${it + 1}", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100))
            },
        ).also { runBlocking { recipes.save(it) } }
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        show(recipe.id)
        insets(navigation = NAVIGATION)
        compose.onNodeWithTag(CardTags.ingredient(19)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX_FIELD) }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performScrollTo().performClick()
        compose.onNodeWithTag(CardTags.FIX_FIELD).assertIsFocused()
        insets(navigation = NAVIGATION, keyboard = KEYBOARD)
        val root = bounds(CardTags.SCREEN)
        compose.waitUntil(PATIENCE_MS) { bounds(CardTags.FIX_FIELD).bottom <= root.bottom - KEYBOARD }
        assertTrue("the content ends at the keyboard", bounds(CardTags.CONTENT).bottom <= root.bottom - KEYBOARD + 0.5f)
    }

    private fun insets(status: Int = 0, navigation: Int = 0, keyboard: Int = 0) {
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, status, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigation))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
                .setVisible(WindowInsetsCompat.Type.ime(), keyboard > 0)
                .build()
            compose.activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        compose.waitForIdle()
    }

    // Each of the bar's icons shows its name in a tooltip on a long press, so that its meaning is never the icon alone.
    @Test
    fun aLongPressOnABarIconShowsItsNameInATooltip() {
        show(save().id)
        mapOf(CardTags.BACK to "Back", CardTags.FORK to "Fork", CardTags.EDIT to "Edit").forEach { (tag, name) ->
            // The screen reader names the long press for what it does.
            assertEquals("Show the name", compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
            compose.onNodeWithTag(tag).performTouchInput { longClick() }
            compose.waitUntil(PATIENCE_MS) { exists(CardTags.tooltip(tag)) }
            fun texts(node: SemanticsNode): List<String> =
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::texts)
            val tooltip = compose.onNodeWithTag(CardTags.tooltip(tag), useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("the tooltip of $tag reads \"$name\"", texts(tooltip).contains(name))
            compose.waitUntil(PATIENCE_MS) { !exists(CardTags.tooltip(tag)) }
        }
        // A long press acts on nothing.
        assertEquals(listOf(0, 0, 0), listOf(backs, forks, edits))
    }

    // Scenario 7: while the recipe is still being read, nothing for a moment, then an indicator rather than a blank
    // screen; the recipe read, the indicator goes.
    @Test
    fun whileTheRecipeLoadsAnIndicatorShowsAfterAMoment() = loading("Loading the recipe")

    @Test
    @Config(qualifiers = "ru")
    fun whileTheRecipeLoadsAnIndicatorShowsAfterAMomentInRussian() = loading("Рецепт загружается")

    private fun loading(said: String) {
        val recipe = save()
        val held = CompletableDeferred<Unit>().also { gate = it }
        compose.mainClock.autoAdvance = false
        setCard(recipe.id)
        compose.mainClock.advanceTimeBy(LOADING_INDICATOR_DELAY_MS / 2)
        assertFalse("no indicator for a quick read", exists(CardTags.LOADING))
        // The bar is there all the while, with its way back.
        assertTrue(exists(CardTags.BACK))
        compose.mainClock.advanceTimeBy(LOADING_INDICATOR_DELAY_MS)
        assertTrue("the indicator once the read takes a while", exists(CardTags.LOADING))
        assertEquals(listOf(said), compose.descriptionOf(CardTags.LOADING))
        assertFalse(exists(CardTags.CONTENT))
        compose.mainClock.autoAdvance = true
        held.complete(Unit)
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.LOADING) }
        assertEquals(listOf("Roast chicken"), compose.textsOf(CardTags.TITLE))
    }

    // Insets are the scaffold's: the bar draws under the status bar with its actions below it, and the content runs
    // under the navigation bar, its end scrolling clear of it.
    @Test
    fun theBarDrawsUnderTheStatusBarAndTheContentEndsClearOfTheNavigationBar() {
        val recipe = save()
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        show(recipe.id)
        insets(status = STATUS, navigation = NAVIGATION)
        val screen = bounds(CardTags.SCREEN)
        assertEquals(0f, bounds(CardTags.BAR).top)
        assertTrue("back below the status bar", bounds(CardTags.BACK).top >= STATUS)
        assertEquals(screen.bottom, bounds(CardTags.CONTENT).bottom)
        compose.onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }
        compose.waitForIdle()
        val end = bounds(CardTags.DESCRIPTION)
        assertTrue("the end of the recipe ($end) clear of the navigation bar", end.bottom <= screen.bottom - NAVIGATION)
    }

    private companion object {
        const val STATUS = 60
        const val NAVIGATION = 90
        const val KEYBOARD = 200
    }
}
