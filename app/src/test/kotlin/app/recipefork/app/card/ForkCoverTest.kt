package app.recipefork.app.card

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
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.cover
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * FORK-007: the owner's fork of a recipe with a cover holds a cover of its own — the file copied under the fork's new
 * media identity — so it shows on the fork's card and outlives the parent's file. A found recipe's fork keeps none.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForkCoverTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
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

    private fun withCover(): Recipe {
        val file = store.file(BookName.Pool, "parent-cover").apply { parentFile!!.mkdirs() }
        TestPhotos.jpeg(file, 1600, 1200)
        val cover = Media("parent-cover", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)
        return Recipe.create(now, null).copy(title = "Borscht", media = listOf(cover))
    }

    private fun card(id: RecipeId) {
        compose.setContent { MaterialTheme { CardScreen(id, recipes, environment, onBack = {}, onEdit = {}) } }
    }

    @Test
    fun theOwnersForkShowsItsOwnCopyOfTheCover() {
        val parent = withCover().also { runBlocking { recipes.save(it) } }

        val forkId = runBlocking { forkRecipe(recipes, active, parent.id, now) }!!

        val fork = runBlocking { recipes.get(forkId) }!!
        val copy = fork.cover!!.mediaId
        assertNotEquals("parent-cover", copy)
        assertArrayEquals(store.file(BookName.Pool, "parent-cover").readBytes(), store.file(BookName.Pool, copy).readBytes())
        // The parent's file gone, the fork's cover still shows: it is the fork's own.
        assertTrue(store.file(BookName.Pool, "parent-cover").delete())
        card(forkId)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.COVER), PATIENCE_MS)
        assertEquals(listOf("Cover photo of “Borscht”"), compose.descriptionOf(CardTags.COVER))
    }

    @Test
    fun aFoundRecipesForkKeepsNoCoverAndCopiesNoFile() {
        val parent = withCover().inState(RecipeImport.FOUND_STATE)
        runBlocking { recipes.addFound(parent, now) }

        val forkId = runBlocking { forkRecipe(recipes, active, parent.id, now) }!!

        assertEquals(emptyList<Media>(), runBlocking { recipes.get(forkId) }!!.media)
        assertEquals(listOf("parent-cover"), store.directory(BookName.Pool).list()!!.toList())
    }

    // A copy that fails leaves the item out of the fork: the fork never names a file it does not hold (MEDIA-008).
    @Test
    fun aCoverWhoseCopyFailsIsLeftOutOfTheFork() {
        val parent = withCover().also { runBlocking { recipes.save(it) } }
        val failing = object : PhotoStore(context.filesDir) {
            override suspend fun copy(book: BookName, from: String, to: String): Boolean = false
        }

        val forkId = runBlocking { forkRecipe(recipes, active, parent.id, now, failing) }!!

        assertEquals(emptyList<Media>(), runBlocking { recipes.get(forkId) }!!.media)
        assertEquals(listOf("parent-cover"), store.directory(BookName.Pool).list()!!.toList())
    }

    // A fork that is not written leaves none of the copies made for it.
    @Test
    fun aForkThatIsNotWrittenLeavesNoCopy() {
        val parent = withCover().also { runBlocking { recipes.save(it) } }
        val refusing = object : RecipeRepository(active, context.filesDir) {
            override suspend fun save(recipe: Recipe) {
                if (recipe.id != parent.id) throw IllegalStateException("not written")
                super.save(recipe)
            }
        }

        runCatching { runBlocking { forkRecipe(refusing, active, parent.id, now) } }

        assertEquals(listOf("parent-cover"), store.directory(BookName.Pool).list()!!.toList())
    }
}
