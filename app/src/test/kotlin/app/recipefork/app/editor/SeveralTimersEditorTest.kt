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
import app.recipefork.core.recipe.Recipe
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
        compose.awaitStatement(string(R.string.editor_timer_removed, 1))
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
        compose.awaitStatement(string(R.string.editor_timer_removed, 1))
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
        compose.awaitStatement("Таймер шага 1 убран")
    }

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
        // While it holds no duration it is no timer: it has no name or removal to show.
        assertFalse(exists(EditorTags.timerName(0, 1)) || exists(EditorTags.timerRemove(0, 1)))
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
