package app.recipefork.app.paste

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.media.SourceSheetTags
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import kotlinx.coroutines.flow.first
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
 * T-280, scenarios 1, 5 and 7: "New recipe" in My recipes offers "Write from scratch" and "Paste text"; the paste
 * screen opens holding the clipboard's text; "Read" makes an editable recipe of the owner's from it — its ingredients
 * bound to the dictionary where known, its source text the text as given — and opens the editor on it; a text with no
 * recipe in it says so under the field and writes nothing. Words are asserted as the reader reads them.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PasteTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle()).apply { choose(LibraryView.OWN) }
    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private val pancakes = """
        Блины на молоке

        Ингредиенты
        - 200 г муки
        - 500 мл молока
        - 2 яйца
        - 1 ст. л. сахара

        Приготовление
        1. Смешать муку, молоко, яйца и сахар.
        2. Дать тесту постоять 15 минут.
        3. Жарить на сковороде по 1 минуте с каждой стороны.
    """.trimIndent()

    private val biscuits = """
        Butter Biscuits
        Serves 4

        Ingredients
        - 2 cups flour
        - 1 tbsp butter
        - 1 cup milk

        Method
        1. Rub the butter into the flour, then whisk in the milk.
        2. Bake for 25 min.
    """.trimIndent()

    private val prose = "Купить хлеб, молоко и яйца. Позвонить маме."

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

    private fun show() {
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = environment) } }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun awaitTag(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun written(): List<String> = runBlocking { book.database.recipes().newest(10).first() }.map { it.recipeId }

    private fun stored(id: String): Recipe = requireNotNull(runBlocking { recipes.get(RecipeId(id)) })

    private fun clipboard(text: String?) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        if (text == null) manager.clearPrimaryClip() else manager.setPrimaryClip(ClipData.newPlainText("recipe", text))
    }

    private fun fieldText(): String =
        compose.onNodeWithTag(PasteTags.FIELD).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /** "New recipe" — the empty book's offer — then "Paste text". */
    private fun openPaste() {
        awaitTag(LibraryTags.WRITE)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        awaitTag(LibraryTags.PASTE)
        compose.onNodeWithTag(LibraryTags.PASTE).performClick()
        awaitTag(PasteTags.FIELD)
    }

    private fun read() {
        compose.onNodeWithTag(PasteTags.READ).performClick()
    }

    // Criterion 1: "New recipe" offers both ways to start, in a sheet titled by it — from the book's floating action too.
    @Test
    fun newRecipeOffersWritingFromScratchAndPastingText() = assertTheChoice("New recipe", "Write from scratch", "Paste text")

    @Test
    @Config(qualifiers = "ru")
    fun newRecipeOffersBothInRussian() = assertTheChoice("Новый рецепт", "Написать с нуля", "Вставить текст")

    private fun assertTheChoice(title: String, write: String, paste: String) {
        runBlocking { recipes.save(Recipe.create(now, null).copy(title = "Soup")) }
        show()
        awaitTag(LibraryTags.NEW)
        compose.onNodeWithTag(LibraryTags.NEW).performClick()
        awaitTag(LibraryTags.PASTE)
        assertEquals(listOf(title), compose.textsOf(SourceSheetTags.TITLE))
        assertEquals(listOf(write), compose.textsOf(LibraryTags.FROM_SCRATCH))
        assertEquals(listOf(paste), compose.textsOf(LibraryTags.PASTE))
        // Nothing is written until a way is taken.
        assertEquals(1, written().size)
    }

    // Criterion 2: the paste screen opens holding the clipboard's text, under its title, with Read.
    @Test
    fun pasteTextOpensWithTheClipboardsText() {
        clipboard(pancakes)
        show()
        openPaste()
        assertEquals(pancakes, fieldText())
        assertEquals(listOf("Paste text"), compose.textsOf(PasteTags.TITLE))
        assertEquals(listOf("Read"), compose.textsOf(PasteTags.READ))
        assertTrue(session.pasting.value)
        assertTrue(written().isEmpty())
    }

    @Test
    @Config(qualifiers = "ru")
    fun thePasteScreenSpeaksRussian() {
        clipboard(null)
        show()
        openPaste()
        assertEquals("", fieldText())
        assertEquals(listOf("Вставить текст"), compose.textsOf(PasteTags.TITLE))
        assertEquals(listOf("Разобрать"), compose.textsOf(PasteTags.READ))
    }

    // Back leaves the paste screen for the book, writing nothing.
    @Test
    fun backReturnsToTheBook() {
        clipboard(pancakes)
        show()
        openPaste()
        compose.onNodeWithTag(PasteTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(PasteTags.SCREEN) }
        assertFalse(session.pasting.value)
        assertTrue(written().isEmpty())
        awaitTag(LibraryTags.WRITE)
    }

    // Criterion 4, scenario 1: Read saves a recipe of the owner's with the text as its source, and opens the editor on it.
    @Test
    fun readingARussianRecipeSavesItAndOpensTheEditor() {
        clipboard(pancakes)
        show()
        openPaste()
        read()
        compose.waitUntil(PATIENCE_MS) { session.editing.value != null && exists(EditorTags.FORM) }
        val id = requireNotNull(session.editing.value)
        assertEquals(listOf(id), written())
        assertFalse(session.pasting.value)
        val recipe = stored(id)
        assertEquals(pancakes, recipe.sourceText)
        assertEquals(RecipeState.LOCAL, recipe.state)
        assertNull(recipe.owner)
        assertEquals("Блины на молоке", recipe.title)
        assertEquals(RecipeLanguage.RU, recipe.language)
        assertEquals(4, recipe.ingredients.size)
        // "муки" is bound to the dictionary's flour.
        assertEquals(FLOUR, recipe.ingredients[0].dictionaryEntry)
        assertEquals(3, recipe.steps.size)
        assertEquals(listOf(15L), recipe.steps[1].timers.map { it.duration.inWholeMinutes })
        // Leaving the editor shows the new recipe's card, as for a recipe written from scratch.
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { session.viewing.value == id }
    }

    // Scenario 5: English reads the same way.
    @Test
    fun anEnglishRecipeReadsTheSameWay() {
        clipboard(null)
        show()
        openPaste()
        compose.onNodeWithTag(PasteTags.FIELD).performTextReplacement(biscuits)
        read()
        compose.waitUntil(PATIENCE_MS) { session.editing.value != null && exists(EditorTags.FORM) }
        val recipe = stored(requireNotNull(session.editing.value))
        assertEquals(biscuits, recipe.sourceText)
        assertEquals("Butter Biscuits", recipe.title)
        assertEquals(RecipeLanguage.EN, recipe.language)
        assertEquals(3, recipe.ingredients.size)
        assertEquals(FLOUR, recipe.ingredients[0].dictionaryEntry)
    }

    // Criterion 5, scenario 7: a text with no recipe keeps the screen, says so under the field, and writes nothing.
    @Test
    fun proseSaysNoRecipeAndWritesNothing() = assertNoRecipe("No recipe found in this text — it needs ingredients and steps.")

    @Test
    @Config(qualifiers = "ru")
    fun proseSaysNoRecipeInRussian() = assertNoRecipe("Не нашёл в тексте рецепта — нужны ингредиенты и шаги.")

    private fun assertNoRecipe(message: String) {
        clipboard(prose)
        show()
        openPaste()
        read()
        awaitTag(PasteTags.MESSAGE)
        assertEquals(listOf(message), compose.textsOf(PasteTags.MESSAGE))
        val field = compose.onNodeWithTag(PasteTags.FIELD).fetchSemanticsNode().config
        assertTrue(SemanticsProperties.Error in field)
        assertEquals(prose, fieldText())
        assertTrue(written().isEmpty())
        assertNull(session.editing.value)
        assertTrue(session.pasting.value)
        // Changing the text takes the message away.
        compose.onNodeWithTag(PasteTags.FIELD).performTextReplacement(prose + " И ещё.")
        compose.waitUntil(PATIENCE_MS) { !exists(PasteTags.MESSAGE) }
    }

    private companion object {
        /** The shipped dictionary's "мука" / "flour". */
        const val FLOUR = 5000L
    }
}
