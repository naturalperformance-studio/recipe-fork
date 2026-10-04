package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.PreferencesDragHandleSide
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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
 * Scenario 4 of the increment: the settings offer "Drag handle on the right" as a switch; on, the editor's
 * steps and ingredients carry their drag column at the block's right edge; off, at the left; the choice is kept on the
 * device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DragHandleMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val side = PreferencesDragHandleSide(context)
    private val now = Instant.parse("2026-10-03T12:00:00Z")

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

    private fun show() {
        val editor = EditorEnvironment(active, device.catalog(context), dragHandleSide = side) { now }
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = editor) } }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun openMenu() {
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.HANDLE_RIGHT) }
    }

    private fun leaveSettings() {
        compose.onNodeWithTag(SettingsTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(SettingsTags.SCREEN) && exists(LibraryTags.SETTINGS) }
    }

    private fun toggle() {
        openMenu()
        val was = checked()
        compose.onNodeWithTag(SettingsTags.HANDLE_RIGHT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { checked() != was }
        leaveSettings()
    }

    private fun checked(): ToggleableState =
        compose.onNodeWithTag(SettingsTags.HANDLE_RIGHT).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    // The settings offer the item, switched on while the column is on the right; each choice is kept on the device.
    @Test
    fun theSettingsOfferTheRightHandleAsASwitchKeptOnTheDevice() {
        show()
        openMenu()
        assertEquals(listOf("Drag handle on the right"), compose.textsOf(SettingsTags.HANDLE_RIGHT))
        val item = compose.onNodeWithTag(SettingsTags.HANDLE_RIGHT).fetchSemanticsNode()
        assertEquals(Role.Switch, item.config[SemanticsProperties.Role])
        assertEquals(ToggleableState.Off, checked())
        compose.onNodeWithTag(SettingsTags.HANDLE_RIGHT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { checked() == ToggleableState.On }
        assertTrue(side.right.value)
        assertTrue("kept across a restart", PreferencesDragHandleSide(context).right.value)
        leaveSettings()
        openMenu()
        assertEquals(ToggleableState.On, checked())
        compose.onNodeWithTag(SettingsTags.HANDLE_RIGHT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { checked() == ToggleableState.Off }
        assertFalse(side.right.value)
        assertFalse("kept across a restart", PreferencesDragHandleSide(context).right.value)
    }

    // A11Y-003, A11Y-005: the item is large enough and says what it is.
    @Test
    fun theItemIsLargeEnoughAndLabelled() {
        show()
        openMenu()
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 2)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 2)
    }

    // L10N: the item reads in Russian, from the resources.
    @Test
    @Config(qualifiers = "ru")
    fun theItemReadsInRussian() {
        show()
        openMenu()
        assertEquals(listOf("Ручка перетаскивания справа"), compose.textsOf(SettingsTags.HANDLE_RIGHT))
        LocalizationChecks.assertScreenTextFromResources(compose, context, emptySet())
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, emptySet())
    }

    // Scenario 4: switched on in the settings, every step's and ingredient's column stands at its block's right
    // edge in the editor; switched off, at its left edge again.
    @Test
    @Config(qualifiers = "en-w360dp-h2000dp")
    fun theMenuMovesEveryColumnToTheRightAndBack() {
        val recipe = Recipe.create(now, null).copy(
            title = "Bread",
            ingredients = listOf(
                Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)),
                Ingredient(1, null, null, "water", UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(300)),
            ),
            steps = listOf(Step(0, "Mix."), Step(1, "Bake.")),
        )
        runBlocking { recipes.save(recipe) }
        show()
        listOf(true, false).forEach { right ->
            toggle()
            assertEquals(right, side.right.value)
            compose.runOnIdle { session.openEditor(recipe.id.value) }
            compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepHandle(1)) && exists(EditorTags.ingredientHandle(1)) }
            val blocks = (0..1).flatMap { listOf(EditorTags.step(it) to EditorTags.stepHandle(it), EditorTags.ingredient(it) to EditorTags.ingredientHandle(it)) }
            blocks.forEach { (blockTag, handleTag) ->
                val block = bounds(blockTag)
                val column = bounds(handleTag)
                if (right) {
                    assertEquals("$handleTag at the block's right edge", block.right, column.right, 0.5f)
                } else {
                    assertEquals("$handleTag at the block's left edge", block.left, column.left, 0.5f)
                }
            }
            // Leaving the editor shows the recipe's card; back from it, the book again.
            compose.runOnIdle {
                session.closeEditor()
                session.closeCard()
            }
            compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SETTINGS) }
        }
    }
}
