package app.recipefork.app.editor

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * L10N-001 (1), A11Y-003 (1) and A11Y-005 (1) on every state of the screens this task adds — the empty own-recipes
 * view with its offer, the editor on a new recipe, its tag editor and the editor on a recipe that is not editable
 * (seeded, and opened on it directly) — in each interface language.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditorLanguageAndAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val frozenTitle = "Plum cake"
    private val content = tagLabels(SAMPLE_DICTIONARIES) + frozenTitle
    private val opened = mutableStateOf<RecipeId?>(null)

    /** What the screen reader says each element does, in one language. */
    private class Actions(
        val write: String,
        val back: String,
        val toRussian: String,
        val toEnglish: String,
        val clear: String,
        val addItalian: String,
        val removeItalian: String,
        val italian: String,
    )

    private val english = Actions(
        write = "Write a recipe",
        back = "Return to My recipe book",
        toRussian = "Declare the recipe written in Russian",
        toEnglish = "Declare the recipe written in English",
        clear = "Clear the declared language",
        addItalian = "Add the tag Italian",
        removeItalian = "Remove the tag Italian",
        italian = "Italian",
    )

    private val russian = Actions(
        write = "Написать рецепт",
        back = "Вернуться в «Мою книгу рецептов»",
        toRussian = "Указать, что рецепт написан по-русски",
        toEnglish = "Указать, что рецепт написан по-английски",
        clear = "Сбросить указанный язык",
        addItalian = "Добавить тег итальянская",
        removeItalian = "Убрать тег итальянская",
        italian = "итальянская",
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

    /** Every check on the screen as it stands: its text in its own language, its targets and its labels. */
    private fun checkTheScreen(expectInteractive: Int) {
        compose.waitForIdle()
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = expectInteractive)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = expectInteractive)
    }

    /** The click of the node tagged [tag] is announced as [action], which is not merely what the node shows. */
    private fun assertAnnounces(tag: String, action: String) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        assertEquals(action, AccessibilityChecks.clickActionLabel(node) ?: AccessibilityChecks.announcedLabel(node))
    }

    @Test
    fun inEnglish() = everyScreen(english)

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() = everyScreen(russian)

    private fun everyScreen(w: Actions) {
        compose.setContent {
            MaterialTheme { LibraryOrEditor(session, recipes, EditorEnvironment(active, device.catalog(context)), opened) }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)

        // The empty own-recipes view: its statement and its one offer.
        checkTheScreen(expectInteractive = 3)
        assertAnnounces(LibraryTags.WRITE, w.write)

        // The editor on a new recipe, with the tag editor offering the dictionary.
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.offered(10)), PATIENCE_MS)
        checkTheScreen(expectInteractive = 7) // back, title, language, clear, search, three offered tags
        assertAnnounces(EditorTags.BACK, w.back)
        val language = compose.onNodeWithTag(EditorTags.LANGUAGE).fetchSemanticsNode()
        val change = AccessibilityChecks.clickActionLabel(language)
        assertEquals(if (w === english) w.toRussian else w.toEnglish, change)
        assertNotEquals(change, AccessibilityChecks.announcedLabel(language))
        assertAnnounces(EditorTags.LANGUAGE_CLEAR, w.clear)
        assertAnnounces(EditorTags.offered(10), w.addItalian)

        // A tag chosen, and a search that matches nothing.
        compose.onNodeWithTag(EditorTags.offered(10)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.chosen(10)), PATIENCE_MS)
        assertAnnounces(EditorTags.remove(10), w.removeItalian)
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performTextInput("zzz")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.NO_MATCH), PATIENCE_MS)
        checkTheScreen(expectInteractive = 6) // back, title, language, clear, remove, search
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performTextReplacement("")

        // The declared language cleared.
        compose.onNodeWithTag(EditorTags.LANGUAGE_CLEAR).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.LANGUAGE_CLEAR), PATIENCE_MS)
        checkTheScreen(expectInteractive = 5)

        // Back in the own view, which lists the recipe.
        val id = requireNotNull(session.editing.value)
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id)), PATIENCE_MS)

        // The editor on a recipe that cannot be changed, seeded and opened on it directly.
        val frozen = Recipe.create(Instant.parse("2026-09-29T13:00:00Z"), null).copy(title = frozenTitle, tags = setOf(10L))
            .inState(RecipeState.PUBLISHED)
        runBlocking { recipes.save(frozen) }
        compose.runOnIdle { opened.value = frozen.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FROZEN), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.chosen(10)), PATIENCE_MS)
        checkTheScreen(expectInteractive = 1)
        assertAnnounces(EditorTags.BACK, w.back)
    }
}
