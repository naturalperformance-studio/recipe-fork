package app.recipefork.app.library

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The session's part of the book's selection (T-261): starting, toggling, choosing all, forgetting the unlisted, ending. */
@RunWith(AndroidJUnit4::class)
class BookSelectionSessionTest {
    private val session = LibrarySession(SavedStateHandle()).also { it.choose(LibraryView.OWN) }

    private fun chosen() = session.selection.value

    @Test
    fun selectStartsTogglesAndEndsWhenNoneIsLeft() {
        session.select(LibraryView.OWN, "a")
        assertEquals(BookSelection(LibraryView.OWN, setOf("a")), chosen())
        session.select(LibraryView.OWN, "b")
        assertEquals(setOf("a", "b"), chosen()!!.ids)
        session.select(LibraryView.OWN, "a")
        assertEquals(setOf("b"), chosen()!!.ids)
        session.select(LibraryView.OWN, "b")
        assertNull(chosen())
    }

    @Test
    fun aLongPressInTheOtherViewStartsANewSelection() {
        session.select(LibraryView.OWN, "a")
        session.select(LibraryView.MARKED, "m")
        assertEquals(BookSelection(LibraryView.MARKED, setOf("m")), chosen())
    }

    @Test
    fun showingTheOtherViewEndsTheSelectionButShowingTheSameKeepsIt() {
        session.select(LibraryView.OWN, "a")
        session.choose(LibraryView.OWN)
        assertEquals(setOf("a"), chosen()!!.ids)
        session.choose(LibraryView.MARKED)
        assertNull(chosen())
    }

    @Test
    fun selectAllChoosesWhatIsListedInTheViewSelecting() {
        session.select(LibraryView.OWN, "a")
        session.selectAll(LibraryView.OWN, listOf("a", "b", "c"))
        assertEquals(setOf("a", "b", "c"), chosen()!!.ids)
        // Nothing listed chooses nothing; another view's list changes nothing.
        session.selectAll(LibraryView.OWN, emptyList())
        session.selectAll(LibraryView.MARKED, listOf("m"))
        assertEquals(BookSelection(LibraryView.OWN, setOf("a", "b", "c")), chosen())
    }

    @Test
    fun anEntryNoLongerListedIsChosenNoMore() {
        session.select(LibraryView.OWN, "a")
        session.select(LibraryView.OWN, "b")
        session.keepListed(LibraryView.MARKED, emptySet())
        assertEquals(setOf("a", "b"), chosen()!!.ids)
        session.keepListed(LibraryView.OWN, setOf("b", "c"))
        assertEquals(setOf("b"), chosen()!!.ids)
        session.keepListed(LibraryView.OWN, setOf("c"))
        assertNull(chosen())
    }

    @Test
    fun endSelectionEndsIt() {
        session.select(LibraryView.OWN, "a")
        session.endSelection()
        assertNull(chosen())
    }
}
