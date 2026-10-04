package app.recipefork.app.card

import app.recipefork.app.ui.assertChangesGradually
import android.content.Context
import androidx.activity.ComponentActivity
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.children
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performImeAction
import org.robolectric.Shadows.shadowOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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
import java.time.Instant

/**
 * Scaling from what the reader has of one ingredient, on the card (scenario 5; SCALE-002, SCALE-008, SCALE-016,
 * SCALE-017, SCALE-018): tapping a line opens the fixing control, an accepted amount scales the whole recipe and the
 * count reads as approximate, a refused one says what is held, a second fixing releases the first (T-065), and a
 * change of count or a release undoes it.
 */
@RunWith(AndroidJUnit4::class)
class CardFixTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    /** For 4: chicken and rice by weight, indivisible eggs, salt by the teaspoon, pepper to taste, flour unfinished. */
    private val forFour: Recipe = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Rice", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(350)),
            Ingredient(2, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
            Ingredient(3, null, freeTextName = "Salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.ONE),
            Ingredient(4, null, freeTextName = "Pepper", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
            Ingredient(5, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = null),
        ),
    )

    private fun open() {
        runBlocking { recipes.save(forFour) }
        compose.setContent { MaterialTheme { CardScreen(forFour.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun line(position: Int) = compose.textsOf(CardTags.ingredient(position))
    private fun quantity(position: Int) = line(position)[1]
    private fun controlShown() = compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty()

    private fun tap(position: Int) {
        compose.onNodeWithTag(CardTags.ingredient(position)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { controlShown() }
    }

    private fun type(text: String) {
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun confirm() {
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
    }

    private fun fix(position: Int, amount: String) {
        tap(position)
        type(amount)
        confirm()
    }

    private fun problem() = compose.textsOf(CardTags.FIX_PROBLEM).single()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Picks [unit] from the control's unit drop-down, as the reader does: open it, then tap the unit. */
    private fun pickUnit(unit: QuantityUnit) {
        compose.onNodeWithTag(FixTags.UNIT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.fixUnit(unit)) }
        compose.onNodeWithTag(CardTags.fixUnit(unit)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.fixUnit(unit)) }
    }

    private fun more() {
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    // Scenario 5, SCALE-002, SCALE-003: 300 g of the 500 g chicken; ≈2 servings; every line at the same exact 3/5.
    @Test
    fun fixingWhatYouHaveRecalculatesTheWholeRecipe() {
        open()
        fix(0, "300")
        assertEquals("Servings: ≈2", servings())
        assertEquals(listOf("Chicken", "300 g", "fixed"), line(0))
        // 350 g × 3/5 = 210 g, not the 175 g that 2 servings would give.
        assertEquals(listOf("Rice", "210 g"), line(1))
        assertEquals(listOf("Eggs", "2 pc", "rounded up, exact value was lower"), line(2))
        assertEquals(listOf("Salt", "⅔ tsp", "rounded up, exact value was lower"), line(3))
        assertEquals(listOf("Pepper", "to taste"), line(4))
    }

    // SCALE-018 (7): a row to taste, or one left unfinished, offers nothing to fix.
    @Test
    fun aRowWithoutAQuantityOffersNoFixing() {
        open()
        listOf(4, 5).forEach { position ->
            compose.onNodeWithTag(CardTags.ingredient(position)).assert(hasClickAction().not())
            compose.onNodeWithTag(CardTags.ingredient(position)).performClick()
            compose.waitForIdle()
            assertFalse("row $position", controlShown())
        }
        compose.onNodeWithTag(CardTags.ingredient(0)).assert(hasClickAction())
    }

    // SCALE-018, DOM-019, DOM-017: what the control does not hold it answers with what it holds, and fixes nothing.
    @Test
    fun anAmountTheControlDoesNotHoldSaysWhatItHolds() {
        open()
        tap(0)
        type("109,375")
        assertEquals("This field accepts a whole number of grams, and from 1000 kg a whole number of kilograms.", problem())
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).assertIsNotEnabled()
        type("0")
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).assertIsNotEnabled()
        type("1 000")
        assertEquals("A number is written without separators: no spaces, and at most one comma or point.", problem())
        type("")
        compose.onNodeWithTag(CardTags.FIX_PROBLEM).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).assertIsNotEnabled()
        // Gram reads in kilograms without conversion, with a comma or a point.
        pickUnit(QuantityUnit.KILOGRAM)
        type("0,3")
        confirm()
        assertEquals("300 g", quantity(0))
        assertEquals("Servings: ≈2", servings())
    }

    @Test
    fun anIndivisiblePieceHoldsWholePiecesOnly() {
        open()
        tap(2)
        type("2,5")
        assertEquals("This field accepts a whole number of pieces, 1 or more.", problem())
        type("2")
        confirm()
        assertEquals("Servings: ≈3", servings())
        assertEquals("333 g", quantity(0))
    }

    private val fixedNote get() = context.getString(R.string.card_quantity_fixed)
    private fun fixed(position: Int) = fixedNote in line(position)

    /** Whether the row's fixing control offers a release, i.e. the card holds a fixing of this row. */
    private fun holdsFixing(position: Int): Boolean {
        tap(position)
        val held = compose.onAllNodes(hasTestTag(CardTags.FIX_RELEASE)).fetchSemanticsNodes().isNotEmpty()
        compose.onNodeWithTag(CardTags.FIX_CANCEL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        return held
    }

    // T-065: fixing rice while chicken is fixed releases chicken; 525 g of 350 g sets 3/2 for every row.
    @Test
    fun aSecondFixingReleasesTheFirst() {
        open()
        fix(0, "300")
        fix(1, "525")
        assertEquals("Servings: ≈6", servings())
        assertEquals("750 g", quantity(0))
        assertEquals("525 g", quantity(1))
        assertTrue(fixed(1))
        assertFalse(fixed(0))
        // The chicken line reads as an ordinary recalculated line: the same notes as rice's but for "fixed".
        assertEquals(line(1).drop(2) - fixedNote, line(0).drop(2))
        assertEquals(listOf(false, true, false, false), (0..3).map(::fixed))
        assertFalse(holdsFixing(0))
        assertTrue(holdsFixing(1))
    }

    // SCALE-017 for the chain: at 4, fix chicken, then rice, release rice → back to 4, not approximate.
    @Test
    fun releasingTheSecondFixingReturnsToTheCountBeforeTheFirst() {
        open()
        val atFour = (0..3).map(::line)
        fix(0, "300")
        fix(1, "525")
        tap(1)
        compose.onNodeWithTag(CardTags.FIX_RELEASE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        assertEquals("Servings: 4", servings())
        assertEquals(atFour, (0..3).map(::line))
        assertEquals("500 g", quantity(0))
        assertFalse(fixed(0))
    }

    // SCALE-016: a change of count releases every fixing, and the count asked for is not approximate.
    @Test
    fun changingTheCountReleasesEveryFixing() {
        open()
        fix(0, "300")
        fix(2, "3")
        more()
        // + walks from the count shown, ≈4, to 5.
        assertEquals("Servings: 5", servings())
        assertEquals(listOf("Chicken", "625 g"), line(0))
        assertEquals(listOf("Eggs", "4 pc", "rounded up, exact value was lower"), line(2))
    }

    // SCALE-017: releasing the last fixing returns to the count in force before the first; clearing is no release.
    @Test
    fun releasingTheLastFixingReturnsToTheCountBeforeIt() {
        open()
        more()
        more()
        fix(0, "300")
        assertEquals("Servings: ≈2", servings())
        tap(0)
        assertEquals("300", compose.onNodeWithTag(CardTags.FIX_FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        type("")
        compose.onNodeWithTag(CardTags.FIX_CANCEL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        assertEquals("Servings: ≈2", servings())
        tap(0)
        compose.onNodeWithTag(CardTags.FIX_RELEASE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        assertEquals("Servings: 6", servings())
        assertEquals(listOf("Chicken", "750 g"), line(0))
    }

    // A11Y-003, A11Y-005, L10N: the control's actions are large enough, say what they do, and read in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun theControlIsReachableAndReadsInRussian() {
        open()
        tap(0)
        type("0,3")
        val content = setOf(forFour.title, "0,3", "г", "кг", "т") + forFour.ingredients.mapNotNull { it.freeTextName } +
            (0..5).map { quantity(it) }
        // With the unit drop-down open, its choices too.
        compose.onNodeWithTag(FixTags.UNIT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.fixUnit(QuantityUnit.KILOGRAM)) }
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 11)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 11)
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)
        compose.onNodeWithTag(CardTags.fixUnit(QuantityUnit.KILOGRAM)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.fixUnit(QuantityUnit.KILOGRAM)) }
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 8)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 8)
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)
        confirm()
        assertEquals("Порций: ≈2", servings())
        assertEquals(listOf("Chicken", "300 г", "зафиксировано"), line(0))
    }

    /** The view the keyboard types into, once a text field holds the input: what it asks the keyboard for. */
    private fun keyboardAskedFor(): EditorInfo {
        fun View.all(): Sequence<View> = sequenceOf(this) + ((this as? ViewGroup)?.children?.flatMap { it.all() } ?: emptySequence())
        val editor = EditorInfo()
        compose.runOnUiThread {
            compose.activity.window.decorView.all().first { it.onCheckIsTextEditor() }.onCreateInputConnection(editor)
        }
        return editor
    }

    private fun keyboardShown(): Boolean =
        shadowOf(context.getSystemService(InputMethodManager::class.java)).isSoftInputVisible

    // T-139: one tap on the line opens fixing with the amount focused and the number keyboard up.
    @Test
    fun oneTapOpensTheAmountWithTheNumberKeyboard() {
        open()
        assertFalse(keyboardShown())
        tap(0)
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(CardTags.FIX_FIELD).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true
        }
        compose.waitUntil(PATIENCE_MS) { keyboardShown() }
        val input = keyboardAskedFor().inputType
        assertEquals(InputType.TYPE_CLASS_NUMBER, input and InputType.TYPE_MASK_CLASS)
        assertTrue(input and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0)
    }

    // T-139, SCALE-018: the unit is a standard drop-down of exactly the units the row's fixing allows.
    @Test
    fun theUnitIsChosenFromADropDownOfTheUnitsAllowed() {
        open()
        tap(0)
        compose.onNodeWithTag(FixTags.UNIT).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
        assertEquals(listOf("Unit", "g"), compose.textsOf(FixTags.UNIT))
        // No unit is laid out as a box of its own any more: the choices exist only while the drop-down is open.
        QuantityUnit.entries.forEach { compose.onNodeWithTag(CardTags.fixUnit(it)).assertDoesNotExist() }
        compose.onNodeWithTag(FixTags.UNIT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.fixUnit(QuantityUnit.GRAM)) }
        val offered = QuantityUnit.entries.filter { exists(CardTags.fixUnit(it)) }
        assertEquals(listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.TONNE), offered)
        compose.onNodeWithTag(CardTags.fixUnit(QuantityUnit.KILOGRAM)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.fixUnit(QuantityUnit.GRAM)) }
        assertEquals(listOf("Unit", "kg"), compose.textsOf(FixTags.UNIT))
        // A row whose amount reads in one unit only names it, with nothing to choose.
        compose.onNodeWithTag(CardTags.FIX_CANCEL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        tap(2)
        compose.onNodeWithTag(FixTags.UNIT).assertDoesNotExist()
    }

    // T-139: what the field does not hold is said in its supporting text, the field marked as in error.
    @Test
    fun aRefusedAmountIsTheFieldsError() {
        open()
        tap(0)
        compose.onNodeWithTag(CardTags.FIX_FIELD).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        type("2,5")
        compose.onNodeWithTag(CardTags.FIX_FIELD).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithTag(CardTags.FIX_PROBLEM, useUnmergedTree = true)
            .assert(hasAnyAncestor(hasTestTag(CardTags.FIX_FIELD)))
        type("250")
        compose.onNodeWithTag(CardTags.FIX_FIELD).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        compose.onNodeWithTag(CardTags.FIX_PROBLEM).assertDoesNotExist()
    }

    // T-139: Done on the keyboard applies as Recalculate does; on an amount refused it applies nothing.
    @Test
    fun doneOnTheKeyboardApplies() {
        open()
        tap(0)
        type("2,5")
        compose.onNodeWithTag(CardTags.FIX_FIELD).performImeAction()
        compose.waitForIdle()
        assertTrue(controlShown())
        assertEquals("Servings: 4", servings())
        type("300")
        compose.onNodeWithTag(CardTags.FIX_FIELD).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        assertEquals("Servings: ≈2", servings())
        assertEquals(listOf("Chicken", "300 g", "fixed"), line(0))
    }

    // T-139: cancel leaves the recipe as it was, whatever was typed or chosen, and keeps nothing of it.
    @Test
    fun cancelLeavesTheRecipeAsItWas() {
        open()
        val before = (0..5).map(::line)
        tap(0)
        type("300")
        pickUnit(QuantityUnit.KILOGRAM)
        compose.onNodeWithTag(CardTags.FIX_CANCEL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !controlShown() }
        assertEquals("Servings: 4", servings())
        assertEquals(before, (0..5).map(::line))
        tap(0)
        assertEquals(listOf("Amount you have", ""), compose.textsOf(CardTags.FIX_FIELD))
        assertEquals(listOf("Unit", "g"), compose.textsOf(FixTags.UNIT))
    }

    // T-159: the fixing control unfolds under its line and folds away as it closes, the lines below moving gradually.
    @Test
    fun theFixingControlUnfoldsAndFoldsAway() {
        open()
        fun nextLine() = compose.onNodeWithTag(CardTags.ingredient(1)).fetchSemanticsNode().positionInRoot.y
        compose.onNodeWithTag(CardTags.ingredient(0)).performScrollTo()
        compose.assertChangesGradually("the control opening", ::nextLine) { compose.onNodeWithTag(CardTags.ingredient(0)).performClick() }
        assertTrue(controlShown())
        compose.onNodeWithTag(CardTags.FIX_CANCEL).performScrollTo()
        compose.assertChangesGradually("the control closing", ::nextLine) { compose.onNodeWithTag(CardTags.FIX_CANCEL).performClick() }
        assertTrue(!controlShown())
    }
}
