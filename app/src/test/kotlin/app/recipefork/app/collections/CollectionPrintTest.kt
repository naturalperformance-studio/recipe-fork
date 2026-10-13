package app.recipefork.app.collections

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintDocumentAdapter
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.card.HeldScale
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.tagsTopToBottom
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.print.LocalPrinting
import app.recipefork.app.print.PdfWriter
import app.recipefork.app.print.PlatformPrinting
import app.recipefork.app.print.PrintBlock
import app.recipefork.app.print.PrintDocument
import app.recipefork.app.print.Printing
import app.recipefork.app.print.assertPrintProgress
import app.recipefork.app.print.printProgressShown
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.shopping.ShoppingEnvironment
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.time.Instant

/**
 * v0.37, T-262 — a collection printed and saved as PDF (scenario 7): the collection's ⋮, own or imported, while it has
 * recipes, holds Print and Share as PDF after "Add all to shopping list" and before Rename, in both modes. Each hands
 * the system's printing, or the share sheet, a document titled with the collection's name: its first page lists the
 * recipes in the collection's order, then each recipe follows at the count its card would open with.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionPrintTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)

    /** Recipes whose reads fail — as a corrupt row or a refusing storage would. */
    private val unreadable = mutableSetOf<RecipeId>()

    /** Set, a recipe's read waits for it to complete: the document is still being made. */
    private var gate: CompletableDeferred<Unit>? = null

    private val recipes = object : RecipeRepository(active, context.filesDir) {
        override fun observe(id: RecipeId): Flow<Recipe?> {
            val read = super.observe(id)
            val waiting = gate
            return when {
                id in unreadable -> flow { throw IOException("the row does not read back") }
                waiting != null -> flow {
                    waiting.await()
                    emitAll(read)
                }
                else -> read
            }
        }
    }
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private var ticks = 0L
    private val collections = CollectionRepository(active) { now.plusSeconds(ticks++) }
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val timers = TestTimers(context)
    private val cardTimers = CardTimers(timers.store, timers.scope, timers.access, timers.ringing) { BookName.Pool }

    /** What was printed and what shared, by the capturing printing. */
    private val printed = mutableListOf<PrintDocument>()
    private val shared = mutableListOf<PrintDocument>()
    private val capturing = object : Printing {
        override fun print(context: Context, document: PrintDocument) {
            printed += document
        }

        override suspend fun share(context: Context, document: PrintDocument): Boolean = true.also { shared += document }
    }

    /** The platform's printing, its print service and PDF writer stood in for. */
    private val bytes = "%PDF-stand-in".toByteArray()
    private val jobs = mutableListOf<Pair<String, PrintDocumentAdapter>>()
    private var failing = false
    private val platform = PlatformPrinting(
        PdfWriter { _, out, _ ->
            if (failing) throw IOException("disk full")
            out.write(bytes)
        },
    ) { _, name, adapter -> jobs += name to adapter }

    @Before
    fun setUp() {
        FileProviderCache.clear()
        listOf(book.idling, device.idling, timers.idling).forEach(compose::registerIdlingResource)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        gate?.complete(Unit)
        listOf(book.idling, device.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        sessions.clear()
        book.close()
        device.close()
        timers.close()
    }

    /** A recipe of the reader's own for [servings]: 200 g of flour. */
    private fun own(title: String, servings: Int?, minutesAgo: Long = 0): RecipeId = runBlocking {
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, servings = servings, ingredients = flour)
            .also { recipes.save(it) }.id
    }

    private fun found(title: String, servings: Int?): RecipeId = runBlocking {
        Recipe.create(now, owner = null).copy(title = title, servings = servings, statedAuthor = "anna", ingredients = flour)
            .inState(RecipeImport.FOUND_STATE)
            .also { assertTrue(recipes.addFound(it, now)) }.id
    }

    private val flour = listOf(
        Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
    )

    private fun collection(name: String, members: List<RecipeId> = emptyList()): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it, listOf(id)) } }
    }

    private fun imported(name: String, members: List<RecipeId>): CollectionId = runBlocking {
        CollectionId.mint().also { assertEquals(ImportedCollection.CREATED, collections.putImported(it, name, "anna", members)) }
    }

    private fun show(view: LibraryView, mode: Mode = Mode.Serverless, printing: Printing = capturing, usual: Rational? = null) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val entries = IngredientEntries { device.database.dictionaryDao().ingredients() }
        val card = CardEnvironment(HeldUsualServings(usual), marks = DoneMarks.inMemory(), ingredients = entries)
        val shopping = ShoppingEnvironment(card.shopping, ingredients = entries)
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers, LocalPrinting provides printing) {
                AppTheme { AppNavHost(session, recipes, mode, editor = editor, card = card, shopping = shopping, collections = collections) }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitGone(tag: String) = compose.waitUntil(PATIENCE_MS) { !exists(tag) }

    /** Opens the collection [id] from the view's row, and waits until its recipes, or its empty state, show. */
    private fun open(id: CollectionId, view: LibraryView = LibraryView.OWN) {
        val list = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(list).performScrollToNode(hasTestTag(CollectionScreenTags.card(id.value)))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(CollectionScreenTags.card(id.value)).performSemanticsAction(SemanticsActions.OnClick)
        await(CollectionScreenTags.SCREEN)
        compose.waitUntil(PATIENCE_MS) { (exists(CollectionScreenTags.LIST) || exists(CollectionScreenTags.EMPTY)) && !exists(LibraryTags.PAGER) }
    }

    private val menuItems = setOf(
        CollectionShareTags.SHARE,
        CollectionScreenTags.ADD_ALL_TO_SHOPPING,
        CollectionScreenTags.PRINT,
        CollectionScreenTags.SHARE_PDF,
        CollectionScreenTags.RENAME,
        CollectionScreenTags.DELETE,
    )

    private fun openMenu() {
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        await(CollectionScreenTags.DELETE)
    }

    /** The ⋮'s items, by tag, top to bottom; the menu closed again. */
    private fun items(): List<String> {
        openMenu()
        val shown = compose.tagsTopToBottom(menuItems)
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        awaitGone(CollectionScreenTags.DELETE)
        return shown
    }

    private fun take(tag: String) {
        openMenu()
        compose.onNodeWithTag(tag).performClick()
        awaitGone(CollectionScreenTags.DELETE)
    }

    private fun backToTheBook() {
        compose.onNodeWithTag(CollectionScreenTags.BACK).performClick()
        await(LibraryTags.PAGER)
    }

    private fun contents(document: PrintDocument) =
        document.blocks.takeWhile { it != PrintBlock.PageBreak }.drop(1).map { (it as PrintBlock.Numbered).number to it.text }

    private fun ingredientHeadings(document: PrintDocument) =
        document.blocks.filterIsInstance<PrintBlock.Heading>().map { it.text }.filter { it.startsWith("Ingredients") || it.startsWith("Ингредиенты") }

    @Test
    fun theMenuOffersPrintAndShareAsPdfAfterAddAllThenExportBeforeRenameInBothModes() {
        val soup = own("Soup", servings = 4)
        val menu = collection("Menu", listOf(soup))
        show(LibraryView.OWN, Mode.Serverless)
        open(menu)
        assertEquals(
            listOf(
                CollectionScreenTags.ADD_ALL_TO_SHOPPING, CollectionScreenTags.PRINT, CollectionScreenTags.SHARE_PDF,
                CollectionShareTags.SHARE, CollectionScreenTags.RENAME, CollectionScreenTags.DELETE,
            ),
            items(),
        )
        openMenu()
        compose.onNodeWithTag(CollectionScreenTags.PRINT).assertTextEquals("Print")
        compose.onNodeWithTag(CollectionScreenTags.SHARE_PDF).assertTextEquals("Share as PDF")
        compose.onNodeWithTag(CollectionShareTags.SHARE).assertTextEquals("Export")
        AccessibilityChecks.assertTouchTargets(compose)
    }

    @Test
    fun theStandardBuildOffersThemTooAndAnImportedCollectionAsAnOwnOne() {
        val soup = own("Soup", servings = 4)
        val menu = collection("Menu", listOf(soup))
        val borscht = found("Borscht", servings = 6)
        val annas = imported("Anna's dinners", listOf(borscht))
        show(LibraryView.OWN, Mode.Standard)
        open(menu)
        assertEquals(
            listOf(
                CollectionScreenTags.ADD_ALL_TO_SHOPPING, CollectionScreenTags.PRINT, CollectionScreenTags.SHARE_PDF,
                CollectionScreenTags.RENAME, CollectionScreenTags.DELETE,
            ),
            items(),
        )
        backToTheBook()
        compose.runOnIdle { session.choose(LibraryView.MARKED) }
        open(annas, LibraryView.MARKED)
        assertEquals(
            listOf(CollectionScreenTags.ADD_ALL_TO_SHOPPING, CollectionScreenTags.PRINT, CollectionScreenTags.SHARE_PDF, CollectionScreenTags.DELETE),
            items(),
        )
        take(CollectionScreenTags.PRINT)
        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        assertEquals("Anna's dinners", printed.single().title)
        assertEquals(listOf(1 to "Borscht"), contents(printed.single()))
    }

    @Test
    fun anEmptyCollectionOffersNeither() {
        own("Soup", servings = 2)
        val salads = collection("Salads")
        show(LibraryView.OWN)
        open(salads)
        await(CollectionScreenTags.EMPTY)
        assertEquals(listOf(CollectionShareTags.SHARE, CollectionScreenTags.RENAME, CollectionScreenTags.DELETE), items())
    }

    // The document is titled with the collection's name and lists its recipes in its order — not the book's — each
    // recipe after a break, at the count its card would open with: the authored one, the reader's usual one, or the one
    // the reader left its card at this session.
    @Test
    fun printHandsOverTheCollectionInItsOrderEachAtItsCardsCount() {
        val soup = own("Soup", servings = 4, minutesAgo = 3)
        val bread = own("Bread", servings = 2, minutesAgo = 2)
        val tea = own("Tea", servings = null, minutesAgo = 1)
        val menu = collection("New Year's menu", listOf(bread, soup, tea))
        session.cards.holdScale(soup, HeldScale(Rational.of(5)))
        show(LibraryView.OWN)
        open(menu)

        take(CollectionScreenTags.PRINT)

        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        val document = printed.single()
        assertEquals("New Year's menu", document.title)
        assertEquals(PrintBlock.Title("New Year's menu"), document.blocks.first())
        assertEquals(listOf(1 to "Bread", 2 to "Soup", 3 to "Tea"), contents(document))
        assertEquals(3, document.blocks.count { it == PrintBlock.PageBreak })
        // After each break, the recipe's own title.
        val afterBreaks = document.blocks.zipWithNext().filter { it.first == PrintBlock.PageBreak }.map { it.second }
        assertEquals(listOf(PrintBlock.Title("Bread"), PrintBlock.Title("Soup"), PrintBlock.Title("Tea")), afterBreaks)
        assertEquals(listOf("Ingredients · 2 servings", "Ingredients · 5 servings", "Ingredients"), ingredientHeadings(document))
        assertTrue(shared.isEmpty())

        // Share as PDF hands over the same document.
        take(CollectionScreenTags.SHARE_PDF)
        compose.waitUntil(PATIENCE_MS) { shared.size == 1 }
        assertEquals(document, shared.single())
        assertEquals(1, printed.size)
    }

    @Test
    fun theReadersUsualCountIsTheCountPrinted() {
        val soup = own("Soup", servings = 4)
        val menu = collection("Menu", listOf(soup))
        show(LibraryView.OWN, usual = Rational.of(3))
        open(menu)
        take(CollectionScreenTags.PRINT)
        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        assertEquals(listOf("Ingredients · 3 servings"), ingredientHeadings(printed.single()))
    }

    // On the platform's printing: the job is named by the collection, and the PDF too, as a recipe's is named by it.
    @Test
    fun printStartsAJobAndShareOpensTheSheetOnAPdfNamedByTheCollection() {
        val soup = own("Soup", servings = 4)
        val menu = collection("Soups / stews", listOf(soup))
        show(LibraryView.OWN, printing = platform)
        open(menu)

        take(CollectionScreenTags.PRINT)
        compose.waitUntil(PATIENCE_MS) { jobs.size == 1 }
        assertEquals("Soups / stews", jobs.single().first)

        take(CollectionScreenTags.SHARE_PDF)
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
        assertEquals("Soups _ stews.pdf", name)
        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
    }

    // A PDF that cannot be written is said to have failed, and no sheet opens; recipes none of which read back cannot be
    // printed, and that is said too.
    @Test
    fun aFailureIsSaid() {
        failing = true
        val soup = own("Soup", servings = 4)
        val menu = collection("Menu", listOf(soup))
        show(LibraryView.OWN, printing = platform)
        open(menu)

        take(CollectionScreenTags.SHARE_PDF)
        awaitNotice("Couldn't write the PDF")
        assertEquals(null, shadowOf(compose.activity).peekNextStartedActivity())

        unreadable += soup
        take(CollectionScreenTags.PRINT)
        awaitNotice("Couldn't start printing")
        assertTrue(jobs.isEmpty())
    }

    // A recipe that does not read back is left out; the rest are printed.
    @Test
    fun aRecipeThatCannotBeReadIsLeftOut() {
        val soup = own("Soup", servings = 4, minutesAgo = 1)
        val bread = own("Bread", servings = 2)
        val menu = collection("Menu", listOf(soup, bread))
        show(LibraryView.OWN)
        open(menu)
        unreadable += soup

        take(CollectionScreenTags.PRINT)

        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        assertEquals(listOf(1 to "Bread"), contents(printed.single()))
    }

    // While the document is being made, the book's progress shows under the bar, its words drawn (v0.38, T-269), and a
    // second tap — on either item — starts nothing; once it is handed over, the progress goes and a tap starts the next.
    @Test
    fun aSecondTapWhileTheDocumentIsMadeStartsNothing() {
        val soup = own("Soup", servings = 4)
        val menu = collection("Menu", listOf(soup))
        show(LibraryView.OWN)
        open(menu)
        assertFalse(compose.printProgressShown())
        val waiting = CompletableDeferred<Unit>()
        gate = waiting

        take(CollectionScreenTags.PRINT)
        compose.waitUntil(PATIENCE_MS) { compose.printProgressShown() }
        compose.assertPrintProgress("Preparing the document", CollectionScreenTags.BAR)
        take(CollectionScreenTags.PRINT)
        take(CollectionScreenTags.SHARE_PDF)
        compose.waitForIdle()
        assertTrue(printed.isEmpty() && shared.isEmpty())

        gate = null
        waiting.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { printed.size == 1 && !compose.printProgressShown() }
        compose.waitForIdle()
        assertEquals(1, printed.size)
        assertTrue(shared.isEmpty())

        take(CollectionScreenTags.SHARE_PDF)
        compose.waitUntil(PATIENCE_MS) { shared.size == 1 }
        assertEquals(1, printed.size)
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        val soup = own("Суп", servings = 4, minutesAgo = 1)
        val bread = own("Хлеб", servings = 2)
        val menu = collection("Зимнее меню", listOf(soup, bread))
        show(LibraryView.OWN)
        open(menu)

        openMenu()
        compose.onNodeWithTag(CollectionScreenTags.PRINT).assertTextEquals("Печать")
        compose.onNodeWithTag(CollectionScreenTags.SHARE_PDF).assertTextEquals("Поделиться PDF")
        compose.onNodeWithTag(CollectionShareTags.SHARE).assertTextEquals("Экспорт")
        val waiting = CompletableDeferred<Unit>()
        gate = waiting
        compose.onNodeWithTag(CollectionScreenTags.SHARE_PDF).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.printProgressShown() }
        compose.assertPrintProgress("Готовим документ", CollectionScreenTags.BAR)
        gate = null
        waiting.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { shared.size == 1 && !compose.printProgressShown() }
        val document = shared.single()
        assertEquals("Зимнее меню", document.title)
        assertEquals(listOf(1 to "Суп", 2 to "Хлеб"), contents(document))
        assertEquals(listOf("Ингредиенты · 4 порции", "Ингредиенты · 2 порции"), ingredientHeadings(document))

        unreadable += listOf(soup, bread)
        take(CollectionScreenTags.PRINT)
        awaitNotice("Не удалось начать печать")
    }

    private fun awaitNotice(text: String) = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }
}
