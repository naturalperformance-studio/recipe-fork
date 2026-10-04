package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.EditedAppTheme
import app.recipefork.app.ui.appColorScheme
import app.recipefork.app.ui.appTypography
import app.recipefork.app.ui.editedThemeResources
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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
 * The card's controls and type come from the theme file (ADR 20261004): with `app_theme.xml` edited, the step text
 * takes the edited title size and stays larger than body text (A11Y-007); − and + are tonal icon buttons of the card's
 * 56 dp in the edited tonal colour; the ingredients sit on the edited tonal surface; the fixing control's actions are a
 * filled and text buttons in the edited colours; the ⋮'s Delete is in the edited error colour.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp")
class CardThemeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { emptyList() }
    private val now = Instant.parse("2026-10-04T12:00:00Z")

    private val recipe = Recipe.create(now, null).copy(
        title = "Pancakes",
        servings = 4,
        state = RecipeState.LOCAL,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
        ),
        steps = listOf(Step(0, "Whisk the flour with the milk")),
    )

    /** The theme file as an owner might edit it: other tones, another error colour, a larger title size. */
    private val resources by lazy {
        editedThemeResources(
            compose.activity,
            colors = mapOf(
                R.color.theme_light_primary to Color(0xFF00695C),
                R.color.theme_light_secondaryContainer to Color(0xFFB2EBF2),
                R.color.theme_light_surfaceContainerLow to Color(0xFFFFF3E0),
                R.color.theme_light_error to Color(0xFF8E0000),
            ),
            sizes = mapOf(R.dimen.theme_type_titleLarge to EDITED_TITLE_SP),
        )
    }
    private val scheme: ColorScheme by lazy { appColorScheme(resources, dark = false) }

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        runBlocking { recipes.save(recipe) }
        compose.setContent { EditedAppTheme(resources) { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(0))).fetchSemanticsNodes().size == 1 }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun layoutOf(matcher: SemanticsMatcher): TextLayoutResult =
        mutableListOf<TextLayoutResult>().also {
            compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(it)
        }.single()

    /** The colour of the label [text] within the element tagged [tag]. */
    private fun labelColour(tag: String, text: String): Color =
        layoutOf(hasAnyAncestor(hasTestTag(tag)) and hasText(text)).layoutInput.style.color

    /** The colour drawn at ([x], [y]) — fractions of its width and height — of the element tagged [tag]. */
    private fun colourAt(tag: String, x: Float, y: Float): Color {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
    }

    private fun assertSameColour(what: String, expected: Color, actual: Color) {
        val close = listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue)
            .all { (e, a) -> kotlin.math.abs(e - a) < 2f / 255 }
        assertTrue("$what is $actual, not $expected", close)
    }

    @Test
    fun aTitleSizeChangedInTheThemeFileShowsOnTheStepsWhichStayLargerThanBodyText() {
        val step = layoutOf(hasTestTag(StepTags.text(0))).layoutInput.style.fontSize
        assertEquals(EDITED_TITLE_SP.sp, step)
        val body = appTypography(resources)
        listOf(body.bodyLarge, body.bodyMedium, body.bodySmall).forEach {
            assertTrue("body text at ${it.fontSize} is smaller than the step at $step", it.fontSize.value < step.value)
        }
    }

    @Test
    fun fewerAndMoreAreTonalIconButtonsOfTheCardsSizeInTheThemesTone() {
        listOf(CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE).forEach { tag ->
            val node = compose.onNodeWithTag(tag).performScrollTo().assertIsEnabled().fetchSemanticsNode()
            assertEquals(Role.Button, node.config[SemanticsProperties.Role])
            val density = node.layoutInfo.density.density
            assertEquals(CardTarget.value, node.size.width / density, 0.5f)
            assertEquals(CardTarget.value, node.size.height / density, 0.5f)
            // Above the icon, inside the button's container.
            assertSameColour("$tag's container", scheme.secondaryContainer, colourAt(tag, 0.5f, 0.2f))
        }
    }

    @Test
    fun theIngredientsSitOnTheThemesTonalSurface() {
        compose.onNodeWithTag(IngredientTags.SURFACE).performScrollTo()
        assertSameColour("the ingredients' surface", scheme.surfaceContainerLow, colourAt(IngredientTags.SURFACE, 0.5f, 0.02f))
        // The section lies on it.
        val surface = compose.onNodeWithTag(IngredientTags.SURFACE).fetchSemanticsNode().boundsInRoot
        val section = compose.onNodeWithTag(CardTags.INGREDIENTS).fetchSemanticsNode().boundsInRoot
        assertTrue("$section within $surface", surface.contains(section.topLeft) && surface.contains(section.center))
    }

    @Test
    fun theFixingActionsAreAFilledAndTextButtonsInTheThemesColours() {
        compose.onNodeWithTag(CardTags.ingredient(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX_FIELD)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextInput("300")
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled()
        listOf(CardTags.FIX_CONFIRM, CardTags.FIX_CANCEL).forEach { tag ->
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals(Role.Button, node.config[SemanticsProperties.Role])
            compose.assertCardTarget(tag)
        }
        assertSameColour("Recalculate's label", scheme.onPrimary, labelColour(CardTags.FIX_CONFIRM, "Recalculate"))
        assertSameColour("Recalculate's container", scheme.primary, colourAt(CardTags.FIX_CONFIRM, 0.5f, 0.1f))
        assertSameColour("Cancel's label", scheme.primary, labelColour(CardTags.FIX_CANCEL, "Cancel"))
    }

    @Test
    fun theMenusDeleteIsInTheThemesErrorColour() {
        compose.onNodeWithTag(CardTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.DELETE)).fetchSemanticsNodes().isNotEmpty() }
        val delete = context.getString(R.string.library_entry_delete)
        assertSameColour("the menu's $delete", scheme.error, labelColour(CardTags.DELETE, delete))
    }

    private companion object {
        /** The title size of the edited theme file, larger than Material's 22 sp. */
        const val EDITED_TITLE_SP = 30
    }
}
