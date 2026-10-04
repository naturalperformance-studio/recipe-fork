package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The settings offer the serving count the reader usually cooks for (FLOW-017): its values are exactly those a
 * requested count may take (SCALE-010, SCALE-011), "not set" among the choices; the choice is kept on the device; and
 * every recipe opened from the book opens at it (scenario 1 of the increment).
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
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.USUAL_DIALOG) }
    }

    private fun choose(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.USUAL_DIALOG) }
    }

    /** What the settings' item shows of the setting: the count held, or that none is. */
    private fun held() = compose.textsOf(SettingsTags.USUAL).last()
    private fun count() = compose.textsOf(LibraryTags.USUAL_COUNT).single()
    private fun fewer() = compose.onNodeWithTag(LibraryTags.USUAL_FEWER).performClick()
    private fun more() = compose.onNodeWithTag(LibraryTags.USUAL_MORE).performClick()

    // FLOW-017 (4): the settings offer it, with what it holds — "not set" until it is set, the count after.
    @Test
    fun theMenuOffersTheUsualCountAndShowsWhatItHolds() {
        show()
        openMenu()
        assertEquals(listOf("I usually cook for…", "not set"), compose.textsOf(SettingsTags.USUAL))
        compose.onNodeWithTag(SettingsTags.USUAL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.USUAL_DIALOG) }
        assertEquals("Servings: 2", count())
        choose(LibraryTags.USUAL_SET)
        assertEquals(Rational.of(2), usual.count.value)
        openMenu()
        assertEquals("2", held())
    }

    // FLOW-017 (4), SCALE-010, SCALE-011: the values are exactly those a requested count may take — the fractions
    // below one, the floor at ¼, every whole number with no ceiling — and "not set" clears the setting.
    @Test
    fun theChoiceWalksTheRequestedCountsAndNotSetClearsIt() {
        show()
        openChoice()
        val walk = mutableListOf(count())
        repeat(6) {
            fewer()
            walk += count()
        }
        assertEquals(
            listOf("Servings: 2", "Servings: 1", "Servings: ¾", "Servings: ⅔", "Servings: ½", "Servings: ⅓", "Servings: ¼"),
            walk,
        )
        compose.onNodeWithTag(LibraryTags.USUAL_FEWER).assertIsNotEnabled()
        fewer()
        assertEquals("Servings: ¼", count())
        more()
        assertEquals("Servings: ⅓", count())
        compose.onNodeWithTag(LibraryTags.USUAL_FEWER).assertIsEnabled()
        choose(LibraryTags.USUAL_SET)
        assertEquals(Rational.of(1, 3), usual.count.value)
        openMenu()
        assertEquals("⅓", held())
        compose.onNodeWithTag(SettingsTags.USUAL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.USUAL_DIALOG) }
        // The choice starts at the count held, and walks up without a ceiling.
        assertEquals("Servings: ⅓", count())
        repeat(15) { more() }
        assertEquals("Servings: 12", count())
        choose(LibraryTags.USUAL_SET)
        assertEquals(Rational.of(12), usual.count.value)
        openChoice()
        choose(LibraryTags.USUAL_CLEAR)
        assertNull(usual.count.value)
        openMenu()
        assertEquals("not set", held())
    }

    // FLOW-017 (4): the choice made in the book is the one the next start reads.
    @Test
    fun theChoiceIsKeptOnTheDevice() {
        show()
        openChoice()
        more()
        choose(LibraryTags.USUAL_SET)
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
        choose(LibraryTags.USUAL_SET)
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
            LibraryTags.USUAL_FEWER to ("Fewer servings" to "Lower the usual serving count"),
            LibraryTags.USUAL_MORE to ("More servings" to "Raise the usual serving count"),
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
        assertEquals(listOf("Обычно готовлю на…", "не задано"), compose.textsOf(SettingsTags.USUAL))
        LocalizationChecks.assertScreenTextFromResources(compose, context, emptySet())
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, emptySet())
        compose.onNodeWithTag(SettingsTags.USUAL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.USUAL_DIALOG) }
        assertEquals("Порций: 2", count())
        assertEquals(listOf("Задать"), compose.textsOf(LibraryTags.USUAL_SET))
        assertEquals(listOf("Не задавать"), compose.textsOf(LibraryTags.USUAL_CLEAR))
        LocalizationChecks.assertScreenTextFromResources(compose, context, emptySet())
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, emptySet())
        choose(LibraryTags.USUAL_SET)
        openMenu()
        assertEquals("2", held())
    }
}
