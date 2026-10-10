package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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

/**
 * The editor at large text on a narrow phone (v0.30, T-216), in English and Russian, and at the usual size on a usual
 * phone: the title field keeps a usable width — the language goes under it where the line is too narrow for both; no
 * label or text of the ingredient and step sheets breaks inside a word — the quantity and the unit, the hours and the
 * minutes, each take a line of their own where their labels do not fit side by side as they rest in their fields, the
 * sheets' titles and the quantity's error wrap between words; the editor's back is Material 3's icon button, labelled.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class EditorAtLargeTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-10T12:00:00Z")
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

    /**
     * A recipe titled "Plum cake", declared in [language], with an ingredient holding no quantity, one holding no unit,
     * and a step with a timer of 1 h 10 min — opened in the editor.
     */
    private fun open(language: RecipeLanguage = RecipeLanguage.EN) {
        val recipe = Recipe.create(created, null).copy(
            title = "Plum cake",
            language = language,
            servings = 4,
            ingredients = listOf(
                Ingredient(0, null, null, "plums", UnitType.EXACT, QuantityUnit.GRAM, null),
                Ingredient(1, null, null, "flour", UnitType.EXACT, null, null),
            ),
            steps = listOf(Step(0, "Bake the cake.", timers = listOf(Timer(1.hours + 10.minutes)))),
        )
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(IngredientSheetTags.line(1)), PATIENCE_MS)
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun px(dp: Float): Float = dp * compose.density.density

    private fun string(id: Int): String = context.getString(id)

    /** How wide [text] is laid out on one line in [style] at the configuration's font scale. */
    private fun widthOf(text: String, style: TextStyle = Typography().bodyLarge): Float {
        val scale = context.resources.configuration.fontScale
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(compose.density.density, scale), LayoutDirection.Ltr)
        return measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat()
    }

    private val laidOutText = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
        !SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    /** The layout of every text — labels, titles, what is said under a field — under the node tagged [tag]. */
    private fun textsUnder(tag: String): List<TextLayoutResult> =
        compose.onAllNodes((hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and laidOutText, useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
            mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }
        }

    /** Every text under the node tagged [tag] breaks, if at all, between words: after a space or a dash, before a space. */
    private fun assertNoWordBroken(tag: String, where: String) {
        val layouts = textsUnder(tag)
        assertTrue("$where shows texts", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val text = layout.layoutInput.text.text
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                if (end <= 0 || end >= text.length) continue
                val before = text[end - 1]
                val after = text[end]
                val between = before.isWhitespace() || after.isWhitespace() || before in DASHES
                assertTrue("$where: “$text” breaks inside a word after line $line (“${text.substring(0, end)}|”)", between)
            }
        }
    }

    /** The texts under the node tagged [tag], by their own words. */
    private fun shownTexts(tag: String): List<String> = textsUnder(tag).map { it.layoutInput.text.text }

    // --- The title and the language -----------------------------------------------------------------------------------

    /**
     * The title field keeps at least half the line beside the language, or has the line to itself with the language
     * under it; its label, as it rests in the empty field, fits in it on one line.
     */
    private fun assertTitleUsable(beside: Boolean?) {
        val row = bounds(EditorTags.TITLE_ROW)
        val title = bounds(EditorTags.TITLE)
        val language = bounds(EditorTags.LANGUAGE)
        val under = language.top >= title.bottom - 0.5f
        fun dp(x: Float) = x / compose.density.density
        if (beside != null) assertEquals("the language beside the title (row ${dp(row.width)} dp, title ${dp(title.width)} dp, language ${dp(language.width)} dp)", beside, !under)
        if (under) {
            assertEquals("alone on its line, the title takes the whole line", row.width, title.width, 0.5f)
            assertTrue("the language starts under the title", language.left >= row.left - 0.5f && language.right <= row.right + 0.5f)
        } else {
            assertTrue("the title (${title.width / compose.density.density} dp) keeps half the line (${row.width / compose.density.density} dp)", title.width >= row.width * 0.5f - 0.5f)
            assertTrue("the language right of the title", language.left >= title.right)
        }
        val label = widthOf(string(R.string.editor_title))
        assertTrue("the title's label ($label px) fits in its field (${title.width} px) as it rests there", title.width - px(32f) >= label)
        // The title shows its words whole, not "Plum c".
        val typed = widthOf("Plum cake")
        assertTrue("“Plum cake” ($typed px) fits in the title field (${title.width} px)", title.width - px(32f) >= typed)
        assertNoWordBroken(EditorTags.TITLE, "the title")
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleOnANarrowPhoneTheTitleKeepsAUsableWidth() {
        open()
        assertTitleUsable(beside = false)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleOnANarrowPhoneTheTitleKeepsAUsableWidth() {
        open(RecipeLanguage.RU)
        assertTitleUsable(beside = false)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun onAUsualPhoneTheLanguageStaysBesideTheTitle() {
        open()
        assertTitleUsable(beside = true)
    }

    // The Russian switch, "РУС | АНГЛ" over "Сбросить", would leave the title less than half of a usual phone's line.
    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianOnAUsualPhoneTheLanguageGoesUnderTheTitle() {
        open(RecipeLanguage.RU)
        assertTitleUsable(beside = false)
    }

    // --- The ingredient's sheet ---------------------------------------------------------------------------------------

    /**
     * In each ingredient's sheet the quantity and the unit stand side by side only where both labels fit on one line in
     * their fields as they rest there, empty, in the fields' text style; no text in the sheet breaks inside a word — its
     * title, the labels, and the quantity's error once something unheld is typed.
     */
    private fun assertIngredientSheetsKeepWordsWhole(sideBySide: Boolean) {
        listOf(0, 1).forEach { position ->
            compose.openIngredient(position)
            val quantity = bounds(EditorTags.ingredientQuantity(position))
            val unit = bounds(EditorTags.ingredientUnit(position))
            val beside = unit.left > quantity.right - 0.5f
            assertEquals("row $position: the quantity and the unit side by side", sideBySide, beside)
            if (!beside) assertTrue("row $position: the unit under the quantity", unit.top >= quantity.bottom - 0.5f)
            val quantityLabel = widthOf(string(R.string.editor_ingredient_quantity))
            assertTrue("row $position: the quantity's label ($quantityLabel px) fits its field (${quantity.width} px)", quantity.width - px(32f) >= quantityLabel)
            val unitLabel = widthOf(string(R.string.editor_ingredient_unit))
            assertTrue("row $position: the unit's label ($unitLabel px) fits its field (${unit.width} px) beside its arrow", unit.width - px(32f + 48f) >= unitLabel)
            assertTrue(
                "row $position: the sheet's title and labels are checked",
                shownTexts(IngredientSheetTags.SHEET).containsAll(
                    listOf(string(R.string.editor_ingredient_edit), string(R.string.editor_ingredient_quantity), string(R.string.editor_ingredient_unit)),
                ),
            )
            assertNoWordBroken(IngredientSheetTags.SHEET, "row $position's sheet")
        }
        // A number written with a separator: the field says how a number is written, wrapped between words.
        compose.openIngredient(0)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("1 000")
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(EditorTags.ingredientError(0)), useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        assertTrue(string(R.string.editor_quantity_separators) in shownTexts(IngredientSheetTags.SHEET))
        assertNoWordBroken(IngredientSheetTags.SHEET, "row 0's sheet with its error")
        assertNoWordBroken(EditorTags.ingredientError(0), "the quantity's error")
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleTheIngredientSheetBreaksNoWord() {
        open()
        assertIngredientSheetsKeepWordsWhole(sideBySide = false)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheIngredientSheetBreaksNoWord() {
        open(RecipeLanguage.RU)
        assertIngredientSheetsKeepWordsWhole(sideBySide = false)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianOnAUsualPhoneTheQuantityStaysBesideTheUnit() {
        open(RecipeLanguage.RU)
        assertIngredientSheetsKeepWordsWhole(sideBySide = true)
    }

    // --- The step's sheet ---------------------------------------------------------------------------------------------

    /**
     * In the step's sheet the hours and the minutes stand side by side only where both labels fit on one line in their
     * fields as they rest there; the removal stays beside them; no text in the sheet breaks inside a word — its title, a
     * timer held and one just added, empty.
     */
    private fun assertStepSheetKeepsWordsWhole(sideBySide: Boolean) {
        compose.openStep(0)
        compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performTouchInput { click() }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerHours(0, 1)), PATIENCE_MS)
        compose.waitForIdle()
        listOf(0, 1).forEach { place ->
            val hours = bounds(EditorTags.timerHours(0, place))
            val minutes = bounds(EditorTags.timerMinutes(0, place))
            val remove = bounds(EditorTags.timerRemove(0, place))
            val beside = minutes.left > hours.right - 0.5f
            assertEquals("timer $place: the hours and the minutes side by side", sideBySide, beside)
            if (!beside) assertTrue("timer $place: the minutes under the hours", minutes.top >= hours.bottom - 0.5f)
            assertTrue("timer $place: the removal beside the fields", remove.left >= maxOf(hours.right, minutes.right) - 0.5f)
            assertTrue("timer $place: the removal is 48 dp", remove.width >= px(48f) - 0.5f && remove.height >= px(48f) - 0.5f)
            listOf(hours to R.string.editor_timer_hours, minutes to R.string.editor_timer_minutes).forEach { (field, label) ->
                val width = widthOf(string(label))
                assertTrue("timer $place: “${string(label)}” ($width px) fits its field (${field.width} px)", field.width - px(32f) >= width)
            }
        }
        assertTrue(
            "the step sheet's title and the timers' labels are checked",
            shownTexts(StepSheetTags.SHEET).containsAll(listOf(string(R.string.editor_timer_hours), string(R.string.editor_timer_minutes))),
        )
        assertNoWordBroken(StepSheetTags.SHEET, "the step's sheet")
        assertNoWordBroken(StepSheetTags.TITLE, "the step sheet's title")
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleTheStepSheetBreaksNoWord() {
        open()
        assertStepSheetKeepsWordsWhole(sideBySide = false)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheStepSheetBreaksNoWord() {
        open(RecipeLanguage.RU)
        assertStepSheetKeepsWordsWhole(sideBySide = false)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianOnAUsualPhoneTheHoursStayBesideTheMinutes() {
        open(RecipeLanguage.RU)
        assertStepSheetKeepsWordsWhole(sideBySide = true)
    }

    // --- The sheets' titles -------------------------------------------------------------------------------------------

    // "Изменить ингредиент" at twice the font scale beside "Готово": the long word is drawn smaller, never broken.
    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheIngredientSheetsTitleIsWhole() {
        open(RecipeLanguage.RU)
        compose.openIngredient(0)
        val layout = textsUnder(IngredientSheetTags.SHEET).single { it.layoutInput.text.text == "Изменить ингредиент" }
        assertNoWordBroken(IngredientSheetTags.SHEET, "the ingredient's sheet")
        for (line in 0 until layout.lineCount) {
            assertTrue("line $line of the title within its room", layout.getLineRight(line) - layout.getLineLeft(line) <= layout.layoutInput.constraints.maxWidth + 0.5f)
        }
        val title = bounds(IngredientSheetTags.TITLE)
        val done = bounds(IngredientSheetTags.DONE)
        assertTrue("the title ends before Done", title.right <= done.left + 0.5f)
        assertTrue("Done is 48 dp tall", done.height >= px(48f) - 0.5f)
    }

    // --- Back ---------------------------------------------------------------------------------------------------------

    /**
     * The editor's back: Material 3's icon button in the bar, a button to the screen reader named "Back", whose click
     * says it shows the recipe, 48 dp each way — and at twice the font scale it is still so.
     */
    private fun assertBack(label: String, action: String) {
        open()
        val back = compose.onNodeWithTag(EditorTags.BACK).fetchSemanticsNode()
        assertEquals(Role.Button, back.config.getOrNull(SemanticsProperties.Role))
        assertEquals(label, AccessibilityChecks.announcedLabel(back))
        assertEquals(action, AccessibilityChecks.clickActionLabel(back))
        val b = bounds(EditorTags.BACK)
        assertTrue("back is 48 dp each way: $b", b.width >= px(48f) - 0.5f && b.height >= px(48f) - 0.5f)
        assertTrue("back is in the bar", compose.onAllNodes(hasTestTag(EditorTags.BACK) and hasAnyAncestor(hasTestTag(EditorTags.BAR))).fetchSemanticsNodes().size == 1)
        // Built from Material 3's IconButton, not a clickable box drawn by hand.
        val source = File(LocalizationChecks.sourceDir, "app/recipefork/app/editor/EditorScreen.kt").readText()
        val bar = source.substringAfter("navigationIcon = {").substringBefore("actions = {")
        assertTrue("the editor's back is an IconButton", "IconButton(" in bar && "clickable(" !in bar)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun theBackIsAnIconButtonWithItsLabel() = assertBack("Back", "Show the recipe")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianTheBackIsAnIconButtonWithItsLabel() = assertBack("Назад", "Показать рецепт")

    private companion object {
        /** Where a line may end inside a run of letters: after a dash. */
        val DASHES = setOf('-', '‐', '–', '—')
    }
}
