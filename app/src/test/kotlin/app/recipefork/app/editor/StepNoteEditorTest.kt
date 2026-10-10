package app.recipefork.app.editor

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.children
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
 * A step's author's note in the editor: offered on demand from the step's actions, under the step's text, labelled as
 * the author's with the hint that it stays with them when someone else forks the recipe; held trimmed, blank as none,
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

    /** Whether the menu of the step at [position] offers to add a note; the menu is closed again. */
    private fun offered(position: Int): Boolean {
        compose.onNodeWithTag(EditorTags.stepMenu(position)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepAction(position, RowAction.INSERT_ABOVE)) }
        val offered = exists(StepNoteTags.add(position))
        compose.onNodeWithTag(EditorTags.stepMenu(position)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepAction(position, RowAction.INSERT_ABOVE)) }
        return offered
    }

    private fun hasTestTagMatching(pattern: Regex) = SemanticsMatcher("tag matches $pattern") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.matches(pattern) == true
    }

    /** What the focused field asks the keyboard for. */
    private fun keyboardAskedFor(): EditorInfo {
        fun View.all(): Sequence<View> = sequenceOf(this) + ((this as? ViewGroup)?.children?.flatMap { it.all() } ?: emptySequence())
        val editor = EditorInfo()
        compose.runOnUiThread {
            compose.activity.window.decorView.all().first { it.onCheckIsTextEditor() }.onCreateInputConnection(editor)
        }
        return editor
    }

    private fun draft(state: RecipeState = RecipeState.LOCAL, secondNote: String? = null) = Recipe.create(created, owner = null).copy(
        title = "Borscht",
        language = RecipeLanguage.EN,
        servings = 4,
        steps = listOf(Step(0, "Fry the beet."), Step(1, "Serve.", note = secondNote)),
    ).inState(state)

    // A step offers its note on demand: "Add a note" in the step's menu, first, reveals the field, which takes the focus;
    // the note is held trimmed, the field keeping what was typed; emptied and left, the field goes and the menu offers it
    // again.
    @Test
    fun theNoteIsOfferedOnDemandAndHeldTrimmed() {
        val id = open(draft())
        assertFalse(exists(StepNoteTags.field(0)))
        compose.onNodeWithTag(EditorTags.stepMenu(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(StepNoteTags.add(0)) }
        assertEquals(listOf("Add a note"), compose.textsOf(StepNoteTags.add(0)))
        compose.onNodeWithTag(StepNoteTags.add(0)).assertHeightIsAtLeast(48.dp)
        val items = compose.onAllNodes(hasTestTagMatching(Regex("editor-step-0-(note-add|insert-above|insert-below|move-down)")))
            .fetchSemanticsNodes().sortedBy { it.positionInRoot.y }.map { it.config[SemanticsProperties.TestTag] }
        assertEquals(StepNoteTags.add(0), items.first())
        compose.onNodeWithTag(StepNoteTags.add(0)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(StepNoteTags.field(0)) && focused(StepNoteTags.field(0)) }
        assertFalse(offered(0))
        val texts = compose.textsOf(StepNoteTags.field(0))
        assertTrue("$texts", "Author's note" in texts)
        assertTrue("$texts", "Stays with you when someone else forks the recipe" in texts)
        // Free text, begun with a capital, over several lines.
        assertTrue(keyboardAskedFor().inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)

        compose.onNodeWithTag(StepNoteTags.field(0)).performTextReplacement("  Бабушка всегда ")
        compose.waitForIdle()
        assertEquals("  Бабушка всегда ", fieldText(StepNoteTags.field(0)))
        assertEquals("Бабушка всегда", stored(id).steps[0].note)
        compose.onNodeWithTag(StepNoteTags.field(0)).performTextReplacement("$note ")
        enter(EditorTags.TITLE)
        // Left, the field reads as held, and stays: the step holds a note.
        assertEquals(note, fieldText(StepNoteTags.field(0)))
        assertEquals(listOf(note, null), stored(id).steps.map { it.note })
        assertFalse(offered(0))
        // The other step is untouched, a note still offered.
        assertTrue(offered(1))

        // Emptied and left, the step holds none; the field goes and the action is offered again.
        type(StepNoteTags.field(0), "   ")
        enter(EditorTags.TITLE)
        compose.waitUntil(PATIENCE_MS) { !exists(StepNoteTags.field(0)) }
        assertNull(stored(id).steps[0].note)
        assertTrue(offered(0))
    }

    // A step that already has a note shows its field, filled, and offers no action to add one.
    @Test
    fun aStepWithANoteShowsItsField() {
        open(draft(secondNote = note))
        compose.onNodeWithTag(StepNoteTags.field(1)).performScrollTo()
        assertEquals(note, fieldText(StepNoteTags.field(1)))
        assertFalse(offered(1))
        assertFalse(exists(StepNoteTags.field(0)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theNoteIsOfferedAndLabelledInRussian() {
        open(draft().copy(language = RecipeLanguage.RU))
        compose.onNodeWithTag(EditorTags.stepMenu(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(StepNoteTags.add(0)) }
        assertEquals(listOf("Добавить заметку"), compose.textsOf(StepNoteTags.add(0)))
        compose.onNodeWithTag(StepNoteTags.add(0)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(StepNoteTags.field(0)) }
        val texts = compose.textsOf(StepNoteTags.field(0))
        assertTrue("$texts", "Заметка автора" in texts)
        assertTrue("$texts", "Остаётся у вас, когда кто-то делает свою версию рецепта" in texts)
    }

    // At most 500 characters: more is kept in full, marked as every limit is once the field is left, and the mark goes
    // once the note is within it again.
    @Test
    fun aNoteOver500IsMarkedOnLeavingTheFieldAndKeptWhole() {
        val id = open(draft(secondNote = "short"))
        val long = "n".repeat(RecipeBounds.MAX_STEP_NOTE + 1)
        type(StepNoteTags.field(1), long)
        assertFalse("not while typing", exists(StepNoteTags.mark(1)))
        enter(EditorTags.TITLE)
        compose.waitUntil(PATIENCE_MS) { exists(StepNoteTags.mark(1)) }
        val en = InterfaceLanguage.ENGLISH
        assertEquals(
            listOf(string(R.string.editor_mark_characters_over, QuantityText.whole(500, en), QuantityText.whole(501, en))),
            compose.textsOf(StepNoteTags.mark(1)),
        )
        assertEquals(long, stored(id).steps[1].note)

        type(StepNoteTags.field(1), "n".repeat(500))
        compose.waitUntil(PATIENCE_MS) { !exists(StepNoteTags.mark(1)) }
    }

    // A frozen recipe shows the note, muted, read-only, and offers nothing to add or change one.
    @Test
    fun aFrozenRecipeShowsTheNoteReadOnly() {
        open(draft(RecipeState.PUBLISHED, secondNote = note))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.step(1)) }
        assertTrue(compose.textsOf(EditorTags.step(1)).contains("Author's note: $note"))
        val shown = compose.onNodeWithTag(StepNoteTags.field(1), useUnmergedTree = true).fetchSemanticsNode()
        assertFalse(shown.config.contains(SemanticsProperties.EditableText))
        val layout = mutableListOf<TextLayoutResult>().also { shown.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()
        assertEquals(lightColorScheme().onSurfaceVariant, layout.layoutInput.style.color)
        assertTrue(compose.onAllNodes(hasTestTag(StepNoteTags.field(0)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // No menu to add one.
        assertFalse(exists(EditorTags.stepMenu(0)))
    }
}
