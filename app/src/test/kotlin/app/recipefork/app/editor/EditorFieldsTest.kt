package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * T-271, the editor's fields, each in English, in Russian, and in Russian at twice the font on a 320 dp phone:
 * - the title, the description and a step's text left outside their bounds say so as the field's own supporting text, in
 *   error and with a character counter, no warning row of their own beside them — and only once left (FLOW-028);
 * - a tag of the recipe is removed by its cross alone: a tap on its words does nothing;
 * - an empty step's field says what goes there;
 * - a recipe that cannot be changed shows no empty part, says what the reader can do instead — fork it — and draws its
 *   rows as plain text, not as boxes to tap.
 * (The top bar no longer repeating the title is EditorTopBarTest's.)
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorFieldsTest {
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
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private val flour = Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))
    private val milk = Ingredient(1, null, null, "milk", UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(300))

    private fun open(recipe: Recipe): Recipe {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe
    }

    private fun draft(change: (Recipe) -> Recipe): Recipe =
        change(Recipe.create(now, null).copy(language = RecipeLanguage.EN, servings = 4, ingredients = listOf(flour, milk)))

    private fun stored(recipe: Recipe): Recipe = runBlocking { recipes.get(recipe.id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun unmerged(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun bounds(tag: String): Rect = unmerged(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun px(dp: Float): Float = dp * compose.density.density

    private fun layoutOf(tag: String): TextLayoutResult = layoutOf(unmerged(tag))

    private fun layoutOf(node: SemanticsNode): TextLayoutResult =
        mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()

    /** The text tagged [tag] is laid out whole: nothing cut off, nothing ellipsized, and within the screen's width. */
    private fun assertWhole(tag: String) = assertWhole(unmerged(tag), tag)

    private fun assertWhole(node: SemanticsNode, what: String) {
        val layout = layoutOf(node)
        assertFalse("$what is cut short of its lines", layout.multiParagraph.didExceedMaxLines)
        assertTrue("$what is cut off below", layout.size.height >= layout.multiParagraph.height - 0.5f && node.size.height >= layout.size.height)
        assertFalse("$what is ellipsized", (0 until layout.lineCount).any { layout.isLineEllipsized(it) })
        val b = Rect(node.positionInRoot, node.size.toSize())
        assertTrue("$what runs off the screen: $b", b.left >= -0.5f && b.right <= px(context.resources.configuration.screenWidthDp.toFloat()) + 0.5f)
    }

    /** Puts the author in the field tagged [tag] — opening its step's sheet, or closing any — whatever field they were in. */
    private fun enter(tag: String) {
        compose.reach(tag)
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }

    /** What the field tagged [field] says the screen reader is wrong with it; null while it is not in error. */
    private fun errorOf(field: String): String? = compose.onNodeWithTag(field).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Error)

    /**
     * The mark tagged [mark] shows, reading [text], as the supporting text of the field tagged [field] — merged into it,
     * drawn in the error colour, with the counter [counter] at its end — and the field is in error, saying the same.
     */
    private fun assertFieldMark(field: String, mark: String, text: String, counter: String) {
        compose.waitUntil(PATIENCE_MS) { exists(mark) }
        assertEquals(listOf(text), compose.textsOf(mark))
        assertEquals(listOf(counter), compose.textsOf(EditorTags.counter(mark)))
        listOf(mark, EditorTags.counter(mark)).forEach { part ->
            assertTrue("$part is not the field's own", compose.onAllNodes(hasTestTag(part) and hasAnyAncestor(hasTestTag(field)), useUnmergedTree = true).fetchSemanticsNodes().size == 1)
            assertEquals("$part is not in the error colour", lightColorScheme().error, layoutOf(part).layoutInput.style.color)
        }
        // Under the field's outline, the counter at its end, within the field.
        val outline = bounds(field)
        val counted = bounds(EditorTags.counter(mark))
        assertTrue("$mark is not under the field", bounds(mark).top >= outline.top)
        assertTrue("$mark: the counter is not after the words", counted.left >= bounds(mark).right - 0.5f)
        assertTrue("$mark: the counter $counted is outside the field $outline", counted.right <= outline.right + 0.5f && counted.bottom <= outline.bottom + 0.5f)
        assertEquals(text, errorOf(field))
        assertWhole(mark)
        assertWhole(EditorTags.counter(mark))
        // No warning row of its own: no warning icon is drawn anywhere on the form for it.
        // (A step's row in the list, not a field, keeps its own mark.)
        val rows = Regex("editor-step-\\d+-line")
        val warnings = compose.onAllNodes(hasContentDescription(string(R.string.editor_mark_warning)), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { w -> generateSequence(w) { it.parent }.none { it.config.getOrNull(SemanticsProperties.TestTag)?.matches(rows) == true } }
        assertTrue(
            "a warning row stands beside the field: ${warnings.map { w -> generateSequence(w) { it.parent }.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.toList() }}",
            warnings.isEmpty(),
        )
    }

    // ---- The marks of the title, the description and a step's text, in their fields.

    @Test
    fun theTextFieldsMarksAreTheirSupportingText() =
        fieldMarks(over = "Over the limit. Kept in full.", titleMissing = "A recipe needs a title.", title = "0 / 120", long = "2,001 / 2,000")

    @Test
    @Config(qualifiers = "ru")
    fun theTextFieldsMarksAreTheirSupportingTextInRussian() =
        fieldMarks(over = "Больше лимита. Текст сохранён целиком.", titleMissing = "У рецепта должно быть название.", title = "0 / 120", long = "2 001 / 2 000")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2.0f)
    fun theTextFieldsMarksAreTheirSupportingTextAt320DpAndTwiceTheFontInRussian() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        fieldMarks(over = "Больше лимита. Текст сохранён целиком.", titleMissing = "У рецепта должно быть название.", title = "0 / 120", long = "2 001 / 2 000")
    }

    private fun fieldMarks(over: String, titleMissing: String, title: String, long: String) {
        val recipe = open(draft { it.copy(title = "", description = "d".repeat(2_001), steps = listOf(Step(0, "s".repeat(2_001)))) })
        // Nothing is marked before the author leaves a part (FLOW-028, unchanged).
        listOf(EditorTags.TITLE_MARK, EditorTags.DESCRIPTION_MARK).forEach { assertFalse(it, exists(it)) }
        assertNull(errorOf(EditorTags.TITLE))
        enter(EditorTags.TITLE)
        assertFalse("marked while in the field", exists(EditorTags.TITLE_MARK))

        // The title left empty, then the description left too long: each its field's supporting text.
        enter(EditorTags.DESCRIPTION)
        assertFieldMark(EditorTags.TITLE, EditorTags.TITLE_MARK, titleMissing, title)
        assertFalse("marked while in the field", exists(EditorTags.DESCRIPTION_MARK))
        enter(EditorTags.TITLE)
        assertFieldMark(EditorTags.DESCRIPTION, EditorTags.DESCRIPTION_MARK, over, long)

        // A step's text, left too long, in its sheet.
        enter(EditorTags.stepText(0))
        assertFalse("marked while in the field", exists(EditorTags.stepMark(0)))
        enter(EditorTags.TITLE)
        compose.openStep(0)
        assertFieldMark(EditorTags.stepText(0), EditorTags.stepMark(0), over, long)
        compose.closeStep()

        // Completed, a field loses its mark and is no longer in error.
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performTextReplacement("Pancakes")
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.TITLE_MARK) }
        assertNull(errorOf(EditorTags.TITLE))
        assertEquals("Pancakes", stored(recipe).title)
    }

    // ---- A tag is removed by its cross alone.

    @Test
    fun aTagIsRemovedOnlyByItsCross() = crossAlone("Italian", "Georgian", "Remove the tag Italian")

    @Test
    @Config(qualifiers = "ru")
    fun aTagIsRemovedOnlyByItsCrossInRussian() = crossAlone("итальянская", "грузинская", "Убрать тег итальянская")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2.0f)
    fun aTagIsRemovedOnlyByItsCrossAt320DpAndTwiceTheFontInRussian() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        crossAlone("итальянская", "грузинская", "Убрать тег итальянская")
    }

    private fun crossAlone(italian: String, georgian: String, removeItalian: String) {
        val recipe = open(draft { it.copy(title = "Pasta", tags = setOf(10L, 11L)) })
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.chip(10)) && exists(EditorTags.chip(11)) }
        compose.onNodeWithTag(EditorTags.chip(10)).performScrollTo()
        assertEquals(listOf(italian), compose.textsOf(EditorTags.chip(10)))
        assertEquals(listOf(georgian), compose.textsOf(EditorTags.chip(11)))
        assertWhole(labelOf(EditorTags.chip(10)), "the chip's words")

        // Nothing on the chip but its cross takes a tap: neither the chip nor its words.
        val words = compose.onAllNodes(hasAnyAncestor(hasTestTag(EditorTags.chip(10))) or hasTestTag(EditorTags.chip(10)), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(words.isNotEmpty())
        words.forEach { assertFalse("${AccessibilityChecks.nameOf(it)} takes a tap", AccessibilityChecks.isInteractive.matches(it)) }
        // A tap on the words keeps the tag.
        val label = labelOf(EditorTags.chip(10)).let { Rect(it.positionInRoot, it.size.toSize()) }
        compose.onRoot().performTouchInput { click(Offset(label.left + label.width / 4, label.center.y)) }
        compose.waitForIdle()
        assertEquals(setOf(10L, 11L), stored(recipe).tags)
        assertTrue(exists(EditorTags.chosen(10)))

        // The cross is a button named for the removal, 48 dp, its touch area reaching past the chip as drawn.
        val cross = compose.onNodeWithTag(EditorTags.remove(10)).fetchSemanticsNode()
        assertEquals(removeItalian, AccessibilityChecks.announcedLabel(cross))
        val area = bounds(EditorTags.remove(10))
        assertTrue("the cross's touch area is under 48 dp: $area", area.width >= px(48f) - 0.5f && area.height >= px(48f) - 0.5f)
        val chip = bounds(EditorTags.chip(10))
        val icon = bounds(EditorTags.removeIcon(10))
        assertTrue("the cross is not at the chip's end", icon.left > label.right && icon.right <= chip.right + 0.5f)
        // A tap just above the chip as drawn — shorter than 48 dp — over its cross, is the cross's: it removes the tag.
        assertTrue("the chip is drawn as tall as the cross's touch area", chip.height < area.height - px(2f))
        compose.onRoot().performTouchInput { click(Offset(icon.center.x, chip.top - px(1.5f))) }
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.chosen(10)) }
        assertEquals(setOf(11L), stored(recipe).tags)

        // And a tap on the cross itself.
        compose.onNodeWithTag(EditorTags.remove(11)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.chosen(11)) }
        assertEquals(emptySet<Long>(), stored(recipe).tags)
    }

    /** The words drawn inside the chip tagged [chip]. */
    private fun labelOf(chip: String): SemanticsNode =
        compose.onNode(hasAnyAncestor(hasTestTag(chip)) and hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNode()

    // ---- An empty step's field says what goes there.

    @Test
    fun anEmptyStepSaysWhatGoesThere() = placeholder("What to do in this step")

    @Test
    @Config(qualifiers = "ru")
    fun anEmptyStepSaysWhatGoesThereInRussian() = placeholder("Что сделать на этом шаге")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2.0f)
    fun anEmptyStepSaysWhatGoesThereAt320DpAndTwiceTheFontInRussian() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        placeholder("Что сделать на этом шаге")
    }

    private fun placeholder(said: String) {
        val recipe = open(draft { it.copy(title = "Tea") })
        compose.addStepAt(0)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPlaceholder(0)) }
        // In the field — read with it by the screen reader — and whole, however large the text.
        assertEquals(listOf(said), compose.textsOf(EditorTags.stepPlaceholder(0)))
        assertTrue(
            compose.onAllNodes(hasTestTag(EditorTags.stepPlaceholder(0)) and hasAnyAncestor(hasTestTag(EditorTags.stepText(0))), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1,
        )
        compose.onNodeWithTag(EditorTags.stepText(0)).assert(hasText(said))
        assertWhole(EditorTags.stepPlaceholder(0))
        val field = bounds(EditorTags.stepText(0))
        val shown = bounds(EditorTags.stepPlaceholder(0))
        assertTrue("the placeholder $shown is outside the field $field", shown.top >= field.top - 0.5f && shown.bottom <= field.bottom + 0.5f)
        // Once something is typed, it goes; emptied again, it is back.
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("Boil")
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepPlaceholder(0)) }
        compose.waitUntil(PATIENCE_MS) { stored(recipe).steps.singleOrNull()?.text == "Boil" }
    }

    // ---- A recipe that cannot be changed.

    @Test
    fun aFrozenRecipeShowsNoEmptyPartSaysToForkAndDrawsNoBoxes() = frozen()

    @Test
    @Config(qualifiers = "ru")
    fun aFrozenRecipeShowsNoEmptyPartSaysToForkAndDrawsNoBoxesInRussian() = frozen()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2.0f)
    fun aFrozenRecipeShowsNoEmptyPartSaysToForkAndDrawsNoBoxesAt320DpAndTwiceTheFontInRussian() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        frozen()
    }

    private fun frozen() {
        open(
            draft { it.copy(title = "Pancakes", steps = listOf(Step(0, "Mix."), Step(1, "Fry."))) }
                .copy(sent = SentStatus.SENT).inState(RecipeState.PUBLISHED),
        )
        compose.waitUntil(PATIENCE_MS) { exists(StepSheetTags.line(1)) }
        // No tags, no description: no part for either, not even its heading.
        assertFalse("an empty Tags part", exists(EditorTags.TAGS))
        assertFalse("an empty description", exists(EditorTags.DESCRIPTION))
        assertTrue(compose.onAllNodes(hasText(string(R.string.editor_tags)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // The notice says what the reader can do: fork it, by the card's own Fork.
        val notice = compose.textsOf(EditorTags.FROZEN).single()
        assertEquals(string(R.string.editor_frozen), notice)
        assertTrue("the notice does not name the card's Fork: $notice", notice.contains(string(R.string.card_fork)))
        assertWhole(EditorTags.FROZEN)
        // The rows read as plain text at the form's margin, as the headings do — no box, nothing to tap.
        val margin = bounds(EditorTags.INGREDIENTS).left
        listOf(IngredientSheetTags.line(0), IngredientSheetTags.line(1), StepSheetTags.line(0), StepSheetTags.line(1)).forEach { row ->
            compose.onNodeWithTag(row, useUnmergedTree = true).performScrollTo()
            assertEquals("$row is inset as in a box", margin, bounds(row).left, 0.5f)
        }
        listOf(EditorTags.ingredient(0), EditorTags.ingredient(1), EditorTags.step(0), EditorTags.step(1)).forEach { row ->
            assertEquals("$row is inset as in a box", margin, bounds(row).left, 0.5f)
            val within = compose.onAllNodes(hasAnyAncestor(hasTestTag(row)) or hasTestTag(row), useUnmergedTree = true).fetchSemanticsNodes()
            within.forEach { assertFalse("$row takes a tap", AccessibilityChecks.isInteractive.matches(it)) }
        }
    }

    // A frozen recipe with tags still shows them, with nothing to remove them by.
    @Test
    fun aFrozenRecipeWithTagsShowsThem() {
        open(draft { it.copy(title = "Pasta", tags = setOf(10L)) }.copy(sent = SentStatus.SENT).inState(RecipeState.PUBLISHED))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.chosen(10)) }
        assertTrue(exists(EditorTags.TAGS))
        assertFalse(exists(EditorTags.remove(10)))
    }
}
