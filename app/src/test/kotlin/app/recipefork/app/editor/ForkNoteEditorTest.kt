package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.fork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * FLOW-030: an info action in the editor's top bar opens the note on which parts a fork takes — the title, tags,
 * servings, ingredients and steps — and that the description and the photos stay with the author unless the fork is
 * the author's own (FORK-001, FORK-002, FORK-007). It says what a fork does, never that forking can be prevented, it is
 * the same on a recipe that was forked as on one that never was, and it takes no room above the form.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ForkNoteEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val created = Instant.parse("2026-09-20T09:00:00Z")
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
    }

    @After
    fun close() {
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private val recipe = Recipe.create(created, null).copy(
        title = "Borscht",
        description = "My grandmother's way.",
        servings = 4,
        steps = listOf(Step(0, "Boil everything.")),
    ).inState(RecipeState.LOCAL)

    private fun open(shown: Recipe) {
        val environment = EditorEnvironment(active, device.catalog(context)) { now }
        compose.setContent { MaterialTheme { EditorScreen(shown.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    /** Opens the note from the top bar and reads it. */
    private fun notes(): List<String> {
        compose.onNodeWithTag(EditorTags.FORK_NOTE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORK_TAKES), PATIENCE_MS)
        return compose.textsOf(EditorTags.FORK_TAKES)
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun px(dp: Float): Float = dp * compose.density.density

    // One line names the five parts a fork takes (1), the description, step notes and photos staying with the author (2), and
    // the author's own fork carrying them too (3).
    private val english = listOf(
        "A fork takes the title, tags, servings, ingredients and steps; the description, step notes and photos stay " +
            "with you unless the fork is your own.",
    )

    // FLOW-030 (1–4): what a fork takes, what stays, that the author's own forks keep it, and nothing of prevention.
    @Test
    fun theEditorSaysWhatAForkTakesAndWhatStaysWithTheAuthor() {
        runBlocking { recipes.save(recipe) }
        open(recipe)
        val read = notes()
        assertEquals(english, read)
        read.forEach { assertFalse(it, it.contains("prevent", ignoreCase = true) || it.contains("cannot", ignoreCase = true)) }
    }

    // The note stands as no block above the form: the form begins with the title, and the note is behind a 48 dp info
    // action in the top bar, named for what it opens; opening and closing it stores nothing.
    @Test
    fun theNoteTakesNoRoomAboveTheForm() {
        runBlocking { recipes.save(recipe) }
        val before = runBlocking { recipes.get(recipe.id) }
        open(recipe)
        assertTrue(compose.onAllNodes(hasTestTag(EditorTags.FORK_TAKES), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // Nothing between the form's 16 dp padding and the title.
        assertEquals(bounds(EditorTags.FORM).top + px(16f), bounds(EditorTags.TITLE).top, 1f)
        // The action is in the bar, 48 dp, and the screen reader names it.
        assertEquals(1, compose.onAllNodes(hasTestTag(EditorTags.FORK_NOTE) and hasAnyAncestor(hasTestTag(EditorTags.BAR))).fetchSemanticsNodes().size)
        val action = bounds(EditorTags.FORK_NOTE)
        assertTrue("$action", action.width >= px(48f) - 0.5f && action.height >= px(48f) - 0.5f)
        assertEquals("What a fork takes", AccessibilityChecks.announcedLabel(compose.onNodeWithTag(EditorTags.FORK_NOTE).fetchSemanticsNode()))
        // A long press shows its name and opens nothing.
        compose.onNodeWithTag(EditorTags.FORK_NOTE).performTouchInput { longClick() }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORK_NOTE_TOOLTIP), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.FORK_NOTE_DIALOG).assertDoesNotExist()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.FORK_NOTE_TOOLTIP), PATIENCE_MS)
        assertEquals(english, notes())
        compose.onNodeWithTag(EditorTags.FORK_NOTE_CLOSE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.FORK_NOTE_DIALOG), PATIENCE_MS)
        assertEquals(before, runBlocking { recipes.get(recipe.id) })
    }

    // FLOW-030 (5): a recipe that was forked reads the same marking.
    @Test
    fun theMarkingIsTheSameOnceTheRecipeWasForked() {
        val fork = recipe.fork(now, null, byAuthor = true)
        runBlocking {
            recipes.save(recipe)
            recipes.save(fork)
        }
        open(recipe)
        assertEquals(english, notes())
    }

    // FLOW-030 in the interface's other language.
    @Test
    @Config(qualifiers = "ru")
    fun theMarkingIsSaidInRussian() {
        runBlocking { recipes.save(recipe) }
        open(recipe)
        assertEquals(
            listOf(
                "Своя версия рецепта берёт название, теги, порции, ингредиенты и шаги; описание, заметки к шагам и фото " +
                    "остаются у вас, если только версия не ваша собственная.",
            ),
            notes(),
        )
    }
}
