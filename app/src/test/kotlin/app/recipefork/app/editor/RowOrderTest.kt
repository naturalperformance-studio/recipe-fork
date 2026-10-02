package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-025 and DOM-007 on the editor's model: steps and ingredient rows inserted anywhere and moved, with the
 * references a step holds — put into the recipe directly, as no screen writes them yet — still resolving to the same
 * ingredient, and the keys the screen holds rows by following their rows.
 */
@RunWith(AndroidJUnit4::class)
class RowOrderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    /** Every write still queued is in the book before it closes: the queue is shared by every editor. */
    @After
    fun close() {
        runBlocking { RecipeEditor(recipes, seeded.id, { now }, CoroutineScope(Dispatchers.Unconfined)).load() }
        book.close()
    }

    private fun row(position: Int, name: String) =
        Ingredient(position, null, null, name, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(position + 1L))

    private val names = listOf("salt", "flour", "water", "yeast", "oil")

    /** Five rows and five steps; every step but the second references rows, and three carry timers. */
    private val seeded = Recipe.create(now, null).copy(
        title = "Bread",
        ingredients = names.mapIndexed { i, name -> row(i, name) },
        steps = listOf(
            Step(0, "Mix salt and flour.", listOf(IngredientReference(0, 4), IngredientReference(1, 13)), Timer(2.minutes)),
            Step(1, "Wait."),
            Step(2, "Add water and yeast.", listOf(IngredientReference(2, 4), IngredientReference(3, 14)), Timer(10.minutes, "Proof")),
            Step(3, "Knead with oil.", listOf(IngredientReference(4, 11))),
            Step(4, "Bake with the rest of the flour.", listOf(IngredientReference(1, 26)), Timer(40.minutes, "Oven")),
        ),
        media = listOf(
            Media("cover", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE),
            Media("dough", null, MediaKind.PHOTO, MediaAttachment.ToStep(3), MediaVisibility.PRIVATE),
            Media("loaf", null, MediaKind.VIDEO, MediaAttachment.ToStep(4), MediaVisibility.PRIVATE),
        ),
    )

    private fun open(recipe: Recipe = seeded): RecipeEditor {
        runBlocking { recipes.save(recipe) }
        return RecipeEditor(recipes, recipe.id, { now.plusSeconds(60) }, CoroutineScope(Dispatchers.Unconfined))
            .also { runBlocking { it.load() } }
    }

    /** The recipe as the book holds it once every write queued before is in it. */
    private fun stored(id: RecipeId = seeded.id): Recipe = runBlocking {
        RecipeEditor(recipes, id, { now }, CoroutineScope(Dispatchers.Unconfined)).run {
            load()
            recipe.value!!
        }
    }

    private val RecipeEditor.now: Recipe get() = recipe.value!!

    /** The name each reference of each step resolves to, step by step. */
    private fun Recipe.resolved(): List<List<String?>> =
        steps.sortedBy { it.position }.map { step -> step.ingredientReferences.map { ingredients[it.ingredientPosition].freeTextName } }

    /** Every step without its position: what moving and inserting must not change. */
    private fun Recipe.stepContents(): List<Step> = steps.sortedBy { it.position }.map { it.copy(position = 0) }

    private fun Recipe.mediaSteps(): Map<String, Int?> = media.associate { it.mediaId to (it.attachment as? MediaAttachment.ToStep)?.stepPosition }

    private fun assertPositions(recipe: Recipe) {
        assertEquals(recipe.steps.indices.toList(), recipe.steps.map { it.position })
        assertEquals(recipe.ingredients.indices.toList(), recipe.ingredients.map { it.position })
    }

    // FLOW-025 (1): a step inserted between the second and the third takes that place; the ones after it move down
    // with their timers, references and media.
    @Test
    fun aStepInsertedBetweenTheSecondAndTheThirdTakesThatPlace() {
        val editor = open()
        val before = editor.now
        assertTrue(editor.insertStep(2))
        val after = editor.now
        assertPositions(after)
        assertEquals(
            listOf("Mix salt and flour.", "Wait.", "", "Add water and yeast.", "Knead with oil.", "Bake with the rest of the flour."),
            after.steps.map { it.text },
        )
        assertEquals(Step(2, ""), after.steps[2])
        val contents = before.stepContents()
        assertEquals(contents.take(2) + Step(0, "") + contents.drop(2), after.stepContents())
        assertEquals(before.resolved().take(2) + listOf(emptyList()) + before.resolved().drop(2), after.resolved())
        assertEquals(mapOf("cover" to null, "dough" to 4, "loaf" to 5), after.mediaSteps())
        assertEquals(before.ingredients, after.ingredients)
        assertEquals(after, stored())

        // At the very top and at the very end too; nowhere outside.
        assertTrue(editor.insertStep(0))
        assertTrue(editor.insertStep(7))
        assertEquals(listOf("", "Mix salt and flour.", "Wait.", "", "Add water and yeast."), editor.now.steps.take(5).map { it.text })
        assertEquals("", editor.now.steps.last().text)
        assertPositions(editor.now)
        val held = editor.now
        assertFalse(editor.insertStep(-1))
        assertFalse(editor.insertStep(9))
        assertEquals(held, editor.now)
    }

    // FLOW-025 (2, 4): the fourth step moved above the second; nothing but the order changes.
    @Test
    fun aStepMovedChangesTheOrderAndNothingElse() {
        val editor = open()
        val before = editor.now
        assertTrue(editor.moveStep(3, 1))
        val after = editor.now
        assertPositions(after)
        val order = listOf(0, 3, 1, 2, 4)
        assertEquals(order.map { before.stepContents()[it] }, after.stepContents())
        assertEquals(order.map { before.resolved()[it] }, after.resolved())
        assertEquals(listOf(Timer(2.minutes), null, null, Timer(10.minutes, "Proof"), Timer(40.minutes, "Oven")), after.steps.map { it.timer })
        assertEquals(mapOf("cover" to null, "dough" to 1, "loaf" to 4), after.mediaSteps())
        assertEquals(before.ingredients, after.ingredients)
        assertEquals(after, stored())

        // One place down, as the row menu's "Move down" does, and back up again.
        assertTrue(editor.moveStep(0, 1))
        assertEquals(listOf(3, 0, 1, 2, 4).map { before.stepContents()[it] }, editor.now.stepContents())
        assertTrue(editor.moveStep(1, 0))
        assertEquals(order.map { before.stepContents()[it] }, editor.now.stepContents())
        // From the last to the first.
        assertTrue(editor.moveStep(4, 0))
        assertEquals(listOf(4, 0, 3, 1, 2).map { before.stepContents()[it] }, editor.now.stepContents())
        assertEquals(mapOf("cover" to null, "dough" to 2, "loaf" to 0), editor.now.mediaSteps())
        val held = editor.now
        assertFalse(editor.moveStep(0, 0))
        assertFalse(editor.moveStep(0, -1))
        assertFalse(editor.moveStep(4, 5))
        assertFalse(editor.moveStep(5, 0))
        assertEquals(held, editor.now)
    }

    // FLOW-025 (1): a row inserted is exactly an appended row, at that place; the rows after it move down.
    @Test
    fun anIngredientRowInsertedIsExactlyAnAppendedRowAtThatPlace() {
        val editor = open()
        val before = editor.now
        assertTrue(editor.insertIngredient(2))
        assertTrue(editor.addIngredient())
        val after = editor.now
        assertPositions(after)
        assertEquals(listOf("salt", "flour", null, "water", "yeast", "oil", null), after.ingredients.map { it.freeTextName })
        assertEquals(after.ingredients[6].copy(position = 2), after.ingredients[2])
        assertEquals(before.ingredients.map { it.copy(position = 0) }, (after.ingredients.take(2) + after.ingredients.slice(3..5)).map { it.copy(position = 0) })
        assertTrue(editor.insertIngredient(0))
        assertEquals(after.ingredients[6].copy(position = 0), editor.now.ingredients[0])
        val held = editor.now
        assertFalse(editor.insertIngredient(-1))
        assertFalse(editor.insertIngredient(9))
        assertEquals(held, editor.now)
    }

    // DOM-007, FLOW-025 (3, 5): every reference resolves to the same ingredient after rows are inserted, moved and
    // deleted; a reference to a deleted row is dropped; the step text and timer are untouched.
    @Test
    fun everyReferenceResolvesToTheSameIngredientAfterRowsAreInsertedMovedAndDeleted() {
        val editor = open()
        val before = editor.now
        val resolved = before.resolved()
        // Each row as it was, but for its position; the inserted rows are those holding no name.
        val rows = before.ingredients.associateBy({ it.freeTextName }, { it.copy(position = 0) })
        fun assertUnchangedApartFromReferences() {
            assertPositions(editor.now)
            editor.now.ingredients.filter { it.freeTextName != null }.forEach { assertEquals(rows[it.freeTextName], it.copy(position = 0)) }
            assertEquals(resolved, editor.now.resolved())
            assertEquals(before.steps.map { it.copy(ingredientReferences = emptyList()) }, editor.now.steps.map { it.copy(ingredientReferences = emptyList()) })
            assertEquals(before.steps.map { refs -> refs.ingredientReferences.map { it.textOffset } }, editor.now.steps.map { refs -> refs.ingredientReferences.map { it.textOffset } })
        }
        assertTrue(editor.insertIngredient(1))
        assertUnchangedApartFromReferences()
        assertTrue(editor.insertIngredient(0))
        assertUnchangedApartFromReferences()
        // The fourth above the second, then the first to the last, then one down.
        assertTrue(editor.moveIngredient(3, 1))
        assertUnchangedApartFromReferences()
        assertTrue(editor.moveIngredient(0, editor.now.ingredients.size - 1))
        assertUnchangedApartFromReferences()
        assertTrue(editor.moveIngredient(2, 3))
        assertUnchangedApartFromReferences()
        // The empty rows go; then a referenced row goes, and only the references to it are dropped.
        editor.now.ingredients.filter { it.freeTextName == null }.map { it.position }.sortedDescending().forEach { editor.removeIngredient(it) }
        assertUnchangedApartFromReferences()
        assertEquals(names.toSet(), editor.now.ingredients.map { it.freeTextName }.toSet())
        assertTrue(editor.removeIngredient(editor.now.ingredients.indexOfFirst { it.freeTextName == "flour" }))
        assertPositions(editor.now)
        assertEquals(resolved.map { step -> step.filter { it != "flour" } }, editor.now.resolved())
        assertEquals(before.steps.map { it.timer }, editor.now.steps.map { it.timer })
        assertEquals(editor.now, stored())
        val held = editor.now
        assertFalse(editor.moveIngredient(0, 0))
        assertFalse(editor.moveIngredient(0, -1))
        assertFalse(editor.moveIngredient(held.ingredients.size, 0))
        assertEquals(held, editor.now)
    }

    // A deleted step's media go with it; the media of the steps after it follow them up.
    @Test
    fun aDeletedStepsMediaGoWithItAndTheRestFollowTheirSteps() {
        val editor = open()
        assertTrue(editor.removeStep(3))
        assertEquals(mapOf("cover" to null, "loaf" to 3), editor.now.mediaSteps())
        assertEquals("Bake with the rest of the flour.", editor.now.steps[3].text)
        assertTrue(editor.removeStep(0))
        assertEquals(mapOf("cover" to null, "loaf" to 2), editor.now.mediaSteps())
        assertEquals(editor.now, stored())
    }

    // References move with their step: a step moved keeps resolving to its own ingredients.
    @Test
    fun referencesMoveWithTheirStep() {
        val editor = open()
        val resolved = editor.now.resolved()
        assertTrue(editor.moveStep(0, 4))
        assertTrue(editor.insertStep(1))
        assertEquals(listOf(resolved[1], emptyList(), resolved[2], resolved[3], resolved[4], resolved[0]), editor.now.resolved())
        assertEquals("Mix salt and flour.", editor.now.steps[5].text)
        assertEquals(listOf(IngredientReference(0, 4), IngredientReference(1, 13)), editor.now.steps[5].ingredientReferences)
    }

    // What the screen holds for a row is keyed by the row: the keys follow inserts, moves and deletes.
    @Test
    fun theRowKeysFollowTheirRows() {
        val editor = open()
        val steps = (0..4).map { editor.stepKey(it) }
        assertEquals(5, steps.toSet().size)
        editor.insertStep(1)
        val inserted = editor.stepKey(1)
        assertTrue(inserted !in steps)
        assertEquals(listOf(steps[0], inserted) + steps.drop(1), (0..5).map { editor.stepKey(it) })
        editor.moveStep(4, 1)
        assertEquals(listOf(steps[0], steps[3], inserted, steps[1], steps[2], steps[4]), (0..5).map { editor.stepKey(it) })
        editor.removeStep(0)
        assertEquals(listOf(steps[3], inserted, steps[1], steps[2], steps[4]), (0..4).map { editor.stepKey(it) })

        val rows = (0..4).map { editor.ingredientKey(it) }
        assertEquals(5, rows.toSet().size)
        editor.insertIngredient(0)
        val first = editor.ingredientKey(0)
        assertTrue(first !in rows)
        assertEquals(listOf(first) + rows, (0..5).map { editor.ingredientKey(it) })
        editor.moveIngredient(0, 5)
        assertEquals(rows + first, (0..5).map { editor.ingredientKey(it) })
        editor.removeIngredient(1)
        assertEquals(listOf(rows[0]) + rows.drop(2) + first, (0..4).map { editor.ingredientKey(it) })
        editor.addIngredient()
        assertNotEquals(first, editor.ingredientKey(5))
        assertEquals(6, (0..5).map { editor.ingredientKey(it) }.toSet().size)
    }

    // FLOW-002 (4): a frozen recipe takes no insert and no move.
    @Test
    fun aFrozenRecipeTakesNoInsertAndNoMove() {
        val frozen = seeded.inState(RecipeState.PUBLISHED)
        val editor = open(frozen)
        assertFalse(editor.insertStep(1))
        assertFalse(editor.moveStep(3, 1))
        assertFalse(editor.insertIngredient(1))
        assertFalse(editor.moveIngredient(3, 1))
        assertEquals(frozen, editor.now)
        assertEquals(frozen, stored(frozen.id))
    }
}
