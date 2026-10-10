package app.recipefork.app.editor

import android.content.Context
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepText
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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
import kotlin.time.Duration.Companion.minutes

/**
 * A step reads as text and opens in a sheet (T-211): the row is its number, its text with its references as the card
 * reads them, its note muted, its timers as the card's View writes them, a small picture of its photograph and its
 * marks, with its drag handle and no fields; a tap opens a bottom sheet holding its text, its note, its timers and
 * picture and its actions, whose edits apply as typed whichever way it is closed. A step added opens in its sheet and
 * goes again if closed empty; the sheet outlives the activity being recreated, follows a moved step and goes with a
 * removed one; a frozen recipe opens nothing.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val store = PhotoStore(context.filesDir)
    private val created = Instant.parse("2026-10-10T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)
    private val restoration = StateRestorationTester(compose)

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        compose.registerIdlingResource(TestPhotos.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        compose.unregisterIdlingResource(TestPhotos.idling)
        book.close()
        device.close()
    }

    /**
     * Beets in the list; a step with a reference to them and the author's note, one with a named timer, one holding
     * nothing yet, and one with a photograph.
     */
    private fun draft(state: RecipeState = RecipeState.LOCAL): Recipe {
        val photo = "step-photo"
        TestPhotos.jpeg(store.file(BookName.Pool, photo).apply { parentFile!!.mkdirs() }, 800, 600)
        return Recipe.create(created, owner = null).copy(
            title = "Borscht",
            language = RecipeLanguage.EN,
            servings = 4,
            ingredients = listOf(Ingredient(0, null, null, "beets", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500))),
            steps = listOf(
                Step(0, "Peel  beets.", listOf(IngredientReference(0, 5)), note = "Wear gloves."),
                Step(1, "Simmer.", timers = listOf(Timer(40.minutes, "Simmer"))),
                Step(2, ""),
                Step(3, "Serve."),
            ),
            media = listOf(Media(photo, null, MediaKind.PHOTO, MediaAttachment.ToStep(3), MediaVisibility.PRIVATE)),
            sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT,
        ).let { if (state == RecipeState.LOCAL) it else it.inState(state) }
    }

    private fun open(recipe: Recipe, restorable: Boolean = false): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            MaterialTheme(lightColorScheme()) { LibraryOrEditor(session, recipes, environment, opened) }
        }
        if (restorable) restoration.setContent(content) else compose.setContent(content)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(recipe.steps.lastIndex)), PATIENCE_MS)
        return recipe.id
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun isFocused(tag: String): Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }

    private fun hasSetTextAction() = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    private fun statementShown(): Boolean = exists(EditorTags.STATEMENT)

    /** Hands a press of the system's back to the topmost window: the sheet's own, in front of the editor. */
    private fun pressBack() {
        val window = windowRoots().last()
        compose.runOnUiThread {
            window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
    }

    private fun awaitClosed() {
        compose.waitUntil(PATIENCE_MS) { !compose.stepSheetOpen() }
        compose.waitForIdle()
    }

    // Scenario 1: each step reads as its number and its text with its references, as the card reads them; its note
    // muted; its timers as "Name · 40 min"; a small picture of its photograph; an empty step "New step". No field and
    // no icon row on a row; its drag handle stays.
    @Test
    fun eachStepReadsAsTextWithNoFields() = rowsReadAsText(listOf("Peel 500 g beets.", "Simmer.", "New step", "Serve."), "Simmer · 40 min")

    @Test
    @Config(qualifiers = "ru")
    fun eachStepReadsAsTextWithNoFieldsInRussian() = rowsReadAsText(listOf("Peel 500 г beets.", "Simmer.", "Новый шаг", "Serve."), "Simmer · 40 мин")

    private fun rowsReadAsText(expected: List<String>, timer: String) {
        val recipe = draft()
        open(recipe)
        assertEquals(expected, (0..3).map { compose.stepLine(it) })
        // The card's own reading, at the authored scale, so the two cannot drift apart.
        val language = if (context.resources.configuration.locales[0].language == "ru") InterfaceLanguage.RUSSIAN else InterfaceLanguage.ENGLISH
        assertEquals(StepText.render(recipe.steps[0], ReferenceText.authored(recipe.ingredients, language)), compose.stepLineAsShown(0))
        assertEquals("Wear gloves.", compose.rowText(StepSheetTags.note(0)))
        assertEquals(lightColorScheme().onSurfaceVariant, compose.ownTextColour(StepSheetTags.note(0)))
        assertEquals(lightColorScheme().onSurfaceVariant, compose.ownTextColour(StepSheetTags.text(2)))
        assertEquals(timer, compose.rowText(StepSheetTags.timer(1, 0)))
        assertFalse(exists(StepSheetTags.note(1)))
        compose.waitUntil(PATIENCE_MS) { exists(StepSheetTags.thumbnail(3)) }
        assertEquals(
            listOf(string(R.string.media_step_description, 4)),
            compose.onNodeWithTag(StepSheetTags.thumbnail(3), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
        )
        assertTrue(compose.onNodeWithTag(StepSheetTags.thumbnail(3), useUnmergedTree = true).fetchSemanticsNode().size.height <= 64 * compose.density.density)
        (0..3).forEach { position ->
            val inRow = hasAnyAncestor(hasTestTag(EditorTags.step(position)))
            assertEquals("step $position holds a field", 0, compose.onAllNodes(inRow and hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
            // The row is one target that opens it; nothing else on it acts. Its handle stays, to drag it by.
            val interactive = compose.onAllNodes(inRow and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
                .map { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } }
            assertEquals(listOf(StepSheetTags.line(position)), interactive)
            assertTrue(exists(EditorTags.stepHandle(position)))
            // Read by the screen reader as the step's name, then its text.
            assertEquals(string(R.string.editor_step, position + 1), compose.textsOf(StepSheetTags.line(position)).first())
        }
        assertFalse(compose.stepSheetOpen())
    }

    // Scenario 3: a tap on the row — one target at least 48 dp tall, named "Edit step" — opens the sheet with the step's
    // text, its "Insert ingredient", its note — labelled as the author's, with the fork hint — its timer and its photo
    // controls, and its actions; edits apply as typed, and closed by Done the row reads the new text.
    @Test
    fun aTapOpensTheSheetAndDoneKeepsTheEdits() = tapOpensTheSheet()

    @Test
    @Config(qualifiers = "ru")
    fun aTapOpensTheSheetAndDoneKeepsTheEditsInRussian() = tapOpensTheSheet()

    private fun tapOpensTheSheet() {
        val id = open(draft())
        val line = compose.onNodeWithTag(StepSheetTags.line(1)).fetchSemanticsNode()
        assertEquals(string(R.string.editor_step_edit), line.config[SemanticsActions.OnClick].label)
        assertEquals(Role.Button, line.config[SemanticsProperties.Role])
        assertTrue(line.size.height >= 48 * compose.density.density - 0.5f)
        compose.openStep(1)
        val sheet = hasAnyAncestor(hasTestTag(StepSheetTags.SHEET))
        val fields = compose.onAllNodes(sheet and hasSetTextAction()).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(
            listOf(EditorTags.stepText(1), EditorTags.timerHours(1), EditorTags.timerMinutes(1), EditorTags.timerName(1), StepNoteTags.field(1)),
            fields,
        )
        assertEquals(listOf(string(R.string.editor_step_note), string(R.string.editor_step_note_hint)), compose.textsOf(StepNoteTags.field(1)).take(2))
        val interactive = compose.onAllNodes(sheet and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } }
        assertEquals(
            // T-212: the timers, then the photo or clip, each a part of its own with the control that adds to it; T-218:
            // those right after the text and its "Insert ingredient", the note after them; the actions in the ingredient
            // sheet's order.
            listOf(
                StepSheetTags.DONE, EditorTags.stepText(1), EditorTags.stepInsert(1),
                EditorTags.timerHours(1), EditorTags.timerMinutes(1), EditorTags.timerRemove(1), EditorTags.timerName(1), EditorTags.timerAttach(1),
                EditorTags.stepPhoto(1), StepNoteTags.field(1),
                EditorTags.stepAction(1, RowAction.MOVE_UP), EditorTags.stepAction(1, RowAction.MOVE_DOWN),
                EditorTags.stepAction(1, RowAction.INSERT_ABOVE), EditorTags.stepAction(1, RowAction.INSERT_BELOW), EditorTags.stepRemove(1),
            ),
            interactive,
        )
        // A11Y-006: moving is a discrete action, one tap each, read as a button with its name.
        listOf(RowAction.MOVE_UP, RowAction.MOVE_DOWN).forEach { action ->
            val node = compose.onNodeWithTag(EditorTags.stepAction(1, action)).fetchSemanticsNode()
            assertEquals(Role.Button, node.config[SemanticsProperties.Role])
            assertEquals(listOf(string(action.label)), node.config[SemanticsProperties.Text].map { it.text })
        }
        assertEquals(listOf(string(R.string.editor_step_remove)), compose.textsOf(EditorTags.stepRemove(1)))
        // A step opened by a tap takes no focus by itself.
        assertFalse(isFocused(EditorTags.stepText(1)))
        compose.onNodeWithTag(EditorTags.stepText(1)).performTextInput(" Stir.")
        compose.onNodeWithTag(StepNoteTags.field(1)).performScrollTo().performTextReplacement("Low heat.")
        compose.waitForIdle()
        // Applied as typed: the book holds it with the sheet still open.
        assertEquals("Simmer. Stir.", stored(id).steps[1].text)
        assertEquals("Low heat.", stored(id).steps[1].note)
        compose.closeStep()
        assertEquals("Simmer. Stir.", compose.stepLine(1))
        assertEquals("Low heat.", compose.rowText(StepSheetTags.note(1)))
    }

    // Closed by the system's back or by a swipe down, the sheet keeps what was typed.
    @Test
    fun backOrASwipeDownKeepsTheEdits() {
        val id = open(draft())
        // A tap on the row, as the author's finger makes it.
        compose.onNodeWithTag(StepSheetTags.line(3)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepText(3)) }
        compose.onNodeWithTag(EditorTags.stepText(3)).performTextReplacement("Serve hot.")
        compose.waitForIdle()
        pressBack()
        awaitClosed()
        assertEquals("Serve hot.", stored(id).steps[3].text)
        assertEquals("Serve hot.", compose.stepLine(3))

        compose.openStep(0)
        compose.onNodeWithTag(StepNoteTags.field(0)).performScrollTo().performTextReplacement("")
        compose.waitForIdle()
        compose.onNodeWithTag(StepSheetTags.SHEET).performTouchInput { swipeDown() }
        awaitClosed()
        assertEquals(null, stored(id).steps[0].note)
        assertFalse(exists(StepSheetTags.note(0)))
    }

    // "Add step" adds a step and opens its sheet with the text focused; closed with nothing in it, the step goes again,
    // with no Undo. Written, it stays. A step put below another from its sheet opens the same way.
    @Test
    fun aStepAddedOpensFocusedAndGoesIfClosedEmpty() {
        val id = open(draft())
        compose.addStepAt(4)
        compose.waitUntil(PATIENCE_MS) { isFocused(EditorTags.stepText(4)) }
        assertEquals(5, stored(id).steps.size)
        compose.closeStep()
        compose.waitUntil(PATIENCE_MS) { stored(id).steps.size == 4 }
        assertFalse(exists(EditorTags.step(4)))
        assertFalse(statementShown())

        // Back closes it too; a note alone keeps it.
        compose.addStepAt(4)
        compose.onNodeWithTag(StepNoteTags.field(4)).performScrollTo().performTextReplacement("Optional.")
        compose.waitForIdle()
        pressBack()
        awaitClosed()
        assertEquals(5, stored(id).steps.size)
        assertEquals("Optional.", stored(id).steps[4].note)
        assertEquals(string(R.string.editor_step_new), compose.stepLine(4))

        // Written, it stays.
        compose.addStepAt(5)
        compose.onNodeWithTag(EditorTags.stepText(5)).performTextInput("Eat.")
        compose.closeStep()
        assertEquals("Eat.", stored(id).steps[5].text)
        assertEquals("Eat.", compose.stepLine(5))

        // Inserted below the first step from its sheet: the new step opens, focused; left empty, it goes.
        compose.stepAction(0, RowAction.INSERT_BELOW)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepText(1)) && isFocused(EditorTags.stepText(1)) }
        assertEquals("", stored(id).steps[1].text)
        assertEquals(7, stored(id).steps.size)
        compose.closeStep()
        compose.waitUntil(PATIENCE_MS) { stored(id).steps.size == 6 }
        assertEquals(listOf("Peel  beets.", "Simmer.", "", "Serve.", "", "Eat."), stored(id).steps.map { it.text })
        assertFalse(statementShown())
        // A step that held something before it was emptied is no new step: it stays.
        compose.openStep(3)
        compose.onNodeWithTag(EditorTags.stepText(3)).performTextReplacement("")
        compose.closeStep()
        assertEquals(6, stored(id).steps.size)
    }

    // The sheet outlives the activity being recreated, open on its step with what was typed in its note.
    @Test
    fun theOpenSheetSurvivesRecreation() {
        val id = open(draft(), restorable = true)
        compose.openStep(1)
        compose.onNodeWithTag(StepNoteTags.field(1)).performScrollTo().performTextReplacement("Lid on")
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepText(1)) }
        assertTrue(compose.stepSheetOpen())
        assertEquals("Simmer.", fieldText(EditorTags.stepText(1)))
        assertEquals("Lid on", fieldText(StepNoteTags.field(1)))
        compose.onNodeWithTag(StepNoteTags.field(1)).performScrollTo().performTextReplacement("Lid on a crack.")
        compose.waitForIdle()
        assertEquals("Lid on a crack.", stored(id).steps[1].note)
        compose.closeStep()
    }

    // A step moved from its sheet keeps it open, at its new place; one removed closes it, and Undo puts the step back.
    @Test
    fun aMovedStepKeepsItsSheetAndARemovedOneClosesIt() {
        val id = open(draft())
        compose.stepAction(1, RowAction.MOVE_UP)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepText(0)) }
        assertEquals("Simmer.", fieldText(EditorTags.stepText(0)))
        assertFalse(exists(EditorTags.stepText(1)))
        assertEquals(listOf("Simmer.", "Peel  beets."), stored(id).steps.take(2).map { it.text })
        // The first step offers no "Move up".
        assertFalse(exists(EditorTags.stepAction(0, RowAction.MOVE_UP)))
        compose.onNodeWithTag(StepNoteTags.field(0)).performScrollTo().performTextReplacement("Covered.")
        compose.waitForIdle()
        assertEquals("Covered.", stored(id).steps[0].note)

        compose.onNodeWithTag(EditorTags.stepRemove(0)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        awaitClosed()
        assertEquals(3, stored(id).steps.size)
        compose.awaitStatement(string(R.string.editor_step_removed, 1))
        val undo = hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        compose.onNode(undo, useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(id).steps.size == 4 }
        assertEquals("Simmer.", compose.stepLine(0))
        assertEquals("Covered.", compose.rowText(StepSheetTags.note(0)))
        assertFalse(compose.stepSheetOpen())
        // The last step offers no "Move down".
        compose.openStep(3)
        assertFalse(exists(EditorTags.stepAction(3, RowAction.MOVE_DOWN)))
        assertTrue(exists(EditorTags.stepAction(3, RowAction.MOVE_UP)))
        compose.closeStep()
    }

    // Remove tapped twice before the sheet has gone removes that step alone, once: the second tap acts on nothing, and
    // Undo puts the step back. A move tapped twice moves the step it was tapped on, found where it stands now.
    @Test
    fun removeTappedTwiceRemovesThatStepOnce() {
        val id = open(draft())
        compose.openStep(1)
        val remove = compose.onNodeWithTag(EditorTags.stepRemove(1)).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnUiThread {
            remove()
            remove()
        }
        awaitClosed()
        assertEquals(listOf("Peel  beets.", "", "Serve."), stored(id).steps.map { it.text })
        compose.awaitStatement(string(R.string.editor_step_removed, 2))
        val undo = hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        compose.onNode(undo, useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(id).steps.size == 4 }
        assertEquals(listOf("Peel  beets.", "Simmer.", "", "Serve."), stored(id).steps.map { it.text })

        compose.openStep(3)
        val up = compose.onNodeWithTag(EditorTags.stepAction(3, RowAction.MOVE_UP)).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnUiThread {
            up()
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("Peel  beets.", "Serve.", "Simmer.", ""), stored(id).steps.map { it.text })
        assertEquals("Serve.", fieldText(EditorTags.stepText(1)))
        compose.closeStep()
    }

    // A timer removed in the sheet says so with Undo once the sheet is closed — not behind the sheet, out of reach, where
    // the Undo would run out unseen — and the Undo puts the timer back.
    @Test
    fun aTimerRemovedInTheSheetOffersItsUndoOnceTheSheetCloses() {
        val id = open(draft())
        compose.openStep(1)
        compose.onNodeWithTag(EditorTags.timerRemove(1)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { stored(id).steps[1].timers.isEmpty() }
        compose.waitForIdle()
        assertFalse(statementShown())
        compose.closeStep()
        compose.awaitStatement(string(R.string.editor_timer_removed, 2))
        val undo = hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        compose.onNode(undo, useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(id).steps[1].timers == listOf(Timer(40.minutes, "Simmer")) }
        compose.waitUntil(PATIENCE_MS) { exists(StepSheetTags.timer(1, 0)) }
        assertEquals("Simmer · 40 min", compose.rowText(StepSheetTags.timer(1, 0)))
    }

    /** What a tap on the control tagged [tag] does, to be run with others within one frame. */
    private fun clickOf(tag: String): () -> Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!

    // Once an action has set the sheet on its way out, a tap within the same frame does nothing: Insert below then
    // Remove opens the step put below and leaves the step itself; Done then Move up then Remove closes the sheet with
    // the step neither moved nor removed.
    @Test
    fun nothingIsActedOnOnceTheSheetIsOnItsWayOut() {
        val id = open(draft())
        val texts = listOf("Peel  beets.", "Simmer.", "", "Serve.")
        compose.openStep(1)
        val insert = clickOf(EditorTags.stepAction(1, RowAction.INSERT_BELOW))
        val remove = clickOf(EditorTags.stepRemove(1))
        compose.runOnUiThread {
            insert()
            remove()
        }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepText(2)) }
        compose.waitForIdle()
        assertEquals(listOf("Peel  beets.", "Simmer.", "", "", "Serve."), stored(id).steps.map { it.text })
        compose.closeStep()
        compose.waitUntil(PATIENCE_MS) { stored(id).steps.size == 4 }
        assertEquals(texts, stored(id).steps.map { it.text })
        assertFalse(statementShown())

        compose.openStep(1)
        val done = clickOf(StepSheetTags.DONE)
        val up = clickOf(EditorTags.stepAction(1, RowAction.MOVE_UP))
        val removeAgain = clickOf(EditorTags.stepRemove(1))
        compose.runOnUiThread {
            done()
            up()
            removeAgain()
        }
        awaitClosed()
        assertEquals(texts, stored(id).steps.map { it.text })
        assertFalse(statementShown())
    }

    // T-212: at 360 dp and twice the font size, the sheet holds the step's timers and its photo or clip as two parts,
    // each under its name once it holds something (T-218: an empty part is its button alone), after the step's text and
    // its "Insert ingredient" and before the note and the actions (T-218): the timer's fields and "Add timer" in the one, "Add
    // photo or clip" in the other; nothing of them goes past the sheet, every target is 48 dp and no text is cut. Its
    // actions are list items as the ingredient sheet's are: as wide as the sheet, as tall as the ingredient's, read as
    // buttons, the removal alone in the error colour.
    @Test
    @Config(qualifiers = "en-w360dp-h2000dp", fontScale = 2f)
    fun theTimersAndThePictureArePartsOfTheSheetAtTheNarrowestWidthAndLargestText() =
        partsFit(listOf("Timers", "Photo or clip"), listOf("Add timer", "Add photo or clip"))

    @Test
    @Config(qualifiers = "ru-w360dp-h2000dp", fontScale = 2f)
    fun theTimersAndThePictureArePartsOfTheSheetAtTheNarrowestWidthAndLargestTextInRussian() =
        partsFit(listOf("Таймеры", "Фото или видео"), listOf("Добавить таймер", "Добавить фото или видео"))

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun partsFit(titles: List<String>, buttons: List<String>) {
        open(draft())
        compose.openStep(1)
        val sheet = bounds(StepSheetTags.SHEET)
        // Under its name, a heading for the screen reader, once it holds something: step 2 has a timer and no picture.
        fun headings(part: String) = compose.onAllNodes(hasAnyAncestor(hasTestTag(part)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true)
            .fetchSemanticsNodes().map { heading -> heading.config[SemanticsProperties.Text].joinToString("") { it.text } }
        assertEquals(listOf(titles[0]), headings(StepSheetTags.TIMERS))
        assertEquals(emptyList<String>(), headings(StepSheetTags.MEDIA))
        listOf(EditorTags.timerHours(1), EditorTags.timerMinutes(1), EditorTags.timerRemove(1), EditorTags.timerName(1), EditorTags.timerAttach(1))
            .forEach { compose.onNode(hasTestTag(it) and hasAnyAncestor(hasTestTag(StepSheetTags.TIMERS))).assertExists() }
        compose.onNode(hasTestTag(EditorTags.stepPhoto(1)) and hasAnyAncestor(hasTestTag(StepSheetTags.MEDIA))).assertExists()
        assertEquals(listOf(buttons[0]), compose.textsOf(EditorTags.timerAttach(1)))
        assertEquals(listOf(buttons[1]), compose.textsOf(EditorTags.stepPhoto(1)))
        // Top to bottom: the text, "Insert ingredient", the timers, the photo or clip, the note, the actions.
        val order = listOf(
            EditorTags.stepText(1), EditorTags.stepInsert(1), StepSheetTags.TIMERS, StepSheetTags.MEDIA, StepNoteTags.field(1),
            EditorTags.stepAction(1, RowAction.MOVE_UP),
        ).map(::bounds)
        order.zipWithNext().forEach { (above, below) -> assertTrue("$above above $below", above.bottom <= below.top + 0.5f) }
        listOf(
            StepSheetTags.TIMERS, StepSheetTags.MEDIA, EditorTags.timerHours(1), EditorTags.timerMinutes(1), EditorTags.timerRemove(1),
            EditorTags.timerName(1), EditorTags.timerAttach(1), EditorTags.stepPhoto(1),
        ).forEach { tag ->
            val shown = bounds(tag)
            assertTrue("$tag within the sheet", shown.left >= sheet.left - 0.5f && shown.right <= sheet.right + 0.5f)
        }
        (titles.take(1) + buttons).forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(StepSheetTags.SHEET)), useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout = layouts.single()
            assertFalse("$text cut", layout.didOverflowHeight)
            (0 until layout.lineCount).forEach { assertFalse("$text ellipsized", layout.isLineEllipsized(it)) }
        }
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose)

        // The actions, as the ingredient sheet's.
        val least = with(compose.density) { 56.dp.toPx() }
        val actions = listOf(RowAction.MOVE_UP, RowAction.MOVE_DOWN, RowAction.INSERT_ABOVE, RowAction.INSERT_BELOW).map { EditorTags.stepAction(1, it) } + EditorTags.stepRemove(1)
        actions.forEach { tag ->
            val shown = bounds(tag)
            assertEquals("$tag as wide as the sheet", sheet.width, shown.width, 1f)
            assertTrue("$tag at least 56 dp tall", shown.height >= least - 0.5f)
            assertEquals(Role.Button, compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Role])
            val colour = if (tag == EditorTags.stepRemove(1)) lightColorScheme().error else lightColorScheme().onSurface
            assertEquals(tag, colour, compose.textColourOf(tag))
        }
        val stepInsert = bounds(EditorTags.stepAction(1, RowAction.INSERT_BELOW))
        compose.closeStep()
        compose.openIngredient(0)
        val ingredientSheet = bounds(IngredientSheetTags.SHEET)
        val ingredientInsert = bounds(EditorTags.ingredientAction(0, RowAction.INSERT_BELOW))
        assertEquals(ingredientSheet.width, ingredientInsert.width, 1f)
        assertEquals(ingredientInsert.height, stepInsert.height, 1f)
        assertEquals(compose.textColourOf(EditorTags.ingredientRemove(0)), lightColorScheme().error)
        compose.closeIngredient()
        // Step 4's picture names its part.
        compose.openStep(3)
        assertEquals(listOf(titles[1]), headings(StepSheetTags.MEDIA))
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(titles[1]) and hasAnyAncestor(hasTestTag(StepSheetTags.MEDIA)), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        assertFalse("${titles[1]} cut", layouts.single().didOverflowHeight)
    }

    // A frozen recipe's steps read as the same text and open nothing.
    @Test
    fun aFrozenRecipesStepsReadAsTextAndOpenNothing() {
        open(draft(RecipeState.PUBLISHED))
        assertEquals(listOf("Peel 500 g beets.", "Simmer.", "New step", "Serve."), (0..3).map { compose.stepLine(it) })
        // Said as the author's, as nothing else on a frozen row says whose it is.
        assertEquals("Author's note: Wear gloves.", compose.rowText(StepSheetTags.note(0)))
        assertEquals("Simmer · 40 min", compose.rowText(StepSheetTags.timer(1, 0)))
        (0..3).forEach { position ->
            val line = compose.onNodeWithTag(StepSheetTags.line(position), useUnmergedTree = true).fetchSemanticsNode()
            assertFalse("step $position opens", SemanticsActions.OnClick in line.config)
            assertFalse(exists(EditorTags.stepHandle(position)))
        }
        compose.onNodeWithTag(StepSheetTags.line(0), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(compose.stepSheetOpen())
        assertEquals(0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }
}
