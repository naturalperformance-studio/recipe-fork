package app.recipefork.app.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.CardTarget
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.backThroughTheCard
import app.recipefork.app.card.cardMenuItems
import app.recipefork.app.card.openTheCardMenu
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.file.awaitFileMessage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.minutes

/**
 * T-316, scenario 6 of v0.43 (decisions 2026-10-15 #7): the card's ⋮ offers Export wherever the book's entries do — in
 * serverless mode, on an own recipe and a found one alike — and it does exactly what the entry's Export does: the same
 * file, handed to the same share and save sheet, the recipe unchanged; a failure is said as the book says it. In
 * standard mode the card offers no export, as the book does not.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ExportFromTheCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private val importFiles = File(context.cacheDir, "export-from-the-card-files")

    @Before
    fun setUp() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::registerIdlingResource)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
        FileProviderCache.clear()
    }

    @After
    fun close() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        book.close()
        device.close()
        timers.close()
        importFiles.deleteRecursively()
    }

    private fun soup(): Recipe = Recipe.create(now, owner = null).copy(
        title = "Soup",
        servings = 2,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "beans", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
        ),
        steps = listOf(Step(0, "Boil the beans", listOf(IngredientReference(0, 9)), Timer(10.minutes))),
    ).also { runBlocking { recipes.save(it) } }

    private fun found(): RecipeId {
        val content = TestRecipeFiles.soup()
        val outcome = runBlocking { RecipeImport(recipes, importFiles, clock = { now }).import { TestRecipeFiles.file(content).inputStream() } }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        return content.id
    }

    /** The app in [mode], [id]'s card open over the book. */
    private fun showCard(id: RecipeId, mode: Mode = Mode.Serverless, from: LibraryView = LibraryView.OWN) {
        session.openCard(id.value, from)
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card) {
                AppTheme { AppNavHost(session, recipes, mode, card = card) }
            }
        }
        compose.awaitCard()
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** What the share sheet opened next was given: its title, the name of the file it was handed and its document. */
    private fun shared(): Triple<String, String, String> {
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivity() != null }
        val sheet = activity.nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        @Suppress("DEPRECATION")
        val send = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(RecipeFile.MIME_TYPE, send.type)
        assertNull("the product sends the file nowhere itself", send.`package`)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        val name = uri.lastPathSegment!!
        assertTrue(name, name.endsWith(".recipefork"))
        val document = ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == RecipeFile.DOCUMENT }
            zip.readBytes().toString(Charsets.UTF_8)
        }
        return Triple(sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString(), name, document)
    }

    private fun exportFromTheCard() {
        compose.openTheCardMenu()
        compose.onNodeWithTag(CardTags.EXPORT).assertHeightIsAtLeast(CardTarget)
        compose.onNodeWithTag(CardTags.EXPORT).performClick()
    }

    // The card's Export hands the same file to the same sheet as the book entry's; nothing else is started, and the
    // recipe stays as it was.
    @Test
    fun theCardsExportSharesTheFileTheBooksEntryDoes() {
        val recipe = soup()
        showCard(recipe.id)
        assertNull(shadowOf(compose.activity).peekNextStartedActivity())
        exportFromTheCard()
        val fromTheCard = shared()
        val (title, _, document) = fromTheCard
        assertEquals("Export “Soup”", title)
        val read = RecipeFile.decode(document)
        assertEquals(recipe.id, read.id)
        assertEquals(recipe.ingredients, read.ingredients)
        assertEquals(recipe.steps, read.steps)
        assertNull("nothing else was started", shadowOf(compose.activity).nextStartedActivity)
        assertEquals(recipe, runBlocking { recipes.get(recipe.id) })
        assertTrue("the card stays open", exists(CardTags.SCREEN))

        // The book entry's Export, for comparison: the same sheet on the same file.
        compose.backThroughTheCard()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(recipe.id.value)) }
        compose.chooseEntryAction(recipe.id.value, LibraryTags.export(recipe.id.value))
        assertEquals(fromTheCard, shared())
    }

    // A found recipe's card offers Export too, as its entry in the marked view does, before its Remove.
    @Test
    fun aFoundRecipesCardOffersExportBeforeRemove() {
        val id = found()
        showCard(id, from = LibraryView.MARKED)
        assertEquals(
            listOf(CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.EXPORT, CardTags.REMOVE),
            compose.cardMenuItems().filter { it != CardTags.COLLECTION && it != CardTags.SOURCE_TEXT },
        )
        exportFromTheCardTitled("Export “Bean soup”") { document ->
            val read = RecipeFile.decode(document)
            assertEquals(id, read.id)
            assertEquals("anna", read.author)
        }
    }

    private fun exportFromTheCardTitled(expected: String, check: (String) -> Unit) {
        exportFromTheCard()
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivity() != null }
        val sheet = activity.nextStartedActivity
        assertEquals(expected, sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        @Suppress("DEPRECATION")
        val uri = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        check(
            ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
                generateSequence { zip.nextEntry }.first { it.name == RecipeFile.DOCUMENT }
                zip.readBytes().toString(Charsets.UTF_8)
            },
        )
    }

    // FLOW-062 (8): in standard mode the card's ⋮ holds no export, as the book's entries hold none.
    @Test
    fun inStandardModeTheCardOffersNoExport() {
        val recipe = soup()
        showCard(recipe.id, Mode.Standard)
        compose.openTheCardMenu()
        assertTrue(exists(CardTags.SHARE_PDF))
        assertFalse(exists(CardTags.EXPORT))
        assertEquals(0, compose.onAllNodes(hasText("Export")).fetchSemanticsNodes().size)
    }

    // An export that fails says so as the book's does, over the card, and opens no sheet.
    @Test
    fun aFailedExportSaysSo() = failed("Couldn't export the recipe")

    @Test
    @Config(qualifiers = "ru")
    fun aFailedExportSaysSoInRussian() = failed("Не удалось экспортировать рецепт")

    private fun failed(message: String) {
        val recipe = soup()
        showCard(recipe.id)
        compose.openTheCardMenu()
        assertEquals(
            listOf(if (message.startsWith("Couldn't")) "Export" else "Экспорт"),
            compose.textsOf(CardTags.EXPORT),
        )
        val cache = context.cacheDir
        File(cache, "exports").deleteRecursively()
        assertTrue(cache.setWritable(false))
        try {
            compose.onNodeWithTag(CardTags.EXPORT).performClick()
            compose.awaitFileMessage(message)
        } finally {
            cache.setWritable(true)
        }
        assertNull(shadowOf(compose.activity).nextStartedActivity)
        assertEquals(recipe, runBlocking { recipes.get(recipe.id) })
    }
}
