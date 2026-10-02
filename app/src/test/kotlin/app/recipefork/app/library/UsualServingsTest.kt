package app.recipefork.app.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.data.PreferencesUsualServings
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The usual serving count is kept on the device across restarts (FLOW-017 (4)): one for the device, not per book,
 * and only ever a count a reader can request (SCALE-010, SCALE-011).
 */
@RunWith(AndroidJUnit4::class)
class UsualServingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // FLOW-017 (2): a device on which it was never set holds none.
    @Test
    fun neverSetItHoldsNone() {
        assertNull(PreferencesUsualServings(context).count.value)
    }

    // FLOW-017 (4): a count set is read back by the store the next start of the application opens.
    @Test
    fun aCountSetIsKeptAcrossRestarts() {
        PreferencesUsualServings(context).set(Rational.of(2))
        assertEquals(Rational.of(2), PreferencesUsualServings(context).count.value)
        PreferencesUsualServings(context).set(Rational.of(2, 3))
        assertEquals(Rational.of(2, 3), PreferencesUsualServings(context).count.value)
        PreferencesUsualServings(context).set(Rational.of(12))
        assertEquals(Rational.of(12), PreferencesUsualServings(context).count.value)
    }

    // "Not set" is kept as well: cleared, the next start holds none.
    @Test
    fun clearedItStaysCleared() {
        val store = PreferencesUsualServings(context)
        store.set(Rational.of(3))
        store.set(null)
        assertNull(store.count.value)
        assertNull(PreferencesUsualServings(context).count.value)
    }

    // SCALE-010, SCALE-011: a count no reader can request is never held.
    @Test
    fun onlyACountThatCanBeRequestedIsHeld() {
        val store = PreferencesUsualServings(context)
        store.set(Rational.of(2))
        listOf(Rational.of(1, 5), Rational.of(3, 2), Rational.ZERO, Rational.of(-2)).forEach {
            store.set(it)
            assertNull("$it", store.count.value)
        }
        assertNull(PreferencesUsualServings(context).count.value)
    }

    // One setting for the device: the application's container holds it, not a book.
    @Test
    fun theApplicationHoldsOneSettingForTheDevice() {
        val container = (context as RecipeForkApplication).container
        container.usualServings.set(Rational.of(5))
        assertEquals(Rational.of(5), PreferencesUsualServings(context).count.value)
    }
}
