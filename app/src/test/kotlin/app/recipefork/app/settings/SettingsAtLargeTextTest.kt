package app.recipefork.app.settings

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.backup.BookBackup
import app.recipefork.app.backup.BookDocuments
import app.recipefork.app.backup.BookFileReader
import app.recipefork.app.backup.BookFileWriter
import app.recipefork.app.backup.BookRestore
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.data.HeldThemeSetting
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.kitchen.KITCHEN_DICTIONARY
import app.recipefork.app.kitchen.KitchenEnvironment
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.WholeWords
import app.recipefork.app.ui.appColorScheme
import app.recipefork.core.scaling.Rational
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * The settings and their dialogs at the largest text (v0.38, T-273, scenario 1; A11Y-004): at twice the font scale on a
 * 320 dp phone, in English and Russian, no word of the settings list — the backup section and My kitchen's row among
 * it — nor of the dialogs of the language, the theme, the units, the usual servings, the clearing of saved media and the
 * restore breaks inside itself or is cut. The usual servings' count, the card's "Servings: 4" beside − and +, keeps its
 * words whole: under its choice's label where they fit there, else from the dialog's edge, drawn smaller as far as they
 * must. The restore question into a book that holds recipes offers its two ways as rows of its own — Add, the safe one,
 * first; Replace under it in the error colour — with Cancel its one button, so that no buttons stack.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class SettingsAtLargeTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val session = LibrarySession(SavedStateHandle())
    private val usual = HeldUsualServings(Rational.of(4))
    private val kitchenStore = HeldKitchen(setOf(KITCHEN_DICTIONARY.first().id))
    private val kitchen = KitchenEnvironment(kitchenStore, EquipmentSource { KITCHEN_DICTIONARY })
    private val backup = BookBackup(active, kitchenStore, NoDocuments, BookFileWriter(context), BookFileReader(context), BookRestore(context.filesDir, kitchenStore))

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun under(tag: String): SemanticsMatcher = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun dp(px: Float) = px / compose.density.density

    /** The settings of the serverless build, its backup section shown, over the book. */
    private fun showSettings() {
        val editor = EditorEnvironment(active, NoTags) { Instant.parse("2026-10-13T12:00:00Z") }
        val card = CardEnvironment(usual, ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            AppTheme {
                AppNavHost(session, recipes, Mode.Serverless, card = card, editor = editor, themeSetting = HeldThemeSetting(), kitchen = kitchen, backup = backup)
            }
        }
        compose.runOnIdle { session.openSettings() }
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.BACKUP_RESTORE) }
        // Counted: My kitchen's ticks and the saved media.
        compose.waitUntil(PATIENCE_MS) {
            listOf(SettingsTags.KITCHEN, SettingsTags.CLEAR_MEDIA).none { compose.textsOf(SettingsTags.value(it)).any { text -> text.endsWith("…") } }
        }
        compose.waitForIdle()
    }

    /** Opens the dialog tagged [dialog] from the item tagged [item], checks its words, and closes it with [close]. */
    private fun dialogKeepsWordsWhole(item: String, dialog: String, close: String) {
        compose.onNodeWithTag(item).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(dialog) }
        compose.waitForIdle()
        WholeWords.assertWhole(compose, under(dialog), dialog)
        compose.onNodeWithTag(close).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(dialog) }
    }

    // --- The list and its dialogs -----------------------------------------------------------------------------------

    private fun settingsKeepWordsWhole() {
        showSettings()
        val shown = WholeWords.assertWhole(compose, under(SettingsTags.SCREEN), "the settings")
        // The list is read through to its end: the drag side, the media, the backup section are among its texts.
        listOf(SettingsTags.HANDLE_RIGHT, SettingsTags.CLEAR_MEDIA, SettingsTags.KITCHEN, SettingsTags.BACKUP_SAVE, SettingsTags.BACKUP_RESTORE)
            .forEach { tag -> compose.textsOf(tag).forEach { assertTrue("$it is not checked", it in shown) } }
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 8)
        dialogKeepsWordsWhole(SettingsTags.LANGUAGE, LanguageTags.DIALOG, LanguageTags.CANCEL)
        dialogKeepsWordsWhole(SettingsTags.THEME, SettingsTags.THEME_DIALOG, SettingsTags.THEME_CANCEL)
        dialogKeepsWordsWhole(SettingsTags.UNITS, SettingsTags.UNITS_DIALOG, SettingsTags.UNITS_CANCEL)
        dialogKeepsWordsWhole(SettingsTags.USUAL, SettingsTags.USUAL_DIALOG, SettingsTags.USUAL_CANCEL)
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleNoWordOfTheSettingsOrTheirDialogsBreaks() = settingsKeepWordsWhole()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleNoWordOfTheSettingsOrTheirDialogsBreaks() = settingsKeepWordsWhole()

    // The statement before saved media is cleared, in either build.
    private fun clearingKeepsWordsWhole(mode: Mode) {
        compose.setContent { AppTheme { ClearMediaStatement(mode, onConfirm = {}, onDecline = {}) } }
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.CLEAR_MEDIA_STATEMENT) }
        compose.waitForIdle()
        // A text the dialog has no room for would be cut at its bottom: WholeWords says so.
        WholeWords.assertWhole(compose, under(SettingsTags.CLEAR_MEDIA_STATEMENT), "the clearing's statement in $mode")
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleTheClearingsStatementKeepsItsWordsWhole() = clearingKeepsWordsWhole(Mode.Standard)

    @Test
    @Config(fontScale = 2f)
    fun inTheServerlessBuildAtTwiceTheFontScaleTheClearingsStatementKeepsItsWordsWhole() = clearingKeepsWordsWhole(Mode.Serverless)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheClearingsStatementKeepsItsWordsWhole() = clearingKeepsWordsWhole(Mode.Standard)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianInTheServerlessBuildAtTwiceTheFontScaleTheClearingsStatementKeepsItsWordsWhole() = clearingKeepsWordsWhole(Mode.Serverless)

    // --- The usual servings' count ----------------------------------------------------------------------------------

    private fun layoutOf(tag: String): TextLayoutResult =
        mutableListOf<TextLayoutResult>().also {
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(it)
        }.single()

    /**
     * The usual count's dialog, at [count]: "Servings: [count]" keeps its words whole beside − and +, within the dialog,
     * drawn no smaller than at a font scale of 1 (titleMedium, 16 sp); under its choice's label where [indented].
     */
    private fun usualCountKeepsWordsWhole(count: Long, shown: String, indented: Boolean) {
        compose.setContent {
            AppTheme { UsualServingsDialog(Rational.of(count), notSet = false, language = servingsLanguage(), onSet = {}, onDismiss = {}) }
        }
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.USUAL_DIALOG) }
        compose.waitForIdle()
        assertEquals(listOf(shown), compose.textsOf(SettingsTags.USUAL_COUNT))
        WholeWords.assertWhole(compose, under(SettingsTags.USUAL_DIALOG), "the usual servings' dialog")
        val layout = layoutOf(SettingsTags.USUAL_COUNT)
        val drawn = layout.layoutInput.style.fontSize.value * context.resources.configuration.fontScale
        assertTrue("the count is drawn at $drawn dp", drawn >= 16f - 0.5f)
        val dialog = bounds(SettingsTags.USUAL_DIALOG)
        val text = compose.onNodeWithTag(SettingsTags.USUAL_COUNT, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val fewer = bounds(SettingsTags.USUAL_FEWER)
        val more = bounds(SettingsTags.USUAL_MORE)
        assertTrue("the count ($text) runs into − ($fewer)", text.right <= fewer.left + 0.5f)
        assertTrue("+ ($more) lies within the dialog ($dialog)", more.right <= dialog.right + 0.5f)
        val label = bounds(SettingsTags.USUAL_COUNT_CHOICE)
        val indent = dp(text.left - label.left)
        if (indented) assertEquals("the count stands under its choice's label", 48f, indent, 1f) else assertEquals("the count starts at the dialog's edge", 0f, indent, 1f)
    }

    @Test
    fun atTheUsualFontScaleTheCountStandsUnderItsChoicesLabel() = usualCountKeepsWordsWhole(4, "Servings: 4", indented = true)

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleTheCountKeepsItsWordsWhole() = usualCountKeepsWordsWhole(4, "Servings: 4", indented = false)

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleALongerCountKeepsItsWordsWhole() = usualCountKeepsWordsWhole(12, "Servings: 12", indented = false)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheCountKeepsItsWordsWhole() = usualCountKeepsWordsWhole(4, "Порций: 4", indented = false)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun inRussianAtTheUsualFontScaleTheCountStandsUnderItsChoicesLabel() = usualCountKeepsWordsWhole(4, "Порций: 4", indented = true)

    // --- The restore question ---------------------------------------------------------------------------------------

    /**
     * The restore question into a book of 3 recipes: its words whole; Add ([add]) first, Replace ([replace]) under it,
     * each a row of the dialog's body at least 48 dp tall and as wide as the body, Replace in the error colour, Add not;
     * Cancel its one button, below them. Each row and Cancel answer a tap with their own way.
     */
    private fun restoreQuestionReadsSafeFirst(add: String, replace: String, cancel: String) {
        val taken = mutableListOf<String>()
        compose.setContent {
            AppTheme { RestoreQuestion(3, onAdd = { taken += "add" }, onReplace = { taken += "replace" }, onCancel = { taken += "cancel" }) }
        }
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.BACKUP_QUESTION) }
        compose.waitForIdle()
        WholeWords.assertWhole(compose, under(SettingsTags.BACKUP_QUESTION), "the restore question")
        assertEquals(listOf(add), compose.textsOf(SettingsTags.BACKUP_ADD))
        assertEquals(listOf(replace), compose.textsOf(SettingsTags.BACKUP_REPLACE))
        assertEquals(listOf(cancel), compose.textsOf(SettingsTags.BACKUP_QUESTION_CANCEL))
        compose.onNodeWithTag(SettingsTags.BACKUP_REPLACE).performScrollTo()
        val addRow = bounds(SettingsTags.BACKUP_ADD)
        val replaceRow = bounds(SettingsTags.BACKUP_REPLACE)
        val cancelButton = bounds(SettingsTags.BACKUP_QUESTION_CANCEL)
        assertTrue("Add ($addRow) reads before Replace ($replaceRow)", addRow.bottom <= replaceRow.top + 0.5f)
        assertTrue("Cancel ($cancelButton) stands below the ways ($replaceRow)", cancelButton.top >= replaceRow.bottom - 0.5f)
        listOf(addRow, replaceRow).forEach { row ->
            assertTrue("a way's row is ${dp(row.height)} dp tall", dp(row.height) >= 47.5f)
            assertEquals("the ways are as wide as each other", addRow.width, row.width, 0.5f)
        }
        // The only buttons of the question are Cancel and the two ways: nothing else stacks under them.
        val clickable = compose.onAllNodes(under(SettingsTags.BACKUP_QUESTION) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertEquals(setOf(SettingsTags.BACKUP_ADD, SettingsTags.BACKUP_REPLACE, SettingsTags.BACKUP_QUESTION_CANCEL), clickable.toSet())
        val colours = appColorScheme(context.resources, dark = false)
        fun colour(tag: String) = WholeWords.layouts(compose, hasAnyAncestor(hasTestTag(tag))).single().layoutInput.style.color
        assertEquals("Replace's colour", colours.error, colour(SettingsTags.BACKUP_REPLACE))
        assertEquals("Add's colour", colours.onSurface, colour(SettingsTags.BACKUP_ADD))
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 3)
        listOf(SettingsTags.BACKUP_ADD, SettingsTags.BACKUP_REPLACE).forEach { compose.onNodeWithTag(it).performScrollTo().performClick() }
        compose.onNodeWithTag(SettingsTags.BACKUP_QUESTION_CANCEL).performClick()
        assertEquals(listOf("add", "replace", "cancel"), taken)
    }

    @Test
    fun theRestoreQuestionOffersAddFirstAndReplaceInTheErrorColour() =
        restoreQuestionReadsSafeFirst("Add to the book", "Replace the book", "Cancel")

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleTheRestoreQuestionReadsSafeFirstWithoutStackedButtons() =
        restoreQuestionReadsSafeFirst("Add to the book", "Replace the book", "Cancel")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheRestoreQuestionReadsSafeFirstWithoutStackedButtons() =
        restoreQuestionReadsSafeFirst("Добавить в книгу", "Заменить книгу", "Отмена")

    // Replace's confirmation: its words whole and none cut, its named confirm in the error colour.
    private fun replaceConfirmationKeepsWordsWhole(confirm: String) {
        compose.setContent { AppTheme { ReplaceConfirmation(3, onReplace = {}, onCancel = {}) } }
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.BACKUP_CONFIRMATION) }
        compose.waitForIdle()
        WholeWords.assertWhole(compose, under(SettingsTags.BACKUP_CONFIRMATION), "Replace's confirmation")
        compose.onNode(hasTestTag(SettingsTags.BACKUP_REPLACE_CONFIRM) and hasText(confirm)).assertExists()
        app.recipefork.app.library.assertConfirmInErrorColour(compose, SettingsTags.BACKUP_REPLACE_CONFIRM, SettingsTags.BACKUP_CONFIRMATION_CANCEL)
        assertFalse(exists(SettingsTags.BACKUP_QUESTION))
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleReplacesConfirmationKeepsItsWordsWhole() = replaceConfirmationKeepsWordsWhole("Replace")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleReplacesConfirmationKeepsItsWordsWhole() = replaceConfirmationKeepsWordsWhole("Заменить")

    /** Nothing is saved or restored here: no document is ever opened. */
    private object NoDocuments : BookDocuments {
        override fun output(uri: Uri): OutputStream = error("no document is written here")
        override fun input(uri: Uri): InputStream = error("no document is read here")
        override fun size(uri: Uri): Long? = null
        override fun delete(uri: Uri) = Unit
    }

    private object NoTags : TagSource {
        override suspend fun tags() = emptyList<TagEntity>()
    }
}
