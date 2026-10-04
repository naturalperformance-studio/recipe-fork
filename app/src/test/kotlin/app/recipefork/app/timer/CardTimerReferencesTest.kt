package app.recipefork.app.timer

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.app.card.CardTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/**
 * A timer on a step whose derived name holds an ingredient reference (FLOW-016 (8)): it starts named with the
 * reference as the card shows it at that moment, announces under that name, and keeps it while held — a change of
 * count, or leaving the card, neither renames nor drops it.
 */
@RunWith(AndroidJUnit4::class)
class CardTimerReferencesTest : CardTimersKit() {
    private val bread = Recipe.create(now, owner = null).copy(
        title = "Bread",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
        ),
        steps = listOf(Step(0, "Knead  flour", listOf(IngredientReference(0, 6)), Timer(5.minutes))),
    )
    private val breadKey = TimerKey(BookName.Pool, bread.id, 0)

    private fun more(times: Int) = repeat(times) {
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun fewer(times: Int) = repeat(times) {
        compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** The name the timer's control reads by. */
    private fun controlName(): String = compose.textsOf(TimerTags.start(0)).first()

    private fun open() {
        runBlocking { recipes.save(bread) }
        show(bread)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.start(0)) }
    }

    @Test
    fun aTimerIsNamedWithTheReferenceAsTheCardShowsIt() {
        open()
        assertEquals("Knead 200 g flour", controlName())
        more(4)
        assertEquals("Knead 400 g flour", controlName())
        start(0)
        awaitHeld { it.size == 1 }
        assertEquals("Knead 400 g flour", held().single().name)
        compose.waitUntil(PATIENCE_MS) { timers.announcements.started.isNotEmpty() }
        assertEquals("Knead 400 g flour", timers.announcements.started.last().name)
        // Back at 4 the step reads 200 g; the held timer keeps the name it started under, and is not dropped.
        fewer(4)
        pass(1.minutes)
        awaitStatus(0, "4:00")
        assertEquals("Knead 400 g flour", controlName())
        assertEquals("Knead 400 g flour", held().single().name)
        assertTrue(timers.announcements.cancelled.isEmpty())
    }

    // FLOW-017 (1), FLOW-016 (8): opened at the usual count, the step and its timer read the quantity at that count.
    @Test
    fun aTimerOnACardOpenedAtTheUsualCountIsNamedWithTheQuantityAtIt() {
        usual.set(Rational.of(2))
        open()
        assertEquals(listOf("Servings: 2"), compose.textsOf(CardTags.SERVINGS))
        assertEquals("Knead 100 g flour", controlName())
        start(0)
        awaitHeld { it.size == 1 }
        assertEquals("Knead 100 g flour", held().single().name)
        compose.waitUntil(PATIENCE_MS) { timers.announcements.started.isNotEmpty() }
        assertEquals("Knead 100 g flour", timers.announcements.started.last().name)
    }

    // The card reopens at the authored count: the timer started at another one is still the step's own.
    @Test
    fun aTimerStartedAtAnotherCountSurvivesTheCardReopening() {
        open()
        more(2)
        start(0)
        awaitHeld { it.size == 1 }
        leaveTheCard()
        compose.waitUntil(PATIENCE_MS) { shown(AWAY) }
        show(bread)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.start(0)) }
        pass(1.minutes)
        awaitStatus(0, "4:00")
        assertEquals("Knead 300 g flour", controlName())
        assertEquals(listOf("Knead 300 g flour"), held().map { it.name })
        assertTrue(breadKey !in timers.announcements.cancelled)
        // Once cleared, a new start takes the name the step reads by now.
        compose.onNodeWithTag(TimerTags.cancel(0)).performScrollTo().performClick()
        awaitHeld { it.isEmpty() }
        compose.waitUntil(PATIENCE_MS) { controlName() == "Knead 200 g flour" }
        start(0)
        awaitHeld { it.size == 1 }
        assertEquals("Knead 200 g flour", held().single().name)
    }

    // An edit to the step's own words still drops the timer, as before.
    @Test
    fun aTimerWhoseStepTextChangedIsDropped() {
        open()
        more(1)
        start(0)
        awaitHeld { it.size == 1 }
        runBlocking { recipes.save(bread.copy(steps = listOf(bread.steps.single().copy(text = "Fold  flour")))) }
        awaitHeld { it.isEmpty() }
        assertEquals(listOf(breadKey), timers.announcements.cancelled)
    }

    // FLOW-016 (8): a step's part of an ingredient reads in a derived name as the card shows it — the part, scaled.
    @Test
    fun aTimerNamedFromAPartReadsThePartAsTheCardShowsIt() {
        val part = IngredientReference(0, 6, IngredientShare(Rational.of(50), QuantityUnit.GRAM))
        val parted = bread.copy(steps = listOf(bread.steps.single().copy(ingredientReferences = listOf(part))))
        runBlocking { recipes.save(parted) }
        show(parted)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.start(0)) }
        assertEquals("Knead 50 g flour", controlName())
        more(4)
        assertEquals("Knead 100 g flour", controlName())
        start(0)
        awaitHeld { it.size == 1 }
        assertEquals("Knead 100 g flour", held().single().name)
    }
}
