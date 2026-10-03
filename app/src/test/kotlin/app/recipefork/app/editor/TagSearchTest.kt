package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * The editor's tag search over the tag dictionary the application ships (DOM-008): with hundreds of tags held, none
 * is listed until the author types, and then at most eight whose label has a word beginning with the text.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TagSearchTest {
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

    /** Takes the offer of the empty own view to write a recipe, and waits for the tag search. */
    private fun writeANewRecipe(): RecipeId {
        val environment = EditorEnvironment(active, device.catalog(context)) { Instant.parse("2026-10-03T12:00:00Z") }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.TAG_SEARCH), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun offered(): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(EditorTags.OFFERED)) and hasClickAction()).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    private fun type(text: String) {
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun searchText(): String =
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    // With the field empty no tag is listed, however many the dictionary holds.
    @Test
    @Config(qualifiers = "ru")
    fun anEmptyFieldListsNoTag() {
        writeANewRecipe()
        assertTrue(shipped.tags.size > 100)
        assertEquals(emptyList<String>(), offered())
        // Nor once a text is typed and wiped again.
        type("итал")
        assertEquals(listOf("итальянская"), offered())
        type("")
        assertEquals(emptyList<String>(), offered())
    }

    // Scenario 4 in Russian: «итал» offers the cuisine, «аэро» the appliance; tapping adds the tag and clears the field,
    // and the added tag is offered no more.
    @Test
    @Config(qualifiers = "ru")
    fun theBeginningOfAWordOffersTheTagAndTappingAddsIt() {
        val id = writeANewRecipe()
        type("итал")
        assertEquals("итальянская", offered().first())
        compose.onNodeWithTag(EditorTags.offered(10)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.chosen(10)), PATIENCE_MS)
        assertEquals("", searchText())
        assertEquals(emptyList<String>(), offered())
        assertEquals(setOf(10L), stored(id).tags)
        // Added, it is offered no more.
        type("итал")
        assertEquals(emptyList<String>(), offered())
        compose.onNodeWithTag(EditorTags.NO_MATCH).assertExists()

        type("аэро")
        assertEquals(listOf("аэрогриль"), offered())
        val airFryer = shipped.tags.single { it.labelRu == "аэрогриль" }.id
        compose.onNodeWithTag(EditorTags.offered(airFryer)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.chosen(airFryer)), PATIENCE_MS)
        assertEquals(setOf(10L, airFryer), stored(id).tags)
    }

    // The same in English, by either label.
    @Test
    fun inEnglishATagIsFoundByEitherLabelAndNamedInEnglish() {
        writeANewRecipe()
        type("air")
        assertEquals(listOf("air fryer"), offered())
        type("аэро")
        assertEquals(listOf("air fryer"), offered())
        type("ITAL")
        assertEquals(listOf("Italian"), offered())
    }

    // ё and е are one letter to the search.
    @Test
    @Config(qualifiers = "ru")
    fun yoAndYeAreOneLetter() {
        writeANewRecipe()
        type("слоен")
        assertEquals(listOf("слоёное тесто"), offered())
        type("слоён")
        assertEquals(listOf("слоёное тесто"), offered())
    }

    // A text that begins many words offers no more than eight, the labels that begin with it first.
    @Test
    @Config(qualifiers = "ru")
    fun atMostEightAreOfferedTheBestFirst() {
        writeANewRecipe()
        type("с")
        assertEquals(8, offered().size)
        type("выпеч")
        val baking = offered()
        assertEquals("без выпечки", baking.last())
        assertTrue(baking.dropLast(1).all { it.startsWith("выпечка") })
    }

    // DOM-008: the typed text is never committed as a tag, by the keyboard's action or otherwise.
    @Test
    @Config(qualifiers = "ru")
    fun typedTextIsNeverCommitted() {
        val id = writeANewRecipe()
        type("итал")
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performImeAction()
        compose.waitForIdle()
        type("моя кухня")
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performImeAction()
        compose.waitForIdle()
        assertEquals(emptySet<Long>(), stored(id).tags)
        compose.onNodeWithTag(EditorTags.NO_MATCH).assertExists()
    }
}
