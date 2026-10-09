package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Scenario 3 of v0.27: on Cooking, once every ingredient is marked done, the list folds into one line, "All gathered ·
 * show"; a tap on it shows the lines again, still marked; clearing the marks shows them too. View never folds.
 */
@RunWith(AndroidJUnit4::class)
class CardIngredientsFoldTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val marks = DoneMarks.inMemory()
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    private val pilaf = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Rice", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(350)),
            Ingredient(2, null, freeTextName = "Pepper", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        ),
        steps = listOf(Step(0, "Fry the chicken."), Step(1, "Add the rice.")),
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        runBlocking { recipes.save(pilaf) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun open(tab: CardMode) {
        val environment = CardEnvironment(marks = marks, lastTab = HeldLastCardTab(tab), ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) { CardScreen(pilaf.id, recipes, environment, onBack = {}, onEdit = {}) }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.INGREDIENTS) }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun line(position: Int) = CardTags.ingredient(position)

    /** The list as the card shows it: its lines, or the one line it folded into. */
    private fun folded(): Boolean {
        val lines = (0..2).count { exists(line(it)) }
        val gathered = exists(IngredientTags.GATHERED)
        assertTrue("the list shows all its lines or none: $lines, folded: $gathered", lines == if (gathered) 0 else 3)
        return gathered
    }

    private fun doneLines(): List<Int> = (0..2).filter {
        compose.onNodeWithTag(line(it)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "Done"
    }

    /** What a sighted reader sees on the folded line. */
    private fun shownOnTheFoldedLine(): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(IngredientTags.GATHERED)), useUnmergedTree = true)
            .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(ShownText) }

    private fun clearMarks() {
        compose.openTheCardMenu()
        compose.onNodeWithTag(CardTags.CLEAR_MARKS).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.MENU) }
        compose.waitForIdle()
    }

    @Test
    fun everyLineMarkedFoldsTheListIntoOneLineThatShowsItAgain() {
        open(CardMode.COOKING)
        tap(line(0))
        tap(line(1))
        assertFalse("two lines of three marked: the list stands", folded())

        tap(line(2))
        assertTrue("every line marked: the list folds", folded())
        // Under its heading, one line that says so and offers the rest.
        val gathered = compose.onNodeWithTag(IngredientTags.GATHERED).fetchSemanticsNode()
        assertEquals(1, compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.INGREDIENTS)) and hasTestTag(IngredientTags.GATHERED)).fetchSemanticsNodes().size)
        assertEquals(listOf("All gathered · show"), shownOnTheFoldedLine())
        assertEquals(listOf("All gathered"), compose.textsOf(IngredientTags.GATHERED))
        assertEquals(Role.Button, gathered.config.getOrNull(SemanticsProperties.Role))
        assertEquals("Show the ingredients", AccessibilityChecks.clickActionLabel(gathered))
        compose.assertCardTarget(IngredientTags.GATHERED)

        // Shown again, the lines are still marked; unmarking one and marking it again folds the list again.
        tap(IngredientTags.GATHERED)
        assertFalse(folded())
        assertEquals(listOf(0, 1, 2), doneLines())
        tap(line(1))
        assertFalse(folded())
        tap(line(1))
        assertTrue(folded())
    }

    @Test
    fun clearingTheMarksShowsTheListAgain() {
        marks.hold(BookName.Pool, pilaf.id, pilaf.ingredients.map(DoneMark::of).toSet())
        open(CardMode.COOKING)
        assertTrue("a card opening with every line marked shows the list folded", folded())
        clearMarks()
        assertFalse(folded())
        assertEquals(emptyList<Int>(), doneLines())

        // Shown again, then cleared: the list stays shown, and folds again once every line is marked again.
        (0..2).forEach { tap(line(it)) }
        assertTrue(folded())
        tap(IngredientTags.GATHERED)
        clearMarks()
        assertFalse(folded())
        (0..2).forEach { tap(line(it)) }
        assertTrue(folded())
    }

    @Test
    fun viewNeverFolds() {
        marks.hold(BookName.Pool, pilaf.id, pilaf.ingredients.map(DoneMark::of).toSet())
        open(CardMode.VIEW)
        assertFalse(folded())
        assertEquals(listOf(0, 1, 2), doneLines())
        // Cooking, beside it, shows the same marks folded.
        compose.chooseCardTab(CardMode.COOKING)
        assertTrue(folded())
        compose.chooseCardTab(CardMode.VIEW)
        assertFalse(folded())
    }

    @Test
    @Config(qualifiers = "ru")
    fun theFoldedLineReadsInRussian() {
        marks.hold(BookName.Pool, pilaf.id, pilaf.ingredients.map(DoneMark::of).toSet())
        open(CardMode.COOKING)
        assertTrue(folded())
        assertEquals(listOf("Всё собрано"), compose.textsOf(IngredientTags.GATHERED))
        assertEquals(listOf("Всё собрано · показать"), shownOnTheFoldedLine())
        assertEquals("Показать ингредиенты", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(IngredientTags.GATHERED).fetchSemanticsNode()))
    }
}
