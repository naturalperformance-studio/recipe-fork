package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.kitchen.FRYING_PAN
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.kitchen.KITCHEN_DICTIONARY
import app.recipefork.app.kitchen.PARCHMENT
import app.recipefork.app.kitchen.SAUCEPAN
import app.recipefork.app.kitchen.SCALE
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
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
import java.time.Instant

/**
 * T-205, scenario 3 of v0.28: with My kitchen holding an item that counts, an item of equipment naming an entry the
 * kitchen lacks says "Not in your kitchen" — at its row's end on View, under its name on Cooking — in words, read with the line; free
 * text, what does not count and an empty kitchen mark nothing; ticking or unticking in My kitchen marks an open card
 * afresh.
 */
@RunWith(AndroidJUnit4::class)
class CardKitchenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val marks = DoneMarks.inMemory()

    /** The frying pan (had), a saucepan of 3 l (lacked), free text, parchment (a consumable, never lacked). */
    private val soup = Recipe.create(now, null).copy(
        title = "Soup",
        servings = 2,
        equipment = listOf(
            EquipmentItem.entry(FRYING_PAN),
            EquipmentItem.entry(SAUCEPAN, "3 l"),
            EquipmentItem.free("Ladle"),
            EquipmentItem.entry(PARCHMENT),
        ),
        ingredients = listOf(Ingredient(0, null, freeTextName = "Water", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500))),
        steps = listOf(Step(0, "Boil the water.")),
    )

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun open(tab: CardMode, kitchen: HeldKitchen) {
        runBlocking { recipes.save(soup) }
        val environment = CardEnvironment(
            marks = marks,
            lastTab = HeldLastCardTab(tab),
            equipment = EquipmentSource { KITCHEN_DICTIONARY },
            kitchen = kitchen,
            ingredients = IngredientEntries { emptyList() },
        )
        compose.setContent {
            MaterialTheme(colorScheme = scheme) { CardScreen(soup.id, recipes, environment, onBack = {}, onEdit = {}) }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.INGREDIENTS) && compose.textsOf(item(0)).firstOrNull() == "Frying pan" }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun existsUnmerged(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun item(index: Int) = CardTags.equipment(index)

    /** The items marked as lacking, as drawn. */
    private fun lacking(): List<Int> = soup.equipment.indices.filter { existsUnmerged(EquipmentTags.lacking(it)) }

    /** Each item as the screen reader reads it: its one merged line. */
    private fun read(): List<List<String>> = soup.equipment.indices.map { compose.textsOf(item(it)) }

    private fun awaitLacking(expected: List<Int>) {
        compose.waitUntil(PATIENCE_MS) { lacking() == expected }
        compose.waitForIdle()
    }

    @Test
    fun viewMarksWhatTheKitchenLacks() {
        open(CardMode.VIEW, HeldKitchen(setOf(FRYING_PAN)))
        awaitLacking(listOf(1))
        assertEquals(
            listOf(listOf("Frying pan"), listOf("Saucepan 3 l", "Not in your kitchen"), listOf("Ladle"), listOf("Parchment paper")),
            read(),
        )
        // In words, muted as a note; the colour adds nothing the words do not say.
        val mark = compose.textLayoutsWithin(EquipmentTags.lacking(1)).single()
        assertEquals(scheme.onSurfaceVariant, mark.layoutInput.style.color)
        // On View the words stand at the row's end, beside the name (redesign-1 § 4.2).
        val words = node(EquipmentTags.lacking(1)).boundsInRoot
        val row = node(item(1)).boundsInRoot
        assertTrue("the mark stands beside the name, on its line", words.top < row.center.y && words.bottom > row.center.y)
        assertEquals("the mark stands at the row's end", row.right, words.right, 1f)
    }

    /** The items drawn with a square on View. */
    private fun squared(): List<Int> = soup.equipment.indices.filter { existsUnmerged(EquipmentTags.square(it)) }

    private fun state(index: Int): String? =
        compose.onNodeWithTag(item(index)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    // T-317 (the owner, 2026-10-29): a square only where the kitchen is known — filled for the pan it has, empty for the
    // saucepan it lacks; the free-text ladle and the consumable parchment have none and say nothing of the kitchen.
    @Test
    fun viewSquaresOnlyWhatTheKitchenIsKnownToHaveOrLack() {
        open(CardMode.VIEW, HeldKitchen(setOf(FRYING_PAN)))
        awaitLacking(listOf(1))
        assertEquals(listOf(0, 1), squared())
        assertEquals(listOf("In your kitchen", null, null, null), soup.equipment.indices.map(::state))
    }

    // With nothing ticked that counts the kitchen is not known: no square, no kitchen state, on any item.
    @Test
    fun anEmptyKitchenSquaresNothing() {
        open(CardMode.VIEW, HeldKitchen(setOf(PARCHMENT)))
        assertEquals(emptyList<Int>(), squared())
        assertEquals(listOf<String?>(null, null, null, null), soup.equipment.indices.map(::state))
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussianViewSquaresOnlyWhatTheKitchenIsKnownToHaveOrLack() {
        runBlocking { recipes.save(soup) }
        val environment = CardEnvironment(
            marks = marks, lastTab = HeldLastCardTab(CardMode.VIEW), equipment = EquipmentSource { KITCHEN_DICTIONARY },
            kitchen = HeldKitchen(setOf(FRYING_PAN)), ingredients = IngredientEntries { emptyList() },
        )
        compose.setContent { MaterialTheme(colorScheme = scheme) { CardScreen(soup.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { lacking() == listOf(1) && compose.textsOf(item(0)) == listOf("Сковорода") }
        compose.waitForIdle()
        assertEquals(listOf(0, 1), squared())
        assertEquals(listOf("Есть на кухне", null, null, null), soup.equipment.indices.map(::state))
    }

    @Test
    fun cookingMarksWhatTheKitchenLacksAndAMarkedItemKeepsIt() {
        open(CardMode.COOKING, HeldKitchen(setOf(FRYING_PAN)))
        awaitLacking(listOf(1))
        assertEquals(listOf("Saucepan 3 l", "Not in your kitchen"), compose.textsOf(item(1)))
        soup.equipment.indices.forEach { compose.assertCardTarget(item(it)) }
        // Marked done, the name is struck through; the kitchen's words stay as they are, and are still read.
        compose.onNodeWithTag(item(1)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Done", compose.onNodeWithTag(item(1)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(listOf("Saucepan 3 l", "Not in your kitchen"), compose.textsOf(item(1)))
        assertEquals(null, compose.textLayoutsWithin(EquipmentTags.lacking(1)).single().layoutInput.style.textDecoration)
        // The entry's item is kept by its explicit key.
        assertEquals(setOf(DoneMark.ofEquipment(1, EquipmentItem.entry(SAUCEPAN, "3 l"))), marks.held(BookName.Pool, soup.id))
        compose.chooseCardTab(CardMode.VIEW)
        awaitLacking(listOf(1))
        assertTrue(compose.textLayoutsWithin(item(1)).any { it.layoutInput.style.textDecoration == TextDecoration.LineThrough })
    }

    @Test
    fun anEmptyKitchenMarksNothing() {
        // A ticked consumable does not count: the kitchen is still empty.
        open(CardMode.VIEW, HeldKitchen(setOf(PARCHMENT)))
        assertEquals(emptyList<Int>(), lacking())
        assertEquals(listOf(listOf("Frying pan"), listOf("Saucepan 3 l"), listOf("Ladle"), listOf("Parchment paper")), read())
        compose.chooseCardTab(CardMode.COOKING)
        assertEquals(emptyList<Int>(), lacking())
    }

    // Ticking or unticking in My kitchen marks the open card afresh.
    @Test
    fun tickingInMyKitchenUpdatesAnOpenCard() {
        val kitchen = HeldKitchen()
        open(CardMode.COOKING, kitchen)
        assertEquals(emptyList<Int>(), lacking())
        compose.runOnIdle { kitchen.hold(listOf(SCALE), has = true) }
        awaitLacking(listOf(0, 1))
        compose.runOnIdle { kitchen.hold(listOf(SAUCEPAN), has = true) }
        awaitLacking(listOf(0))
        compose.runOnIdle { kitchen.hold(listOf(SCALE, SAUCEPAN), has = false) }
        awaitLacking(emptyList())
    }

    @Test
    @Config(qualifiers = "ru")
    fun theMarkReadsInRussian() {
        val kitchen = HeldKitchen(setOf(FRYING_PAN))
        runBlocking { recipes.save(soup) }
        val environment = CardEnvironment(
            marks = marks, lastTab = HeldLastCardTab(CardMode.COOKING), equipment = EquipmentSource { KITCHEN_DICTIONARY },
            kitchen = kitchen, ingredients = IngredientEntries { emptyList() },
        )
        compose.setContent { MaterialTheme(colorScheme = scheme) { CardScreen(soup.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        awaitLacking(listOf(1))
        assertEquals(listOf("Кастрюля 3 l", "Нет на кухне"), compose.textsOf(item(1)))
        assertEquals(listOf("Сковорода"), compose.textsOf(item(0)))
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private companion object {
        val scheme = lightColorScheme()
    }
}
