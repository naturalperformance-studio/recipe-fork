package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.FileMessageTags
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.Refusal
import app.recipefork.app.file.awaitFileMessage
import app.recipefork.app.file.closeFileMessage
import app.recipefork.app.file.message
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.RecipeId
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
import org.robolectric.annotation.GraphicsMode

/**
 * What became of a file — every import outcome and a failed export — is said whole, in a dialog closed by its OK, and
 * never in a toast that cuts it short: at the narrowest width and the largest text, in either language, with a recipe
 * named by its title, shortened when long.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileMessageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    // The font scale is the device's own, set by the test's configuration: a dialog is a window of its own, and
    // takes its density from the device, not from the screen beneath it.
    private fun show() {
        compose.setContent { MaterialTheme { LibraryScreen(session, recipes, Mode.Serverless) } }
        compose.waitForIdle()
    }

    /**
     * Every outcome an import has, with a title as long as a person might give a recipe in the interface's language,
     * and a failed export.
     */
    private val everyMessage: List<FileMessage>
        get() {
            val title = if (context.resources.configuration.locales[0].language == "ru") RUSSIAN_TITLE else LONG_TITLE
            return listOf(
                ImportOutcome.Added(RecipeId("a"), title),
                ImportOutcome.AlreadyInBook(RecipeId("a"), title),
            ).map { it.message() } +
                Refusal.entries.map { ImportOutcome.Refused(it).message() } +
                FileMessage(FileMessage.Kind.EXPORT_FAILED)
        }

    // Each import outcome and a failed export has a message of its own, in English and in Russian.
    @Test
    fun everyOutcomeHasAMessageOfItsOwn() {
        assertEquals(9, everyMessage.map { it.kind }.toSet().size)
        assertEquals(9, everyMessage.map { it.text(context.resources) }.toSet().size)
    }

    // The longest message of all, at 360 dp and twice the font size, is laid out whole: no line cut short or ellipsized,
    // nothing clipped by the dialog, and it is read by the screen reader.
    @Test
    @Config(qualifiers = "ru-w360dp-h640dp", fontScale = 2f)
    fun everyMessageIsSaidWholeAtTheNarrowestWidthAndLargestTextInRussian() = saidWhole()

    @Test
    @Config(qualifiers = "en-w360dp-h640dp", fontScale = 2f)
    fun everyMessageIsSaidWholeAtTheNarrowestWidthAndLargestText() = saidWhole()

    private fun saidWhole() {
        show()
        val longest = everyMessage.maxBy { it.text(context.resources).length }
        (listOf(longest) + everyMessage).forEach { message ->
            val text = message.text(context.resources)
            compose.runOnIdle { session.tell(message) }
            compose.awaitFileMessage(text)
            val node = compose.onAllNodes(hasTestTag(FileMessageTags.TEXT), useUnmergedTree = true).fetchSemanticsNodes().single()
            val layout = layoutOf(node)
            assertEquals(text, layout.layoutInput.text.text)
            assertEquals("measured at twice the font size", 2f, layout.layoutInput.density.fontScale, 0.001f)
            // Every line's ink lies within the room the text has: none runs past its right edge. (The paragraph's own
            // width may round a pixel or so past the text's, which cuts nothing.)
            (0 until layout.lineCount).forEach { line ->
                assertTrue("line $line of “$text” runs past its room", layout.getLineRight(line) <= layout.size.width + 0.5f)
            }
            assertFalse("“$text” is cut in height", layout.didOverflowHeight)
            (0 until layout.lineCount).forEach { line ->
                assertFalse("line $line of “$text” is ellipsized", layout.isLineEllipsized(line))
            }
            assertEquals("“$text” ends on its last line", text.length, layout.getLineEnd(layout.lineCount - 1))
            // Nothing of the text is clipped: what is shown of it is all of it.
            val shown = node.boundsInRoot
            assertEquals("“$text” is shown in full height", node.size.height.toFloat(), shown.height, 1f)
            assertEquals("“$text” is shown in full width", node.size.width.toFloat(), shown.width, 1f)
            assertReadAloud(node, text)
            AccessibilityChecks.assertTouchTargets(compose)
            AccessibilityChecks.assertInteractiveElementsLabelled(compose)
            compose.closeFileMessage()
        }
    }

    /** The screen reader reads [text] from the dialog: a text node of the dialog's pane, visible to it. */
    private fun assertReadAloud(node: SemanticsNode, text: String) {
        assertEquals(listOf(text), node.config.getOrNull(SemanticsProperties.Text)?.map { it.text })
        assertTrue(SemanticsProperties.InvisibleToUser !in node.config)
        assertTrue(SemanticsProperties.HideFromAccessibility !in node.config)
        assertEquals(1, compose.onAllNodes(hasTestTag(FileMessageTags.TEXT) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().size)
    }

    // "Already in the book" names the recipe by its title: whole when short, its first 40 characters and "…" when long,
    // so that the sentence itself is always whole.
    @Test
    fun alreadyInTheBookNamesTheRecipeShortenedWhenLong() {
        val short = ImportOutcome.AlreadyInBook(RecipeId("a"), "Bean soup").message()
        assertEquals("“Bean soup” is already in your recipe book, so nothing was added", short.text(context.resources))
        val long = ImportOutcome.AlreadyInBook(RecipeId("a"), LONG_TITLE).message()
        assertEquals(
            "“${LONG_TITLE.take(40)}…” is already in your recipe book, so nothing was added",
            long.text(context.resources),
        )
        val untitled = ImportOutcome.AlreadyInBook(RecipeId("a"), " ").message()
        assertEquals("“Untitled recipe” is already in your recipe book, so nothing was added", untitled.text(context.resources))
    }

    @Test
    @Config(qualifiers = "ru")
    fun alreadyInTheBookNamesTheRecipeShortenedWhenLongInRussian() {
        val long = ImportOutcome.AlreadyInBook(RecipeId("a"), RUSSIAN_TITLE).message()
        assertEquals("«${RUSSIAN_TITLE.take(40).trimEnd()}…» уже есть в вашей книге рецептов, ничего не добавлено", long.text(context.resources))
    }

    @Test
    fun aTitleIsShortenedWithoutSplittingACharacter() {
        assertEquals("a".repeat(40), FileMessage.shortened("a".repeat(40)))
        assertEquals("a".repeat(39) + "…", FileMessage.shortened("a".repeat(39) + "🍲" + "bb"))
        assertEquals("ab…", FileMessage.shortened("ab" + " ".repeat(38) + "cd"))
    }

    // A message stays until closed by its OK, and once closed is gone: a recreated screen shows it again only while
    // it is not closed, and kept in saved state it comes back once, not twice, after the process ends.
    @Test
    fun aMessageIsShownUntilClosedAndOnlyOnce() {
        show()
        val message = FileMessage(FileMessage.Kind.ALREADY_IN_BOOK, "Bean soup")
        compose.runOnIdle { session.tell(message) }
        compose.awaitFileMessage(message.text(context.resources))
        val saved = SavedStateHandle()
        LibrarySession(saved).tell(message)
        val restored = LibrarySession(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertEquals(message, restored.message.value)
        compose.closeFileMessage()
        assertNull(session.message.value)
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private companion object {
        const val LONG_TITLE =
            "Grandmother's slow-cooked bean soup with smoked paprika, roasted garlic and a long list of winter herbs"
        const val RUSSIAN_TITLE =
            "Бабушкин томлёный фасолевый суп с копчёной паприкой, печёным чесноком и длинным списком зимних трав"
    }
}
