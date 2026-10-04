package app.recipefork.app.editor

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
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
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.timer.TimerName
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * The checklist (GUIDE-003, GUIDE-004): a control at the editor's top, beside "Done", on every recipe in every state,
 * opening the seven items of advice — read only, the same for every recipe, item 1 naming the very length a timer's
 * name is cut at — closed by its button, by back or by a touch outside, back to the same place, with nothing recorded.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class ChecklistTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-03T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)
    private var shown = false
    private var mode = Mode.Standard

    private val englishTitles = listOf(
        "A step that carries a timer opens with what it is waiting for.",
        "One action, one step.",
        "A photograph or a clip belongs at the step it shows.",
        "Quantities inside a step go in as references, not as typed numbers.",
        "An ingredient is picked from the suggestions wherever one fits.",
        "The method goes in the steps, not in the description.",
        "The serving count is the number you actually cooked for.",
    )

    private val russianTitles = listOf(
        "Шаг с таймером начинается с того, чего он ждёт.",
        "Одно действие — один шаг.",
        "Фото или видео — у того шага, который они показывают.",
        "Количества внутри шага вставляются ссылками, а не набираются числами.",
        "Ингредиент выбирается из подсказок везде, где подсказка подходит.",
        "Способ приготовления — в шагах, а не в описании.",
        "Число порций — то, на сколько вы на самом деле готовили.",
    )

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
        if (shown) return
        shown = true
        val environment = EditorEnvironment(active, device.catalog(context), clock = { created.plusSeconds(60) }, mode = mode)
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    /** From an empty book: takes the offer to write, and returns the new, empty recipe the editor opened on. */
    private fun writeANewRecipe(): RecipeId {
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    /** Seeds a recipe holding something in every part, in [state], and opens the editor on it directly. */
    private fun openAFullRecipe(state: RecipeState): Recipe {
        val recipe = Recipe.create(created, null).copy(
            title = "Beetroot salad",
            description = "Grandmother's summer salad",
            language = RecipeLanguage.EN,
            tags = setOf(11L),
            servings = 4,
            steps = listOf(Step(0, "Bake the beetroot for an hour"), Step(1, "Grate it and dress with garlic")),
            sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT,
        ).inState(state)
        runBlocking { recipes.save(recipe) }
        show()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.chosen(11)), PATIENCE_MS)
        return recipe
    }

    private fun openTheChecklist() {
        compose.onNodeWithTag(ChecklistTags.CONTROL).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(ChecklistTags.DIALOG), PATIENCE_MS)
    }

    private fun awaitClosed() = compose.waitUntilDoesNotExist(hasTestTag(ChecklistTags.DIALOG), PATIENCE_MS)

    /** Every text of the item at [index], in reading order: its title, then its explanation. */
    private fun itemTexts(index: Int): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(ChecklistTags.item(index))) and hasText(), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    /** The checklist as it reads: the texts of each of its items. */
    private fun checklistTexts(): List<List<String>> {
        val count = compose.onAllNodes(SemanticsMatcher("a checklist item") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("editor-checklist-item-") == true
        }).fetchSemanticsNodes().size
        return (0 until count).map(::itemTexts)
    }

    private fun hasText() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun dp(value: Float) = value * context.resources.displayMetrics.density

    // GUIDE-003 (1): on a new and empty recipe the control is there at the top, beside "Done": an icon named for the
    // screen reader, 48 dp, announced as what it does, with a tooltip on a long press.
    @Test
    fun aNewEmptyRecipeHasTheControlBesideDone() {
        writeANewRecipe()
        compose.onNodeWithTag(ChecklistTags.CONTROL).assertIsDisplayed()
        val control = bounds(ChecklistTags.CONTROL)
        val done = bounds(EditorTags.BACK)
        // On the line of "Done", to its right, above the form.
        assertTrue("the checklist is not to the right of Done", control.left >= done.right)
        assertTrue("the checklist is not on Done's line", control.top < done.bottom && done.top < control.bottom)
        assertTrue("the checklist is not above the form", control.bottom <= bounds(EditorTags.FORM).top)
        assertTrue("the checklist is lower than 48 dp", control.height >= dp(48f) - 0.5f)
        assertTrue("the checklist is narrower than 48 dp", control.width >= dp(48f) - 0.5f)
        // It is named, and its click is announced as opening the checklist.
        assertEquals(listOf("Checklist"), compose.descriptionOf(ChecklistTags.CONTROL))
        val node = compose.onNodeWithTag(ChecklistTags.CONTROL).fetchSemanticsNode()
        assertEquals("Open the checklist", AccessibilityChecks.clickActionLabel(node))
        assertEquals(androidx.compose.ui.semantics.Role.Button, node.config[SemanticsProperties.Role])
        // A long press shows what it offers, and opens nothing.
        compose.onNodeWithTag(ChecklistTags.CONTROL).performTouchInput { longClick() }
        compose.waitUntilExactlyOneExists(hasTestTag(ChecklistTags.TOOLTIP), PATIENCE_MS)
        assertEquals(listOf("Advice on writing a recipe"), tooltipTexts())
        compose.onNodeWithTag(ChecklistTags.DIALOG).assertDoesNotExist()
        // And it opens the checklist on the empty recipe.
        compose.waitUntilDoesNotExist(hasTestTag(ChecklistTags.TOOLTIP), PATIENCE_MS)
        openTheChecklist()
        assertEquals(englishTitles, checklistTexts().map { it.first() })
    }

    private fun tooltipTexts(): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(ChecklistTags.TOOLTIP)) and hasText(), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    // GUIDE-003: the control is there whatever state the recipe is in, a frozen one too, and opens the same checklist.
    @Test
    fun everyStateHasTheControlAFrozenRecipeToo() {
        RecipeState.entries.forEach { state ->
            openAFullRecipe(state)
            if (!state.editable) compose.onNodeWithTag(EditorTags.FROZEN).assertExists()
            compose.onNodeWithTag(ChecklistTags.CONTROL).assertIsDisplayed()
            openTheChecklist()
            assertEquals("$state", englishTitles, checklistTexts().map { it.first() })
            compose.onNodeWithTag(ChecklistTags.CLOSE).performClick()
            awaitClosed()
            compose.onNodeWithTag(EditorTags.BACK).performClick()
            compose.waitUntilDoesNotExist(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        }
    }

    // GUIDE-004 (1), in English: the seven items, each a title and its explanation, every text from the resources.
    @Test
    fun itHoldsTheSevenItemsInEnglish() = sevenItems(englishTitles, "first ${TimerName.LENGTH} characters")

    // GUIDE-004 (1), in Russian, the examples kept as the author wrote them.
    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun itHoldsTheSevenItemsInRussian() {
        sevenItems(russianTitles, "первые ${TimerName.LENGTH} символов")
        val first = itemTexts(0)[1]
        assertTrue(first, "«курица в духовку на 30 минут»" in first)
        assertTrue(first, "«нарежьте лук полукольцами, обжарьте… и в духовку»" in first)
    }

    private fun sevenItems(titles: List<String>, length: String) {
        writeANewRecipe()
        assertEquals(if (titles === russianTitles) "Чек-лист" else "Checklist", compose.descriptionOf(ChecklistTags.CONTROL).single())
        openTheChecklist()
        val items = checklistTexts()
        assertEquals(7, items.size)
        items.forEach { assertEquals(it.toString(), 2, it.size) }
        assertEquals(titles, items.map { it[0] })
        assertTrue(items[0][1], length in items[0][1])
        // Every text shown, the checklist's included, is a resource of the language in force, written in it.
        LocalizationChecks.assertScreenTextFromResources(compose, context, tagLabels(SAMPLE_DICTIONARIES))
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, tagLabels(SAMPLE_DICTIONARIES))
    }

    // In the build without the server nothing is ever published, so no item speaks of publishing, sending or
    // submitting; item 5 keeps its advice without the clause about publication.
    @Test
    fun inServerlessNoItemMentionsPublication() =
        serverless(Regex("(?i)publi|send|sent|submit"), "What is typed as free text is never found by an ingredient filter.")

    // The same, in Russian.
    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inServerlessNoItemMentionsPublicationInRussian() = serverless(
        Regex("(?i)публик|отправ|подач|модерац"),
        "Ингредиент, набранный своими словами, никогда не найдётся фильтром по ингредиентам.",
    )

    private fun serverless(publication: Regex, item5: String) {
        mode = Mode.Serverless
        writeANewRecipe()
        openTheChecklist()
        val items = checklistTexts()
        assertEquals(7, items.size)
        items.flatten().forEach { assertFalse(it, publication.containsMatchIn(it)) }
        assertEquals(item5, items[4][1])
        LocalizationChecks.assertScreenTextFromResources(compose, context, tagLabels(SAMPLE_DICTIONARIES))
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, tagLabels(SAMPLE_DICTIONARIES))
    }

    // In the standard build item 5 keeps the clause: what is typed as free text cannot be changed after publication.
    @Test
    fun inStandardItemFiveKeepsThePublicationClause() = standard("after publication that cannot be changed")

    // The same, in Russian.
    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inStandardItemFiveKeepsThePublicationClauseInRussian() = standard("после публикации этого уже не изменить")

    private fun standard(clause: String) {
        mode = Mode.Standard
        writeANewRecipe()
        openTheChecklist()
        val item5 = checklistTexts()[4][1]
        assertTrue(item5, clause in item5)
    }

    // GUIDE-004 (5): the characters item 1 names are the ones FLOW-016 cuts at — the number is not written in the text
    // but rendered from the very constant the timer's name is cut by, so the two cannot disagree.
    @Test
    fun itemOneNamesTheLengthATimersNameIsCutAt() {
        val raw = context.resources.getString(R.string.editor_checklist_1_text)
        assertTrue(raw, "%1\$d" in raw)
        assertFalse(raw, Regex("""\b${TimerName.LENGTH}\b""").containsMatchIn(raw))
        val russian = File("src/main/res/values-ru/strings_editor.xml").readText()
            .substringAfter("name=\"editor_checklist_1_text\">").substringBefore("</string>")
        assertTrue(russian, "%1\$d" in russian)
        assertFalse(russian, Regex("""\b${TimerName.LENGTH}\b""").containsMatchIn(russian))
        // The number the checklist shows is the length a derived name keeps, no more and no less.
        writeANewRecipe()
        openTheChecklist()
        val shownNumber = Regex("""first (\d+) characters""").find(itemTexts(0)[1])!!.groupValues[1].toInt()
        assertEquals(TimerName.LENGTH, shownNumber)
        val exactly = "a".repeat(shownNumber)
        assertEquals(exactly, TimerName.derived(exactly))
        assertNotEquals(exactly + "a", TimerName.derived(exactly + "a"))
        assertEquals(exactly + TimerName.ELLIPSIS, TimerName.derived(exactly + "a"))
    }

    // GUIDE-004 (2), (3): the advice names nothing in the recipe being written — it reads the same on an empty recipe
    // and on a full one, and none of the full one's words appear in it.
    @Test
    fun theAdviceIsTheSameForEveryRecipeAndNamesNothingInIt() {
        val id = writeANewRecipe()
        openTheChecklist()
        val onEmpty = checklistTexts()
        compose.onNodeWithTag(ChecklistTags.CLOSE).performClick()
        awaitClosed()
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        val full = openAFullRecipe(RecipeState.LOCAL)
        openTheChecklist()
        val onFull = checklistTexts()
        assertEquals(onEmpty, onFull)
        val said = onFull.flatten().joinToString(" ")
        listOf(full.title, full.description, "Beetroot", "beetroot", "garlic", "Georgian").forEach {
            assertFalse("the checklist names $it", it in said)
        }
    }

    // GUIDE-003 (3): the checklist offers nothing but reading and closing — no tick boxes, no field, nothing that
    // changes the recipe; its one action is to close.
    @Test
    fun theChecklistOffersNothingButClosing() {
        openAFullRecipe(RecipeState.LOCAL)
        openTheChecklist()
        val inDialog = compose.onAllNodes(hasAnyAncestor(isDialog()) or isDialog(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(inDialog.isNotEmpty())
        fun has(node: SemanticsNode, key: androidx.compose.ui.semantics.SemanticsPropertyKey<*>) = key in node.config
        assertTrue("a tick box", inDialog.none { has(it, SemanticsProperties.ToggleableState) || has(it, SemanticsProperties.Selected) })
        assertTrue("a field", inDialog.none { has(it, SemanticsProperties.EditableText) || has(it, SemanticsActions.SetText) })
        val actions = inDialog.filter { has(it, SemanticsActions.OnClick) || has(it, SemanticsActions.OnLongClick) }
        assertEquals(listOf(ChecklistTags.CLOSE), actions.map { it.config.getOrNull(SemanticsProperties.TestTag) })
    }

    // GUIDE-003 (2), (4): closed by its button, the editor is where it was — the same scroll, the same field focused —
    // and nothing anywhere records that the checklist was opened.
    @Test
    fun closedByItsButtonTheEditorIsWhereItWas() = backToTheSamePlace {
        compose.onNodeWithTag(ChecklistTags.CLOSE).performClick()
    }

    // The same, closed by the system back, which closes the checklist and not the editor.
    @Test
    fun closedByBackTheEditorIsWhereItWas() = backToTheSamePlace { dispatchToTheTopWindow(::pressBack) }

    // The same, closed by a touch outside it.
    @Test
    fun closedByATouchOutsideTheEditorIsWhereItWas() = backToTheSamePlace { dispatchToTheTopWindow(::touchOutside) }

    private fun backToTheSamePlace(close: () -> Unit) {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Borscht")
        compose.onNodeWithTag(EditorTags.STEP_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepText(0)).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("Boil the beetroot")
        compose.swipeAwayStatement()
        compose.waitForIdle()
        val scrolled = scrollOf(EditorTags.FORM)
        assertTrue("the editor is not scrolled", scrolled > 0f)
        assertTrue(isFocused(EditorTags.stepText(0)))
        val recipe = runBlocking { recipes.get(id) }
        val prefs = preferences()

        openTheChecklist()
        // It scrolls by itself, inside its own window, and leaves the editor where it is.
        compose.onNodeWithTag(ChecklistTags.item(6)).performScrollTo().assertIsDisplayed()
        close()
        awaitClosed()
        compose.waitForIdle()

        compose.onNodeWithTag(EditorTags.SCREEN).assertExists()
        assertEquals(scrolled, scrollOf(EditorTags.FORM), 0.5f)
        assertTrue("the field lost its focus", isFocused(EditorTags.stepText(0)))
        assertEquals(recipe, runBlocking { recipes.get(id) })
        assertEquals(prefs, preferences())
        // The author goes on typing where they were.
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput(" and grate it")
        compose.waitForIdle()
        assertTrue(runBlocking { recipes.get(id) }!!.steps.single().text.endsWith("and grate it"))
    }

    private fun scrollOf(tag: String): Float =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun isFocused(tag: String): Boolean =
        compose.onAllNodes(hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .any { it.config.getOrNull(SemanticsProperties.Focused) == true }

    /** Everything the application keeps in its preferences, file by file. */
    private fun preferences(): Map<String, String> =
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty().associate { it.name to it.readText() }

    /** Hands [event] to the topmost window — the checklist's own, in front of the editor. */
    private fun dispatchToTheTopWindow(event: (View) -> Unit) {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
        val window = views.last()
        compose.runOnUiThread { event(window) }
    }

    private fun pressBack(window: View) {
        window.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
        window.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
    }

    /**
     * A tap beside the checklist, to the right of its window, on the editor that shows there: the system hands a touch
     * outside a modal window to that window, at a place outside its bounds.
     */
    private fun touchOutside(window: View) {
        val time = SystemClock.uptimeMillis()
        val x = window.width + dp(8f)
        val y = window.height / 2f
        assertTrue("the checklist leaves no room beside it", window.width + dp(8f) < dp(360f))
        MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0).also { window.dispatchTouchEvent(it) }.recycle()
        MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, x, y, 0).also { window.dispatchTouchEvent(it) }.recycle()
    }

    // The checklist is readable on a 360 dp screen at twice the font size: the control and "Done" both fit the top, every
    // item can be scrolled to and read whole, and the button to close it stays in reach.
    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2.0f)
    fun readableAt360DpAndTwiceTheFont() = readableLarge()

    // The same in Russian, whose texts run longer.
    @Test
    @Config(qualifiers = "ru-w360dp-h640dp", fontScale = 2.0f)
    fun readableAt360DpAndTwiceTheFontInRussian() = readableLarge()

    private fun readableLarge() {
        assertEquals(2.0f, context.resources.configuration.fontScale, 0.001f)
        writeANewRecipe()
        val width = dp(360f)
        val control = bounds(ChecklistTags.CONTROL)
        val done = bounds(EditorTags.BACK)
        assertTrue("the checklist runs off the screen: $control", control.right <= width + 0.5f)
        assertTrue("the checklist overlaps Done", control.left >= done.right)
        compose.onNodeWithTag(ChecklistTags.CONTROL).assertIsDisplayed()
        compose.onNodeWithTag(EditorTags.BACK).assertIsDisplayed()

        openTheChecklist()
        // Longer than the screen at this size, so it scrolls.
        val range = compose.onNodeWithTag(ChecklistTags.ITEMS).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("the checklist does not scroll", range.maxValue() > 0f)
        val dialog = bounds(ChecklistTags.DIALOG)
        assertTrue("the checklist is wider than the screen: $dialog", dialog.width <= width + 0.5f)
        (0 until 7).forEach { index ->
            compose.onNodeWithTag(ChecklistTags.item(index)).performScrollTo().assertIsDisplayed()
            val item = bounds(ChecklistTags.item(index))
            assertTrue("item $index runs out of the checklist", item.left >= dialog.left && item.right <= dialog.right)
        }
        compose.onNodeWithTag(ChecklistTags.CLOSE).assertIsDisplayed()
        compose.onNodeWithTag(ChecklistTags.CLOSE).performClick()
        awaitClosed()
    }
}
