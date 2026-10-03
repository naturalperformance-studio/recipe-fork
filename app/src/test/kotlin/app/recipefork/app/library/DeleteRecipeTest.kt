package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.Connection
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
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
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.fork
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-053: an own recipe in `local`, `private` or `rejected` is deleted from its entry in the own-recipes view, after
 * a statement that it goes at once and cannot be brought back; declining changes nothing, confirming removes it with
 * everything it holds, and its timers with it. Nothing else offers deletion.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DeleteRecipeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    // The timers as the device wires them: a cancel withdraws the notice and the ringing (DeviceTimerAnnouncements).
    private val timers = TestTimers(context)
    private val notices = RecordedNotices()
    private val sound = RecordedSound()
    private val ringing = TimerRinging(timers.clock, sound, notices)
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

    private class Words(
        /** A recipe's title written in the language, which the statement's title quotes. */
        val recipe: String,
        val delete: String,
        val title: (String) -> String,
        val statement: String,
        val confirm: String,
        val decline: String,
        val empty: String,
        val write: String,
    )

    private val english = Words(
        recipe = "Soup",
        delete = "Delete",
        title = { "Delete “$it”?" },
        statement = "The recipe goes at once, with its photographs and video, and cannot be brought back.",
        confirm = "Delete",
        decline = "Cancel",
        empty = "Nothing has been written in this recipe book yet.",
        write = "Write a recipe",
    )

    private val russian = Words(
        recipe = "Суп",
        delete = "Удалить",
        title = { "Удалить «$it»?" },
        statement = "Рецепт удалится сразу, вместе с фотографиями и видео, и вернуть его будет нельзя.",
        confirm = "Удалить",
        decline = "Отмена",
        empty = "В этой книге рецептов пока ничего не написано.",
        write = "Написать рецепт",
    )

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
    }

    private fun own(
        title: String,
        state: RecipeState = RecipeState.LOCAL,
        minutesAgo: Long = 0,
        shape: (Recipe) -> Recipe = { it },
    ): Recipe {
        val sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT
        val recipe = shape(
            Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, sent = sent).inState(state),
        )
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun show() {
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                MaterialTheme { AppNavHost(session, recipes, editor = editor, card = card) }
            }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun bringIntoView(recipe: Recipe) {
        val entry = LibraryTags.entry(recipe.id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(entry))
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun askToDelete(recipe: Recipe) {
        bringIntoView(recipe)
        compose.chooseEntryAction(recipe.id.value, LibraryTags.delete(recipe.id.value))
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.DELETE_STATEMENT), PATIENCE_MS)
    }

    private fun confirm() {
        compose.onNodeWithTag(LibraryTags.DELETE_CONFIRM).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.DELETE_STATEMENT), PATIENCE_MS)
    }

    private fun stored(recipe: Recipe) = runBlocking { recipes.get(recipe.id) }

    private fun awaitGone(recipe: Recipe) {
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.entry(recipe.id.value)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { stored(recipe) == null }
    }

    /** How many rows each table of a recipe holds for [recipe], read from the book itself. */
    private fun rowsOf(recipe: Recipe): Map<String, Int> =
        listOf("recipe", "ingredient", "step", "step_reference", "media", "recipe_tag").associateWith { table ->
            book.database.openHelper.readableDatabase
                .query("select count(*) from $table where recipe_id = ?", arrayOf(recipe.id.value))
                .use { it.moveToFirst(); it.getInt(0) }
        }

    private fun ownRows() = runBlocking { book.database.recipes().newest(100).first() }

    // FLOW-053 (1), ST-010: deletion is offered, in the entry's menu, on the entries of local, private and rejected
    // recipes only; in standard mode any other entry has no action, so it carries no menu and reserves no space for
    // one — it takes the whole width of the list. No button stands under an entry.
    @Test
    fun deletionIsOfferedOnLocalPrivateAndRejectedEntriesAndNowhereElse() {
        val all = RecipeState.entries.mapIndexed { i, state -> own("Soup in $state", state, i.toLong()) }
        show()
        all.forEach { recipe ->
            bringIntoView(recipe)
            val entry = compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).fetchSemanticsNode().boundsInRoot
            // The room the title has: the card's width less its padding, and less the ⋮ when there is one.
            val room = titleRoom(recipe.id.value)
            val whole = entry.width - with(compose.density) { 32.dp.toPx() }
            assertFalse("${recipe.state} shows its offer outside a menu", exists(LibraryTags.delete(recipe.id.value)))
            if (recipe.state in setOf(RecipeState.LOCAL, RecipeState.PRIVATE, RecipeState.REJECTED)) {
                assertTrue("${recipe.state} has no menu", exists(LibraryTags.actions(recipe.id.value)))
                assertTrue("${recipe.state} keeps room for its menu", room < whole - 1f)
                compose.openEntryMenu(recipe.id.value)
                assertTrue("${recipe.state} offers no deletion", exists(LibraryTags.delete(recipe.id.value)))
                assertEquals(listOf("Delete"), compose.textsOf(LibraryTags.delete(recipe.id.value)))
                compose.closeEntryMenu(recipe.id.value)
            } else {
                assertFalse("${recipe.state} has a menu", exists(LibraryTags.actions(recipe.id.value)))
                assertEquals("${recipe.state} reserves space", whole, room, 1f)
            }
        }
        // A11Y-003, A11Y-005: the offer is large enough and says what it does.
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 3)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 3)
    }

    // FLOW-053 (7): the card of a recipe that may be deleted carries no deletion control, in the recipe or its bar.
    @Test
    fun theCardCarriesNoDeletionControl() {
        val recipe = own("Soup")
        show()
        bringIntoView(recipe)
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).performClick()
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.BAR).assertExists()
        val delete = hasText("Delete", substring = true, ignoreCase = true) or
            hasContentDescription("Delete", substring = true, ignoreCase = true)
        assertEquals(0, compose.onAllNodes(delete, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertFalse(exists(LibraryTags.delete(recipe.id.value)))
    }

    // FLOW-053 (2): before anything is destroyed, the statement says the recipe goes at once and cannot be brought
    // back; for a recipe in `local` no amount of any allowance is named.
    @Test
    fun theStatementSaysItGoesAtOnceAndCannotBeBroughtBack() = statement(english)

    @Test
    @Config(qualifiers = "ru")
    fun theStatementSaysItGoesAtOnceAndCannotBeBroughtBackInRussian() = statement(russian)

    private fun statement(w: Words) {
        val recipe = own(w.recipe)
        show()
        bringIntoView(recipe)
        compose.openEntryMenu(recipe.id.value)
        assertEquals(listOf(w.delete), compose.textsOf(LibraryTags.delete(recipe.id.value)))
        compose.onNodeWithTag(LibraryTags.delete(recipe.id.value)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.DELETE_STATEMENT), PATIENCE_MS)
        val inStatement = hasAnyAncestor(hasTestTag(LibraryTags.DELETE_STATEMENT))
        compose.onNode(hasText(w.title(w.recipe)) and inStatement).assertExists()
        compose.onNode(hasText(w.statement) and inStatement).assertExists()
        assertEquals(listOf(w.confirm), compose.textsOf(LibraryTags.DELETE_CONFIRM))
        assertEquals(listOf(w.decline), compose.textsOf(LibraryTags.DELETE_DECLINE))
        // No amount is named: nothing in the statement carries a figure.
        assertFalse(w.statement.any(Char::isDigit))
        assertFalse(w.title(w.recipe).any(Char::isDigit))
        // Shown before anything is destroyed.
        assertNotNull(stored(recipe))
        val content = setOf(w.recipe)
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 2)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 2)
    }

    // FLOW-053 (5): declining changes nothing — the entry stays and the recipe keeps its state and all it holds.
    @Test
    fun decliningChangesNothing() {
        val recipe = own("Soup", RecipeState.PRIVATE) { full(it) }
        val before = rowsOf(recipe)
        show()
        askToDelete(recipe)
        compose.onNodeWithTag(LibraryTags.DELETE_DECLINE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.DELETE_STATEMENT), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).assertExists()
        assertEquals(recipe, stored(recipe))
        assertEquals(before, rowsOf(recipe))
        // Asked again, the statement comes back: declining did not remember an answer.
        askToDelete(recipe)
    }

    // FLOW-053 (6): confirming removes the entry, and the recipe with everything it holds leaves the book; another
    // recipe is untouched.
    @Test
    fun confirmingRemovesTheEntryAndTheRecipeWithEverythingItHolds() {
        val kept = own("Bread", minutesAgo = 1) { full(it) }
        val recipe = own("Soup", RecipeState.REJECTED) { full(it) }
        assertTrue(rowsOf(recipe).values.all { it > 0 })
        show()
        askToDelete(recipe)
        confirm()
        awaitGone(recipe)
        assertEquals(rowsOf(recipe).keys.associateWith { 0 }, rowsOf(recipe))
        assertEquals(kept, stored(kept))
        assertEquals(listOf(kept.id.value), ownRows().map { it.recipeId })
        compose.onNodeWithTag(LibraryTags.entry(kept.id.value)).assertExists()
    }

    // FLOW-053 (6), FLOW-042: once the last recipe is deleted, the view says so and offers to write one.
    @Test
    fun deletingTheLastRecipeLeavesTheViewThatOffersToWriteOne() = lastRecipe(english)

    @Test
    @Config(qualifiers = "ru")
    fun deletingTheLastRecipeLeavesTheViewThatOffersToWriteOneInRussian() = lastRecipe(russian)

    private fun lastRecipe(w: Words) {
        val recipe = own("Soup")
        show()
        askToDelete(recipe)
        confirm()
        awaitGone(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_EMPTY), PATIENCE_MS)
        compose.onNode(hasText(w.empty) and hasAnyAncestor(hasTestTag(LibraryTags.OWN_EMPTY))).assertExists()
        assertEquals(listOf(w.write), compose.textsOf(LibraryTags.WRITE))
    }

    // FLOW-053 (8): a recipe in `local` is deleted with no connection.
    @Test
    fun aLocalRecipeIsDeletedWithNoConnection() {
        Connection.offline(context)
        assertTrue(Connection.isOffline(context))
        val recipe = own("Soup") { full(it) }
        show()
        askToDelete(recipe)
        confirm()
        awaitGone(recipe)
        assertEquals(rowsOf(recipe).keys.associateWith { 0 }, rowsOf(recipe))
    }

    // The deleted recipe's timers are cancelled: the ringing one falls silent and its notice is taken back, the
    // running one never announces itself; another recipe's timer runs on and is announced.
    @Test
    fun theDeletedRecipesTimersNeitherRingNorAnnounceAfterwards() {
        val soup = own("Soup") { full(it) }
        val bread = own("Bread", minutesAgo = 1)
        val boil = TimerKey(BookName.Pool, soup.id, 0)
        val simmer = TimerKey(BookName.Pool, soup.id, 1)
        val rise = TimerKey(BookName.Pool, bread.id, 0)
        runBlocking {
            store.start(boil, "Boil", "Soup", 10.minutes)
            store.start(simmer, "Simmer", "Soup", 1.hours)
            store.start(rise, "Rise", "Bread", 20.minutes)
            timers.clock.advance(10.minutes)
            announcer.announceEnded(store)
        }
        assertEquals(setOf(boil), ringing.ringing.value)
        assertTrue(sound.ringing)

        show()
        askToDelete(soup)
        confirm()
        awaitGone(soup)
        compose.waitUntil(PATIENCE_MS) { runBlocking { store.all.first() }.none { it.key.recipeId == soup.id.value } }

        assertEquals(emptySet<TimerKey>(), ringing.ringing.value)
        assertFalse(sound.ringing)
        assertTrue(notices.withdrawn.containsAll(listOf(boil, simmer)))

        runBlocking {
            timers.clock.advance(2.hours)
            announcer.announceEnded(store)
        }
        assertEquals(listOf(boil, rise), notices.announced.map { it.key })
        assertEquals(setOf(rise), ringing.ringing.value)
        assertEquals(listOf(rise), runBlocking { store.all.first() }.map { it.key })
    }

    // A fork whose parent was deleted still opens, and says its parent is not in this recipe book.
    @Test
    fun aForkWhoseParentWasDeletedStillOpensAndSaysItsParentIsNotInTheBook() {
        val parent = own("Borscht", minutesAgo = 1)
        val fork = parent.fork(now, owner = null).copy(title = "Green borscht")
        runBlocking { recipes.save(fork) }
        show()
        askToDelete(parent)
        confirm()
        awaitGone(parent)
        bringIntoView(fork)
        compose.onNodeWithTag(LibraryTags.entry(fork.id.value)).performClick()
        compose.awaitCard()
        assertEquals(listOf("Green borscht"), compose.textsOf(CardTags.TITLE))
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasTestTag(CardTags.PROVENANCE)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("Forked from a recipe that is not in this recipe book"), compose.textsOf(CardTags.PROVENANCE))
        assertEquals(parent.id, stored(fork)?.parent)
        assertNull(stored(parent))
    }

    /** [recipe] holding something in every part a recipe's rows live in. */
    private fun full(recipe: Recipe): Recipe = recipe.copy(
        servings = 2,
        tags = setOf(3, 7),
        ingredients = listOf(
            Ingredient(0, null, null, "beans", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200), null, null),
        ),
        steps = listOf(
            Step(0, "Boil the beans", listOf(IngredientReference(0, 9)), Timer(10.minutes)),
            Step(1, "Simmer", timer = Timer(1.hours)),
        ),
        media = listOf(
            Media("m-${recipe.id.value}", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE),
        ),
    )

    /** The width the title of [recipeId]'s entry is laid out in. */
    private fun titleRoom(recipeId: String): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(LibraryTags.entryTitle(recipeId), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single().layoutInput.constraints.maxWidth.toFloat()
    }
}
