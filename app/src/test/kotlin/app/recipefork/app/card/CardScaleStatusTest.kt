package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The status line above the card's ingredients (SCALE-004 (1), A11Y-008): always present — "as the author wrote it"
 * at the authored scale, the recalculation legend otherwise, each read in words — and as high as the taller of the two
 * at the width and font scale in force, so changing servings to and from the authored count never moves the first
 * ingredient.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardScaleStatusTest {
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

    /** For 4: chicken and indivisible eggs, so 6 servings round the eggs. */
    private val forFour: Recipe = Recipe.create(now, null).copy(
        title = "Omelette",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
        ),
    )

    private fun open(fontScale: Float = 1f) {
        runBlocking { recipes.save(forFour) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme { CardScreen(forFour.id, recipes, environment, onBack = {}, onEdit = {}) }
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun more() = compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
    private fun fewer() = compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performScrollTo().performClick()

    /** What the screen reader reads of the status line, from its one text node. */
    private fun spoken(): String {
        val tag = if (exists(IngredientTags.RECALCULATED)) IngredientTags.RECALCULATED else IngredientTags.AUTHORED
        return compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    }

    /** The first ingredient's top, measured from the top of its section, so scrolling does not count. */
    private fun firstIngredientOffset(): Float {
        val section = compose.onNodeWithTag(CardTags.INGREDIENTS).fetchSemanticsNode().boundsInRoot
        return compose.onNodeWithTag(CardTags.ingredient(0)).fetchSemanticsNode().boundsInRoot.top - section.top
    }

    private fun statusHeight() = compose.onNodeWithTag(IngredientTags.STATUS).fetchSemanticsNode().boundsInRoot.height

    private fun shownHeight(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height

    private fun readsBothStates(authored: String, recalculated: String) {
        open()
        assertTrue(exists(IngredientTags.STATUS))
        assertTrue(exists(IngredientTags.AUTHORED))
        assertTrue(!exists(IngredientTags.RECALCULATED))
        assertEquals(authored, spoken())
        more()
        compose.waitUntil(PATIENCE_MS) { exists(IngredientTags.RECALCULATED) }
        assertTrue(exists(IngredientTags.STATUS))
        assertTrue(!exists(IngredientTags.AUTHORED))
        assertEquals(recalculated, spoken())
        fewer()
        compose.waitUntil(PATIENCE_MS) { exists(IngredientTags.AUTHORED) }
        assertTrue(!exists(IngredientTags.RECALCULATED))
        assertEquals(authored, spoken())
    }

    @Test
    fun theStatusLineIsAlwaysThereAndReadInWords() = readsBothStates(
        authored = "As the author wrote it",
        recalculated = "Quantities are recalculated. An up arrow marks a quantity rounded up, a down arrow one rounded down.",
    )

    @Test
    @Config(qualifiers = "ru")
    fun theStatusLineIsAlwaysThereAndReadInWordsInRussian() = readsBothStates(
        authored = "Как написал автор",
        recalculated = "Количества пересчитаны. Стрелка вверх — округлено вверх, стрелка вниз — округлено вниз.",
    )

    private fun keepsTheListStill(fontScale: Float) {
        open(fontScale)
        val authoredOffset = firstIngredientOffset()
        val authoredHeight = statusHeight()
        val authoredText = shownHeight(IngredientTags.AUTHORED)
        more()
        assertTrue(servings().endsWith(": 5"))
        compose.waitUntil(PATIENCE_MS) { exists(IngredientTags.RECALCULATED) }
        compose.waitForIdle()
        val recalculatedText = shownHeight(IngredientTags.RECALCULATED)
        assertEquals(authoredHeight, statusHeight(), 0.5f)
        // The line is the taller of its two texts — no blank gap beyond it.
        assertEquals(maxOf(authoredText, recalculatedText), statusHeight(), 0.5f)
        assertEquals(authoredOffset, firstIngredientOffset(), 0.5f)
        fewer()
        compose.waitUntil(PATIENCE_MS) { exists(IngredientTags.AUTHORED) }
        compose.waitForIdle()
        assertEquals(authoredOffset, firstIngredientOffset(), 0.5f)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun changingServingsDoesNotMoveTheFirstIngredient() = keepsTheListStill(1f)

    // At 2× font on a narrow screen the legend wraps onto more lines than the authored text: the line still holds.
    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun changingServingsDoesNotMoveTheFirstIngredientAtLargeText() {
        keepsTheListStill(2f)
        val authored = shownHeight(IngredientTags.AUTHORED)
        assertTrue("the authored text is shorter than the line (${authored} < ${statusHeight()})", authored < statusHeight())
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun changingServingsDoesNotMoveTheFirstIngredientAtLargeTextInRussian() = keepsTheListStill(2f)
}
