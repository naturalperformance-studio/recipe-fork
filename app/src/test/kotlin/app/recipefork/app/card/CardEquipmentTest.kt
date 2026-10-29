package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
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
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Scenario 4 of v0.27: the card shows the equipment a recipe needs above its ingredients — compact on View, and on
 * Cooking as lines of the card's target that mark done as ingredients do, kept with the recipe's other marks, cleared
 * with them, a changed item's mark dropped; once every item is marked the list folds on its own into "All gathered ·
 * show", shown again apart from the ingredients. A recipe without equipment shows no block.
 */
@RunWith(AndroidJUnit4::class)
class CardEquipmentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    /** The device's marks, as the application keeps them. */
    private val marks = DoneMarks.preferences(context)

    private val pilaf = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        equipment = listOf("Cauldron", "Skimmer").map(EquipmentItem::free),
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Rice", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(350)),
        ),
        steps = listOf(Step(0, "Fry the chicken."), Step(1, "Add the rice.")),
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun open(tab: CardMode, recipe: Recipe = pilaf): StateRestorationTester {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(
            marks = marks, lastTab = HeldLastCardTab(tab), equipment = { equipmentDictionary }, ingredients = IngredientEntries { emptyList() },
        )
        val tester = StateRestorationTester(compose)
        tester.setContent {
            MaterialTheme(colorScheme = scheme) { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
        }
        awaitIngredients()
        return tester
    }

    private fun awaitIngredients() {
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.INGREDIENTS) }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun item(index: Int) = CardTags.equipment(index)

    /** The items read as done, as the card shows them — struck through and muted where read so. */
    private fun doneItems(): List<Int> = pilaf.equipment.indices.filter { index ->
        // On View the state may also say that the item is in the kitchen: "In your kitchen, Done".
        val state = node(item(index)).config.getOrNull(SemanticsProperties.StateDescription).orEmpty().split(", ")
        val read = state.any { it in setOf("Done", "Готово") }
        val struck = compose.textLayoutsWithin(item(index)).any {
            it.layoutInput.style.textDecoration == TextDecoration.LineThrough && it.layoutInput.style.color == scheme.onSurfaceVariant
        }
        assertEquals("item $index is read as it is drawn", read, struck)
        read
    }

    /** The equipment as the card shows it: its items, or the one line it folded into. */
    private fun equipmentFolded(): Boolean {
        val items = pilaf.equipment.indices.count { exists(item(it)) }
        val gathered = exists(EquipmentTags.GATHERED)
        assertTrue("the equipment shows all its items or none: $items, folded: $gathered", items == if (gathered) 0 else pilaf.equipment.size)
        return gathered
    }

    private fun ingredientsFolded() = exists(IngredientTags.GATHERED)

    private fun clearMarks() {
        compose.openTheCardMenu()
        compose.onNodeWithTag(CardTags.CLEAR_MARKS).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.CLEAR_MARKS) }
        compose.waitForIdle()
    }

    private fun markAllEquipment() = pilaf.equipment.indices.forEach { tap(item(it)) }

    private fun markAllIngredients() = pilaf.ingredients.forEach { tap(CardTags.ingredient(it.position)) }

    // View: the block "Equipment" above the ingredients, its items in order, each a row of the page 48 dp tall
    // (redesign-1 § 4.2), no tap on one the kitchen does not lack. The screen is tall, so that every part is measured whole.
    @Test
    @Config(qualifiers = "w360dp-h2000dp")
    fun viewListsTheEquipmentCompactlyAboveTheIngredients() {
        open(CardMode.VIEW)
        val heading = hasText("Equipment") and hasAnyAncestor(hasTestTag(CardTags.EQUIPMENT))
        compose.onNode(heading).assertExists()
        assertEquals(listOf("Cauldron"), compose.textsOf(item(0)))
        assertEquals(listOf("Skimmer"), compose.textsOf(item(1)))
        assertTrue("the equipment stands above the ingredients", node(EquipmentTags.SURFACE).boundsInRoot.bottom <= node(CardTags.INGREDIENTS).boundsInRoot.top)
        assertTrue("the items in their order", node(item(0)).boundsInRoot.bottom <= node(item(1)).boundsInRoot.top + 0.5f)
        // A row of the page, lower than the card's target, and answering no tap.
        val density = compose.density.density
        val itemHeight = node(item(0)).boundsInRoot.height / density
        assertEquals(VIEW_ROW_HEIGHT.value, itemHeight, 1f)
        assertTrue("$itemHeight dp is lower than a target", itemHeight < CardTarget.value)
        assertNull(node(item(0)).config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theEquipmentReadsInRussian() {
        open(CardMode.COOKING)
        compose.onNode(hasText("Посуда и техника") and hasAnyAncestor(hasTestTag(CardTags.EQUIPMENT))).assertExists()
        assertEquals("Отметить как готовое", AccessibilityChecks.clickActionLabel(node(item(0))))
        markAllEquipment()
        assertTrue(equipmentFolded())
        assertEquals(listOf("Всё собрано"), compose.textsOf(EquipmentTags.GATHERED))
        assertEquals("Показать посуду и технику", AccessibilityChecks.clickActionLabel(node(EquipmentTags.GATHERED)))
    }

    /** The device's equipment dictionary the card names items from: an entry it offers, and one removed from it. */
    private val equipmentDictionary = listOf(
        EquipmentEntryEntity(10001, "Сковорода", "Frying pan", 10000),
        EquipmentEntryEntity(10950, "Казан", "Kazan", 10000, removed = true),
    )

    private val named = pilaf.copy(
        equipment = listOf(EquipmentItem.entry(10950, "8 l"), EquipmentItem.entry(10001), EquipmentItem.free("Skimmer")),
    )

    // An item an entry names reads as the entry's label in the interface language and its qualifier — an entry removed
    // from the dictionary too — on View and on Cooking; free text reads as itself.
    @Test
    fun anEntrysItemReadsAsItsLabelAndQualifier() {
        open(CardMode.VIEW, named)
        assertEquals(listOf("Kazan 8 l"), compose.textsOf(item(0)))
        assertEquals(listOf("Frying pan"), compose.textsOf(item(1)))
        assertEquals(listOf("Skimmer"), compose.textsOf(item(2)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun anEntrysItemReadsInRussian() {
        open(CardMode.COOKING, named)
        assertEquals(listOf("Казан 8 l"), compose.textsOf(item(0)))
        assertEquals(listOf("Сковорода"), compose.textsOf(item(1)))
    }

    // A recipe without equipment shows no block, on either tab.
    @Test
    fun aRecipeWithoutEquipmentShowsNoBlock() {
        open(CardMode.VIEW, pilaf.copy(equipment = emptyList()))
        assertFalse(exists(CardTags.EQUIPMENT) || exists(EquipmentTags.SURFACE))
        compose.chooseCardTab(CardMode.COOKING)
        assertFalse(exists(CardTags.EQUIPMENT) || exists(EquipmentTags.SURFACE))
    }

    // Cooking: lines of the card's target that a tap marks done and another unmarks, kept with the recipe's marks.
    @Test
    fun cookingMarksAnItemDoneAsAnIngredient() {
        open(CardMode.COOKING)
        pilaf.equipment.indices.forEach { compose.assertCardTarget(item(it)) }
        assertEquals("Mark as done", AccessibilityChecks.clickActionLabel(node(item(1))))
        tap(item(1))
        assertEquals(listOf(1), doneItems())
        assertEquals("Mark as not done", AccessibilityChecks.clickActionLabel(node(item(1))))
        assertEquals(setOf(DoneMark.ofEquipment(1, EquipmentItem.free("Skimmer"))), marks.held(BookName.Pool, pilaf.id))
        // Shown on View, as an ingredient's mark is, and not changed by a tap there.
        compose.chooseCardTab(CardMode.VIEW)
        assertEquals(listOf(1), doneItems())
        compose.chooseCardTab(CardMode.COOKING)
        tap(item(1))
        assertEquals(emptyList<Int>(), doneItems())
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget)
    }

    // Clear marks takes the equipment's marks with the others; the menu offers it for an item's mark alone.
    @Test
    fun clearMarksClearsTheEquipment() {
        open(CardMode.COOKING)
        assertEquals(listOf(CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF), compose.cardMenuItems())
        tap(item(0))
        assertEquals(listOf(CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.CLEAR_MARKS), compose.cardMenuItems())
        tap(CardTags.ingredient(0))
        clearMarks()
        assertEquals(emptyList<Int>(), doneItems())
        assertEquals(emptySet<DoneMark>(), marks.held(BookName.Pool, pilaf.id))
    }

    // An item changed in the editor loses its mark, for good, as an ingredient line does; the others keep theirs.
    @Test
    fun aMarkOfAnItemChangedInTheEditorIsDropped() {
        marks.hold(BookName.Pool, pilaf.id, setOf(DoneMark.ofEquipment(0, EquipmentItem.free("Cauldron")), DoneMark.ofEquipment(1, EquipmentItem.free("Skimmer"))))
        open(CardMode.COOKING)
        assertTrue(equipmentFolded())
        compose.onNodeWithTag(EquipmentTags.GATHERED).performClick()
        compose.waitForIdle()

        val changed = pilaf.copy(equipment = listOf("Cauldron 6 l", "Skimmer").map(EquipmentItem::free))
        runBlocking { recipes.save(changed) }
        compose.waitUntil(PATIENCE_MS) { exists(item(0)) && compose.textsOf(item(0)) == listOf("Cauldron 6 l") }
        compose.waitForIdle()
        assertEquals(listOf(1), doneItems())
        // Changed back, the item does not bring its old mark back.
        runBlocking { recipes.save(pilaf) }
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(item(0)) == listOf("Cauldron") }
        compose.waitForIdle()
        assertEquals(listOf(1), doneItems())
        assertEquals(setOf(DoneMark.ofEquipment(1, EquipmentItem.free("Skimmer"))), marks.held(BookName.Pool, pilaf.id))
    }

    // Every item marked folds the equipment into its one line, on its own: the ingredients stand until theirs are all
    // marked, and each list is shown again apart from the other.
    @Test
    fun theEquipmentFoldsOnItsOwn() {
        open(CardMode.COOKING)
        tap(item(0))
        assertFalse(equipmentFolded())
        tap(item(1))
        assertTrue(equipmentFolded())
        assertFalse("the ingredients stand", ingredientsFolded())
        assertEquals(listOf("All gathered"), compose.textsOf(EquipmentTags.GATHERED))
        val shown = compose.onAllNodes(hasAnyAncestor(hasTestTag(EquipmentTags.GATHERED)), useUnmergedTree = true)
            .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(ShownText) }
        assertEquals(listOf("All gathered · show"), shown)
        assertEquals("Show the equipment", AccessibilityChecks.clickActionLabel(node(EquipmentTags.GATHERED)))
        compose.assertCardTarget(EquipmentTags.GATHERED)

        markAllIngredients()
        assertTrue(ingredientsFolded())
        // Showing the equipment again leaves the ingredients folded, and the other way round.
        tap(EquipmentTags.GATHERED)
        assertFalse(equipmentFolded())
        assertTrue(ingredientsFolded())
        assertEquals(listOf(0, 1), doneItems())
        tap(item(0))
        tap(item(0))
        assertTrue("an item unmarked and marked again folds the equipment again", equipmentFolded())
        tap(IngredientTags.GATHERED)
        assertTrue(equipmentFolded())
        assertFalse(ingredientsFolded())
    }

    // Clearing the marks shows the equipment again; View never folds it.
    @Test
    fun clearingTheMarksShowsTheEquipmentAndViewNeverFoldsIt() {
        marks.hold(BookName.Pool, pilaf.id, setOf(DoneMark.ofEquipment(0, EquipmentItem.free("Cauldron")), DoneMark.ofEquipment(1, EquipmentItem.free("Skimmer"))))
        open(CardMode.VIEW)
        assertFalse(equipmentFolded())
        assertEquals(listOf(0, 1), doneItems())
        compose.chooseCardTab(CardMode.COOKING)
        assertTrue(equipmentFolded())
        clearMarks()
        assertFalse(equipmentFolded())
        assertEquals(emptyList<Int>(), doneItems())
    }

    // Shown again, the equipment stays shown through a turn to View and back and the activity being recreated, while
    // the ingredients, folded, stay folded.
    @Test
    fun equipmentShownAgainStaysShownThroughATurnAndRecreation() {
        marks.hold(
            BookName.Pool, pilaf.id,
            pilaf.ingredients.map(DoneMark::of).toSet() + setOf(DoneMark.ofEquipment(0, EquipmentItem.free("Cauldron")), DoneMark.ofEquipment(1, EquipmentItem.free("Skimmer"))),
        )
        val tester = open(CardMode.COOKING)
        assertTrue(equipmentFolded())
        assertTrue(ingredientsFolded())
        tap(EquipmentTags.GATHERED)
        assertFalse(equipmentFolded())

        compose.chooseCardTab(CardMode.VIEW)
        compose.chooseCardTab(CardMode.COOKING)
        assertFalse("shown again, the equipment stays shown on coming back to Cooking", equipmentFolded())
        assertTrue(ingredientsFolded())

        tester.emulateSavedInstanceStateRestore()
        awaitIngredients()
        assertFalse("shown again, the equipment stays shown once the activity is recreated", equipmentFolded())
        assertTrue(ingredientsFolded())
        assertEquals(listOf(0, 1), doneItems())
    }

    private companion object {
        val scheme = lightColorScheme()
    }
}
