package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The compact step block on a 360 dp phone: the step's text across the whole block beside its drag column, and no
 * control on it (T-211); a one-line step a fraction of its v0.5 height. In the step's sheet, under its text, one row of
 * icons — insert an ingredient, timer, photo — each 48 dp, named for the screen reader by its step and shown in a
 * tooltip on a long press; the timer's form and the photograph under that row only when the step has them; at double
 * font scale every control whole and 48 dp.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
class StepBlockCompactTest(private val handleRight: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-02T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)

    /** True while the screen shows the v0.5 step block, measured before the editor opens. */
    private val former = mutableStateOf(false)
    private var formerHeight = 0f
    private var formerWidth = 0f

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

    /** Opens, at [fontScale], a recipe of a one-line step, a step with a timer and a last one-line step. */
    private fun open(fontScale: Float, state: RecipeState? = null) {
        var recipe = Recipe.create(created, null).copy(
            title = "Pasta",
            steps = listOf(Step(0, "Boil the water."), Step(1, "Cook the pasta.", timer = Timer(10.minutes)), Step(2, "Drain.")),
        )
        if (state != null) recipe = recipe.inState(state)
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context), dragHandleSide = HeldDragHandleSide(handleRight)) { created.plusSeconds(60) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    if (former.value) V05StepBlock() else LibraryOrEditor(session, recipes, environment, opened)
                }
            }
        }
        if (former.value) {
            compose.waitUntilExactlyOneExists(hasTestTag(V05_BLOCK), PATIENCE_MS)
            formerHeight = dp(bounds(V05_BLOCK).height)
            formerWidth = bounds(V05_BLOCK).width
            compose.runOnIdle { former.value = false }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(2)), PATIENCE_MS)
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun dp(px: Float): Float = px / compose.density.density

    /**
     * The text buttons of the sheet of the step at [position], under its text: inserting an ingredient (a labelled button
     * since T-218), then its parts' (T-212) — adding a timer, a photo or clip.
     */
    private fun buttons(position: Int) = listOf(EditorTags.stepInsert(position), EditorTags.timerAttach(position), EditorTags.stepPhoto(position))

    /** What the screen reader speaks for each control of the step at [position]: its buttons, its removal. */
    private fun labels(position: Int): List<String> =
        (buttons(position) + EditorTags.stepRemove(position)).map { AccessibilityChecks.announcedLabel(compose.onNodeWithTag(it).fetchSemanticsNode()) }

    /** What a click on each of the parts' buttons of the step at [position] is announced as. */
    private fun clickLabels(position: Int): List<String?> =
        buttons(position).map { AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(it).fetchSemanticsNode()) }

    // A one-line step at 360 dp and font scale 1.0 is at most 40 % of the height its block had in v0.5: that block,
    // rebuilt as v0.5 laid it out, measured here first, at the same width and font.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aOneLineStepIsAtMostFortyPercentOfItsFormerHeight() = oneLineStepIsCompact()

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun aOneLineStepIsAtMostFortyPercentOfItsFormerHeightInEnglish() = oneLineStepIsCompact()

    private fun oneLineStepIsCompact() {
        former.value = true
        open(1f)
        // The v0.5 block is the tall one the author saw: its text beside two controls, three controls under it.
        assertTrue("the v0.5 block measures $formerHeight dp", formerHeight >= 4 * 48f)
        assertEquals("the v0.5 block is as wide as a step block", bounds(EditorTags.step(0)).width, formerWidth, 0.5f)
        listOf(0, 2).forEach { position ->
            val height = dp(bounds(EditorTags.step(position)).height)
            assertTrue("step $position is $height dp high, over 40 % of the v0.5 $formerHeight dp", height <= formerHeight * 0.4f)
        }
    }

    // The row's text takes the block's full width beside the drag column, nothing beside it and no control in the
    // block. In the step's sheet, under its text, its text buttons — "Insert ingredient", then the parts' — each at least
    // 48 dp tall, whole and within the sheet, in order.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theTextIsFullWidthAndTheIconsAreOneRowUnderIt() = textAndRow(1f)

    // At double font scale every control is whole and 48 dp, under the text, the row going on to a second line if it must.
    // The font scale is the configuration's too, so that it reaches the sheet's own window.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp", fontScale = 2f)
    fun atDoubleFontScaleEveryControlStaysWhole() = textAndRow(2f)

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp", fontScale = 2f)
    fun atDoubleFontScaleEveryControlStaysWholeInEnglish() = textAndRow(2f)

    private fun textAndRow(fontScale: Float) {
        open(fontScale)
        (0..2).forEach { position ->
            val block = bounds(EditorTags.step(position))
            val column = bounds(EditorTags.stepHandle(position))
            val line = bounds(StepSheetTags.line(position))
            // The row from just beside the column to the block's other edge.
            val (start, end) = if (handleRight) block.left to column.left else column.right to block.right
            assertTrue("step $position: the row starts by the column", line.left - start in -0.5f..(12f * compose.density.density))
            assertTrue("step $position: the row reaches the block's other edge", end - line.right in -0.5f..(8f * compose.density.density))
            // The row is the one control in the block.
            val inStep = hasAnyAncestor(hasTestTag(EditorTags.step(position)))
            val controls = compose.onAllNodes(inStep and AccessibilityChecks.isInteractive).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertEquals("step $position", listOf(StepSheetTags.line(position)), controls)
        }
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose)
        (0..2).forEach { position ->
            compose.openStep(position)
            val text = bounds(EditorTags.stepText(position))
            // The buttons are text buttons, whole, under the text, one under another in order, and within the sheet.
            val sheet = bounds(StepSheetTags.SHEET)
            buttons(position).forEach { tag ->
                val b = bounds(tag)
                assertTrue("step $position: $tag is at least 48 dp high", dp(b.height) >= 48f - 0.5f)
                assertTrue("step $position: $tag is at least 48 dp wide", dp(b.width) >= 48f - 0.5f)
                assertTrue("step $position: $tag is under the text", b.top >= text.bottom - 0.5f)
                assertTrue("step $position: $tag is within the sheet", b.left >= sheet.left - 0.5f && b.right <= sheet.right + 0.5f)
                assertTrue("step $position: $tag says what it does", compose.textsOf(tag).single().isNotBlank())
            }
            buttons(position).map(::bounds).zipWithNext().forEach { (a, b) -> assertTrue("step $position: the buttons stand in order", b.top >= a.bottom - 0.5f) }
            AccessibilityChecks.assertTouchTargets(compose)
            AccessibilityChecks.assertInteractiveElementsLabelled(compose)
            compose.closeStep()
        }
    }

    // Each control says in words what it does — none is a bare icon (T-218) — and its click is announced naming its step;
    // the removal is named as the ingredient's and the group's are, with no number.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun eachControlSaysWhatItDoesAndItsClickNamesItsStep() {
        open(1f)
        compose.openStep(1)
        assertEquals(listOf("Insert ingredient", "Add timer", "Add photo or clip", "Remove step"), labels(1))
        assertEquals(
            listOf("Insert an ingredient's quantity into step 2", "Add a timer to step 2", "Add, change or remove the photo or clip of step 2"),
            clickLabels(1),
        )
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun eachControlSaysWhatItDoesAndItsClickNamesItsStepInRussian() {
        open(1f)
        compose.openStep(1)
        assertEquals(listOf("Вставить ингредиент", "Добавить таймер", "Добавить фото или видео", "Убрать шаг"), labels(1))
        assertEquals(
            listOf("Вставить количество ингредиента в шаг 2", "Добавить таймер к шагу 2", "Добавить, заменить или убрать фото или видео шага 2"),
            clickLabels(1),
        )
    }

    // In the sheet, the timer's form and the photograph show in their parts only when the step has them, above the
    // button that adds one; "Add timer" opens the form above it, "Add photo or clip" its menu.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theTimerAndThePhotoShowUnderTheRowOnlyWhenPresent() {
        open(1f)
        compose.openStep(1)
        val timers = bounds(StepSheetTags.TIMERS)
        listOf(EditorTags.timerHours(1), EditorTags.timerName(1)).forEach { tag ->
            assertTrue("$tag in the timers' part", bounds(tag).top >= timers.top - 0.5f)
            assertTrue("$tag above Add timer", bounds(tag).bottom <= bounds(EditorTags.timerAttach(1)).top + 0.5f)
        }

        compose.openStep(0)
        assertFalse(exists(EditorTags.timerHours(0)) || exists(EditorTags.timerName(0)) || exists(EditorTags.stepPhotoImage(0)))
        compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.timerHours(0)) }
        assertTrue(bounds(EditorTags.timerHours(0)).bottom <= bounds(EditorTags.timerAttach(0)).top + 0.5f)

        compose.openStep(2)
        compose.onNodeWithTag(EditorTags.stepPhoto(2)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoTake(2)) }
        assertTrue(exists(EditorTags.stepPhotoChoose(2)))
        assertFalse(exists(EditorTags.stepPhotoRemove(2)))
    }

    // A frozen recipe's step block shows no action icons.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun aFrozenRecipesStepsShowNoActionIcons() {
        open(1f, RecipeState.PUBLISHED)
        (0..2).forEach { position ->
            assertTrue(exists(StepSheetTags.text(position)))
            (buttons(position) + EditorTags.stepRemove(position)).forEach { assertFalse("frozen step $position shows $it", exists(it)) }
            val inStep = hasAnyAncestor(hasTestTag(EditorTags.step(position)))
            assertEquals(0, compose.onAllNodes(inStep and AccessibilityChecks.isInteractive, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    companion object {
        private const val V05_BLOCK = "v05-step-block"

        /** Every test runs with the drag column on the left, as by default, and on the right, for a left hand. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "handle on the right: {0}")
        fun sides(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}

/**
 * A one-line step's block as v0.5 laid it out, on a 360 dp screen: the 48 dp drag column; beside it the text field of two
 * lines at least, squeezed beside "Remove" and the menu; under them "Insert ingredient", "Timer" and "Photo", one a line.
 */
@Composable
private fun V05StepBlock() {
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    fun t(en: String, ru: String) = if (russian) ru else en
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedCard(Modifier.fillMaxWidth().testTag("v05-step-block")) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(48.dp).fillMaxHeight())
                Column(Modifier.fillMaxWidth().padding(PaddingValues(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 8.dp))) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = "Boil the water.",
                            onValueChange = {},
                            label = { Text(t("Step 1", "Шаг 1")) },
                            minLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {}) { Text(t("Remove", "Убрать")) }
                        IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {}
                    }
                    TextButton(onClick = {}) { Text(t("Insert ingredient", "Вставить ингредиент")) }
                    TextButton(onClick = {}) { Text(t("Timer", "Таймер")) }
                    TextButton(onClick = {}) { Text(t("Photo", "Фото")) }
                }
            }
        }
    }
}
