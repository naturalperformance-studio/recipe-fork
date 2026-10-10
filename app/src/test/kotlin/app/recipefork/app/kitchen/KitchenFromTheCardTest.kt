package app.recipefork.app.kitchen

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.EquipmentTags
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.chooseCardTab
import app.recipefork.app.card.scrollClearOfFork
import app.recipefork.app.card.textLayoutsWithin
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.data.HeldThemeSetting
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
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
import kotlin.math.abs

/**
 * T-213, scenario 6 of v0.29: on a recipe's View an item of equipment marked "Not in your kitchen" is a button that
 * opens My kitchen at that item — scrolled to it, the row standing out — above the card; ticked there, Back (the bar's
 * or the system's) returns to the same card, on View, at the same place, the mark gone. Other lines, free text and
 * Cooking behave as before; My kitchen so opened outlasts the screen turning and the end of the process, Back from it
 * never lands in the settings, and the settings behave as before afterwards.
 */
@RunWith(AndroidJUnit4::class)
class KitchenFromTheCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val saved = SavedStateHandle()
    private var session = LibrarySession(saved)
    private val store = HeldKitchen(setOf(FRYING_PAN))
    private val equipment = EquipmentSource { DICTIONARY }
    private val card = CardEnvironment(
        HeldUsualServings(), lastTab = HeldLastCardTab(CardMode.VIEW), equipment = equipment, kitchen = store,
        ingredients = IngredientEntries { emptyList() },
    )
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = emptyList<TagEntity>()
        },
    ) { NOW }
    private val kitchen = KitchenEnvironment(store, equipment)

    /** The frying pan (had), the kitchen scale (lacked, far down My kitchen), free text; a description that scrolls. */
    private val soup = Recipe.create(NOW, null).copy(
        title = "Soup",
        description = List(12) { "A long line of the description, to be scrolled past before the equipment." }.joinToString(" "),
        servings = 2,
        equipment = listOf(EquipmentItem.entry(FRYING_PAN), EquipmentItem.entry(SCALE), EquipmentItem.free("Ladle")),
        ingredients = listOf(Ingredient(0, null, freeTextName = "Water", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500))),
        steps = listOf(Step(0, "Boil the water.")),
    )

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String, unmerged: Boolean = false) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

    private fun line(index: Int) = CardTags.equipment(index)

    private fun lacking(): List<Int> = soup.equipment.indices.filter { exists(EquipmentTags.lacking(it), unmerged = true) }

    private fun showCard(): StateRestorationTester {
        runBlocking { recipes.save(soup) }
        session.choose(LibraryView.OWN)
        val tester = StateRestorationTester(compose)
        // The session read anew each time the content is: a process ended and restored brings a new one.
        tester.setContent {
            MaterialTheme(colorScheme = scheme) {
                AppNavHost(session, recipes, card = card, editor = editor, themeSetting = HeldThemeSetting(), kitchen = kitchen)
            }
        }
        compose.runOnIdle { session.openCard(soup.id.value, LibraryView.OWN) }
        compose.awaitCard()
        // The scale marked once the dictionary is read, by which the items are named too.
        compose.waitUntil(PATIENCE_MS) { lacking() == listOf(1) }
        compose.waitForIdle()
        return tester
    }

    /** Taps the lacking line, clear of the fork action, and waits for My kitchen in place of the card. */
    private fun openFromTheLine() {
        compose.scrollClearOfFork(line(1))
        compose.onNodeWithTag(line(1)).performClick()
        awaitKitchen()
    }

    private fun awaitKitchen() = compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.item(SCALE)) && !exists(CardTags.SCREEN) }

    private fun awaitTheCard() {
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.SCREEN) && !exists(KitchenTags.SCREEN) }
        compose.awaitCard()
    }

    private fun surfaceTop() = compose.onNodeWithTag(EquipmentTags.SURFACE).fetchSemanticsNode().boundsInRoot.top

    private fun pressSystemBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    /** The item's row in My kitchen is in sight, and stands out: in words, and on the primary container. */
    private fun assertStandsOut(needed: String) {
        compose.onNodeWithTag(KitchenTags.item(SCALE)).assertIsDisplayed()
        assertEquals(needed, compose.textsOf(KitchenTags.item(SCALE)).last())
        val name = compose.textLayoutsWithin(KitchenTags.item(SCALE)).first()
        assertEquals(scheme.onPrimaryContainer, name.layoutInput.style.color)
    }

    // The marked line is a button "Open My kitchen", the card's target tall; the others answer no tap on View.
    // A tap opens My kitchen above the card, at the item; ticked there, the bar's Back returns to the card on View at
    // the same place, the mark gone; again with the system's Back.
    @Test
    fun theMarkedLineOpensMyKitchenAtItsItemAndBackReturnsToTheSameCard() {
        showCard()
        val marked = compose.onNodeWithTag(line(1)).fetchSemanticsNode()
        assertEquals(Role.Button, marked.config.getOrNull(SemanticsProperties.Role))
        assertEquals("Open My kitchen", AccessibilityChecks.clickActionLabel(marked))
        compose.assertCardTarget(line(1))
        listOf(0, 2).forEach { index ->
            assertNull("line $index answers no tap", compose.onNodeWithTag(line(index)).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        }

        val atOpening = surfaceTop()
        compose.scrollClearOfFork(line(1))
        val top = surfaceTop()
        assertTrue("the card is scrolled down to its equipment", top < atOpening)
        compose.onNodeWithTag(line(1)).performClick()
        awaitKitchen()
        assertTrue(session.kitchen.value)
        assertFalse(session.settings.value)
        assertEquals(SCALE, session.kitchenAt.value)
        assertStandsOut("Needed for the recipe")
        assertEquals("Back to the recipe", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(KitchenTags.BACK).fetchSemanticsNode()))

        compose.onNodeWithTag(KitchenTags.item(SCALE)).performClick()
        compose.waitForIdle()
        assertEquals(setOf(FRYING_PAN, SCALE), store.held.value)
        compose.onNodeWithTag(KitchenTags.BACK).performClick()
        awaitTheCard()
        compose.onNodeWithTag(CardTags.tab(CardMode.VIEW)).assertIsSelected()
        compose.waitUntil(PATIENCE_MS) { lacking().isEmpty() && exists(EquipmentTags.SURFACE) && abs(surfaceTop() - top) <= 2f }
        assertEquals(soup.id.value, session.viewing.value)
        assertFalse(session.kitchen.value)
        assertNull(compose.onNodeWithTag(line(1)).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))

        // The system's Back does the same.
        compose.runOnIdle { store.hold(listOf(SCALE), has = false) }
        compose.waitUntil(PATIENCE_MS) { lacking() == listOf(1) }
        openFromTheLine()
        compose.onNodeWithTag(KitchenTags.item(SCALE)).performClick()
        compose.waitForIdle()
        pressSystemBack()
        awaitTheCard()
        compose.onNodeWithTag(CardTags.tab(CardMode.VIEW)).assertIsSelected()
        compose.waitUntil(PATIENCE_MS) { lacking().isEmpty() && exists(EquipmentTags.SURFACE) && abs(surfaceTop() - top) <= 2f }
        assertFalse(session.kitchen.value)
        assertFalse(session.settings.value)
        // And Back from the card goes on to the book, never to the settings.
        pressSystemBack()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.PAGER) && !exists(CardTags.SCREEN) && !exists(SettingsTags.SCREEN) }
    }

    // On Cooking a tap on the marked line marks it done, as before, and opens nothing.
    @Test
    fun onCookingATapStillMarksTheLineDone() {
        showCard()
        compose.chooseCardTab(CardMode.COOKING)
        compose.waitUntil(PATIENCE_MS) { lacking() == listOf(1) }
        compose.scrollClearOfFork(line(1))
        compose.onNodeWithTag(line(1)).performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(line(1)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "Done"
        }
        assertFalse(session.kitchen.value)
        assertFalse(exists(KitchenTags.SCREEN))
    }

    // My kitchen opened from the card is open again, at its item, after the screen turns and after the process ends;
    // Back returns to the card, not to the settings; the settings and their My kitchen behave as before afterwards.
    @Test
    fun itOutlastsTheScreenTurningAndTheEndOfTheProcess() {
        val tester = showCard()
        openFromTheLine()
        tester.emulateSavedInstanceStateRestore()
        awaitKitchen()
        assertStandsOut("Needed for the recipe")
        session = LibrarySession(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        tester.emulateSavedInstanceStateRestore()
        awaitKitchen()
        compose.waitForIdle()
        assertEquals(SCALE, session.kitchenAt.value)
        assertStandsOut("Needed for the recipe")
        pressSystemBack()
        awaitTheCard()
        assertEquals(soup.id.value, session.viewing.value)
        assertFalse(session.kitchen.value)
        assertFalse(session.settings.value)
        compose.waitUntil(PATIENCE_MS) { lacking() == listOf(1) }

        // The settings afterwards: My kitchen opens from them as ever, nothing standing out, and Back returns to them.
        compose.runOnIdle {
            session.closeCard()
            session.openSettings()
        }
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.KITCHEN) && !exists(KitchenTags.SCREEN) && !exists(CardTags.SCREEN) }
        compose.onNodeWithTag(SettingsTags.KITCHEN).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) && !exists(SettingsTags.SCREEN) }
        assertNull(session.kitchenAt.value)
        assertEquals("Back to the settings", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(KitchenTags.BACK).fetchSemanticsNode()))
        compose.onNodeWithTag(KitchenTags.LIST).performScrollToKey(SCALE)
        assertEquals(listOf("Kitchen scale"), compose.textsOf(KitchenTags.item(SCALE)))
        pressSystemBack()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.SCREEN) && !exists(KitchenTags.SCREEN) }
        assertTrue(session.settings.value)
    }

    // L10N: the button's action, the row's words and the back action in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun readsInRussian() {
        showCard()
        assertEquals(listOf("Кухонные весы", "Нет на кухне"), compose.textsOf(line(1)))
        assertEquals("Открыть «Мою кухню»", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(line(1)).fetchSemanticsNode()))
        openFromTheLine()
        assertStandsOut("Нужно для рецепта")
        assertEquals("Вернуться к рецепту", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(KitchenTags.BACK).fetchSemanticsNode()))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-10T12:00:00Z")
        val scheme = lightColorScheme()

        /** The small dictionary, its stovetop cookware grown long, so that the scale stands far down My kitchen. */
        val DICTIONARY: List<EquipmentEntryEntity> = KITCHEN_DICTIONARY + List(30) { i ->
            EquipmentEntryEntity(10030L + i, "Сотейник $i", "Sauté pan $i", STOVETOP)
        }
    }
}
