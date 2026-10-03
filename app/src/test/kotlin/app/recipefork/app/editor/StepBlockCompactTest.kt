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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
 * The compact step block on a 360 dp phone: the step's text across the whole block beside its drag column; under it,
 * one row of icons — insert an ingredient, timer, photo, remove, the menu — each 48 dp, named for the screen reader by
 * its step and shown in a tooltip on a long press; the timer's form and the photograph under that row only when the
 * step has them; a one-line step a fraction of its v0.5 height; at double font scale every control whole and 48 dp.
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
        if (state == null) compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(1)), PATIENCE_MS)
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun dp(px: Float): Float = px / compose.density.density

    /** The row of the step at [position], in order. */
    private fun actions(position: Int) = listOf(
        EditorTags.stepInsert(position),
        EditorTags.timerAttach(position),
        EditorTags.stepPhoto(position),
        EditorTags.stepRemove(position),
        EditorTags.stepMenu(position),
    )

    /** What the screen reader speaks for each of [actions] of the step at [position]. */
    private fun labels(position: Int): List<String> = actions(position).map { AccessibilityChecks.announcedLabel(compose.onNodeWithTag(it).fetchSemanticsNode()) }

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

    // The text takes the block's full width beside the drag column, nothing beside it; under it, one row of the five
    // icons in order, each 48 dp, and no text button anywhere in the block.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theTextIsFullWidthAndTheIconsAreOneRowUnderIt() = textAndRow(1f)

    // At double font scale every control is whole and 48 dp, under the text, the row going on to a second line if it must.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun atDoubleFontScaleEveryControlStaysWhole() = textAndRow(2f)

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun atDoubleFontScaleEveryControlStaysWholeInEnglish() = textAndRow(2f)

    private fun textAndRow(fontScale: Float) {
        open(fontScale)
        (0..2).forEach { position ->
            val block = bounds(EditorTags.step(position))
            val column = bounds(EditorTags.stepHandle(position))
            val text = bounds(EditorTags.stepText(position))
            // The text's field from just beside the column to just inside the block's other edge.
            val (start, end) = if (handleRight) block.left to column.left else column.right to block.right
            assertTrue("step $position: the text starts by the column", text.left - start in 0f..(12f * compose.density.density))
            assertTrue("step $position: the text reaches the block's other edge (${if (handleRight) "the column" else "the right edge"})", end - text.right in 0f..(8f * compose.density.density))
            val controls = actions(position).map { bounds(it) }
            controls.forEachIndexed { i, b ->
                assertEquals("step $position: ${actions(position)[i]} is 48 dp wide", 48f, dp(b.width), 0.5f)
                assertEquals("step $position: ${actions(position)[i]} is 48 dp high", 48f, dp(b.height), 0.5f)
                assertTrue("step $position: ${actions(position)[i]} is under the text", b.top >= text.bottom - 0.5f)
                assertTrue("step $position: ${actions(position)[i]} lies inside the block", b.left >= block.left - 0.5f && b.right <= block.right + 0.5f && b.bottom <= block.bottom + 0.5f)
            }
            controls.zipWithNext().forEach { (a, b) -> assertTrue("step $position: the icons do not overlap", b.left >= a.right - 0.5f || b.top >= a.bottom - 0.5f) }
            if (fontScale == 1f) {
                controls.forEach { assertEquals("step $position: the icons stand in one row", controls.first().top, it.top, 0.5f) }
                controls.zipWithNext().forEach { (a, b) -> assertTrue("step $position: the icons stand in order", b.left >= a.right - 0.5f) }
            }
            // No text button: every control in the block but the field carries no text of its own, only its spoken name.
            val inStep = hasAnyAncestor(hasTestTag(EditorTags.step(position)))
            val texts = compose.onAllNodes(inStep and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
                .filter { it.config.getOrNull(SemanticsProperties.TestTag) != EditorTags.stepText(position) && it.config.getOrNull(SemanticsProperties.EditableText) == null }
                .filter { it.config.getOrNull(SemanticsProperties.Text).orEmpty().isNotEmpty() }
            assertEquals("step $position: no text buttons", emptyList<SemanticsNode>(), texts)
        }
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose)
    }

    // Each icon is named for the screen reader by its step, and a long press shows that name in a tooltip.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun eachIconIsNamedByItsStepAndShowsATooltip() {
        open(1f)
        assertEquals(listOf("Insert an ingredient into step 2", "Timer for step 2", "Photo or clip for step 2", "Remove step 2", "Step 2 actions"), labels(1))
        actions(1).forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().performTouchInput { longClick() }
            compose.waitUntil(PATIENCE_MS) { exists(EditorTags.tooltip(tag)) }
            val label = AccessibilityChecks.announcedLabel(compose.onNodeWithTag(tag).fetchSemanticsNode())
            fun texts(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::texts)
            assertTrue("$tag shows \"$label\" on a long press", texts(node(EditorTags.tooltip(tag))).contains(label))
            compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.tooltip(tag)) }
        }
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun eachIconIsNamedByItsStepInRussian() {
        open(1f)
        assertEquals(listOf("Вставить ингредиент в шаг 2", "Таймер для шага 2", "Фото или видео для шага 2", "Убрать шаг 2", "Действия с шагом 2"), labels(1))
    }

    // The timer's form and the photograph show under the row only when the step has them; the timer icon opens the
    // form under the row, the photo icon its menu.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theTimerAndThePhotoShowUnderTheRowOnlyWhenPresent() {
        open(1f)
        assertFalse(exists(EditorTags.timerHours(0)) || exists(EditorTags.timerName(0)) || exists(EditorTags.stepPhotoImage(0)))
        assertTrue(bounds(EditorTags.timerHours(1)).top >= bounds(EditorTags.timerAttach(1)).bottom - 0.5f)
        assertTrue(bounds(EditorTags.timerName(1)).top >= bounds(EditorTags.timerAttach(1)).bottom - 0.5f)

        compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.timerHours(0)) }
        assertTrue(bounds(EditorTags.timerHours(0)).top >= bounds(EditorTags.timerAttach(0)).bottom - 0.5f)

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
            assertTrue(exists(EditorTags.stepText(position)))
            actions(position).forEach { assertFalse("frozen step $position shows $it", exists(it)) }
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
                        ActionText(t("Remove", "Убрать"), "", {})
                        IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {}
                    }
                    ActionText(t("Insert ingredient", "Вставить ингредиент"), "", {})
                    ActionText(t("Timer", "Таймер"), "", {})
                    ActionText(t("Photo", "Фото"), "", {})
                }
            }
        }
    }
}
