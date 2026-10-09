package app.recipefork.app.card

import app.recipefork.app.data.HeldLastCardTab
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.TestTimers
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * DOM-023 on the card: an ingredient's note follows its name in a muted style, reads as written at every scale, and
 * wraps whole with the name at large text on a narrow phone. And, from the done marks (FLOW-068): the target over a
 * short amount never lies over the name beside it, so a tap at the name's start marks the line.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardIngredientNotesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private val timers = TestTimers(context)
    private val scheme = lightColorScheme()

    @Before
    fun setUp() {
        listOf(book.idling, timers.idling).forEach(compose::registerIdlingResource)
    }

    @After
    fun close() {
        listOf(book.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        book.close()
        timers.close()
    }

    private fun piece(position: Int, name: String, count: Long, note: String? = null) = Ingredient(
        position, null, freeTextName = name, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(count),
        divisible = true, note = note,
    )

    private fun show(recipe: Recipe) {
        runBlocking { recipes.save(recipe) }
        compose.setContent {
            MaterialTheme(colorScheme = scheme) {
                CardScreen(recipe.id, recipes, CardEnvironment(lastTab = HeldLastCardTab(CardMode.COOKING), ingredients = IngredientEntries { emptyList() }), onBack = {}, onEdit = {})
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(0))).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun unmerged(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun layoutOf(node: SemanticsNode): TextLayoutResult =
        mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()

    /** The colour the text of the line at [position] is drawn in where [part] of it stands. */
    private fun colourOf(position: Int, part: String): androidx.compose.ui.graphics.Color {
        val layout = layoutOf(unmerged(IngredientTags.name(position)))
        val text = layout.layoutInput.text
        val start = text.text.indexOf(part)
        assertTrue("\"$part\" in \"${text.text}\"", start >= 0)
        return text.spanStyles.lastOrNull { it.start <= start && it.end >= start + part.length }?.item?.color
            ?.takeIf { it.isSpecified() } ?: layout.layoutInput.style.color
    }

    private fun androidx.compose.ui.graphics.Color.isSpecified() = this != androidx.compose.ui.graphics.Color.Unspecified

    // The note follows the name, muted, the name itself in the line's own colour; a change of servings scales the
    // quantity and leaves the note as written.
    @Test
    fun theNoteFollowsTheNameMutedAndIsNeverScaled() {
        show(
            Recipe.create(now, null).copy(
                title = "Onion soup",
                servings = 2,
                ingredients = listOf(
                    piece(0, "Onion", 2, note = "finely chopped"),
                    Ingredient(1, null, freeTextName = "Butter", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(50), note = "room temperature"),
                    piece(2, "Bay leaf", 1),
                ),
                steps = listOf(Step(0, "Cook.")),
            ),
        )
        assertEquals(listOf("Onion — finely chopped", "2 pc"), compose.textsOf(CardTags.ingredient(0)))
        assertEquals(listOf("Butter — room temperature", "50 g"), compose.textsOf(CardTags.ingredient(1)))
        assertEquals(listOf("Bay leaf", "1 pc"), compose.textsOf(CardTags.ingredient(2)))
        assertEquals(scheme.onSurfaceVariant, colourOf(0, "finely chopped"))
        assertEquals(scheme.onSurfaceVariant, colourOf(1, "room temperature"))
        assertEquals(scheme.onSurface, colourOf(0, "Onion"))

        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SERVINGS).single() == "Servings: 3" }
        assertEquals(listOf("Onion — finely chopped", "3 pc"), compose.textsOf(CardTags.ingredient(0)))
        assertEquals(listOf("Butter — room temperature", "75 g"), compose.textsOf(CardTags.ingredient(1)))
        // The lines as computed say the same: the note is the author's at every scale.
        val ingredients = Recipe.create(now, null).copy(ingredients = listOf(piece(0, "Onion", 2, note = " finely chopped "))).ingredients
        listOf(Rational.of(2), Rational.of(7), Rational.of(1, 2)).forEach { servings ->
            assertEquals("finely chopped", ingredientLines(ingredients, emptyMap(), InterfaceLanguage.ENGLISH, 2, servings).single().note)
        }
    }

    // With one-digit amounts ("2 pc", "1 pc") the amount's target keeps its card width and ends where the name begins:
    // a tap at the name's very start marks the line and opens no fixing; a tap on the amount still fixes.
    @Test
    fun aTapAtTheStartOfTheNameMarksTheLineEvenBesideAShortAmount() {
        show(
            Recipe.create(now, null).copy(
                title = "Lemonade",
                servings = 2,
                ingredients = listOf(piece(0, "Eggs", 2), piece(1, "Lemon", 1)),
                steps = listOf(Step(0, "Mix.")),
            ),
        )
        listOf(0, 1).forEach { position ->
            compose.assertCardTarget(CardTags.fixAmount(position))
            val amount = unmerged(CardTags.fixAmount(position)).boundsInRoot
            val name = unmerged(IngredientTags.name(position)).boundsInRoot
            assertTrue("the amount ($amount) ends before the name ($name)", amount.right <= name.left + 0.5f)
        }
        val line = unmerged(CardTags.ingredient(1)).boundsInRoot
        val name = unmerged(IngredientTags.name(1)).boundsInRoot
        compose.onNodeWithTag(CardTags.ingredient(1)).performTouchInput {
            click(Offset(name.left - line.left + 1f, (name.top + name.bottom) / 2 - line.top))
        }
        compose.waitForIdle()
        assertEquals("Done", compose.onNodeWithTag(CardTags.ingredient(1)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        assertFalse(compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty())

        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
    }
}

/** At 2× text on a 320 dp phone, a long name with a long note wraps whole within its line, nothing of it cut. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardIngredientNoteWrapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun aLongNameWithANoteWrapsWithoutClipping() {
        val name = "Skinless chicken thigh fillets, boned"
        val note = "cut into large cubes, at room temperature"
        val lines = listOf(IngredientLine(name, "1250 g", note = note), IngredientLine("Salt", "1 tsp"))
        val ordered = lines.indices.map {
            Ingredient(it, null, freeTextName = lines[it].name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.ONE)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).padding(horizontal = 16.dp)) {
                        Column { IngredientsList(ordered, IngredientList(lines, holdsProportions = true), emptyList(), InterfaceLanguage.ENGLISH, onFix = {}, onRelease = {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.assertWhole(CardTags.ingredient(0))
        val text = compose.onNodeWithTag(IngredientTags.name(0), useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { text.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()
        assertEquals("$name — $note", layout.layoutInput.text.text)
        assertTrue("it wraps", layout.lineCount > 2)
        assertFalse("nothing is cut", layout.hasVisualOverflow)
        // The text lies whole within its line, and the line within the screen's content.
        val line = compose.onNodeWithTag(CardTags.ingredient(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bounds = text.boundsInRoot
        val edge = with(compose.density) { 304.dp.toPx() }
        assertTrue("$bounds in $line", bounds.top >= line.top - 0.5f && bounds.bottom <= line.bottom + 0.5f && bounds.right <= line.right + 0.5f)
        assertTrue("$line on screen", line.right <= edge + 0.5f)
        assertEquals(layout.size.height.toFloat(), bounds.height, 0.5f)
    }
}
