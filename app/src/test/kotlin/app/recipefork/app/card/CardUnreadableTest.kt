package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * FLOW-020 (3): a card whose recipe cannot be read from the device says it could not be loaded and offers to try
 * again, which reads it again; a recipe the book does not hold keeps saying so, and offers nothing to retry.
 */
@RunWith(AndroidJUnit4::class)
class CardUnreadableTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    /** How many reads are still to fail, and how many were made. */
    private val failing = AtomicInteger(0)
    private val reads = AtomicInteger(0)

    /** Held shut, a read waits on it before it fails or reads, so the card can be seen while it loads. */
    @Volatile
    private var gate: CompletableDeferred<Unit>? = null

    /** The book's recipes, whose reads fail — as a corrupt row or a refusing storage would — while [failing] lasts. */
    private val recipes = object : RecipeRepository(active) {
        override fun observe(id: RecipeId): Flow<Recipe?> {
            val real = super.observe(id)
            return flow {
                reads.incrementAndGet()
                gate?.await()
                if (failing.getAndUpdate { (it - 1).coerceAtLeast(0) } > 0) throw IOException("the row does not read back")
                emitAll(real)
            }
        }
    }

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun show(id: RecipeId) {
        compose.setContent {
            AppTheme { CardScreen(id, recipes, environment, onBack = {}, onEdit = {}) }
        }
        compose.awaitCard()
    }

    @Test
    fun aRecipeThatCannotBeReadSaysSoAndIsReadAgainOnTryAgain() =
        unreadable("This recipe could not be loaded.", "Try again")

    @Test
    @Config(qualifiers = "ru")
    fun aRecipeThatCannotBeReadSaysSoAndIsReadAgainOnTryAgainInRussian() =
        unreadable("Не удалось загрузить этот рецепт.", "Попробовать ещё раз")

    private fun unreadable(message: String, retry: String) {
        val recipe = Recipe.create(now, null).copy(title = "Roast chicken").also { runBlocking { recipes.save(it) } }
        failing.set(2)

        show(recipe.id)

        assertEquals(listOf(message, retry), unreadableTexts())
        compose.onNodeWithTag(CardTags.MISSING).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.CONTENT).assertDoesNotExist()
        compose.assertCardTarget(CardTags.RETRY)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose)
        LocalizationChecks.assertScreenTextFromResources(compose, context)

        // While the second read runs, the card is loading: the failure and its offer are gone.
        val held = CompletableDeferred<Unit>().also { gate = it }
        compose.onNodeWithTag(CardTags.RETRY).performClick()
        compose.waitUntil(PATIENCE_MS) { reads.get() == 2 }
        compose.waitForIdle()
        compose.onNodeWithTag(CardTags.UNREADABLE).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.MISSING).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.CONTENT).assertDoesNotExist()
        gate = null
        held.complete(Unit)

        // The second read fails as well: the card says so again, and offers the next try.
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.UNREADABLE)).fetchSemanticsNodes().size == 1 }
        assertEquals(listOf(message, retry), unreadableTexts())

        // The third read succeeds, and the card opens on the recipe.
        compose.onNodeWithTag(CardTags.RETRY).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.CONTENT)).fetchSemanticsNodes().size == 1 }
        assertEquals(listOf("Roast chicken"), compose.textsOf(CardTags.TITLE))
        compose.onNodeWithTag(CardTags.UNREADABLE).assertDoesNotExist()
        assertEquals(3, reads.get())
    }

    // A recipe the book does not hold is not a failure to read it: the existing message, and nothing to retry.
    @Test
    fun aRecipeTheBookDoesNotHoldKeepsItsMessageAndOffersNoRetry() {
        show(RecipeId("not-in-this-book"))

        assertEquals(listOf("This recipe is no longer in this recipe book."), compose.textsOf(CardTags.MISSING))
        compose.onNodeWithTag(CardTags.UNREADABLE).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.RETRY).assertDoesNotExist()
    }

    /** The texts shown within the failure's word, in order. */
    private fun unreadableTexts(): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.UNREADABLE)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } }
            .distinct()
}
