package app.recipefork.app.data

import android.content.Context
import app.recipefork.app.library.LibraryView

/**
 * The view of the recipe book last shown, one for the device and kept across restarts, so that the book opens on the
 * view last left; null while none was ever shown.
 */
interface LastBookView {
    fun read(): LibraryView?

    fun keep(view: LibraryView)
}

/** The view held in memory alone, as long as the object lives. */
class HeldLastBookView(private var held: LibraryView? = null) : LastBookView {
    override fun read(): LibraryView? = held

    override fun keep(view: LibraryView) {
        held = view
    }
}

/** The view kept in the device's small preferences file, beside the usual serving count. */
class PreferencesLastBookView(context: Context) : LastBookView {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Whatever the file holds under the key that names no view reads as none, never fails.
    override fun read(): LibraryView? = (preferences.all[KEY] as? String)?.let { name -> LibraryView.entries.firstOrNull { it.name == name } }

    override fun keep(view: LibraryView) {
        if (preferences.getString(KEY, null) != view.name) preferences.edit().putString(KEY, view.name).apply()
    }

    internal companion object {
        const val FILE = "reading"
        const val KEY = "book-view"
    }
}
