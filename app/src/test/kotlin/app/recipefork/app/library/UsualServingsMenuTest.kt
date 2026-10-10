package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.CardTarget
import app.recipefork.app.card.IngredientTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.data.PreferencesUsualServings
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The settings offer the serving count the reader usually cooks for (FLOW-017): its values are exactly those a
 * requested count may take (SCALE-010, SCALE-011), "Not set" among the choices; the choice is kept on the device; and
 * every recipe opened from the book opens at it (scenario 1 of the increment).
 *
 * v0.30, T-221: the dialog walks the count with the card's own control — tonal connected − | + — offers "Not set" as a
 * choice inside it, and its buttons are Cancel and Set; it settles at 360 dp and at 320 dp at twice the font scale.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UsualServingsMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val usual = PreferencesUsualServings(context)
    private val now = Instant.parse("2026-10-03T12:00:00Z")

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

    private fun show() {
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment(usual) { device.database.dictionaryDao().ingredients() }
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = editor, card = card) } }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Opens the settings, unless they are open already. */
    private fun openMenu() {
        if (exists(SettingsTags.SCREEN)) return
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.USUAL) }
    }

    private fun leaveSettings() {
        compose.onNodeWithTag(SettingsTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(SettingsTags.SCREEN) && exists(LibraryTags.SETTINGS) }
    }

    private fun openChoice() {
        openMenu()
        compose.onNodeWithTag(SettingsTags.USUAL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.USUAL_DIALOG) }
    }

    private fun choose(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(SettingsTags.USUAL_DIALOG) }
    }

    /** What the settings' item shows of the setting: the count held, or that none is. */
    private fun held() = compose.textsOf(SettingsTags.USUAL).last()
    private fun count() = compose.textsOf(SettingsTags.USUAL_COUNT).single()
    private fun fewer() = compose.onNodeWithTag(SettingsTags.USUAL_FEWER).performClick()
    private fun more() = compose.onNodeWithTag(SettingsTags.USUAL_MORE).performClick()
    private fun selected(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Selected]

    // FLOW-017 (4): the settings offer it, with what it holds — "Not set" until it is set, the count after. The dialog
    // opens on the setting in force: "Not set" chosen; choosing the count and Set sets it.
    @Test
    fun theMenuOffersTheUsualCountAndShowsWhatItHolds() {
        show()
        openMenu()
        assertEquals(listOf("I usually cook for…", "Not set"), compose.textsOf(SettingsTags.USUAL))
        compose.onNodeWithTag(SettingsTags.USUAL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.USUAL_DIALOG) }
        assertEquals("Servings: 2", count())
        assertTrue(selected(SettingsTags.USUAL_NOT_SET))
        assertFalse(selected(SettingsTags.USUAL_COUNT_CHOICE))
        compose.onNodeWithTag(SettingsTags.USUAL_COUNT_CHOICE).performClick()
        assertTrue(selected(SettingsTags.USUAL_COUNT_CHOICE))
        choose(SettingsTags.USUAL_SET)
        assertEquals(Rational.of(2), usual.count.value)
        openMenu()
        assertEquals("2", held())
    }

    // FLOW-017 (4), SCALE-010, SCALE-011: the values are exactly those a requested count may take — the fractions
    // below one, the floor at ¼, every whole number with no ceiling — and "Not set", chosen and set, clears the setting.
    // A step of the count chooses the count.
    @Test
    fun theChoiceWalksTheRequestedCountsAndNotSetClearsIt() {
        show()
        openChoice()
        val walk = mutableListOf(count())
        assertTrue(selected(SettingsTags.USUAL_NOT_SET))
        repeat(6) {
            fewer()
            walk += count()
        }
        assertEquals(
            listOf("Servings: 2", "Servings: 1", "Servings: ¾", "Servings: ⅔", "Servings: ½", "Servings: ⅓", "Servings: ¼"),
            walk,
        )
        assertTrue("a step chooses the count", selected(SettingsTags.USUAL_COUNT_CHOICE))
        compose.onNodeWithTag(SettingsTags.USUAL_FEWER).assertIsNotEnabled()
        fewer()
        assertEquals("Servings: ¼", count())
        more()
        assertEquals("Servings: ⅓", count())
        compose.onNodeWithTag(SettingsTags.USUAL_FEWER).assertIsEnabled()
        choose(SettingsTags.USUAL_SET)
        assertEquals(Rational.of(1, 3), usual.count.value)
        openMenu()
        assertEquals("⅓", held())
        compose.onNodeWithTag(SettingsTags.USUAL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.USUAL_DIALOG) }
        // The choice starts at the count held, chosen, and walks up without a ceiling.
        assertEquals("Servings: ⅓", count())
        assertTrue(selected(SettingsTags.USUAL_COUNT_CHOICE))
        repeat(15) { more() }
        assertEquals("Servings: 12", count())
        choose(SettingsTags.USUAL_SET)
        assertEquals(Rational.of(12), usual.count.value)
        // Cancel changes nothing, whatever was chosen in the dialog.
        openChoice()
        compose.onNodeWithTag(SettingsTags.USUAL_NOT_SET).performClick()
        choose(SettingsTags.USUAL_CANCEL)
        assertEquals(Rational.of(12), usual.count.value)
        openChoice()
        compose.onNodeWithTag(SettingsTags.USUAL_NOT_SET).performClick()
        assertTrue(selected(SettingsTags.USUAL_NOT_SET))
        assertTrue("the dialog stays open on a choice", exists(SettingsTags.USUAL_DIALOG))
        choose(SettingsTags.USUAL_SET)
        assertNull(usual.count.value)
        openMenu()
        assertEquals("Not set", held())
    }

    // The dialog's buttons: Cancel, then Set at the end; "Not set" is a choice inside it, not in Cancel's place.
    @Test
    fun theDialogsButtonsAreCancelAndSetAndNotSetIsAChoice() {
        show()
        openChoice()
        assertEquals(listOf("Cancel"), compose.textsOf(SettingsTags.USUAL_CANCEL))
        assertEquals(listOf("Set"), compose.textsOf(SettingsTags.USUAL_SET))
        assertEquals(listOf("Not set"), compose.textsOf(SettingsTags.USUAL_NOT_SET))
        assertEquals(listOf("A usual count"), compose.textsOf(SettingsTags.USUAL_COUNT_CHOICE))
        val cancel = compose.onNodeWithTag(SettingsTags.USUAL_CANCEL).fetchSemanticsNode().boundsInRoot
        val set = compose.onNodeWithTag(SettingsTags.USUAL_SET).fetchSemanticsNode().boundsInRoot
        assertTrue("Set ($set) stands after Cancel ($cancel)", set.left >= cancel.right)
        listOf(SettingsTags.USUAL_NOT_SET, SettingsTags.USUAL_COUNT_CHOICE).forEach {
            assertEquals(Role.RadioButton, compose.onNodeWithTag(it).fetchSemanticsNode().config[SemanticsProperties.Role])
        }
    }

    // The count is walked with the card's own control: the − | + halves are tonal icon buttons the card's target size,
    // side by side as one connected group.
    @Test
    fun theCountIsWalkedWithTheCardsControl() {
        show()
        openChoice()
        val fewer = compose.onNodeWithTag(SettingsTags.USUAL_FEWER).fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithTag(SettingsTags.USUAL_MORE).fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        listOf(fewer, more).forEach {
            assertEquals(CardTarget.value, it.width / density, 0.5f)
            assertEquals(CardTarget.value, it.height / density, 0.5f)
        }
        assertTrue("− and + are side by side, connected", more.left >= fewer.right && (more.left - fewer.right) / density <= 4f)
        assertEquals(fewer.top, more.top, 0.5f)
    }

    // T-215: the dialog settles — nothing in it keeps laying itself out — at 360 dp, and at 320 dp at twice the font
    // scale, in English and Russian; its targets stay whole and labelled.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun theDialogSettlesAt360Dp() = settles()

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun theDialogSettlesAt320DpAtTwiceTheFontScale() = settles()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianTheDialogSettlesAt320DpAtTwiceTheFontScale() = settles()

    private fun settles() {
        show()
        openChoice()
        compose.waitForIdle()
        more()
        compose.waitForIdle()
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 6)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 6)
        val dialog = compose.onNodeWithTag(SettingsTags.USUAL_DIALOG).fetchSemanticsNode().boundsInRoot
        listOf(SettingsTags.USUAL_NOT_SET, SettingsTags.USUAL_COUNT_CHOICE, SettingsTags.USUAL_COUNT, SettingsTags.USUAL_FEWER,
            SettingsTags.USUAL_MORE, SettingsTags.USUAL_CANCEL, SettingsTags.USUAL_SET).forEach { tag ->
            val part = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag ($part) lies within the dialog ($dialog)", part.left >= dialog.left - 0.5f && part.right <= dialog.right + 0.5f)
        }
        choose(SettingsTags.USUAL_SET)
        assertEquals(Rational.of(3), usual.count.value)
    }

    // FLOW-017 (4): the choice made in the book is the one the next start reads.
    @Test
    fun theChoiceIsKeptOnTheDevice() {
        show()
        openChoice()
        more()
        choose(SettingsTags.USUAL_SET)
        assertEquals(Rational.of(3), PreferencesUsualServings(context).count.value)
    }

    /** A recipe written in the book for 4. */
    private fun forFour(): Recipe = Recipe.create(now, null).copy(
        title = "Omelette",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
        ),
    ).also { runBlocking { recipes.save(it) } }

    private fun openCard(recipe: Recipe) {
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        val entry = LibraryTags.entry(recipe.id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(entry))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(entry).performClick()
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.ingredient(0)) }
    }

    private fun recalculated() = exists(IngredientTags.RECALCULATED)

    // Scenario 1, FLOW-017 (1, 3, 5): "I usually cook for 2" → a recipe for 4 opens at 2, marked as recalculated;
    // set back to 4 on the card, nothing is marked, and the setting is still 2.
    @Test
    fun aRecipeForFourOpensAtTheUsualTwoAndBackAtFourShowsNoMarks() {
        val recipe = forFour()
        show()
        openChoice()
        compose.onNodeWithTag(SettingsTags.USUAL_COUNT_CHOICE).performClick()
        choose(SettingsTags.USUAL_SET)
        leaveSettings()
        openCard(recipe)
        assertEquals(listOf("Servings: 2"), compose.textsOf(CardTags.SERVINGS))
        compose.waitUntil(PATIENCE_MS) { recalculated() }
        assertEquals(listOf("Chicken", "250 g"), compose.textsOf(CardTags.ingredient(0)))
        repeat(2) { compose.onNodeWithTag(CardTags.SERVINGS_MORE).performClick() }
        assertEquals(listOf("Servings: 4"), compose.textsOf(CardTags.SERVINGS))
        compose.waitUntil(PATIENCE_MS) { !recalculated() }
        assertEquals(listOf("Chicken", "500 g"), compose.textsOf(CardTags.ingredient(0)))
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SETTINGS) }
        assertEquals(Rational.of(2), usual.count.value)
        openMenu()
        assertEquals("2", held())
    }

    // A11Y-003, A11Y-005: the menu, its item and the choice's actions are large enough and say what they do.
    @Test
    fun theMenuAndTheChoiceAreLargeEnoughAndSayWhatTheyDo() {
        show()
        val menu = compose.onNodeWithTag(LibraryTags.SETTINGS).fetchSemanticsNode()
        assertEquals("Open the settings", AccessibilityChecks.clickActionLabel(menu))
        openChoice()
        listOf(
            SettingsTags.USUAL_FEWER to ("Fewer servings" to "Lower the usual serving count"),
            SettingsTags.USUAL_MORE to ("More servings" to "Raise the usual serving count"),
        ).forEach { (tag, words) ->
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals(words.second, AccessibilityChecks.clickActionLabel(node))
            assertEquals(words.first, AccessibilityChecks.announcedLabel(node))
        }
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 4)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 4)
    }

    // L10N: the menu and the choice read in Russian, every word from the resources.
    @Test
    @Config(qualifiers = "ru")
    fun theMenuAndTheChoiceReadInRussian() {
        show()
        openMenu()
        assertEquals(listOf("Обычно готовлю на…", "Не задано"), compose.textsOf(SettingsTags.USUAL))
        LocalizationChecks.assertScreenTextFromResources(compose, context, emptySet())
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, emptySet())
        compose.onNodeWithTag(SettingsTags.USUAL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.USUAL_DIALOG) }
        assertEquals("Порций: 2", count())
        assertEquals(listOf("Задать"), compose.textsOf(SettingsTags.USUAL_SET))
        assertEquals(listOf("Отмена"), compose.textsOf(SettingsTags.USUAL_CANCEL))
        assertEquals(listOf("Не задано"), compose.textsOf(SettingsTags.USUAL_NOT_SET))
        assertEquals(listOf("Обычное число порций"), compose.textsOf(SettingsTags.USUAL_COUNT_CHOICE))
        LocalizationChecks.assertScreenTextFromResources(compose, context, emptySet())
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, emptySet())
        compose.onNodeWithTag(SettingsTags.USUAL_COUNT_CHOICE).performClick()
        choose(SettingsTags.USUAL_SET)
        openMenu()
        assertEquals("2", held())
    }
}
