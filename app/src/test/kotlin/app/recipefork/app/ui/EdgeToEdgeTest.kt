package app.recipefork.app.ui

import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.card.CardTags
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.runBlocking
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The application is drawn edge to edge: the system bars' icons are dark on its light surface, on the oldest supported
 * release and on the releases that enforce edge to edge, and each screen keeps its own content clear of the bars — and
 * of the keyboard — while the window's surface runs under them.
 */
@RunWith(AndroidJUnit4::class)
class EdgeToEdgeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun assertDarkBarIcons() {
        compose.runOnUiThread {
            val window = compose.activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertTrue("status bar icons dark", controller.isAppearanceLightStatusBars)
            assertTrue("navigation bar icons dark", controller.isAppearanceLightNavigationBars)
        }
    }

    @Test
    @Config(sdk = [29])
    fun theBarsIconsAreDarkOnTheOldestRelease() = assertDarkBarIcons()

    @Test
    @Config(sdk = [36])
    fun theBarsIconsAreDarkWhereEdgeToEdgeIsEnforced() = assertDarkBarIcons()

    private fun applyInsets(top: Int, bottom: Int, ime: Int = 0) {
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime))
                .setVisible(WindowInsetsCompat.Type.ime(), ime > 0)
                .build()
            val decor: View = compose.activity.window.decorView
            decor.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private val session: LibrarySession get() = ViewModelProvider(compose.activity)[LibrarySession::class.java]

    /** The window's surface spans the whole window, under the bars: nothing pads it from outside. */
    private fun assertRootUnderTheBars(): Rect {
        val root = bounds(MainActivity.ROOT_TAG)
        val decor = compose.activity.window.decorView
        assertEquals(0f, root.top)
        assertEquals(decor.height.toFloat(), root.bottom)
        return root
    }

    /**
     * The book's top bar stands below the status bar; its list runs under the navigation bar, to the window's bottom,
     * its last entry kept clear of the bar by the list's own padding; its action stands above the navigation bar.
     */
    @Test
    fun theLibraryKeepsClearOfTheBars() {
        val container = (compose.activity.application as RecipeForkApplication).container
        val recipe = Recipe.create(Instant.parse("2026-10-04T12:00:00Z"), null).copy(title = "Soup")
        runBlocking { container.recipes.save(recipe) }
        compose.runOnUiThread { session.choose(LibraryView.OWN) }
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodesWithTag(LibraryTags.entry(recipe.id.value)).fetchSemanticsNodes().isNotEmpty()
        }
        applyInsets(top = STATUS, bottom = NAVIGATION)
        val root = assertRootUnderTheBars()
        assertTrue("title below the status bar", bounds(LibraryTags.TITLE).top >= STATUS)
        assertEquals("the list runs under the navigation bar", root.bottom, bounds(LibraryTags.OWN_LIST).bottom)
        assertTrue("the action above the navigation bar", bounds(LibraryTags.NEW).bottom <= root.bottom - NAVIGATION)
        // Scrolled to its end, the list's last entry stands clear of the navigation bar and of the action.
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        val entry = bounds(LibraryTags.entry(recipe.id.value))
        assertTrue("the entry above the navigation bar", entry.bottom <= root.bottom - NAVIGATION)
        assertTrue("the entry above the action", entry.bottom <= bounds(LibraryTags.NEW).top)
    }

    @Test
    fun theCardKeepsClearOfTheBars() {
        compose.runOnUiThread { session.openCard("no-such-recipe") }
        applyInsets(top = STATUS, bottom = NAVIGATION)
        val root = assertRootUnderTheBars()
        val card = bounds(CardTags.SCREEN)
        assertEquals(STATUS.toFloat(), card.top)
        assertEquals(root.bottom - NAVIGATION, card.bottom)
    }

    @Test
    fun theEditorKeepsClearOfTheBarsAndIsPushedUpByTheKeyboard() {
        compose.runOnUiThread { session.openEditor("no-such-recipe") }
        applyInsets(top = STATUS, bottom = NAVIGATION)
        val root = assertRootUnderTheBars()
        assertEquals(STATUS.toFloat(), bounds(EditorTags.SCREEN).top)
        assertEquals(root.bottom - NAVIGATION, bounds(EditorTags.SCREEN).bottom)

        applyInsets(top = STATUS, bottom = NAVIGATION, ime = KEYBOARD)
        assertEquals(root.bottom - KEYBOARD, bounds(EditorTags.SCREEN).bottom)
    }

    private companion object {
        const val STATUS = 60
        const val NAVIGATION = 90
        const val KEYBOARD = 400
    }
}
