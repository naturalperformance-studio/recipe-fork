package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.scaling.Rational
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
import kotlin.time.Duration.Companion.minutes

/**
 * The hints a step gives as it is written (GUIDE-005): references offered where the first step is begun (GUIDE-008),
 * and what a long step's unnamed timer will announce, shown on leaving the step (GUIDE-006).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class StepHintsEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-03T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)
    private var shown = false

    private val flour = Ingredient(
        0, null, freeTextName = "flour", unitType = QuantityUnit.GRAM.type, unit = QuantityUnit.GRAM, quantity = Rational.of(200),
    )
    private val longStep = "Put the chicken in the oven at 190 degrees and roast until the skin is golden and crisp, " +
        "turning the tray once halfway through so that both sides colour evenly, then let it rest under foil"

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

    /** Opens the editor directly on [recipe], as a test fixture: the application routes there through the card. */
    private fun openDirectly(recipe: Recipe): Recipe {
        runBlocking { recipes.save(recipe) }
        show()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe
    }

    private fun draft(vararg steps: Step, ingredients: List<Ingredient> = listOf(flour)): Recipe =
        Recipe.create(created, owner = null).copy(title = "Roast", language = RecipeLanguage.EN, ingredients = ingredients, steps = steps.toList())

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun statements(): Int =
        compose.onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun fieldText(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** Puts the author in the field tagged [tag]; whatever field they were in, they have left. */
    private fun enter(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }

    /** Comes into the step at [position] and leaves it for the title. */
    private fun passThrough(position: Int) {
        enter(EditorTags.stepText(position))
        enter(EditorTags.TITLE)
    }

    private fun notificationText(position: Int): String = compose.textsOf(EditorTags.timerNotificationText(position)).single()

    private fun awaitNotification(position: Int) =
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerNotification(position)), PATIENCE_MS)

    // ---- GUIDE-008 ----

    private fun offered(position: Int): Boolean = exists(EditorTags.stepReferencesOffered(position))

    // Beginning the first step of a recipe that holds an ingredient says, under the step, that a quantity goes in as a
    // reference, by @ or by the step's icon, and that a reference follows the reader's count while a typed number does
    // not; leaving the step, it goes.
    @Test
    fun beginningTheFirstStepOfARecipeWithIngredientsOffersReferences() {
        openDirectly(draft(Step(0, ""), Step(1, "")))
        assertFalse(offered(0))
        enter(EditorTags.stepText(0))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepReferencesOffered(0)), PATIENCE_MS)
        val offer = string(R.string.editor_references_offered)
        assertEquals(listOf(offer), compose.textsOf(EditorTags.stepReferencesOffered(0)))
        assertTrue(offer, offer.contains("@") && offer.contains("insert-ingredient icon"))
        assertTrue(offer, offer.contains("reader's serving count") && offer.contains("typed by hand does not"))
        // The icon it names is the step's control that inserts a reference (FLOW-031), and it is not covered.
        compose.onNodeWithTag(EditorTags.stepInsert(0)).assertExists()
        assertEquals(0, statements())
        // Typing goes on in the step while it is there.
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("Sift the flour.")
        compose.waitForIdle()
        assertEquals("Sift the flour.", fieldText(EditorTags.stepText(0)))
        compose.onNodeWithTag(EditorTags.stepText(0)).assertIsFocused()
        enter(EditorTags.TITLE)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.stepReferencesOffered(0)), PATIENCE_MS)
    }

    // Said once an editing session: leaving the first step and beginning it again, or beginning another, says nothing.
    @Test
    fun theOfferIsSaidOnceAnEditingSession() {
        openDirectly(draft(Step(0, ""), Step(1, "")))
        enter(EditorTags.stepText(0))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepReferencesOffered(0)), PATIENCE_MS)
        enter(EditorTags.stepText(1))
        enter(EditorTags.TITLE)
        enter(EditorTags.stepText(0))
        compose.waitForIdle()
        assertFalse(offered(0))
        assertFalse(offered(1))
    }

    // A recipe with no ingredient yet offers nothing, there being nothing to reference.
    @Test
    fun aRecipeWithNoIngredientOffersNothing() {
        writeANewRecipe()
        compose.onNodeWithTag(EditorTags.STEP_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(0)), PATIENCE_MS)
        enter(EditorTags.stepText(0))
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextReplacement("Boil the water.")
        compose.waitForIdle()
        assertFalse(offered(0))
    }

    // Leaving the first step for the one below it, the offer goes without pulling that step up.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun theOfferGoingDoesNotPullTheNextStepUp() {
        openDirectly(draft(Step(0, "Sift the flour."), Step(1, "Serve.")))
        enter(EditorTags.stepText(0))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepReferencesOffered(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepText(1)).performScrollTo()
        compose.waitForIdle()
        val before: Rect = compose.onNodeWithTag(EditorTags.stepText(1)).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(EditorTags.stepText(1)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.stepReferencesOffered(0)), PATIENCE_MS)
        compose.waitForIdle()
        val after = compose.onNodeWithTag(EditorTags.stepText(1)).fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
        compose.onNodeWithTag(EditorTags.stepText(1)).assertIsFocused()
    }

    // Only "the first step is begun" and "the recipe holds an ingredient" decide it: whatever the steps say — empty, a
    // typed number, a reference, the @ itself — the answer is the same; another step is never the first.
    @Test
    fun theDecisionReadsNoStepsText() {
        listOf(
            "",
            "Add 200 g flour",
            "@",
            "Всыпьте  муки",
        ).forEach { text ->
            val withIngredient = draft(Step(0, text), Step(1, text))
            assertFalse(text, began(withIngredient, 1))
            assertTrue(text, began(withIngredient, 0))
            assertFalse(text, began(draft(Step(0, text), ingredients = emptyList()), 0))
        }
        // A frozen recipe is not written.
        assertFalse(began(draft(Step(0, "")).inState(RecipeState.PENDING), 0))
    }

    private fun began(recipe: Recipe, position: Int): Boolean {
        runBlocking { recipes.save(recipe) }
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        return editor.beginStep(position).also { offered ->
            // Asked again in the same session, it is never offered twice.
            if (offered) assertFalse(editor.beginStep(position))
        }
    }

    // A number typed by hand after the offer is taken as typed: nothing marks it and nothing refuses it.
    @Test
    fun aNumberTypedByHandIsAcceptedAndUnmarked() {
        val recipe = openDirectly(draft(Step(0, "")))
        enter(EditorTags.stepText(0))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepReferencesOffered(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("Add 200 g flour and stir.")
        compose.waitForIdle()
        enter(EditorTags.TITLE)
        assertEquals(Step(0, "Add 200 g flour and stir."), stored(recipe.id).steps.single())
        assertFalse(exists(EditorTags.stepMark(0)))
        assertFalse(exists(EditorTags.stepPartWarning(0)))
        assertEquals(0, statements())
    }

    @Test
    @Config(qualifiers = "ru")
    fun theOfferIsInRussian() {
        openDirectly(draft(Step(0, "")))
        enter(EditorTags.stepText(0))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepReferencesOffered(0)), PATIENCE_MS)
        val offer = compose.textsOf(EditorTags.stepReferencesOffered(0)).single()
        assertEquals(string(R.string.editor_references_offered), offer)
        assertTrue(offer, offer.contains("@") && offer.contains("значок вставки ингредиента") && offer.contains("вручную"))
    }

    // ---- GUIDE-006 ----

    // Leaving a step over 60 characters with an unnamed timer shows what the notification will say, cut as FLOW-016
    // cuts it, says that this is what it will say, and offers to name the timer; the step is stored as typed.
    @Test
    fun leavingALongStepWithAnUnnamedTimerShowsWhatItsNotificationWillSay() {
        val recipe = openDirectly(draft(Step(0, longStep, timer = Timer(40.minutes))))
        assertFalse(exists(EditorTags.timerNotification(0)))
        passThrough(0)
        awaitNotification(0)
        val name = TimerName.derived(longStep)
        assertEquals("Put the chicken in the oven at 190 degrees and roast until…", name)
        assertEquals(string(R.string.editor_timer_notification, name), notificationText(0))
        assertTrue(notificationText(0).startsWith("The timer's notification will say"))
        // No reference in it: nothing about the reader's count, and nothing asks the author to rewrite the step.
        assertFalse(exists(EditorTags.timerNotificationFollows(0)))
        assertEquals(string(R.string.editor_timer_notification_name), compose.textsOf(EditorTags.timerNotificationName(0)).single())
        compose.textsOf(EditorTags.timerNotification(0)).forEach { assertFalse(it, it.contains("rewrite", ignoreCase = true)) }
        // Nothing refused, and the step stands exactly as typed.
        assertEquals(recipe, stored(recipe.id))
        // Coming back into the step, the author types on: the display is not in their way.
        enter(EditorTags.stepText(0))
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerNotification(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput(" Serve.")
        compose.waitForIdle()
        assertEquals("$longStep Serve.", stored(recipe.id).steps.single().text)
    }

    // 61 characters, the first past the cut, with an unnamed timer: shown, cut with the ellipsis.
    @Test
    fun leavingA61CharacterStepWithAnUnnamedTimerShowsTheDisplay() {
        val text = "Roast the chicken until golden, then rest it under foil now.." // one past the 60 below
        assertEquals(61, text.length)
        openDirectly(draft(Step(0, text, timer = Timer(10.minutes))))
        passThrough(0)
        awaitNotification(0)
        val name = TimerName.derived(text)
        assertTrue(name, name.endsWith(TimerName.ELLIPSIS))
        assertEquals(string(R.string.editor_timer_notification, name), notificationText(0))
    }

    // 59 and 60 characters with no reference, a long step with no timer, and a long step whose timer is named: nothing.
    @Test
    fun nothingIsShownWhereNothingWillBeCut() {
        val fiftyNine = "Roast the chicken until golden, then rest it under foil now"
        val sixty = "Roast the chicken until golden, then rest it under foil now."
        assertEquals(59, fiftyNine.length)
        assertEquals(60, sixty.length)
        openDirectly(
            draft(
                Step(0, fiftyNine, timer = Timer(10.minutes)),
                Step(1, sixty, timer = Timer(10.minutes)),
                Step(2, longStep),
                Step(3, longStep, timer = Timer(10.minutes, "Chicken in the oven")),
            ),
        )
        (0..3).forEach { passThrough(it) }
        compose.waitForIdle()
        (0..3).forEach { assertFalse("step $it", exists(EditorTags.timerNotification(it))) }
    }

    // A short step whose opening holds a reference is shown, rendered at the authored count, and says that its
    // quantities follow the reader's count.
    @Test
    fun aShortStepOpeningWithAReferenceIsShownAndSaysQuantitiesFollowTheReader() {
        val text = "Sift  into the bowl, then whisk it with the eggs" // the reference sits at 5
        val step = Step(0, text, listOf(IngredientReference(0, 5)), Timer(5.minutes))
        openDirectly(draft(step))
        passThrough(0)
        awaitNotification(0)
        val read = "Sift 200 g into the bowl, then whisk it with the eggs"
        assertTrue(read.length <= TimerName.LENGTH)
        assertEquals(string(R.string.editor_timer_notification, read), notificationText(0))
        assertEquals(listOf(string(R.string.editor_timer_notification_follows)), compose.textsOf(EditorTags.timerNotificationFollows(0)))
    }

    // Naming the timer from the display takes the author to its name, filled with the derived one; the name typed is
    // held, and the display does not come back for this step while the name is set.
    @Test
    fun namingTheTimerFromTheDisplayHoldsTheNameAndTheDisplayGoes() {
        val recipe = openDirectly(draft(Step(0, longStep, timer = Timer(40.minutes)), Step(1, "Serve.")))
        passThrough(0)
        awaitNotification(0)
        compose.onNodeWithTag(EditorTags.timerNotificationName(0)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTags.timerName(0)).assertIsFocused()
        assertEquals(TimerName.derived(longStep), fieldText(EditorTags.timerName(0)))
        compose.onNodeWithTag(EditorTags.timerName(0)).performTextReplacement("Chicken in the oven")
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.timerNotification(0)), PATIENCE_MS)
        assertEquals(Timer(40.minutes, "Chicken in the oven"), stored(recipe.id).steps[0].timer)
        assertEquals(longStep, stored(recipe.id).steps[0].text)
        passThrough(0)
        compose.waitForIdle()
        assertFalse(exists(EditorTags.timerNotification(0)))
    }

    // A rewritten step left again shows its new opening.
    @Test
    fun aRewrittenStepLeftAgainShowsItsNewOpening() {
        openDirectly(draft(Step(0, longStep, timer = Timer(40.minutes))))
        passThrough(0)
        awaitNotification(0)
        val rewritten = "Chicken into the oven for 40 minutes: put it in at 190 degrees, skin up, on the middle shelf"
        enter(EditorTags.stepText(0))
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextReplacement(rewritten)
        compose.waitForIdle()
        enter(EditorTags.TITLE)
        awaitNotification(0)
        assertEquals(string(R.string.editor_timer_notification, TimerName.derived(rewritten)), notificationText(0))
        assertTrue(notificationText(0).contains("Chicken into the oven for 40 minutes"))
    }

    // Appearing above the field the author went on to, the display does not push that field away.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun theDisplayDoesNotPushTheFieldTheAuthorWentOnTo() {
        openDirectly(draft(Step(0, longStep, timer = Timer(40.minutes)), Step(1, "Serve.")))
        enter(EditorTags.stepText(0))
        compose.onNodeWithTag(EditorTags.stepText(1)).performScrollTo()
        compose.waitForIdle()
        val before: Rect = compose.onNodeWithTag(EditorTags.stepText(1)).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(EditorTags.stepText(1)).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitNotification(0)
        compose.waitForIdle()
        val after = compose.onNodeWithTag(EditorTags.stepText(1)).fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
        compose.onNodeWithTag(EditorTags.stepText(1)).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "ru")
    fun theDisplayIsInRussian() {
        val text = "Просейте  в миску и взбейте венчиком с яйцами до пышности"
        val recipe = openDirectly(draft(Step(0, text, listOf(IngredientReference(0, 9)), Timer(5.minutes))))
        passThrough(0)
        awaitNotification(0)
        assertTrue(notificationText(0), notificationText(0).startsWith("Уведомление таймера скажет: «Просейте 200"))
        assertTrue(compose.textsOf(EditorTags.timerNotificationFollows(0)).single().contains("числом порций читателя"))
        assertEquals("Назвать таймер", compose.textsOf(EditorTags.timerNotificationName(0)).single())
        assertNull(stored(recipe.id).steps.single().timer!!.label)
    }
}
