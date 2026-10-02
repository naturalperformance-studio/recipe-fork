package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The cover on the card: at the top, above the title, with a text alternative; a recipe without one — or whose cover
 * cannot be read — shows nothing in its place; a frozen or found recipe shows its own.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardCoverTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun cover(mediaId: String, width: Int = 1600, height: Int = 1200): Media {
        val file = store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }
        TestPhotos.jpeg(file, width, height)
        return Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)
    }

    private fun show(recipe: Recipe) {
        runBlocking { recipes.save(recipe) }
        compose.setContent { MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.TITLE), PATIENCE_MS)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theCoverShowsAtTheTopOfTheCardWithATextAlternative() {
        show(Recipe.create(now, null).copy(title = "Borscht", steps = listOf(Step(0, "Boil.")), media = listOf(cover("c1"))))

        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.COVER), PATIENCE_MS)
        assertEquals(listOf("Cover photo of “Borscht”"), compose.descriptionOf(CardTags.COVER))
        val cover = compose.onNodeWithTag(CardTags.COVER).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag(CardTags.TITLE).fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().boundsInRoot
        assertTrue(cover.bottom <= title.top)
        assertTrue(cover.top - content.top < 100f)
        // A landscape cover fills the width, whole.
        assertEquals(cover.width / cover.height, 1600f / 1200f, 0.02f)
    }

    @Test
    @Config(qualifiers = "ru")
    fun theTextAlternativeIsInTheInterfaceLanguage() {
        show(Recipe.create(now, null).copy(title = "Борщ", media = listOf(cover("c1"))))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.COVER), PATIENCE_MS)
        assertEquals(listOf("Фото обложки рецепта «Борщ»"), compose.descriptionOf(CardTags.COVER))
    }

    @Test
    fun anUntitledRecipesCoverIsNamedAsUntitled() {
        show(Recipe.create(now, null).copy(media = listOf(cover("c1", 900, 1600))))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.COVER), PATIENCE_MS)
        assertEquals(listOf("Cover photo of “Untitled recipe”"), compose.descriptionOf(CardTags.COVER))
        // A taller cover is cropped to a square.
        val bounds = compose.onNodeWithTag(CardTags.COVER).fetchSemanticsNode().boundsInRoot
        assertEquals(1f, bounds.width / bounds.height, 0.02f)
    }

    @Test
    fun aRecipeWithoutACoverShowsNothingInItsPlace() {
        show(Recipe.create(now, null).copy(title = "Borscht"))
        compose.waitForIdle()
        assertFalse(exists(CardTags.COVER))
        val title = compose.onNodeWithTag(CardTags.TITLE).fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().boundsInRoot
        // The title opens the card, as it did before covers.
        assertTrue(title.top - content.top < 40f)
    }

    @Test
    fun aCoverWhoseFileCannotBeReadShowsNothing() {
        val gone = Media("gone", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)
        show(Recipe.create(now, null).copy(title = "Borscht", media = listOf(gone)))
        compose.waitForIdle()
        assertFalse(exists(CardTags.COVER))
    }

    // A frozen recipe — published, or found in a file — shows its cover, read-only like the rest of it.
    @Test
    fun aFrozenRecipeShowsItsCover() {
        show(Recipe.create(now, null).copy(title = "Borscht", media = listOf(cover("c1"))).inState(RecipeState.PUBLISHED))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.COVER), PATIENCE_MS)
        assertFalse(exists(CardTags.EDIT))
    }

    @Test
    fun aFoundRecipeShowsTheCoverItsFileCarried() {
        // An imported photograph is kept where the editor keeps its own, and shows alike.
        assertEquals(RecipeImport.mediaDirectory(context.filesDir, BookName.Pool), store.directory(BookName.Pool))
        show(Recipe.create(now, null).copy(title = "Borscht", media = listOf(cover("c1"))).inState(RecipeImport.FOUND_STATE))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.COVER), PATIENCE_MS)
    }
}
