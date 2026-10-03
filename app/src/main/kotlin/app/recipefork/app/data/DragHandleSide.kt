package app.recipefork.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which side of an editable step's or ingredient's block its drag column stands on: the left, as it always has, or
 * the right, for a left hand. One for the device, kept across restarts.
 */
interface DragHandleSide {
    /** True while the column stands on the right. */
    val right: StateFlow<Boolean>

    fun set(right: Boolean)
}

/** The side held in memory alone, as long as the object lives. */
class HeldDragHandleSide(initial: Boolean = false) : DragHandleSide {
    private val held = MutableStateFlow(initial)
    override val right: StateFlow<Boolean> = held.asStateFlow()

    override fun set(right: Boolean) {
        held.value = right
    }
}

/** The side kept in the device's small preferences file, beside the usual serving count. */
class PreferencesDragHandleSide(context: Context) : DragHandleSide {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    // Whatever the file holds under the key that is not a boolean reads as the left, never fails.
    private val held = MutableStateFlow(preferences.all[KEY] as? Boolean ?: false)
    override val right: StateFlow<Boolean> = held.asStateFlow()

    override fun set(right: Boolean) {
        preferences.edit().putBoolean(KEY, right).commit()
        held.value = right
    }

    internal companion object {
        const val FILE = "reading"
        const val KEY = "drag-handle-right"
    }
}
