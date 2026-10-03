package app.recipefork.app.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.AppContainer
import app.recipefork.app.Mode
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.data.PreferencesDragHandleSide
import app.recipefork.app.data.PreferencesUsualServings
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The side of the editor's drag column is kept on the device across restarts, beside the usual serving count, and
 * whatever the file holds under its key that is not a boolean reads as off — the column on the left.
 */
@RunWith(AndroidJUnit4::class)
class DragHandleSideTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    @Test
    fun neverSetTheColumnIsOnTheLeft() {
        assertFalse(PreferencesDragHandleSide(context).right.value)
    }

    @Test
    fun theSideSetIsKeptAcrossRestarts() {
        PreferencesDragHandleSide(context).set(true)
        assertTrue(PreferencesDragHandleSide(context).right.value)
        PreferencesDragHandleSide(context).set(false)
        assertFalse(PreferencesDragHandleSide(context).right.value)
    }

    // Kept beside the usual serving count, in the same file, neither disturbing the other.
    @Test
    fun itIsKeptBesideTheUsualServingCount() {
        PreferencesUsualServings(context).set(Rational.of(3))
        PreferencesDragHandleSide(context).set(true)
        assertEquals(true, file.all[KEY])
        assertEquals(Rational.of(3), PreferencesUsualServings(context).count.value)
        PreferencesUsualServings(context).set(Rational.of(4))
        assertTrue(PreferencesDragHandleSide(context).right.value)
    }

    @Test
    fun aKeptValueOfAnotherTypeReadsAsOff() {
        file.edit().putString(KEY, "true").commit()
        assertFalse(PreferencesDragHandleSide(context).right.value)
        file.edit().putInt(KEY, 1).commit()
        assertFalse(PreferencesDragHandleSide(context).right.value)
    }

    @Test
    fun theContainerBuildsWhateverTheKeyHolds() {
        file.edit().putString(KEY, "yes").commit()
        val container = AppContainer(context, Mode.Serverless)
        try {
            assertFalse(container.dragHandleSide.right.value)
            container.dragHandleSide.set(true)
            assertTrue(PreferencesDragHandleSide(context).right.value)
        } finally {
            container.database.close()
        }
    }

    @Test
    fun theHeldSideIsTheLeftUntilSet() {
        val held = HeldDragHandleSide()
        assertFalse(held.right.value)
        held.set(true)
        assertTrue(held.right.value)
    }

    private companion object {
        const val KEY = "drag-handle-right"
    }
}
