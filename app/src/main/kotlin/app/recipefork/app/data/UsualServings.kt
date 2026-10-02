package app.recipefork.app.data

import android.content.Context
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigInteger

/**
 * The serving count the reader usually cooks for (FLOW-017): one for the device, not per book, and kept across
 * restarts. Null while it is not set. Only a count a reader can request (SCALE-010, SCALE-011) is ever held.
 */
interface UsualServings {
    val count: StateFlow<Rational?>

    /** Sets the usual count to [count], or clears it with null. */
    fun set(count: Rational?)
}

/** The usual count held in memory alone, as long as the object lives. */
class HeldUsualServings(initial: Rational? = null) : UsualServings {
    private val held = MutableStateFlow(initial?.takeIf(RequestedServings::holds))
    override val count: StateFlow<Rational?> = held.asStateFlow()

    override fun set(count: Rational?) {
        held.value = count?.takeIf(RequestedServings::holds)
    }
}

/** The usual count kept in a small preferences file of the device. */
class PreferencesUsualServings(context: Context) : UsualServings {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val held = MutableStateFlow(parse(preferences.getString(KEY, null)))
    override val count: StateFlow<Rational?> = held.asStateFlow()

    override fun set(count: Rational?) {
        val kept = count?.takeIf(RequestedServings::holds)
        preferences.edit().apply { if (kept == null) remove(KEY) else putString(KEY, kept.toString()) }.commit()
        held.value = kept
    }

    private companion object {
        const val FILE = "reading"
        const val KEY = "usual-servings"

        /** A kept count, written as `n` or `n/d`; anything else reads as not set. */
        fun parse(text: String?): Rational? {
            val parts = text?.split('/') ?: return null
            return try {
                val numerator = BigInteger(parts[0])
                val denominator = if (parts.size == 2) BigInteger(parts[1]) else BigInteger.ONE
                if (parts.size > 2 || denominator.signum() == 0) null
                else Rational.of(numerator, denominator).takeIf(RequestedServings::holds)
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
