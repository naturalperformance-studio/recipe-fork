package app.recipefork.app.library

import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.SavedStateHandle
import app.recipefork.app.card.CardAnchor
import app.recipefork.app.card.CardFixing
import app.recipefork.app.card.HeldScale
import app.recipefork.core.recipe.RecipeId
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.firstAtTheTop
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import java.time.Instant

/**
 * The cards of a session (FLOW-017 (6, 7), FLOW-012, FLOW-058): a count set on a card is kept for its recipe for the
 * rest of the session — through the book, another recipe, the home action and the end of the process while away —
 * with its fixing and the line at the top; a new session opens every recipe at the usual count again.
 */
// Text measured as on a device, so that a larger text or a wider screen really changes the lines' heights.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class CardSessionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val container = ApplicationProvider.getApplicationContext<RecipeForkApplication>().container
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    /** Chicken, rice and salt for 4, with enough lines and steps that the card scrolls. */
    private val pilaf = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Rice", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(400)),
        ) + (2..19).map {
            Ingredient(it, null, freeTextName = "Spice $it, ground in a mortar with coarse salt, a pinch of sugar and dried herbs until fragrant", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.ONE)
        },
        steps = (0..11).map { Step(it, "Step $it: stir the pot slowly and look at it for a while before going on") },
    )
    private val soup = Recipe.create(now, null).copy(
        title = "Soup",
        servings = 2,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Water", unitType = UnitType.EXACT, unit = QuantityUnit.MILLILITRE, quantity = Rational.of(500)),
        ),
    )

    @After
    fun forgetTheUsualCount() {
        container.usualServings.set(null)
    }

    private fun save(vararg recipes: Recipe) = runBlocking { recipes.forEach { container.recipes.save(it) } }

    private fun launch(saved: Bundle? = null): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java)
            .let { if (saved == null) it.setup() else it.setup(saved) }.also { compose.waitForIdle() }

    /** Opens [recipe]'s card from the own recipes. */
    private fun open(recipe: Recipe) {
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        val entry = LibraryTags.entry(recipe.id.value)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(entry)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(entry).performScrollTo().performClick()
        awaitLines()
    }

    private fun awaitLines() {
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun back() {
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.OWN_LIST)).fetchSemanticsNodes().size == 1 }
    }

    private fun more(times: Int) = repeat(times) {
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun line(position: Int) = compose.textsOf(CardTags.ingredient(position))
    private fun fixed(position: Int) = "fixed" in line(position)

    private fun fix(position: Int, amount: String) {
        compose.onNodeWithTag(CardTags.ingredient(position)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement(amount)
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
    }

    /** How far the card is scrolled, in pixels. */
    private fun scrolled(): Float = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** Scrolls the card down by [pixels], as the reader's own scroll does. */
    private fun scrollBy(pixels: Float) {
        compose.onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
        compose.waitForIdle()
    }

    /** The process ends while away: only the saved state is left, from which the application is opened again. */
    private fun ActivityController<MainActivity>.endTheProcess(): Bundle {
        val saved = Bundle()
        pause().stop().saveInstanceState(saved)
        destroy()
        return saved
    }

    /** The reader removes the application from the recent applications: the task is gone with what it saved. */
    private fun ActivityController<MainActivity>.closeTask() {
        get().finish()
        pause().stop().destroy()
    }

    // FLOW-017 (7), scenario 2: 6 set on a card is there after the book and another recipe.
    @Test
    fun aCountSetOnACardIsKeptThroughTheBookAndAnotherRecipe() {
        save(pilaf, soup)
        launch()
        open(pilaf)
        more(2)
        assertEquals("Servings: 6", servings())
        back()
        open(soup)
        assertEquals("Servings: 2", servings())
        back()
        open(pilaf)
        assertEquals("Servings: 6", servings())
    }

    // FLOW-017 (6), FLOW-058 (3), scenario 2: removed from the recent applications, every card opens at the usual count.
    @Test
    fun aNewSessionOpensAtTheUsualCountAgain() {
        save(pilaf)
        container.usualServings.set(Rational.of(2))
        val activity = launch()
        open(pilaf)
        more(4)
        assertEquals("Servings: 6", servings())
        activity.closeTask()
        launch()
        open(pilaf)
        assertEquals("Servings: 2", servings())
    }

    // FLOW-058 (4): after a restart of the device — no saved state handed back — the usual count again.
    @Test
    fun aDeviceRestartOpensAtTheUsualCountAgain() {
        save(pilaf)
        val activity = launch()
        open(pilaf)
        more(2)
        activity.endTheProcess()
        launch(saved = null)
        open(pilaf)
        assertEquals("Servings: 4", servings())
    }

    // FLOW-012 (1, 3, 5), FLOW-058 (2), scenario 3: the process ended while away, the text scale and the orientation
    // changed — the same recipe, count, fixing and line at the top.
    @Test
    fun theProcessEndedWhileAwayReturnsToTheSameCardCountFixingAndPlace() {
        save(pilaf)
        val activity = launch()
        open(pilaf)
        more(2)
        fix(1, "300")
        assertTrue(fixed(1))
        val count = servings()
        scrollBy(1500f)
        val top = compose.firstAtTheTop()
        assertTrue("scrolled into the lines: $top", top.startsWith("card-ingredient-") || top.startsWith("card-step-"))
        val pixels = scrolled()
        val saved = activity.endTheProcess()
        RuntimeEnvironment.setFontScale(1.6f)
        RuntimeEnvironment.setQualifiers("+land")
        launch(saved)
        awaitLines()
        assertEquals(listOf("Pilaf"), compose.textsOf(CardTags.TITLE))
        assertEquals(count, servings())
        assertTrue(fixed(1))
        compose.waitUntil(PATIENCE_MS) { compose.firstAtTheTop() == top }
        assertEquals(top, compose.firstAtTheTop())
        // The wrapped lines above it changed height: the same line is at the top at another scroll in pixels.
        assertNotEquals(pixels, scrolled(), 1f)
    }

    // A fixed row replaced while away — position 0 holds another ingredient now — loses its fixing; the count stays.
    @Test
    fun aFixingOfARowReplacedWhileAwayIsDropped() {
        save(pilaf)
        val activity = launch()
        open(pilaf)
        more(2)
        fix(0, "300")
        val saved = activity.endTheProcess()
        val (chicken, rice) = pilaf.ingredients
        save(pilaf.copy(ingredients = listOf(rice.copy(position = 0), chicken.copy(position = 1)) + pilaf.ingredients.drop(2)))
        launch(saved)
        awaitLines()
        compose.waitUntil(PATIENCE_MS) { line(0).firstOrNull() == "Rice" }
        assertEquals("Servings: 6", servings())
        assertFalse(fixed(0) || fixed(1))
    }

    // A fixed row edited in the same session loses its fixing when the card is shown again; the count stays.
    @Test
    fun aFixingOfARowEditedInTheSessionIsDropped() {
        save(pilaf)
        launch()
        open(pilaf)
        more(2)
        fix(0, "300")
        back()
        save(pilaf.copy(ingredients = listOf(pilaf.ingredients[0].copy(quantity = Rational.of(600))) + pilaf.ingredients.drop(1)))
        open(pilaf)
        compose.waitUntil(PATIENCE_MS) { line(0).any { it.startsWith("900") } }
        assertEquals("Servings: 6", servings())
        assertFalse(fixed(0))
    }

    // Saved state stays small: the 50 cards most recently left at a count or place are held, the oldest forgotten.
    @Test
    fun onlyTheFiftyMostRecentCardsAreHeld() {
        val session = LibrarySession(SavedStateHandle())
        val ids = (0..50).map { RecipeId("recipe-$it") }
        ids.forEach { session.cards.holdScale(it, HeldScale(Rational.of(6))); session.cards.holdAnchor(it, CardAnchor("i1", 10)) }
        assertNull(session.cards.heldScale(ids[0]))
        assertNull(session.cards.heldAnchor(ids[0]))
        assertTrue(ids.drop(1).all { session.cards.heldScale(it) != null && session.cards.heldAnchor(it) != null })
        // Shown again, a card is the most recent: the next one forgotten is the oldest after it.
        session.cards.holdScale(ids[1], HeldScale(Rational.of(3)))
        session.cards.holdScale(RecipeId("recipe-new"), HeldScale(Rational.of(2)))
        assertEquals(HeldScale(Rational.of(3)), session.cards.heldScale(ids[1]))
        assertNull(session.cards.heldScale(ids[2]))
    }

    // A card opened again and only read — nothing changed on it — is the most recent too: the oldest shown goes, not it.
    @Test
    fun aCardReadAgainWithoutAChangeIsTheMostRecent() {
        val session = LibrarySession(SavedStateHandle())
        val ids = (0..49).map { RecipeId("recipe-$it") }
        ids.forEach { session.cards.holdScale(it, HeldScale(Rational.of(6))) }
        // The oldest is opened again: its card reads what is held, and the reader changes nothing.
        assertEquals(HeldScale(Rational.of(6)), session.cards.heldScale(ids[0]))
        session.cards.holdScale(RecipeId("recipe-new"), HeldScale(Rational.of(2)))
        assertEquals(HeldScale(Rational.of(6)), session.cards.heldScale(ids[0]))
        assertNull(session.cards.heldScale(ids[1]))
    }

    // T-092: a recipe imported from a file is new to the book; nothing held of a card of its identity — a recipe since
    // deleted, whose file it is — is applied to it, while every other card keeps what it held.
    @Test
    fun anImportedRecipeIsHeldNothingOfAnEarlierCardOfItsIdentity() {
        val session = LibrarySession(SavedStateHandle())
        val gone = RecipeId("recipe-gone")
        val other = RecipeId("recipe-other")
        val fixing = CardFixing(1, Rational.of(200), QuantityUnit.GRAM)
        session.cards.holdScale(gone, HeldScale(Rational.of(3), fixing, 42))
        session.cards.holdAnchor(gone, CardAnchor("i1", 10))
        session.cards.holdScale(other, HeldScale(Rational.of(6)))
        session.showImported(gone)
        assertNull(session.cards.heldScale(gone))
        assertNull(session.cards.heldAnchor(gone))
        assertEquals(HeldScale(Rational.of(6)), session.cards.heldScale(other))
    }

    // The home action and return: the same count and fixing (FLOW-012 (1)).
    @Test
    fun theHomeActionAndReturnKeepTheCountAndFixing() {
        save(pilaf)
        val activity = launch()
        open(pilaf)
        fix(0, "250")
        activity.pause().stop()
        activity.restart().start().resume()
        awaitLines()
        assertTrue(fixed(0))
        assertEquals("Servings: ≈2", servings())
    }

    // A recipe changed while away so that its fixed ingredient is gone: it opens, the fixing dropped, the count kept.
    @Test
    fun aFixedIngredientGoneWhileAwayIsDroppedAndTheCountKept() {
        save(pilaf)
        val activity = launch()
        open(pilaf)
        more(2)
        fix(0, "300")
        val saved = activity.endTheProcess()
        save(pilaf.copy(ingredients = pilaf.ingredients.drop(1)))
        launch(saved)
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(1))).fetchSemanticsNodes().size == 1 }
        assertEquals("Servings: 6", servings())
        assertFalse((1..19).any(::fixed))
    }

    // A recipe deleted while away: the card says the book no longer holds it, and nothing fails.
    @Test
    fun aRecipeDeletedWhileAwayOpensWithoutFailing() {
        save(pilaf)
        val activity = launch()
        open(pilaf)
        more(2)
        fix(0, "300")
        val saved = activity.endTheProcess()
        runBlocking { container.recipes.delete(pilaf.id) }
        launch(saved)
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.MISSING).assertExists()
    }
}
