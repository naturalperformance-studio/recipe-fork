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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
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
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rational
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
 * DOM-023 in the editor: every ingredient row — a dictionary entry or free text — offers a note on how the ingredient
 * is prepared, labelled apart from the name (whose qualifier says what the ingredient is); the note is held trimmed,
 * blank as none, kept in full over its limit of 60 characters and marked on leaving the field as every limit is
 * (FLOW-028); a frozen recipe shows it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class IngredientNoteEditorTest {
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

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(TEST_INGREDIENTS) }
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

    /** What the focused field asks the keyboard for. */
    private fun keyboardAskedFor(): EditorInfo {
        fun View.all(): Sequence<View> = sequenceOf(this) + ((this as? ViewGroup)?.children?.flatMap { it.all() } ?: emptySequence())
        val editor = EditorInfo()
        compose.runOnUiThread {
            compose.activity.window.decorView.all().first { it.onCheckIsTextEditor() }.onCreateInputConnection(editor)
        }
        return editor
    }

    /** Chicken from the dictionary, qualified "skinless", and onion in the author's own words. */
    private fun draft(state: RecipeState = RecipeState.LOCAL, note: String? = null) = Recipe.create(created, owner = null).copy(
        title = "Stew",
        language = RecipeLanguage.EN,
        servings = 2,
        ingredients = listOf(
            Ingredient(0, 10, qualifier = "skinless", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "onion", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(1), divisible = true, note = note),
        ),
    ).inState(state)

    // Each row, a dictionary entry's or free text's, offers the note under a label of its own, apart from the name's.
    @Test
    fun everyRowOffersANoteLabelledApartFromTheName() {
        val id = open(draft())
        listOf(0, 1).forEach { position ->
            compose.onNodeWithTag(IngredientNoteTags.field(position)).performScrollTo()
            val note = compose.textsOf(IngredientNoteTags.field(position))
            val name = compose.textsOf(EditorTags.ingredientName(position))
            assertTrue("$note", "How to prepare" in note)
            assertTrue("$name", "Ingredient" in name && "How to prepare" !in name)
        }
        // Empty and entered, the field shows its example.
        enter(IngredientNoteTags.field(0))
        assertTrue("e.g. finely chopped" in compose.textsOf(IngredientNoteTags.field(0)))
        // The note goes on after the name: no capital forced on it.
        assertEquals(0, keyboardAskedFor().inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        // Typed, it is held trimmed; the field keeps what was typed, the space before the next word included.
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performTextReplacement("cut into ")
        compose.waitForIdle()
        assertEquals("cut into ", fieldText(IngredientNoteTags.field(0)))
        assertEquals("cut into", stored(id).ingredients[0].note)
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performTextReplacement("  cut into cubes ")
        type(IngredientNoteTags.field(1), "finely chopped")
        // Left, the field reads as held.
        assertEquals("cut into cubes", fieldText(IngredientNoteTags.field(0)))

        val rows = stored(id).ingredients
        assertEquals(listOf("cut into cubes", "finely chopped"), rows.map { it.note })
        // The note is no part of the name: the entry keeps its qualifier, the free text its words.
        assertEquals(10L, rows[0].dictionaryEntry)
        assertEquals("skinless", rows[0].qualifier)
        assertEquals("onion", rows[1].freeTextName)
        assertTrue(fieldText(EditorTags.ingredientName(0)).endsWith("skinless"))

        // Cleared, or blank, the row holds no note.
        type(IngredientNoteTags.field(1), "   ")
        enter(EditorTags.TITLE)
        assertNull(stored(id).ingredients[1].note)
        assertEquals("", fieldText(IngredientNoteTags.field(1)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theNoteIsLabelledInRussian() {
        open(draft().copy(language = RecipeLanguage.RU))
        enter(IngredientNoteTags.field(1))
        val texts = compose.textsOf(IngredientNoteTags.field(1))
        assertTrue("$texts", "Как подготовить" in texts)
        assertTrue("$texts", "например, мелко нарезать" in texts)
    }

    // At most 60 characters: more is kept in full, marked as every limit is once the field is left, and the mark goes
    // once the note is within it again.
    @Test
    fun aNoteOver60IsMarkedOnLeavingTheFieldAndKeptWhole() {
        val id = open(draft())
        val long = "sliced into thin half-moons, then soaked in cold water for ten minutes"
        assertTrue(long.length > RecipeBounds.MAX_NOTE)
        type(IngredientNoteTags.field(1), long)
        assertFalse("not while typing", exists(IngredientNoteTags.mark(1)))
        enter(EditorTags.TITLE)
        compose.waitUntil(PATIENCE_MS) { exists(IngredientNoteTags.mark(1)) }
        val en = InterfaceLanguage.ENGLISH
        assertEquals(
            listOf(string(R.string.editor_mark_characters_over, QuantityText.whole(60, en), QuantityText.whole(long.length.toLong(), en))),
            compose.textsOf(IngredientNoteTags.mark(1)),
        )
        assertEquals(listOf(string(R.string.editor_mark_warning)), compose.descriptionOf(IngredientNoteTags.mark(1)))
        assertEquals(long, stored(id).ingredients[1].note)
        assertFalse(exists(IngredientNoteTags.mark(0)))

        // Exactly 60 is within the limit.
        type(IngredientNoteTags.field(1), "n".repeat(60))
        compose.waitUntil(PATIENCE_MS) { !exists(IngredientNoteTags.mark(1)) }
    }

    // A frozen recipe shows the note under the name, muted, and offers no field for it.
    @Test
    fun aFrozenRecipeShowsTheNote() {
        open(draft(RecipeState.PUBLISHED, note = "finely chopped"))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredient(1)) }
        assertEquals(listOf("onion", "finely chopped"), compose.textsOf(EditorTags.ingredient(1)).take(2))
        val note = compose.onNodeWithTag(IngredientNoteTags.field(1), useUnmergedTree = true).fetchSemanticsNode()
        assertFalse(note.config.contains(SemanticsProperties.EditableText))
        val layout = mutableListOf<TextLayoutResult>().also { note.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()
        assertEquals(lightColorScheme().onSurfaceVariant, layout.layoutInput.style.color)
        assertTrue(compose.onAllNodes(hasTestTag(IngredientNoteTags.field(0)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
