package app.recipefork.app.kitchen

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.data.HeldThemeSetting
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.library.scrolledTo
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.LocalizationChecks
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
 * T-204, scenario 1 of v0.28: the settings' "My kitchen" says how many items are ticked and opens My kitchen — the
 * equipment dictionary by its groups, consumables and removed entries left out, each item a checkbox row, each group a
 * row that ticks all of it — with a search in either language and by synonyms; back returns to the settings.
 *
 * v0.30, T-221: the groups fold — all folded as My kitchen opens, each saying "n of m" — and a tap on a group's row
 * shows or hides its items; its checkbox, a target of its own, ticks the whole group, said with Undo, which puts back
 * exactly the ticks the group had. My kitchen opens from the book's filters too. Russian says «посуда и техника».
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KitchenScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val saved = SavedStateHandle()
    private var session = LibrarySession(saved)
    private val card = CardEnvironment(HeldUsualServings(), ingredients = IngredientEntries { emptyList() })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = emptyList<TagEntity>()
        },
    ) { Instant.parse("2026-10-10T12:00:00Z") }
    private val store = PreferencesKitchen(context)
    /** Whether the equipment dictionary fails to read, as a damaged device store would. */
    @Volatile
    private var unreadable = false
    private val kitchen = KitchenEnvironment(store, EquipmentSource { if (unreadable) error("the store cannot be read") else KITCHEN_DICTIONARY })

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun showSettings(): StateRestorationTester {
        session.choose(LibraryView.OWN)
        val tester = StateRestorationTester(compose)
        // The session read anew each time the content is: a process ended and restored brings a new one.
        tester.setContent {
            MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor, themeSetting = HeldThemeSetting(), kitchen = kitchen) }
        }
        compose.runOnIdle { session.openSettings() }
        compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.KITCHEN) && !exists(LibraryTags.OWN_LIST) }
        return tester
    }

    private fun kitchenValue(): String = compose.textsOf(SettingsTags.value(SettingsTags.KITCHEN)).single()

    private fun openKitchen() {
        compose.onNodeWithTag(SettingsTags.KITCHEN).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) && !exists(SettingsTags.SCREEN) }
    }

    private fun awaitSettings() = compose.waitUntil(PATIENCE_MS) { exists(SettingsTags.SCREEN) && !exists(KitchenTags.SCREEN) }

    private fun state(tag: String): ToggleableState =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

    private fun tap(tag: String, list: String = KitchenTags.LIST) {
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    /** Shows the items of the group [id], folded. */
    private fun unfold(id: Long) {
        tap(KitchenTags.group(id))
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(KitchenTags.group(id))).fetchSemanticsNodes().single().config[SemanticsProperties.StateDescription] == expanded }
    }

    private val expanded get() = context.getString(app.recipefork.app.R.string.kitchen_group_expanded)
    private val collapsed get() = context.getString(app.recipefork.app.R.string.kitchen_group_collapsed)

    private fun folding(id: Long): String = compose.onNodeWithTag(KitchenTags.group(id)).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    private fun search(text: String) {
        compose.onNodeWithTag(KitchenTags.SEARCH).performTextReplacement(text)
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.RESULTS) || exists(KitchenTags.EMPTY) }
    }

    private fun found(): List<String> = compose.onAllNodes(hasAnyAncestor(hasTestTag(KitchenTags.RESULTS)) and hasTestTagStartingWith("kitchen-item-"))
        .fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    private fun hasTestTagStartingWith(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    // The settings' row counts what is ticked and opens My kitchen, which lists the groups and their items as checkbox
    // rows — not the consumables, not a removed entry; ticks stay, and the row counts them on return.
    @Test
    fun theSettingsRowCountsTheTicksAndOpensTheListByGroups() {
        showSettings()
        compose.waitUntil(PATIENCE_MS) { kitchenValue() == "Nothing ticked yet" }
        assertEquals("My kitchen", compose.textsOf(SettingsTags.KITCHEN).first())
        openKitchen()
        assertEquals(listOf("My kitchen"), compose.textsOf(KitchenTags.TITLE))
        // Every group folded as it opens, its items out of the list.
        listOf(STOVETOP, MEASURING).forEach { assertEquals(collapsed, folding(it)) }
        listOf(FRYING_PAN, SAUCEPAN, SCALE).forEach { assertFalse(exists(KitchenTags.item(it))) }
        unfold(STOVETOP)
        unfold(MEASURING)
        listOf(KitchenTags.group(STOVETOP), KitchenTags.group(MEASURING), KitchenTags.item(FRYING_PAN), KitchenTags.item(SAUCEPAN), KitchenTags.item(SCALE))
            .forEach { compose.onNodeWithTag(KitchenTags.LIST).performScrollToNode(hasTestTag(it)) }
        listOf(KitchenTags.group(Kitchen.CONSUMABLES), KitchenTags.item(PARCHMENT), KitchenTags.item(FOIL), KitchenTags.item(REMOVED_POT))
            .forEach { assertFalse(it, exists(it)) }
        assertEquals(listOf("Stovetop cookware", "0 of 2"), compose.textsOf(KitchenTags.group(STOVETOP)))
        val pan = compose.onNodeWithTag(KitchenTags.item(FRYING_PAN)).fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.Role.Checkbox, pan.config[SemanticsProperties.Role])
        assertEquals(ToggleableState.Off, state(KitchenTags.item(FRYING_PAN)))

        tap(KitchenTags.item(FRYING_PAN))
        assertEquals(ToggleableState.On, state(KitchenTags.item(FRYING_PAN)))
        assertEquals(setOf(FRYING_PAN), store.held.value)
        assertEquals(setOf(FRYING_PAN), PreferencesKitchen(context).held.value)

        compose.onNodeWithTag(KitchenTags.BACK).performClick()
        awaitSettings()
        compose.waitUntil(PATIENCE_MS) { kitchenValue() == "1 item ticked" }
        openKitchen()
        assertEquals(listOf("Stovetop cookware", "1 of 2"), compose.textsOf(KitchenTags.group(STOVETOP)))
        unfold(STOVETOP)
        assertEquals(ToggleableState.On, state(KitchenTags.item(FRYING_PAN)))
        // A tap on the row folds it again.
        tap(KitchenTags.group(STOVETOP))
        compose.waitUntil(PATIENCE_MS) { !exists(KitchenTags.item(FRYING_PAN)) }
        assertEquals(collapsed, folding(STOVETOP))
    }

    // The group's row is a button that shows or hides its items, saying which it does and whether it is unfolded;
    // its checkbox is a target of its own, named for the group.
    @Test
    fun aGroupsRowFoldsAndItsCheckboxIsATargetOfItsOwn() {
        showSettings()
        openKitchen()
        val row = compose.onNodeWithTag(KitchenTags.group(STOVETOP)).fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.Role.Button, row.config[SemanticsProperties.Role])
        assertEquals("Show the items", AccessibilityChecks.clickActionLabel(row))
        unfold(STOVETOP)
        assertEquals("Hide the items", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(KitchenTags.group(STOVETOP)).fetchSemanticsNode()))
        val tick = compose.onNodeWithTag(KitchenTags.groupTick(STOVETOP)).fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.Role.Checkbox, tick.config[SemanticsProperties.Role])
        assertEquals(listOf("Every item of Stovetop cookware"), tick.config[SemanticsProperties.ContentDescription])
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 6)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 6)
        AccessibilityChecks.assertDistinctLabels(compose)
    }

    // A group's checkbox is tri-state: some ticked is partly, a tap ticks the whole group, another unticks it.
    @Test
    fun aGroupIsTickedWhole() {
        store.hold(listOf(FRYING_PAN), has = true)
        showSettings()
        openKitchen()
        unfold(STOVETOP)
        val group = KitchenTags.groupTick(STOVETOP)
        assertEquals(ToggleableState.Indeterminate, state(group))
        assertEquals(androidx.compose.ui.semantics.Role.Checkbox, compose.onNodeWithTag(group).fetchSemanticsNode().config[SemanticsProperties.Role])
        tap(group)
        assertEquals(ToggleableState.On, state(group))
        assertEquals(ToggleableState.On, state(KitchenTags.item(SAUCEPAN)))
        assertEquals(setOf(FRYING_PAN, SAUCEPAN), store.held.value)
        assertEquals(listOf("Stovetop cookware", "2 of 2"), compose.textsOf(KitchenTags.group(STOVETOP)))
        tap(group)
        assertEquals(ToggleableState.Off, state(group))
        assertEquals(emptySet<Long>(), store.held.value)
        // The other group is untouched by either, and ticking a group never folds or unfolds it.
        assertEquals(ToggleableState.Off, state(KitchenTags.groupTick(MEASURING)))
        assertEquals(expanded, folding(STOVETOP))
        assertEquals(collapsed, folding(MEASURING))
    }

    // A whole group ticked is said with Undo, which puts back exactly the ticks it had — partly ticked stays partly;
    // so is a whole group unticked. Anything ticked since makes the change final, its Undo gone.
    @Test
    fun aWholeGroupTickedOffersUndoRestoringExactlyThePreviousTicks() = undoesAGroup("Undo", "All of “Stovetop cookware” ticked", "All of “Stovetop cookware” unticked")

    @Test
    @Config(qualifiers = "ru")
    fun inRussianAWholeGroupTickedOffersUndo() =
        undoesAGroup("Отменить", "Отмечена вся группа «Посуда для плиты»", "Снята отметка со всей группы «Посуда для плиты»")

    private fun undoesAGroup(undo: String, ticked: String, unticked: String) {
        store.hold(listOf(FRYING_PAN, SCALE), has = true)
        showSettings()
        openKitchen()
        tap(KitchenTags.groupTick(STOVETOP))
        assertEquals(setOf(FRYING_PAN, SAUCEPAN, SCALE), store.held.value)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(androidx.compose.ui.test.hasText(ticked)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(androidx.compose.ui.test.hasText(undo) and androidx.compose.ui.test.hasClickAction()).performClick()
        compose.waitUntil(PATIENCE_MS) { store.held.value == setOf(FRYING_PAN, SCALE) }
        assertEquals(ToggleableState.Indeterminate, state(KitchenTags.groupTick(STOVETOP)))
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(androidx.compose.ui.test.hasText(ticked)).fetchSemanticsNodes().isEmpty() }

        // Unticked whole, and undone: both items back.
        tap(KitchenTags.groupTick(STOVETOP))
        tap(KitchenTags.groupTick(STOVETOP))
        assertEquals(setOf(SCALE), store.held.value)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(androidx.compose.ui.test.hasText(unticked)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(androidx.compose.ui.test.hasText(undo) and androidx.compose.ui.test.hasClickAction()).performClick()
        compose.waitUntil(PATIENCE_MS) { store.held.value == setOf(FRYING_PAN, SAUCEPAN, SCALE) }

        // Ticked whole, then an item ticked elsewhere: the change is final, its Undo gone at once — the clock held, so
        // well before the statement's own time runs out.
        tap(KitchenTags.groupTick(STOVETOP))
        assertEquals(setOf(SCALE), store.held.value)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(androidx.compose.ui.test.hasText(unticked)).fetchSemanticsNodes().isNotEmpty() }
        fun undoShown() = compose.onAllNodes(androidx.compose.ui.test.hasText(undo)).fetchSemanticsNodes().isNotEmpty()
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(SECOND_MS)
            compose.waitForIdle()
            assertTrue("left alone a second, the Undo still shows", undoShown())
            compose.runOnUiThread { store.hold(listOf(SCALE), has = false) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SECOND_MS)
            compose.waitForIdle()
            assertFalse("a second after another tick, the Undo is gone", undoShown())
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertEquals(emptySet<Long>(), store.held.value)
    }

    // My kitchen opens from the book's filters sheet: "Set up My kitchen" while nothing is ticked — the filter
    // "Enough equipment" not offered — and "Edit My kitchen" beside the filter once something is; it opens above the
    // book, every group folded, and Back returns to the book.
    @Test
    fun itOpensFromTheFiltersSheet() = fromTheFilters("Set up My kitchen", "Edit My kitchen", "Back to the book")

    @Test
    @Config(qualifiers = "ru")
    fun itOpensFromTheFiltersSheetInRussian() = fromTheFilters("Настроить «Мою кухню»", "Изменить «Мою кухню»", "Вернуться к книге")

    private fun fromTheFilters(setUp: String, edit: String, back: String) {
        session.choose(LibraryView.OWN)
        compose.setContent {
            MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor, themeSetting = HeldThemeSetting(), kitchen = kitchen) }
        }
        runBlocking { recipes.save(app.recipefork.core.recipe.Recipe.create(Instant.parse("2026-10-10T12:00:00Z"), null).copy(title = "Soup")) }
        fun openSheet() {
            compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.FILTER_ADD) }
            compose.onNodeWithTag(LibraryTags.FILTER_ADD).performClick()
            compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.TAG_CHOOSER) && exists(LibraryTags.EQUIPMENT_KITCHEN) }
            compose.onNodeWithTag(LibraryTags.EQUIPMENT_KITCHEN).scrolledTo()
            compose.waitForIdle()
        }
        openSheet()
        assertEquals(listOf(setUp), compose.textsOf(LibraryTags.EQUIPMENT_KITCHEN))
        assertFalse("the filter is not offered while the kitchen is empty", exists(LibraryTags.EQUIPMENT_CHOICE))
        compose.onNodeWithTag(LibraryTags.EQUIPMENT_KITCHEN).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) && !exists(LibraryTags.PAGER) && !exists(LibraryTags.FILTERS_SHEET) }
        assertEquals(back, AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(KitchenTags.BACK).fetchSemanticsNode()))
        assertFalse(session.settings.value)
        tap(KitchenTags.groupTick(MEASURING))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.PAGER) && !exists(KitchenTags.SCREEN) }
        assertFalse(session.kitchen.value)
        openSheet()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.EQUIPMENT_CHOICE) }
        assertEquals(listOf(edit), compose.textsOf(LibraryTags.EQUIPMENT_KITCHEN))
        compose.onNodeWithTag(LibraryTags.EQUIPMENT_KITCHEN).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) && !exists(LibraryTags.PAGER) }
        compose.onNodeWithTag(KitchenTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.PAGER) && !exists(KitchenTags.SCREEN) }
    }

    // The search finds by label or synonym in either language, as the editor's field does; a consumable is not found.
    // Nothing found says so and offers the whole list back.
    @Test
    fun theSearchFindsInEitherLanguageAndBySynonyms() {
        showSettings()
        openKitchen()
        val field = compose.onNodeWithTag(KitchenTags.SEARCH).fetchSemanticsNode()
        assertEquals(androidx.compose.ui.text.input.ImeAction.Search, field.config[SemanticsProperties.ImeAction])
        search("Frying")
        assertEquals(listOf(KitchenTags.item(FRYING_PAN)), found())
        search("кастр")
        assertEquals(listOf(KitchenTags.item(SAUCEPAN)), found())
        search("skillet")
        assertEquals(listOf(KitchenTags.item(FRYING_PAN)), found())
        assertEquals(listOf("Frying pan", "skillet · Stovetop cookware"), compose.textsOf(KitchenTags.item(FRYING_PAN)))
        search("весы")
        assertEquals(listOf(KitchenTags.item(SCALE)), found())
        tap(KitchenTags.item(SCALE), list = KitchenTags.RESULTS)
        assertEquals(setOf(SCALE), store.held.value)
        assertEquals(ToggleableState.On, state(KitchenTags.item(SCALE)))

        search("parchment")
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.EMPTY) }
        assertEquals(listOf("Nothing found for “parchment”"), compose.textsOf(LibraryTags.emptyHeadline(KitchenTags.EMPTY)))
        compose.onNodeWithTag(KitchenTags.EMPTY_CLEAR).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) && !exists(KitchenTags.EMPTY) }
        assertEquals("", compose.onNodeWithTag(KitchenTags.SEARCH).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)

        search("pot")
        compose.onNodeWithTag(KitchenTags.SEARCH_CLEAR).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) && !exists(KitchenTags.RESULTS) }
    }

    // Back — the bar's and the system's — returns to the settings, and back from them to the book.
    @Test
    fun backReturnsToTheSettings() {
        showSettings()
        openKitchen()
        val back = compose.onNodeWithTag(KitchenTags.BACK).fetchSemanticsNode()
        assertEquals("Back to the settings", AccessibilityChecks.clickActionLabel(back))
        compose.onNodeWithTag(KitchenTags.BACK).performClick()
        awaitSettings()
        assertFalse(session.kitchen.value)
        openKitchen()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitSettings()
        assertFalse(session.kitchen.value)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.PAGER) && !exists(SettingsTags.SCREEN) }
        // My kitchen left open when something else took the settings' place: the settings opened again show themselves.
        compose.runOnIdle { session.openSettings() }
        awaitSettings()
        openKitchen()
        compose.runOnIdle { session.showHeld() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.PAGER) && !exists(KitchenTags.SCREEN) && !exists(SettingsTags.SCREEN) }
        compose.runOnIdle { session.openSettings() }
        awaitSettings()
        assertFalse(exists(KitchenTags.SCREEN))
    }

    // My kitchen open when the screen turns, or when the process ends, is open again, above the settings.
    @Test
    fun myKitchenComesBackAfterAConfigurationChangeAndTheEndOfTheProcess() {
        val tester = showSettings()
        openKitchen()
        tester.emulateSavedInstanceStateRestore()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) }
        session = LibrarySession(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        tester.emulateSavedInstanceStateRestore()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) }
        compose.waitForIdle()
        assertTrue(session.kitchen.value)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitSettings()
        assertFalse(session.kitchen.value)
        assertTrue(session.settings.value)
    }

    // At twice the font scale on a 320 dp screen every target stays 48 dp, nothing spills sideways, no text is cut and no
    // word breaks inside itself.
    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleOnANarrowScreen() = narrowAtTwiceTheFontScale()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleOnANarrowScreen() = narrowAtTwiceTheFontScale()

    private fun narrowAtTwiceTheFontScale() {
        store.hold(listOf(FRYING_PAN), has = true)
        showSettings()
        openKitchen()
        assertFits()
        unfold(STOVETOP)
        assertFits()
        compose.onNodeWithTag(KitchenTags.SEARCH).performTextInput("zzz")
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.EMPTY) }
        assertFits()
        compose.onNodeWithTag(KitchenTags.EMPTY_CLEAR).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) }
        search("skillet")
        assertFits()
    }

    private fun assertFits() {
        compose.waitForIdle()
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 3)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 3)
        AccessibilityChecks.assertDistinctLabels(compose)
        val width = compose.onNodeWithTag(KitchenTags.SCREEN).fetchSemanticsNode().boundsInRoot.right
        val texts = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(texts.isNotEmpty())
        texts.forEach { node ->
            assertTrue("${node.config[SemanticsProperties.Text]} spills sideways", node.boundsInRoot.right <= width + 0.5f)
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            // Text is cut when it runs past its bottom or ends in an ellipsis; wrapped text is laid out the width it is
            // given, so its width says nothing.
            layouts.forEach { layout ->
                val cut = layout.didOverflowHeight || (0 until layout.lineCount).any(layout::isLineEllipsized)
                assertFalse("${node.config[SemanticsProperties.Text]} is cut", cut)
            }
        }
        // T-273: and no word of it breaks inside itself (A11Y-004).
        app.recipefork.app.ui.WholeWords.assertWhole(compose, hasAnyAncestor(hasTestTag(KitchenTags.SCREEN)), "My kitchen")
    }

    // L10N: My kitchen reads in Russian, every word of the interface from the resources.
    @Test
    @Config(qualifiers = "ru")
    fun myKitchenReadsInRussian() {
        showSettings()
        compose.waitUntil(PATIENCE_MS) { kitchenValue() == "Пока ничего не отмечено" }
        assertEquals("Моя кухня", compose.textsOf(SettingsTags.KITCHEN).first())
        openKitchen()
        assertEquals(listOf("Моя кухня"), compose.textsOf(KitchenTags.TITLE))
        assertEquals(listOf("Посуда для плиты", "0 из 2"), compose.textsOf(KitchenTags.group(STOVETOP)))
        assertEquals("Найти посуду и технику", context.getString(app.recipefork.app.R.string.kitchen_search))
        unfold(STOVETOP)
        compose.onNodeWithTag(KitchenTags.LIST).performScrollToNode(hasTestTag(KitchenTags.item(SAUCEPAN)))
        assertEquals("Кастрюля", compose.textsOf(KitchenTags.item(SAUCEPAN)).first())
        LocalizationChecks.assertScreenTextFromResources(compose, context, KITCHEN_DICTIONARY.map { it.labelRu }.toSet())
        search("ничего такого")
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.EMPTY) }
        assertEquals(listOf("По запросу «ничего такого» ничего не найдено"), compose.textsOf(LibraryTags.emptyHeadline(KitchenTags.EMPTY)))
        assertEquals("Показать всю посуду и технику", compose.textsOf(KitchenTags.EMPTY_CLEAR).single())
        LocalizationChecks.assertScreenTextFromResources(compose, context, setOf("ничего такого"))
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, setOf("ничего такого"))
        compose.onNodeWithTag(KitchenTags.EMPTY_CLEAR).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) }
        compose.onNodeWithTag(KitchenTags.BACK).performClick()
        awaitSettings()
        store.hold(listOf(FRYING_PAN, SAUCEPAN), has = true)
        compose.waitUntil(PATIENCE_MS) { kitchenValue() == "Отмечено 2 предмета" }
    }

    // T-205: a dictionary that cannot be read crashes neither the settings nor My kitchen: the settings' row counts
    // nothing, My kitchen says it could not read the equipment and offers "Try again", which reads it anew.
    @Test
    fun anUnreadableDictionaryIsSaidSoAndTriedAgain() = unreadableDictionary("Nothing ticked yet", "Couldn’t read the equipment", "Try again")

    @Test
    @Config(qualifiers = "ru")
    fun anUnreadableDictionaryIsSaidSoInRussian() =
        unreadableDictionary("Пока ничего не отмечено", "Не удалось прочитать список посуды и техники", "Попробовать ещё раз")

    private fun unreadableDictionary(none: String, headline: String, retry: String) {
        store.hold(listOf(FRYING_PAN), has = true)
        unreadable = true
        showSettings()
        compose.waitUntil(PATIENCE_MS) { kitchenValue() == none }
        compose.onNodeWithTag(SettingsTags.KITCHEN).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.UNREADABLE) }
        assertEquals(listOf(headline), compose.textsOf(LibraryTags.emptyHeadline(KitchenTags.UNREADABLE)))
        assertEquals(retry, compose.textsOf(KitchenTags.UNREADABLE_RETRY).single())
        assertFalse(exists(KitchenTags.LIST))
        LocalizationChecks.assertScreenTextFromResources(compose, context, emptySet())
        AccessibilityChecks.assertTouchTargets(compose)
        unreadable = false
        compose.onNodeWithTag(KitchenTags.UNREADABLE_RETRY).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) }
        assertFalse(exists(KitchenTags.UNREADABLE))
        unfold(STOVETOP)
        assertEquals(ToggleableState.On, state(KitchenTags.item(FRYING_PAN)))
    }

    // v0.30, T-221 review: at twice the font scale on a 320 dp phone every group of the shipped dictionary keeps its
    // name's words whole — «Кухонные принадлежности» among them — beside the checkbox and the chevron.
    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianTheShippedGroupNamesKeepTheirWordsWholeAtTwiceTheFontScale() = shippedGroupNamesKeepWordsWhole()

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun theShippedGroupNamesKeepTheirWordsWholeAtTwiceTheFontScale() = shippedGroupNamesKeepWordsWhole()

    private fun shippedGroupNamesKeepWordsWhole() {
        val shipped = app.recipefork.app.dictionary.DictionarySnapshot.read(context).equipment.map(EquipmentEntryEntity::of)
        val language = if (context.resources.configuration.locales[0].language == "ru") InterfaceLanguage.RUSSIAN else InterfaceLanguage.ENGLISH
        val groups = Kitchen.groups(shipped, language)
        assertTrue(groups.any { it.group.id == TOOLS_AND_UTENSILS })
        compose.setContent { MaterialTheme { KitchenScreen(KitchenEnvironment(HeldKitchen(), EquipmentSource { shipped }), onBack = {}) } }
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.LIST) }
        groups.forEach { (group, _) ->
            compose.onNodeWithTag(KitchenTags.LIST).performScrollToNode(hasTestTag(KitchenTags.group(group.id)))
            compose.waitForIdle()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(KitchenTags.groupName(group.id), useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                if (end <= 0 || end >= text.length) continue
                val between = text[end - 1].isWhitespace() || text[end].isWhitespace() || text[end - 1] == '-'
                assertTrue("“$text” breaks inside a word after line $line (“${text.substring(0, end)}|”)", between)
            }
            assertFalse("“$text” is cut", layout.didOverflowHeight || (0 until layout.lineCount).any(layout::isLineEllipsized))
        }
    }

    private companion object {
        /** A second of the test's clock. */
        const val SECOND_MS = 1_000L

        /** The shipped group with the longest Russian word, «Кухонные принадлежности». */
        const val TOOLS_AND_UTENSILS = 10400L
    }
}
