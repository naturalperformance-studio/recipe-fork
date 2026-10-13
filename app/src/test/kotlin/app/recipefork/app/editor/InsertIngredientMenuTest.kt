package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * T-272, scenario 6: "Insert ingredient" lists each ingredient of the recipe once. The row inserts the whole; a part is
 * the second choice, a "Part…" button at the end of a row holding a quantity — named for its ingredient, 48 dp, beside
 * the row's words and never over them — which asks for the part and inserts it. In English, in Russian, and in both at
 * twice the font on a 320 dp phone.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class InsertIngredientMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-13T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)

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

    @Test
    fun eachIngredientOnceAndAPartByItsButton() = menu(
        names = listOf("olive oil", "garlic", "salt", "boneless chicken thighs"),
        twoOfGarlic = "2 pc",
    )

    @Test
    @Config(qualifiers = "ru")
    fun eachIngredientOnceAndAPartByItsButtonInRussian() = menu(
        names = listOf("оливковое масло", "чеснок", "соль", "куриные бёдра без кости"),
        twoOfGarlic = "2 шт",
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2.0f)
    fun eachIngredientOnceAndAPartByItsButtonAt320DpAndTwiceTheFont() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        menu(names = listOf("olive oil", "garlic", "salt", "boneless chicken thighs"), twoOfGarlic = "2 pc")
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2.0f)
    fun eachIngredientOnceAndAPartByItsButtonAt320DpAndTwiceTheFontInRussian() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        menu(names = listOf("оливковое масло", "чеснок", "соль", "куриные бёдра без кости"), twoOfGarlic = "2 шт")
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun unmerged(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun bounds(node: SemanticsNode) = Rect(node.positionInRoot, node.size.toSize())

    private fun px(dp: Float): Float = dp * compose.density.density

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun menu(names: List<String>, twoOfGarlic: String) {
        val (oil, garlic, salt, thighs) = names
        val recipe = Recipe.create(created, null).copy(
            title = "Chicken",
            servings = 4,
            ingredients = listOf(
                Ingredient(0, null, null, oil, UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(45)),
                Ingredient(1, null, null, garlic, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(4), divisible = false),
                Ingredient(2, null, null, salt, UnitType.TASTE_UNMEASURED, null, null),
                Ingredient(3, null, null, thighs, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(800)),
            ),
            steps = listOf(Step(0, "")),
        ).also { runBlocking { recipes.save(it) } }
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)

        openMenu()
        val rows = compose.onAllNodes(hasTagStartingWith("editor-step-0-offer-")).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }
        // Each row once, in the recipe's order — no second row for a part.
        assertEquals((0..3).map { EditorTags.stepOffer(0, it) }, rows)
        names.forEach { name ->
            val said = compose.onAllNodes(hasText(name, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { node -> generateSequence(node) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("editor-step-0-offer-") == true } }
            assertEquals("$name is listed ${said.size} times", 1, said.size)
        }
        // A part where the row holds a quantity, by the button at the row's end; none for "to taste".
        assertEquals(listOf(true, true, false, true), (0..3).map { exists(EditorTags.stepOfferPart(0, it)) })
        listOf(0 to oil, 1 to garlic, 3 to thighs).forEach { (row, name) ->
            val button = unmerged(EditorTags.stepOfferPart(0, row))
            assertTrue(
                "the part of $name is not in its row",
                generateSequence(button) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag) == EditorTags.stepOffer(0, row) },
            )
            assertEquals(listOf(string(R.string.editor_step_offered_part)), compose.textsOf(EditorTags.stepOfferPart(0, row)))
            // Named for its ingredient, so the reader tells one row's part from another's.
            assertEquals(
                listOf(string(R.string.editor_step_offered_part_name, name)),
                compose.onNodeWithTag(EditorTags.stepOfferPart(0, row)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
            )
            // 48 dp at least, after the row's words and inside the row, which stays one target to insert the whole.
            val area = bounds(button)
            assertTrue("the part of $name is under 48 dp: $area", area.width >= px(48f) - 0.5f && area.height >= px(48f) - 0.5f)
            val rowBounds = bounds(unmerged(EditorTags.stepOffer(0, row)))
            val words = compose.onAllNodes(
                hasAnyAncestor(hasTestTag(EditorTags.stepOffer(0, row))) and hasText(name, substring = true),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().single()
            assertTrue("the words of $name run under its part button", bounds(words).right <= area.left + 0.5f)
            assertTrue("the part of $name stands outside its row", area.left >= rowBounds.left && area.right <= rowBounds.right + 0.5f)
            assertTrue(SemanticsActions.OnClick in unmerged(EditorTags.stepOffer(0, row)).config)
        }
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 7)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 7)

        // The part path: the button asks for the part of garlic, which is inserted.
        compose.onNodeWithTag(EditorTags.stepOfferPart(0, 1)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.PART_FIELD), PATIENCE_MS)
        assertFalse(exists(EditorTags.stepOffer(0, 0)))
        compose.onNodeWithTag(EditorTags.PART_FIELD).performTextReplacement("2")
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTags.PART_INSERT).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.PART_FIELD), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { stored(recipe.id).steps.single().ingredientReferences.isNotEmpty() }
        assertEquals(
            listOf(IngredientReference(1, 0, IngredientShare(Rational.of(2), QuantityUnit.PIECE))),
            stored(recipe.id).steps.single().ingredientReferences,
        )
        compose.openStep(0)
        assertEquals(twoOfGarlic, compose.onNodeWithTag(EditorTags.stepText(0)).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.trim())

        // The whole path: a tap on the row inserts the whole.
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInputSelection(TextRange(0))
        compose.waitForIdle()
        openMenu()
        compose.onNodeWithTag(EditorTags.stepOffer(0, 3)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { stored(recipe.id).steps.single().ingredientReferences.size == 2 }
        val whole = stored(recipe.id).steps.single().ingredientReferences.single { it.ingredientPosition == 3 }
        assertNull(whole.share)
    }

    private fun openMenu() {
        compose.openStep(0)
        compose.onNodeWithTag(EditorTags.stepInsert(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOffer(0, 0)), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun hasTagStartingWith(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
}
