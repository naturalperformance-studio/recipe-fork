package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.fork
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
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The card's summary line under the title (the view of the card): "N ingredients · M steps · timers ≈ X", muted, read
 * by the screen reader as one sentence, its counts in the plural forms of English and Russian; and, for a fork, the
 * provenance under it, opening the parent.
 */
@RunWith(AndroidJUnit4::class)
class CardSummaryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { emptyList() }
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private val opened = mutableListOf<RecipeId>()
    private var muted = Color.Unspecified

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private var shown by mutableStateOf<RecipeId?>(null)
    private var composed = false

    private fun show(recipe: Recipe) {
        runBlocking { recipes.save(recipe) }
        if (!composed) {
            composed = true
            compose.setContent {
                MaterialTheme {
                    muted = MaterialTheme.colorScheme.onSurfaceVariant
                    shown?.let {
                        key(it) {
                            CardScreen(it, recipes, environment, onBack = {}, onEdit = {}, onOpenParent = { parent -> opened += parent })
                        }
                    }
                }
            }
        }
        compose.runOnIdle { shown = recipe.id }
        compose.waitForIdle()
        compose.awaitCard()
    }

    private fun recipe(
        title: String,
        ingredients: Int,
        steps: Int,
        timers: List<Duration> = emptyList(),
        name: String = "Flour",
        step: String = "Stir",
    ) = Recipe.create(now, null).copy(
        title = title,
        ingredients = (0 until ingredients).map {
            Ingredient(it, null, freeTextName = "$name $it", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100))
        },
        // The timers on the first step, two of them on one step where given so.
        steps = (0 until steps).map { Step(it, "$step $it", timers = if (it == 0) timers.map(::Timer) else emptyList()) },
    )

    private fun summary(): String = compose.textsOf(CardTags.SUMMARY).single()

    private fun spoken(): String =
        compose.onNodeWithTag(CardTags.SUMMARY).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    // Under the title, before the ingredients: the counts and the timers' time together, rounded to 10 min from an hour
    // (10 + 25 + 35 = 70 min); a step that reads as nothing is not counted, as the card does not list it. Muted, and
    // one element the screen reader reads as one sentence.
    @Test
    fun theSummaryCountsIngredientsStepsAndTimersUnderTheTitle() {
        val base = recipe("Soup", ingredients = 9, steps = 4, timers = listOf(10.minutes, 25.minutes))
        val soup = base.copy(
            steps = base.steps.mapIndexed { i, s -> if (i == 2) s.copy(timers = listOf(Timer(35.minutes))) else s } + Step(4, "  "),
        )
        show(soup)
        assertEquals("9 ingredients · 4 steps · timers ≈ 1 h 10 min", summary())
        assertEquals("9 ingredients, 4 steps, timers about 1 h 10 min", spoken())
        assertEquals(1, compose.onAllNodes(hasTestTag(CardTags.SUMMARY), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(top(CardTags.TITLE) < top(CardTags.SUMMARY))
        assertTrue(top(CardTags.SUMMARY) < top(CardTags.INGREDIENTS))
        val colors = compose.textLayoutsWithin(CardTags.SUMMARY).map { it.layoutInput.style.color }
        assertEquals(listOf(muted), colors)
    }

    @Test
    @Config(qualifiers = "ru")
    fun theSummaryCountsIngredientsStepsAndTimersUnderTheTitleInRussian() {
        show(recipe("Суп", ingredients = 9, steps = 4, timers = listOf(30.minutes, 40.minutes), name = "Мука", step = "Помешать"))
        assertEquals("9 ингредиентов · 4 шага · таймеры ≈ 1 ч 10 мин", summary())
        assertEquals("9 ингредиентов, 4 шага, таймеры примерно 1 ч 10 мин", spoken())
    }

    // Under an hour the time is rounded to 5 min: 43 min reads as 45.
    @Test
    fun underAnHourTheTimersTimeIsRoundedToFiveMinutes() {
        show(recipe("Bread", ingredients = 2, steps = 2, timers = listOf(43.minutes)))
        assertEquals("2 ingredients · 2 steps · timers ≈ 45 min", summary())
    }

    // A recipe without timers: the timers part is left out.
    @Test
    fun aRecipeWithoutTimersSaysOnlyItsCounts() {
        show(recipe("Toast", ingredients = 1, steps = 1))
        assertEquals("1 ingredient · 1 step", summary())
        assertEquals("1 ingredient, 1 step", spoken())
        show(recipe("Salad", ingredients = 21, steps = 21))
        assertEquals("21 ingredients · 21 steps", summary())
    }

    @Test
    @Config(qualifiers = "ru")
    fun russianCountsTakeTheirPluralForms() {
        mapOf(
            1 to "1 ингредиент · 1 шаг",
            2 to "2 ингредиента · 2 шага",
            5 to "5 ингредиентов · 5 шагов",
            21 to "21 ингредиент · 21 шаг",
        ).forEach { (count, expected) ->
            show(recipe("Салат $count", ingredients = count, steps = count, name = "Мука", step = "Помешать"))
            compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SUMMARY) == listOf(expected) }
            assertEquals(expected, spoken().replace(",", " ·"))
        }
    }

    // A part the recipe does not hold is left out of the line, and a recipe of a title alone has no line (FLOW-019).
    @Test
    fun partsTheRecipeDoesNotHoldAreLeftOut() {
        show(recipe("Water", ingredients = 0, steps = 3, timers = listOf(5.minutes)))
        assertEquals("3 steps · timers ≈ 5 min", summary())
        show(recipe("Pantry", ingredients = 2, steps = 0))
        assertEquals("2 ingredients", summary())
        show(recipe("Idea", ingredients = 0, steps = 0))
        compose.onNodeWithTag(CardTags.SUMMARY).assertDoesNotExist()
    }

    // For a fork, where it came from stands under the summary — the card's one provenance — and opens the parent.
    @Test
    fun aForksProvenanceStandsUnderTheSummaryAndOpensTheParent() {
        val parent = recipe("Borscht", ingredients = 3, steps = 2)
        runBlocking { recipes.save(parent) }
        show(parent.fork(now, owner = null).copy(title = "Green borscht"))
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.PROVENANCE)).fetchSemanticsNodes().size == 1 }
        assertEquals("3 ingredients · 2 steps", summary())
        assertEquals(listOf("Forked from “Borscht”"), compose.textsOf(CardTags.PROVENANCE))
        assertTrue(top(CardTags.TITLE) < top(CardTags.SUMMARY))
        assertTrue(top(CardTags.SUMMARY) < top(CardTags.PROVENANCE))
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        assertEquals(listOf(parent.id), opened)
    }
}
