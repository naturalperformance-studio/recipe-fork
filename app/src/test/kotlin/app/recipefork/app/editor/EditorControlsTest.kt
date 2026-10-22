package app.recipefork.app.editor

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.appColorScheme
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * The editor's controls are Material 3's (v0.21): its text actions are buttons announced by what they do, their corners
 * the theme's; a removal — an ingredient row, a step, a timer — is drawn in the error colour.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorControlsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val scheme = lightColorScheme()

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

    private fun open() {
        val recipe = Recipe.create(now, null).copy(
            title = "Pancakes",
            language = RecipeLanguage.EN,
            ingredients = listOf(Ingredient(0, null, null, "Flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))),
            steps = listOf(Step(0, "Mix flour.", timer = Timer(5.minutes, "Rest"))),
        )
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    /** What the node tagged [tag] draws, scrolled to first unless it stands in the top bar. */
    private fun image(tag: String): PixelMap {
        val node = compose.onNodeWithTag(tag)
        if (tag != EditorTags.DONE) node.performScrollTo()
        return node.captureToImage().toPixelMap()
    }

    private fun close(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) < 0.06f

    private fun PixelMap.draws(colour: Color): Boolean =
        (0 until width).any { x -> (0 until height).any { y -> close(this[x, y], colour) } }

    // Done, Add ingredient, Add step and "No language" are buttons at least 48 dp high, announced by what they do; the
    // two Adds are filled tonal buttons.
    @Test
    fun theTextActionsAreButtonsAnnouncedByWhatTheyDo() {
        open()
        listOf(
            EditorTags.DONE to R.string.editor_done_action,
            EditorTags.INGREDIENT_ADD to R.string.editor_ingredient_add_action,
            EditorTags.STEP_ADD to R.string.editor_step_add_action,
            EditorTags.LANGUAGE_CLEAR to R.string.editor_language_clear_action,
        ).forEach { (tag, action) ->
            val node = compose.onNodeWithTag(tag).apply { if (tag != EditorTags.DONE) performScrollTo() }.fetchSemanticsNode()
            assertEquals(tag, Role.Button, node.config[SemanticsProperties.Role])
            assertEquals(tag, context.getString(action), AccessibilityChecks.clickActionLabel(node))
            assertTrue(tag, node.boundsInRoot.height >= node.layoutInfo.density.run { 48.dp.toPx() } - 0.5f)
        }
        assertTrue(image(EditorTags.INGREDIENT_ADD).draws(scheme.secondaryContainer))
        assertTrue(image(EditorTags.STEP_ADD).draws(scheme.secondaryContainer))
    }

    // The unit, in the row's sheet, is Material 3's exposed drop-down: a read-only outlined field labelled Unit, holding
    // the unit, whose tap opens the menu of units.
    @Test
    fun theUnitIsAReadOnlyFieldOverItsMenu() {
        open()
        compose.openIngredient(0)
        val node = compose.onNodeWithTag(EditorTags.ingredientUnit(0)).performScrollTo().fetchSemanticsNode()
        assertEquals(context.getString(R.string.editor_unit_gram), node.config[SemanticsProperties.EditableText].text)
        assertFalse("not typed into", node.config.contains(SemanticsActions.SetText))
        assertEquals(context.getString(R.string.editor_ingredient_unit), compose.textsOf(EditorTags.ingredientUnit(0)).first())
        assertEquals(context.getString(R.string.editor_ingredient_unit_action), AccessibilityChecks.clickActionLabel(node))
        compose.onNodeWithTag(EditorTags.ingredientUnit(0)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(QuantityUnit.KILOGRAM)), PATIENCE_MS)
    }

    // Scenario 3: removing an ingredient row — in its sheet —, a step or a timer is drawn in the error colour; the
    // sheet's other actions are not.
    @Test
    fun theRemovalsAreInTheErrorColour() {
        open()
        // A step's removal and its timer's are in its sheet.
        compose.openStep(0)
        listOf(EditorTags.stepRemove(0), EditorTags.timerRemove(0)).forEach { tag ->
            assertTrue("$tag in the error colour", image(tag).draws(scheme.error))
        }
        compose.closeStep()
        compose.openIngredient(0)
        assertTrue("the row's removal in the error colour", image(EditorTags.ingredientRemove(0)).draws(scheme.error))
        assertFalse(image(EditorTags.ingredientAction(0, RowAction.INSERT_ABOVE)).draws(scheme.error))
    }

    // Scenario 1: a corner size changed in app_theme.xml reaches an editor button, whose shape is the theme's.
    @Test
    fun aChangedCornerInTheThemeReachesAnEditorButton() {
        val base = compose.activity
        @Suppress("DEPRECATION")
        val square = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
                super.getValue(id, outValue, resolveRefs)
                // A zero mantissa in dp: 0 dp, square corners.
                if (id == R.dimen.theme_shape_extraLarge) outValue.data = TypedValue.COMPLEX_UNIT_DIP shl TypedValue.COMPLEX_UNIT_SHIFT
            }
        }
        val edited = object : ContextWrapper(base) {
            override fun getResources(): Resources = square
        }
        var changed by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides if (changed) edited else base) {
                AppTheme {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
                        EditorButton("Add ingredient", "Add an ingredient", {}, Modifier.testTag(BUTTON), ButtonWeight.TONAL)
                    }
                }
            }
        }
        // The fill of AppTheme's light scheme, which the button is drawn in here.
        val fill = appColorScheme(base.resources, dark = false).secondaryContainer
        // The button's own top left corner: its fill's first line down its middle, a couple of pixels in from its left.
        fun corner(): Color {
            compose.waitForIdle()
            val image = compose.onNodeWithTag(BUTTON).captureToImage().toPixelMap()
            val top = (0 until image.height).first { close(image[image.width / 2, it], fill) }
            return image[2, top + 2]
        }
        assertFalse("the theme's default corners are round", close(corner(), fill))
        changed = true
        assertTrue("square corners from the edited theme", close(corner(), fill))
    }

    private companion object {
        const val BUTTON = "button"
    }
}
