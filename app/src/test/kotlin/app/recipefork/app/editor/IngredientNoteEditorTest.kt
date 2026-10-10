package app.recipefork.app.editor

import android.content.Context
import android.text.InputType
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

    // Each row, a dictionary entry's or free text's, offers the note in its sheet under a label of its own, apart from
    // the name's.
    @Test
    fun everyRowOffersANoteLabelledApartFromTheName() {
        val id = open(draft())
        listOf(0, 1).forEach { position ->
            compose.openIngredient(position)
            val note = compose.textsOf(IngredientNoteTags.field(position))
            val name = compose.textsOf(EditorTags.ingredientName(position))
            assertTrue("$note", "How to prepare" in note)
            assertTrue("$name", "Ingredient" in name && "How to prepare" !in name)
        }
        // Empty and entered, the field shows its example.
        compose.openIngredient(0)
        enter(IngredientNoteTags.field(0))
        assertTrue("e.g. finely chopped" in compose.textsOf(IngredientNoteTags.field(0)))
        // The note goes on after the name: no capital forced on it.
        assertEquals(0, compose.keyboardAskedFor().inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        // Typed, it is held trimmed; the field keeps what was typed, the space before the next word included.
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performTextReplacement("cut into ")
        compose.waitForIdle()
        assertEquals("cut into ", fieldText(IngredientNoteTags.field(0)))
        assertEquals("cut into", stored(id).ingredients[0].note)
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performTextReplacement("  cut into cubes ")
        // Left, the field reads as held.
        enter(EditorTags.ingredientName(0))
        assertEquals("cut into cubes", fieldText(IngredientNoteTags.field(0)))
        compose.openIngredient(1)
        type(IngredientNoteTags.field(1), "finely chopped")
        compose.closeIngredient()

        val rows = stored(id).ingredients
        assertEquals(listOf("cut into cubes", "finely chopped"), rows.map { it.note })
        // The note is no part of the name: the entry keeps its qualifier, the free text its words.
        assertEquals(10L, rows[0].dictionaryEntry)
        assertEquals("skinless", rows[0].qualifier)
        assertEquals("onion", rows[1].freeTextName)
        compose.openIngredient(0)
        assertTrue(fieldText(EditorTags.ingredientName(0)).endsWith("skinless"))
        // The rows read the notes after their names, as the card does.
        compose.closeIngredient()
        assertEquals(listOf("500 g chicken, skinless — cut into cubes", "1 pc onion — finely chopped"), listOf(0, 1).map { compose.ingredientLine(it) })

        // Cleared, or blank, the row holds no note.
        compose.openIngredient(1)
        type(IngredientNoteTags.field(1), "   ")
        compose.closeIngredient()
        assertNull(stored(id).ingredients[1].note)
        compose.openIngredient(1)
        assertEquals("", fieldText(IngredientNoteTags.field(1)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theNoteIsLabelledInRussian() {
        open(draft().copy(language = RecipeLanguage.RU))
        compose.openIngredient(1)
        enter(IngredientNoteTags.field(1))
        val texts = compose.textsOf(IngredientNoteTags.field(1))
        assertTrue("$texts", "Как подготовить" in texts)
        assertTrue("$texts", "например, мелко нарезать" in texts)
    }

    // At most 60 characters: more is kept in full, marked as every limit is once the field is left — in the sheet, and
    // on the row once it is closed — and the mark goes once the note is within it again.
    @Test
    fun aNoteOver60IsMarkedOnLeavingTheFieldAndKeptWhole() {
        val id = open(draft())
        val long = "sliced into thin half-moons, then soaked in cold water for ten minutes"
        assertTrue(long.length > RecipeBounds.MAX_NOTE)
        compose.openIngredient(1)
        type(IngredientNoteTags.field(1), long)
        assertFalse("not while typing", exists(IngredientNoteTags.mark(1)))
        enter(EditorTags.ingredientName(1))
        compose.waitUntil(PATIENCE_MS) { exists(IngredientNoteTags.mark(1)) }
        val en = InterfaceLanguage.ENGLISH
        val said = listOf(string(R.string.editor_mark_characters_over, QuantityText.whole(60, en), QuantityText.whole(long.length.toLong(), en)))
        assertEquals(said, compose.textsOf(IngredientNoteTags.mark(1)))
        assertEquals(listOf(string(R.string.editor_mark_warning)), compose.descriptionOf(IngredientNoteTags.mark(1)))
        assertEquals(long, stored(id).ingredients[1].note)
        compose.closeIngredient()
        compose.waitUntil(PATIENCE_MS) { exists(IngredientSheetTags.noteMark(1)) }
        assertEquals(said, compose.textsOf(IngredientSheetTags.noteMark(1)))
        assertFalse(exists(IngredientSheetTags.noteMark(0)))

        // Exactly 60 is within the limit.
        compose.openIngredient(1)
        type(IngredientNoteTags.field(1), "n".repeat(60))
        compose.waitUntil(PATIENCE_MS) { !exists(IngredientNoteTags.mark(1)) }
        compose.closeIngredient()
        assertFalse(exists(IngredientSheetTags.noteMark(1)))
    }

    // A frozen recipe shows the note after the name, muted, as the card does, and offers no field for it.
    @Test
    fun aFrozenRecipeShowsTheNote() {
        open(draft(RecipeState.PUBLISHED, note = "finely chopped"))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredient(1)) }
        assertEquals(listOf("1 pc onion — finely chopped"), compose.textsOf(EditorTags.ingredient(1)))
        val line = compose.onNodeWithTag(IngredientSheetTags.line(1), useUnmergedTree = true).fetchSemanticsNode()
        assertFalse(line.config.contains(SemanticsProperties.EditableText))
        val text = line.config[SemanticsProperties.Text].single()
        val muted = text.spanStyles.single { it.item.color == lightColorScheme().onSurfaceVariant }
        assertEquals(" — finely chopped", text.text.substring(muted.start, muted.end))
        assertEquals(0, compose.onAllNodes(hasTestTag(IngredientNoteTags.field(1)), useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
