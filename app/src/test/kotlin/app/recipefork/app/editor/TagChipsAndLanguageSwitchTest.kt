package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Scenario 5 of v0.18: the recipe's tags are input chips with a cross, the dictionary's offers are suggestion chips
 * that add the tag on a tap, and the declared language is a single-choice segmented switch [RU | EN] — each telling
 * the screen reader its state.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TagChipsAndLanguageSwitchTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val opened = mutableStateOf<RecipeId?>(null)
    private val shipped: DictionariesDto =
        DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText())

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(shipped) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun writeANewRecipe(): RecipeId {
        val environment = EditorEnvironment(active, device.catalog(context)) { Instant.parse("2026-10-04T12:00:00Z") }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.TAG_SEARCH), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun selected(tag: String): Boolean? = node(tag).config.getOrNull(SemanticsProperties.Selected)

    private fun type(text: String) {
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    // Typing still adds a tag; the offer shows as a suggestion chip — a button named by the tag, whose tap adds it.
    @Test
    fun anOfferedTagIsASuggestionChipThatAddsTheTagOnATap() {
        val id = writeANewRecipe()
        type("ital")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.offered(10)), PATIENCE_MS)
        val offer = node(EditorTags.offered(10))
        assertEquals(Role.Button, offer.config.getOrNull(SemanticsProperties.Role))
        assertEquals(listOf("Italian"), offer.config[SemanticsProperties.Text].map { it.text })
        assertEquals("Add the tag Italian", AccessibilityChecks.clickActionLabel(offer))
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 6)

        compose.onNodeWithTag(EditorTags.offered(10)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.chosen(10)), PATIENCE_MS)
        assertEquals(setOf(10L), stored(id).tags)
        assertEquals("", node(EditorTags.TAG_SEARCH).config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag(EditorTags.offered(10)).assertDoesNotExist()
    }

    // A tag of the recipe is an input chip with a cross: the chip names the tag, and its cross — the one thing on it that
    // does anything (T-271) — is a 48 dp button named for the removal, whose tap removes the tag.
    @Test
    fun aChosenTagIsAnInputChipWithARemoveIcon() {
        val id = writeANewRecipe()
        compose.addTag("ital", 10)
        compose.addTag("geor", 11)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.remove(11)), PATIENCE_MS)
        listOf(10L to "Italian", 11L to "Georgian").forEach { (tag, label) ->
            assertEquals(listOf(label), compose.textsOf(EditorTags.chip(tag)))
            val cross = node(EditorTags.remove(tag))
            assertEquals(Role.Button, cross.config.getOrNull(SemanticsProperties.Role))
            assertEquals("Remove the tag $label", AccessibilityChecks.announcedLabel(cross))
            // The cross is drawn inside the chip.
            val icon = compose.onNodeWithTag(EditorTags.removeIcon(tag), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val chip = node(EditorTags.chip(tag)).boundsInRoot
            assertTrue("the cross $icon is outside the chip $chip", chip.contains(icon.topLeft) && chip.contains(icon.bottomRight))
        }
        // Two chips share a line.
        val italian = node(EditorTags.chosen(10)).boundsInRoot
        val georgian = node(EditorTags.chosen(11)).boundsInRoot
        assertTrue("the chips are not on one line", georgian.left >= italian.right && georgian.top < italian.bottom)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 7)

        compose.onNodeWithTag(EditorTags.remove(10)).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.chosen(10)), PATIENCE_MS)
        assertEquals(setOf(11L), stored(id).tags)
        compose.onNodeWithTag(EditorTags.chosen(11)).assertExists()
    }

    // The language is a single-choice segmented switch: two radio-button segments in one group, the declared one
    // selected; a tap declares the other, a tap on the declared one keeps it, and Clear leaves neither selected.
    @Test
    fun theLanguageIsASingleChoiceSegmentedSwitch() {
        val id = writeANewRecipe()
        val ru = EditorTags.language(RecipeLanguage.RU)
        val en = EditorTags.language(RecipeLanguage.EN)
        listOf(ru, en).forEach { tag ->
            val segment = node(tag)
            assertEquals(Role.RadioButton, segment.config.getOrNull(SemanticsProperties.Role))
            assertTrue("$tag is outside a selectable group", generateSequence(segment.parent) { it.parent }.any {
                SemanticsProperties.SelectableGroup in it.config
            })
        }
        assertEquals(listOf("Russian"), compose.descriptionOf(ru))
        assertEquals(listOf("English"), compose.descriptionOf(en))
        // RU on the left, EN on its right.
        assertTrue(node(en).boundsInRoot.left > node(ru).boundsInRoot.center.x)
        assertEquals(false, selected(ru))
        assertEquals(true, selected(en))

        compose.declareLanguage(RecipeLanguage.RU)
        compose.waitForIdle()
        assertEquals(RecipeLanguage.RU, stored(id).language)
        assertEquals(true, selected(ru))
        assertEquals(false, selected(en))
        compose.declareLanguage(RecipeLanguage.RU)
        compose.waitForIdle()
        assertEquals(RecipeLanguage.RU, stored(id).language)
        assertEquals(RecipeLanguage.RU, compose.declaredLanguage())

        compose.onNodeWithTag(EditorTags.LANGUAGE_CLEAR).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.LANGUAGE_CLEAR), PATIENCE_MS)
        assertNull(stored(id).language)
        assertNull(compose.declaredLanguage())
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 5)
    }
}
