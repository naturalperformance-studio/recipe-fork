package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Bound
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.RecipeState
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

/**
 * The equipment on the editor's model: items added — up to fifteen, a sixteenth refused with a statement until the
 * author's next action — typed, settled trimmed on leaving and dropped when left blank, removed with Undo and moved;
 * an item's mark stays with it; the book never holds a blank or untrimmed item; a frozen recipe takes no change.
 */
@RunWith(AndroidJUnit4::class)
class EquipmentEditorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    private val seeded = Recipe.create(now, null).copy(title = "Pancakes", equipment = listOf("Bowl", "Whisk", "Frying pan 26 cm").map(EquipmentItem::free))

    @After
    fun close() {
        stored()
        book.close()
    }

    private fun open(recipe: Recipe = seeded): RecipeEditor {
        runBlocking { recipes.save(recipe) }
        return RecipeEditor(recipes, recipe.id, { now.plusSeconds(60) }, CoroutineScope(Dispatchers.Unconfined))
            .also { runBlocking { it.load() } }
    }

    /** The recipe as the book holds it, once every write is in. */
    private fun stored(): Recipe = runBlocking {
        RecipeEditor(recipes, seeded.id, { now }, CoroutineScope(Dispatchers.Unconfined)).run {
            load()
            recipe.value ?: seeded
        }
    }

    /** The items as the editor holds them, each as it reads. */
    private val RecipeEditor.items: List<String> get() = recipe.value!!.equipment.map { it.shown(null) }

    @Test
    fun addAppendsAnEmptyItemThatTheAuthorTypesInto() {
        val editor = open()
        assertTrue(editor.addEquipment())
        assertEquals(listOf("Bowl", "Whisk", "Frying pan 26 cm", ""), editor.items)
        assertTrue(editor.setEquipment(3, "Ladle"))
        assertEquals(listOf("Bowl", "Whisk", "Frying pan 26 cm", "Ladle").map(EquipmentItem::free), stored().equipment)
    }

    // Up to fifteen: a sixteenth "Add" adds nothing and says why until the author's next action (GUIDE-007).
    @Test
    fun aSixteenthItemIsRefusedWithAStatementUntilTheNextAction() {
        val fifteen = (1..RecipeBounds.MAX_EQUIPMENT).map { "Item $it" }
        val editor = open(seeded.copy(equipment = fifteen.map(EquipmentItem::free)))
        assertFalse(editor.equipmentStatement.value)
        assertFalse(editor.addEquipment())
        assertEquals(fifteen, editor.items)
        assertTrue(editor.equipmentStatement.value)
        editor.noteAction()
        assertFalse(editor.equipmentStatement.value)
        // A change is an action too.
        assertFalse(editor.addEquipment())
        assertTrue(editor.equipmentStatement.value)
        editor.setEquipment(0, "Oven")
        assertFalse(editor.equipmentStatement.value)
        // With one taken away, one more is added.
        assertTrue(editor.removeEquipment(14))
        assertTrue(editor.addEquipment())
        assertFalse(editor.equipmentStatement.value)
    }

    // An item is kept as typed while typed, and held trimmed once left; one left blank goes without a word.
    @Test
    fun leavingAnItemTrimsItAndDropsItWhenBlank() {
        val editor = open()
        editor.setEquipment(1, "  Balloon whisk ")
        assertEquals("  Balloon whisk ", editor.items[1])
        editor.settleEquipment(1)
        assertEquals(listOf("Bowl", "Balloon whisk", "Frying pan 26 cm"), editor.items)
        val whiskKey = editor.equipmentKey(1)
        val panKey = editor.equipmentKey(2)
        editor.addEquipment()
        editor.setEquipment(3, "   ")
        editor.settleEquipment(3)
        editor.setEquipment(0, "")
        editor.settleEquipment(0)
        assertEquals(listOf("Balloon whisk", "Frying pan 26 cm"), editor.items)
        assertNull(editor.removal.value)
        // The items after the one dropped keep their keys, so what the screen holds for them stays with them.
        assertEquals(0, editor.equipmentIndex(whiskKey))
        assertEquals(1, editor.equipmentIndex(panKey))
        assertEquals(listOf("Balloon whisk", "Frying pan 26 cm").map(EquipmentItem::free), stored().equipment)
    }

    // The book never holds a blank item, nor an untrimmed one, even while the author is still typing it.
    @Test
    fun theBookHoldsNoBlankOrUntrimmedItem() {
        val editor = open()
        editor.addEquipment()
        assertEquals(listOf("Bowl", "Whisk", "Frying pan 26 cm").map(EquipmentItem::free), stored().equipment)
        editor.setEquipment(3, " Ladle ")
        assertEquals(listOf("Bowl", "Whisk", "Frying pan 26 cm", "Ladle").map(EquipmentItem::free), stored().equipment)
        assertEquals(" Ladle ", editor.items[3])
    }

    // A removal is offered for undoing, as a row's is; Undo puts the item back at its place.
    @Test
    fun aRemovedItemComesBackWithUndo() {
        val editor = open()
        val panKey = editor.equipmentKey(2)
        assertTrue(editor.removeEquipment(1))
        assertEquals(listOf("Bowl", "Frying pan 26 cm"), editor.items)
        val removal = editor.removal.value!!
        assertEquals(Removal.Kind.EQUIPMENT, removal.kind)
        assertEquals(1, removal.position)
        assertEquals(listOf("Bowl", "Frying pan 26 cm").map(EquipmentItem::free), stored().equipment)
        assertTrue(editor.undoRemoval(removal))
        assertEquals(listOf("Bowl", "Whisk", "Frying pan 26 cm"), editor.items)
        assertEquals(2, editor.equipmentIndex(panKey))
        assertEquals(listOf("Bowl", "Whisk", "Frying pan 26 cm").map(EquipmentItem::free), stored().equipment)
    }

    @Test
    fun anItemMovesUpAndDown() {
        val editor = open()
        val bowl = editor.equipmentKey(0)
        assertTrue(editor.moveEquipment(0, 1))
        assertEquals(listOf("Whisk", "Bowl", "Frying pan 26 cm"), editor.items)
        assertEquals(1, editor.equipmentIndex(bowl))
        assertTrue(editor.moveEquipment(2, 1))
        assertEquals(listOf("Whisk", "Frying pan 26 cm", "Bowl"), editor.items)
        assertFalse(editor.moveEquipment(0, -1))
        assertFalse(editor.moveEquipment(2, 3))
        assertEquals(listOf("Whisk", "Frying pan 26 cm", "Bowl").map(EquipmentItem::free), stored().equipment)
    }

    // An item over 40 characters is kept and marked on leaving it; the mark stays with the item as others move.
    @Test
    fun aLongItemIsMarkedOnLeavingAndTheMarkFollowsIt() {
        val editor = open()
        val long = "Cast-iron frying pan with a lid, 28 cm wide"
        editor.setEquipment(2, long)
        assertTrue(editor.marks.value.isEmpty())
        editor.settleEquipment(2)
        editor.leave(RecipePart.EquipmentName(2))
        assertEquals(long, editor.items[2])
        assertEquals(mapOf(RecipePart.EquipmentName(2) to Bound.OverLimit(40, long.length)), editor.marks.value)
        editor.moveEquipment(2, 0)
        assertEquals(mapOf(RecipePart.EquipmentName(0) to Bound.OverLimit(40, long.length)), editor.marks.value)
        editor.setEquipment(0, "Cast-iron frying pan, 28 cm")
        assertTrue(editor.marks.value.isEmpty())
    }

    // An item an entry names stays that entry, its qualifier with it, when the author leaves it or moves it; typed into,
    // it becomes the text typed, free text, as every line the editor takes is until it offers entries (T-203).
    @Test
    fun anEntrysItemStaysUntilTypedInto() {
        val pan = EquipmentItem.entry(10001, "26 cm")
        val editor = open(seeded.copy(equipment = listOf(pan, EquipmentItem.free("Whisk"))))
        editor.settleEquipment(0)
        assertTrue(editor.moveEquipment(0, 1))
        assertEquals(listOf(EquipmentItem.free("Whisk"), pan), stored().equipment)
        assertTrue(editor.setEquipment(1, " Frying pan 28 cm"))
        editor.settleEquipment(1)
        assertEquals(listOf(EquipmentItem.free("Whisk"), EquipmentItem.free("Frying pan 28 cm")), stored().equipment)
    }

    @Test
    fun aFrozenRecipeTakesNoChangeToItsEquipment() {
        val frozen = seeded.inState(RecipeState.PUBLISHED)
        val editor = open(frozen)
        assertFalse(editor.addEquipment())
        assertFalse(editor.equipmentStatement.value)
        assertFalse(editor.setEquipment(0, "Pot"))
        assertFalse(editor.removeEquipment(0))
        assertFalse(editor.moveEquipment(0, 1))
        editor.settleEquipment(0)
        assertEquals(seeded.equipment.map { it.shown(null) }, editor.items)
    }
}
