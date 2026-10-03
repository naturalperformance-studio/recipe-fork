package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
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
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-028: a part left below DOM-006's minimum or over a limit of DOM-012 is marked as a warning — words and an icon,
 * read by the screen reader — and nothing is refused or cut; completing the part takes the mark away by itself, and a
 * part never left is not marked.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BoundMarksEditorTest {
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

    private val everyWholeMark = listOf(
        EditorTags.TITLE_MARK, EditorTags.LANGUAGE_MARK, EditorTags.SERVINGS_MARK,
        EditorTags.INGREDIENTS_MARK, EditorTags.STEPS_MARK, EditorTags.DESCRIPTION_MARK,
    )

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
    private fun openDirectly(recipe: Recipe): RecipeId {
        runBlocking { recipes.save(recipe) }
        show()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe.id
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Puts the author in the field tagged [tag]; whatever field they were in, they have left. */
    private fun enter(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        enter(tag)
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun marked(tag: String): Boolean =
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun awaitMark(tag: String, text: String) {
        compose.waitUntil(PATIENCE_MS) { marked(tag) }
        assertEquals(listOf(text), compose.textsOf(tag))
    }

    private fun awaitNoMark(tag: String) = compose.waitUntil(PATIENCE_MS) { !marked(tag) }

    /** Appends a step to a recipe of [position] steps. */
    private fun addStep(position: Int = 0) {
        compose.onNodeWithTag(EditorTags.STEP_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(position)), PATIENCE_MS)
    }

    private fun row(position: Int) = Ingredient(
        position, null, freeTextName = "salt", unitType = QuantityUnit.GRAM.type, unit = QuantityUnit.GRAM, quantity = Rational.of(5),
    )

    private fun draft(change: (Recipe) -> Recipe): Recipe = change(Recipe.create(created, owner = null).copy(language = RecipeLanguage.EN))

    // FLOW-028 (7): a part the author has not visited is not marked, however far outside its bound it stands.
    @Test
    fun nothingIsMarkedWhenTheEditorOpens() {
        openDirectly(
            draft {
                it.copy(
                    title = "",
                    language = null,
                    description = "d".repeat(2_001),
                    steps = listOf(Step(0, "s".repeat(2_001), timer = Timer(5.minutes, "n".repeat(61)))),
                )
            },
        )
        (everyWholeMark + EditorTags.stepMark(0) + EditorTags.timerNameMark(0)).forEach { assertTrue(it, !marked(it)) }
    }

    // FLOW-028 (1, 3): an empty ingredient list is marked when the author moves on to the steps; one ingredient
    // takes the mark away by itself.
    @Test
    fun anEmptyIngredientListIsMarkedOnMovingToTheStepsAndUnmarkedByAddingOne() {
        writeANewRecipe()
        assertTrue(!marked(EditorTags.INGREDIENTS_MARK))
        addStep()
        awaitMark(EditorTags.INGREDIENTS_MARK, string(R.string.editor_mark_ingredients_missing))
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        awaitNoMark(EditorTags.INGREDIENTS_MARK)
    }

    // FLOW-028 (1): coming into a step that is already there is moving on to the steps as well.
    @Test
    fun comingIntoAStepMarksAnEmptyIngredientList() {
        openDirectly(draft { it.copy(title = "Tea", steps = listOf(Step(0, "Boil the water."))) })
        enter(EditorTags.stepText(0))
        awaitMark(EditorTags.INGREDIENTS_MARK, string(R.string.editor_mark_ingredients_missing))
    }

    // FLOW-028 (2, 3, 5): a step over 2 000 characters is marked when the author leaves it, never while they type in
    // it; its text is kept whole, typing on is not refused, and shortening it takes the mark away.
    @Test
    fun aStepOverItsLimitIsMarkedOnLeavingAndKeptWhole() {
        val id = writeANewRecipe()
        addStep()
        val long = "s".repeat(2_001)
        type(EditorTags.stepText(0), long)
        assertTrue(!marked(EditorTags.stepMark(0)))
        enter(EditorTags.TITLE)
        awaitMark(EditorTags.stepMark(0), string(R.string.editor_mark_characters_over, 2_000, 2_001))
        assertEquals(long, stored(id).steps[0].text)
        // Typing on is not refused, and nothing is cut.
        enter(EditorTags.stepText(0))
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("!")
        compose.waitUntil(PATIENCE_MS) { stored(id).steps[0].text == "$long!" }
        type(EditorTags.stepText(0), "s".repeat(2_000))
        awaitNoMark(EditorTags.stepMark(0))
        assertEquals(2_000, stored(id).steps[0].text.length)
    }

    // A step's mark stays with its step when a step is put before it.
    @Test
    fun aStepsMarkFollowsItsStep() {
        openDirectly(draft { it.copy(title = "Tea", steps = listOf(Step(0, "Boil."), Step(1, "s".repeat(2_001)))) })
        enter(EditorTags.stepText(1))
        enter(EditorTags.TITLE)
        compose.waitUntil(PATIENCE_MS) { marked(EditorTags.stepMark(1)) }
        compose.onNodeWithTag(EditorTags.stepMenu(1)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepAction(1, RowAction.INSERT_ABOVE)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepAction(1, RowAction.INSERT_ABOVE)).performClick()
        compose.waitUntil(PATIENCE_MS) { marked(EditorTags.stepMark(2)) && !marked(EditorTags.stepMark(1)) }
    }

    // FLOW-028 (3) over DOM-006 and DOM-012 for the title: left empty, then left too long; each mark goes once the
    // title is within its bound.
    @Test
    fun theTitleIsMarkedOnLeavingItEmptyOrTooLong() {
        val id = writeANewRecipe()
        enter(EditorTags.TITLE)
        assertTrue(!marked(EditorTags.TITLE_MARK))
        enter(EditorTags.SERVINGS)
        awaitMark(EditorTags.TITLE_MARK, string(R.string.editor_mark_title_missing))
        type(EditorTags.TITLE, "Borscht")
        awaitNoMark(EditorTags.TITLE_MARK)
        val long = "t".repeat(121)
        type(EditorTags.TITLE, long)
        enter(EditorTags.SERVINGS)
        awaitMark(EditorTags.TITLE_MARK, string(R.string.editor_mark_characters_over, 120, 121))
        assertEquals(long, stored(id).title)
    }

    // DOM-006: the serving count and the declared language.
    @Test
    fun theServingCountAndTheLanguageAreMarkedWhenLeftWithout() {
        writeANewRecipe()
        enter(EditorTags.SERVINGS)
        enter(EditorTags.TITLE)
        awaitMark(EditorTags.SERVINGS_MARK, string(R.string.editor_mark_servings_missing))
        type(EditorTags.SERVINGS, "4")
        awaitNoMark(EditorTags.SERVINGS_MARK)

        assertTrue(!marked(EditorTags.LANGUAGE_MARK))
        compose.onNodeWithTag(EditorTags.LANGUAGE_CLEAR).performScrollTo().performClick()
        awaitMark(EditorTags.LANGUAGE_MARK, string(R.string.editor_mark_language_missing))
        compose.onNodeWithTag(EditorTags.LANGUAGE).performScrollTo().performClick()
        awaitNoMark(EditorTags.LANGUAGE_MARK)
    }

    // FLOW-028 over DOM-006: reaching the description moves the author on from the ingredients and the steps, and a
    // description over 2 000 characters is marked on leaving it.
    @Test
    fun theStepsAndTheDescriptionAreMarkedOnLeaving() {
        val id = writeANewRecipe()
        enter(EditorTags.DESCRIPTION)
        awaitMark(EditorTags.STEPS_MARK, string(R.string.editor_mark_steps_missing))
        awaitMark(EditorTags.INGREDIENTS_MARK, string(R.string.editor_mark_ingredients_missing))
        val long = "d".repeat(2_001)
        type(EditorTags.DESCRIPTION, long)
        assertTrue(!marked(EditorTags.DESCRIPTION_MARK))
        enter(EditorTags.TITLE)
        awaitMark(EditorTags.DESCRIPTION_MARK, string(R.string.editor_mark_characters_over, 2_000, 2_001))
        assertEquals(long, stored(id).description)
        addStep()
        awaitNoMark(EditorTags.STEPS_MARK)
    }

    // DOM-012: more than 50 ingredients and more than 50 steps, each named with its count.
    @Test
    fun moreRowsThanTheLimitsAreMarked() {
        openDirectly(
            draft {
                it.copy(title = "Feast", ingredients = (0 until 51).map(::row), steps = (0 until 51).map { p -> Step(p, "Step.") })
            },
        )
        enter(EditorTags.DESCRIPTION)
        awaitMark(EditorTags.INGREDIENTS_MARK, string(R.string.editor_mark_ingredients_over, 50, 51))
        awaitMark(EditorTags.STEPS_MARK, string(R.string.editor_mark_steps_over, 50, 51))
    }

    // v0.2 left a timer name over 60 characters unmarked until FLOW-028; it is now marked on leaving the field, and
    // kept whole. A name the author never set follows its step and has no limit of its own.
    @Test
    fun aTimerNameOver60IsMarkedOnLeavingTheField() {
        val id = openDirectly(
            draft { it.copy(title = "Bread", steps = listOf(Step(0, "Bake.", timer = Timer(30.minutes)), Step(1, "b".repeat(80), timer = Timer(5.minutes)))) },
        )
        enter(EditorTags.timerName(1))
        enter(EditorTags.TITLE)
        assertTrue(!marked(EditorTags.timerNameMark(1)))

        val name = "The bread in the oven on the lowest shelf, with a tray of water beneath it for steam"
        enter(EditorTags.timerName(0))
        compose.onNodeWithTag(EditorTags.timerName(0)).performTextReplacement(name)
        compose.waitForIdle()
        assertTrue(!marked(EditorTags.timerNameMark(0)))
        enter(EditorTags.TITLE)
        awaitMark(EditorTags.timerNameMark(0), string(R.string.editor_mark_characters_over, 60, name.length))
        assertEquals(name, stored(id).steps[0].timer!!.label)
    }

    // The mark is words and an icon, never colour alone, and the screen reader reads it — as it appears, too.
    @Test
    fun aMarkIsWordsAndAnIconTheScreenReaderReads() {
        writeANewRecipe()
        enter(EditorTags.TITLE)
        enter(EditorTags.SERVINGS)
        compose.waitUntil(PATIENCE_MS) { marked(EditorTags.TITLE_MARK) }
        val node = compose.onNodeWithTag(EditorTags.TITLE_MARK).fetchSemanticsNode()
        assertEquals(listOf(string(R.string.editor_mark_title_missing)), compose.textsOf(EditorTags.TITLE_MARK))
        assertEquals(listOf(string(R.string.editor_mark_warning)), compose.descriptionOf(EditorTags.TITLE_MARK))
        assertEquals(LiveRegionMode.Polite, node.config[SemanticsProperties.LiveRegion])
        // A warning, not an error: the field it belongs to is not in error.
        assertTrue(compose.onNodeWithTag(EditorTags.TITLE).fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.Error) { null } == null)
    }

    // L10N: the marks are in the interface language.
    @Config(qualifiers = "ru")
    @Test
    fun theMarksSpeakRussian() {
        writeANewRecipe()
        addStep()
        awaitMark(EditorTags.INGREDIENTS_MARK, "Чтобы отправить рецепт, нужен хотя бы один ингредиент.")
        assertEquals(listOf("Предупреждение"), compose.descriptionOf(EditorTags.INGREDIENTS_MARK))
    }
}
