package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.DoneMark
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerKey
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-053 as rewritten on 2026-10-04: a recipe deleted from the book, or a found one removed, goes at once and the
 * book offers Undo while its notice is shown. Undo brings it back whole — its content with ingredient groups and notes,
 * its photographs and clips, its place in the book, a found recipe's mark — but neither its done marks nor its timers,
 * ended as it left. Once the notice goes without Undo — by itself, swiped away, replaced by another notice, the book
 * left — the deletion is final and the files of its media are deleted.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RecipeUndoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val photos = PhotoStore(context.filesDir)
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val marks = DoneMarks.inMemory()
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    private val timers = TestTimers(context)
    private val store = timers.store
    private val cardTimers = CardTimers(store, timers.scope, timers.access, timers.ringing) { BookName.Pool }

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

    /** A media file held under [mediaId] — a photograph, or a clip — as the book's media lives on the device. */
    private fun media(mediaId: String, kind: MediaKind = MediaKind.PHOTO, attachment: MediaAttachment = MediaAttachment.Cover): Media {
        photos.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(-1, -40, -1, 1, 2, 3))
        return Media(mediaId, null, kind, attachment, MediaVisibility.PRIVATE)
    }

    /**
     * A recipe holding something in every part: ingredients falling into two groups, one carrying a note, steps with a
     * timer, a cover photograph and a step's clip.
     */
    private fun recipe(title: String, minutesAgo: Long = 0): Recipe {
        val key = title.lowercase().replace(' ', '-')
        return Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(
            title = title,
            servings = 2,
            tags = setOf(3),
            ingredients = listOf(
                Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200), null, null),
                Ingredient(1, null, null, "butter", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(50), null, null, note = "cold"),
                Ingredient(2, null, null, "apples", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(300), null, null),
            ),
            ingredientGroups = listOf(IngredientGroup("Dough", 0), IngredientGroup("Filling", 2)),
            steps = listOf(Step(0, "Rub the butter in.", timer = Timer(10.minutes)), Step(1, "Bake.", timer = Timer(40.minutes))),
            media = listOf(media("$key-cover"), media("$key-clip", MediaKind.VIDEO, MediaAttachment.ToStep(1))),
        )
    }

    private fun own(title: String, minutesAgo: Long = 0): Recipe =
        recipe(title, minutesAgo).also { runBlocking { recipes.save(it) } }

    private fun found(title: String, minutesAgo: Long): Recipe =
        recipe(title).inState(RecipeImport.FOUND_STATE).also {
            assertTrue(runBlocking { recipes.addFound(it, now.minusSeconds(minutesAgo * 60)) })
        }

    private fun show(view: LibraryView) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment(marks = marks) { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, editor = editor, card = card) }
            }
        }
        compose.waitForIdle()
    }

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    private fun awaitEntry(recipe: Recipe, view: LibraryView = LibraryView.OWN) {
        val entry = LibraryTags.entry(recipe.id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(list(view)).performScrollToNode(hasTestTag(entry))
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    /**
     * Deletes [recipe], or removes it when found, from its entry's menu; the book says so with Undo. Returns the test
     * clock's time once the notice showed.
     */
    private fun takeOut(recipe: Recipe, view: LibraryView = LibraryView.OWN): Long {
        awaitEntry(recipe, view)
        val item = if (view == LibraryView.OWN) LibraryTags.delete(recipe.id.value) else LibraryTags.remove(recipe.id.value)
        compose.chooseEntryAction(recipe.id.value, item)
        compose.awaitRemovalNotice(if (view == LibraryView.OWN) "Recipe deleted" else "Recipe removed", "Undo")
        val shown = compose.mainClock.currentTime
        compose.waitUntil(PATIENCE_MS) { stored(recipe) == null }
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.entry(recipe.id.value)), PATIENCE_MS)
        return shown
    }

    /**
     * Waits until the book's media directory holds exactly [expected], and asserts that it did well before the notice
     * shown at [noticeShown] could have gone by itself: the deletion was made final by what the test did, not by the
     * notice timing out while the test clock ran on.
     */
    private fun awaitFinalBeforeTimeout(expected: List<String>, noticeShown: Long) {
        awaitFiles(expected)
        val elapsed = compose.mainClock.currentTime - noticeShown
        assertTrue("final only after $elapsed ms, as the notice timed out", elapsed < WELL_BEFORE_TIMEOUT_MS)
    }

    private fun stored(recipe: Recipe) = runBlocking { recipes.get(recipe.id) }

    private fun files() = photos.directory(BookName.Pool).list().orEmpty().sorted()

    private fun filesOf(vararg recipes: Recipe) = recipes.flatMap { r -> r.media.map { it.mediaId } }.sorted()

    /**
     * Waits until the book's media directory holds exactly [expected]. The removal's work runs on the timers' scope, on
     * the main thread: its looper is let run meanwhile.
     */
    private fun awaitFiles(expected: List<String>) = compose.waitUntil(PATIENCE_MS) {
        ShadowLooper.idleMainLooper()
        files() == expected
    }

    private fun ownOrder() = runBlocking { book.database.recipes().newest(100).first() }.map { it.recipeId }

    private fun markedOrder() = runBlocking { recipes.marked().first() }.map { it.recipeId }

    private fun favourites() = runBlocking { book.database.favourites().all().first() }

    // Undo brings an own recipe back whole: content with its ingredient groups and notes, its cover and its clip on
    // the device, and its place among the book's own recipes.
    @Test
    fun undoBringsAnOwnRecipeBackWhole() {
        val older = own("Bread", minutesAgo = 2)
        val pie = own("Apple pie", minutesAgo = 1)
        val newer = own("Soup")
        val order = ownOrder()
        assertEquals(listOf(newer, pie, older).map { it.id.value }, order)
        show(LibraryView.OWN)
        takeOut(pie)
        // While Undo is offered, the recipe's media stays on the device.
        assertEquals(filesOf(older, pie, newer), files())

        compose.takeNoticeAction("Undo")

        compose.waitUntil(PATIENCE_MS) { stored(pie) != null }
        assertEquals(pie, stored(pie))
        assertEquals(listOf(IngredientGroup("Dough", 0), IngredientGroup("Filling", 2)), stored(pie)!!.ingredientGroups)
        assertEquals("cold", stored(pie)!!.ingredients[1].note)
        assertEquals(order, ownOrder())
        awaitEntry(pie)
        compose.waitForIdle()
        assertEquals(filesOf(older, pie, newer), files())
    }

    // Undo brings a found recipe back found — marked again, at the time it was marked, so listed where it was — with
    // its content and media.
    @Test
    fun undoBringsAFoundRecipeBackMarkedWhereItWas() {
        val older = found("Bread", minutesAgo = 2)
        val pie = found("Apple pie", minutesAgo = 1)
        val newer = found("Soup", minutesAgo = 0)
        val marked = favourites()
        assertEquals(listOf(newer, pie, older).map { it.id.value }, markedOrder())
        show(LibraryView.MARKED)
        takeOut(pie, LibraryView.MARKED)
        assertFalse(runBlocking { recipes.isMarked(pie.id) })

        compose.takeNoticeAction("Undo")

        compose.waitUntil(PATIENCE_MS) { stored(pie) != null }
        assertEquals(pie, stored(pie))
        assertTrue(runBlocking { recipes.isMarked(pie.id) })
        assertEquals(marked, favourites())
        assertEquals(listOf(newer, pie, older).map { it.id.value }, markedOrder())
        awaitEntry(pie, LibraryView.MARKED)
        compose.waitForIdle()
        assertEquals(filesOf(older, pie, newer), files())
    }

    // The deletion ended the recipe's timers and dropped its done marks; Undo restores neither.
    @Test
    fun undoRestartsNoTimerAndRestoresNoDoneMark() {
        val pie = own("Apple pie")
        val other = own("Soup", minutesAgo = 1)
        val rub = TimerKey(BookName.Pool, pie.id, 0)
        val simmer = TimerKey(BookName.Pool, other.id, 0)
        runBlocking {
            store.start(rub, "Rub", "Apple pie", 10.minutes)
            store.start(simmer, "Simmer", "Soup", 10.minutes)
        }
        marks.hold(BookName.Pool, pie.id, setOf(DoneMark.of(pie.steps.first()), DoneMark.of(pie.ingredients.first())))
        marks.hold(BookName.Pool, other.id, setOf(DoneMark.of(other.steps.first())))
        show(LibraryView.OWN)
        takeOut(pie)
        compose.waitUntil(PATIENCE_MS) { runBlocking { store.all.first() }.map { it.key } == listOf(simmer) }
        assertEquals(emptySet<DoneMark>(), marks.held(BookName.Pool, pie.id))

        compose.takeNoticeAction("Undo")

        compose.waitUntil(PATIENCE_MS) { stored(pie) != null }
        compose.waitForIdle()
        assertEquals(listOf(simmer), runBlocking { store.all.first() }.map { it.key })
        assertEquals(emptySet<DoneMark>(), marks.held(BookName.Pool, pie.id))
        assertEquals(1, marks.held(BookName.Pool, other.id).size)
    }

    // The notice going by itself makes the deletion final: the recipe's photograph and clip are deleted, another
    // recipe's stay, and the recipe does not come back.
    @Test
    fun theNoticeGoingByItselfMakesTheDeletionFinal() {
        val kept = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        takeOut(pie)
        assertEquals(filesOf(kept, pie), files())

        compose.mainClock.advanceTimeBy(LONG_NOTICE_MS)

        compose.waitUntil(PATIENCE_MS) { !compose.noticeShown() }
        awaitFiles(filesOf(kept))
        assertNull(stored(pie))
        assertEquals(kept, stored(kept))
    }

    // Swiped away, the notice makes the deletion final.
    @Test
    fun swipingTheNoticeAwayMakesTheDeletionFinal() {
        val kept = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)

        compose.swipeAwayNotice()

        awaitFinalBeforeTimeout(filesOf(kept), shown)
        assertNull(stored(pie))
    }

    // Another deletion's notice replaces the first, whose deletion is final then; Undo brings back the second alone.
    @Test
    fun anotherDeletionMakesTheFirstFinal() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)
        takeOut(soup)

        awaitFinalBeforeTimeout(filesOf(soup), shown)
        compose.takeNoticeAction("Undo")

        compose.waitUntil(PATIENCE_MS) { stored(soup) != null }
        assertEquals(soup, stored(soup))
        compose.waitForIdle()
        assertNull(stored(pie))
        assertEquals(filesOf(soup), files())
    }

    // A file's notice in the book's snackbar replaces the deletion's, which is final then.
    @Test
    fun aFilesNoticeMakesTheDeletionFinal() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)

        compose.runOnIdle {
            session.tell(FileMessage(FileMessage.Kind.ALREADY_IN_BOOK, soup.title, soup.id.value))
            assertNull("the removal is still offered", session.removal.value)
        }

        awaitFinalBeforeTimeout(filesOf(soup), shown)
        assertNull(stored(pie))
    }

    // Leaving the book for a card makes the deletion final; back in the book, nothing offers to undo it.
    @Test
    fun leavingTheBookForACardMakesTheDeletionFinal() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)

        compose.onNodeWithTag(LibraryTags.entry(soup.id.value)).performSemanticsAction(SemanticsActions.OnClick)
        compose.awaitCard()

        awaitFinalBeforeTimeout(filesOf(soup), shown)
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(soup.id.value)), PATIENCE_MS)
        compose.waitForIdle()
        assertFalse(compose.noticeShown())
        assertNull(stored(pie))
        assertNull(session.removal.value)
    }

    // Leaving the book for the settings makes the deletion final.
    @Test
    fun leavingTheBookForTheSettingsMakesTheDeletionFinal() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)

        // As a screen reader acts on it: the list brought to the entry may have scrolled the bar away.
        compose.onNodeWithTag(LibraryTags.SETTINGS).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasTestTag(SettingsTags.USUAL), PATIENCE_MS)

        awaitFinalBeforeTimeout(filesOf(soup), shown)
        assertNull(stored(pie))
    }

    // Leaving the book for the editor on a new recipe makes the deletion final.
    @Test
    fun leavingTheBookForANewRecipeMakesTheDeletionFinal() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)

        compose.onNodeWithTag(LibraryTags.NEW).performSemanticsAction(SemanticsActions.OnClick)
        // The new recipe is written, then opened, on the main thread: its looper is let run meanwhile.
        compose.waitUntil(PATIENCE_MS) {
            ShadowLooper.idleMainLooper()
            session.editing.value != null
        }

        assertNull("the removal is still offered", session.removal.value)
        awaitFinalBeforeTimeout(filesOf(soup), shown)
        assertNull(stored(pie))
    }

    // A timer's notification tapped opens its step's card in place of the book: the deletion is final.
    @Test
    fun aTimerNotificationsStepMakesTheDeletionFinal() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)

        compose.runOnIdle {
            session.showStep(soup.id.value, 1)
            assertNull("the removal is still offered", session.removal.value)
        }
        compose.awaitCard()

        awaitFinalBeforeTimeout(filesOf(soup), shown)
        assertNull(stored(pie))
    }

    // A file bringing a recipe the book holds shows the book in place of whatever was open: the deletion is final.
    @Test
    fun theBookShownForAFilesRecipeMakesTheDeletionFinal() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)

        compose.runOnIdle {
            session.showHeld()
            assertNull("the removal is still offered", session.removal.value)
        }

        awaitFinalBeforeTimeout(filesOf(soup), shown)
        assertNull(stored(pie))
    }

    // Undo the book cannot take back — another recipe holds one of its media items now — fails softly: the book says
    // so, the deletion is final, and the other recipe keeps everything it holds, its file included.
    @Test
    fun undoTheBookCannotTakeBackSaysSoAndChangesNothingElse() {
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        takeOut(pie)
        val cover = pie.media.first { it.attachment == MediaAttachment.Cover }
        val other = Recipe.create(now, owner = null).copy(title = "Pie again", media = listOf(cover))
        runBlocking { recipes.save(other) }

        // Undo taken; the notice that follows is the failure's, so nothing waits for the book's snackbar to empty.
        compose.onNode(hasText("Undo") and hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)), useUnmergedTree = true).performClick()

        // The Undo runs on the timers' scope, on the main thread: its looper is let run meanwhile.
        compose.waitUntil(PATIENCE_MS) {
            ShadowLooper.idleMainLooper()
            compose.onAllNodes(hasText("Couldn't bring the recipe back") and hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1
        }
        awaitFiles(listOf(cover.mediaId))
        assertNull(stored(pie))
        assertEquals(other, stored(other))
    }

    // The book's session ended — the application closed from the book — makes the deletion final.
    @Test
    fun theSessionEndingMakesTheDeletionFinal() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Apple pie")
        show(LibraryView.OWN)
        val shown = takeOut(pie)

        compose.runOnIdle {
            sessions.clear()
            assertNull("the removal is still offered", session.removal.value)
        }

        awaitFinalBeforeTimeout(filesOf(soup), shown)
        assertNull(stored(pie))
    }

    // The removal of a found recipe reads in Russian, its Undo too.
    @Test
    @Config(qualifiers = "ru")
    fun removingAFoundRecipeSaysSoInRussian() {
        val pie = found("Яблочный пирог", minutesAgo = 0)
        show(LibraryView.MARKED)
        awaitEntry(pie, LibraryView.MARKED)
        compose.chooseEntryAction(pie.id.value, LibraryTags.remove(pie.id.value))
        compose.awaitRemovalNotice("Рецепт убран", "Вернуть")
        compose.takeNoticeAction("Вернуть")
        compose.waitUntil(PATIENCE_MS) { stored(pie) != null }
        assertEquals(pie, stored(pie))
    }

    private companion object {
        /** Longer than a notice with an action stays by itself: Material's long duration, ten seconds. */
        const val LONG_NOTICE_MS = 11_000L

        /** Half the time a notice with an action stays: a deletion final by then did not wait for it to time out. */
        const val WELL_BEFORE_TIMEOUT_MS = 5_000L
    }
}
