package app.recipefork.app.card

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * How the card keeps its done marks (FLOW-068): a mark of a row changed since is dropped from the store from an effect
 * once the card is composed, never by writing while it is composed; and the device's store reads the one key asked for.
 */
@RunWith(AndroidJUnit4::class)
class DoneMarksStoreTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    private val recipe = Recipe.create(now, null).copy(
        title = "Tea",
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Tea", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(5)),
        ),
        steps = listOf(Step(0, "Brew."), Step(1, "Pour.")),
    )

    /** A store in memory that counts what is written to it. */
    private class Counted {
        val held = ConcurrentHashMap<String, String>()
        var writes = 0
        val store = DoneMarks(held::get) { key, value ->
            writes++
            if (value == null) held.remove(key) else held[key] = value
        }
    }

    // Reading the marks writes nothing: the stale one is not shown, and only dropStale — run from an effect — takes it
    // out of the store. This is the test that guards "from an effect": a write in the constructor fails it.
    @Test
    fun readingTheMarksWritesNothingAndTheStaleOneGoesFromAnEffect() {
        val counted = Counted()
        val stale = DoneMark.of(recipe.steps[1].copy(text = "Pour it out."))
        val kept = DoneMark.of(recipe.steps[0])
        counted.store.hold(BookName.Pool, recipe.id, setOf(stale, kept))
        counted.writes = 0

        val marks = CardMarks(counted.store, BookName.Pool, recipe)
        assertEquals(0, counted.writes)
        assertTrue(marks.done(MarkedPart.STEP, 0))
        assertFalse(marks.done(MarkedPart.STEP, 1))
        assertEquals(setOf(stale, kept), counted.store.held(BookName.Pool, recipe.id))

        marks.dropStale()
        assertEquals(setOf(kept), counted.store.held(BookName.Pool, recipe.id))
        // Nothing stale, nothing written.
        val writes = counted.writes
        CardMarks(counted.store, BookName.Pool, recipe).dropStale()
        assertEquals(writes, counted.writes)
    }

    // Composed, the card's marks are read during composition and the stale one leaves the store once it is composed.
    @Test
    fun theComposedCardDropsTheStaleMarkAfterComposition() {
        val counted = Counted()
        val stale = DoneMark.of(recipe.ingredients[0].copy(quantity = Rational.of(7)))
        val kept = DoneMark.of(recipe.steps[0])
        counted.store.hold(BookName.Pool, recipe.id, setOf(stale, kept))
        var any: Boolean? = null
        compose.setContent { any = rememberCardMarks(counted.store, BookName.Pool, recipe).any }
        compose.waitUntil(PATIENCE_MS) { counted.store.held(BookName.Pool, recipe.id) == setOf(kept) }
        assertEquals(true, any)
    }

    /**
     * The device's preferences file, as the store opens it, whose every read of the whole file — `getAll` — is
     * refused: a store that copied the whole file to read one key would fail here.
     */
    private class OneKeyOnly(base: Context) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val file = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by file {
                override fun getAll(): MutableMap<String, *> = throw AssertionError("the whole file was read for one key")
            }
        }
    }

    // The device's store reads its one key, never the whole file; a value of another kind under it reads as no marks.
    @Test
    fun theDevicesStoreReadsTheOneKeyAskedFor() {
        val store = DoneMarks.preferences(OneKeyOnly(context))
        val mark = DoneMark.of(recipe.steps[0])
        store.hold(BookName.Pool, recipe.id, setOf(mark))
        assertEquals(setOf(mark), DoneMarks.preferences(OneKeyOnly(context)).held(BookName.Pool, recipe.id))

        val other = Recipe.create(now, null)
        context.getSharedPreferences("done-marks", Context.MODE_PRIVATE).edit()
            .putInt("${BookName.Pool.fileName}/${other.id.value}", 3).commit()
        assertEquals(emptySet<DoneMark>(), store.held(BookName.Pool, other.id))
        assertEquals(setOf(mark), store.held(BookName.Pool, recipe.id))
        // Marking, clearing and reading the card's marks read the one key too.
        val marks = CardMarks(store, BookName.Pool, recipe)
        assertTrue(marks.done(MarkedPart.STEP, 0))
        marks.toggle(MarkedPart.STEP, 1)
        marks.dropStale()
        assertEquals(2, store.held(BookName.Pool, recipe.id).size)
        marks.clear()
        assertEquals(emptySet<DoneMark>(), store.held(BookName.Pool, recipe.id))
    }
}
