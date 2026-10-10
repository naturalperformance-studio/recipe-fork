package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.editor.textsOf
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.ChecklistTags
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.IngredientSheetTags
import app.recipefork.app.editor.closeIngredient
import app.recipefork.app.editor.openIngredient
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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
 * FLOW-062 (1): in a build without the server no screen says anything of sending, the server, publication or an
 * account — not the book's entries, menus and dialogs, not the card, not the editor with its warnings and checklist.
 * Every string resource that does is enumerated, and is one only the standard build shows; the standard build still
 * shows its sending marks.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2000dp")
class ServerlessWordingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val files = File(context.cacheDir, "serverless-wording-files").also { it.deleteRecursively() }
    private val importer = RecipeImport(recipes, files, clock = { now })

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
        files.deleteRecursively()
    }

    // Every string resource that speaks of sending, the server, publication or an account, in either language, is one
    // the standard build alone shows; each has a serverless wording or is not shown at all in serverless mode.
    @Test
    fun everyStringThatSpeaksOfTheServerIsShownInTheStandardBuildAlone() {
        val res = File("src/main/res")
        val speaking = listOf("values", "values-ru").flatMap { dir ->
            LocalizationChecks.stringsIn(File(res, dir)).filterValues(::speaksOfTheServer).keys
        }.toSortedSet()
        assertEquals(STANDARD_ONLY.toSortedSet(), speaking)
        assertEquals(R.string.library_state_published_serverless, stateLabel(RecipeState.PUBLISHED, Mode.Serverless))
        RecipeState.entries.forEach { state ->
            val name = stateLabel(state, Mode.Serverless)?.let(context.resources::getResourceEntryName)
            assertTrue("$state reads $name in serverless mode", name !in STANDARD_ONLY)
        }
    }

    @Test
    fun noMainScreenSaysAnythingOfTheServer() = everyScreen()

    @Test
    @Config(qualifiers = "ru-w360dp-h2000dp")
    fun noMainScreenSaysAnythingOfTheServerInRussian() = everyScreen()

    // The standard build is unchanged: an own recipe's entry still shows its sending mark beside its state.
    @Test
    fun theStandardBuildStillShowsTheSendingMarks() {
        val draft = own("Draft soup", RecipeState.LOCAL, SentStatus.NEVER, 0)
        val published = own("Published soup", RecipeState.PUBLISHED, SentStatus.SENT_WITH_CHANGES, 1)
        show(Mode.Standard)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        awaitEntry(published)
        assertEquals(
            listOf("Draft soup", "Draft", "Not sent: kept on this device alone and lost with it"),
            entryTexts(draft),
        )
        assertEquals(listOf("Published soup", "Published", "Sent, with changes not sent"), entryTexts(published))
    }

    // Nothing can be published without the server, so an own draft, local or private, names no state there; the entry
    // closes the gap where the state was, and the screen reader reads it the same, less the state. A recipe once
    // published keeps its serverless state.
    @Test
    fun aServerlessDraftNamesNoState() {
        val local = own("Local soup", RecipeState.LOCAL, SentStatus.NEVER, 0)
        val private = own("Private soup", RecipeState.PRIVATE, SentStatus.SENT, 1)
        val published = own("Published soup", RecipeState.PUBLISHED, SentStatus.SENT, 2)
        show(Mode.Serverless)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        listOf(local, private, published).forEach(::awaitEntry)
        assertEquals(listOf("Local soup"), entryTexts(local))
        assertEquals(listOf("Private soup"), entryTexts(private))
        assertEquals(listOf("Published soup", context.getString(R.string.library_state_published_serverless)), entryTexts(published))
        assertTrue("the draft's entry is shorter by the missing line", entryHeight(local) < entryHeight(published))
        assertEquals(entryHeight(local), entryHeight(private))
    }

    // The standard build still names both kinds of draft.
    @Test
    @Config(qualifiers = "ru-w360dp-h2000dp")
    fun theStandardBuildStillNamesADraft() {
        val local = own("Суп", RecipeState.LOCAL, SentStatus.NEVER, 0)
        val private = own("Борщ", RecipeState.PRIVATE, SentStatus.SENT, 1)
        show(Mode.Standard)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        listOf(local, private).forEach(::awaitEntry)
        assertEquals(listOf("Суп", "Черновик"), entryTexts(local).take(2))
        assertEquals(listOf("Борщ", "Черновик"), entryTexts(private).take(2))
    }

    private fun everyScreen() {
        // An own recipe in every state, with every sending mark, as a device that once had the server may hold them.
        val owned = RecipeState.entries.mapIndexed { i, state ->
            val sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.entries[i % SentStatus.entries.size]
            own("Soup ${i + 1}", state, sent, i.toLong())
        }
        val local = owned.first { it.state == RecipeState.LOCAL }
        val frozen = owned.first { it.state == RecipeState.PUBLISHED }
        val found = runBlocking { importer.import { TestRecipeFiles.file(TestRecipeFiles.soup()).inputStream() } }
        assertTrue("$found", found is ImportOutcome.Added)
        val foundId = TestRecipeFiles.soup().id.value
        show(Mode.Serverless)

        // The marked view, a found recipe's menu and the notice of its removal, undone.
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(foundId)), PATIENCE_MS)
        assertSaysNothingOfTheServer("the marked view")
        compose.chooseEntryAction(foundId, LibraryTags.remove(foundId))
        compose.awaitRemovalNotice(context.getString(R.string.library_recipe_removed), context.getString(R.string.library_undo))
        assertSaysNothingOfTheServer("the notice of a removal")
        compose.takeNoticeAction(context.getString(R.string.library_undo))
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(foundId)), PATIENCE_MS)

        // The settings, their storage item counted.
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(SettingsTags.USUAL), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasText(context.getString(R.string.settings_media_counting))).fetchSemanticsNodes().isEmpty()
        }
        assertSaysNothingOfTheServer("the settings")

        // The clearing of saved media from the settings, with nothing to clear: disabled, saying why beneath its name.
        compose.onNodeWithTag(SettingsTags.CLEAR_MEDIA).performScrollTo()
        compose.waitUntilExactlyOneExists(
            hasTestTag(SettingsTags.CLEAR_MEDIA) and hasText(context.getString(R.string.settings_media_none)), PATIENCE_MS,
        )
        assertSaysNothingOfTheServer("the reason there is no media to clear")
        compose.onNodeWithTag(SettingsTags.BACK).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SettingsTags.SCREEN), PATIENCE_MS)

        // The own view: every entry, the menu of one, and the notice of a deletion, undone.
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        owned.forEach { recipe ->
            awaitEntry(recipe)
            assertSaysNothingOfTheServer("the entry of a recipe in ${recipe.state}")
        }
        compose.chooseEntryAction(local.id.value, LibraryTags.delete(local.id.value))
        compose.awaitRemovalNotice(context.getString(R.string.library_recipe_deleted), context.getString(R.string.library_undo))
        assertSaysNothingOfTheServer("the notice of a deletion")
        compose.takeNoticeAction(context.getString(R.string.library_undo))
        awaitEntry(local)

        // The card of a recipe that was published.
        compose.runOnIdle { session.openCard(frozen.id.value, LibraryView.OWN) }
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.CONTENT).assertExists()
        assertSaysNothingOfTheServer("the card")
        compose.runOnIdle { session.closeCard() }

        // The editor, frozen and editable: its marks, an ingredient in the author's own words with its warning, and
        // the checklist.
        compose.runOnIdle { session.openEditor(frozen.id.value) }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        assertSaysNothingOfTheServer("the editor on a frozen recipe")
        compose.runOnIdle { session.closeEditor() }
        compose.runOnIdle { session.openEditor(local.id.value) }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(IngredientSheetTags.freeText(0)), useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        assertSaysNothingOfTheServer("the editor and its free-text mark")
        // The free-text warning is among what the row's sheet shows, in its name's supporting text.
        compose.openIngredient(0)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(EditorTags.ingredientWarning(0)), useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        assertSaysNothingOfTheServer("the editor and its free-text warning")
        assertEquals(
            listOf(context.getString(R.string.editor_ingredient_free_text_warning_serverless)),
            compose.textsOf(EditorTags.ingredientWarning(0)),
        )
        compose.closeIngredient()
        compose.onNodeWithTag(ChecklistTags.CONTROL).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(ChecklistTags.DIALOG), PATIENCE_MS)
        assertSaysNothingOfTheServer("the checklist")
    }

    private fun own(title: String, state: RecipeState, sent: SentStatus, minutesAgo: Long): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(
            title = title,
            servings = 2,
            sent = sent,
            ingredients = listOf(
                Ingredient(0, null, freeTextName = "beans", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
            ),
        ).inState(state)
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun show(mode: Mode) {
        val editor = EditorEnvironment(active, device.catalog(context), mode = mode, clock = { now })
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent { AppTheme { AppNavHost(session, recipes, mode, editor = editor, card = card, files = importer) } }
        compose.waitForIdle()
    }

    private fun awaitEntry(recipe: Recipe) {
        val tag = LibraryTags.entry(recipe.id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(tag))
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    private fun entryTexts(recipe: Recipe): List<String> =
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.map { it.text }

    private fun entryHeight(recipe: Recipe): Int =
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).fetchSemanticsNode().size.height

    private fun assertSaysNothingOfTheServer(where: String) {
        compose.waitForIdle()
        val shown = LocalizationChecks.shownText(compose)
        assertTrue("$where shows text", shown.isNotEmpty())
        assertEquals("what $where says of the server", emptyList<String>(), shown.filter(::speaksOfTheServer))
    }

    private companion object {
        /**
         * The words — and the stems of words — that speak of sending, the server, publication, review or a private
         * link, or an account.
         */
        val WORDS = listOf(
            "send", "sent", "server", "publish", "account", "moderat", "review", "link",
            "отправ", "сервер", "публик", "аккаунт", "модерац", "проверк",
            // A private link; a reference in a step is a link (ссылка) of another kind, and is not about the server.
            "закрытой ссылк",
        )

        /** The strings only the standard build shows. */
        val STANDARD_ONLY = setOf(
            "library_state_published",
            "library_state_pending",
            "library_state_link_shared",
            "library_sent_never",
            "library_sent_sent",
            "library_sent_with_changes",
            "editor_ingredient_free_text_warning",
            "editor_checklist_5_text",
            // Its serverless form says the media cannot be fetched again (FLOW-060 (4)).
            "library_clear_media_statement",
        )

        fun speaksOfTheServer(text: String): Boolean = WORDS.any { text.contains(it, ignoreCase = true) }
    }
}
