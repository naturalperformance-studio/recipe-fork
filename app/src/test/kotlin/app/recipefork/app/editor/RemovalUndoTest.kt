package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * A step or an ingredient row deleted in the editor says so at the bottom with Undo: Undo puts it back where it stood
 * with all it held — text, references, timer, photograph — and a step's photograph is deleted from the disk only once
 * the statement goes without Undo, or the editor closes, so nothing is lost by a slip and nothing is left behind.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemovalUndoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val store = PhotoStore(context.filesDir)
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private var shown by mutableStateOf(true)

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

    private val flour = Ingredient(0, null, null, "Flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))
    private val milk = Ingredient(1, null, null, "Milk", UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(300))
    private val salt = Ingredient(2, null, null, "Salt", UnitType.TASTE_UNMEASURED, null, null)

    /** Three steps; the second refers to the milk and the salt, carries a timer and a photograph whose file is held. */
    private fun recipe(): Recipe {
        val photo = "fry.jpg"
        TestPhotos.jpeg(store.file(BookName.Pool, photo).apply { parentFile!!.mkdirs() }, 400, 300)
        return Recipe.create(now, null).copy(
            title = "Pancakes",
            ingredients = listOf(flour, milk, salt),
            steps = listOf(
                Step(0, "Mix flour."),
                Step(1, "Pour  and  in.", listOf(IngredientReference(1, 5), IngredientReference(2, 10)), Timer(5.minutes, "Rest")),
                Step(2, "Fry."),
            ),
            media = listOf(Media(photo, null, MediaKind.PHOTO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE)),
        )
    }

    private fun open(recipe: Recipe): Recipe {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent {
            MaterialTheme {
                if (shown) EditorScreen(recipe.id, recipes, environment, saving, onClose = {})
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe
    }

    private fun stored(recipe: Recipe): Recipe = runBlocking { recipes.get(recipe.id) }!!

    private fun held(): List<String> = store.directory(BookName.Pool).list()?.sorted().orEmpty()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** What the recipe holds, its time aside. */
    private fun Recipe.content() = listOf(ingredients, steps.sortedBy { it.position }, media)

    private fun undo() {
        compose.onNode(
            hasText(context.getString(R.string.editor_undo)) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
            useUnmergedTree = true,
        ).performClick()
    }

    private fun removeStep(position: Int) {
        compose.onNodeWithTag(EditorTags.stepRemove(position)).performScrollTo().performClick()
        compose.awaitStatement(context.getString(R.string.editor_step_removed, position + 1))
    }

    // Undo puts the step back at its place with its text, references, timer and photograph, whose file stays held —
    // also once the editor is closed afterwards.
    @Test
    fun undoPutsAStepBackWithItsPhotograph() {
        val recipe = open(recipe())
        removeStep(1)
        compose.waitUntil(PATIENCE_MS) { stored(recipe).steps.size == 2 && stored(recipe).media.isEmpty() }

        undo()

        compose.waitUntil(PATIENCE_MS) { stored(recipe).content() == recipe.content() }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(1)) && !exists(EditorTags.STATEMENT) }
        shown = false
        compose.waitForIdle()
        assertEquals(recipe.content(), stored(recipe).content())
        assertEquals(listOf("fry.jpg"), held())
    }

    // A removed step's photograph stays on the disk while Undo is offered, and is deleted once the statement goes
    // without Undo.
    @Test
    fun aRemovedStepsPhotographIsDeletedOnceTheStatementGoes() {
        val recipe = open(recipe())
        removeStep(1)
        compose.waitUntil(PATIENCE_MS) { stored(recipe).steps.size == 2 && stored(recipe).media.isEmpty() }
        compose.waitForIdle()
        assertTrue(exists(EditorTags.STATEMENT))
        assertEquals(listOf("fry.jpg"), held())

        compose.swipeAwayStatement()

        compose.waitUntil(PATIENCE_MS) { held().isEmpty() }
        assertEquals(listOf(Step(0, "Mix flour."), Step(1, "Fry.")), stored(recipe).steps)
    }

    // The editor closed while Undo is offered: the removed step's photograph is deleted, none left orphaned.
    @Test
    fun closingTheEditorDeletesARemovedStepsPhotograph() {
        val recipe = open(recipe())
        removeStep(1)

        // The clock held, so that the statement cannot go by itself first.
        compose.mainClock.autoAdvance = false
        shown = false
        compose.waitForIdle()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }

        compose.waitUntil(PATIENCE_MS) { held().isEmpty() }
        assertTrue(stored(recipe).media.isEmpty())
        assertEquals(2, stored(recipe).steps.size)
    }

    // The next change ends Undo: the statement goes and the removed step's photograph is deleted.
    @Test
    fun theNextChangeEndsUndoAndDeletesThePhotograph() {
        val recipe = open(recipe())
        removeStep(1)

        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextInput("!")

        compose.waitUntil(PATIENCE_MS) { held().isEmpty() && !exists(EditorTags.STATEMENT) }
        assertEquals(2, stored(recipe).steps.size)
    }

    // An ingredient row removed: its references go as they always did, the text closed up; Undo puts the row back at
    // its place and the references back where they stood.
    @Test
    fun undoPutsAnIngredientBackWithItsReferences() {
        val recipe = open(recipe())
        compose.onNodeWithTag(EditorTags.ingredientRemove(1)).performScrollTo().performClick()
        compose.awaitStatement(context.getString(R.string.editor_ingredient_removed, 2))
        compose.waitUntil(PATIENCE_MS) { stored(recipe).ingredients.size == 2 }
        assertEquals(listOf(flour, salt.copy(position = 1)), stored(recipe).ingredients)
        assertEquals(Step(1, "Pour and  in.", listOf(IngredientReference(1, 9)), Timer(5.minutes, "Rest")), stored(recipe).steps[1])

        undo()

        compose.waitUntil(PATIENCE_MS) { stored(recipe).content() == recipe.content() }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredient(2)) && !exists(EditorTags.STATEMENT) }
        assertEquals(listOf("fry.jpg"), held())
    }
}
