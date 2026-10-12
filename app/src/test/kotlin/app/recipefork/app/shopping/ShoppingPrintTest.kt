package app.recipefork.app.shopping

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
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.tagsTopToBottom
import app.recipefork.app.data.HeldUnitSystemSetting
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.editor.textsOf
import app.recipefork.app.print.LocalPrinting
import app.recipefork.app.print.PdfWriter
import app.recipefork.app.print.PlatformPrinting
import app.recipefork.app.print.PrintBlock
import app.recipefork.app.print.PrintDocument
import app.recipefork.app.print.Printing
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.shopping.ShoppingAmount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

/**
 * v0.33, T-237 — Print and Share as PDF from the shopping list (FLOW-075, scenario 5): while the list has lines its ⋮
 * holds "Print" and "Share as PDF" before its clearings; the job and the file are named "Shopping list".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingPrintTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val bytes = "%PDF-stand-in".toByteArray()
    private val jobs = mutableListOf<Pair<String, PrintDocumentAdapter>>()
    private var failing = false
    private val printing = PlatformPrinting(
        PdfWriter { _, out, _ ->
            if (failing) throw IOException("disk full")
            out.write(bytes)
        },
    ) { _, name, adapter -> jobs += name to adapter }

    /** Soup for 2: an onion and 300 g of flour. */
    private val soup = recipe("Onion soup", 2, row(ONION, r(1), PIECE), row(FLOUR, r(300), GRAM))

    @Before
    fun waitForTheBook() {
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

    private fun show(list: ShoppingList, units: UnitSystem = UnitSystem.METRIC, printing: Printing = this.printing) {
        val environment = ShoppingEnvironment(
            HeldShopping(list), unitSystem = HeldUnitSystemSetting(units), ingredients = IngredientEntries { SHOPPING_DICTIONARY },
        )
        compose.setContent {
            CompositionLocalProvider(LocalPrinting provides printing) {
                MaterialTheme { ShoppingScreen(environment, recipes, onBack = {}) }
            }
        }
        compose.waitUntil(PATIENCE_MS) { !exists(ShoppingTags.LOADING) && (exists(ShoppingTags.LIST) || exists(ShoppingTags.EMPTY)) }
        compose.waitForIdle()
    }

    private val menuItems = setOf(ShoppingTags.PRINT, ShoppingTags.SHARE_PDF, ShoppingTags.CLEAR_TICKED, ShoppingTags.CLEAR)

    /** The ⋮'s items, by tag, in the order they stand, top to bottom; the menu closed again. */
    private fun items(): List<String> {
        compose.onNodeWithTag(ShoppingTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.CLEAR) }
        val shown = compose.tagsTopToBottom(menuItems)
        compose.onNodeWithTag(ShoppingTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(ShoppingTags.CLEAR) }
        return shown
    }

    private fun take(tag: String) {
        compose.onNodeWithTag(ShoppingTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(tag) }
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    @Test
    fun theMenuHoldsPrintAndShareWhileTheListHasLines() {
        runBlocking { recipes.save(soup) }
        show(ShoppingList(listOf(listed(soup))))
        assertEquals(listOf(ShoppingTags.PRINT, ShoppingTags.SHARE_PDF, ShoppingTags.CLEAR), items())
        compose.onNodeWithTag(ShoppingTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.PRINT) }
        assertEquals(listOf("Print"), compose.textsOf(ShoppingTags.PRINT))
        assertEquals(listOf("Share as PDF"), compose.textsOf(ShoppingTags.SHARE_PDF))
    }

    // With a line ticked, Clear ticked stands between them and Clear list.
    @Test
    fun aTickedLineAddsClearTickedAfterThem() {
        runBlocking { recipes.save(soup) }
        val ticked = mapOf(TickedLine(POOL, "e:$ONION") to ShoppingAmount(PIECE, r(1)))
        show(ShoppingList(listOf(listed(soup)), ticked))
        assertEquals(listOf(ShoppingTags.PRINT, ShoppingTags.SHARE_PDF, ShoppingTags.CLEAR_TICKED, ShoppingTags.CLEAR), items())
    }

    // A PDF of the list that cannot be written is said to have failed, and no sheet opens.
    @Test
    fun aFailureToWriteIsSaid() {
        failing = true
        runBlocking { recipes.save(soup) }
        show(ShoppingList(listOf(listed(soup))))
        take(ShoppingTags.SHARE_PDF)
        val notice = hasText("Couldn't write the PDF") and hasAnyAncestor(hasTestTag(ShoppingTags.SNACKBAR))
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(notice, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        assertEquals(null, shadowOf(compose.activity).peekNextStartedActivity())
    }

    @Test
    fun anEmptyListOffersNeither() {
        show(ShoppingList())
        assertTrue(exists(ShoppingTags.EMPTY))
        assertFalse(exists(ShoppingTags.MENU))
    }

    // v0.35 T-250: what is printed is the list in the chosen system, as the screen shows it.
    @Test
    fun printingFollowsTheChosenSystem() {
        val printed = mutableListOf<PrintDocument>()
        val capturing = object : Printing {
            override fun print(context: Context, document: PrintDocument) {
                printed += document
            }

            override suspend fun share(context: Context, document: PrintDocument): Boolean = true.also { printed += document }
        }
        runBlocking { recipes.save(soup) }
        show(ShoppingList(listOf(listed(soup))), UnitSystem.US, capturing)
        take(ShoppingTags.PRINT)
        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        // 300 g of flour, 130 g a cup: ≈ 2⅓ cups.
        assertEquals(
            listOf("onion — 1 pc", "flour — ≈ 2⅓ cup"),
            printed.single().blocks.filterIsInstance<PrintBlock.Box>().map { it.text },
        )
    }

    @Test
    fun printStartsAJobNamedShoppingList() {
        runBlocking { recipes.save(soup) }
        show(ShoppingList(listOf(listed(soup))))
        take(ShoppingTags.PRINT)
        compose.waitUntil(PATIENCE_MS) { jobs.size == 1 }
        assertEquals("Shopping list", jobs.single().first)
    }

    @Test
    fun shareOpensTheSheetOnShoppingListPdf() {
        runBlocking { recipes.save(soup) }
        show(ShoppingList(listOf(listed(soup))))
        take(ShoppingTags.SHARE_PDF)
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivity() != null }
        @Suppress("DEPRECATION")
        val send = activity.nextStartedActivity.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("application/pdf", send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }
        assertEquals("Shopping list.pdf", name)
        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
    }
}
