package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * What the screen saved for a step or an ingredient row comes back on that row when the activity is recreated
 * (rotation, process restore) after rows were moved or inserted: the row keys are kept as the saved state is.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RowKeysRestorationTest {
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
    private val restoration = StateRestorationTester(compose)

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

    private fun open(recipe: Recipe) {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        restoration.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun fieldText(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun choose(menu: String, actionTag: String) {
        compose.onNodeWithTag(menu).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(actionTag), PATIENCE_MS)
        compose.onNodeWithTag(actionTag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(actionTag), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun restore() {
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        compose.waitForIdle()
    }

    @Test
    fun aStepsTimerFieldsComeBackOnItsStepAfterAMoveAndAnInsert() {
        val recipe = Recipe.create(created, null).copy(
            steps = listOf(
                Step(0, "Whisk.", timer = Timer(2.minutes)),
                Step(1, "Rest.", timer = Timer(7.minutes)),
                Step(2, "Bake.", timer = Timer(40.minutes)),
            ),
        )
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(2)), PATIENCE_MS)
        // The third to the top, then a new step above everything.
        choose(EditorTags.stepMenu(2), EditorTags.stepAction(2, RowAction.MOVE_UP))
        choose(EditorTags.stepMenu(1), EditorTags.stepAction(1, RowAction.MOVE_UP))
        choose(EditorTags.stepMenu(0), EditorTags.stepAction(0, RowAction.INSERT_ABOVE))
        assertEquals(listOf("40", "2", "7"), (1..3).map { fieldText(EditorTags.timerMinutes(it)) })

        restore()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(3)), PATIENCE_MS)
        assertEquals(listOf("", "Bake.", "Whisk.", "Rest."), (0..3).map { fieldText(EditorTags.stepText(it)) })
        assertEquals(listOf("40", "2", "7"), (1..3).map { fieldText(EditorTags.timerMinutes(it)) })
        // The moved step's hours are typed against its own minutes.
        compose.onNodeWithTag(EditorTags.timerHours(1)).performScrollTo().performTextReplacement("1")
        compose.waitForIdle()
        assertEquals(
            listOf(null, Timer(1.hours + 40.minutes), Timer(2.minutes), Timer(7.minutes)),
            stored(recipe.id).steps.map { it.timer },
        )
    }

    @Test
    fun aRowsTypedQuantityComesBackOnItsRowAfterAMoveAndAnInsert() {
        val recipe = Recipe.create(created, null).copy(
            ingredients = listOf(
                Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)),
                Ingredient(1, null, null, "salt", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(5)),
            ),
        )
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientQuantity(1)), PATIENCE_MS)
        // "2.50" g is not held — whole grams only — and is shown as typed.
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("2.50")
        compose.waitForIdle()
        choose(EditorTags.ingredientMenu(0), EditorTags.ingredientAction(0, RowAction.MOVE_DOWN))
        choose(EditorTags.ingredientMenu(0), EditorTags.ingredientAction(0, RowAction.INSERT_ABOVE))
        assertEquals(listOf("", "5", "2.50"), (0..2).map { fieldText(EditorTags.ingredientQuantity(it)) })

        restore()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientQuantity(2)), PATIENCE_MS)
        assertEquals(listOf("", "salt", "flour"), (0..2).map { fieldText(EditorTags.ingredientName(it)) })
        assertEquals(listOf("", "5", "2.50"), (0..2).map { fieldText(EditorTags.ingredientQuantity(it)) })
        compose.onNodeWithTag(EditorTags.ingredientError(2)).assertExists()
    }

    // A row inserted after the state is restored gets a key no other row holds: what the screen keeps for each row
    // stays on that row, the new one starting empty.
    @Test
    fun aRowInsertedAfterARestoreGetsAKeyOfItsOwn() {
        val recipe = Recipe.create(created, null).copy(
            steps = listOf(Step(0, "Whisk.", timer = Timer(2.minutes)), Step(1, "Rest.", timer = Timer(7.minutes))),
        )
        open(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(1)), PATIENCE_MS)
        choose(EditorTags.stepMenu(1), EditorTags.stepAction(1, RowAction.MOVE_UP))

        restore()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(1)), PATIENCE_MS)
        choose(EditorTags.stepMenu(0), EditorTags.stepAction(0, RowAction.INSERT_BELOW))
        choose(EditorTags.stepMenu(0), EditorTags.stepAction(0, RowAction.INSERT_ABOVE))
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(3)), PATIENCE_MS)
        assertEquals(listOf("", "Rest.", "", "Whisk."), (0..3).map { fieldText(EditorTags.stepText(it)) })
        assertEquals("7", fieldText(EditorTags.timerMinutes(1)))
        assertEquals("2", fieldText(EditorTags.timerMinutes(3)))
        compose.onNodeWithTag(EditorTags.timerMinutes(0)).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.timerMinutes(2)).assertDoesNotExist()
        // The new rows' own state is their own: a timer opened on one shows on it alone.
        compose.onNodeWithTag(EditorTags.timerAttach(2)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(2)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerMinutes(0)).assertDoesNotExist()
        assertEquals(listOf("", "7", "", "2"), (0..3).map { tag -> EditorTags.timerMinutes(tag).let { t -> if (compose.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isEmpty()) "" else fieldText(t) } })
    }

    // The keys as saved and restored: every key given after the restore is one no row held before it.
    @Test
    fun keysGivenAfterARestoreAreNew() {
        val keys = RowKeys()
        repeat(3) { keys.of(it) }
        keys.insert(1, 3)
        keys.remove(0, 4)
        val saved = with(RowKeys.Saver) { object : androidx.compose.runtime.saveable.SaverScope { override fun canBeSaved(value: Any) = true }.save(keys) }!!
        val restored = RowKeys.Saver.restore(saved)!!
        val before = (0 until 3).map { restored.of(it) }
        assertEquals((0 until 3).map { keys.of(it) }, before)
        restored.insert(0, 3)
        restored.insert(4, 4)
        val after = (0 until 5).map { restored.of(it) }
        assertEquals(5, after.toSet().size)
        assertEquals(before, after.subList(1, 4))
    }
}
