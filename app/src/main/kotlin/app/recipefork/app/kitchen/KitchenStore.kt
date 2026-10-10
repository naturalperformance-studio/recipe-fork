package app.recipefork.app.kitchen

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The equipment the reader's kitchen has, as ticked on My kitchen: the ids of equipment dictionary entries, one set for
 * the device, kept across restarts. It is kept apart from the dictionary's store, which a schema change wipes and a
 * refresh rewrites, so neither ever loses it; an entry that later leaves the dictionary stays in the set, unlisted and
 * harmless ([Kitchen]).
 */
interface KitchenStore {
    /** The ids held, as they change. */
    val held: StateFlow<Set<Long>>

    /** Ticks every one of [ids] when [has], else unticks them; the others stay as they are. */
    fun hold(ids: Collection<Long>, has: Boolean)
}

/** The set held in memory alone, as long as the object lives. */
class HeldKitchen(initial: Set<Long> = emptySet()) : KitchenStore {
    private val state = MutableStateFlow(initial)
    override val held: StateFlow<Set<Long>> = state.asStateFlow()

    override fun hold(ids: Collection<Long>, has: Boolean) {
        state.value = if (has) state.value + ids else state.value - ids.toSet()
    }
}

/** The set kept in a small preferences file of the device, as the other device choices are. */
class PreferencesKitchen(context: Context) : KitchenStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    override val held: StateFlow<Set<Long>> = state.asStateFlow()

    // Whatever the file holds under the key that is no set of ids reads as none of them, never fails.
    private fun read(): Set<Long> =
        (preferences.all[KEY] as? Set<*>).orEmpty().mapNotNull { (it as? String)?.toLongOrNull() }.toSet()

    @Synchronized
    override fun hold(ids: Collection<Long>, has: Boolean) {
        val now = if (has) state.value + ids else state.value - ids.toSet()
        if (now == state.value) return
        preferences.edit().putStringSet(KEY, now.mapTo(HashSet()) { it.toString() }).commit()
        state.value = now
    }

    internal companion object {
        const val FILE = "kitchen"
        const val KEY = "held"
    }
}
