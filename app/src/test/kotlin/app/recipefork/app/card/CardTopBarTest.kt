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
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.timer.TimerTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Timer
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import app.recipefork.app.ui.AppTheme
import androidx.compose.runtime.key
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The card's top bar (scenarios 6 and 7 of the book-and-card increment): back, the recipe's title and its actions as
 * icons in an app bar that draws under the status bar and collapses as the recipe scrolls under it; the content kept
 * clear of the navigation bar; and, while the recipe is still being read, an indicator after a moment.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    // Scenario 6: the bar holds the back arrow and, as icons named for the screen reader, edit and the ⋮; fork floats at
    // the bottom end, its icon and its word. Each does what the bar's actions did. The title is the content's heading,
    // under the bar, which repeats it only later.
    @Test
    fun theBarHoldsBackTheTitleAndTheRecipesActionsAsNamedIcons() = theBar("Back", "Fork", "Edit", "More actions")

    @Test
    @Config(qualifiers = "ru")
    fun theBarHoldsBackTheTitleAndTheRecipesActionsAsNamedIconsInRussian() = theBar("Назад", "Своя версия", "Изменить", "Другие действия")

    private fun theBar(back: String, fork: String, edit: String, menu: String) {
        show(save().id)
        listOf(CardTags.BACK, CardTags.EDIT, CardTags.MENU).forEach { assertTrue("$it in the bar", inTheBar(it)) }
        assertFalse("fork is not on the bar", inTheBar(CardTags.FORK))
        assertFalse("no title in the expanded bar", exists(CardTags.BAR_TITLE))
        assertTrue(compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.CONTENT)) and hasTestTag(CardTags.TITLE)).fetchSemanticsNodes().size == 1)
        assertEquals(listOf("Roast chicken"), compose.textsOf(CardTags.TITLE))
        assertTrue(compose.onAllNodes(hasTestTag(CardTags.TITLE) and isHeading()).fetchSemanticsNodes().size == 1)
        // Icons, not words: each is named by its content description alone.
        mapOf(CardTags.BACK to back, CardTags.EDIT to edit, CardTags.MENU to menu).forEach { (tag, name) ->
            assertEquals(listOf(name), compose.descriptionOf(tag))
            assertEquals(emptyList<String>(), compose.textsOf(tag))
            compose.assertCardTarget(tag)
        }
        val (b, e, m) = listOf(CardTags.BACK, CardTags.EDIT, CardTags.MENU).map(::bounds)
        val title = bounds(CardTags.TITLE)
        assertTrue("back at the start, edit then the ⋮ at the end", b.right <= e.left && e.right <= m.left)
        assertTrue("the title under the bar", title.top >= bounds(CardTags.BAR).bottom)
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        // Fork, floating at the bottom end, by its word, a card target, under the bar's actions.
        assertEquals(listOf(fork), compose.textsOf(CardTags.FORK))
        compose.assertCardTarget(CardTags.FORK)
        val f = bounds(CardTags.FORK)
        val screen = bounds(CardTags.SCREEN)
        assertTrue("fork at the bottom end: $f in $screen", screen.right - f.right < screen.width * 0.1f && screen.bottom - f.bottom < screen.height * 0.1f)
        compose.onNodeWithTag(CardTags.FORK).performClick()
        assertEquals(listOf(1, 1, 1), listOf(backs, forks, edits))
    }

    // Scenario 6: a frozen recipe's card offers fork, floating, and no edit, as before.
    @Test
    fun aFrozenRecipesCardOffersForkAndNoEdit() {
        show(save(state = RecipeState.PUBLISHED).id)
        assertFalse(exists(CardTags.EDIT))
        assertFalse(inTheBar(CardTags.FORK))
        compose.forkFromTheCard()
        assertEquals(1, forks)
    }

    // M3's one primary action of a scrolling screen: fork shows its word at the top, shrinks to its icon while the
    // recipe scrolls down, and shows its word again as the reader scrolls up, or at the top; it is a card target and
    // reads its word to the screen reader all the while.
    @Test
    fun forkShrinksToItsIconScrollingDownAndShowsItsWordScrollingUpAndAtTheTop() {
        show(save().id)
        assertTrue("${bounds(CardTags.FORK)} shows its word at the top", forkExpanded())
        repeat(3) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
        compose.waitUntil(PATIENCE_MS) { forkCollapsed() }
        compose.assertCardTarget(CardTags.FORK)
        assertEquals(listOf("Fork"), compose.textsOf(CardTags.FORK))
        compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeDown() }
        compose.waitUntil(PATIENCE_MS) { forkExpanded() }
        val range = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("a little way up, not at the top", range.value() > 0f)
        repeat(2) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
        compose.waitUntil(PATIENCE_MS) { forkCollapsed() }
        compose.onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -1_000_000f) }
        compose.waitUntil(PATIENCE_MS) { forkExpanded() }
    }

    /** Whether fork shows its word beside its icon: wider than tall. Shrunk to its icon it is as wide as tall. */
    private fun forkExpanded(): Boolean = bounds(CardTags.FORK).let { it.width > it.height + 8f }

    private fun forkCollapsed(): Boolean = bounds(CardTags.FORK).let { abs(it.width - it.height) < 1f }

    // View's end scrolls clear of fork: the last step, its timer's line and the description, scrolled to the end, stand
    // whole above it.
    @Test
    fun theRecipesEndScrollsClearOfFork() {
        val recipe = Recipe.create(now, null).copy(
            title = "Roast chicken",
            description = "A weeknight dinner.",
            steps = (0 until 12).map { Step(it, "Step number ${it + 1} of the roast.", timer = Timer(10.minutes).takeIf { _ -> it == 11 }) },
        ).also { runBlocking { recipes.save(it) } }
        show(recipe.id)
        compose.onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }
        compose.waitForIdle()
        val fork = bounds(CardTags.FORK)
        compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.CONTENT)) and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .maxByOrNull { it.boundsInRoot.bottom }?.let { last ->
                assertTrue("the last interactive element ${last.config.getOrNull(SemanticsProperties.TestTag)} ${last.boundsInRoot} clear above fork $fork",
                    last.boundsInRoot.bottom <= fork.top + 0.5f)
            }
        listOf(CardTags.step(11), ViewTimerTags.line(11), CardTags.DESCRIPTION).forEach { tag ->
            val part = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag $part clear above fork $fork", part.bottom <= fork.top + 0.5f && part.top >= bounds(CardTags.BAR).bottom - 0.5f)
        }
    }

    // Scenario 4 of v0.26: on a 360 dp phone at 1× font, a title of some thirty characters reads whole in the collapsed
    // bar — beside back, before the actions, on up to two lines — on an own editable recipe (back, edit, ⋮), a frozen
    // one (back) and a found one (back, ⋮); every action stays a 56 dp target named for the screen reader.
    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun aTitleOfThirtyCharactersReadsWholeInTheBarOnA360dpPhone() = wholeInTheBar("Oven chicken with new potatoes")

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun aTitleOfThirtyCharactersReadsWholeInTheBarOnA360dpPhoneInRussian() = wholeInTheBar("Курица с картофелем в духовке")

    private fun wholeInTheBar(title: String) {
        assertTrue("some thirty characters: ${title.length}", title.length in 29..30)
        val own = save(title = title)
        val frozen = save(title = title, state = RecipeState.PUBLISHED)
        val found = save(title = title, state = RecipeState.PUBLISHED)
        runBlocking { book.database.favourites().put(FavouriteEntity(found.id.value, title, now)) }
        var shown by mutableStateOf(own.id)
        compose.setContent { AppTheme { key(shown) { CardScreen(shown, recipes, environment, onBack = {}, onEdit = {}) } } }
        listOf(own to true, frozen to false, found to false).forEach { (recipe, editable) ->
            compose.runOnIdle { shown = recipe.id }
            compose.awaitCard()
            compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf(title) }
            repeat(4) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
            compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
            val actions = listOf(CardTags.EDIT, CardTags.MENU).filter(::exists)
            assertEquals("${recipe.state}: edit only while editable", editable, exists(CardTags.EDIT))
            assertFalse("fork is not on the bar", inTheBar(CardTags.FORK))
            assertTrue("fork floats", exists(CardTags.FORK))
            (listOf(CardTags.BACK) + actions).forEach { tag ->
                assertTrue("$tag in the bar", inTheBar(tag))
                compose.assertCardTarget(tag)
                assertEquals(1, compose.descriptionOf(tag).size)
            }
            // Whole: every character on at most two lines, none cut, none ellipsized, the bar its own height.
            val layout = layoutOf(CardTags.BAR_TITLE)
            assertEquals(title, layout.layoutInput.text.text)
            assertTrue("\"$title\" on ${layout.lineCount} lines", layout.lineCount <= 2)
            (0 until layout.lineCount).forEach { assertFalse("\"$title\": line $it is ellipsized", layout.isLineEllipsized(it)) }
            compose.assertWhole(CardTags.BAR_TITLE)
            val bar = bounds(CardTags.BAR)
            val text = bounds(CardTags.BAR_TITLE)
            assertTrue("the title within the bar: $text, $bar", text.top >= bar.top - 0.5f && text.bottom <= bar.bottom + 0.5f)
            assertTrue("the title after back", text.left >= bounds(CardTags.BACK).right - 0.5f)
            assertTrue("the title before the actions", text.right <= (actions.firstOrNull()?.let { bounds(it).left } ?: bar.right) + 0.5f)
        }
    }

    // Scenario 6: the bar is one row that stays as the recipe scrolls — no empty band above the cover; once the content's
    // heading is out of view the bar shows the title on its row, on up to two lines, ellipsized at the end; it loses the
    // title back at the top.
    @Test
    fun theBarShowsTheTitleOnceScrolledOnUpToTwoLinesEllipsized() {
        val long = "Slow-roasted chicken with lemon, garlic, thyme and a crisp potato crust for a long Sunday lunch " +
            "with the whole family, the neighbours, their dog and anyone else who happens to drop by the kitchen " +
            "while it roasts slowly in the oven"
        show(save(title = long).id)
        val expanded = bounds(CardTags.BAR).height
        assertFalse("no title in the bar while the heading is in view", exists(CardTags.BAR_TITLE))
        // One row: the bar is no taller than back's row and the status bar above it.
        val backRow = bounds(CardTags.BACK)
        assertTrue("one row: bar ${bounds(CardTags.BAR)}, back $backRow", bounds(CardTags.BAR).bottom - backRow.bottom < backRow.height / 2)
        repeat(4) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
        compose.onNodeWithTag(CardTags.TITLE).assertIsNotDisplayed()
        val collapsed = bounds(CardTags.BAR)
        assertEquals("the bar keeps its height as the recipe scrolls", expanded, collapsed.height, 1f)
        // The title beside back on the one row.
        val back = bounds(CardTags.BACK)
        val title = bounds(CardTags.BAR_TITLE)
        assertTrue("the title on the row of back: $title, $back", title.top >= collapsed.top && title.bottom <= collapsed.bottom && title.left >= back.right)
        val layout = layoutOf(CardTags.BAR_TITLE)
        assertEquals(long, layout.layoutInput.text.text)
        assertEquals(2, layout.layoutInput.maxLines)
        assertEquals(2, layout.lineCount)
        assertTrue("the long title is longer than its two lines", layout.multiParagraph.didExceedMaxLines)
        assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
        assertTrue("ellipsized at the end of its second line", layout.isLineEllipsized(1))
        assertEquals("the smaller title style on two lines", 16f, layout.layoutInput.style.fontSize.value, 0.01f)
        assertEquals(listOf(long), compose.textsOf(CardTags.BAR_TITLE))
        // Back at the top, the bar loses the title again.
        repeat(6) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeDown() } }
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.BAR_TITLE) }
    }

    // A short title stands in the bar's own large title style, on one line; only one that does not fit takes the
    // smaller style on two lines.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun aShortTitleStandsInTheBarsLargeStyleOnOneLine() {
        show(save().id)
        repeat(4) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
        val layout = layoutOf(CardTags.BAR_TITLE)
        assertEquals("Roast chicken", layout.layoutInput.text.text)
        assertEquals("titleLarge", 22f, layout.layoutInput.style.fontSize.value, 0.01f)
        assertEquals(1, layout.lineCount)
        assertFalse(layout.isLineEllipsized(0))
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

    // A card opened at a place further down — a held place, a timer's step — opens with the title in the bar.
    @Test
    fun aCardOpenedFurtherDownOpensWithTheTitleInTheBar() {
        val recipe = save()
        val session = TextCardSession.inMemory()
        var open by mutableStateOf(true)
        compose.setContent {
            AppTheme { if (open) CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}, session = session) }
        }
        compose.awaitCard()
        repeat(4) { compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeUp() } }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
        compose.runOnIdle { open = false }
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.SCREEN) }
        compose.runOnIdle { open = true }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.BAR_TITLE) }
    }

    // An amount is fixed on Cooking, where no fork floats to lie over the fixing control; View, which holds fork, offers
    // no fixing.
    @Test
    fun anAmountIsFixedOnCookingWhereNoForkFloats() {
        val recipe = Recipe.create(now, null).copy(
            title = "Stew",
            servings = 4,
            ingredients = listOf(Ingredient(0, null, freeTextName = "Beef", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500))),
        ).also { runBlocking { recipes.save(it) } }
        show(recipe.id)
        compose.chooseCardTab(CardMode.VIEW)
        assertTrue(exists(CardTags.FORK))
        assertFalse("View offers no fixing", exists(CardTags.fixAmount(0)))
        compose.chooseCardTab(CardMode.COOKING)
        assertFalse(exists(CardTags.FORK))
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX) }
        assertFalse(exists(CardTags.FORK))
        compose.onNodeWithTag(CardTags.FIX_CANCEL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.FIX) }
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
        compose.chooseCardTab(CardMode.COOKING)
        insets(navigation = NAVIGATION)
        compose.onNodeWithTag(CardTags.fixAmount(19), useUnmergedTree = true).performScrollTo().performClick()
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
        mapOf(CardTags.BACK to "Back", CardTags.EDIT to "Edit", CardTags.MENU to "More actions").forEach { (tag, name) ->
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
        val fork = bounds(CardTags.FORK)
        assertTrue("fork ($fork) above the navigation bar", fork.bottom <= screen.bottom - NAVIGATION)
        assertTrue("the end of the recipe ($end) clear of fork ($fork)", end.bottom <= fork.top + 0.5f)
    }

    private companion object {
        const val STATUS = 60
        const val NAVIGATION = 90
        const val KEYBOARD = 200
    }
}
