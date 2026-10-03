package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
import app.recipefork.app.card.backThroughTheCard
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
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
 * The editor as the author meets it: on a new recipe, opened by the offer of the own-recipes view of the active
 * book; and on a seeded recipe, opened on it directly rather than through its card (FLOW-029 (1)).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditorScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-09-29T12:00:00Z")
    private var now = created.plusSeconds(60)
    private var shown = false
    private val opened = mutableStateOf<RecipeId?>(null)

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
        val environment = EditorEnvironment(active, device.catalog(context)) { now }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    /** From an empty book: takes the offer to write, and returns the new recipe the editor opened on. */
    private fun writeANewRecipe(): RecipeId {
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    /** Opens the editor directly on the recipe [id], as a test fixture: the application routes there through the card. */
    private fun openDirectly(id: RecipeId) {
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    /** Seeds an own recipe in [state] and opens the editor on it directly. */
    private fun openOwn(state: RecipeState, sent: SentStatus = SentStatus.NEVER): Recipe {
        val recipe = Recipe.create(created, null)
            .copy(title = "Beetroot salad", language = RecipeLanguage.RU, tags = setOf(11L), sent = sent).inState(state)
        runBlocking { recipes.save(recipe) }
        if (!shown) show()
        shown = true
        openDirectly(recipe.id)
        return recipe
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun languageShown(): List<String> = compose.textsOf(EditorTags.LANGUAGE)

    private fun offeredTags(): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(EditorTags.OFFERED)) and hasClickAction()).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    private fun hasClickAction() = androidx.compose.ui.test.hasClickAction()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    // FLOW-024 (4), L10N-004 (1), DOM-006 (3), in English.
    @Test
    fun aNewRecipesLanguageIsTheInterfaceLanguageBesideTheTitleAndChangedInOneAction() =
        languageBesideTheTitle(RecipeLanguage.EN, "Language", "English", "Russian")

    // The same, in Russian: the interface language is Russian, so the new recipe is declared Russian.
    @Test
    @Config(qualifiers = "ru")
    fun inRussianANewRecipeIsDeclaredRussianBesideTheTitle() =
        languageBesideTheTitle(RecipeLanguage.RU, "Язык", "Русский", "Английский")

    private fun languageBesideTheTitle(expected: RecipeLanguage, heading: String, name: String, other: String) {
        val id = writeANewRecipe()
        // Already set, before the author does anything: in the book and on the screen.
        assertEquals(expected, stored(id).language)
        assertEquals(listOf(heading, name), languageShown())
        // Beside the title: on the same line, to its right, and on screen without scrolling — it cannot be missed.
        val title = bounds(EditorTags.TITLE)
        val language = bounds(EditorTags.LANGUAGE)
        assertTrue("the language is not to the right of the title", language.left >= title.right)
        assertTrue("the language is not on the title's line", language.top < title.bottom && title.top < language.bottom)
        compose.onNodeWithTag(EditorTags.LANGUAGE).assertIsDisplayed()
        compose.onNodeWithTag(EditorTags.TITLE).assertIsDisplayed()
        // One action changes it to the other language, and one more changes it back.
        compose.onNodeWithTag(EditorTags.LANGUAGE).performClick()
        compose.waitForIdle()
        assertEquals(listOf(heading, other), languageShown())
        assertEquals(if (expected == RecipeLanguage.EN) RecipeLanguage.RU else RecipeLanguage.EN, stored(id).language)
        compose.onNodeWithTag(EditorTags.LANGUAGE).performClick()
        compose.waitForIdle()
        assertEquals(expected, stored(id).language)
    }

    // DOM-006 (3): the declaration is empty only when the author clears it.
    @Test
    fun theDeclaredLanguageIsEmptyOnlyOnceTheAuthorClearsIt() {
        val id = writeANewRecipe()
        assertEquals(RecipeLanguage.EN, stored(id).language)
        // Writing the other parts leaves it set.
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Borscht")
        compose.onNodeWithTag(EditorTags.offered(10)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(RecipeLanguage.EN, stored(id).language)
        // Clearing is the one way to empty it.
        compose.onNodeWithTag(EditorTags.LANGUAGE_CLEAR).performScrollTo().performClick()
        compose.waitForIdle()
        assertNull(stored(id).language)
        assertEquals(listOf("Language", "Not declared"), languageShown())
        compose.onNodeWithTag(EditorTags.LANGUAGE_CLEAR).assertDoesNotExist()
        // And one action declares it again, in the interface language.
        compose.onNodeWithTag(EditorTags.LANGUAGE).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(RecipeLanguage.EN, stored(id).language)
    }

    // FLOW-024 (6): the author leaves the editor by its back action and returns to the same recipe, opened directly.
    @Test
    fun leavingByTheBackActionAndReturningKeepsTheOrderAndEverythingEntered() =
        leaveAndReturn { compose.onNodeWithTag(EditorTags.BACK).performClick() }

    // FLOW-024 (6): the same, leaving by the system's back.
    @Test
    fun leavingByTheSystemBackAndReturningKeepsTheOrderAndEverythingEntered() =
        leaveAndReturn { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } }

    private fun leaveAndReturn(leave: () -> Unit) {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Borscht")
        compose.onNodeWithTag(EditorTags.LANGUAGE).performClick()
        compose.onNodeWithTag(EditorTags.offered(11)).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performTextInput("des")
        compose.onNodeWithTag(EditorTags.offered(12)).performClick()
        // Left at once, with the last change just made.
        leave()
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.SCREEN).assertDoesNotExist()
        compose.onNode(hasText("Borscht") and hasAnyAncestor(hasTestTag(LibraryTags.OWN_LIST))).assertExists()

        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.chosen(12)), PATIENCE_MS)
        assertEquals(listOf("Title", "Borscht"), compose.textsOf(EditorTags.TITLE).filter { it.isNotBlank() }.distinct())
        assertEquals(listOf("Language", "Russian"), languageShown())
        compose.onNodeWithTag(EditorTags.chosen(11)).assertExists()
        compose.onNodeWithTag(EditorTags.chosen(12)).assertExists()
        // The same order: the title with the language beside it, then the tags.
        assertTrue(bounds(EditorTags.LANGUAGE).left >= bounds(EditorTags.TITLE).right)
        assertTrue(bounds(EditorTags.TAGS).top >= bounds(EditorTags.TITLE).bottom)
        assertTrue(bounds(EditorTags.TAGS).top >= bounds(EditorTags.LANGUAGE).top)
        val recipe = stored(id)
        assertEquals("Borscht", recipe.title)
        assertEquals(RecipeLanguage.RU, recipe.language)
        assertEquals(setOf(11L, 12L), recipe.tags)
    }

    // FLOW-002 (3): local, private and rejected accept an edit, with a connection.
    @Test
    fun anEditableStateAcceptsAnEditWithAConnection() {
        assertFalse(Connection.isOffline(context))
        editAccepted()
    }

    // FLOW-002 (3): and without one.
    @Test
    fun anEditableStateAcceptsAnEditWithoutAConnection() {
        Connection.offline(context)
        assertTrue(Connection.isOffline(context))
        editAccepted()
    }

    private fun editAccepted() {
        listOf(
            RecipeState.LOCAL to SentStatus.NEVER,
            RecipeState.PRIVATE to SentStatus.SENT,
            RecipeState.REJECTED to SentStatus.SENT,
        ).forEach { (state, sent) ->
            val recipe = openOwn(state, sent)
            compose.onNodeWithTag(EditorTags.FROZEN).assertDoesNotExist()
            compose.onNodeWithTag(EditorTags.TITLE).performTextReplacement("Beetroot salad with dill")
            compose.onNodeWithTag(EditorTags.LANGUAGE).performClick()
            compose.onNodeWithTag(EditorTags.offered(10)).performScrollTo().performClick()
            compose.onNodeWithTag(EditorTags.remove(11)).performScrollTo().performClick()
            compose.waitForIdle()
            val edited = stored(recipe.id)
            assertEquals("$state", "Beetroot salad with dill", edited.title)
            assertEquals("$state", RecipeLanguage.EN, edited.language)
            assertEquals("$state", setOf(10L), edited.tags)
            assertEquals("$state", state, edited.state)
            assertEquals("$state", now, edited.contentChangedAt)
            // A sent recipe now holds changes not sent (FLOW-005): nothing was sent by the edit.
            assertEquals("$state", if (sent == SentStatus.SENT) SentStatus.SENT_WITH_CHANGES else sent, edited.sent)
            compose.onNodeWithTag(EditorTags.BACK).performClick()
            compose.waitForIdle()
        }
    }

    // FLOW-002 (4): pending, link_shared, published, reported_hidden and removed are not editable, with a connection.
    @Test
    fun aFrozenStateIsNotEditableWithAConnection() {
        assertFalse(Connection.isOffline(context))
        notEditable()
    }

    // FLOW-002 (4): and without one.
    @Test
    fun aFrozenStateIsNotEditableWithoutAConnection() {
        Connection.offline(context)
        notEditable()
    }

    private fun notEditable() {
        listOf(
            RecipeState.PENDING, RecipeState.LINK_SHARED, RecipeState.PUBLISHED, RecipeState.REPORTED_HIDDEN,
            RecipeState.REMOVED,
        ).forEach { state ->
            val recipe = openOwn(state, SentStatus.SENT)
            compose.onNodeWithTag(EditorTags.FROZEN).assertIsDisplayed()
            // What it holds is shown…
            assertTrue("$state", "Beetroot salad" in compose.textsOf(EditorTags.TITLE))
            compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.chosen(11)), PATIENCE_MS)
            // …and nothing on the screen changes it: no field takes text, and the only actions are to leave and to
            // read the checklist (GUIDE-003).
            val setText = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            assertEquals("$state", 0, setText.size)
            val actions = compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            assertEquals("$state", listOf(EditorTags.BACK, ChecklistTags.CONTROL), actions.map { it.config[SemanticsProperties.TestTag] })
            compose.onNodeWithTag(EditorTags.remove(11)).assertDoesNotExist()
            compose.onNodeWithTag(EditorTags.LANGUAGE_CLEAR).assertDoesNotExist()
            compose.onNodeWithTag(EditorTags.TAG_SEARCH).assertDoesNotExist()
            // Tapping the language does nothing either.
            compose.onNodeWithTag(EditorTags.LANGUAGE).performClick()
            compose.waitForIdle()
            assertEquals("$state", recipe, stored(recipe.id))
            compose.onNodeWithTag(EditorTags.BACK).performClick()
            compose.waitForIdle()
        }
    }

    // FLOW-002 (4) at the editor's own boundary: a change asked of a frozen recipe is refused and never written.
    @Test
    fun theEditorRefusesAChangeToAFrozenRecipe() {
        RecipeState.entries.forEach { state ->
            val recipe = Recipe.create(created, null).copy(title = "Held").inState(state)
            runBlocking { recipes.save(recipe) }
            val editor = RecipeEditor(recipes, recipe.id, { now }, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
            runBlocking { editor.load() }
            assertEquals("$state", state.editable, editor.setTitle("Changed"))
            assertEquals("$state", state.editable, editor.recipe.value!!.title == "Changed")
        }
    }

    private fun hasSetTextAction() = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    // DOM-008 (1): only dictionary entries are offered, and typed text that is no entry cannot be committed.
    @Test
    fun theTagEditorOffersOnlyDictionaryEntriesAndCommitsNoFreeText() {
        val id = writeANewRecipe()
        val dictionary = SAMPLE_DICTIONARIES.tags.map { it.labelEn }.toSet()
        // Looking with nothing typed, and with parts of words: everything offered is an entry.
        compose.waitUntil(PATIENCE_MS) { offeredTags().isNotEmpty() }
        assertEquals(dictionary.sorted(), offeredTags())
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performTextInput("an")
        assertEquals(listOf("Georgian", "Italian"), offeredTags())
        // A tag that is not in the dictionary: nothing is offered, and the statement says so.
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performTextReplacement("Mexican")
        assertEquals(emptyList<String>(), offeredTags())
        compose.onNodeWithTag(EditorTags.NO_MATCH).performScrollTo().assertIsDisplayed()
        // The keyboard's action commits nothing, and nothing on the screen offers to add the text as a tag.
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performImeAction()
        compose.waitForIdle()
        assertEquals(emptySet<Long>(), stored(id).tags)
        compose.onAllNodes(hasText("Mexican", substring = true) and hasClickAction() and !hasTestTag(EditorTags.TAG_SEARCH))
            .assertCountEquals(0)
        val labels = compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .mapNotNull { AccessibilityChecks.clickActionLabel(it) }
        assertTrue(labels.none { "Mexican" in it })
        // An offered entry is committed as that entry.
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performTextReplacement("ital")
        assertEquals(listOf("Italian"), offeredTags())
        compose.onNodeWithTag(EditorTags.offered(10)).performClick()
        compose.waitForIdle()
        assertEquals(setOf(10L), stored(id).tags)
        compose.onNodeWithTag(EditorTags.chosen(10)).assertExists()
        assertTrue(stored(id).tags.all { tag -> SAMPLE_DICTIONARIES.tags.any { it.id == tag } })
    }

    // FLOW-001 (1): offline, the tag editor offers the held entries exactly as it does with a connection.
    @Test
    fun offlineTheTagEditorOffersExactlyWhatItOffersOnline() {
        val id = writeANewRecipe()
        compose.waitUntil(PATIENCE_MS) { offeredTags().isNotEmpty() }
        val online = offeredTags()
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)

        Connection.offline(context)
        assertTrue(Connection.isOffline(context))
        openDirectly(id)
        compose.waitUntil(PATIENCE_MS) { offeredTags().isNotEmpty() }
        assertEquals(online, offeredTags())
        assertEquals(SAMPLE_DICTIONARIES.tags.map { it.labelEn }.sorted(), offeredTags())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(count: Int) =
        assertEquals(count, fetchSemanticsNodes().size)

}
