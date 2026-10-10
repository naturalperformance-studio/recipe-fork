package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Bound
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.random.Random

/**
 * DOM-022 on the editor's model: headings added above a row or after the last, named, renamed and removed with Undo;
 * every heading staying over the rows it had while rows are inserted, moved and removed; a group moved whole; and
 * whatever the edits, the recipe as the book keeps it passes the checks a recipe file is read with.
 */
@RunWith(AndroidJUnit4::class)
class IngredientGroupsEditorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    @After
    fun close() {
        runBlocking { RecipeEditor(recipes, seeded.id, { now }, CoroutineScope(Dispatchers.Unconfined)).load() }
        book.close()
    }

    private fun row(position: Int, name: String) =
        Ingredient(position, null, null, name, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(position + 1L))

    /** Salt above any group, then "Dough" over flour and water, and "Glaze" over sugar, butter and egg. */
    private val seeded = Recipe.create(now, null).copy(
        title = "Buns",
        ingredients = listOf("salt", "flour", "water", "sugar", "butter", "egg").mapIndexed { i, name -> row(i, name) },
        ingredientGroups = listOf(IngredientGroup("Dough", 1), IngredientGroup("Glaze", 3)),
        steps = listOf(
            Step(0, "Mix salt and flour.", listOf(IngredientReference(0, 4), IngredientReference(1, 13))),
            Step(1, "Glaze with egg.", listOf(IngredientReference(5, 11))),
        ),
    )

    private fun open(recipe: Recipe = seeded): RecipeEditor {
        runBlocking { recipes.save(recipe) }
        return RecipeEditor(recipes, recipe.id, { now.plusSeconds(60) }, CoroutineScope(Dispatchers.Unconfined))
            .also { runBlocking { it.load() } }
    }

    private fun stored(): Recipe = runBlocking {
        RecipeEditor(recipes, seeded.id, { now }, CoroutineScope(Dispatchers.Unconfined)).run {
            load()
            recipe.value!!
        }
    }

    private val RecipeEditor.now: Recipe get() = recipe.value!!

    /** The list as it reads: each heading's name and a colon before its rows; a row with no name yet as "?". */
    private fun Recipe.shape(): String = buildList {
        ingredients.forEachIndexed { position, row ->
            ingredientGroups.filter { it.firstPosition == position }.forEach { add("${it.name}:") }
            add(row.freeTextName ?: "?")
        }
        ingredientGroups.filter { it.firstPosition >= ingredients.size }.forEach { add("${it.name}:") }
    }.joinToString(" ")

    /** The name each reference of each step resolves to, step by step. */
    private fun Recipe.resolved(): List<List<String?>> =
        steps.sortedBy { it.position }.map { step -> step.ingredientReferences.map { ingredients[it.ingredientPosition].freeTextName } }

    private fun assertFileFit(recipe: Recipe) {
        assertEquals(recipe.shape(), emptyList<String>(), RecipeFileCheck.problems(RecipeFileContent.of(recipe, null, null)))
    }

    // A row inserted joins the group of the row it is put above — or, put below a row, that row's group; one appended
    // joins the last group. Every other row keeps its heading.
    @Test
    fun aRowInsertedJoinsTheGroupOfTheRowBesideIt() {
        val editor = open()
        assertTrue(editor.insertIngredient(1))
        assertEquals("salt Dough: ? flour water Glaze: sugar butter egg", editor.now.shape())
        assertTrue(editor.insertIngredient(4, intoGroupAbove = true))
        assertEquals("salt Dough: ? flour water ? Glaze: sugar butter egg", editor.now.shape())
        assertTrue(editor.insertIngredient(0))
        assertEquals("? salt Dough: ? flour water ? Glaze: sugar butter egg", editor.now.shape())
        assertTrue(editor.addIngredient())
        assertEquals("? salt Dough: ? flour water ? Glaze: sugar butter egg ?", editor.now.shape())
        assertEquals(listOf(listOf("salt", "flour"), listOf("egg")), editor.now.resolved())
        assertEquals(editor.now.ingredientGroups, stored().ingredientGroups)
    }

    // A row removed takes nothing else with it: the others keep their headings, and the references follow. The last
    // row of a group takes its heading with it — no heading stands over no rows — and Undo brings back both.
    @Test
    fun aRowRemovedLeavesTheHeadingsOverTheirRowsAndUndoBringsBackAHeadingItEmptied() {
        val editor = open()
        val dough = editor.groupKey(0)
        val glaze = editor.groupKey(1)
        assertTrue(editor.removeIngredient(1))
        assertEquals("salt Dough: water Glaze: sugar butter egg", editor.now.shape())
        assertEquals(listOf(listOf("salt"), listOf("egg")), editor.now.resolved())
        assertTrue(editor.removeIngredient(1))
        assertEquals("salt Glaze: sugar butter egg", editor.now.shape())
        assertEquals(glaze, editor.groupKey(0))
        assertFileFit(stored())

        assertTrue(editor.undoRemoval(editor.removal.value!!))
        assertEquals("salt Dough: water Glaze: sugar butter egg", editor.now.shape())
        // The heading put back is held by a key of its own again; the other kept its key throughout.
        assertEquals(glaze, editor.groupKey(1))
        assertTrue(editor.groupKey(0) != glaze && editor.groupKey(0) != dough)
        assertEquals(editor.now.ingredientGroups, stored().ingredientGroups)
    }

    // A row added and left holding nothing goes as though never added (T-210): nothing is offered for undoing, and a
    // heading added at the end that the row filled stays there over no rows.
    @Test
    fun anEmptyRowDroppedGoesWithNothingToUndoAndLeavesAHeadingJustAddedInPlace() {
        val editor = open()
        assertTrue(editor.addGroup(6))
        assertTrue(editor.renameGroup(2, "Topping"))
        assertTrue(editor.addIngredient())
        assertEquals("salt Dough: flour water Glaze: sugar butter egg Topping: ?", editor.now.shape())
        assertTrue(editor.dropIngredient(6))
        assertEquals("salt Dough: flour water Glaze: sugar butter egg Topping:", editor.now.shape())
        assertEquals(null, editor.removal.value)
        assertEquals(editor.now.ingredientGroups, stored().ingredientGroups)
        assertEquals(6, stored().ingredients.size)

        // Inserted above salt, dropped: the list is as it was, the references on their rows.
        assertTrue(editor.insertIngredient(0))
        assertTrue(editor.dropIngredient(0))
        assertEquals("salt Dough: flour water Glaze: sugar butter egg Topping:", editor.now.shape())
        assertEquals(listOf(listOf("salt", "flour"), listOf("egg")), editor.now.resolved())
        assertEquals(null, editor.removal.value)
        assertTrue(!editor.dropIngredient(6))
    }

    // Moved up or down one place, a row trades places with the item next to it: past a heading, it leaves its group
    // for the one above, or joins the one below as its first row — the rows between and their headings stay.
    @Test
    fun aRowMovedPastAHeadingChangesGroupAndNothingElseDoes() {
        val editor = open()
        // Items: salt, Dough:, flour, water, Glaze:, sugar, butter, egg.
        assertTrue(editor.moveIngredientItem(5, 4))
        assertEquals("salt Dough: flour water sugar Glaze: butter egg", editor.now.shape())
        assertTrue(editor.moveIngredientItem(0, 1))
        assertEquals("Dough: salt flour water sugar Glaze: butter egg", editor.now.shape())
        assertEquals(listOf(listOf("salt", "flour"), listOf("egg")), editor.now.resolved())
        // A move by rows lands the row where the row it passes stood, in that row's group.
        assertTrue(editor.moveIngredient(0, 4))
        assertEquals("Dough: flour water sugar Glaze: butter salt egg", editor.now.shape())
        assertTrue(editor.moveIngredient(5, 0))
        assertEquals("Dough: egg flour water sugar Glaze: butter salt", editor.now.shape())
        assertEquals(listOf(listOf("salt", "flour"), listOf("egg")), editor.now.resolved())
        assertEquals(editor.now.ingredientGroups, stored().ingredientGroups)
    }

    // A group moves whole — its heading and every row it owns — past the group above or below; never above the rows
    // no group owns, nor past the last group. Dragged by its heading, it does the same.
    @Test
    fun aGroupMovesWithItsRows() {
        val editor = open()
        assertFalse(editor.moveGroup(0, down = false))
        assertFalse(editor.moveGroup(1, down = true))
        assertTrue(editor.moveGroup(1, down = false))
        assertEquals("salt Glaze: sugar butter egg Dough: flour water", editor.now.shape())
        assertEquals(listOf(listOf("salt", "flour"), listOf("egg")), editor.now.resolved())
        assertTrue(editor.moveGroup(0, down = true))
        assertEquals("salt Dough: flour water Glaze: sugar butter egg", editor.now.shape())
        // The heading at item 1 carries its two rows: put back after the rest, it ends the list.
        val keys = (0..5).map(editor::ingredientKey)
        assertTrue(editor.moveIngredientItem(1, 5))
        assertEquals("salt Glaze: sugar butter egg Dough: flour water", editor.now.shape())
        assertEquals(listOf(keys[0], keys[3], keys[4], keys[5], keys[1], keys[2]), (0..5).map(editor::ingredientKey))
        assertEquals(editor.now.ingredientGroups, stored().ingredientGroups)
    }

    // "Add group" puts a heading above a row — splitting the group it stood in — or after the last row; never a second
    // at one place. Not named yet, it is not saved; named, it is.
    @Test
    fun aGroupAddedIsSavedOnceNamed() {
        val editor = open()
        assertFalse(editor.addGroup(3))
        assertTrue(editor.addGroup(4))
        assertEquals("salt Dough: flour water Glaze: sugar : butter egg", editor.now.shape())
        assertEquals(listOf("Dough", "Glaze"), stored().ingredientGroups.map { it.name })
        assertTrue(editor.renameGroup(2, "Topping"))
        assertEquals("salt Dough: flour water Glaze: sugar Topping: butter egg", stored().shape())

        assertTrue(editor.addGroup(6))
        assertFalse(editor.addGroup(6))
        assertEquals("salt Dough: flour water Glaze: sugar Topping: butter egg :", editor.now.shape())
        assertTrue(editor.renameGroup(3, "To serve"))
        assertTrue(editor.addIngredient())
        assertEquals("salt Dough: flour water Glaze: sugar Topping: butter egg To serve: ?", stored().shape())
        assertFileFit(stored())
    }

    // A name left blank is no name: a heading never named goes, one renamed takes back the name it had; a name is held
    // trimmed.
    @Test
    fun aNameLeftBlankIsNoName() {
        val editor = open()
        assertTrue(editor.addGroup(6))
        editor.settleGroupName(2, entered = "")
        assertEquals("salt Dough: flour water Glaze: sugar butter egg", editor.now.shape())

        editor.renameGroup(1, " ")
        editor.settleGroupName(1, entered = "Glaze")
        assertEquals("salt Dough: flour water Glaze: sugar butter egg", editor.now.shape())
        editor.renameGroup(1, " Icing ")
        editor.settleGroupName(1, entered = "Glaze")
        assertEquals("Icing", stored().ingredientGroups[1].name)
        assertNull(editor.removal.value)
    }

    // Removing a heading keeps its rows where they are, in the group above or in none; Undo puts it back.
    @Test
    fun aHeadingRemovedLeavesItsRowsAndUndoPutsItBack() {
        val editor = open()
        assertTrue(editor.removeGroup(1))
        assertEquals("salt Dough: flour water sugar butter egg", editor.now.shape())
        assertEquals(Removal.Kind.GROUP, editor.removal.value!!.kind)
        assertTrue(editor.undoRemoval(editor.removal.value!!))
        assertEquals("salt Dough: flour water Glaze: sugar butter egg", stored().shape())
        assertTrue(editor.removeGroup(0))
        assertEquals("salt flour water Glaze: sugar butter egg", stored().shape())
        assertEquals(listOf(listOf("salt", "flour"), listOf("egg")), editor.now.resolved())
    }

    // A name over the limit is kept and marked once the author leaves it, as every limit is (FLOW-028).
    @Test
    fun aNameOverTheLimitIsMarkedOnLeaving() {
        val editor = open()
        val long = "g".repeat(RecipeBounds.MAX_GROUP_NAME + 1)
        assertTrue(editor.renameGroup(0, long))
        assertTrue(editor.marks.value.isEmpty())
        editor.leave(RecipePart.GroupName(0))
        assertEquals(Bound.OverLimit(RecipeBounds.MAX_GROUP_NAME, long.length), editor.marks.value[RecipePart.GroupName(0)])
        assertEquals(long, stored().ingredientGroups[0].name)
        // The mark stays with its heading when a heading is added above it.
        assertTrue(editor.addGroup(0))
        assertEquals(setOf(RecipePart.GroupName(1)), editor.marks.value.keys)
        editor.renameGroup(1, "Dough")
        assertTrue(editor.marks.value.isEmpty())
        assertEquals(Bound.OverLimit(40, 41), RecipeBounds.all(seeded.copy(ingredientGroups = listOf(IngredientGroup(long, 0))))[RecipePart.GroupName(0)])
        // Every part outside its bound in the order the editor shows them: each heading's name above its rows' notes.
        val note = "n".repeat(RecipeBounds.MAX_NOTE + 1)
        val marked = seeded.copy(
            ingredients = seeded.ingredients.map { it.copy(note = note) },
            ingredientGroups = listOf(IngredientGroup(long, 1), IngredientGroup(long, 3), IngredientGroup(long, 6)),
        )
        assertEquals(
            listOf(
                RecipePart.IngredientNote(0), RecipePart.GroupName(0), RecipePart.IngredientNote(1), RecipePart.IngredientNote(2),
                RecipePart.GroupName(1), RecipePart.IngredientNote(3), RecipePart.IngredientNote(4), RecipePart.IngredientNote(5),
                RecipePart.GroupName(2),
            ),
            RecipeBounds.all(marked).keys.filter { it is RecipePart.GroupName || it is RecipePart.IngredientNote },
        )
    }

    // No move leaves a group with no rows: a heading with no rows yet stays last, and the only row of a group stays in
    // it — by the item, by the row and by the menu's neighbour alike.
    @Test
    fun noMoveEmptiesAGroup() {
        val editor = open()
        assertTrue(editor.addGroup(6))
        assertTrue(editor.renameGroup(2, "To serve"))
        // Items: salt, Dough:, flour, water, Glaze:, sugar, butter, egg, To serve:.
        assertFalse(editor.now.listItems.movable(8))
        assertFalse(editor.moveIngredientItem(8, 4))
        assertFalse(editor.moveIngredientItem(8, 1))
        assertEquals("salt Dough: flour water Glaze: sugar butter egg To serve:", editor.now.shape())
        assertNull(editor.removal.value)

        assertTrue(editor.removeIngredient(1))
        // Dough now owns water alone: it can go neither up past its heading nor down past the next one.
        val items = editor.now.listItems
        assertEquals(ListItem.Row(1), items[2])
        assertFalse(items.movable(2))
        assertFalse(editor.moveIngredientItem(2, 1))
        assertFalse(editor.moveIngredientItem(2, 3))
        assertFalse(editor.moveIngredient(1, 3))
        assertEquals("salt Dough: water Glaze: sugar butter egg To serve:", editor.now.shape())
        // Rows a group has to spare still move in and out of it.
        assertTrue(editor.moveIngredientItem(4, 3))
        assertEquals("salt Dough: water sugar Glaze: butter egg To serve:", editor.now.shape())
        assertEquals(listOf("Dough", "Glaze", "To serve"), stored().ingredientGroups.map { it.name })
    }

    // Whatever series of edits the author makes, every heading stays over the rows it had — the row moved aside — and
    // the recipe as the book keeps it, exported, passes every check the importer reads a file with.
    @Test
    fun aSeriesOfEditsAlwaysLeavesARecipeTheImporterAccepts() {
        val editor = open()
        val random = Random(171)
        var named = 0
        repeat(400) { turn ->
            val recipe = editor.now
            val count = recipe.ingredients.size
            val items = recipe.listItems
            fun groupsOfRows() = editor.now.let { r ->
                r.ingredients.indices.associate { p ->
                    editor.ingredientKey(p) to r.ingredientGroups.indexOfLast { it.firstPosition <= p }.let { if (it < 0) null else editor.groupKey(it) }
                }
            }
            val before = groupsOfRows()
            var moved: Long? = null
            val op = random.nextInt(9)
            when (op) {
                0 -> if (count < 40) editor.addIngredient()
                1 -> if (count < 40) editor.insertIngredient(random.nextInt(count + 1), random.nextBoolean())
                2 -> if (count > 1) editor.removeIngredient(random.nextInt(count))
                3 -> items.indices.filter { items[it] is ListItem.Row }.randomOrNull(random)?.let { from ->
                    moved = editor.ingredientKey((items[from] as ListItem.Row).position)
                    editor.moveIngredientItem(from, random.nextInt(items.size))
                }
                4 -> if (editor.addGroup(random.nextInt(count + 1))) {
                    val index = editor.now.ingredientGroups.indexOfFirst { it.name.isEmpty() }
                    editor.renameGroup(index, "Group ${++named}")
                }
                5 -> if (recipe.ingredientGroups.isNotEmpty()) editor.removeGroup(random.nextInt(recipe.ingredientGroups.size))
                6 -> if (recipe.ingredientGroups.isNotEmpty()) editor.moveGroup(random.nextInt(recipe.ingredientGroups.size), random.nextBoolean())
                7 -> editor.removal.value?.let(editor::undoRemoval)
                8 -> items.indices.filter { items[it] is ListItem.Heading }.randomOrNull(random)?.let { heading ->
                    val span = blockAt(items, heading)
                    (0..items.size - span).filter(groupLanding(items, heading, span)).randomOrNull(random)
                        ?.let { editor.moveIngredientItem(heading, it) }
                }
            }
            // A move never takes a heading away.
            if (op == 3 || op == 8) assertEquals(recipe.ingredientGroups.size, editor.now.ingredientGroups.size)
            if (op <= 3) {
                // An edit of rows leaves every other row under the heading it had.
                val after = groupsOfRows()
                val regrouped = (before.keys.intersect(after.keys) - setOfNotNull(moved)).filter { before[it] != after[it] }
                assertTrue("turn $turn, op $op: ${recipe.shape()} → ${editor.now.shape()}", regrouped.isEmpty())
            }
            assertFileFit(editor.now.withNamedGroups())
        }
        assertTrue(editor.now.ingredientGroups.isNotEmpty())
        assertFileFit(stored())
        assertEquals(editor.now.ingredientGroups, stored().ingredientGroups)
    }
}
