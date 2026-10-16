package app.recipefork.app.card

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.shopping.HeldShopping
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * v0.39, T-279, scenario 6 — the original text one tap away:
 *
 * - A recipe that keeps the text it was made from shows "Original text" in its card's ⋮, after Print and Share as PDF and before Delete;
 *   a recipe with none shows no such item.
 * - It opens a sheet titled so, holding the text exactly as stored, selectable, with one action, Copy, which puts the
 *   whole text on the clipboard, closes the sheet and has the card say "Text copied".
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class CardSourceTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val pasted = "Блины\n\n  Мука — 200 г\nМолоко 500 мл\t(тёплое)\n\nСмешать. Жарить 2 мин.  "

    private fun pancakes(text: String?) =
        Recipe.create(now, null).copy(title = "Блины", steps = listOf(Step(0, "Жарить."))).withSourceText(text)

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun open(recipe: Recipe, tab: CardMode = CardMode.VIEW) {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(tab), shopping = HeldShopping(), ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            MaterialTheme {
                CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}, session = TextCardSession.inMemory())
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(0)) }
        compose.waitForIdle()
    }

    private fun openTheSheet() {
        compose.openTheCardMenu()
        compose.onNodeWithTag(CardTags.SOURCE_TEXT).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SourceTextTags.TEXT) }
        compose.waitForIdle()
    }

    private fun clipboard() = compose.activity.getSystemService(ClipboardManager::class.java)

    @Test
    fun aRecipeWithASourceTextOffersItInTheMenuAfterPrintingAndBeforeDelete() {
        open(pancakes(pasted))
        assertEquals(listOf(CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.SOURCE_TEXT, CardTags.DELETE), compose.cardMenuItems())
        compose.openTheCardMenu()
        assertEquals(listOf("Original text"), compose.textsOf(CardTags.SOURCE_TEXT))
        compose.assertCardTarget(CardTags.SOURCE_TEXT)
        compose.closeTheCardMenu()
    }

    @Test
    fun cookingsMenuOffersItToo() {
        open(pancakes(pasted), CardMode.COOKING)
        assertTrue(CardTags.SOURCE_TEXT in compose.cardMenuItems())
    }

    @Test
    fun aRecipeWithNoSourceTextShowsNoSuchItem() {
        open(pancakes(null))
        assertEquals(listOf(CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.DELETE), compose.cardMenuItems())
        assertFalse(exists(CardTags.SOURCE_TEXT))
    }

    @Test
    fun theSheetShowsTheTextExactlyAsStoredAndSelectable() {
        open(pancakes(pasted))
        openTheSheet()
        assertEquals(listOf("Original text"), compose.textsOf(SourceTextTags.TITLE))
        assertEquals(listOf(pasted), compose.textsOf(SourceTextTags.TEXT))
        compose.onNode(hasTestTag(SourceTextTags.TEXT) and hasAnyAncestor(hasTestTag(SourceTextTags.SELECTABLE)), useUnmergedTree = true)
            .assertExists()
        assertEquals(listOf("Copy"), compose.textsOf(SourceTextTags.COPY))
        compose.assertCardTarget(SourceTextTags.COPY)
    }

    @Test
    fun copyPutsTheWholeTextOnTheClipboardClosesTheSheetAndSaysSo() {
        open(pancakes(pasted))
        openTheSheet()
        compose.onNodeWithTag(SourceTextTags.COPY).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(SourceTextTags.SHEET) }
        val clip = clipboard().primaryClip!!
        assertEquals(1, clip.itemCount)
        assertEquals(pasted, clip.getItemAt(0).text.toString())
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasText("Text copied") and hasAnyAncestor(hasTestTag(CardTags.SNACKBAR)), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1
        }
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianTheItemTheSheetAndTheStatementReadInRussian() {
        open(pancakes(pasted))
        compose.openTheCardMenu()
        assertEquals(listOf("Исходный текст"), compose.textsOf(CardTags.SOURCE_TEXT))
        compose.onNodeWithTag(CardTags.SOURCE_TEXT).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SourceTextTags.TEXT) }
        assertEquals(listOf("Исходный текст"), compose.textsOf(SourceTextTags.TITLE))
        assertEquals(listOf("Копировать"), compose.textsOf(SourceTextTags.COPY))
        compose.onNodeWithTag(SourceTextTags.COPY).performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasText("Текст скопирован") and hasAnyAncestor(hasTestTag(CardTags.SNACKBAR)), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1
        }
    }
}
