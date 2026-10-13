package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.res.stringResource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.media.SourceItem
import app.recipefork.app.media.SourceSheet
import app.recipefork.app.media.SourceSheetTags
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.WholeWords
import app.recipefork.core.recipe.Recipe
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The empty cover's tile and the photo source sheet at the largest text (v0.38, T-273, scenario 1; A11Y-004): at twice
 * the font scale on a 320 dp phone, in English and Russian, no word of the tile — «Необязательно» among them — nor of
 * the sheet — «Сфотографировать», its title — breaks inside itself or is cut, whether the sheet is the cover's or a
 * step's.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class CoverAtLargeTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-13T12:00:00Z")

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

    private fun under(tag: String): SemanticsMatcher = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** A new recipe in the editor: its cover empty, the tile shown; then the cover's sheet opened from the tile. */
    private fun coverKeepsWordsWhole(optional: String, take: String) {
        val recipe = Recipe.create(now, null)
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntil(PATIENCE_MS) { exists(CoverTags.PLACEHOLDER) }
        compose.onNodeWithTag(CoverTags.PLACEHOLDER).performScrollTo()
        compose.waitForIdle()
        val tile = WholeWords.assertWhole(compose, under(CoverTags.PLACEHOLDER), "the empty cover's tile")
        assertTrue("“$optional” is among $tile", optional in tile)
        compose.onNodeWithTag(CoverTags.PLACEHOLDER).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CoverTags.TAKE) }
        compose.waitForIdle()
        val sheet = WholeWords.assertWhole(compose, under(SourceSheetTags.SHEET), "the cover's source sheet")
        assertTrue("“$take” is among $sheet", take in sheet)
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleTheCoversTileAndSheetKeepTheirWordsWhole() = coverKeepsWordsWhole("Optional", "Take a photo")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheCoversTileAndSheetKeepTheirWordsWhole() = coverKeepsWordsWhole("Необязательно", "Сфотографировать")

    /** A step's sheet, its photo attached: its title, its four sources and the photo's removal. */
    private fun stepSheetKeepsWordsWhole(title: String) {
        compose.setContent {
            MaterialTheme {
                SourceSheet(
                    stringResource(R.string.media_step_photo_label, 1),
                    listOf(
                        SourceItem(R.drawable.ic_step_photo, stringResource(R.string.media_source_take), "take") {},
                        SourceItem(R.drawable.ic_photo_library, stringResource(R.string.media_source_choose), "choose") {},
                        SourceItem(R.drawable.ic_videocam, stringResource(R.string.media_step_clip_record), "record") {},
                        SourceItem(R.drawable.ic_video_library, stringResource(R.string.media_step_clip_choose), "choose-clip") {},
                        SourceItem(R.drawable.ic_remove_row, stringResource(R.string.media_step_remove), "remove", destructive = true) {},
                    ),
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(PATIENCE_MS) { exists("remove") }
        compose.waitForIdle()
        WholeWords.assertWhole(compose, under(SourceSheetTags.SHEET), "a step's source sheet")
        assertEquals(listOf(title), WholeWords.layouts(compose, hasTestTag(SourceSheetTags.TITLE)).map { it.layoutInput.text.text })
    }

    @Test
    @Config(fontScale = 2f)
    fun atTwiceTheFontScaleAStepsSheetKeepsItsWordsWhole() = stepSheetKeepsWordsWhole("Photo or clip for step 1")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleAStepsSheetKeepsItsWordsWhole() = stepSheetKeepsWordsWhole("Фото или видео для шага 1")
}
