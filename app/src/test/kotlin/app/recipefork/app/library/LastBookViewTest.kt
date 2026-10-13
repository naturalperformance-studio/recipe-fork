package app.recipefork.app.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.data.PreferencesLastBookView
import app.recipefork.core.recipe.RecipeId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scenario 8: the view of the book last shown is kept on the device, and a new session — a task started anew, with no
 * saved state — opens on it; the first one ever opens on the reader's own recipes, "My recipes" (v0.38). A session
 * restored from saved state shows the view it saved.
 */
@RunWith(AndroidJUnit4::class)
class LastBookViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A new session on this device: no saved state, the device's preferences read anew. */
    private fun newSession() = LibrarySession(SavedStateHandle(), PreferencesLastBookView(context))

    @Test
    fun theFirstSessionOpensOnTheOwnRecipes() {
        assertEquals(LibraryView.OWN, newSession().view.value)
    }

    // A view remembered still wins over the first one.
    @Test
    fun aRememberedViewWinsOverTheFirst() {
        newSession().choose(LibraryView.MARKED)
        assertEquals(LibraryView.MARKED, newSession().view.value)
    }

    @Test
    fun aNewSessionOpensOnTheViewLastShown() {
        newSession().choose(LibraryView.OWN)
        assertEquals(LibraryView.OWN, newSession().view.value)
        newSession().choose(LibraryView.MARKED)
        assertEquals(LibraryView.MARKED, newSession().view.value)
    }

    @Test
    fun aViewShownOtherwiseThanByChoosingIsKeptToo() {
        // Back from a card opened from the own recipes shows them.
        newSession().apply {
            openCard("a", LibraryView.OWN)
            closeCard()
        }
        assertEquals(LibraryView.OWN, newSession().view.value)
        // An imported recipe is shown among the marked ones.
        newSession().showImported(RecipeId("b"))
        assertEquals(LibraryView.MARKED, newSession().view.value)
    }

    @Test
    fun aRestoredSessionShowsTheViewItSaved() {
        newSession().choose(LibraryView.OWN)
        val restored = LibrarySession(SavedStateHandle(mapOf("library.view" to LibraryView.MARKED)), PreferencesLastBookView(context))
        assertEquals(LibraryView.MARKED, restored.view.value)
    }

    @Test
    fun whatTheDeviceHoldsThatNamesNoViewReadsAsNone() {
        context.getSharedPreferences(PreferencesLastBookView.FILE, Context.MODE_PRIVATE).edit()
            .putString(PreferencesLastBookView.KEY, "SOMETHING_ELSE").commit()
        assertEquals(LibraryView.OWN, newSession().view.value)
        context.getSharedPreferences(PreferencesLastBookView.FILE, Context.MODE_PRIVATE).edit()
            .putInt(PreferencesLastBookView.KEY, 3).commit()
        assertEquals(LibraryView.OWN, newSession().view.value)
    }
}
