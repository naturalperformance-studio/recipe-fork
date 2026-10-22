package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.DoneMark
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.CollectionScreenTags
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerKey
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * The book's selection (v0.37, T-261): a long press on an entry of either view starts it with that entry chosen; taps
 * then choose and take off entries without opening them; the bar shows ✕, "N selected" and "Select all", which chooses
 * every entry the view lists under its search and filters, and the actions stand at the bottom (v0.42, T-305); ✕, Back or an action taken ends it,
 * and while it lasts the other view is not shown (v0.38). Delete (own) and Remove (imported) take every chosen recipe they apply to out of the book at once,
 * asking nothing, with one notice and one Undo, which brings them all back with their places in collections.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BookSelectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val collections = CollectionRepository(active)
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val marks = DoneMarks.inMemory()
    private val photos = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-13T12:00:00Z")

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
        sessions.clear()
        book.close()
        device.close()
        timers.close()
        photos.directory(BookName.Pool).deleteRecursively()
    }

    private fun recipe(title: String, minutesAgo: Long, language: RecipeLanguage? = RecipeLanguage.EN) =
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(
            title = title,
            language = language,
            steps = listOf(Step(0, "Cook.", timer = Timer(10.minutes))),
        )

    private fun own(title: String, minutesAgo: Long = 0, language: RecipeLanguage? = RecipeLanguage.EN, state: RecipeState? = null): Recipe {
        val made = recipe(title, minutesAgo, language).let { if (state == null) it else it.inState(state) }
        return made.also { runBlocking { recipes.save(it) } }
    }

    private fun found(title: String, minutesAgo: Long = 0): Recipe =
        recipe(title, minutesAgo).inState(RecipeImport.FOUND_STATE).also {
            assertTrue(runBlocking { recipes.addFound(it, now.minusSeconds(minutesAgo * 60)) })
        }

    /** A recipe marked in the book whose content the book does not hold: it opens nothing and cannot be removed. */
    private fun markedOnly(id: String, title: String) =
        runBlocking { book.database.favourites().put(FavouriteEntity(id, title, now)) }

    private fun collection(name: String, vararg members: Recipe): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it.id, listOf(id)) } }
    }

    private fun members(id: CollectionId): List<RecipeId> = runBlocking { collections.get(id)!!.recipes }

    private fun show(view: LibraryView, mode: Mode = Mode.Standard) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment(marks = marks) { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, mode, editor = editor, card = card, collections = collections) }
            }
        }
        compose.waitForIdle()
    }

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun awaitEntry(id: String, view: LibraryView) = compose.waitUntil(PATIENCE_MS) {
        try {
            compose.onNodeWithTag(list(view)).performScrollToNode(hasTestTag(LibraryTags.entry(id)))
            true
        } catch (e: AssertionError) {
            false
        }
    }

    private fun longPress(recipe: Recipe, view: LibraryView = LibraryView.OWN) = longPress(recipe.id.value, view)

    private fun longPress(id: String, view: LibraryView) {
        awaitEntry(id, view)
        compose.onNodeWithTag(LibraryTags.entry(id)).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_BAR) }
    }

    private fun tap(recipe: Recipe, view: LibraryView = LibraryView.OWN) {
        awaitEntry(recipe.id.value, view)
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).performClick()
        compose.waitForIdle()
    }

    /** What the selection's bar counts, as it reads. */
    private fun count(): String = compose.textsOf(LibraryTags.SELECTION_COUNT).joinToString("").replace(' ', ' ')

    private fun awaitCount(text: String) = compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_COUNT) && count() == text }

    private fun awaitNoSelection() = compose.waitUntil(PATIENCE_MS) {
        !exists(LibraryTags.SELECTION_BAR) && exists(LibraryTags.TITLE) && session.selection.value == null
    }

    /**
     * Whether the entry of [recipe] is drawn and announced as chosen: the screen reader hears its state, and its mark is
     * there in place of its ⋮.
     */
    private fun assertChosen(recipe: Recipe, chosen: Boolean, selected: String = "Selected", notSelected: String = "Not selected") {
        awaitEntry(recipe.id.value, session.view.value)
        val config = compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).fetchSemanticsNode().config
        assertEquals("${recipe.title} selected", chosen, config.getOrNull(SemanticsProperties.Selected))
        assertEquals("${recipe.title} announced", if (chosen) selected else notSelected, config.getOrNull(SemanticsProperties.StateDescription))
        assertTrue("${recipe.title}'s mark", exists(LibraryTags.selectionMark(recipe.id.value)))
        assertFalse("${recipe.title}'s ⋮ while selecting", exists(LibraryTags.actions(recipe.id.value)))
    }

    /** The entry of [recipe] is out of any selection: it shows its ⋮ and no mark, and announces no chosen state. */
    private fun assertChosenNot(recipe: Recipe) {
        awaitEntry(recipe.id.value, session.view.value)
        val config = compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).fetchSemanticsNode().config
        assertNull(config.getOrNull(SemanticsProperties.Selected))
        assertFalse(exists(LibraryTags.selectionMark(recipe.id.value)))
    }

    /** Takes the selection's action tagged [tag]: "Select all" in its bar, or one of its actions at the bottom. */
    private fun take(tag: String) {
        compose.waitUntil(PATIENCE_MS) { exists(tag) }
        compose.onNodeWithTag(tag).performClick()
    }

    /** What the action tagged [tag] reads. */
    private fun itemText(tag: String): String = compose.textsOf(tag).joinToString("")

    private fun stored(recipe: Recipe) = runBlocking { recipes.get(recipe.id) }

    private fun pressSystemBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    // --- Entering, toggling, leaving ----------------------------------------------------------------------------------

    // A long press starts the selection with its entry chosen; taps then choose and take off entries without opening
    // them, each drawn and announced as it is; the last taken off ends the selection.
    @Test
    fun aLongPressStartsTheSelectionAndTapsToggleEntries() {
        val soup = own("Soup", minutesAgo = 2)
        val pie = own("Pie", minutesAgo = 1)
        val bread = own("Bread")
        show(LibraryView.OWN)

        longPress(soup)

        awaitCount("1 selected")
        assertChosen(soup, true)
        assertChosen(pie, false)
        assertChosen(bread, false)
        tap(pie)
        awaitCount("2 selected")
        assertChosen(pie, true)
        assertNull("a tap opened a card", session.viewing.value)
        tap(soup)
        awaitCount("1 selected")
        assertChosen(soup, false)
        assertEquals(setOf(pie.id.value), session.selection.value!!.ids)
        tap(pie)
        awaitNoSelection()
        assertNull(session.viewing.value)
        // Out of the selection, an entry opens again and shows its ⋮.
        assertTrue(exists(LibraryTags.actions(soup.id.value)))
        assertFalse(exists(LibraryTags.selectionMark(soup.id.value)))
    }

    @Test
    fun theCrossEndsTheSelection() {
        val soup = own("Soup")
        show(LibraryView.OWN)
        longPress(soup)
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        awaitNoSelection()
        assertNull(session.viewing.value)
    }

    @Test
    fun backEndsTheSelectionAndStaysInTheBook() {
        val soup = own("Soup")
        val pie = own("Pie", minutesAgo = 1)
        show(LibraryView.OWN)
        longPress(soup)
        tap(pie)
        awaitCount("2 selected")
        pressSystemBack()
        awaitNoSelection()
        assertFalse(compose.activity.isFinishing)
        assertTrue(exists(LibraryTags.entry(soup.id.value)))
    }

    // v0.38: while selecting the view does not change — the other tab does not respond, and the selection holds.
    @Test
    fun theOtherTabNeitherShowsItsViewNorEndsTheSelection() {
        val soup = own("Soup")
        found("Borscht")
        show(LibraryView.OWN)
        longPress(soup)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitForIdle()
        assertEquals(LibraryView.OWN, session.view.value)
        awaitCount("1 selected")
        assertChosen(soup, true)
    }

    // The same in the view of imported recipes, a recipe whose content the book does not hold chosen as well.
    @Test
    fun theSelectionWorksTheSameAmongTheImported() {
        val borscht = found("Borscht", minutesAgo = 1)
        val pelmeni = found("Pelmeni")
        markedOnly("server-1", "Shchi")
        show(LibraryView.MARKED)

        longPress(borscht, LibraryView.MARKED)
        awaitCount("1 selected")
        assertChosen(borscht, true)
        tap(pelmeni, LibraryView.MARKED)
        awaitCount("2 selected")
        awaitEntry("server-1", LibraryView.MARKED)
        compose.onNodeWithTag(LibraryTags.entry("server-1")).performClick()
        awaitCount("3 selected")
        assertNull(session.viewing.value)
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        awaitNoSelection()
        // An entry that opens nothing starts the selection by a long press too.
        longPress("server-1", LibraryView.MARKED)
        awaitCount("1 selected")
    }

    // "Select all" chooses every entry the view lists under its filters, and none it hides.
    @Test
    fun selectAllChoosesEveryEntryTheFiltersLetThrough() {
        val english = (1..4).map { own("Dish $it", minutesAgo = it.toLong()) }
        val russian = own("Щи", minutesAgo = 10, language = RecipeLanguage.RU)
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.EN))
        show(LibraryView.OWN)
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(english.first().id.value)) && !exists(LibraryTags.entry(russian.id.value)) }

        longPress(english.first())
        take(LibraryTags.SELECT_ALL)

        awaitCount("4 selected")
        assertEquals(english.map { it.id.value }.toSet(), session.selection.value!!.ids)
        english.forEach { assertChosen(it, true) }
        // The filter lifted, the recipe it hid shows, not chosen.
        compose.runOnIdle { session.filter(LibraryView.OWN, BookFilters()) }
        awaitEntry(russian.id.value, LibraryView.OWN)
        assertChosen(russian, false)
        awaitCount("4 selected")
    }

    // A chosen entry a search hides is chosen no more.
    @Test
    fun anEntryASearchHidesLeavesTheSelection() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Pie")
        show(LibraryView.OWN)
        longPress(soup)
        tap(pie)
        awaitCount("2 selected")
        compose.runOnIdle { session.search(LibraryView.OWN, "pie") }
        awaitCount("1 selected")
        assertEquals(setOf(pie.id.value), session.selection.value!!.ids)
    }

    // --- Deleting and removing together -------------------------------------------------------------------------------

    // Delete takes every chosen own recipe out at once, asking nothing, with their timers and done marks; one notice
    // counts them with Undo, which brings every one back in its place in its collections.
    @Test
    fun deleteTakesTheChosenOutTogetherAndUndoBringsThemAllBackWithTheirCollections() {
        val soup = own("Soup", minutesAgo = 4)
        val pie = own("Pie", minutesAgo = 3)
        val bread = own("Bread", minutesAgo = 2)
        val salad = own("Salad", minutesAgo = 1)
        val kept = own("Kept")
        val dinner = collection("Dinner", soup, kept, pie)
        val baking = collection("Baking", bread, pie)
        val simmer = TimerKey(BookName.Pool, soup.id, 0)
        val other = TimerKey(BookName.Pool, kept.id, 0)
        runBlocking {
            store.start(simmer, "Simmer", "Soup", 10.minutes)
            store.start(other, "Cook", "Kept", 10.minutes)
        }
        marks.hold(BookName.Pool, pie.id, setOf(DoneMark.of(pie.steps.first())))
        show(LibraryView.OWN)

        longPress(soup)
        listOf(pie, bread, salad).forEach { tap(it) }
        awaitCount("4 selected")
        assertEquals("Delete", itemText(LibraryTags.SELECTION_DELETE))
        assertFalse("Remove among the own", exists(LibraryTags.SELECTION_REMOVE))
        compose.onNodeWithTag(LibraryTags.SELECTION_DELETE).performClick()

        compose.awaitRemovalNotice("4 recipes deleted", "Undo")
        awaitNoSelection()
        listOf(soup, pie, bread, salad).forEach { compose.waitUntil(PATIENCE_MS) { stored(it) == null } }
        listOf(soup, pie, bread, salad).forEach { compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.entry(it.id.value)), PATIENCE_MS) }
        assertEquals(kept, stored(kept))
        assertEquals(listOf(kept.id), members(dinner))
        assertEquals(emptyList<RecipeId>(), members(baking))
        compose.waitUntil(PATIENCE_MS) { runBlocking { store.all.first() }.map { it.key } == listOf(other) }
        assertEquals(emptySet<DoneMark>(), marks.held(BookName.Pool, pie.id))

        compose.takeNoticeAction("Undo")

        listOf(soup, pie, bread, salad).forEach { compose.waitUntil(PATIENCE_MS) { stored(it) != null } }
        assertEquals(listOf(soup, kept, pie).map { it.id }, members(dinner))
        assertEquals(listOf(bread, pie).map { it.id }, members(baking))
        listOf(soup, pie, bread, salad).forEach { awaitEntry(it.id.value, LibraryView.OWN) }
        // Undo restarts no timer.
        assertEquals(listOf(other), runBlocking { store.all.first() }.map { it.key })
    }

    /** [recipe] with a cover photograph held on the device under [mediaId]. */
    private fun withCover(recipe: Recipe, mediaId: String): Recipe {
        photos.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(-1, -40, -1, 1, 2, 3))
        return recipe.copy(media = listOf(Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)))
    }

    private fun files() = photos.directory(BookName.Pool).list().orEmpty().sorted()

    // The notice going without Undo makes the leaving of them all final: their photographs are deleted then, and not
    // before; another recipe's stay.
    @Test
    fun theNoticeGoingMakesTheDeletionOfThemAllFinal() {
        val soup = withCover(recipe("Soup", 2), "soup-cover").also { runBlocking { recipes.save(it) } }
        val pie = withCover(recipe("Pie", 1), "pie-cover").also { runBlocking { recipes.save(it) } }
        val kept = withCover(recipe("Kept", 0), "kept-cover").also { runBlocking { recipes.save(it) } }
        show(LibraryView.OWN)
        longPress(soup)
        tap(pie)
        take(LibraryTags.SELECTION_DELETE)
        compose.awaitRemovalNotice("2 recipes deleted", "Undo")
        compose.waitUntil(PATIENCE_MS) { stored(soup) == null && stored(pie) == null }
        assertEquals(listOf("kept-cover", "pie-cover", "soup-cover"), files())

        compose.swipeAwayNotice()

        compose.waitUntil(PATIENCE_MS) {
            ShadowLooper.idleMainLooper()
            files() == listOf("kept-cover")
        }
        assertNull(session.removal.value)
        assertNull(stored(soup))
        assertNull(stored(pie))
        assertEquals(kept, stored(kept))
    }

    // Remove takes every chosen imported recipe out at once; Undo brings them back marked.
    @Test
    fun removeTakesTheChosenImportedOutTogether() {
        val borscht = found("Borscht", minutesAgo = 2)
        val pelmeni = found("Pelmeni", minutesAgo = 1)
        val shchi = found("Shchi")
        show(LibraryView.MARKED, Mode.Serverless)

        longPress(borscht, LibraryView.MARKED)
        take(LibraryTags.SELECT_ALL)
        awaitCount("3 selected")
        assertFalse("Delete among the imported", exists(LibraryTags.SELECTION_DELETE))
        assertEquals("Remove", itemText(LibraryTags.SELECTION_REMOVE))
        compose.onNodeWithTag(LibraryTags.SELECTION_REMOVE).performClick()

        compose.awaitRemovalNotice("3 recipes removed", "Undo")
        awaitNoSelection()
        listOf(borscht, pelmeni, shchi).forEach { compose.waitUntil(PATIENCE_MS) { stored(it) == null } }
        assertFalse(runBlocking { recipes.isMarked(borscht.id) })

        compose.takeNoticeAction("Undo")

        listOf(borscht, pelmeni, shchi).forEach { compose.waitUntil(PATIENCE_MS) { stored(it) != null } }
        listOf(borscht, pelmeni, shchi).forEach { assertTrue(runBlocking { recipes.isMarked(it.id) }) }
    }

    // An entry an action does not apply to is chosen all the same; the action takes the others, and the notice counts
    // only them — one, named by its title.
    @Test
    fun deleteTakesOnlyTheChosenItAppliesTo() {
        val soup = own("Soup", minutesAgo = 1)
        val published = own("Published pie", state = RecipeState.PUBLISHED)
        show(LibraryView.OWN)

        longPress(published)
        tap(soup)
        awaitCount("2 selected")
        take(LibraryTags.SELECTION_DELETE)

        compose.awaitRemovalNotice("“Soup” deleted", "Undo")
        // The published recipe is still listed, and was chosen: the action itself ended the selection.
        awaitNoSelection()
        compose.waitUntil(PATIENCE_MS) { stored(soup) == null }
        assertEquals(published, stored(published))
        assertChosenNot(published)
    }

    // A selection none of which may be deleted shows Delete not responding; one of imported entries whose content the
    // book does not hold, Remove — and every other action, none of which applies to them (T-305).
    @Test
    fun aSelectionNoneOfWhichMayLeaveIsOfferedNoRemoval() {
        val published = own("Published pie", minutesAgo = 1, state = RecipeState.PUBLISHED)
        val pending = own("Pending soup", state = RecipeState.PENDING)
        markedOnly("server-1", "Shchi")
        show(LibraryView.OWN)

        longPress(published)
        tap(pending)
        awaitCount("2 selected")
        assertTrue(exists(LibraryTags.SELECT_ALL))
        compose.onNodeWithTag(LibraryTags.SELECTION_DELETE).assertIsNotEnabled()
        // Not responding, it is offered no action to the screen reader, and a tap takes nothing out.
        assertNull(compose.onNodeWithTag(LibraryTags.SELECTION_DELETE).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        compose.onNodeWithTag(LibraryTags.SELECTION_DELETE).performClick()
        compose.waitForIdle()
        awaitCount("2 selected")
        assertEquals(published, stored(published))
        compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_COLLECTION).assertIsEnabled()

        // The other view is shown once the selection ends (v0.38).
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        awaitNoSelection()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        longPress("server-1", LibraryView.MARKED)
        listOf(
            LibraryTags.SELECTION_ADD_TO_COLLECTION, LibraryTags.SELECTION_ADD_TO_SHOPPING, LibraryTags.SELECTION_PRINT,
            LibraryTags.SELECTION_SHARE_PDF, LibraryTags.SELECTION_REMOVE,
        ).forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }
        assertTrue(exists(LibraryTags.SELECT_ALL))
    }

    // While selecting, a long press chooses or takes off an entry as a tap does, opening nothing.
    @Test
    fun aLongPressWhileSelectingTogglesAsATapDoes() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Pie")
        show(LibraryView.OWN)
        longPress(soup)
        awaitCount("1 selected")

        longPress(pie)
        awaitCount("2 selected")
        assertChosen(pie, true)
        // The screen reader is offered the long press too, named for what it does.
        val actions = compose.onNodeWithTag(LibraryTags.entry(pie.id.value)).fetchSemanticsNode().config
        assertEquals("Take the recipe off the selection", actions.getOrNull(SemanticsActions.OnLongClick)?.label)
        assertEquals("Take the recipe off the selection", actions.getOrNull(SemanticsActions.OnClick)?.label)
        longPress(pie)
        awaitCount("1 selected")
        assertChosen(pie, false)
        assertNull(session.viewing.value)
    }

    // Opening a collection from the row above the recipes leaves the book: the selection ends, and back in the book none
    // is shown.
    @Test
    fun openingACollectionEndsTheSelection() {
        val soup = own("Soup")
        val dinner = collection("Dinner", soup)
        show(LibraryView.OWN)
        longPress(soup)
        awaitCount("1 selected")

        compose.waitUntil(PATIENCE_MS) { exists(CollectionScreenTags.card(dinner.value)) }
        // v0.38: the row does not respond while selecting; a collection opened otherwise — a file's notice's Open — is.
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(CollectionScreenTags.card(dinner.value)))
        compose.onNodeWithTag(CollectionScreenTags.card(dinner.value)).assertIsNotEnabled()
        compose.runOnUiThread { session.openCollection(dinner.value) }

        compose.waitUntil(PATIENCE_MS) { exists(CollectionScreenTags.SCREEN) }
        assertNull(session.selection.value)
        pressSystemBack()
        awaitNoSelection()
        assertChosenNot(soup)
    }

    // An Undo the book cannot take back for one of the recipes still brings back the others, and says the failure once.
    @Test
    fun undoPartlyRefusedBringsBackTheOthersAndSaysSoOnce() {
        val soup = own("Soup", minutesAgo = 2)
        val bread = own("Bread", minutesAgo = 1)
        // The newest, taken first: another recipe holds its cover by the time Undo runs.
        val pie = withCover(recipe("Pie", 0), "pie-cover").also { runBlocking { recipes.save(it) } }
        show(LibraryView.OWN)
        longPress(pie)
        tap(bread)
        tap(soup)
        take(LibraryTags.SELECTION_DELETE)
        compose.awaitRemovalNotice("3 recipes deleted", "Undo")
        listOf(soup, bread, pie).forEach { compose.waitUntil(PATIENCE_MS) { stored(it) == null } }
        val other = Recipe.create(now, owner = null).copy(title = "Pie again", media = pie.media)
        runBlocking { recipes.save(other) }
        val failures = mutableListOf<PendingRemoval>()
        val watching = timers.scope.launch { session.undoFailed.collect { if (it != null) failures += it } }

        compose.onNode(hasText("Undo") and hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)), useUnmergedTree = true).performClick()

        compose.waitUntil(PATIENCE_MS) {
            ShadowLooper.idleMainLooper()
            compose.onAllNodes(hasText("Couldn't bring the recipe back") and hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1
        }
        listOf(soup, bread).forEach { compose.waitUntil(PATIENCE_MS) { stored(it) != null } }
        assertNull(stored(pie))
        assertEquals(other, stored(other))
        assertEquals(listOf("pie-cover"), files())
        compose.swipeAwayNotice()
        compose.waitUntil(PATIENCE_MS) { session.undoFailed.value == null }
        compose.waitForIdle()
        assertFalse("the failure said again", compose.noticeShown())
        watching.cancel()
        assertEquals("the failure told", 1, failures.size)
    }

    // --- Russian -----------------------------------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "ru")
    fun theSelectionReadsInRussian() {
        val dishes = (1..5).map { own("Блюдо $it", minutesAgo = it.toLong(), language = RecipeLanguage.RU) }
        show(LibraryView.OWN)

        longPress(dishes[0])
        awaitCount("Выбран 1")
        assertChosen(dishes[0], true, "Выбран", "Не выбран")
        assertChosen(dishes[1], false, "Выбран", "Не выбран")
        tap(dishes[1])
        awaitCount("Выбрано 2")
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(LibraryTags.SELECT_ALL).joinToString("").replace('\u00A0', ' ') == "Выбрать все" }
        compose.onNodeWithTag(LibraryTags.SELECT_ALL).performClick()
        awaitCount("Выбрано 5")
        assertEquals("Удалить", itemText(LibraryTags.SELECTION_DELETE))
        compose.onNodeWithTag(LibraryTags.SELECTION_DELETE).performClick()
        compose.awaitRemovalNotice("5 рецептов удалено", "Вернуть")
        compose.takeNoticeAction("Вернуть")
        dishes.forEach { compose.waitUntil(PATIENCE_MS) { stored(it) != null } }
    }

    @Test
    @Config(qualifiers = "ru")
    fun removingSeveralImportedSaysSoInRussian() {
        val dishes = (1..2).map { found("Блюдо $it", minutesAgo = it.toLong()) }
        show(LibraryView.MARKED, Mode.Serverless)
        longPress(dishes[0], LibraryView.MARKED)
        tap(dishes[1], LibraryView.MARKED)
        awaitCount("Выбрано 2")
        assertEquals("Убрать", itemText(LibraryTags.SELECTION_REMOVE))
        compose.onNodeWithTag(LibraryTags.SELECTION_REMOVE).performClick()
        compose.awaitRemovalNotice("2 рецепта убраны", "Вернуть")
    }
}
