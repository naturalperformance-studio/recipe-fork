package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.CardTarget
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.RecordedNotices
import app.recipefork.app.timer.RecordedSound
import app.recipefork.app.timer.RunningTimer
import app.recipefork.app.timer.RunningTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerAnnouncements
import app.recipefork.app.timer.TimerAnnouncer
import app.recipefork.app.timer.TimerKey
import app.recipefork.app.timer.TimerRinging
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Scenario 5 of v0.19: a recipe leaves the book from its card's ⋮ as from the book's entry — an own recipe in a
 * deletable state (ST-010) by Delete, a found one by Remove — after the same statement, whose confirm is marked as
 * destructive; confirmed, the recipe goes with its media and timers and the card closes to the book.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RemoveFromTheCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val photos = PhotoStore(context.filesDir)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-04T12:00:00Z")

    private val timers = TestTimers(context)
    private val notices = RecordedNotices()
    private val ringing = TimerRinging(timers.clock, RecordedSound(), notices)
    private val announcer = TimerAnnouncer(notices, ringing) { true }
    private val store = RunningTimers(
        timers.database.timers(),
        timers.clock,
        object : TimerAnnouncements {
            override suspend fun started(timer: RunningTimer) = announcer.withdraw(timer.key)
            override suspend fun cancelled(key: TimerKey) = announcer.withdraw(key)
        },
    )
    private val cardTimers = CardTimers(store, timers.scope, timers.access, ringing) { BookName.Pool }

    @Before
    fun setUp() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::registerIdlingResource)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        book.close()
        device.close()
        timers.close()
        photos.directory(BookName.Pool).deleteRecursively()
    }

    /** A photograph file held under [mediaId], as the book's media lives on the device. */
    private fun photo(mediaId: String): Media {
        photos.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(-1, -40, -1, 1, 2, 3))
        return Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.ToStep(0), MediaVisibility.PRIVATE)
    }

    private fun recipe(title: String, state: RecipeState = RecipeState.LOCAL, minutesAgo: Long = 0, media: List<Media> = emptyList()): Recipe =
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(
                title = title,
                sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT,
                steps = listOf(Step(0, "Boil.")),
                media = media,
            )
            .inState(state)

    private fun own(title: String, state: RecipeState = RecipeState.LOCAL, minutesAgo: Long = 0, media: List<Media> = emptyList()): Recipe =
        recipe(title, state, minutesAgo, media).also { runBlocking { recipes.save(it) } }

    private fun found(title: String, media: List<Media>): Recipe =
        recipe(title, RecipeImport.FOUND_STATE, 0, media).also { assertTrue(runBlocking { recipes.addFound(it, now) }) }

    /** The app with [recipe]'s card open, opened from [from]. */
    private fun showCard(recipe: Recipe, from: LibraryView = LibraryView.OWN) {
        session.openCard(recipe.id.value, from)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, editor = editor, card = card) }
            }
        }
        compose.awaitCard()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun stored(recipe: Recipe) = runBlocking { recipes.get(recipe.id) }

    private fun openMenu() {
        compose.onNodeWithTag(CardTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.DELETE) || exists(CardTags.REMOVE) }
    }

    /** The card has closed to the book, showing [view]. */
    private fun awaitBook(view: LibraryView) {
        compose.waitUntilDoesNotExist(hasTestTag(CardTags.SCREEN), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { session.view.value == view && exists(LibraryTags.tab(view)) }
    }

    // ST-010: the card's ⋮ offers Delete for an own recipe in local, private or rejected, the item the card's 56 dp
    // tall; a recipe in any other state has no ⋮ on its card, and the recipe itself carries no deletion control.
    @Test
    fun theCardsMenuOffersDeleteOnlyInADeletableState() {
        val all = RecipeState.entries.mapIndexed { i, state -> own("Soup in $state", state, i.toLong()) }
        showCard(all.first())
        all.forEach { recipe ->
            compose.runOnUiThread { session.openCard(recipe.id.value, LibraryView.OWN) }
            compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf(recipe.title) }
            compose.waitForIdle()
            val inContent = hasAnyAncestor(hasTestTag(CardTags.CONTENT))
            assertEquals(0, compose.onAllNodes(hasText("Delete") and inContent).fetchSemanticsNodes().size)
            if (recipe.state in DELETABLE) {
                assertTrue("${recipe.state} has no ⋮", exists(CardTags.MENU))
                compose.onNodeWithTag(CardTags.MENU).assertHeightIsAtLeast(CardTarget).assertWidthIsAtLeast(CardTarget)
                openMenu()
                assertEquals(listOf("Delete"), compose.textsOf(CardTags.DELETE))
                assertFalse(exists(CardTags.REMOVE))
                compose.onNodeWithTag(CardTags.DELETE).assertHeightIsAtLeast(CardTarget)
                compose.onNodeWithTag(CardTags.MENU).performClick()
                compose.waitUntilDoesNotExist(hasTestTag(CardTags.DELETE), PATIENCE_MS)
            } else {
                assertFalse("${recipe.state} has a ⋮", exists(CardTags.MENU))
            }
        }
    }

    // FLOW-053 (2, 6), MEDIA-012: Delete asks with the book's own statement, its confirm in the error colour;
    // confirming deletes the recipe with its media files and its timers, and the card closes to the book. Another
    // recipe and its photograph stay.
    @Test
    fun deletingFromTheCardDeletesTheRecipeWithItsMediaAndClosesToTheBook() {
        val kept = own("Bread", minutesAgo = 1, media = listOf(photo("bread-step")))
        val soup = own("Soup", RecipeState.PRIVATE, media = listOf(photo("soup-step")))
        val boil = TimerKey(BookName.Pool, soup.id, 0)
        runBlocking { store.start(boil, "Boil", "Soup", 10.minutes) }
        showCard(soup)
        openMenu()
        compose.onNodeWithTag(CardTags.DELETE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.DELETE_STATEMENT), PATIENCE_MS)
        val inStatement = hasAnyAncestor(hasTestTag(LibraryTags.DELETE_STATEMENT))
        compose.onNode(hasText("Delete “Soup”?") and inStatement).assertExists()
        compose.onNode(
            hasText("The recipe goes at once, with its photographs and video, and cannot be brought back.") and inStatement,
        ).assertExists()
        assertEquals(listOf("Delete"), compose.textsOf(LibraryTags.DELETE_CONFIRM))
        assertConfirmInErrorColour(compose, LibraryTags.DELETE_CONFIRM, LibraryTags.DELETE_DECLINE)
        compose.onNodeWithTag(LibraryTags.DELETE_CONFIRM).assertHeightIsAtLeast(CardTarget)
        assertNotNull(stored(soup))

        compose.onNodeWithTag(LibraryTags.DELETE_CONFIRM).performClick()
        awaitBook(LibraryView.OWN)
        compose.waitUntil(PATIENCE_MS) { stored(soup) == null }
        compose.waitUntil(PATIENCE_MS) { runBlocking { store.all.first() }.isEmpty() }
        compose.waitUntil(PATIENCE_MS) { photos.directory(BookName.Pool).list()!!.sorted() == listOf("bread-step") }
        assertEquals(kept, stored(kept))
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.entry(soup.id.value)), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(kept.id.value)), PATIENCE_MS)
    }

    // FLOW-053 (5): declining the statement from the card changes nothing and leaves the card open.
    @Test
    fun decliningOnTheCardChangesNothing() {
        val soup = own("Soup", media = listOf(photo("soup-step")))
        showCard(soup)
        openMenu()
        compose.onNodeWithTag(CardTags.DELETE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.DELETE_STATEMENT), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.DELETE_DECLINE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.DELETE_STATEMENT), PATIENCE_MS)
        compose.onNodeWithTag(CardTags.CONTENT).assertExists()
        assertEquals(soup, stored(soup))
        assertEquals(listOf("soup-step"), photos.directory(BookName.Pool).list()!!.toList())
    }

    // FLOW-009 (4), FLOW-064 (6): a found recipe's card offers Remove, never Delete; confirming the book's statement,
    // its confirm in the error colour, removes it with its media and the card closes to the marked recipes.
    @Test
    fun removingAFoundRecipeFromTheCardClosesToTheMarkedRecipes() {
        val recipe = found("Found soup", listOf(photo("found-step")))
        showCard(recipe, LibraryView.MARKED)
        assertFalse(exists(CardTags.EDIT))
        openMenu()
        assertEquals(listOf("Remove"), compose.textsOf(CardTags.REMOVE))
        assertFalse(exists(CardTags.DELETE))
        compose.onNodeWithTag(CardTags.REMOVE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.REMOVE_STATEMENT), PATIENCE_MS)
        val inStatement = hasAnyAncestor(hasTestTag(LibraryTags.REMOVE_STATEMENT))
        compose.onNode(hasText("Remove “Found soup” from your book?") and inStatement).assertExists()
        assertEquals(listOf("Remove"), compose.textsOf(LibraryTags.REMOVE_CONFIRM))
        assertConfirmInErrorColour(compose, LibraryTags.REMOVE_CONFIRM, LibraryTags.REMOVE_DECLINE)

        compose.onNodeWithTag(LibraryTags.REMOVE_CONFIRM).performClick()
        awaitBook(LibraryView.MARKED)
        compose.waitUntil(PATIENCE_MS) { stored(recipe) == null }
        compose.waitUntil(PATIENCE_MS) { photos.directory(BookName.Pool).list().isNullOrEmpty() }
        assertTrue(runBlocking { recipes.marked().first() }.isEmpty())
    }

    // L10N: the ⋮ is named, and its items read, in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun theCardsMenuReadsInRussian() {
        val soup = own("Суп")
        showCard(soup)
        compose.onNode(hasTestTag(CardTags.MENU) and hasContentDescription("Другие действия"))
            .assertExists()
        openMenu()
        assertEquals(listOf("Удалить"), compose.textsOf(CardTags.DELETE))
        compose.onNodeWithTag(CardTags.DELETE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.DELETE_STATEMENT), PATIENCE_MS)
        assertEquals(listOf("Удалить"), compose.textsOf(LibraryTags.DELETE_CONFIRM))
        assertEquals(listOf("Отмена"), compose.textsOf(LibraryTags.DELETE_DECLINE))
    }
}
