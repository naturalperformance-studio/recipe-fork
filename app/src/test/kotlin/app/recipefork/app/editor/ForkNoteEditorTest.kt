package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * FLOW-030: the editor says which parts a fork takes — the title, tags, servings, ingredients and steps, in one line at
 * the top — and that the description and the photos stay with the author when someone else forks, while the author's
 * own forks keep them (FORK-001, FORK-002, FORK-007). It says what a fork does, never that forking can be prevented,
 * and it is the same on a recipe that was forked as on one that never was.
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

    private fun notes(): List<String> = listOf(EditorTags.FORK_TAKES, CoverTags.PHOTOS_STAY, EditorTags.DESCRIPTION_STAYS)
        .flatMap { compose.textsOf(it) }

    private val english = listOf(
        "A fork takes the title, tags, servings, ingredients and steps.",
        "Photos stay with you when someone else forks this recipe; your own forks keep them.",
        "Stays with you when someone else forks this recipe; your own forks keep it.",
    )

    // FLOW-030 (1–4): what a fork takes, what stays, that the author's own forks keep it, and nothing of prevention.
    @Test
    fun theEditorSaysWhatAForkTakesAndWhatStaysWithTheAuthor() {
        runBlocking { recipes.save(recipe) }
        open(recipe)
        assertEquals(english, notes())
        notes().forEach { assertFalse(it, it.contains("prevent", ignoreCase = true) || it.contains("cannot", ignoreCase = true)) }
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
                "Своя версия рецепта берёт название, теги, порции, ингредиенты и шаги.",
                "Фото остаются у вас, когда кто-то другой делает свою версию рецепта; ваши собственные версии их сохраняют.",
                "Остаётся у вас, когда кто-то другой делает свою версию рецепта; ваши собственные версии его сохраняют.",
            ),
            notes(),
        )
    }
}
