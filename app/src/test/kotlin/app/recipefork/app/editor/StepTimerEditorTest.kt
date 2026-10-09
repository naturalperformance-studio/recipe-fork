package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.backThroughTheCard
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
import app.recipefork.core.timer.TimerName
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

/** A step's timer in the editor (FLOW-027, GUIDE-007), and the name it will announce itself by (FLOW-016). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class StepTimerEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-02T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)
    private var shown = false

    private val longStep = "Put the chicken in the oven at 190 degrees and roast until the skin is golden and crisp"

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

    private fun show() {
        if (shown) return
        shown = true
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun writeANewRecipe(): RecipeId {
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    /** Opens the editor directly on the recipe [id], as a test fixture: the application routes there through the card. */
    private fun openDirectly(id: RecipeId) {
        show()
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun seed(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun fieldText(tag: String): String = node(tag).config[SemanticsProperties.EditableText].text

    private fun fieldLabel(tag: String): String = compose.textsOf(tag).first()

    private fun hasSetTextAction() = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    private fun addStep(text: String): Int {
        val position = compose.onAllNodes(hasTestTagMatching(Regex("editor-step-\\d+"))).fetchSemanticsNodes().size
        compose.onNodeWithTag(EditorTags.STEP_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(position)), PATIENCE_MS)
        type(EditorTags.stepText(position), text)
        return position
    }

    private fun hasTestTagMatching(pattern: Regex) = SemanticsMatcher("tag matches $pattern") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.matches(pattern) == true
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    /** Chooses the step's timer control with a touch, as the author's finger does. */
    private fun chooseTimer(position: Int) {
        compose.onNodeWithTag(EditorTags.timerAttach(position)).performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
    }

    /** The fields the author can type into inside the step at [position]. */
    private fun fieldsIn(position: Int): Set<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(EditorTags.step(position))) and hasSetTextAction()).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }.toSet()

    private fun timerOf(id: RecipeId, position: Int = 0): Timer? = stored(id).steps[position].timer

    // FLOW-027 (1, 4, 12, 14): every editable step offers a timer, whose duration — hours and whole minutes, up to
    // 99 h 59 min — is the only thing asked for.
    @Test
    fun eachStepOffersATimerOfHoursAndWholeMinutesUpTo99h59() {
        val id = writeANewRecipe()
        addStep("Boil the water.")
        addStep(longStep)
        listOf(0, 1).forEach { position ->
            compose.onNodeWithTag(EditorTags.timerAttach(position)).assertExists()
            assertEquals(string(R.string.editor_timer_action, position + 1), AccessibilityChecks.clickActionLabel(node(EditorTags.timerAttach(position))))
        }
        assertEquals(setOf(EditorTags.stepText(1)), fieldsIn(1))

        chooseTimer(1)
        // Hours and minutes, and nothing else: no seconds, and no name asked for.
        assertEquals(setOf(EditorTags.stepText(1), EditorTags.timerHours(1), EditorTags.timerMinutes(1)), fieldsIn(1))
        assertEquals("Hours", fieldLabel(EditorTags.timerHours(1)))
        assertEquals("Minutes", fieldLabel(EditorTags.timerMinutes(1)))
        assertNull(timerOf(id, 1))

        type(EditorTags.timerMinutes(1), "20")
        assertEquals(Timer(20.minutes), timerOf(id, 1))
        type(EditorTags.timerHours(1), "1")
        assertEquals(Timer(1.hours + 20.minutes), timerOf(id, 1))
        assertNull(timerOf(id, 0))

        // The longest timer a recipe carries.
        type(EditorTags.timerHours(1), "99")
        type(EditorTags.timerMinutes(1), "59")
        assertEquals(Timer(99.hours + 59.minutes), timerOf(id, 1))
        // The field cannot hold 100 hours, 60 minutes, or anything but whole numbers.
        listOf("100", "999").forEach { type(EditorTags.timerHours(1), it) }
        listOf("60", "99", "100").forEach { type(EditorTags.timerMinutes(1), it) }
        listOf("1.5", "-1", "1,5", "a").forEach {
            type(EditorTags.timerHours(1), it)
            type(EditorTags.timerMinutes(1), it)
        }
        assertEquals("99", fieldText(EditorTags.timerHours(1)))
        assertEquals("59", fieldText(EditorTags.timerMinutes(1)))
        assertEquals(Timer(99.hours + 59.minutes), timerOf(id, 1))
        // Typed character by character, the third digit is not taken either.
        compose.onNodeWithTag(EditorTags.timerHours(1)).performTextInput("9")
        compose.waitForIdle()
        assertEquals("99", fieldText(EditorTags.timerHours(1)))
        assertEquals(Timer(99.hours + 59.minutes), timerOf(id, 1))
    }

    // The editor itself holds nothing beyond 99 h 59 min.
    @Test
    fun theEditorHoldsNoDurationBeyond99h59() {
        val recipe = seed(Recipe.create(created, null).copy(steps = listOf(Step(0, "Ferment."))))
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        assertFalse(editor.addTimer(0, 0, 100, 0))
        assertFalse(editor.addTimer(0, 0, 0, 60))
        assertFalse(editor.addTimer(0, 0, -1, 30))
        assertNull(editor.recipe.value!!.steps[0].timer)
        assertTrue(editor.addTimer(0, 0, 99, 59))
        assertFalse(editor.setTimerDuration(0, 0, 100, 0))
        assertFalse(editor.setTimerDuration(0, 0, 0, 60))
        assertEquals(Timer(99.hours + 59.minutes), editor.recipe.value!!.steps[0].timer)
        // Another editor reads once every write of this one is in the book.
        val read = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { read.load() }
        assertEquals(Timer(99.hours + 59.minutes), read.recipe.value!!.steps[0].timer)
    }

    // FLOW-027 (11, 13): no duration, or 0 h 0 min, is no timer; nothing is refused.
    @Test
    fun noDurationOrZeroStoresNoTimerAndRefusesNothing() {
        val id = writeANewRecipe()
        addStep("Rest the dough.")
        chooseTimer(0)
        assertNull(timerOf(id))
        type(EditorTags.timerHours(0), "0")
        type(EditorTags.timerMinutes(0), "0")
        assertNull(timerOf(id))
        assertEquals("0", fieldText(EditorTags.timerHours(0)))
        assertEquals("0", fieldText(EditorTags.timerMinutes(0)))
        // Nothing is refused: no message, and the step and the recipe stand as they are.
        compose.onNodeWithTag(EditorTags.timerStatement(0)).assertDoesNotExist()
        assertEquals("Rest the dough.", stored(id).steps.single().text)

        // A timer whose duration is cleared is no longer stored; the fields stay while the author is at them.
        type(EditorTags.timerMinutes(0), "30")
        assertEquals(Timer(30.minutes), timerOf(id))
        type(EditorTags.timerMinutes(0), "")
        assertNull(timerOf(id))
        compose.onNodeWithTag(EditorTags.timerMinutes(0)).assertExists()

        // Left and opened again: no timer, and only the control that attaches one.
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)
        assertNull(timerOf(id))
        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerAttach(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerHours(0)).assertDoesNotExist()
        assertEquals(setOf(EditorTags.stepText(0)), fieldsIn(0))
    }

    // FLOW-027 (2, 3), GUIDE-007: the control stays on a step with timers and adds another each time, up to five;
    // choosing it on a step showing five creates no sixth and says that a step holds up to five timers and further
    // waits are further steps. The author's next action ends it.
    @Test
    fun aSixthTimerOnAStepIsNotCreatedAndTheStepSaysWhy() {
        val five = (1..5).map { Timer(it.minutes) }
        val recipe = seed(
            Recipe.create(created, null).copy(
                title = "Roast",
                steps = listOf(Step(0, "Preheat the oven."), Step(1, "Roast the chicken.", timers = five)),
            ),
        )
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(1, 4)), PATIENCE_MS)
        assertEquals((1..5).map { "$it" }, (0..4).map { fieldText(EditorTags.timerMinutes(1, it)) })

        chooseTimer(1)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerStatement(1)), PATIENCE_MS)
        val statement = string(R.string.editor_timer_limit)
        assertEquals(listOf(statement), compose.textsOf(EditorTags.timerStatement(1)))
        assertTrue(statement.startsWith("A step can hold up to five timers") && statement.contains("split this step"))
        // No sixth timer, and nothing about the step or its timers changed.
        compose.onNodeWithTag(EditorTags.timerHours(1, 5)).assertDoesNotExist()
        assertEquals(recipe, stored(recipe.id))
        compose.onNodeWithTag(EditorTags.timerAttach(1)).assertExists()
        // Choosing it again changes nothing either.
        chooseTimer(1)
        compose.onNodeWithTag(EditorTags.timerStatement(1)).assertExists()
        compose.onNodeWithTag(EditorTags.timerHours(1, 5)).assertDoesNotExist()
        assertEquals(recipe, stored(recipe.id))

        // A scroll is not the next action: the statement stays.
        compose.onNodeWithTag(EditorTags.FORM).performTouchInput { swipeUp() }
        compose.onNodeWithTag(EditorTags.FORM).performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTags.timerStatement(1)).assertExists()

        // A touch on another field is: the statement goes, and the step and its timers stand.
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTouchInput { click() }
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerStatement(1)), PATIENCE_MS)
        assertEquals(recipe, stored(recipe.id))

        // So is a typed character.
        chooseTimer(1)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerStatement(1)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepText(1)).performTextInput(" Baste.")
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerStatement(1)), PATIENCE_MS)
        assertEquals(five, stored(recipe.id).steps[1].timers)

        // On a step with no timer, adding one shows nothing.
        chooseTimer(0)
        compose.onNodeWithTag(EditorTags.timerStatement(0)).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.timerHours(0)).assertExists()
    }

    // FLOW-027 (2, 3): "Add timer" adds a timer each time it is chosen, each with its own duration, until five; the
    // sixth adds nothing and says why — timers added but holding no duration yet count among the five.
    @Test
    fun addTimerAddsAnotherTimerUntilFive() {
        val id = writeANewRecipe()
        addStep("Bake the bread.")
        (0..4).forEach { place ->
            chooseTimer(0)
            compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, place)), PATIENCE_MS)
            compose.onNodeWithTag(EditorTags.timerStatement(0)).assertDoesNotExist()
            if (place < 4) type(EditorTags.timerMinutes(0, place), "${place + 1}0")
        }
        assertEquals((1..4).map { Timer((it * 10).minutes) }, stored(id).steps[0].timers)
        // Four timers and one added with no duration yet: a sixth is not offered.
        chooseTimer(0)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerStatement(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerMinutes(0, 5)).assertDoesNotExist()
        type(EditorTags.timerHours(0, 4), "1")
        assertEquals((1..4).map { Timer((it * 10).minutes) } + Timer(1.hours), stored(id).steps[0].timers)
        chooseTimer(0)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerStatement(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerMinutes(0, 5)).assertDoesNotExist()
        assertEquals(5, stored(id).steps[0].timers.size)
    }

    // The editor itself adds no sixth timer, and counts those shown with no duration yet.
    @Test
    fun theEditorAddsNoSixthTimer() {
        val recipe = seed(Recipe.create(created, null).copy(steps = listOf(Step(0, "Ferment.", timers = (1..4).map { Timer(it.hours) }))))
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        assertFalse(editor.attachTimer(0))
        assertNull(editor.statement.value)
        assertTrue(editor.attachTimer(0, unheld = 1))
        assertEquals(0, editor.statement.value)
        assertTrue(editor.addTimer(0, 4, 0, 5))
        assertTrue(editor.attachTimer(0))
        assertFalse(editor.addTimer(0, 5, 0, 6))
        assertFalse(editor.addTimer(0, 0, 0, 6))
        assertEquals((1..4).map { Timer(it.hours) } + Timer(5.minutes), editor.recipe.value!!.steps[0].timers)
        runBlocking { editor.written() }
    }

    @Test
    @Config(qualifiers = "ru")
    fun theStatementOfFiveTimersPerStepIsInRussian() {
        val recipe = seed(Recipe.create(created, null).copy(steps = listOf(Step(0, "Томить.", timers = (1..5).map { Timer(it.hours) }))))
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerAttach(0)), PATIENCE_MS)
        assertEquals("Таймер для шага 1", compose.descriptionOf(EditorTags.timerAttach(0)).single())
        chooseTimer(0)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerStatement(0)), PATIENCE_MS)
        val statement = compose.textsOf(EditorTags.timerStatement(0)).single()
        assertTrue(statement, statement.contains("до пяти таймеров") && statement.contains("разделите этот шаг"))
        assertEquals(recipe, stored(recipe.id))
    }

    // FLOW-027 (5–7), FLOW-016 (6, 7): the name the timer will announce is shown, derived from the step and
    // following it; a correction stays as written; clearing it gives the name back to the step.
    @Test
    fun theTimerShowsItsDerivedNameAndTheAuthorCanCorrectAndClearIt() {
        val id = writeANewRecipe()
        addStep(longStep)
        chooseTimer(0)
        compose.onNodeWithTag(EditorTags.timerName(0)).assertDoesNotExist()
        type(EditorTags.timerMinutes(0), "45")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0)), PATIENCE_MS)
        assertEquals(TimerName.derived(longStep), fieldText(EditorTags.timerName(0)))
        assertEquals("Put the chicken in the oven at 190 degrees and roast until…", fieldText(EditorTags.timerName(0)))
        compose.onNodeWithTag(EditorTags.timerNameFollows(0), useUnmergedTree = true).assertExists()
        assertNull(timerOf(id)!!.label)

        // Unnamed, it follows the step's text.
        type(EditorTags.stepText(0), "Roast the chicken.")
        assertEquals("Roast the chicken.", fieldText(EditorTags.timerName(0)))
        assertNull(timerOf(id)!!.label)

        // Corrected, it holds what the author wrote, whatever the step says afterwards.
        type(EditorTags.timerName(0), "Chicken in the oven")
        assertEquals(Timer(45.minutes, "Chicken in the oven"), timerOf(id))
        compose.onNodeWithTag(EditorTags.timerNameFollows(0), useUnmergedTree = true).assertDoesNotExist()
        type(EditorTags.stepText(0), "Roast the chicken, then rest it.")
        assertEquals("Chicken in the oven", fieldText(EditorTags.timerName(0)))
        assertEquals(Timer(45.minutes, "Chicken in the oven"), timerOf(id))
        // Changing the duration keeps the name.
        type(EditorTags.timerHours(0), "1")
        assertEquals(Timer(1.hours + 45.minutes, "Chicken in the oven"), timerOf(id))

        // Cleared, the name follows the step again, and nothing of what was typed is kept.
        compose.onNodeWithTag(EditorTags.timerName(0)).performScrollTo().performTouchInput { click() }
        compose.onNodeWithTag(EditorTags.timerName(0)).performTextClearance()
        compose.waitForIdle()
        assertEquals(Timer(1.hours + 45.minutes), timerOf(id))
        // While the author is still in the field it stays empty; once they leave it, it shows the step's name.
        assertEquals("", fieldText(EditorTags.timerName(0)))
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        assertEquals("Roast the chicken, then rest it.", fieldText(EditorTags.timerName(0)))
        type(EditorTags.stepText(0), "Roast.")
        assertEquals("Roast.", fieldText(EditorTags.timerName(0)))
        assertNull(timerOf(id)!!.label)
    }

    // A name the author gives is kept when the duration is cleared and entered again while they are at it.
    @Test
    fun aGivenNameComesBackWhenTheDurationIsEnteredAgain() {
        val id = writeANewRecipe()
        addStep("Simmer the stock.")
        chooseTimer(0)
        type(EditorTags.timerMinutes(0), "30")
        type(EditorTags.timerName(0), "Stock")
        type(EditorTags.timerMinutes(0), "")
        assertNull(timerOf(id))
        type(EditorTags.timerMinutes(0), "40")
        assertEquals(Timer(40.minutes, "Stock"), timerOf(id))
    }

    // A name over 60 characters is kept in full, and nothing refuses it while it is typed.
    @Test
    fun aNameOver60CharactersIsKeptInFull() {
        val id = writeANewRecipe()
        addStep("Bake.")
        chooseTimer(0)
        type(EditorTags.timerHours(0), "2")
        val name = "The bread in the oven on the lowest shelf, with a tray of water beneath it for steam"
        assertTrue(name.length > 60)
        type(EditorTags.timerName(0), name)
        assertEquals(Timer(2.hours, name), timerOf(id))
        assertEquals(name, fieldText(EditorTags.timerName(0)))
        compose.onNodeWithTag(EditorTags.timerName(0)).performTextInput(" and more")
        compose.waitForIdle()
        assertEquals("$name and more", timerOf(id)!!.label)
    }

    // Deleting a step deletes its timer; the timers of the steps after it move up with them, on screen and stored.
    @Test
    fun deletingAStepDeletesItsTimerAndTheOthersKeepTheirs() {
        val id = writeANewRecipe()
        addStep("Marinate the chicken.")
        addStep("Roast the chicken.")
        chooseTimer(0)
        type(EditorTags.timerHours(0), "12")
        type(EditorTags.timerName(0), "Marinade")
        chooseTimer(1)
        type(EditorTags.timerMinutes(1), "40")
        assertEquals(listOf(Timer(12.hours, "Marinade"), Timer(40.minutes)), stored(id).steps.map { it.timer })

        compose.onNodeWithTag(EditorTags.stepRemove(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(Step(0, "Roast the chicken.", timer = Timer(40.minutes))), stored(id).steps)
        // What the screen held for the step stays with it: its hours were never entered.
        assertEquals("", fieldText(EditorTags.timerHours(0)))
        assertEquals("40", fieldText(EditorTags.timerMinutes(0)))
        assertEquals("Roast the chicken.", fieldText(EditorTags.timerName(0)))
        compose.onNodeWithTag(EditorTags.step(1)).assertDoesNotExist()
    }

    // The timer and its name survive leaving the editor, and are back when it opens again.
    @Test
    @Config(qualifiers = "ru")
    fun theTimerAndItsNameAreThereAfterLeavingTheEditor() {
        val id = writeANewRecipe()
        addStep("Поставить тесто в тёплое место.")
        addStep("Выпекать.")
        chooseTimer(0)
        type(EditorTags.timerHours(0), "1")
        type(EditorTags.timerMinutes(0), "30")
        chooseTimer(1)
        type(EditorTags.timerMinutes(1), "35")
        type(EditorTags.timerName(1), "Хлеб в духовке")
        val before = stored(id)
        assertEquals(listOf(Timer(90.minutes), Timer(35.minutes, "Хлеб в духовке")), before.steps.map { it.timer })

        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)
        assertEquals(before, stored(id))

        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(1)), PATIENCE_MS)
        assertEquals("1", fieldText(EditorTags.timerHours(0)))
        assertEquals("30", fieldText(EditorTags.timerMinutes(0)))
        assertEquals("Поставить тесто в тёплое место.", fieldText(EditorTags.timerName(0)))
        assertEquals("0", fieldText(EditorTags.timerHours(1)))
        assertEquals("35", fieldText(EditorTags.timerMinutes(1)))
        assertEquals("Хлеб в духовке", fieldText(EditorTags.timerName(1)))
        assertEquals(before, stored(id))
    }

    // A frozen recipe shows a step's timer — its duration and its name — and nothing to change it with.
    @Test
    @Config(qualifiers = "ru")
    fun aFrozenRecipeShowsAStepsTimerReadOnly() {
        val recipe = seed(
            Recipe.create(created, null).copy(
                title = "Курица",
                steps = listOf(
                    Step(0, "Замариновать курицу.", timer = Timer(12.hours, "Маринад")),
                    Step(1, "Запекать курицу при 190 градусах, пока кожа не станет золотистой и хрустящей", timer = Timer(1.hours + 5.minutes)),
                    Step(2, "Дать отдохнуть.", timer = Timer(10.minutes)),
                    Step(3, "Подать."),
                ),
                sent = SentStatus.SENT,
            ).inState(RecipeState.PUBLISHED),
        )
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(3)), PATIENCE_MS)
        assertEquals(listOf("Шаг 1", "Замариновать курицу.", "Таймер: 12 ч", "Название таймера: Маринад"), compose.textsOf(EditorTags.step(0)))
        assertEquals(
            listOf(
                "Шаг 2",
                "Запекать курицу при 190 градусах, пока кожа не станет золотистой и хрустящей",
                "Таймер: 1 ч 5 мин",
                "Название таймера: Запекать курицу при 190 градусах, пока кожа не станет…",
            ),
            compose.textsOf(EditorTags.step(1)),
        )
        assertEquals(listOf("Шаг 3", "Дать отдохнуть.", "Таймер: 10 мин", "Название таймера: Дать отдохнуть."), compose.textsOf(EditorTags.step(2)))
        assertEquals(listOf("Шаг 4", "Подать."), compose.textsOf(EditorTags.step(3)))
        (0..3).forEach { compose.onNodeWithTag(EditorTags.timerAttach(it)).assertDoesNotExist() }
        // No control takes a frozen recipe's timer off either.
        (0..3).forEach { compose.onNodeWithTag(EditorTags.timerRemove(it)).assertDoesNotExist() }
        assertEquals(0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        val actions = compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
        assertEquals(listOf(EditorTags.BACK, EditorTags.FORK_NOTE, ChecklistTags.CONTROL), actions.map { it.config[SemanticsProperties.TestTag] })
        assertEquals(recipe, stored(recipe.id))
    }

    // FLOW-002 (4) at the editor's boundary: no change to a timer reaches a frozen recipe.
    @Test
    fun theEditorRefusesEveryChangeOfATimerToAFrozenRecipe() {
        val recipe = seed(
            Recipe.create(created, null).copy(steps = listOf(Step(0, "Bake.", timer = Timer(30.minutes)))).inState(RecipeState.PENDING),
        )
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        assertFalse(editor.attachTimer(0))
        assertNull(editor.statement.value)
        assertFalse(editor.addTimer(0, 1, 1, 0))
        assertFalse(editor.setTimerDuration(0, 0, 1, 0))
        assertFalse(editor.setTimerDuration(0, 0, 0, 0))
        assertFalse(editor.setTimerLabel(0, 0, "Oven"))
        assertFalse(editor.removeTimer(0, 0))
        assertNull(editor.removal.value)
        assertEquals(recipe, editor.recipe.value)
        assertEquals(recipe, stored(recipe.id))
    }

    // A11Y-003, A11Y-005 over the timer: every target is 48 dp and every action is named, in both languages.
    @Test
    fun theTimerIsReachableAndNamedInEnglish() = reachableAndNamed()

    @Test
    @Config(qualifiers = "ru")
    fun theTimerIsReachableAndNamedInRussian() = reachableAndNamed()

    private fun reachableAndNamed() {
        writeANewRecipe()
        addStep("Mix.")
        chooseTimer(0)
        type(EditorTags.timerMinutes(0), "5")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0)), PATIENCE_MS)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 8)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 8)
        assertEquals(string(R.string.editor_timer_hours), fieldLabel(EditorTags.timerHours(0)))
        assertEquals(string(R.string.editor_timer_minutes), fieldLabel(EditorTags.timerMinutes(0)))
        assertEquals(string(R.string.editor_timer_name), fieldLabel(EditorTags.timerName(0)))
        assertEquals(string(R.string.editor_timer_action, 1), AccessibilityChecks.clickActionLabel(node(EditorTags.timerAttach(0))))
        // The control that takes the timer off is named for the screen reader, with its step, and is 48 dp.
        assertEquals(string(R.string.editor_timer_remove_action, 1), AccessibilityChecks.announcedLabel(node(EditorTags.timerRemove(0))))
        assertTargetOf48(EditorTags.timerRemove(0))

        // Several timers are each named with their place: their names and their removals.
        chooseTimer(0)
        type(EditorTags.timerMinutes(0, 1), "7")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerRemove(0, 1)), PATIENCE_MS)
        chooseTimer(0)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0, 2)), PATIENCE_MS)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 14)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 14)
        (0..1).forEach { place ->
            assertEquals(string(R.string.editor_timer_name_numbered, place + 1), fieldLabel(EditorTags.timerName(0, place)))
            assertEquals(string(R.string.editor_timer_remove_numbered_action, 1, place + 1), AccessibilityChecks.announcedLabel(node(EditorTags.timerRemove(0, place))))
            assertTargetOf48(EditorTags.timerRemove(0, place))
        }
        assertEquals(string(R.string.editor_timer_hours), fieldLabel(EditorTags.timerHours(0, 2)))
        assertEquals(string(R.string.editor_timer_minutes), fieldLabel(EditorTags.timerMinutes(0, 2)))
    }

    private fun assertTargetOf48(tag: String) {
        val target = node(tag)
        val density = target.layoutInfo.density.density
        assertTrue(tag, target.size.width / density >= 48f && target.size.height / density >= 48f)
    }

    // At the demo the author asked for a small button that takes a step's timer off: it sits with the timer's fields,
    // and nowhere else. One just opened, holding no duration yet, has it too: it only takes the fields away.
    @Test
    fun aRemoveControlSitsWithTheTimerOfAStepThatCarriesOne() {
        val recipe = seed(
            Recipe.create(created, null).copy(steps = listOf(Step(0, "Preheat the oven."), Step(1, "Roast.", timer = Timer(20.minutes)))),
        )
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerRemove(1)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerRemove(1)).assertExists()
        assertEquals("Remove timer from step 2", AccessibilityChecks.announcedLabel(node(EditorTags.timerRemove(1))))
        // A step with no timer has none.
        compose.onNodeWithTag(EditorTags.timerRemove(0)).assertDoesNotExist()
        chooseTimer(0)
        compose.onNodeWithTag(EditorTags.timerHours(0)).assertExists()
        assertEquals("Remove timer from step 1", AccessibilityChecks.announcedLabel(node(EditorTags.timerRemove(0))))
        compose.onNodeWithTag(EditorTags.timerRemove(0)).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerHours(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.STATEMENT, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(recipe, stored(recipe.id))
        chooseTimer(0)
        type(EditorTags.timerMinutes(0), "10")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerRemove(0)).assertExists()
    }

    // Pressing it takes the timer off, its given name with it: the fields go, nothing is stored, the step offers a
    // timer again, which starts from the derived name; and the removal is in the book after leaving the editor.
    @Test
    fun removingATimerTakesItOffWithItsNameAndItStaysOff() {
        val id = writeANewRecipe()
        addStep("Roast the chicken.")
        addStep("Rest it.")
        chooseTimer(0)
        type(EditorTags.timerMinutes(0), "20")
        type(EditorTags.timerName(0), "Chicken in the oven")
        chooseTimer(1)
        type(EditorTags.timerMinutes(1), "10")
        assertEquals(Timer(20.minutes, "Chicken in the oven"), timerOf(id, 0))

        compose.onNodeWithTag(EditorTags.timerRemove(0)).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerHours(0)), PATIENCE_MS)
        // Said as today, with Undo: a step's only timer is not numbered.
        compose.awaitStatement(string(R.string.editor_timer_removed, 1))
        compose.onNodeWithTag(EditorTags.timerMinutes(0)).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.timerName(0)).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.timerRemove(0)).assertDoesNotExist()
        assertEquals(setOf(EditorTags.stepText(0)), fieldsIn(0))
        assertNull(timerOf(id, 0))
        // The other step's timer stands.
        assertEquals(Timer(10.minutes), timerOf(id, 1))
        assertEquals("10", fieldText(EditorTags.timerMinutes(1)))

        // Attached again, the timer starts empty, and from the name derived from the step.
        chooseTimer(0)
        compose.onNodeWithTag(EditorTags.timerStatement(0)).assertDoesNotExist()
        assertEquals("", fieldText(EditorTags.timerHours(0)))
        assertEquals("", fieldText(EditorTags.timerMinutes(0)))
        type(EditorTags.timerMinutes(0), "25")
        assertEquals(Timer(25.minutes), timerOf(id, 0))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0)), PATIENCE_MS)
        assertEquals("Roast the chicken.", fieldText(EditorTags.timerName(0)))

        // Taken off again and the editor left: the book holds no timer on the step, and opened again, none shows.
        compose.onNodeWithTag(EditorTags.timerRemove(0)).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerHours(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)
        assertNull(timerOf(id, 0))
        assertEquals(Timer(10.minutes), timerOf(id, 1))
        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerAttach(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerHours(0)).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.timerRemove(0)).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.timerRemove(1)).assertExists()
    }

    // A timer the author adds takes the focus in its hours, whether the step had a timer or not.
    @Test
    fun anAddedTimerTakesTheFocusInItsHours() {
        writeANewRecipe()
        addStep("Bake.")
        chooseTimer(0)
        compose.waitUntil(PATIENCE_MS) { node(EditorTags.timerHours(0)).config[SemanticsProperties.Focused] }
        type(EditorTags.timerMinutes(0), "30")
        chooseTimer(0)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerHours(0, 1)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { node(EditorTags.timerHours(0, 1)).config[SemanticsProperties.Focused] }
        assertFalse(node(EditorTags.timerHours(0)).config[SemanticsProperties.Focused])
    }

    // The remove control is named in Russian, with its step.
    @Test
    @Config(qualifiers = "ru")
    fun theRemoveControlIsNamedInRussian() {
        val recipe = seed(Recipe.create(created, null).copy(steps = listOf(Step(0, "Томить.", timer = Timer(2.hours, "Соус")))))
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerRemove(0)), PATIENCE_MS)
        assertEquals("Убрать таймер шага 1", AccessibilityChecks.announcedLabel(node(EditorTags.timerRemove(0))))
        compose.onNodeWithTag(EditorTags.timerRemove(0)).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerHours(0)), PATIENCE_MS)
        assertNull(timerOf(recipe.id))
    }
}
