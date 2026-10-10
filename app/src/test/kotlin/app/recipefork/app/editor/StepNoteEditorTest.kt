package app.recipefork.app.editor

import android.content.Context
import android.text.InputType
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
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

/**
 * A step's author's note in the editor: always in the step's sheet, under the step's text, labelled as the author's with the hint that it stays with them when someone else forks the recipe; held trimmed, blank as none,
 * kept in full over its limit of 500 characters and marked on leaving the field (FLOW-028); a frozen recipe shows it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class StepNoteEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-10T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)
    private val note = "Бабушка всегда добавляла щепотку сахара"

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

    /** Opens the editor on [recipe], as a test fixture: the application routes there through the card. */
    private fun open(recipe: Recipe): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe.id
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun focused(tag: String): Boolean = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    private fun enter(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        enter(tag)
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    /** Whether anything on screen, the step's sheet included, still offers "Add a note". */
    private fun addOffered(): Boolean =
        compose.onAllNodes(hasText("Add a note") or hasText("Добавить заметку"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodes(hasTestTagMatching(Regex("editor-step-\\d+-note-add")), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasTestTagMatching(pattern: Regex) = SemanticsMatcher("tag matches $pattern") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.matches(pattern) == true
    }

    private fun draft(state: RecipeState = RecipeState.LOCAL, secondNote: String? = null) = Recipe.create(created, owner = null).copy(
        title = "Borscht",
        language = RecipeLanguage.EN,
        servings = 4,
        steps = listOf(Step(0, "Fry the beet."), Step(1, "Serve.", note = secondNote)),
    ).inState(state)

    // T-211: the note's field is always in the step's sheet, under its text — labelled as the author's, with the hint
    // that it stays with them when someone else forks the recipe; no "Add a note" step any more. The note is held
    // trimmed, the field keeping what was typed; the row then reads it, and emptied, the step holds none and the field
    // stays, empty.
    @Test
    fun theNoteIsAlwaysInTheSheetAndHeldTrimmed() {
        val id = open(draft())
        assertFalse(exists(StepNoteTags.field(0)))
        compose.openStep(0)
        assertTrue(exists(StepNoteTags.field(0)))
        assertEquals("", fieldText(StepNoteTags.field(0)))
        assertFalse(addOffered())
        // Under the step's text.
        val text = compose.onNodeWithTag(EditorTags.stepText(0)).fetchSemanticsNode()
        val field = compose.onNodeWithTag(StepNoteTags.field(0)).fetchSemanticsNode()
        assertTrue(field.positionInRoot.y >= text.positionInRoot.y + text.size.height)
        compose.onNodeWithTag(StepNoteTags.field(0)).assertHeightIsAtLeast(48.dp)
        val texts = compose.textsOf(StepNoteTags.field(0))
        assertTrue("$texts", "Author's note" in texts)
        assertTrue("$texts", "Stays with you when someone else forks the recipe" in texts)
        // Free text, begun with a capital, over several lines.
        enter(StepNoteTags.field(0))
        assertTrue(compose.keyboardAskedFor().inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)

        compose.onNodeWithTag(StepNoteTags.field(0)).performTextReplacement("  Бабушка всегда ")
        compose.waitForIdle()
        assertEquals("  Бабушка всегда ", fieldText(StepNoteTags.field(0)))
        assertEquals("Бабушка всегда", stored(id).steps[0].note)
        compose.onNodeWithTag(StepNoteTags.field(0)).performTextReplacement("$note ")
        enter(EditorTags.stepText(0))
        // Left, the field reads as held.
        assertEquals(note, fieldText(StepNoteTags.field(0)))
        assertEquals(listOf(note, null), stored(id).steps.map { it.note })
        compose.closeStep()
        // The row reads it, under the step's text; the other step is untouched.
        assertEquals(note, compose.rowText(StepSheetTags.note(0)))
        assertFalse(exists(StepSheetTags.note(1)))
        compose.openStep(1)
        assertEquals("", fieldText(StepNoteTags.field(1)))
        compose.closeStep()

        // Emptied, the step holds none; the field stays, empty, and the row reads no note.
        compose.openStep(0)
        type(StepNoteTags.field(0), "   ")
        enter(EditorTags.stepText(0))
        assertNull(stored(id).steps[0].note)
        assertTrue(exists(StepNoteTags.field(0)))
        assertEquals("", fieldText(StepNoteTags.field(0)))
        compose.closeStep()
        assertFalse(exists(StepSheetTags.note(0)))
        assertFalse(addOffered())
    }

    // The field of a step that opened with a note stays while the author is in it, emptied: they can clear the note and
    // write it anew without the field, the focus or the keyboard going. Left empty, it stays, and the step holds none.
    @Test
    fun anEmptiedNoteKeepsItsFieldWhileTheAuthorIsInIt() {
        val id = open(draft(secondNote = note))
        compose.openStep(1)
        type(StepNoteTags.field(1), "")
        assertTrue(exists(StepNoteTags.field(1)))
        assertTrue(focused(StepNoteTags.field(1)))
        assertNull(stored(id).steps[1].note)
        compose.onNodeWithTag(StepNoteTags.field(1)).performTextReplacement("Pour it hot.")
        compose.waitForIdle()
        assertTrue(focused(StepNoteTags.field(1)))
        assertEquals("Pour it hot.", stored(id).steps[1].note)

        compose.onNodeWithTag(StepNoteTags.field(1)).performTextReplacement("")
        compose.waitForIdle()
        assertTrue(focused(StepNoteTags.field(1)))
        enter(EditorTags.stepText(1))
        assertTrue(exists(StepNoteTags.field(1)))
        assertNull(stored(id).steps[1].note)
        compose.closeStep()
        assertFalse(exists(StepSheetTags.note(1)))
    }

    // A step that already has a note reads it on its row, muted, and its sheet shows the field filled.
    @Test
    fun aStepWithANoteShowsItsField() {
        open(draft(secondNote = note))
        assertEquals(note, compose.rowText(StepSheetTags.note(1)))
        assertEquals(lightColorScheme().onSurfaceVariant, compose.ownTextColour(StepSheetTags.note(1)))
        compose.openStep(1)
        assertEquals(note, fieldText(StepNoteTags.field(1)))
        assertFalse(addOffered())
    }

    @Test
    @Config(qualifiers = "ru")
    fun theNoteIsOfferedAndLabelledInRussian() {
        open(draft().copy(language = RecipeLanguage.RU))
        compose.openStep(0)
        val texts = compose.textsOf(StepNoteTags.field(0))
        assertTrue("$texts", "Заметка автора" in texts)
        assertTrue("$texts", "Остаётся у вас, когда кто-то делает свою версию рецепта" in texts)
        assertFalse(addOffered())
    }

    // At most 500 characters: more is kept in full, marked as every limit is once the field is left — in the sheet, and
    // on the row once it is closed — and the mark goes once the note is within it again.
    @Test
    fun aNoteOver500IsMarkedOnLeavingTheFieldAndKeptWhole() {
        val id = open(draft(secondNote = "short"))
        val long = "n".repeat(RecipeBounds.MAX_STEP_NOTE + 1)
        compose.openStep(1)
        type(StepNoteTags.field(1), long)
        assertFalse("not while typing", exists(StepNoteTags.mark(1)))
        enter(EditorTags.stepText(1))
        compose.waitUntil(PATIENCE_MS) { exists(StepNoteTags.mark(1)) }
        val en = InterfaceLanguage.ENGLISH
        val said = listOf(string(R.string.editor_mark_characters_over, QuantityText.whole(500, en), QuantityText.whole(501, en)))
        assertEquals(said, compose.textsOf(StepNoteTags.mark(1)))
        assertEquals(long, stored(id).steps[1].note)
        compose.closeStep()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(StepSheetTags.noteMark(1)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(said, compose.textsOf(StepSheetTags.noteMark(1)))

        compose.openStep(1)
        type(StepNoteTags.field(1), "n".repeat(500))
        compose.waitUntil(PATIENCE_MS) { !exists(StepNoteTags.mark(1)) }
        compose.closeStep()
        assertFalse(compose.onAllNodes(hasTestTag(StepSheetTags.noteMark(1)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    // A frozen recipe shows the note on the step's row, muted, read-only, and nothing opens to change one.
    @Test
    fun aFrozenRecipeShowsTheNoteReadOnly() {
        open(draft(RecipeState.PUBLISHED, secondNote = note))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.step(1)) }
        assertTrue(compose.textsOf(StepSheetTags.line(1)).contains("Author's note: $note"))
        val shown = compose.onNodeWithTag(StepSheetTags.note(1), useUnmergedTree = true).fetchSemanticsNode()
        assertFalse(shown.config.contains(SemanticsProperties.EditableText))
        val layout = mutableListOf<TextLayoutResult>().also { shown.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()
        assertEquals(lightColorScheme().onSurfaceVariant, layout.layoutInput.style.color)
        assertTrue(compose.onAllNodes(hasTestTag(StepSheetTags.note(0)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertFalse(exists(StepNoteTags.field(1)))
        compose.onNodeWithTag(StepSheetTags.line(1), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(compose.stepSheetOpen())
    }
}
