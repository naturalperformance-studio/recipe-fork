package app.recipefork.app.library

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-09-29T12:00:00Z")

    @After
    fun close() = database.close()

    private fun str(id: Int) = context.getString(id)

    /** Seeds the pool — the active book — with an own recipe, written in [state]. */
    private fun own(title: String, state: RecipeState, sent: SentStatus = SentStatus.NEVER, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, sent = sent).inState(state)
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun mark(title: String) = runBlocking {
        database.favourites().put(FavouriteEntity("marked-$title", title, now))
    }

    private fun show() = compose.setContent { MaterialTheme { LibraryScreen(session, recipes) } }

    private fun openOwn() {
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    /** Waits until the entry tagged [tag] is in the list on screen, scrolling the list to it. */
    private fun waitFor(tag: String) = compose.waitUntil(5_000) {
        val list = listOf(LibraryTags.OWN_LIST, LibraryTags.MARKED_LIST)
            .firstOrNull { compose.onAllNodes(hasTestTag(it)).fetchSemanticsNodes().isNotEmpty() }
            ?: return@waitUntil false
        try {
            compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
            true
        } catch (e: AssertionError) {
            false
        }
    }

    /** The texts of an entry, in the order the screen reader speaks them. */
    private fun entryTexts(recipe: Recipe): List<String> {
        val tag = LibraryTags.entry(recipe.id.value)
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    // FLOW-034 (1)
    @Test
    fun bothViewsAreReachableAndTheSwitchIsVisibleWithoutScrolling() {
        repeat(40) { own("Recipe $it", RecipeState.LOCAL, minutesAgo = it.toLong()) }
        mark("Someone's pie")
        show()
        compose.onNodeWithTag(LibraryTags.SWITCH).assertIsDisplayed()
        waitFor(LibraryTags.entry("marked-Someone's pie"))
        compose.onNodeWithTag(LibraryTags.entry("marked-Someone's pie")).assertIsDisplayed()
        openOwn()
        waitFor(LibraryTags.entry(own("Newest", RecipeState.LOCAL, minutesAgo = -1).id.value))
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasText("Recipe 39"))
        // Scrolled to the end of the list, the switch is still on screen.
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsDisplayed().assertIsSelected()
    }

    // FLOW-034 (3), FLOW-038: every state is listed, including those only the owner can see.
    @Test
    fun theOwnViewListsEveryRecipeInEveryState() {
        val all = RecipeState.entries.mapIndexed { i, state -> own("In $state", state, minutesAgo = i.toLong()) }
        show()
        openOwn()
        all.forEach {
            waitFor(LibraryTags.entry(it.id.value))
            assertEquals("In ${it.state}", entryTexts(it).first())
        }
    }

    // FLOW-034 (5): no account — the pool shows both views and the switch, what was marked and written in it.
    @Test
    fun withNoAccountThePoolShowsBothViews() {
        val written = own("Written with no account", RecipeState.LOCAL)
        mark("Marked with no account")
        show()
        compose.onNodeWithTag(LibraryTags.SWITCH).assertIsDisplayed()
        waitFor(LibraryTags.entry("marked-Marked with no account"))
        openOwn()
        waitFor(LibraryTags.entry(written.id.value))
    }

    // FLOW-034 (6): a session in which no view was chosen opens on the marked recipes.
    @Test
    fun aSessionWithNoChoiceOpensOnTheMarkedRecipes() {
        own("Own", RecipeState.PUBLISHED)
        show()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.MARKED_LIST).assertExists()
        compose.onNodeWithTag(LibraryTags.OWN_LIST).assertDoesNotExist()
    }

    // FLOW-034 (3 of the task: leaving the section and returning in one session keeps the view).
    @Test
    fun leavingTheSectionAndReturningKeepsTheView() {
        var inSection by mutableStateOf(true)
        compose.setContent { MaterialTheme { if (inSection) LibraryScreen(session, recipes) } }
        openOwn()
        inSection = false
        compose.waitForIdle()
        compose.onNodeWithTag(LibraryTags.SWITCH).assertDoesNotExist()
        inSection = true
        compose.waitForIdle()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.OWN_LIST).assertExists()
    }

    // FLOW-036 (1, 2, 4, 5), FLOW-036 (6), FLOW-005 (1), A11Y-008 (3)
    @Test
    fun eachEntryNamesItsStateInWordsAndCarriesItsSendingMarkBeside() {
        val local = own("Local", RecipeState.LOCAL, SentStatus.NEVER, 1)
        val private = own("Private", RecipeState.PRIVATE, SentStatus.SENT_WITH_CHANGES, 2)
        val sentPrivate = own("Sent private", RecipeState.PRIVATE, SentStatus.SENT, 3)
        val pending = own("Pending", RecipeState.PENDING, SentStatus.SENT, 4)
        val hidden = own("Hidden", RecipeState.REPORTED_HIDDEN, SentStatus.SENT, 5)
        val removed = own("Removed", RecipeState.REMOVED, SentStatus.SENT, 6)
        val rejected = own("Rejected", RecipeState.REJECTED, SentStatus.SENT, 7)
        val linkShared = own("Link", RecipeState.LINK_SHARED, SentStatus.SENT, 8)
        val published = own("Published", RecipeState.PUBLISHED, SentStatus.SENT, 9)
        show()
        openOwn()
        waitFor(LibraryTags.entry(local.id.value))
        val draft = str(R.string.library_state_draft)
        val never = str(R.string.library_sent_never)
        val withChanges = str(R.string.library_sent_with_changes)
        val sent = str(R.string.library_sent_sent)
        // Local and private are drafts, and the sending mark says whether it was sent; it does not replace the state.
        assertEquals(listOf("Local", draft, never), entryTexts(local))
        assertEquals(listOf("Private", draft, withChanges), entryTexts(private))
        assertEquals(listOf("Sent private", draft, sent), entryTexts(sentPrivate))
        // Pending says it awaits review and nothing more (no expectation of MOD-005).
        assertEquals(listOf("Pending", str(R.string.library_state_pending), sent), entryTexts(pending))
        // Hidden says so and gives no further detail (ST-006).
        assertEquals(listOf("Hidden", str(R.string.library_state_hidden), sent), entryTexts(hidden))
        // Removed stays in the view, marked as removed.
        assertEquals(listOf("Removed", str(R.string.library_state_removed), sent), entryTexts(removed))
        assertEquals(listOf("Rejected", str(R.string.library_state_rejected), sent), entryTexts(rejected))
        assertEquals(listOf("Link", str(R.string.library_state_link_shared), sent), entryTexts(linkShared))
        assertEquals(listOf("Published", str(R.string.library_state_published), sent), entryTexts(published))
        // FLOW-005 (1): each entry carries exactly one of the three marks.
        val marks = setOf(never, sent, withChanges)
        listOf(local, private, sentPrivate, pending, hidden, removed, rejected, linkShared, published).forEach {
            assertEquals(1, entryTexts(it).count { text -> text in marks })
        }
        // A11Y-008 (3): every one of the eight states has a name of its own in words, distinct where they differ.
        val names = RecipeState.entries.map { str(stateLabel(it)) }
        assertTrue(names.all { it.isNotBlank() })
        assertEquals(7, names.toSet().size) // local and private share "draft"
    }

    // FLOW-005 (2)
    @Test
    fun theNeverSentMarkSaysTheRecipeIsOnThisDeviceAloneAndLostWithIt() {
        val recipe = own("Only here", RecipeState.LOCAL, SentStatus.NEVER)
        show()
        openOwn()
        waitFor(LibraryTags.entry(recipe.id.value))
        assertEquals("Not sent: kept on this device alone and lost with it", entryTexts(recipe)[2])
    }

    private fun goOffline() {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(manager).setDefaultNetworkActive(false)
        @Suppress("DEPRECATION")
        shadowOf(manager).setActiveNetworkInfo(null)
    }

    // FLOW-041 (5): offline, scrolling to the end reads the following recipes from the device.
    @Test
    fun offlineScrollingLoadsTheFollowingOwnRecipesFromTheDevice() {
        goOffline()
        val all = (0 until OWN_PAGE * 4).map { own("Recipe $it", RecipeState.LOCAL, minutesAgo = it.toLong()) }
        show()
        openOwn()
        waitFor(LibraryTags.entry(all.first().id.value))
        // Beyond the first page: reached only if the next pages were read as the list was scrolled.
        compose.onNodeWithTag(LibraryTags.OWN_LIST)
            .performScrollToNode(hasTestTag(LibraryTags.entry(all.last().id.value)))
        compose.onNodeWithTag(LibraryTags.entry(all.last().id.value)).assertIsDisplayed()
        assertNoMentionOfTheNetwork()
    }

    // FLOW-043 (5): built from the device — no load failure, no waiting on the network.
    @Test
    fun theOwnViewOpensFromTheDeviceWithoutWaitingOrFailing() {
        goOffline()
        val recipe = own("Held", RecipeState.PUBLISHED, SentStatus.SENT)
        show()
        openOwn()
        waitFor(LibraryTags.entry(recipe.id.value))
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        assertNoMentionOfTheNetwork()
    }

    private fun assertNoMentionOfTheNetwork() {
        listOf("connection", "network", "offline", "could not", "failed", "retry", "loading").forEach {
            compose.onAllNodes(hasText(it, substring = true, ignoreCase = true), useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }

    // A11Y-003 (1), A11Y-005 (1) on both views of the section.
    @Test
    fun everyInteractiveElementIsLargeEnoughAndAnnouncesWhatItDoes() {
        own("Recipe", RecipeState.LOCAL)
        show()
        listOf(LibraryView.MARKED, LibraryView.OWN).forEach { view ->
            compose.onNodeWithTag(LibraryTags.tab(view)).performClick()
            compose.waitForIdle()
            AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 2)
            AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 2)
        }
        // Each tab says what choosing it does, not what it looks like.
        mapOf(
            LibraryView.MARKED to R.string.library_view_marked_action,
            LibraryView.OWN to R.string.library_view_own_action,
        ).forEach { (view, action) ->
            val node = compose.onNodeWithTag(LibraryTags.tab(view)).fetchSemanticsNode()
            assertEquals(str(action), AccessibilityChecks.clickActionLabel(node))
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(count: Int) {
    assertEquals(count, fetchSemanticsNodes().size)
}
