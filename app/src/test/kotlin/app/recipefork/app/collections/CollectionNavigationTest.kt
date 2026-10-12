package app.recipefork.app.collections

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.ui.Shown
import app.recipefork.app.ui.shownFor
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.RecipeCollection
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.Rational
import app.recipefork.app.card.HeldScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Where a collection's screen lies (v0.36): above the book, beneath a card opened from it; kept through the end of the
 * process; shown no more once the settings, the shopping list, an import's book or a timer's card is shown in its place.
 * Its deletion is offered to undo until the book is left or another notice is said.
 */
@RunWith(AndroidJUnit4::class)
class CollectionNavigationTest {
    private val collection = RecipeCollection(CollectionId("c1"), "Menu", CollectionOrigin.OWN, null, Instant.EPOCH, emptyList())

    @Test
    fun aCollectionLiesAboveTheBookAndBeneathACardOpenedFromIt() {
        assertEquals(listOf(Shown.Book, Shown.Collection("c1")), shownFor(null, null, collection = "c1"))
        assertEquals(listOf(Shown.Book, Shown.Collection("c1"), Shown.Card("r1")), shownFor("r1", null, collection = "c1"))
        assertEquals(
            listOf(Shown.Book, Shown.Collection("c1"), Shown.Card("r1"), Shown.Editor("r1")),
            shownFor("r1", "r1", collection = "c1"),
        )
        assertEquals(
            listOf(Shown.Book, Shown.Collection("c1"), Shown.Card("r1"), Shown.Kitchen),
            shownFor("r1", null, kitchen = true, collection = "c1"),
        )
    }

    // T-257: the list opened from a collection's notice lies above it, and back from it returns there; nothing else
    // puts the list above a collection.
    @Test
    fun theShoppingListOpenedFromACollectionLiesAboveIt() {
        assertEquals(listOf(Shown.Book, Shown.Collection("c1"), Shown.Shopping), shownFor(null, null, shopping = true, collection = "c1"))
        assertEquals(listOf(Shown.Book, Shown.Collection("c1"), Shown.Card("r1")), shownFor("r1", null, shopping = true, collection = "c1"))
        val saved = SavedStateHandle()
        val session = LibrarySession(saved)
        session.openCollection("c1")
        session.openShoppingAboveCollection()
        assertTrue(session.shopping.value)
        assertEquals("c1", session.collection.value)
        // Kept through the end of the process.
        assertTrue(LibrarySession(saved).shopping.value)
        assertEquals("c1", LibrarySession(saved).collection.value)
        session.closeShopping()
        assertFalse(session.shopping.value)
        assertEquals("c1", session.collection.value)
        // With no collection open, it opens above the book.
        session.closeCollection()
        session.openShoppingAboveCollection()
        assertTrue(session.shopping.value)
        assertNull(session.collection.value)
    }

    // T-257: a whole collection's counts are read of the session without its cards being taken as shown: the cards most
    // recently shown — the ones kept when the session holds too many — stay as they were.
    @Test
    fun readingAHeldScaleForTheListLeavesTheCardsShownAsTheyWere() {
        val saved = SavedStateHandle()
        val session = LibrarySession(saved)
        session.cards.holdScale(RecipeId("a"), HeldScale(Rational.of(3)))
        session.cards.holdScale(RecipeId("b"), HeldScale(Rational.of(5)))
        assertEquals("b a", saved.get<String>("library.cards"))
        assertEquals(HeldScale(Rational.of(3)), session.heldScale(RecipeId("a")))
        assertEquals("b a", saved.get<String>("library.cards"))
        assertNull(session.heldScale(RecipeId("c")))
    }

    @Test
    fun theCollectionOpenIsKeptAndClosedBySettingsShoppingAndATimersCard() {
        val saved = SavedStateHandle()
        val session = LibrarySession(saved)
        session.openCollection("c1")
        assertEquals("c1", LibrarySession(saved).collection.value)
        session.openCard("r1")
        assertEquals("c1", session.collection.value)
        session.closeCard()
        assertEquals("c1", session.collection.value)

        session.openSettings()
        assertNull(session.collection.value)
        session.closeSettings()
        session.openCollection("c1")
        session.openShopping()
        assertNull(session.collection.value)
        session.closeShopping()
        session.openCollection("c1")
        session.showStep("r1", 0)
        assertNull(session.collection.value)
        session.openCollection("c1")
        session.showHeld()
        assertNull(session.collection.value)
    }

    @Test
    fun aDeletionIsOfferedUntilTheBookIsLeftAndUndoTakesItOnce() {
        val session = LibrarySession(SavedStateHandle())
        session.openCollection("c1")
        session.collectionDeleted(collection)
        assertNull(session.collection.value)
        assertEquals(collection, session.collectionDeletion.value)
        assertTrue(session.endCollectionDeletion(collection))
        assertFalse(session.endCollectionDeletion(collection))
        assertNull(session.collectionDeletion.value)

        session.collectionDeleted(collection)
        session.openCard("r1")
        assertNull(session.collectionDeletion.value)
        session.closeCard()
        session.collectionDeleted(collection)
        session.openCollection("c2")
        assertNull(session.collectionDeletion.value)
    }
}
