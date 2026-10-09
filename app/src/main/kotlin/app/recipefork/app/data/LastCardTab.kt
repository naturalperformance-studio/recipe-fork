package app.recipefork.app.data

import android.content.Context
import app.recipefork.app.card.CardMode

/**
 * The card's tab last chosen — View or Cooking — one for the device and kept across restarts, so that a card opens on
 * the tab last chosen; null while none was ever chosen.
 */
interface LastCardTab {
    fun read(): CardMode?

    fun keep(tab: CardMode)
}

/** The tab held in memory alone, as long as the object lives. */
class HeldLastCardTab(private var held: CardMode? = null) : LastCardTab {
    override fun read(): CardMode? = held

    override fun keep(tab: CardMode) {
        held = tab
    }
}

/** The tab kept in the device's small preferences file, beside the book's view last shown. */
class PreferencesLastCardTab(context: Context) : LastCardTab {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Whatever the file holds under the key that names no tab reads as none, never fails.
    override fun read(): CardMode? = (preferences.all[KEY] as? String)?.let { name -> CardMode.entries.firstOrNull { it.name == name } }

    override fun keep(tab: CardMode) {
        if (preferences.getString(KEY, null) != tab.name) preferences.edit().putString(KEY, tab.name).apply()
    }

    internal companion object {
        const val FILE = PreferencesLastBookView.FILE
        const val KEY = "card-tab"
    }
}
