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
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.LocalizationChecks
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
        assertEquals(ToggleableState.On, state(KitchenTags.item(FRYING_PAN)))
    }

    // A group's row is a tri-state checkbox: some ticked is partly, a tap ticks the whole group, another unticks it.
    @Test
    fun aGroupIsTickedWhole() {
        store.hold(listOf(FRYING_PAN), has = true)
        showSettings()
        openKitchen()
        val group = KitchenTags.group(STOVETOP)
        assertEquals(ToggleableState.Indeterminate, state(group))
        assertEquals(androidx.compose.ui.semantics.Role.Checkbox, compose.onNodeWithTag(group).fetchSemanticsNode().config[SemanticsProperties.Role])
        tap(group)
        assertEquals(ToggleableState.On, state(group))
        assertEquals(ToggleableState.On, state(KitchenTags.item(SAUCEPAN)))
        assertEquals(setOf(FRYING_PAN, SAUCEPAN), store.held.value)
        assertEquals(listOf("Stovetop cookware", "2 of 2"), compose.textsOf(group))
        tap(group)
        assertEquals(ToggleableState.Off, state(group))
        assertEquals(emptySet<Long>(), store.held.value)
        // The other group is untouched by either.
        assertEquals(ToggleableState.Off, state(KitchenTags.group(MEASURING)))
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

    // At twice the font scale on a 320 dp screen every target stays 48 dp, nothing spills sideways and no text is cut.
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
        compose.onNodeWithTag(KitchenTags.LIST).performScrollToNode(hasTestTag(KitchenTags.item(SAUCEPAN)))
        assertEquals("Кастрюля", compose.textsOf(KitchenTags.item(SAUCEPAN)).first())
        LocalizationChecks.assertScreenTextFromResources(compose, context, KITCHEN_DICTIONARY.map { it.labelRu }.toSet())
        search("ничего такого")
        compose.waitUntil(PATIENCE_MS) { exists(KitchenTags.EMPTY) }
        assertEquals(listOf("По запросу «ничего такого» ничего не найдено"), compose.textsOf(LibraryTags.emptyHeadline(KitchenTags.EMPTY)))
        assertEquals("Показать всё оборудование", compose.textsOf(KitchenTags.EMPTY_CLEAR).single())
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
        unreadableDictionary("Пока ничего не отмечено", "Не удалось прочитать список оборудования", "Попробовать ещё раз")

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
        assertEquals(ToggleableState.On, state(KitchenTags.item(FRYING_PAN)))
    }
}
