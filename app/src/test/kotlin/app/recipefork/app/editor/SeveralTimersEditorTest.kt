package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Bound
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * A step's several timers in the editor (DOM-024, FLOW-027): each with its own duration, name and removal, edited and
 * removed one by one while the step's other timers stand as they were.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SeveralTimersEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-09T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)

    /** Three timers, as a recipe file may bring them: the first and the last named, the middle one not. */
    private val three = listOf(Timer(10.minutes, "Dough"), Timer(1.hours + 20.minutes), Timer(5.minutes, "Glaze"))

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun seed(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun recipe(vararg steps: Step): Recipe = seed(Recipe.create(created, null).copy(title = "Buns", steps = steps.toList()))

    private fun open(recipe: Recipe) {
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun timers(id: RecipeId, position: Int = 0): List<Timer> = stored(id).steps[position].timers

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun minutesShown(position: Int, count: Int) = (0 until count).map { fieldText(EditorTags.timerMinutes(position, it)) }

    private fun undo() {
        compose.onNode(
            hasText(string(R.string.editor_undo)) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
            useUnmergedTree = true,
        ).performClick()
    }

    // Editing or removing one timer of a step with three leaves the step's other timers exactly as they were, and
    // every other step's timers too.
    @Test
    fun editingOrRemovingOneTimerLeavesTheOthersAsTheyWere() {
        val other = Step(1, "Bake.", timers = listOf(Timer(25.minutes, "Oven")))
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three), other)
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        fun now() = editor.recipe.value!!.steps

        assertTrue(editor.setTimerDuration(0, 1, 1, 5))
        assertEquals(listOf(three[0], Timer(1.hours + 5.minutes), three[2]), now()[0].timers)
        assertTrue(editor.setTimerLabel(0, 1, "Proof"))
        assertEquals(listOf(three[0], Timer(1.hours + 5.minutes, "Proof"), three[2]), now()[0].timers)
        // The first timer's name given back to the step; the others keep theirs.
        assertTrue(editor.setTimerLabel(0, 0, ""))
        assertEquals(listOf(Timer(10.minutes), Timer(1.hours + 5.minutes, "Proof"), three[2]), now()[0].timers)
        // Its duration changed: its name and the others stand.
        assertTrue(editor.setTimerDuration(0, 0, 0, 12))
        assertEquals(listOf(Timer(12.minutes), Timer(1.hours + 5.minutes, "Proof"), three[2]), now()[0].timers)
        // 0 h 0 min takes that one timer off, and only it.
        assertTrue(editor.setTimerDuration(0, 2, 0, 0))
        assertEquals(listOf(Timer(12.minutes), Timer(1.hours + 5.minutes, "Proof")), now()[0].timers)
        // A timer added at a place goes in there; the others keep their order.
        assertTrue(editor.addTimer(0, 1, 0, 15, "Rest"))
        assertEquals(listOf(Timer(12.minutes), Timer(15.minutes, "Rest"), Timer(1.hours + 5.minutes, "Proof")), now()[0].timers)
        // Removing one leaves the others.
        assertTrue(editor.removeTimer(0, 1))
        assertEquals(listOf(Timer(12.minutes), Timer(1.hours + 5.minutes, "Proof")), now()[0].timers)
        // No timer where the step has none.
        assertFalse(editor.removeTimer(0, 2))
        assertFalse(editor.setTimerDuration(0, 2, 0, 1))
        assertFalse(editor.setTimerLabel(0, 2, "None"))
        assertEquals(other, now()[1])
        assertEquals(now(), runBlocking { editor.written(); recipes.get(recipe.id)!!.steps })
    }

    // Removing a timer is offered for undoing, like any removal: Undo puts it back at its place, with its name.
    @Test
    fun undoingATimersRemovalPutsItBackAtItsPlace() {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three))
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        assertTrue(editor.removeTimer(0, 0))
        val removal = editor.removal.value!!
        assertEquals(Removal.Kind.TIMER, removal.kind)
        assertEquals(0, removal.position)
        assertEquals(0, removal.index)
        assertEquals(three.drop(1), editor.recipe.value!!.steps[0].timers)
        assertTrue(editor.undoRemoval(removal))
        assertEquals(three, editor.recipe.value!!.steps[0].timers)
        assertNull(editor.removal.value)
        runBlocking { editor.written() }
    }

    // A timer name's mark is its own timer's: it stays with that timer as one before it goes, by removal or by its
    // duration cleared, and goes with its own timer.
    @Test
    fun aTimerNamesMarkStaysWithItsTimer() {
        val long = "n".repeat(61)
        val recipe = recipe(Step(0, "Knead.", timers = listOf(Timer(1.minutes), Timer(2.minutes), Timer(3.minutes, long))))
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        editor.leave(RecipePart.TimerName(0, 2))
        assertEquals(setOf(RecipePart.TimerName(0, 2)), editor.marks.value.keys)
        assertTrue(editor.setTimerDuration(0, 0, 0, 0))
        assertEquals(mapOf(RecipePart.TimerName(0, 1) to Bound.OverLimit(60, 61)), editor.marks.value)
        assertTrue(editor.removeTimer(0, 0))
        assertEquals(mapOf(RecipePart.TimerName(0, 0) to Bound.OverLimit(60, 61)), editor.marks.value)
        assertTrue(editor.removeTimer(0, 0))
        assertTrue(editor.marks.value.isEmpty())
        runBlocking { editor.written() }
    }

    // Undo of a timer's removal puts back the marks of the step's timer names as they were: a later timer's moved up
    // with it, and goes back down with it.
    @Test
    fun undoingATimersRemovalPutsBackTheMarksOfItsStep() {
        val long = "n".repeat(61)
        val recipe = recipe(Step(0, "Knead.", timers = listOf(Timer(1.minutes, "A"), Timer(2.minutes, long), Timer(3.minutes, "C"))))
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        editor.leave(RecipePart.TimerName(0, 1))
        assertEquals(mapOf(RecipePart.TimerName(0, 1) to Bound.OverLimit(60, 61)), editor.marks.value)
        assertTrue(editor.removeTimer(0, 0))
        assertEquals(mapOf(RecipePart.TimerName(0, 0) to Bound.OverLimit(60, 61)), editor.marks.value)
        assertTrue(editor.undoRemoval(editor.removal.value!!))
        assertEquals(mapOf(RecipePart.TimerName(0, 1) to Bound.OverLimit(60, 61)), editor.marks.value)
        // Removed itself, its mark goes; undone, it comes back with it.
        assertTrue(editor.removeTimer(0, 1))
        assertTrue(editor.marks.value.isEmpty())
        assertTrue(editor.undoRemoval(editor.removal.value!!))
        assertEquals(mapOf(RecipePart.TimerName(0, 1) to Bound.OverLimit(60, 61)), editor.marks.value)
        assertEquals(recipe.steps, editor.recipe.value!!.steps)
        runBlocking { editor.written() }
    }

    // A timer with no duration yet — added, or cleared — has a removal that takes only it off the screen; it counts
    // among the five while shown, and taken off, leaves room for another.
    @Test
    fun aTimerWithNoDurationCanBeTakenOffAndLeavesRoom() {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        repeat(2) { compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performClick() }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 4)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerStatement(0)), PATIENCE_MS)
        assertFalse(exists(EditorTags.timerMinutes(0, 5)))

        // Taken off: nothing stored changes, nothing is offered for undoing, and there is room for one more.
        assertTrue(exists(EditorTags.timerRemove(0, 4)))
        compose.onNodeWithTag(EditorTags.timerRemove(0, 4)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.timerMinutes(0, 4)) }
        assertFalse(exists(EditorTags.STATEMENT))
        assertEquals(three, timers(recipe.id))
        compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 4)), PATIENCE_MS)
        assertFalse(exists(EditorTags.timerStatement(0)))

        // A timer cleared to no duration is taken off the same way; the others stand.
        type(EditorTags.timerHours(0, 1), "")
        type(EditorTags.timerMinutes(0, 1), "")
        assertEquals(listOf(three[0], three[2]), timers(recipe.id))
        compose.onNodeWithTag(EditorTags.timerRemove(0, 1)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.timerMinutes(0, 4)) }
        assertEquals(listOf("10", "5", "", ""), minutesShown(0, 4))
        assertFalse(exists(EditorTags.STATEMENT))
        assertEquals(listOf(three[0], three[2]), timers(recipe.id))
        // The empty rows filled in, they go in after the others.
        type(EditorTags.timerMinutes(0, 3), "9")
        assertEquals(listOf(three[0], three[2], Timer(9.minutes)), timers(recipe.id))
    }

    // A step moved carries all its timers, names and all, on screen and stored.
    @Test
    fun aMovedStepCarriesAllItsTimers() {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three), Step(1, "Bake.", timers = listOf(Timer(25.minutes))))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepMenu(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepAction(0, RowAction.MOVE_DOWN)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepAction(0, RowAction.MOVE_DOWN)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { stored(recipe.id).steps.map { it.text } == listOf("Bake.", "Knead, prove and glaze.") }
        assertEquals(listOf(listOf(Timer(25.minutes)), three), stored(recipe.id).steps.map { it.timers })
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(1, 2)), PATIENCE_MS)
        assertEquals(listOf("25"), minutesShown(0, 1))
        assertFalse(exists(EditorTags.timerMinutes(0, 1)))
        assertEquals(listOf("10", "20", "5"), minutesShown(1, 3))
        assertEquals(listOf("Dough", "Knead, prove and glaze.", "Glaze"), (0..2).map { fieldText(EditorTags.timerName(1, it)) })
    }

    // A step removed with its timers, and put back by Undo, comes back with every one of them, names and all.
    @Test
    fun aRemovedStepComesBackWithAllItsTimers() {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three), Step(1, "Bake.", timers = listOf(Timer(25.minutes))))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepRemove(0)).performScrollTo().performClick()
        compose.awaitStatement(string(R.string.editor_step_removed, 1))
        compose.waitUntil(PATIENCE_MS) { stored(recipe.id).steps == listOf(Step(0, "Bake.", timers = listOf(Timer(25.minutes)))) }
        assertEquals(listOf("25"), minutesShown(0, 1))

        undo()
        compose.waitUntil(PATIENCE_MS) { stored(recipe.id).steps == recipe.steps }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        assertEquals(listOf("10", "20", "5"), minutesShown(0, 3))
        assertEquals(listOf("Dough", "Knead, prove and glaze.", "Glaze"), (0..2).map { fieldText(EditorTags.timerName(0, it)) })
        assertEquals(listOf("25"), minutesShown(1, 1))
    }

    // GUIDE-006 with several timers: once the step is left, what the notification will say shows under each unnamed
    // timer, whose own "Name the timer" takes the author to its own name.
    @Test
    fun theNotificationShowsUnderEachUnnamedTimer() {
        val longStep = "Put the chicken in the oven at 190 degrees and roast until the skin is golden and crisp"
        val recipe = recipe(Step(0, longStep, timers = listOf(Timer(40.minutes), Timer(5.minutes, "Rest"), Timer(10.minutes))))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        assertFalse((0..2).any { exists(EditorTags.timerNotification(0, it)) })
        compose.onNodeWithTag(EditorTags.stepText(0)).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.timerNotification(0, 0)) && exists(EditorTags.timerNotification(0, 2)) }
        assertFalse(exists(EditorTags.timerNotification(0, 1)))
        val said = string(R.string.editor_timer_notification, "Put the chicken in the oven at 190 degrees and roast until…")
        listOf(0, 2).forEach { assertEquals(listOf(said), compose.textsOf(EditorTags.timerNotificationText(0, it))) }
        assertEquals(
            string(R.string.editor_timer_notification_name_numbered_action, 1, 3),
            AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(EditorTags.timerNotificationName(0, 2)).fetchSemanticsNode()),
        )

        compose.onNodeWithTag(EditorTags.timerNotificationName(0, 2)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(EditorTags.timerName(0, 2)).fetchSemanticsNode().config[SemanticsProperties.Focused]
        }
        compose.onNodeWithTag(EditorTags.timerName(0, 2)).performTextReplacement("Chicken")
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.timerNotification(0, 2)) }
        assertTrue(exists(EditorTags.timerNotification(0, 0)))
        assertEquals(listOf(Timer(40.minutes), Timer(5.minutes, "Rest"), Timer(10.minutes, "Chicken")), timers(recipe.id))
    }

    // Each timer has its own duration fields, its own name — following the step's text until the author types one —
    // and its own removal; editing the first leaves the others as they were (a file brought them all).
    @Test
    fun eachTimerHasItsOwnFieldsAndEditingTheFirstKeepsTheOthers() {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        assertEquals(listOf("0", "1", "0"), (0..2).map { fieldText(EditorTags.timerHours(0, it)) })
        assertEquals(listOf("10", "20", "5"), minutesShown(0, 3))
        assertEquals(listOf("Dough", "Knead, prove and glaze.", "Glaze"), (0..2).map { fieldText(EditorTags.timerName(0, it)) })
        assertEquals(listOf(false, true, false), (0..2).map { exists(EditorTags.timerNameFollows(0, it)) })
        assertTrue((0..2).all { exists(EditorTags.timerRemove(0, it)) })
        assertFalse(exists(EditorTags.timerMinutes(0, 3)))

        // The unnamed timer's name follows the step; the named ones keep theirs.
        type(EditorTags.stepText(0), "Prove the buns.")
        assertEquals(listOf("Dough", "Prove the buns.", "Glaze"), (0..2).map { fieldText(EditorTags.timerName(0, it)) })
        assertEquals(three, timers(recipe.id))

        // The first timer's duration and name edited: the second and third stand exactly as they were.
        type(EditorTags.timerMinutes(0, 0), "15")
        assertEquals(listOf(Timer(15.minutes, "Dough")) + three.drop(1), timers(recipe.id))
        type(EditorTags.timerName(0, 0), "Kneaded dough")
        assertEquals(listOf(Timer(15.minutes, "Kneaded dough")) + three.drop(1), timers(recipe.id))
        // The second named by hand: it stops following the step.
        type(EditorTags.timerName(0, 1), "Proof")
        assertEquals(listOf(Timer(15.minutes, "Kneaded dough"), Timer(1.hours + 20.minutes, "Proof"), three[2]), timers(recipe.id))
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.timerNameFollows(0, 1)) }
        type(EditorTags.timerHours(0, 2), "1")
        assertEquals(
            listOf(Timer(15.minutes, "Kneaded dough"), Timer(1.hours + 20.minutes, "Proof"), Timer(1.hours + 5.minutes, "Glaze")),
            timers(recipe.id),
        )
    }

    // Removing one timer leaves the others, on screen and stored; the statement offers Undo, which puts it back at
    // its place, its fields and name with it.
    @Test
    fun removingOneTimerLeavesTheOthersAndUndoPutsItBack() {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerRemove(0, 1)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerRemove(0, 1)).performScrollTo().performClick()
        compose.awaitStatement(string(R.string.editor_timer_removed_numbered, 1, 2))
        assertEquals("Timer 2 removed from step 1", string(R.string.editor_timer_removed_numbered, 1, 2))
        compose.waitUntil(PATIENCE_MS) { timers(recipe.id) == listOf(three[0], three[2]) }
        assertEquals(listOf("10", "5"), minutesShown(0, 2))
        assertEquals(listOf("Dough", "Glaze"), (0..1).map { fieldText(EditorTags.timerName(0, it)) })
        assertFalse(exists(EditorTags.timerMinutes(0, 2)))

        undo()
        compose.waitUntil(PATIENCE_MS) { timers(recipe.id) == three }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        assertEquals(listOf("10", "20", "5"), minutesShown(0, 3))
        assertEquals(listOf("Dough", "Knead, prove and glaze.", "Glaze"), (0..2).map { fieldText(EditorTags.timerName(0, it)) })

        // Removed again and left to go: the others stay, and the removed one stays off.
        compose.onNodeWithTag(EditorTags.timerRemove(0, 0)).performScrollTo().performClick()
        compose.awaitStatement(string(R.string.editor_timer_removed_numbered, 1, 1))
        compose.swipeAwayStatement()
        assertEquals(three.drop(1), timers(recipe.id))
        assertEquals(listOf("20", "5"), minutesShown(0, 2))
    }

    @Test
    @Config(qualifiers = "ru")
    fun aTimersRemovalIsSaidInRussian() {
        val recipe = recipe(Step(0, "Замесить.", timers = three))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerRemove(0, 2)), PATIENCE_MS)
        assertEquals("Убрать таймер 3 шага 1", AccessibilityChecks.announcedLabel(compose.onNodeWithTag(EditorTags.timerRemove(0, 2)).fetchSemanticsNode()))
        compose.onNodeWithTag(EditorTags.timerRemove(0, 2)).performScrollTo().performClick()
        compose.awaitStatement("Таймер 3 шага 1 убран")
    }

    // Scenario 6 of v0.26: when a step's block shows several timer rows, each is numbered by its place in the block,
    // top to bottom, whether or not it holds a duration — its removal, its name — and the notice of its removal says
    // the number its removal showed, though the step may carry fewer timers than it shows rows.

    // Rows [A, empty, C]: timers 1, 2 and 3; removing C says timer 3.
    @Test
    fun withAnEmptyRowBetweenEachRowIsNumberedByItsPlace() = emptyBetween(
        removes = listOf("Remove timer 1 from step 1", "Remove timer 2 from step 1", "Remove timer 3 from step 1"),
        names = listOf("Timer 1's name", "Timer 3's name"),
        said = "Timer 3 removed from step 1",
    )

    @Test
    @Config(qualifiers = "ru")
    fun withAnEmptyRowBetweenEachRowIsNumberedByItsPlaceInRussian() = emptyBetween(
        removes = listOf("Убрать таймер 1 шага 1", "Убрать таймер 2 шага 1", "Убрать таймер 3 шага 1"),
        names = listOf("Название таймера 1", "Название таймера 3"),
        said = "Таймер 3 шага 1 убран",
    )

    // Rows [empty, A], the step carrying A alone: A is timer 2, and its removal says so.
    @Test
    fun withAnEmptyRowAboveTheOnlyTimerItIsTimer2() = emptyAbove(
        removes = listOf("Remove timer 1 from step 1", "Remove timer 2 from step 1"),
        name = "Timer 2's name",
        said = "Timer 2 removed from step 1",
    )

    @Test
    @Config(qualifiers = "ru")
    fun withAnEmptyRowAboveTheOnlyTimerItIsTimer2InRussian() = emptyAbove(
        removes = listOf("Убрать таймер 1 шага 1", "Убрать таймер 2 шага 1"),
        name = "Название таймера 2",
        said = "Таймер 2 шага 1 убран",
    )

    // Rows [A, empty], the step carrying A alone: A is timer 1, and its removal says so.
    @Test
    fun withAnEmptyRowBelowTheOnlyTimerItIsTimer1() = emptyBelow(
        removes = listOf("Remove timer 1 from step 1", "Remove timer 2 from step 1"),
        name = "Timer 1's name",
        said = "Timer 1 removed from step 1",
    )

    @Test
    @Config(qualifiers = "ru")
    fun withAnEmptyRowBelowTheOnlyTimerItIsTimer1InRussian() = emptyBelow(
        removes = listOf("Убрать таймер 1 шага 1", "Убрать таймер 2 шага 1"),
        name = "Название таймера 1",
        said = "Таймер 1 шага 1 убран",
    )

    private fun emptyBetween(removes: List<String>, names: List<String>, said: String) {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        empty(1)
        compose.waitUntil(PATIENCE_MS) { timers(recipe.id) == listOf(three[0], three[2]) }
        assertNumbered(removes, mapOf(0 to names[0], 2 to names[1]))
        removeAndHear(2, said)
        compose.waitUntil(PATIENCE_MS) { timers(recipe.id) == listOf(three[0]) }
    }

    private fun emptyAbove(removes: List<String>, name: String, said: String) {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three.take(2)))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 1)), PATIENCE_MS)
        empty(0)
        compose.waitUntil(PATIENCE_MS) { timers(recipe.id) == listOf(three[1]) }
        assertNumbered(removes, mapOf(1 to name))
        removeAndHear(1, said)
        compose.waitUntil(PATIENCE_MS) { timers(recipe.id).isEmpty() }
    }

    private fun emptyBelow(removes: List<String>, name: String, said: String) {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three.take(1)))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 1)), PATIENCE_MS)
        assertEquals(three.take(1), timers(recipe.id))
        assertNumbered(removes, mapOf(0 to name))
        removeAndHear(0, said)
        compose.waitUntil(PATIENCE_MS) { timers(recipe.id).isEmpty() }
    }

    /** Empties the duration of the timer row at [place]: it stays on screen, the step no longer carrying it. */
    private fun empty(place: Int) {
        type(EditorTags.timerHours(0, place), "")
        type(EditorTags.timerMinutes(0, place), "")
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerName(0, place)), PATIENCE_MS)
    }

    /** Each row's removal reads as in [removes], top to bottom, and the rows holding a timer are named as in [names]. */
    private fun assertNumbered(removes: List<String>, names: Map<Int, String>) {
        fun label(tag: String) = AccessibilityChecks.announcedLabel(compose.onNodeWithTag(tag).fetchSemanticsNode())
        assertEquals(removes, removes.indices.map { label(EditorTags.timerRemove(0, it)) })
        assertFalse(exists(EditorTags.timerRemove(0, removes.size)))
        names.forEach { (place, name) -> assertEquals(name, fieldLabel(EditorTags.timerName(0, place))) }
        AccessibilityChecks.assertDistinctLabels(compose)
    }

    /** Removes the timer row at [place]; the notice of its removal says [said]. */
    private fun removeAndHear(place: Int, said: String) {
        compose.onNodeWithTag(EditorTags.timerRemove(0, place)).performScrollTo().performClick()
        compose.awaitStatement(said)
    }

    /** The label of the text field tagged [tag]. */
    private fun fieldLabel(tag: String): String = compose.textsOf(tag).first()

    // A timer whose duration is cleared stays on screen where it stood, holding its name; entered again, it goes
    // back among the step's timers at that place.
    @Test
    fun aClearedTimerComesBackAtItsPlace() {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        type(EditorTags.timerName(0, 1), "Proof")
        type(EditorTags.timerHours(0, 1), "")
        type(EditorTags.timerMinutes(0, 1), "")
        assertEquals(listOf(three[0], three[2]), timers(recipe.id))
        assertEquals(listOf("10", "", "5"), minutesShown(0, 3))
        // While it holds no duration it is no timer: it has no name to show, only the removal that takes it off the screen.
        assertFalse(exists(EditorTags.timerName(0, 1)))
        assertTrue(exists(EditorTags.timerRemove(0, 1)))
        assertEquals("Glaze", fieldText(EditorTags.timerName(0, 2)))

        type(EditorTags.timerMinutes(0, 1), "45")
        assertEquals(listOf(three[0], Timer(45.minutes, "Proof"), three[2]), timers(recipe.id))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0, 1)), PATIENCE_MS)
        assertEquals("Proof", fieldText(EditorTags.timerName(0, 1)))
    }

    // A timer added under others holds no duration until one is entered, and then goes in after them.
    @Test
    fun aTimerAddedUnderOthersGoesInAfterThem() {
        val recipe = recipe(Step(0, "Knead, prove and glaze.", timers = three.take(2)))
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 1)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        assertEquals("", fieldText(EditorTags.timerMinutes(0, 2)))
        assertEquals(three.take(2), timers(recipe.id))
        type(EditorTags.timerMinutes(0, 2), "5")
        assertEquals(three.take(2) + Timer(5.minutes), timers(recipe.id))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0, 2)), PATIENCE_MS)
        assertEquals("Knead, prove and glaze.", fieldText(EditorTags.timerName(0, 2)))
    }

    // A frozen recipe shows every timer of a step, each with its duration and name, and nothing to change them with;
    // a step's only timer reads as it always did.
    @Test
    fun aFrozenRecipeShowsEveryTimerReadOnly() {
        val recipe = seed(
            Recipe.create(created, null).copy(
                title = "Buns",
                steps = listOf(Step(0, "Knead, prove and glaze.", timers = three), Step(1, "Bake.", timers = listOf(Timer(25.minutes)))),
                sent = SentStatus.SENT,
            ).inState(RecipeState.PUBLISHED),
        )
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(1)), PATIENCE_MS)
        assertEquals(
            listOf(
                "Step 1", "Knead, prove and glaze.",
                "Timer 1: 10 min", "Timer 1's name: Dough",
                "Timer 2: 1 h 20 min", "Timer 2's name: Knead, prove and glaze.",
                "Timer 3: 5 min", "Timer 3's name: Glaze",
            ),
            compose.textsOf(EditorTags.step(0)),
        )
        assertEquals(listOf("Step 2", "Bake.", "Timer: 25 min", "Timer's name: Bake."), compose.textsOf(EditorTags.step(1)))
        assertEquals(0, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).fetchSemanticsNodes().size)
        (0..2).forEach { assertFalse(exists(EditorTags.timerRemove(0, it))) }
        assertFalse(exists(EditorTags.timerAttach(0)))
        assertEquals(recipe, stored(recipe.id))
    }

    @Test
    @Config(qualifiers = "ru")
    fun aFrozenRecipesTimersReadInRussian() {
        val recipe = seed(
            Recipe.create(created, null).copy(
                title = "Булочки",
                steps = listOf(Step(0, "Замесить.", timers = three.take(2))),
                sent = SentStatus.SENT,
            ).inState(RecipeState.PUBLISHED),
        )
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(0)), PATIENCE_MS)
        assertEquals(
            listOf("Шаг 1", "Замесить.", "Таймер 1: 10 мин", "Название таймера 1: Dough", "Таймер 2: 1 ч 20 мин", "Название таймера 2: Замесить."),
            compose.textsOf(EditorTags.step(0)),
        )
    }
}
