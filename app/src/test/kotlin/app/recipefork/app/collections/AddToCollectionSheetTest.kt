package app.recipefork.app.collections

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.CardScreen
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.card.assertWhole
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.cardMenuItems
import app.recipefork.app.card.openTheCardMenu
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.TextContrastChecks
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.flow.first
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
 * v0.36, T-253 — "Add to collection" from the card (scenario 1):
 *
 * - The card's ⋮, on View and on Cooking, holds "Add to collection" right after the shopping item.
 * - It opens a sheet of the reader's own collections, each ticked where the recipe already is; a tick or an untick is
 *   written as it is made; "New collection…" takes a name and makes the collection with the recipe in it, ticked.
 * - As the sheet closes on a change, the card says what the recipe is in now ("In 2 collections").
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class AddToCollectionSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val collections = CollectionRepository(active)
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    private val salad = Recipe.create(now, null).copy(title = "Olivier salad", steps = listOf(Step(0, "Mix it.")))

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun create(name: String): CollectionId = runBlocking { requireNotNull(collections.create(name)) }

    private fun holding(): Set<CollectionId> = runBlocking { collections.holding(salad.id).first() }

    /** Opens the salad's card on [tab], given the book's collections, or none. */
    private fun open(tab: CardMode = CardMode.VIEW, given: CollectionRepository? = collections) {
        runBlocking { recipes.save(salad) }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(tab), collections = given, ingredients = IngredientEntries { emptyList() })
        compose.setContent { AppTheme(dark = false) { CardScreen(salad.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(0)) }
        compose.waitForIdle()
    }

    /** Opens the sheet from the card's ⋮ and waits for its collections to be listed. */
    private fun openTheSheet() {
        compose.openTheCardMenu()
        compose.onNodeWithTag(CardTags.COLLECTION).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.SHEET) && exists(AddToCollectionTags.NEW) }
        compose.waitForIdle()
    }

    private fun row(id: CollectionId) = AddToCollectionTags.collection(id.value)

    private fun ticked(id: CollectionId): Boolean =
        compose.onNodeWithTag(row(id)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    /** Closes the sheet by its Done and waits until it is gone. */
    private fun done() {
        tap(AddToCollectionTags.DONE)
        compose.waitUntil(PATIENCE_MS) { !exists(AddToCollectionTags.SHEET) }
    }

    private fun inSnackbar(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(CardTags.SNACKBAR))

    private fun awaitNotice(text: String) = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(inSnackbar(text), useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }

    private fun noNotice() = compose.onAllNodes(hasTestTag(CardTags.SNACKBAR)).fetchSemanticsNodes().isEmpty()

    // The ⋮ read top to bottom, as since v0.33: the shopping item, then "Add to collection", on View and on Cooking.
    @Test
    fun theItemStandsRightAfterTheShoppingItemOnBothTabs() {
        open(CardMode.VIEW)
        assertEquals(
            listOf(CardTags.SHOPPING, CardTags.COLLECTION, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.DELETE),
            compose.cardMenuItems(),
        )
        compose.openTheCardMenu()
        assertEquals(listOf("Add to collection"), compose.textsOf(CardTags.COLLECTION))
        compose.assertCardTarget(CardTags.COLLECTION)
    }

    @Test
    fun cookingsMenuHoldsItTooRightAfterTheShoppingItem() {
        open(CardMode.COOKING)
        assertEquals(listOf(CardTags.SHOPPING, CardTags.COLLECTION, CardTags.PRINT, CardTags.SHARE_PDF), compose.cardMenuItems())
    }

    // Scenario 1: tick "New Year's menu" and "Salads" — the recipe is in both as each is ticked; the card says so.
    @Test
    fun tickingTwoCollectionsPutsTheRecipeInBothAsTheyAreTicked() {
        val menu = create("New Year's menu")
        val salads = create("Salads")
        val soups = create("Soups")
        open()
        openTheSheet()
        listOf(menu, salads, soups).forEach { assertFalse(ticked(it)) }
        assertFalse(exists(AddToCollectionTags.EMPTY))

        tap(row(menu))
        compose.waitUntil(PATIENCE_MS) { holding() == setOf(menu) }
        compose.waitUntil(PATIENCE_MS) { ticked(menu) }
        tap(row(salads))
        compose.waitUntil(PATIENCE_MS) { holding() == setOf(menu, salads) }
        compose.waitUntil(PATIENCE_MS) { ticked(salads) }
        assertFalse(ticked(soups))
        assertEquals(listOf(salad.id), runBlocking { collections.recipes(menu).first() }.map { RecipeId(it.recipeId) })

        done()
        awaitNotice("In 2 collections")
        assertEquals(setOf(menu, salads), holding())
    }

    // The sheet shows where the recipe already is; unticking takes it out at once, and ticking again puts it back.
    @Test
    fun theSheetShowsWhereTheRecipeIsAndUntickingTakesItOut() {
        val salads = create("Salads")
        val menu = create("New Year's menu")
        runBlocking { recipes.save(salad); collections.addTo(salad.id, listOf(salads, menu)) }
        open()
        openTheSheet()
        compose.waitUntil(PATIENCE_MS) { ticked(salads) && ticked(menu) }

        tap(row(salads))
        compose.waitUntil(PATIENCE_MS) { holding() == setOf(menu) }
        compose.waitUntil(PATIENCE_MS) { !ticked(salads) }
        tap(row(menu))
        compose.waitUntil(PATIENCE_MS) { holding().isEmpty() }
        done()
        awaitNotice("Not in any collection")

        openTheSheet()
        compose.waitUntil(PATIENCE_MS) { !ticked(salads) && !ticked(menu) }
        tap(row(menu))
        compose.waitUntil(PATIENCE_MS) { holding() == setOf(menu) }
        done()
        awaitNotice("In 1 collection")
    }

    // Closed with nothing changed, the sheet leaves the card saying nothing.
    @Test
    fun closingWithNoChangeSaysNothing() {
        create("Salads")
        open()
        openTheSheet()
        done()
        compose.waitForIdle()
        assertTrue(noNotice())
    }

    // "New collection…": a name, then Create — the collection is made with the recipe in it, shown ticked.
    @Test
    fun aNewCollectionIsMadeWithTheRecipeInItAndShownTicked() {
        val soups = create("Soups")
        open()
        openTheSheet()
        tap(AddToCollectionTags.NEW)
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.NAME) }
        compose.onNodeWithTag(AddToCollectionTags.NAME).assertIsFocused()
        assertEquals(ImeAction.Done, compose.onNodeWithTag(AddToCollectionTags.NAME).fetchSemanticsNode().config[SemanticsProperties.ImeAction])
        compose.onNodeWithTag(AddToCollectionTags.CREATE).assertIsNotEnabled()
        compose.onNodeWithTag(AddToCollectionTags.NAME).performTextInput("   ")
        compose.onNodeWithTag(AddToCollectionTags.CREATE).assertIsNotEnabled()
        compose.onNodeWithTag(AddToCollectionTags.NAME).performTextInput(" Salads ")
        compose.onNodeWithTag(AddToCollectionTags.CREATE).assertIsEnabled()
        compose.assertCardTarget(AddToCollectionTags.CREATE)
        tap(AddToCollectionTags.CREATE)

        compose.waitUntil(PATIENCE_MS) { runBlocking { collections.listed(CollectionOrigin.OWN).first() }.size == 2 }
        val made = runBlocking { collections.listed(CollectionOrigin.OWN).first() }.single { it.collectionId != soups.value }
        assertEquals("Salads", made.name)
        val salads = CollectionId(made.collectionId)
        compose.waitUntil(PATIENCE_MS) { holding() == setOf(salads) }
        compose.waitUntil(PATIENCE_MS) { exists(row(salads)) && ticked(salads) }
        assertFalse(ticked(soups))
        // The field turns back into "New collection…".
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.NEW) && !exists(AddToCollectionTags.NAME) }
        done()
        awaitNotice("In 1 collection")
    }

    // With no collection of their own, the sheet says so and offers "New collection…"; an imported one is not listed.
    @Test
    fun withNoOwnCollectionTheSheetSaysSoAndListsNoImportedOne() {
        runBlocking {
            recipes.save(salad)
            collections.putImported(CollectionId.mint(), "Anna's salads", "anna", listOf(salad.id))
        }
        open()
        openTheSheet()
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.EMPTY) }
        assertEquals(listOf("No collections yet"), compose.textsOf(AddToCollectionTags.EMPTY))
        assertTrue(compose.onAllNodes(hasText("Anna's salads")).fetchSemanticsNodes().isEmpty())
    }

    // A card not given the book's collections offers no such item.
    @Test
    fun aCardGivenNoCollectionsOffersNoItem() {
        open(given = null)
        assertEquals(listOf(CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.DELETE), compose.cardMenuItems())
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianTheItemTheSheetAndTheNoticeSaySo() {
        val menu = create("Новогоднее меню")
        val salads = create("Салаты")
        open()
        compose.openTheCardMenu()
        assertEquals(listOf("Добавить в коллекцию"), compose.textsOf(CardTags.COLLECTION))
        compose.onNodeWithTag(CardTags.COLLECTION).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.NEW) }
        assertEquals(listOf("Добавить в коллекцию"), compose.textsOf(AddToCollectionTags.TITLE))
        assertEquals(listOf("Новая коллекция…"), compose.textsOf(AddToCollectionTags.NEW))
        assertEquals(listOf("Готово"), compose.textsOf(AddToCollectionTags.DONE))
        tap(row(menu))
        tap(row(salads))
        compose.waitUntil(PATIENCE_MS) { holding() == setOf(menu, salads) }
        done()
        awaitNotice("В 2 коллекциях")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun theRussianNoticeTakesEachPluralForm() {
        val resources = context.resources
        assertEquals("В 1 коллекции", noticeOf(resources, 1))
        assertEquals("В 3 коллекциях", noticeOf(resources, 3))
        assertEquals("В 5 коллекциях", noticeOf(resources, 5))
        assertEquals("В 21 коллекции", noticeOf(resources, 21))
        assertEquals("Ни в одной коллекции", noticeOf(resources, 0))
    }

    // At twice the font scale on a 320 dp phone: every row and action the card's target, every text whole, labelled,
    // readable — in English and in Russian, the field open too.
    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atTwiceTheFontScaleOnA320dpPhoneTheSheetReadsWhole() = readsWhole()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun inRussianAtTwiceTheFontScaleOnA320dpPhoneTheSheetReadsWhole() = readsWhole()

    private fun readsWhole() {
        val long = create("Holiday dinners for the whole family and friends")
        val menu = create("New Year's menu")
        runBlocking { recipes.save(salad); collections.addTo(salad.id, listOf(menu)) }
        open()
        compose.openTheCardMenu()
        compose.assertWhole(CardTags.COLLECTION)
        compose.assertCardTarget(CardTags.COLLECTION)
        compose.onNodeWithTag(CardTags.COLLECTION).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(row(long)) && exists(row(menu)) }
        compose.waitUntil(PATIENCE_MS) { ticked(menu) }
        listOf(AddToCollectionTags.TITLE, AddToCollectionTags.DONE, AddToCollectionTags.NEW, row(long), row(menu)).forEach {
            compose.assertWhole(it)
        }
        listOf(AddToCollectionTags.DONE, AddToCollectionTags.NEW, row(long), row(menu)).forEach { compose.assertCardTarget(it) }
        checkTheScreen()
        tap(AddToCollectionTags.NEW)
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.NAME) }
        compose.onNodeWithTag(AddToCollectionTags.NAME).performTextInput("Salads")
        compose.assertWhole(AddToCollectionTags.CREATE)
        compose.assertCardTarget(AddToCollectionTags.CREATE)
        checkTheScreen()
    }

    private fun checkTheScreen() {
        compose.waitForIdle()
        AccessibilityChecks.assertTouchTargets(compose, min = 48.dp)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, except = AccessibilityChecks.snackbarPane)
        AccessibilityChecks.assertDistinctLabels(compose)
        TextContrastChecks.assertTextContrast(compose)
    }
}
