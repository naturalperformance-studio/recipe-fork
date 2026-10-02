package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Recalculated quantities on the card say so, and which way they were rounded (SCALE-004, SCALE-005), and a
 * warning says when the proportions no longer hold (SCALE-009) — in words the screen reader reads (A11Y-005,
 * A11Y-008).
 */
@RunWith(AndroidJUnit4::class)
class CardRecalculatedTest {
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

    /** For 4: chicken, indivisible eggs and salt that scale, and flour left unfinished. */
    private val forFour: Recipe = Recipe.create(now, null).copy(
        title = "Omelette",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
            Ingredient(2, null, freeTextName = "Salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.ONE),
            Ingredient(3, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = null),
        ),
    )

    private fun open() {
        runBlocking { recipes.save(forFour) }
        compose.setContent { MaterialTheme { CardScreen(forFour.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun more(times: Int = 1) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_MORE).performClick() }
    private fun fewer(times: Int = 1) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performClick() }

    /** Every line as the screen reader reads it: the texts of its one merged node, from the name on. */
    private fun lines() = (0..3).map { compose.textsOf(CardTags.ingredient(it)) }

    private fun warningShown() = compose.onAllNodes(hasTestTag(CardTags.PROPORTIONS)).fetchSemanticsNodes().isNotEmpty()

    private fun awaitWarning(shown: Boolean) = compose.waitUntil(PATIENCE_MS) { warningShown() == shown }

    // SCALE-004 (1), SCALE-001: at the authored count nothing is marked; at any other count every quantity is.
    @Test
    fun everyQuantityIsMarkedAtAnyCountButTheAuthoredOne() {
        open()
        assertEquals(
            listOf(listOf("Chicken", "500 g"), listOf("Eggs", "3 pc"), listOf("Salt", "1 tsp"), listOf("Flour", "g")),
            lines(),
        )
        more(2)
        assertEquals("Servings: 6", servings())
        // 750 g and 1½ tsp exact; 4.5 eggs shown as 5; the unfinished row is not scaled and so not marked.
        assertEquals(
            listOf(
                listOf("Chicken", "750 g", "recalculated"),
                listOf("Eggs", "5 pc", "recalculated, exact value was lower"),
                listOf("Salt", "1½ tsp", "recalculated"),
                listOf("Flour", "g"),
            ),
            lines(),
        )
        fewer(3)
        assertEquals("Servings: 3", servings())
        // 2.25 eggs shown as 2.
        assertEquals(listOf("Eggs", "2 pc", "recalculated, exact value was higher"), lines()[1])
        more()
        assertEquals("Servings: 4", servings())
        assertEquals(listOf(listOf("Chicken", "500 g"), listOf("Eggs", "3 pc"), listOf("Salt", "1 tsp"), listOf("Flour", "g")), lines())
    }

    // SCALE-005, SCALE-004 (3): at ¼ serving the eggs and the salt are held at their floors and read as rounded up.
    @Test
    fun aQuantityHeldAtItsFloorReadsAsRoundedUp() {
        open()
        fewer(8)
        assertEquals("Servings: ¼", servings())
        assertEquals(listOf("Eggs", "1 pc", "recalculated, exact value was lower"), lines()[1])
        assertEquals(listOf("Salt", "¼ tsp", "recalculated, exact value was lower"), lines()[2])
    }

    // SCALE-009 (1–4): one line at its floor, no warning; two, the warning; back at the authored count, none.
    @Test
    fun aWarningSaysTheProportionsNoLongerHoldOnceTwoLinesAreAtTheirFloors() {
        open()
        fewer(3)
        assertEquals("Servings: 1", servings())
        // Only the eggs (0.75) are held; the salt is exactly ¼ tsp.
        assertEquals(listOf("Eggs", "1 pc", "recalculated, exact value was lower"), lines()[1])
        assertEquals(listOf("Salt", "¼ tsp", "recalculated"), lines()[2])
        awaitWarning(false)
        fewer()
        assertEquals("Servings: ¾", servings())
        awaitWarning(true)
        val warning = compose.onNodeWithTag(CardTags.PROPORTIONS).fetchSemanticsNode()
        assertEquals(
            listOf("Some ingredients are already at their smallest amount, so these quantities no longer keep the recipe’s proportions."),
            warning.config[SemanticsProperties.Text].map { it.text },
        )
        // Announced when it appears: nobody asked for it by name.
        assertEquals(LiveRegionMode.Polite, warning.config[SemanticsProperties.LiveRegion])
        // Each held line still carries its own rounded-up marking.
        assertEquals("recalculated, exact value was lower", lines()[1].last())
        assertEquals("recalculated, exact value was lower", lines()[2].last())
        more()
        awaitWarning(false)
        fewer()
        awaitWarning(true)
        more(4)
        assertEquals("Servings: 4", servings())
        awaitWarning(false)
    }

    // A11Y-005, A11Y-008, L10N-001: the marking and the warning are words of the language in force, read as part of
    // the line or as text of their own, never a colour.
    @Test
    fun theMarkingAndTheWarningAreWordsTheScreenReaderReads() = readAloud(
        lower = "recalculated, exact value was lower",
    )

    @Test
    @Config(qualifiers = "ru")
    fun theMarkingAndTheWarningAreWordsTheScreenReaderReadsInRussian() = readAloud(
        lower = "пересчитано, точное значение было меньше",
    )

    private fun readAloud(lower: String) {
        open()
        fewer(4)
        awaitWarning(true)
        assertEquals(lower, lines()[1].last())
        val content = setOf(forFour.title) + forFour.ingredients.mapNotNull { it.freeTextName } + lines().map { it[1] }
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)
    }
}
