package app.recipefork.app.card

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintDocumentAdapter
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.editor.textsOf
import app.recipefork.app.print.LocalPrinting
import app.recipefork.app.print.PagesPrintAdapter
import app.recipefork.app.print.PdfWriter
import app.recipefork.app.print.PlatformPrinting
import app.recipefork.app.print.PrintBlock
import app.recipefork.app.print.PrintDocument
import app.recipefork.app.print.Printing
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.time.Instant

/**
 * v0.33, T-237 — Print and Share as PDF from a recipe's card (FLOW-075, scenarios 1, 2, 4):
 *
 * - The ⋮, on View and on Cooking, holds "Print" and "Share as PDF" after the shopping-list action.
 * - "Print" starts a print job named by the recipe's title, its PDF named by it too; "Share as PDF" opens the share
 *   sheet on an `application/pdf` file named by the title as written, readable through the provider; a failure to write
 *   says so and changes nothing.
 * - Either prints the recipe at the count and fixings the card shows at that moment (FLOW-075 (1)).
 *
 * The print service itself is the platform's, so the job is recorded where it would start; the JVM cannot run the
 * platform's PdfDocument, so the pages are written by a stand-in (T-238 reads a real one on the device).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardPrintTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val bytes = "%PDF-stand-in".toByteArray()
    private var failing = false

    /** The print jobs started, by name and adapter, and every document printed or shared. */
    private val jobs = mutableListOf<Pair<String, PrintDocumentAdapter>>()
    private val documents = mutableListOf<PrintDocument>()

    private val writer = PdfWriter { _, out, _ ->
        if (failing) throw IOException("disk full")
        out.write(bytes)
    }
    private var printUnavailable = false
    private val platform = PlatformPrinting(writer) { _, name, adapter ->
        if (printUnavailable) throw IllegalStateException("no print service")
        jobs += name to adapter
    }
    private val printing = object : Printing {
        override fun print(context: Context, document: PrintDocument) {
            documents += document
            platform.print(context, document)
        }

        override suspend fun share(context: Context, document: PrintDocument): Boolean {
            documents += document
            return platform.share(context, document)
        }
    }

    /** Лимонный кекс for 4: 300 g of flour and 100 g of butter. */
    private val cake = Recipe.create(now, null).copy(
        title = "Лимонный кекс",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(300)),
            Ingredient(1, null, freeTextName = "Butter", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100)),
        ),
        steps = listOf(Step(0, "Bake it.")),
    )

    @Before
    fun setUp() {
        // The provider keeps the directories it found first; each test has its own.
        FileProviderCache.clear()
        compose.registerIdlingResource(book.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun open(recipe: Recipe, tab: CardMode = CardMode.VIEW) {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(tab), ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            CompositionLocalProvider(LocalPrinting provides printing) {
                MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(0)) }
        compose.waitForIdle()
    }

    private fun take(tag: String) {
        compose.openTheCardMenu()
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun texts(document: PrintDocument) = document.blocks.map { block ->
        when (block) {
            is PrintBlock.Heading -> "heading: ${block.text}"
            is PrintBlock.Bullet -> "• ${block.text}"
            else -> block.toString()
        }
    }

    /** The share sheet the card opened, once it has. */
    private fun sheet(): Intent {
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivity() != null }
        return activity.nextStartedActivity
    }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }

    @Test
    fun bothTabsMenusHoldPrintAndShareAfterTheShoppingItem() {
        open(cake)
        // An own recipe's removal stays last, on View.
        assertEquals(listOf(CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.DELETE), compose.cardMenuItems())
        compose.openTheCardMenu()
        assertEquals(listOf("Print"), compose.textsOf(CardTags.PRINT))
        assertEquals(listOf("Share as PDF"), compose.textsOf(CardTags.SHARE_PDF))
        compose.assertCardTarget(CardTags.PRINT)
        compose.assertCardTarget(CardTags.SHARE_PDF)
        compose.closeTheCardMenu()

        compose.chooseCardTab(CardMode.COOKING)
        assertEquals(listOf(CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF), compose.cardMenuItems())
    }

    // Scenario 1: set to six servings, "Print" hands the recipe at six to the print service, under its title.
    @Test
    fun printStartsAJobUnderTheTitleAtTheCountShown() {
        open(cake)
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performClick()
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SERVINGS) == listOf("Servings: 6") }
        take(CardTags.PRINT)
        compose.waitUntil(PATIENCE_MS) { jobs.size == 1 }
        val (name, adapter) = jobs.single()
        assertEquals("Лимонный кекс", name)
        assertTrue(adapter is PagesPrintAdapter)
        val printed = texts(documents.single())
        assertTrue(printed.toString(), "heading: Ingredients · 6 servings" in printed)
        assertTrue(printed.toString(), "• 450 g Flour" in printed)
        assertTrue(printed.toString(), "• 150 g Butter" in printed)
    }

    // Scenario 2: "Share as PDF" opens the share sheet on the recipe's PDF, named by its title as written.
    @Test
    fun shareOpensTheSheetOnAPdfNamedByTheTitle() {
        open(cake, CardMode.COOKING)
        take(CardTags.SHARE_PDF)
        val chooser = sheet()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("application/pdf", send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("Лимонный кекс.pdf", displayName(uri))
        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        assertTrue(texts(documents.single()).contains("heading: Ingredients · 4 servings"))
        assertTrue(jobs.isEmpty())
    }

    // A PDF that cannot be written is said to have failed; the recipe is as it was, and no sheet opens.
    @Test
    fun aFailureToWriteIsSaid() {
        failing = true
        open(cake)
        take(CardTags.SHARE_PDF)
        val notice = hasText("Couldn't write the PDF") and hasAnyAncestor(hasTestTag(CardTags.SNACKBAR))
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(notice, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        assertEquals(null, shadowOf(compose.activity).peekNextStartedActivity())
        assertEquals(cake, runBlocking { recipes.get(cake.id) })
    }

    // A print service that cannot be started is said to have failed.
    @Test
    fun aPrintThatCannotStartIsSaid() {
        printUnavailable = true
        open(cake)
        take(CardTags.PRINT)
        val notice = hasText("Couldn't start printing") and hasAnyAncestor(hasTestTag(CardTags.SNACKBAR))
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(notice, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        assertTrue(jobs.isEmpty())
    }
}
