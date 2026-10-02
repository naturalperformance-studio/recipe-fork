package app.recipefork.app.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.file.awaitFileMessage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.file.FileParent
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-062 (8), FLOW-063: in serverless mode every own recipe's entry offers its export, which hands the recipe's
 * file to the system's share and save sheet and changes nothing in the recipe; in standard mode no export exists.
 * Each test passes the mode it is about.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportFromTheBookTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        FileProviderCache.clear()
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
        importFiles.deleteRecursively()
    }

    private fun own(title: String, state: RecipeState = RecipeState.LOCAL, minutesAgo: Long = 0): Recipe {
        val sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, sent = sent).inState(state)
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun show(mode: Mode, fontScale: Float? = null) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                MaterialTheme { LibraryScreen(session, recipes, mode) }
            }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun bringIntoView(recipe: Recipe) {
        val entry = LibraryTags.entry(recipe.id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(entry))
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun everyState() = RecipeState.entries.mapIndexed { i, state -> own("Soup in $state", state, i.toLong()) }

    // FLOW-063: in serverless mode every entry of the own-recipes view offers Export, whatever the recipe's state.
    @Test
    fun inServerlessModeEveryOwnEntryOffersExport() {
        val all = everyState()
        show(Mode.Serverless)
        all.forEach { recipe ->
            bringIntoView(recipe)
            assertEquals("${recipe.state}", listOf("Export"), compose.textsOf(LibraryTags.export(recipe.id.value)))
        }
        // A11Y-003, A11Y-005: the offer is large enough and says what it does.
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 3)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 3)
    }

    @Test
    @Config(qualifiers = "ru")
    fun theOfferReadsInRussian() {
        val recipe = own("Суп")
        show(Mode.Serverless)
        bringIntoView(recipe)
        assertEquals(listOf("Экспорт"), compose.textsOf(LibraryTags.export(recipe.id.value)))
    }

    // FLOW-062 (8): in standard mode no export control exists anywhere on the book's screen.
    @Test
    fun inStandardModeNoExportExists() {
        val all = everyState()
        show(Mode.Standard)
        all.forEach { recipe ->
            bringIntoView(recipe)
            assertTrue("${recipe.state}", exists(LibraryTags.entry(recipe.id.value)))
            assertEquals(0, compose.onAllNodes(hasTestTag(LibraryTags.export(recipe.id.value))).fetchSemanticsNodes().size)
        }
        val export = hasText("Export", substring = true, ignoreCase = true) or
            hasContentDescription("Export", substring = true, ignoreCase = true)
        assertEquals(0, compose.onAllNodes(export, useUnmergedTree = true).fetchSemanticsNodes().size)
        // Nor in the marked view.
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(export, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // FLOW-063 (7): Export opens the system's share and save sheet on the recipe's file, and sends it nowhere itself.
    // FLOW-063 (1): the recipe exported is unchanged and stays editable.
    @Test
    fun exportOpensTheShareSheetOnTheFileAndChangesNothing() {
        val recipe = own("Soup").copy(
            servings = 2,
            ingredients = listOf(
                Ingredient(0, null, freeTextName = "beans", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
            ),
            steps = listOf(Step(0, "Boil the beans", listOf(IngredientReference(0, 9)), Timer(10.minutes))),
        ).also { runBlocking { recipes.save(it) } }
        show(Mode.Serverless)
        bringIntoView(recipe)
        val activity = shadowOf(compose.activity)
        assertNull(activity.peekNextStartedActivity())
        compose.onNodeWithTag(LibraryTags.export(recipe.id.value)).performClick()
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivity() != null }
        val sheet = activity.nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        assertEquals("Export “Soup”", sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        @Suppress("DEPRECATION")
        val send = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(RecipeFile.MIME_TYPE, send.type)
        assertNull(send.`package`)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertTrue(uri.lastPathSegment!!.endsWith(".recipefork"))
        val document = ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == RecipeFile.DOCUMENT }
            zip.readBytes().toString(Charsets.UTF_8)
        }
        val read = RecipeFile.decode(document)
        assertEquals(recipe.id, read.id)
        assertEquals(recipe.ingredients, read.ingredients)
        assertEquals(recipe.steps, read.steps)
        // Nothing else was started: the product sent the file nowhere.
        assertNull(activity.nextStartedActivity)

        val after = runBlocking { recipes.get(recipe.id) }!!
        assertEquals(recipe, after)
        assertTrue(after.state.editable)
        assertTrue(compose.textsOf(LibraryTags.entry(recipe.id.value)).contains("Draft"))
        assertTrue(exists(LibraryTags.delete(recipe.id.value)))
    }

    // The entry stays readable on a 360 dp-wide phone at font scale 2.0: the title keeps the list's whole width less
    // the entry's padding, the offers sit below it within the list, and each offer's label is laid out whole.
    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun theEntryStaysReadableAtLargeFontScale() = readable("Shakshuka with feta", "Export", "Delete")

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun theEntryStaysReadableAtLargeFontScaleInRussian() = readable("Шакшука с фетой", "Экспорт", "Удалить")

    private fun readable(title: String, export: String, delete: String) {
        val recipe = own(title)
        show(Mode.Serverless, fontScale = 2f)
        bringIntoView(recipe)
        val id = recipe.id.value
        val list = compose.onNodeWithTag(LibraryTags.OWN_LIST).fetchSemanticsNode().boundsInRoot
        val entry = compose.onNodeWithTag(LibraryTags.entry(id)).fetchSemanticsNode().boundsInRoot
        assertEquals("the entry takes the list's whole width", list.width, entry.width, 0.5f)
        val titleNode = compose.onNodeWithTag(LibraryTags.entryTitle(id), useUnmergedTree = true).fetchSemanticsNode()
        val titleLayout = layoutOf(titleNode.config)
        assertEquals(title, titleLayout.layoutInput.text.text)
        val room = titleLayout.layoutInput.constraints.maxWidth
        assertTrue("the title has the entry's width less its padding ($room px of ${entry.width} px)",
            room + 0.5f >= entry.width - with(compose.density) { 32.dp.toPx() })
        listOf(LibraryTags.export(id) to export, LibraryTags.delete(id) to delete).forEach { (tag, label) ->
            val button = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val bounds = button.boundsInRoot
            assertTrue("$label lies within the list: $bounds in $list", bounds.left >= list.left && bounds.right <= list.right)
            assertTrue("$label stands below the title", bounds.top >= titleNode.boundsInRoot.bottom)
            val text = compose.onNodeWithTag(tag, useUnmergedTree = true).onChildren().fetchSemanticsNodes()
                .single { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
            val layout = layoutOf(text.config)
            assertEquals(label, layout.layoutInput.text.text)
            assertEquals("$label is laid out on one line", 1, layout.lineCount)
        }
        val exportBounds = compose.onNodeWithTag(LibraryTags.export(id)).fetchSemanticsNode().boundsInRoot
        val deleteBounds = compose.onNodeWithTag(LibraryTags.delete(id)).fetchSemanticsNode().boundsInRoot
        assertTrue("the offers do not overlap", exportBounds.right <= deleteBounds.left || deleteBounds.right <= exportBounds.left)
    }

    // FLOW-063 (2, 6): in serverless mode a found recipe's entry in the marked view offers Export too, beside Remove; its
    // file carries the author and the parent the found recipe's own file stated, and the found recipe stays as it was.
    @Test
    fun inServerlessModeAFoundEntryOffersExportCarryingTheStatedAuthorAndParent() {
        val id = found()
        val before = runBlocking { recipes.get(id) }!!
        show(Mode.Serverless)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.export(id.value)), PATIENCE_MS)
        assertEquals(listOf("Export"), compose.textsOf(LibraryTags.export(id.value)))
        assertTrue(exists(LibraryTags.remove(id.value)))
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 4)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 4)
        val activity = shadowOf(compose.activity)
        compose.onNodeWithTag(LibraryTags.export(id.value)).performClick()
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivity() != null }
        val sheet = activity.nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        assertEquals("Export “Bean soup”", sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        @Suppress("DEPRECATION")
        val uri = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        val read = RecipeFile.decode(
            ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
                generateSequence { zip.nextEntry }.first { it.name == RecipeFile.DOCUMENT }
                zip.readBytes().toString(Charsets.UTF_8)
            },
        )
        assertEquals(id, read.id)
        assertEquals("anna", read.author)
        assertEquals(FileParent(RecipeId("p-1"), "Soup", "boris"), read.parent)
        assertEquals(before, runBlocking { recipes.get(id) })
        assertEquals(listOf(id.value), runBlocking { recipes.marked().first() }.map { it.recipeId })
    }

    // FLOW-062 (8): in standard mode a found recipe's entry offers no export.
    @Test
    fun inStandardModeAFoundEntryOffersNoExport() {
        val id = found()
        show(Mode.Standard)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.remove(id.value)), PATIENCE_MS)
        assertTrue(!exists(LibraryTags.export(id.value)))
        val export = hasText("Export", substring = true, ignoreCase = true) or
            hasContentDescription("Export", substring = true, ignoreCase = true)
        assertEquals(0, compose.onAllNodes(export, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    private val importFiles = File(context.cacheDir, "export-from-the-book-files")

    private fun found(): RecipeId {
        val content = TestRecipeFiles.soup()
        val outcome = runBlocking { RecipeImport(recipes, importFiles, clock = { now }).import { TestRecipeFiles.file(content).inputStream() } }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        return content.id
    }

    // An export that fails says so, and opens no sheet.
    @Test
    fun aFailedExportSaysSo() = failed("Couldn't export the recipe")

    @Test
    @Config(qualifiers = "ru")
    fun aFailedExportSaysSoInRussian() = failed("Не удалось экспортировать рецепт")

    private fun failed(message: String) {
        val recipe = own("Soup")
        show(Mode.Serverless)
        bringIntoView(recipe)
        val cache = context.cacheDir
        File(cache, "exports").deleteRecursively()
        assertTrue(cache.setWritable(false))
        try {
            compose.onNodeWithTag(LibraryTags.export(recipe.id.value)).performClick()
            compose.awaitFileMessage(message)
        } finally {
            cache.setWritable(true)
        }
        assertNull(shadowOf(compose.activity).nextStartedActivity)
        assertEquals(recipe, runBlocking { recipes.get(recipe.id) })
    }

    private fun layoutOf(config: androidx.compose.ui.semantics.SemanticsConfiguration): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }
}
