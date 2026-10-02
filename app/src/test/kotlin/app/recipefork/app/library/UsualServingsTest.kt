package app.recipefork.app.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.AppContainer
import app.recipefork.app.Mode
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.data.HeldUsualServings
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

    // SCALE-010, SCALE-011: a count no reader can request is never held, and setting one leaves the setting as it was.
    @Test
    fun aCountThatCannotBeRequestedLeavesTheSettingAsItWas() {
        val store = PreferencesUsualServings(context)
        store.set(Rational.of(2))
        listOf(Rational.of(1, 5), Rational.of(3, 2), Rational.ZERO, Rational.of(-2)).forEach {
            store.set(it)
            assertEquals("$it", Rational.of(2), store.count.value)
        }
        assertEquals(Rational.of(2), PreferencesUsualServings(context).count.value)
        val held = HeldUsualServings(Rational.of(3))
        held.set(Rational.of(1, 5))
        assertEquals(Rational.of(3), held.count.value)
    }

    private val file get() = context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    // Whatever the file holds that is no count a reader can request reads as not set, and never fails.
    @Test
    fun aKeptValueThatIsNoCountReadsAsNotSet() {
        listOf("", "abc", "1/0", "0", "-2", "1/5", "0.5", "1/2/3", "/", "3/2").forEach { text ->
            file.edit().putString(KEY, text).commit()
            assertNull(text, PreferencesUsualServings(context).count.value)
        }
    }

    // A value of another type under the key — an int, a boolean — reads as not set, and does not throw.
    @Test
    fun aKeptValueOfAnotherTypeReadsAsNotSet() {
        file.edit().putInt(KEY, 2).commit()
        assertNull(PreferencesUsualServings(context).count.value)
        file.edit().putBoolean(KEY, true).commit()
        assertNull(PreferencesUsualServings(context).count.value)
    }

    // The application's container, which builds the store as it starts, builds whatever the key holds.
    @Test
    fun theContainerBuildsWhateverTheKeyHolds() {
        file.edit().putInt(KEY, 2).commit()
        val container = AppContainer(context, Mode.Serverless)
        try {
            assertNull(container.usualServings.count.value)
            container.usualServings.set(Rational.of(2))
            assertEquals(Rational.of(2), PreferencesUsualServings(context).count.value)
        } finally {
            container.database.close()
        }
    }

    // One setting for the device: the application's container holds it, not a book.
    @Test
    fun theApplicationHoldsOneSettingForTheDevice() {
        val container = (context as RecipeForkApplication).container
        container.usualServings.set(Rational.of(5))
        assertEquals(Rational.of(5), PreferencesUsualServings(context).count.value)
    }

    private companion object {
        const val KEY = "usual-servings"
    }
}
