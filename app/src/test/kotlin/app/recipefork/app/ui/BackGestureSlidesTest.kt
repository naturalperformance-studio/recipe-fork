package app.recipefork.app.ui

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.CollectionScreenTags
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.shopping.ShoppingTags
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The system's back gesture from a destination above the book — a recipe's card, a collection, the editor, the
 * settings, the shopping list — moves the same slide as a back tapped: the screen above goes out to the right as the
 * finger moves, never shrinking, and the one beneath slides back in from where it was pushed aside. Cancelled, the
 * screen returns to its place; released, it slides on out as a tapped back does. On the book itself the host takes no
 * back, which stays the system's.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BackGestureSlidesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val card = CardEnvironment(ingredients = IngredientEntries { emptyList() })
    private val collections = CollectionRepository(active) { now }
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = emptyList<TagEntity>()
        },
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.mainClock.autoAdvance = true
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private lateinit var soup: Recipe

    private fun recipe(): Recipe = Recipe.create(now, owner = null).copy(title = "Soup").also { runBlocking { recipes.save(it) } }

    /** Shows the host on a book holding one recipe, [soup]: an empty book shows no list. */
    private fun show() {
        soup = recipe()
        compose.setContent {
            MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor, collections = collections) }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_LIST), PATIENCE_MS)
    }

    /** Opens what [open] tells the session to, and waits until only the screen tagged [tag] is shown. */
    private fun open(tag: String, beneath: String, open: () -> Unit) {
        compose.runOnUiThread(open)
        compose.waitUntilExactlyOneExists(hasTestTag(tag), PATIENCE_MS)
        compose.waitUntilDoesNotExist(hasTestTag(beneath), PATIENCE_MS)
    }

    private fun gesture(progress: Float) = BackEventCompat(progress * 300f, 400f, progress, BackEventCompat.EDGE_LEFT)

    private val dispatcher get() = compose.activity.onBackPressedDispatcher

    private fun width() = compose.onRoot().fetchSemanticsNode().boundsInRoot.width

    private fun boundsOf(tag: String): Rect? = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().singleOrNull()?.boundsInRoot

    private fun seek(progress: Float, beneath: String) {
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(gesture(progress)) }
        compose.waitUntilExactlyOneExists(hasTestTag(beneath), PATIENCE_MS)
        compose.waitForIdle()
    }

    /**
     * The screen [tag] stands at its full size and at the top — not shrunk about its centre — at the moment [what]
     * names; returns how far it is moved to the right.
     */
    private fun assertSlidNotShrunk(what: String, tag: String): Float {
        val bounds = requireNotNull(boundsOf(tag)) { "$what: the screen is shown" }
        assertEquals("$what: the screen keeps its width", width(), bounds.width, 0.5f)
        assertEquals("$what: the screen keeps its top", 0f, bounds.top, 0.5f)
        return bounds.left
    }

    /**
     * The gesture held over the screen [tag] moves it out to the right as the finger moves, the screen [beneath] sliding
     * in from the left; cancelled, the screen is back in place; released, it slides out without shrinking and the one
     * beneath is shown in place. [stillOpen] says whether the session still holds the screen.
     */
    private fun assertTheGestureSlides(tag: String, beneath: String, stillOpen: () -> Boolean) {
        assertEquals(0f, assertSlidNotShrunk("before the gesture", tag))
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(gesture(0f)) }
        seek(0.25f, beneath)
        val early = assertSlidNotShrunk("a quarter of the gesture", tag)
        val beneathEarly = requireNotNull(boundsOf(beneath)).left
        seek(0.75f, beneath)
        val late = assertSlidNotShrunk("three quarters of the gesture", tag)
        assertTrue("the screen moves right with the finger: $early, then $late", early > 0f && late > early && late < width())
        val beneathLate = requireNotNull(boundsOf(beneath)).left
        assertTrue("the screen beneath slides in from the left: $beneathEarly, then $beneathLate", beneathEarly < beneathLate && beneathLate < 0f)

        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitUntilDoesNotExist(hasTestTag(beneath), PATIENCE_MS)
        assertEquals("cancelled, the screen is back in place", 0f, assertSlidNotShrunk("cancelled", tag))
        assertTrue("cancelled, the screen is still open", stillOpen())

        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.5f))
        }
        compose.waitUntilExactlyOneExists(hasTestTag(beneath), PATIENCE_MS)
        compose.waitForIdle()
        assertSlidesOut("released", tag) { dispatcher.onBackPressed() }
        compose.waitUntilDoesNotExist(hasTestTag(tag), PATIENCE_MS)
        assertEquals("released, the screen beneath is in place", 0f, requireNotNull(boundsOf(beneath)).left)
        assertFalse("released, the screen is closed", stillOpen())
    }

    /** Frame by frame after [act], the screen [tag] keeps its size and moves only right until it is gone. */
    private fun assertSlidesOut(what: String, tag: String, act: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread(act)
            val lefts = mutableListOf<Float>()
            while (boundsOf(tag) != null && lefts.size < FRAMES) {
                lefts += assertSlidNotShrunk("$what, frame ${lefts.size}", tag)
                compose.mainClock.advanceTimeByFrame()
            }
            assertTrue("$what: the screen leaves within $FRAMES frames", lefts.size < FRAMES)
            assertTrue("$what: the screen moves right on its way out: $lefts", lefts.zipWithNext().all { (a, b) -> b >= a })
            assertTrue("$what: the screen is seen moving: $lefts", lefts.any { it > 0f && it < width() })
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun theGestureFromACardSlidesItOut() {
        show()
        open(CardTags.SCREEN, LibraryTags.OWN_LIST) { session.openCard(soup.id.value) }
        assertTheGestureSlides(CardTags.SCREEN, LibraryTags.OWN_LIST) { session.viewing.value != null }
    }

    // The card's own back action ends with the same slide as a released gesture.
    @Test
    fun theCardsBackActionSlidesItOutTheSameWay() {
        show()
        open(CardTags.SCREEN, LibraryTags.OWN_LIST) { session.openCard(soup.id.value) }
        assertSlidesOut("the back action", CardTags.SCREEN) { compose.onNodeWithTag(CardTags.BACK).performClick() }
        compose.waitUntilDoesNotExist(hasTestTag(CardTags.SCREEN), PATIENCE_MS)
        assertNull(session.viewing.value)
    }

    @Test
    fun theGestureFromTheEditorSlidesItOffTheCard() {
        show()
        open(CardTags.SCREEN, LibraryTags.OWN_LIST) { session.openCard(soup.id.value) }
        open(EditorTags.SCREEN, CardTags.SCREEN) { session.openEditor(soup.id.value) }
        assertTheGestureSlides(EditorTags.SCREEN, CardTags.SCREEN) { session.editing.value != null }
    }

    @Test
    fun theGestureFromACollectionSlidesItOut() {
        val id = runBlocking { requireNotNull(collections.create("Soups")) }
        show()
        open(CollectionScreenTags.SCREEN, LibraryTags.OWN_LIST) { session.openCollection(id.value) }
        assertTheGestureSlides(CollectionScreenTags.SCREEN, LibraryTags.OWN_LIST) { session.collection.value != null }
    }

    @Test
    fun theGestureFromTheSettingsSlidesThemOut() {
        show()
        open(SettingsTags.SCREEN, LibraryTags.OWN_LIST) { session.openSettings() }
        assertTheGestureSlides(SettingsTags.SCREEN, LibraryTags.OWN_LIST) { session.settings.value }
    }

    @Test
    fun theGestureFromTheShoppingListSlidesItOut() {
        show()
        open(ShoppingTags.SCREEN, LibraryTags.OWN_LIST) { session.openShopping() }
        assertTheGestureSlides(ShoppingTags.SCREEN, LibraryTags.OWN_LIST) { session.shopping.value }
    }

    // Nothing lies beneath the book: the host takes no back there, and the system's own back leaves the application.
    @Test
    fun onTheBookTheHostTakesNoBack() {
        show()
        compose.waitForIdle()
        assertFalse(dispatcher.hasEnabledCallbacks())
    }

    private companion object {
        /** More frames than a slide out takes, at any point of a gesture it starts from. */
        const val FRAMES = 60
    }
}
