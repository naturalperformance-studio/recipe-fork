package app.recipefork.app.library

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The recipe book's screen as the reader sees it. Every text is asserted as it reads in the language in force —
 * English, and Russian under `@Config(qualifiers = "ru")` — and never against the string resource the screen
 * itself uses, so a resource that says the wrong thing fails here.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val database = book.database
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-09-29T12:00:00Z")

    /** What the reader reads on the screen, in one language. */
    private class Words(
        val draft: String,
        val pending: String,
        val rejected: String,
        val linkShared: String,
        val published: String,
        val hidden: String,
        val removed: String,
        val sentNever: String,
        val sent: String,
        val sentWithChanges: String,
        val showMarked: String,
        val showOwn: String,
        /** Any part of a waiting time or of the expectation MOD-005 states before submission. */
        val expectation: Regex,
        /** Any detail of why, how or by whom a recipe was hidden (ST-006). */
        val hidingDetail: Regex,
        /** Any word that speaks of the network, a connection, a failure or loading. */
        val network: List<String>,
    )

    private val english = Words(
        draft = "Draft",
        pending = "Awaiting review",
        rejected = "Rejected",
        linkShared = "Shared by private link",
        published = "Published",
        hidden = "Hidden",
        removed = "Removed",
        sentNever = "Not sent: kept on this device alone and lost with it",
        sent = "Sent",
        sentWithChanges = "Sent, with changes not sent",
        showMarked = "Show the recipes marked in this book",
        showOwn = "Show the recipes written in this book",
        expectation = Regex(
            """\p{N}|\b(one|two|three|four|five|six|seven|eight|nine|ten|fourteen|thirty)\b|""" +
                """day|week|hour|minute|within|up to|expect|until|usually|takes|soon""",
            RegexOption.IGNORE_CASE,
        ),
        hidingDetail = Regex(
            """\p{N}|\b(one|two|three|several|many)\b|report|reason|moderat|because|complain|categor|""" +
                """account|people|user|flag|review|why|rule""",
            RegexOption.IGNORE_CASE,
        ),
        network = listOf(
            "connect", "network", "internet", "offline", "online", "wi-fi", "could not", "couldn't", "can't",
            "unable", "failed", "error", "retry", "try again", "loading", "load more",
        ),
    )

    private val russian = Words(
        draft = "Черновик",
        pending = "Ждёт проверки",
        rejected = "Отклонён",
        linkShared = "Открыт по закрытой ссылке",
        published = "Опубликован",
        hidden = "Скрыт",
        removed = "Удалён",
        sentNever = "Не отправлен: хранится только на этом устройстве и пропадёт вместе с ним",
        sent = "Отправлен",
        sentWithChanges = "Отправлен, есть неотправленные изменения",
        showMarked = "Показать рецепты, отмеченные в этой книге",
        showOwn = "Показать рецепты, написанные в этой книге",
        expectation = Regex(
            """\p{N}|один|одна|два|две|три|четыр|пять|шесть|семь|восем|девять|десять|""" +
                """день|дня|дней|сутк|суток|недел|час|минут|течение|ожида|срок|обычно|занима|скоро""",
            RegexOption.IGNORE_CASE,
        ),
        hidingDetail = Regex(
            """\p{N}|один|два|три|нескольк|много|жалоб|пожалова|причин|модерат|потому|из-за|категор|""" +
                """аккаунт|пользовател|люд|провер|почему|правил""",
            RegexOption.IGNORE_CASE,
        ),
        network = listOf(
            "подключ", "соединен", "сеть", "сети", "интернет", "офлайн", "онлайн", "не удалось", "невозможно",
            "ошибк", "повтор", "загруз",
        ),
    )

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

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

    /**
     * Brings the entry tagged [tag] into view in whichever list is shown, scrolling to it. The own list reads
     * its next recipes as it is scrolled, so a scroll that stops at the end of what is loaded is tried again
     * once the book has answered: the rule waits on the book and the screen, and the time limit is only there
     * so that a missing entry fails rather than hangs.
     */
    private fun bringIntoView(tag: String) = compose.waitUntil(PATIENCE_MS) {
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

    /**
     * Everything the entry of [recipe] says, in the order the screen reader speaks it: its texts, then any
     * description or state description it carries.
     */
    private fun spoken(recipe: Recipe): List<String> {
        val tag = LibraryTags.entry(recipe.id.value)
        bringIntoView(tag)
        val config = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            listOfNotNull(config.getOrNull(SemanticsProperties.StateDescription))
    }

    // FLOW-034 (1)
    @Test
    fun bothViewsAreReachableAndTheSwitchIsVisibleWithoutScrolling() {
        repeat(40) { own("Recipe $it", RecipeState.LOCAL, minutesAgo = it.toLong()) }
        mark("Someone's pie")
        show()
        compose.onNodeWithTag(LibraryTags.SWITCH).assertIsDisplayed()
        bringIntoView(LibraryTags.entry("marked-Someone's pie"))
        compose.onNodeWithTag(LibraryTags.entry("marked-Someone's pie")).assertIsDisplayed()
        openOwn()
        bringIntoView(LibraryTags.entry(own("Newest", RecipeState.LOCAL, minutesAgo = -1).id.value))
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
        all.forEach { assertEquals("In ${it.state}", spoken(it).first()) }
    }

    // FLOW-034 (5): no account — the pool shows both views and the switch, what was marked and written in it.
    @Test
    fun withNoAccountThePoolShowsBothViews() {
        val written = own("Written with no account", RecipeState.LOCAL)
        mark("Marked with no account")
        show()
        compose.waitForIdle()
        compose.onNodeWithTag(LibraryTags.SWITCH).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsDisplayed()
        val marked = LibraryTags.entry("marked-Marked with no account")
        compose.waitUntilExactlyOneExists(hasTestTag(marked), PATIENCE_MS)
        compose.onNodeWithTag(marked).assertIsDisplayed().assertTextIs("Marked with no account")
        openOwn()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(written.id.value)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.entry(written.id.value)).assertIsDisplayed()
        assertEquals("Written with no account", spoken(written).first())
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
        // A book with a recipe written in it, so that the own view is its list (an empty one says so: FLOW-042).
        own("Soup", RecipeState.LOCAL)
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

    // FLOW-036 (1, 2, 4, 5, 6), FLOW-005 (1), A11Y-008 (3), in English.
    @Test
    fun eachEntryNamesItsStateInWordsAndCarriesItsSendingMarkBeside() = eachEntryNamesItsState(english)

    // The same, in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun eachEntryNamesItsStateInWordsAndCarriesItsSendingMarkBesideInRussian() = eachEntryNamesItsState(russian)

    private fun eachEntryNamesItsState(w: Words) {
        val local = own("Tomato soup", RecipeState.LOCAL, SentStatus.NEVER, 1)
        val private = own("Rye bread", RecipeState.PRIVATE, SentStatus.SENT_WITH_CHANGES, 2)
        val sentPrivate = own("Onion tart", RecipeState.PRIVATE, SentStatus.SENT, 3)
        val pending = own("Beetroot salad", RecipeState.PENDING, SentStatus.SENT, 4)
        val hidden = own("Plum cake", RecipeState.REPORTED_HIDDEN, SentStatus.SENT, 5)
        val removed = own("Lentil stew", RecipeState.REMOVED, SentStatus.SENT, 6)
        val rejected = own("Pea soup", RecipeState.REJECTED, SentStatus.SENT_WITH_CHANGES, 7)
        val linkShared = own("Apple pie", RecipeState.LINK_SHARED, SentStatus.SENT, 8)
        val published = own("Fish pie", RecipeState.PUBLISHED, SentStatus.SENT, 9)
        show()
        openOwn()

        // FLOW-036 (1), (6): local and private are drafts, and the sending mark stands beside the state —
        // never sent, sent, or holding changes not sent — without replacing it.
        assertEquals(listOf("Tomato soup", w.draft, w.sentNever), spoken(local))
        assertEquals(listOf("Rye bread", w.draft, w.sentWithChanges), spoken(private))
        assertEquals(listOf("Onion tart", w.draft, w.sent), spoken(sentPrivate))
        assertEquals(listOf("Pea soup", w.rejected, w.sentWithChanges), spoken(rejected))

        // FLOW-036 (2): pending says it awaits review, and no waiting time or expectation (MOD-005) is on it:
        // the entry holds its title, the state and the sending mark, and none of them names a time.
        val onPending = spoken(pending)
        assertEquals(listOf("Beetroot salad", w.pending, w.sent), onPending)
        onPending.forEach { assertNull("the pending entry states an expectation: \"$it\"", w.expectation.find(it)) }

        // FLOW-036 (4): hidden says so and nothing else about it — no reason, no count, no reports, no
        // moderator: the entry holds its title, the state and the sending mark only (ST-006).
        val onHidden = spoken(hidden)
        assertEquals(listOf("Plum cake", w.hidden, w.sent), onHidden)
        onHidden.forEach { assertNull("the hidden entry gives a detail: \"$it\"", w.hidingDetail.find(it)) }

        // FLOW-036 (5): removed stays in the view, marked as removed.
        assertEquals(listOf("Lentil stew", w.removed, w.sent), spoken(removed))
        assertEquals(listOf("Apple pie", w.linkShared, w.sent), spoken(linkShared))
        assertEquals(listOf("Fish pie", w.published, w.sent), spoken(published))

        val every = listOf(local, private, sentPrivate, pending, hidden, removed, rejected, linkShared, published)
        // FLOW-005 (1): each entry carries exactly one of the three marks.
        val marks = setOf(w.sentNever, w.sent, w.sentWithChanges)
        every.forEach { assertEquals(1, spoken(it).count { text -> text in marks }) }
        // A11Y-008 (3): each state is named in words on its entry, and states that differ are named differently;
        // local and private are both a draft.
        val named = every.associate { it.state to spoken(it)[1] }
        assertTrue(named.values.all { name -> name.any(Char::isLetter) })
        assertEquals(named.getValue(RecipeState.LOCAL), named.getValue(RecipeState.PRIVATE))
        assertEquals(7, named.filterKeys { it != RecipeState.PRIVATE }.values.toSet().size)
    }

    // FLOW-005 (2), in English.
    @Test
    fun theNeverSentMarkSaysTheRecipeIsOnThisDeviceAloneAndLostWithIt() = theNeverSentMark(english)

    // FLOW-005 (2), in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun theNeverSentMarkSaysTheRecipeIsOnThisDeviceAloneAndLostWithItInRussian() = theNeverSentMark(russian)

    private fun theNeverSentMark(w: Words) {
        val recipe = own("Only here", RecipeState.LOCAL, SentStatus.NEVER)
        show()
        openOwn()
        assertEquals(w.sentNever, spoken(recipe)[2])
    }

    private fun goOffline() {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(manager).setDefaultNetworkActive(false)
        @Suppress("DEPRECATION")
        shadowOf(manager).setActiveNetworkInfo(null)
    }

    /**
     * Scrolls the own list to its true end: past its last entry to the last item it holds of any kind, again
     * while reaching the end makes it read more, until it can scroll no further once the book has answered.
     */
    private fun scrollToTheEndOfTheOwnList() {
        val list = compose.onNodeWithTag(LibraryTags.OWN_LIST)
        fun range() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        repeat(200) {
            list.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, 100_000f) }
            compose.waitForIdle()
            val reached = range()
            if (reached.value() >= reached.maxValue()) {
                compose.waitForIdle()
                val after = range()
                if (after.maxValue() == reached.maxValue() && after.value() >= after.maxValue()) return
            }
        }
        fail("the own list never reached its end")
    }

    // FLOW-041 (5), in English: offline, scrolling to the end reads the following recipes from the device.
    @Test
    fun offlineScrollingLoadsTheFollowingOwnRecipesFromTheDevice() = offlineScrolling(english)

    // FLOW-041 (5), in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun offlineScrollingLoadsTheFollowingOwnRecipesFromTheDeviceInRussian() = offlineScrolling(russian)

    private fun offlineScrolling(w: Words) {
        goOffline()
        // Three full pages and part of a fourth, so the oldest recipe is read only by the fourth reading.
        val all = (0 until OWN_PAGE * 3 + 7).map { own("Recipe $it", RecipeState.LOCAL, minutesAgo = it.toLong()) }
        show()
        openOwn()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(all.first().id.value)), PATIENCE_MS)
        scrollToTheEndOfTheOwnList()
        // At the very end of the list, past its last entry, nothing states that a connection is needed.
        assertNoMentionOfTheNetwork(w)
        // And the oldest recipe, beyond the first pages, is in the list: the following recipes were read from
        // the device.
        compose.onNodeWithTag(LibraryTags.entry(all.last().id.value)).assertExists()
        assertEquals("Recipe ${all.size - 1}", spoken(all.last()).first())
        compose.onNodeWithTag(LibraryTags.entry(all.last().id.value)).assertIsDisplayed()
    }

    // FLOW-043 (5), in English: built from the device — no load failure, no waiting on the network.
    @Test
    fun theOwnViewOpensFromTheDeviceWithoutWaitingOrFailing() = opensFromTheDevice(english)

    // FLOW-043 (5), in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun theOwnViewOpensFromTheDeviceWithoutWaitingOrFailingInRussian() = opensFromTheDevice(russian)

    private fun opensFromTheDevice(w: Words) {
        goOffline()
        val recipe = own("Held", RecipeState.PUBLISHED, SentStatus.SENT)
        show()
        openOwn()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(recipe.id.value)), PATIENCE_MS)
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
            .assertCountEquals(0)
        assertNoMentionOfTheNetwork(w)
    }

    /** Nothing on screen — no text, no description — speaks of the network, a connection, a failure or loading. */
    private fun assertNoMentionOfTheNetwork(w: Words) {
        w.network.forEach {
            val found = compose.onAllNodes(
                hasText(it, substring = true, ignoreCase = true) or
                    hasContentDescription(it, substring = true, ignoreCase = true),
                useUnmergedTree = true,
            )
            assertEquals("something on screen says \"$it\"", 0, found.fetchSemanticsNodes().size)
        }
    }

    // A11Y-003 (1), A11Y-005 (1) on both views of the section, in English.
    @Test
    fun everyInteractiveElementIsLargeEnoughAndAnnouncesWhatItDoes() = largeEnoughAndAnnounced(english)

    // The same, in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun everyInteractiveElementIsLargeEnoughAndAnnouncesWhatItDoesInRussian() = largeEnoughAndAnnounced(russian)

    private fun largeEnoughAndAnnounced(w: Words) {
        own("Recipe", RecipeState.LOCAL)
        show()
        listOf(LibraryView.MARKED, LibraryView.OWN).forEach { view ->
            compose.onNodeWithTag(LibraryTags.tab(view)).performClick()
            compose.waitForIdle()
            AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 2)
            AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 2)
        }
        // Each tab announces what choosing it does — which recipes it shows — not what it looks like.
        mapOf(LibraryView.MARKED to w.showMarked, LibraryView.OWN to w.showOwn).forEach { (view, action) ->
            val node = compose.onNodeWithTag(LibraryTags.tab(view)).fetchSemanticsNode()
            assertEquals(action, AccessibilityChecks.clickActionLabel(node))
            assertNotEquals(action, AccessibilityChecks.announcedLabel(node))
        }
    }

    private fun SemanticsNodeInteraction.assertTextIs(text: String) = apply {
        assertEquals(listOf(text), fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
    }

    private fun SemanticsNodeInteractionCollection.assertCountEquals(count: Int) {
        assertEquals(count, fetchSemanticsNodes().size)
    }

    private companion object {
        /** How long a wait on a condition may take before it counts as failed: generous, never what passes. */
        const val PATIENCE_MS = 60_000L
    }
}
