package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * v0.30, T-220: View's floating Fork action never covers the serving count's control — its count, − and + — on the first
 * screen, wherever the recipe's description puts the count, on a 360 × 720 dp phone at the usual text size and on a
 * 320 dp one at twice the font scale, in English and Russian; nor anywhere while the reader scrolls through. Where the
 * count stands under it, Fork stands aside, and comes back once the count is scrolled clear of it.
 *
 * v0.30, T-221: so on a short recipe too — one ingredient, one step — whose count the description puts in Fork's band:
 * the room View leaves at its end for Fork (its spacing, its height, a gap) lets the count be scrolled clear, and Fork
 * comes back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardForkClearOfServingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private var shown by mutableStateOf<RecipeId?>(null)

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private val sentence = "Brown the chicken well, then let the rice soak up every drop of the stock."

    /** A recipe whose description is [sentences] sentences long: the more, the lower the serving count stands. */
    private fun recipe(sentences: Int) = Recipe.create(now, null).copy(
        title = "Pilaf",
        description = List(sentences) { sentence }.joinToString(" "),
        servings = 4,
        ingredients = (0 until 8).map {
            Ingredient(it, null, freeTextName = "Ingredient $it", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100))
        },
        steps = (0 until 6).map { Step(it, "Stir the pot slowly with a wooden spoon so that nothing catches.") },
    )

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** The part of the element tagged [tag] in sight, in the root; empty when it is scrolled out of sight. */
    private fun shownBounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private val servingsParts = listOf(CardTags.SERVINGS, CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE)

    /** While Fork shows, none of the serving count's parts in sight lies under it. */
    private fun assertForkClearOfTheCount(where: String) {
        if (!exists(CardTags.FORK)) return
        val fork = shownBounds(CardTags.FORK)
        servingsParts.forEach { part ->
            val bounds = shownBounds(part)
            if (bounds.height > 0f && bounds.width > 0f) {
                assertFalse("$where: Fork ($fork) covers $part ($bounds)", fork.overlaps(bounds))
            }
        }
    }

    private fun sweep() {
        val opened = (0..SENTENCES).map { recipe(it).also { r -> runBlocking { recipes.save(r) } } }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(CardMode.VIEW), ingredients = IngredientEntries { emptyList() })
        compose.setContent { MaterialTheme { shown?.let { CardScreen(it, recipes, environment, onBack = {}, onEdit = {}) } } }
        var asideOnce = false
        opened.forEachIndexed { sentences, recipe ->
            compose.runOnIdle { shown = null }
            compose.waitForIdle()
            compose.runOnIdle { shown = recipe.id }
            compose.awaitCard()
            compose.waitUntil(PATIENCE_MS) { exists(CardTags.SERVINGS) }
            compose.waitForIdle()
            // The first screen, as the card opens.
            assertForkClearOfTheCount("$sentences sentences, as the card opens")
            if (!exists(CardTags.FORK)) asideOnce = true
            // Scrolled through a little at a time: Fork, while it shows, covers no part of the count.
            repeat(12) { step ->
                compose.onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, STEP_PX) }
                compose.waitForIdle()
                assertForkClearOfTheCount("$sentences sentences, scrolled ${step + 1} steps")
                if (!exists(CardTags.FORK)) asideOnce = true
            }
        }
        assertTrue("the count came under Fork somewhere, and Fork stood aside", asideOnce)
        // Where it stood aside, Fork comes back once the count is scrolled clear.
        opened.forEach { recipe ->
            compose.runOnIdle { shown = null }
            compose.waitForIdle()
            compose.runOnIdle { shown = recipe.id }
            compose.awaitCard()
            compose.waitUntil(PATIENCE_MS) { exists(CardTags.SERVINGS) }
            compose.scrollUntilForkShows()
            assertForkClearOfTheCount("${recipe.description.length} characters, Fork back")
        }
    }

    /** A recipe of one ingredient and one step, whose description is [sentences] sentences long. */
    private fun short(sentences: Int) = Recipe.create(now, null).copy(
        title = "Tea",
        description = List(sentences) { sentence }.joinToString(" "),
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Tea leaves", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(5)),
        ),
        steps = listOf(Step(0, "Pour on boiling water.")),
    )

    private fun shortRecipeSweep() {
        val opened = (0..SENTENCES).map { short(it).also { r -> runBlocking { recipes.save(r) } } }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(CardMode.VIEW), ingredients = IngredientEntries { emptyList() })
        compose.setContent { MaterialTheme { shown?.let { CardScreen(it, recipes, environment, onBack = {}, onEdit = {}) } } }
        var asideOnce = false
        opened.forEachIndexed { sentences, recipe ->
            compose.runOnIdle { shown = null }
            compose.waitForIdle()
            compose.runOnIdle { shown = recipe.id }
            compose.awaitCard()
            compose.waitUntil(PATIENCE_MS) { exists(CardTags.SERVINGS) }
            compose.waitForIdle()
            assertForkClearOfTheCount("a short recipe, $sentences sentences, as the card opens")
            if (!exists(CardTags.FORK)) {
                // The count is in Fork's band: scrolled clear of it, Fork comes back, covering nothing.
                asideOnce = true
                compose.scrollUntilForkShows()
                assertForkClearOfTheCount("a short recipe, $sentences sentences, Fork back")
            }
        }
        assertTrue("the short recipe's count came under Fork somewhere, and Fork stood aside", asideOnce)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h720dp")
    fun onAShortRecipeForkComesBackOnceTheCountIsScrolledClear() = shortRecipeSweep()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun onAShortRecipeForkComesBackAtTwiceTheFontScaleInRussian() = shortRecipeSweep()

    @Test
    @Config(qualifiers = "en-w360dp-h720dp")
    fun forkNeverCoversTheCountOnA360By720Phone() = sweep()

    @Test
    @Config(qualifiers = "ru-w360dp-h720dp")
    fun inRussianForkNeverCoversTheCountOnA360By720Phone() = sweep()

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun forkNeverCoversTheCountOnA320DpPhoneAtTwiceTheFontScale() = sweep()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianForkNeverCoversTheCountOnA320DpPhoneAtTwiceTheFontScale() = sweep()

    private companion object {
        /** About 20 dp at the test's density: fine enough to catch the count under Fork. */
        const val STEP_PX = 20f

        /** The longest description, in sentences: enough to put the count below the first screen at either size. */
        const val SENTENCES = 24
    }
}
