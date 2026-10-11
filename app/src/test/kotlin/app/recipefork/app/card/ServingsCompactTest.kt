package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.settings.UsualServingsDialog
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.LocalHaptics
import app.recipefork.app.ui.RecordingHaptics
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
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
 * T-224, SCALE-011, A11Y-003: the − and + of the serving count are drawn smaller than before — 40 dp in each direction
 * — on the card's View and Cooking tabs and in the usual-servings dialog, while each half's target, measured on its
 * node, stays at least A11Y-003's figure there (48 dp on View and in the dialog, 56 dp on Cooking), the two targets
 * do not overlap, and a touch just outside the drawing but inside the target still steps the count, with its tick.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class ServingsCompactTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { emptyList() }
    private val haptics = RecordingHaptics()

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun openCard(tab: CardMode) {
        val recipe = Recipe.create(Instant.parse("2026-10-12T12:00:00Z"), null).copy(
            title = "Omelette",
            servings = 4,
            ingredients = listOf(
                Ingredient(0, null, freeTextName = "Eggs", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
            ),
        )
        runBlocking { recipes.save(recipe) }
        compose.setContent {
            CompositionLocalProvider(LocalHaptics provides haptics) {
                MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
            }
        }
        compose.awaitCard()
        compose.chooseCardTab(tab)
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo()
    }

    @Test
    fun onViewTheHalvesAreDrawnSmallerAndAnswerATouchNearTheDrawing() {
        openCard(CardMode.VIEW)
        compact(CardTags.SERVINGS, CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE, least = 48.dp, from = 4)
    }

    @Test
    fun onCookingTheHalvesAreDrawnSmallerAndAnswerATouchNearTheDrawing() {
        openCard(CardMode.COOKING)
        compact(CardTags.SERVINGS, CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE, least = CardTarget, from = 4)
    }

    @Test
    fun inTheUsualServingsDialogTheHalvesAreDrawnSmallerAndAnswerATouchNearTheDrawing() {
        compose.setContent {
            CompositionLocalProvider(LocalHaptics provides haptics) {
                MaterialTheme {
                    UsualServingsDialog(Rational.of(4), notSet = false, language = InterfaceLanguage.ENGLISH, onSet = {}, onDismiss = {})
                }
            }
        }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(SettingsTags.USUAL_MORE)).fetchSemanticsNodes().size == 1 }
        compact(SettingsTags.USUAL_COUNT, SettingsTags.USUAL_FEWER, SettingsTags.USUAL_MORE, least = 48.dp, from = 4)
    }

    /**
     * Checks the halves tagged [fewer] and [more] beside the count tagged [count], which shows [from]: each drawn at most
     * 40 dp and smaller than its target, the target at least [least] and holding the drawing, the targets apart; then
     * touches each target just outside its drawing — beside it and above or below it — and sees the count step each time.
     */
    private fun compact(count: String, fewer: String, more: String, least: Dp, from: Int) {
        val density = compose.onNodeWithTag(fewer).fetchSemanticsNode().layoutInfo.density.density
        fun dp(px: Float) = px / density
        fun target(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        fun drawing(tag: String): Rect = compose.onNodeWithTag(stepDrawingTag(tag), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        listOf(fewer, more).forEach { tag ->
            val target = target(tag)
            val drawing = drawing(tag)
            assertTrue("$tag's drawing is at most 40 dp wide: ${dp(drawing.width)}", dp(drawing.width) <= 40.5f)
            assertTrue("$tag's drawing is at most 40 dp tall: ${dp(drawing.height)}", dp(drawing.height) <= 40.5f)
            assertTrue("$tag's target is at least $least wide: ${dp(target.width)}", dp(target.width) >= least.value - 0.5f)
            assertTrue("$tag's target is at least $least tall: ${dp(target.height)}", dp(target.height) >= least.value - 0.5f)
            assertTrue("$tag's drawing is smaller than its target", drawing.width < target.width && drawing.height < target.height)
            assertTrue("$tag's drawing $drawing lies in its target $target",
                drawing.left >= target.left - 0.5f && drawing.right <= target.right + 0.5f &&
                    drawing.top >= target.top - 0.5f && drawing.bottom <= target.bottom + 0.5f)
        }
        assertFalse("the targets do not overlap", target(fewer).overlaps(target(more)))
        assertTrue("− comes before +", target(fewer).right <= target(more).left + 0.5f)

        val near = 2f * density
        fun touch(tag: String, at: (Rect) -> Offset) {
            val target = target(tag)
            val drawing = drawing(tag)
            val point = at(drawing)
            assertTrue("$point is in $tag's target $target", target.contains(point))
            assertFalse("$point is outside $tag's drawing $drawing", drawing.contains(point))
            compose.onNodeWithTag(tag).performTouchInput { click(point - target.topLeft) }
            compose.waitForIdle()
        }
        fun shown() = compose.textsOf(count).single()
        assertEquals("Servings: $from", shown())
        touch(fewer) { Offset(it.left - near, it.center.y) }
        assertEquals("beside −", "Servings: ${from - 1}", shown())
        touch(fewer) { Offset(it.center.x, it.top - near) }
        assertEquals("above −", "Servings: ${from - 2}", shown())
        touch(more) { Offset(it.right + near, it.center.y) }
        assertEquals("beside +", "Servings: ${from - 1}", shown())
        touch(more) { Offset(it.center.x, it.bottom + near) }
        assertEquals("below +", "Servings: $from", shown())
        assertEquals(List(4) { Haptic.TICK }, haptics.all)
    }
}
