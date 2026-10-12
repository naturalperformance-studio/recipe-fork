package app.recipefork.app.shopping

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.data.HeldThemeSetting
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.app.ui.Shown
import app.recipefork.app.ui.shownFor
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.Recipe
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * T-229, scenario 6 of v0.32: the shopping list opens from an icon in the book's top bar and back returns to the book;
 * its recipes stand at the top, each with the card's servings − and +, a count set there kept and every line it feeds
 * changed at once, ¼ the lowest; a recipe is removed with Undo, and "Clear list" in the ⋮ offers Undo; an empty list
 * says how to add a recipe; a recipe deleted from the book leaves the list. At twice the font scale on a 320 dp phone,
 * in English and Russian, nothing is cut or overlapped.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val saved = SavedStateHandle()
    private var session = LibrarySession(saved)
    private val card = CardEnvironment(HeldUsualServings(), ingredients = IngredientEntries { SHOPPING_DICTIONARY })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = emptyList<TagEntity>()
        },
    ) { Instant.parse("2026-10-12T12:00:00Z") }
    private val store = HeldShopping()
    private val shopping = ShoppingEnvironment(store, IngredientEntries { SHOPPING_DICTIONARY })

    /** Soup for 2: an onion and 300 g of flour. */
    private val soup = recipe("Onion soup", 2, row(ONION, r(1), PIECE), row(FLOUR, r(300), GRAM), minutesAgo = 10)

    /** Pie for 4: two onions and 2 cups of flour, a tablespoon of sugar. */
    private val pie = recipe("Onion pie", 4, row(ONION, r(2), PIECE), row(FLOUR, r(2), CUP), row(SUGAR, r(1), TABLESPOON))

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun save(vararg saved: Recipe) = runBlocking { saved.forEach { recipes.save(it) } }

    private fun showBook(): StateRestorationTester {
        session.choose(LibraryView.OWN)
        val tester = StateRestorationTester(compose)
        // The session read anew each time the content is: a process ended and restored brings a new one.
        tester.setContent {
            MaterialTheme {
                AppNavHost(session, recipes, card = card, editor = editor, themeSetting = HeldThemeSetting(), shopping = shopping)
            }
        }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SHOPPING) }
        return tester
    }

    /** Opens the list from the book's top bar, and waits for it, read. */
    private fun openList() {
        compose.onNodeWithTag(LibraryTags.SHOPPING).performClick()
        compose.waitUntil(PATIENCE_MS) {
            exists(ShoppingTags.SCREEN) && !exists(LibraryTags.TOP_BAR) && !exists(ShoppingTags.LOADING)
        }
    }

    private fun awaitBook() = compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.TOP_BAR) && !exists(ShoppingTags.SCREEN) }

    /** Every line of what to buy, as its first text, each scrolled to in turn. */
    private fun lines(): List<String> = generateSequence(0) { it + 1 }
        .takeWhile { index ->
            try {
                compose.onNodeWithTag(ShoppingTags.LIST).performScrollToNode(hasTestTag(ShoppingTags.line(index)))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        .map { compose.textsOf(ShoppingTags.line(it)).first() }
        .toList()

    private fun tap(tag: String) {
        compose.onNodeWithTag(ShoppingTags.LIST).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun tapText(text: String) {
        compose.onNode(hasText(text)).performClick()
        compose.waitForIdle()
    }

    /** The message the snackbar says, while one shows. */
    private fun snackbar(): String? =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(ShoppingTags.SNACKBAR)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().firstOrNull()?.config?.get(SemanticsProperties.Text)?.joinToString { it.text }

    // The list lies above the book alone; whatever else opens takes its place.
    @Test
    fun theListLiesAboveTheBookAlone() {
        assertEquals(listOf(Shown.Book, Shown.Shopping), shownFor(null, null, shopping = true))
        assertEquals(listOf(Shown.Book, Shown.Card("a")), shownFor("a", null, shopping = true))
        assertEquals(listOf(Shown.Book, Shown.Settings), shownFor(null, null, settings = true, shopping = true))
        session.openShopping()
        assertTrue(session.shopping.value)
        session.openCard("a")
        assertFalse(session.shopping.value)
        session.openShopping()
        session.openSettings()
        assertFalse(session.shopping.value)
    }

    @Test
    fun theBooksTopBarOpensTheListAndBackReturnsToTheBook() {
        showBook()
        val icon = compose.onNodeWithTag(LibraryTags.SHOPPING).fetchSemanticsNode()
        assertEquals("Shopping list", AccessibilityChecks.announcedLabel(icon))
        assertEquals("Open the shopping list", AccessibilityChecks.clickActionLabel(icon))
        openList()
        assertTrue(session.shopping.value)
        assertEquals(listOf("Shopping list"), compose.textsOf(ShoppingTags.TITLE))
        val back = compose.onNodeWithTag(ShoppingTags.BACK).fetchSemanticsNode()
        assertEquals("Back to the book", AccessibilityChecks.clickActionLabel(back))
        compose.onNodeWithTag(ShoppingTags.BACK).performClick()
        awaitBook()
        assertFalse(session.shopping.value)
        // The system's back returns to the book too.
        openList()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitBook()
        assertFalse(session.shopping.value)
    }

    @Test
    fun anEmptyListSaysHowToAddARecipe() {
        showBook()
        openList()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.EMPTY) }
        assertEquals(
            listOf("Your shopping list is empty", "Open a recipe and choose “Add to shopping list” in its ⋮ menu."),
            LocalizationChecks.shownText(compose).filter { it.startsWith("Your") || it.startsWith("Open a") },
        )
        // Nothing to clear: the ⋮ is not offered.
        assertFalse(exists(ShoppingTags.MENU))
    }

    // Scenarios 1, 2 and 6: the recipes at the top with their counts; the lines added up from both; a count set on the
    // list is kept and every line it feeds changes at once.
    @Test
    fun aCountSetOnTheListIsKeptAndEveryLineItFeedsChanges() {
        save(soup, pie)
        store.update { it.with(listed(soup)).with(listed(pie)) }
        showBook()
        openList()
        assertEquals(listOf("Onion soup"), compose.textsOf(ShoppingTags.recipeTitle(soup.id.value)))
        assertEquals(listOf("Servings: 2"), compose.textsOf(ShoppingTags.servings(soup.id.value)))
        assertEquals(listOf("Servings: 4"), compose.textsOf(ShoppingTags.servings(pie.id.value)))
        assertEquals(listOf("onion — 3 pc", "flour — ≈ 560 g", "sugar — 1 tbsp"), lines())
        // Under each line, muted, what it counted and the recipes it comes from.
        assertEquals(listOf("flour — ≈ 560 g", "2 cup counted as ≈ 260 g", "Onion soup, Onion pie"), compose.textsOf(ShoppingTags.line(1)))
        assertEquals(listOf("sugar — 1 tbsp", "Onion pie"), compose.textsOf(ShoppingTags.line(2)))
        val more = compose.onNodeWithTag(ShoppingTags.more(soup.id.value)).fetchSemanticsNode()
        assertEquals("Buy for more servings", AccessibilityChecks.clickActionLabel(more))
        tap(ShoppingTags.more(soup.id.value))
        tap(ShoppingTags.more(soup.id.value))
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(ShoppingTags.servings(soup.id.value)) == listOf("Servings: 4") }
        assertEquals(listOf("onion — 4 pc", "flour — ≈ 860 g", "sugar — 1 tbsp"), lines())
        assertEquals(listOf(listed(soup, r(4)), listed(pie)), store.list.value.recipes)
    }

    // The count walks as on the card: ¼ is the lowest, and there − is disabled.
    @Test
    fun aQuarterIsTheLowestCount() {
        save(soup)
        store.update { it.with(listed(soup, r(1, 3))) }
        showBook()
        openList()
        tap(ShoppingTags.fewer(soup.id.value))
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(ShoppingTags.servings(soup.id.value)) == listOf("Servings: ¼") }
        val fewer = compose.onNodeWithTag(ShoppingTags.fewer(soup.id.value)).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Disabled in fewer.config)
        assertEquals(listOf(listed(soup, r(1, 4))), store.list.value.recipes)
    }

    @Test
    fun aRecipeIsRemovedWithUndo() {
        save(soup, pie)
        store.update { it.with(listed(soup)).with(listed(pie)) }
        showBook()
        openList()
        val remove = compose.onNodeWithTag(ShoppingTags.remove(soup.id.value)).fetchSemanticsNode()
        assertEquals("Remove “Onion soup” from the list", AccessibilityChecks.announcedLabel(remove))
        tap(ShoppingTags.remove(soup.id.value))
        compose.waitUntil(PATIENCE_MS) { !exists(ShoppingTags.recipe(soup.id.value)) && snackbar() != null }
        assertEquals("“Onion soup” removed from the list", snackbar())
        assertEquals(listOf("onion — 2 pc", "flour — 2 cup", "sugar — 1 tbsp"), lines())
        assertEquals(listOf(listed(pie)), store.list.value.recipes)
        tapText("Undo")
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.recipe(soup.id.value)) }
        assertEquals(listOf(listed(soup), listed(pie)), store.list.value.recipes)
        assertEquals(listOf("onion — 3 pc", "flour — ≈ 560 g", "sugar — 1 tbsp"), lines())
    }

    @Test
    fun clearingTheListOffersUndo() {
        save(soup, pie)
        store.update { it.with(listed(soup)).with(listed(pie, r(6))) }
        showBook()
        openList()
        compose.onNodeWithTag(ShoppingTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.CLEAR) }
        assertEquals(listOf("Clear list"), compose.textsOf(ShoppingTags.CLEAR))
        compose.onNodeWithTag(ShoppingTags.CLEAR).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.EMPTY) && snackbar() != null }
        assertEquals("List cleared", snackbar())
        assertTrue(store.list.value.isEmpty)
        tapText("Undo")
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.recipe(pie.id.value)) }
        assertEquals(listOf(listed(soup), listed(pie, r(6))), store.list.value.recipes)
    }

    // A change of anything else on the list makes a removal final: its Undo goes.
    @Test
    fun anotherChangeMakesARemovalFinal() {
        save(soup, pie)
        store.update { it.with(listed(soup)).with(listed(pie)) }
        showBook()
        openList()
        tap(ShoppingTags.remove(soup.id.value))
        compose.waitUntil(PATIENCE_MS) { snackbar() != null }
        tap(ShoppingTags.more(pie.id.value))
        compose.waitUntil(PATIENCE_MS) { snackbar() == null }
        assertEquals(listOf(listed(pie, r(5))), store.list.value.recipes)
    }

    @Test
    fun aRecipeDeletedFromTheBookLeavesTheList() {
        save(soup, pie)
        store.update { it.with(listed(soup)).with(listed(pie)) }
        showBook()
        openList()
        runBlocking { assertTrue(recipes.delete(pie.id)) }
        compose.waitUntil(PATIENCE_MS) { !exists(ShoppingTags.recipe(pie.id.value)) }
        assertEquals(listOf("onion — 1 pc", "flour — 300 g"), lines())
        compose.waitUntil(PATIENCE_MS) { store.list.value.recipes == listOf(listed(soup)) }
    }

    // The list open when the screen turns, or when the process ends, is open again, above the book.
    @Test
    fun theListComesBackAfterAConfigurationChangeAndTheEndOfTheProcess() {
        save(soup)
        store.update { it.with(listed(soup)) }
        val tester = showBook()
        openList()
        tester.emulateSavedInstanceStateRestore()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.LIST) }
        session = LibrarySession(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        tester.emulateSavedInstanceStateRestore()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.LIST) }
        assertTrue(session.shopping.value)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitBook()
        assertFalse(session.shopping.value)
    }

    // At twice the font scale on a 320 dp screen every target stays 48 dp, nothing spills sideways and no text is cut.
    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleOnANarrowScreen() = narrowAtTwiceTheFontScale()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleOnANarrowScreen() = narrowAtTwiceTheFontScale()

    private fun narrowAtTwiceTheFontScale() {
        save(soup, pie)
        showBook()
        assertFits(ShoppingFit.BOOK)
        openList()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.EMPTY) }
        assertFits(ShoppingFit.SCREEN)
        compose.onNodeWithTag(ShoppingTags.BACK).performClick()
        awaitBook()
        store.update { it.with(listed(soup)).with(listed(pie)) }
        openList()
        assertFits(ShoppingFit.SCREEN)
        compose.onNodeWithTag(ShoppingTags.LIST).performScrollToNode(hasTestTag(ShoppingTags.line(1)))
        assertFits(ShoppingFit.SCREEN)
        compose.onNodeWithTag(ShoppingTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.CLEAR) }
        assertFits(ShoppingFit.SCREEN)
    }

    private enum class ShoppingFit(val tag: String) { BOOK(LibraryTags.TOP_BAR), SCREEN(ShoppingTags.SCREEN) }

    private fun assertFits(fit: ShoppingFit) {
        compose.waitForIdle()
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, except = AccessibilityChecks.snackbarPane)
        AccessibilityChecks.assertDistinctLabels(compose)
        val width = compose.onNodeWithTag(fit.tag).fetchSemanticsNode().boundsInRoot.right
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(texts.isNotEmpty())
        texts.forEach { node ->
            assertTrue("${node.config[SemanticsProperties.Text]} spills sideways", node.boundsInRoot.right <= width + 0.5f)
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { layout ->
                val cut = layout.didOverflowHeight || (0 until layout.lineCount).any(layout::isLineEllipsized)
                assertFalse("${node.config[SemanticsProperties.Text]} is cut", cut)
            }
        }
        // The top bar's actions overlap nothing: each stands apart from the others and from the title.
        val bar = listOfNotNull(
            if (fit == ShoppingFit.BOOK) LibraryTags.SHOPPING else ShoppingTags.BACK,
            if (fit == ShoppingFit.BOOK) LibraryTags.SETTINGS else ShoppingTags.MENU.takeIf(::exists),
            if (fit == ShoppingFit.BOOK) LibraryTags.TITLE else ShoppingTags.TITLE,
        ).map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        bar.forEachIndexed { i, a -> bar.drop(i + 1).forEach { b -> assertFalse("$a overlaps $b", a.overlaps(b)) } }
    }

    // L10N: the list reads in Russian, every word of the interface from the resources.
    @Test
    @Config(qualifiers = "ru")
    fun theListReadsInRussian() {
        save(soup, pie)
        store.update { it.with(listed(soup)).with(listed(pie)) }
        showBook()
        val icon = compose.onNodeWithTag(LibraryTags.SHOPPING).fetchSemanticsNode()
        assertEquals("Открыть список покупок", AccessibilityChecks.clickActionLabel(icon))
        openList()
        assertEquals(listOf("Список покупок"), compose.textsOf(ShoppingTags.TITLE))
        assertEquals(listOf("Порций: 2"), compose.textsOf(ShoppingTags.servings(soup.id.value)))
        assertEquals(listOf("лук — 3 шт", "мука — ≈ 560 г", "сахар — 1 ст. л."), lines())
        val content = setOf("Onion soup", "Onion pie", "Onion soup, Onion pie") + lines()
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        tap(ShoppingTags.remove(soup.id.value))
        compose.waitUntil(PATIENCE_MS) { snackbar() != null }
        assertEquals("Рецепт «Onion soup» убран из списка", snackbar())
        tapText("Вернуть")
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.recipe(soup.id.value)) }
        compose.onNodeWithTag(ShoppingTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.CLEAR) }
        assertEquals(listOf("Очистить список"), compose.textsOf(ShoppingTags.CLEAR))
        compose.onNodeWithTag(ShoppingTags.CLEAR).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.EMPTY) }
        LocalizationChecks.assertScreenTextFromResources(compose, context)
    }
}
