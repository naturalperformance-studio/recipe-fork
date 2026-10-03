package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.descriptionOf
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
 * A step's photograph on the card (FLOW-026 (2)): at its step, under its text and before the next step, named for the
 * screen reader by the step's number as the card shows it; a photograph the device does not hold, or cannot read, says
 * it is unavailable (FLOW-061); a frozen recipe shows its own.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardStepPhotoTest {
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

    private fun photo(mediaId: String, step: Int): Media {
        TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 1600, 1200)
        return Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.ToStep(step), MediaVisibility.PRIVATE)
    }

    private fun threeSteps(vararg media: Media) =
        Recipe.create(now, null).copy(title = "Borscht", steps = listOf(Step(0, "Boil."), Step(1, "Fry."), Step(2, "Serve.")), media = media.toList())

    private fun show(recipe: Recipe) {
        runBlocking { recipes.save(recipe) }
        compose.setContent { MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.TITLE), PATIENCE_MS)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    @Test
    fun aStepPhotoShowsAtItsStepWithATextAlternative() {
        show(threeSteps(photo("p1", 1)))

        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(1)), PATIENCE_MS)
        assertEquals(listOf("Photo of step 2"), compose.descriptionOf(CardTags.stepPhoto(1)))
        val image = bounds(CardTags.stepPhoto(1))
        assertTrue("under its step's text", image.top >= bounds(CardTags.step(1)).bottom)
        assertTrue("before the next step", image.bottom <= bounds(CardTags.step(2)).top)
        // Indented as the step's text is, under its number.
        assertTrue(image.left >= bounds(CardTags.step(1)).left)
        assertFalse(exists(CardTags.stepPhoto(0)) || exists(CardTags.stepPhoto(2)) || exists(CardTags.COVER))
    }

    // The number named is the one the card shows: a step that reads as nothing is not counted.
    @Test
    fun theTextAlternativeNamesTheStepAsTheCardNumbersIt() {
        val recipe = threeSteps(photo("p2", 2)).let { it.copy(steps = listOf(Step(0, ""), it.steps[1], it.steps[2])) }
        show(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(2)), PATIENCE_MS)
        assertEquals(listOf("Photo of step 2"), compose.descriptionOf(CardTags.stepPhoto(2)))
    }

    // A step that holds only a photograph shows it, numbered as a step.
    @Test
    fun aStepThatIsOnlyAPhotoShowsIt() {
        val recipe = threeSteps(photo("p0", 0)).let { it.copy(steps = listOf(Step(0, ""), it.steps[1], it.steps[2])) }
        show(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(0)), PATIENCE_MS)
        assertEquals(listOf("Photo of step 1"), compose.descriptionOf(CardTags.stepPhoto(0)))
        assertTrue(exists(CardTags.step(0)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theTextAlternativeIsInTheInterfaceLanguage() {
        show(threeSteps(photo("p1", 2)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(2)), PATIENCE_MS)
        assertEquals(listOf("Фото шага 3"), compose.descriptionOf(CardTags.stepPhoto(2)))
    }

    private fun unavailableLine(position: Int) = compose.onNodeWithTag(CardTags.stepMediaUnavailable(position), useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    // FLOW-061: a photograph whose file is gone says it is unavailable, rather than showing nothing.
    @Test
    fun aStepPhotoWhoseFileIsGoneSaysItIsUnavailable() {
        val gone = Media("gone", null, MediaKind.PHOTO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE)
        show(threeSteps(gone))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepMediaUnavailable(1)), PATIENCE_MS)
        assertEquals("The photo of step 2 is unavailable on this phone.", unavailableLine(1))
        assertFalse(exists(CardTags.stepPhoto(1)))
        assertTrue(exists(CardTags.step(1)))
    }

    // A file that is there but cannot be read as a picture says the same.
    @Test
    fun aStepPhotoThatCannotBeReadSaysItIsUnavailable() {
        store.file(BookName.Pool, "broken").apply { parentFile!!.mkdirs() }.writeBytes(ByteArray(100) { 7 })
        show(threeSteps(Media("broken", null, MediaKind.PHOTO, MediaAttachment.ToStep(2), MediaVisibility.PRIVATE)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepMediaUnavailable(2)), PATIENCE_MS)
        assertEquals("The photo of step 3 is unavailable on this phone.", unavailableLine(2))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theUnavailableLineIsInTheInterfaceLanguage() {
        show(threeSteps(Media("gone", null, MediaKind.PHOTO, MediaAttachment.ToStep(0), MediaVisibility.PRIVATE)))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepMediaUnavailable(0)), PATIENCE_MS)
        assertEquals("Фото шага 1 недоступно на этом телефоне.", unavailableLine(0))
    }

    // A step that holds nothing but a photograph whose file is gone is still a step: it is numbered and says its
    // photograph is unavailable (FLOW-061), never left out in silence.
    @Test
    fun aStepThatIsOnlyAPhotoWhoseFileIsGoneSaysItIsUnavailable() {
        val gone = Media("gone", null, MediaKind.PHOTO, MediaAttachment.ToStep(0), MediaVisibility.PRIVATE)
        val recipe = threeSteps(gone, photo("p1", 1)).let { it.copy(steps = listOf(Step(0, ""), it.steps[1], it.steps[2])) }
        show(recipe)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(1)), PATIENCE_MS)
        assertTrue(exists(CardTags.step(0)))
        assertFalse(exists(CardTags.stepPhoto(0)))
        assertEquals("The photo of step 1 is unavailable on this phone.", unavailableLine(0))
        assertEquals(listOf("Photo of step 2"), compose.descriptionOf(CardTags.stepPhoto(1)))
    }

    @Test
    fun aFrozenRecipeShowsItsStepPhotos() {
        show(threeSteps(photo("p0", 0), photo("p2", 2)).inState(RecipeState.PUBLISHED))
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(0)), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepPhoto(2)), PATIENCE_MS)
        assertFalse(exists(CardTags.EDIT))
    }
}
