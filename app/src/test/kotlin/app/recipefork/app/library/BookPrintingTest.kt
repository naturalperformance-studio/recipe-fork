package app.recipefork.app.library

import android.content.Context
import android.print.PrintDocumentAdapter
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
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
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.card.HeldScale
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.openTheCardMenu
import app.recipefork.app.card.tagsTopToBottom
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.print.LocalPrinting
import app.recipefork.app.print.PdfWriter
import app.recipefork.app.print.PlatformPrinting
import app.recipefork.app.print.PrintBlock
import app.recipefork.app.print.PrintDocument
import app.recipefork.app.print.Printing
import app.recipefork.app.print.assertPrintProgress
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.shopping.ShoppingEnvironment
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.time.Instant

/**
 * Print and Share as PDF from the book (v0.37, T-264, scenarios 4 and 6), in both builds. The selection's ⋮ offers them
 * while it holds a recipe the book holds the content of: one document titled "Recipes" — a first page listing the
 * recipes, then each from a new page — of those recipes in the order the view lists them, each at the count its card
 * would open with, handed to the system's printing or shared as a PDF. A progress shows while it is made, a second tap
 * starts nothing, a failure is said, and the action over ends the selection. An entry's ⋮ in either view offers both for
 * its one recipe: the document its card prints.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookPrintingTest {
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
    private val now = Instant.parse("2026-10-13T12:00:00Z")
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

    /** The platform's printing, its print service and PDF writer stood in for; the writer fails. */
    private val jobs = mutableListOf<Pair<String, PrintDocumentAdapter>>()
    private val platform = PlatformPrinting(PdfWriter { _, _, _ -> throw IOException("disk full") }) { _, name, adapter -> jobs += name to adapter }

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

    private val flour = listOf(
        Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
    )

    private fun recipe(title: String, servings: Int?, minutesAgo: Long) =
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, servings = servings, ingredients = flour)

    private fun own(title: String, servings: Int? = 2, minutesAgo: Long = 0): Recipe =
        recipe(title, servings, minutesAgo).also { runBlocking { recipes.save(it) } }

    private fun found(title: String, servings: Int? = 2, minutesAgo: Long = 0): Recipe =
        recipe(title, servings, minutesAgo).inState(RecipeImport.FOUND_STATE).also {
            assertTrue(runBlocking { recipes.addFound(it, now.minusSeconds(minutesAgo * 60)) })
        }

    /** A recipe marked in the book whose content the book does not hold. */
    private fun markedOnly(id: String, title: String) =
        runBlocking { book.database.favourites().put(FavouriteEntity(id, title, now)) }

    private fun show(view: LibraryView, mode: Mode = Mode.Standard, printing: Printing = capturing, usual: Rational? = null) {
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

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitGone(tag: String) = compose.waitUntil(PATIENCE_MS) { !exists(tag) }

    private fun awaitEntry(id: String, view: LibraryView) = compose.waitUntil(PATIENCE_MS) {
        try {
            compose.onNodeWithTag(list(view)).performScrollToNode(hasTestTag(LibraryTags.entry(id)))
            true
        } catch (e: AssertionError) {
            false
        }
    }

    private fun longPress(id: String, view: LibraryView) {
        awaitEntry(id, view)
        // At its left: at its middle, an entry low in the list may stand under the view's floating action.
        compose.onNodeWithTag(LibraryTags.entry(id)).performTouchInput { longClick(centerLeft + Offset(24f, 0f)) }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_BAR) }
    }

    private fun tap(id: String, view: LibraryView) {
        awaitEntry(id, view)
        compose.onNodeWithTag(LibraryTags.entry(id)).performTouchInput { click(centerLeft + Offset(24f, 0f)) }
        compose.waitForIdle()
    }

    /** Chooses [recipes] in [view] in this order: the first by a long press, the others by taps. */
    private fun choose(vararg recipes: Recipe, view: LibraryView = LibraryView.OWN) {
        compose.waitUntil(PATIENCE_MS) { session.selection.value == null }
        if (compose.noticeShown()) compose.swipeAwayNotice()
        longPress(recipes.first().id.value, view)
        recipes.drop(1).forEach { tap(it.id.value, view) }
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == recipes.size }
    }

    private fun awaitNoSelection() = compose.waitUntil(PATIENCE_MS) {
        !exists(LibraryTags.SELECTION_BAR) && exists(LibraryTags.TITLE) && session.selection.value == null
    }

    /** Takes the selection's action tagged [tag], at the bottom (v0.42). */
    private fun takeFromSelection(tag: String) {
        await(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    /** The recipes of [shown], by their identities, in the order [view] lists them, top to bottom. */
    private fun listOrder(view: LibraryView, vararg shown: Recipe): List<Recipe> {
        shown.forEach { awaitEntry(it.id.value, view) }
        compose.onNodeWithTag(list(view)).performScrollToNode(hasTestTag(LibraryTags.entry(shown.first().id.value)))
        val byTag = shown.associateBy { LibraryTags.entry(it.id.value) }
        return compose.tagsTopToBottom(byTag.keys).map { byTag.getValue(it) }
    }

    private fun contents(document: PrintDocument) =
        document.blocks.takeWhile { it != PrintBlock.PageBreak }.drop(1).map { (it as PrintBlock.Numbered).number to it.text }

    private fun ingredientHeadings(document: PrintDocument) =
        document.blocks.filterIsInstance<PrintBlock.Heading>().map { it.text }.filter { it.startsWith("Ingredients") || it.startsWith("Ингредиенты") }

    private fun awaitNotice(text: String) = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)), useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }

    // --- The selection -------------------------------------------------------------------------------------------------

    // Three chosen in another order than the list's: Print hands over one document titled "Recipes", its first page
    // listing them in the list's order, then each from a new page at the count its card would open with — the one the
    // reader left its card at, the authored one, none. The selection ends. Share as PDF hands over the same.
    @Test
    fun theSelectionPrintsAndSharesTheChosenInTheListsOrderUnderRecipes() {
        val soup = own("Soup", servings = 4, minutesAgo = 3)
        val bread = own("Bread", servings = 2, minutesAgo = 2)
        val tea = own("Tea", servings = null, minutesAgo = 1)
        session.cards.holdScale(soup.id, HeldScale(Rational.of(5)))
        show(LibraryView.OWN, Mode.Serverless)
        val order = listOrder(LibraryView.OWN, soup, bread, tea)
        val chosen = listOf(bread, soup, tea).let { if (it == order) listOf(tea, soup, bread) else it }
        assertNotEquals(order, chosen)

        choose(*chosen.toTypedArray())
        // At the bottom (v0.42), Print and Share as PDF stand before Delete, named by their words.
        await(LibraryTags.SELECTION_DELETE)
        val lefts = listOf(LibraryTags.SELECTION_PRINT, LibraryTags.SELECTION_SHARE_PDF, LibraryTags.SELECTION_DELETE)
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(lefts.sorted(), lefts)
        assertEquals(listOf("Print"), compose.textsOf(LibraryTags.SELECTION_PRINT))
        assertEquals(listOf("PDF"), compose.textsOf(LibraryTags.SELECTION_SHARE_PDF))
        assertEquals("Share as PDF", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(LibraryTags.SELECTION_SHARE_PDF).fetchSemanticsNode()))
        AccessibilityChecks.assertTouchTargets(compose)
        compose.onNodeWithTag(LibraryTags.SELECTION_PRINT).performClick()

        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        awaitNoSelection()
        val document = printed.single()
        assertEquals("Recipes", document.title)
        assertEquals(PrintBlock.Title("Recipes"), document.blocks.first())
        assertEquals(order.mapIndexed { i, it -> i + 1 to it.title }, contents(document))
        assertEquals(3, document.blocks.count { it == PrintBlock.PageBreak })
        val afterBreaks = document.blocks.zipWithNext().filter { it.first == PrintBlock.PageBreak }.map { it.second }
        assertEquals(order.map { PrintBlock.Title(it.title) }, afterBreaks)
        val counts = mapOf(soup to "Ingredients · 5 servings", bread to "Ingredients · 2 servings", tea to "Ingredients")
        assertEquals(order.map { counts.getValue(it) }, ingredientHeadings(document))
        assertTrue(shared.isEmpty())

        choose(*chosen.toTypedArray())
        takeFromSelection(LibraryTags.SELECTION_SHARE_PDF)
        compose.waitUntil(PATIENCE_MS) { shared.size == 1 }
        awaitNoSelection()
        assertEquals(document, shared.single())
        assertEquals(1, printed.size)
    }

    // Among the imported, in the build with the server: a marked recipe whose content the book does not hold is chosen
    // all the same and left out; chosen alone, neither action responds.
    @Test
    fun onlyRecipesTheBookHoldsArePrintedAndNoneHeldOffersNeither() {
        val borscht = found("Borscht", servings = 6, minutesAgo = 1)
        val pelmeni = found("Pelmeni", servings = 2)
        markedOnly("server-1", "Shchi")
        show(LibraryView.MARKED, Mode.Standard)

        longPress("server-1", LibraryView.MARKED)
        await(LibraryTags.SELECTION_PRINT)
        compose.onNodeWithTag(LibraryTags.SELECTION_PRINT).assertIsNotEnabled()
        compose.onNodeWithTag(LibraryTags.SELECTION_SHARE_PDF).assertIsNotEnabled()

        tap(pelmeni.id.value, LibraryView.MARKED)
        tap(borscht.id.value, LibraryView.MARKED)
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 3 }
        val order = listOrder(LibraryView.MARKED, borscht, pelmeni)
        takeFromSelection(LibraryTags.SELECTION_SHARE_PDF)

        compose.waitUntil(PATIENCE_MS) { shared.size == 1 }
        awaitNoSelection()
        assertEquals("Recipes", shared.single().title)
        assertEquals(order.mapIndexed { i, it -> i + 1 to it.title }, contents(shared.single()))
    }

    // While the document is made, a progress shows over the list and the selection stays; a second tap — on either item —
    // starts nothing. Once it is handed over the progress goes and the selection ends. An entry's item the same.
    @Test
    fun whileTheDocumentIsMadeAProgressShowsAndASecondTapStartsNothing() {
        val soup = own("Soup", servings = 4, minutesAgo = 1)
        val bread = own("Bread", servings = 2)
        show(LibraryView.OWN)
        choose(soup, bread)
        assertFalse(exists(LibraryTags.PRINT_PROGRESS))
        val waiting = CompletableDeferred<Unit>()
        gate = waiting

        takeFromSelection(LibraryTags.SELECTION_PRINT)
        await(LibraryTags.PRINT_PROGRESS)
        // Its words shown under the tabs, not only to the screen reader (v0.38, T-269).
        compose.assertPrintProgress("Preparing the document", LibraryTags.SWITCH)
        assertEquals(2, session.selection.value?.count)
        takeFromSelection(LibraryTags.SELECTION_PRINT)
        takeFromSelection(LibraryTags.SELECTION_SHARE_PDF)
        compose.waitForIdle()
        assertTrue(printed.isEmpty() && shared.isEmpty())

        gate = null
        waiting.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        awaitGone(LibraryTags.PRINT_PROGRESS)
        awaitNoSelection()
        compose.waitForIdle()
        assertEquals(1, printed.size)
        assertTrue(shared.isEmpty())

        // From an entry's ⋮.
        val again = CompletableDeferred<Unit>()
        gate = again
        compose.chooseEntryAction(soup.id.value, LibraryTags.sharePdf(soup.id.value))
        await(LibraryTags.PRINT_PROGRESS)
        compose.chooseEntryAction(soup.id.value, LibraryTags.print(soup.id.value))
        compose.chooseEntryAction(bread.id.value, LibraryTags.sharePdf(bread.id.value))
        compose.waitForIdle()
        assertTrue(shared.isEmpty())
        gate = null
        again.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { shared.size == 1 }
        awaitGone(LibraryTags.PRINT_PROGRESS)
        compose.waitForIdle()
        assertEquals("Soup", shared.single().title)
        assertEquals(1, printed.size)
    }

    // An action cancelled before its end — the screen recreated while the document is made — prints nothing and leaves
    // the reader's selection as it was.
    @Test
    fun aCancelledActionLeavesTheSelection() {
        val soup = own("Soup", servings = 4, minutesAgo = 1)
        val bread = own("Bread", servings = 2)
        show(LibraryView.OWN)
        choose(soup, bread)
        val waiting = CompletableDeferred<Unit>()
        gate = waiting
        takeFromSelection(LibraryTags.SELECTION_PRINT)
        await(LibraryTags.PRINT_PROGRESS)

        compose.activityRule.scenario.recreate()
        gate = null
        waiting.complete(Unit)
        compose.waitForIdle()

        assertTrue(printed.isEmpty() && shared.isEmpty())
        assertEquals(setOf(soup.id.value, bread.id.value), session.selection.value?.ids)
    }

    // A selection made while an earlier one's document was being made is not the one the action was taken from: the
    // action's end leaves it.
    @Test
    fun theActionsEndLeavesALaterSelection() {
        val soup = own("Soup", servings = 4, minutesAgo = 2)
        val bread = own("Bread", servings = 2, minutesAgo = 1)
        val tea = own("Tea", servings = 1)
        show(LibraryView.OWN)
        choose(soup, bread)
        val waiting = CompletableDeferred<Unit>()
        gate = waiting
        takeFromSelection(LibraryTags.SELECTION_PRINT)
        await(LibraryTags.PRINT_PROGRESS)

        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        awaitNoSelection()
        choose(tea)
        gate = null
        waiting.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        awaitGone(LibraryTags.PRINT_PROGRESS)
        compose.waitForIdle()

        assertEquals(setOf(tea.id.value), session.selection.value?.ids)
        assertTrue(exists(LibraryTags.SELECTION_BAR))
        assertEquals(setOf("Soup", "Bread"), contents(printed.single()).map { it.second }.toSet())
    }

    // On the platform's printing the job is named "Recipes". A PDF that cannot be written is said to have failed, and
    // recipes none of which read back cannot be printed, which is said too; either way the selection ends.
    @Test
    fun printStartsAJobNamedRecipesAndAFailureIsSaid() {
        val soup = own("Soup", servings = 4, minutesAgo = 1)
        val bread = own("Bread", servings = 2)
        show(LibraryView.OWN, Mode.Serverless, printing = platform)

        choose(soup, bread)
        takeFromSelection(LibraryTags.SELECTION_PRINT)
        compose.waitUntil(PATIENCE_MS) { jobs.size == 1 }
        assertEquals("Recipes", jobs.single().first)
        awaitNoSelection()

        choose(soup, bread)
        takeFromSelection(LibraryTags.SELECTION_SHARE_PDF)
        awaitNotice("Couldn't write the PDF")
        awaitNoSelection()

        unreadable += listOf(soup.id, bread.id)
        compose.swipeAwayNotice()
        choose(soup, bread)
        takeFromSelection(LibraryTags.SELECTION_PRINT)
        awaitNotice("Couldn't start printing")
        awaitNoSelection()
        assertEquals(1, jobs.size)

        // An entry's recipe that does not read back: the same.
        compose.swipeAwayNotice()
        compose.chooseEntryAction(soup.id.value, LibraryTags.print(soup.id.value))
        awaitNotice("Couldn't start printing")
        assertEquals(1, jobs.size)
    }

    // --- An entry's ⋮ --------------------------------------------------------------------------------------------------

    // An entry's Print and Share as PDF hand over its one recipe as its card prints it — at the count the card opens
    // with, no contents page — in either view; the card's own Print hands over the same document.
    @Test
    fun anEntrysPrintAndShareAreWhatItsCardPrintsInEitherView() {
        val soup = own("Soup", servings = 4)
        val borscht = found("Borscht", servings = 6)
        session.cards.holdScale(soup.id, HeldScale(Rational.of(7)))
        show(LibraryView.MARKED, Mode.Serverless, usual = Rational.of(3))

        awaitEntry(borscht.id.value, LibraryView.MARKED)
        compose.chooseEntryAction(borscht.id.value, LibraryTags.sharePdf(borscht.id.value))
        compose.waitUntil(PATIENCE_MS) { shared.size == 1 }
        assertEquals("Borscht", shared.single().title)
        assertEquals(PrintBlock.Title("Borscht"), shared.single().blocks.first())
        assertFalse(PrintBlock.PageBreak in shared.single().blocks)
        assertEquals(listOf("Ingredients · 3 servings"), ingredientHeadings(shared.single()))

        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        awaitEntry(soup.id.value, LibraryView.OWN)
        compose.chooseEntryAction(soup.id.value, LibraryTags.print(soup.id.value))
        compose.waitUntil(PATIENCE_MS) { printed.size == 1 }
        val fromTheEntry = printed.single()
        assertEquals("Soup", fromTheEntry.title)
        assertEquals(listOf("Ingredients · 7 servings"), ingredientHeadings(fromTheEntry))

        // The card's Print, the card opened from the entry.
        tap(soup.id.value, LibraryView.OWN)
        compose.awaitCard()
        compose.waitForIdle()
        compose.openTheCardMenu()
        compose.onNodeWithTag(CardTags.PRINT).performClick()
        compose.waitUntil(PATIENCE_MS) { printed.size == 2 }
        assertEquals(fromTheEntry, printed.last())
    }

    // --- Russian -------------------------------------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        val soup = own("Суп", servings = 4, minutesAgo = 1)
        val bread = own("Хлеб", servings = 21)
        show(LibraryView.OWN)
        val order = listOrder(LibraryView.OWN, soup, bread)

        choose(soup, bread)
        await(LibraryTags.SELECTION_PRINT)
        assertEquals(listOf("Печать"), compose.textsOf(LibraryTags.SELECTION_PRINT))
        assertEquals(listOf("PDF"), compose.textsOf(LibraryTags.SELECTION_SHARE_PDF))
        assertEquals("Распечатать", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(LibraryTags.SELECTION_PRINT).fetchSemanticsNode()))
        assertEquals("Поделиться PDF", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(LibraryTags.SELECTION_SHARE_PDF).fetchSemanticsNode()))
        val waiting = CompletableDeferred<Unit>()
        gate = waiting
        compose.onNodeWithTag(LibraryTags.SELECTION_SHARE_PDF).performClick()
        await(LibraryTags.PRINT_PROGRESS)
        compose.assertPrintProgress("Готовим документ", LibraryTags.SWITCH)
        gate = null
        waiting.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { shared.size == 1 }
        awaitNoSelection()
        val document = shared.single()
        assertEquals("Рецепты", document.title)
        assertEquals(PrintBlock.Title("Рецепты"), document.blocks.first())
        assertEquals(order.mapIndexed { i, it -> i + 1 to it.title }, contents(document))
        val counts = mapOf(soup to "Ингредиенты · 4 порции", bread to "Ингредиенты · 21 порция")
        assertEquals(order.map { counts.getValue(it) }, ingredientHeadings(document))

        unreadable += listOf(soup.id, bread.id)
        choose(soup, bread)
        takeFromSelection(LibraryTags.SELECTION_PRINT)
        awaitNotice("Не удалось начать печать")
        awaitNoSelection()
    }
}
