package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.kitchen.FRYING_PAN
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.kitchen.KITCHEN_DICTIONARY
import app.recipefork.app.kitchen.Kitchen
import app.recipefork.app.kitchen.KitchenEnvironment
import app.recipefork.app.kitchen.PARCHMENT
import app.recipefork.app.kitchen.REMOVED_POT
import app.recipefork.app.kitchen.SAUCEPAN
import app.recipefork.app.kitchen.SCALE
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.ListingParams
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import java.util.Locale

/**
 * T-206: the book's filter "Enough equipment" — offered while My kitchen holds something that counts; on, it hides
 * every recipe naming an entry the kitchen lacks, never for an item of free text; it combines with the other filters,
 * shows among them as a chip, follows what is ticked, and is switched off with the kitchen emptied. The screen is tall
 * enough for every entry of a test to be laid out at once.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "h2000dp")
class EquipmentFilterTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    private val flour = IngredientEntryEntity(1, "мука", "flour", null)
    private val quick = TagEntity(12, "быстро", "Quick")

    private val store = HeldKitchen(setOf(FRYING_PAN))
    private val kitchen = KitchenEnvironment(store, EquipmentSource { KITCHEN_DICTIONARY })
    private val card = CardEnvironment(ingredients = IngredientEntries { listOf(flour) })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = listOf(quick)
        },
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun item(entry: Long) = EquipmentItem(entry)
    private fun freeText(name: String) = EquipmentItem(null, freeTextName = name)

    /** An own recipe needing [equipment], last changed [minutesAgo] minutes before now. */
    private fun own(
        title: String,
        vararg equipment: EquipmentItem,
        minutesAgo: Long = 0,
        tags: Set<Long> = emptySet(),
        language: RecipeLanguage? = null,
        ingredients: List<Ingredient> = emptyList(),
    ): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(
            title = title, equipment = equipment.toList(), tags = tags, language = language, ingredients = ingredients,
        )
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun found(title: String, vararg equipment: EquipmentItem, minutesAgo: Long = 0) {
        val recipe = Recipe.create(now, owner = null).copy(title = title, equipment = equipment.toList())
            .inState(RecipeImport.FOUND_STATE)
        runBlocking { recipes.addFound(recipe, now.minusSeconds(minutesAgo * 60)) }
    }

    /** The book: needing a frying pan, a saucepan, a scale and a basket of free text, a basket alone, parchment, nothing. */
    private fun theBook() {
        own("Pancakes", item(FRYING_PAN), minutesAgo = 1)
        own("Soup", item(SAUCEPAN), minutesAgo = 2)
        own("Bread", item(SCALE), freeText("Proving basket"), minutesAgo = 3)
        own("Wok noodles", freeText("Wok"), minutesAgo = 4)
        own("Baked fish", item(PARCHMENT), minutesAgo = 5)
        own("Old pot stew", item(REMOVED_POT), minutesAgo = 6)
        own("Toast", minutesAgo = 7)
    }

    private val all = arrayOf("Pancakes", "Soup", "Bread", "Wok noodles", "Baked fish", "Old pot stew", "Toast")

    /** What a kitchen holding a frying pan alone can cook of [theBook]. */
    private val cookable = arrayOf("Pancakes", "Wok noodles", "Baked fish", "Old pot stew", "Toast")

    private fun show(view: LibraryView) {
        session.choose(view)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor, kitchen = kitchen) } }
        compose.waitForIdle()
    }

    private val anEntryTitle = SemanticsMatcher("an entry's title") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-entry-title-") == true
    }

    private fun listed(): List<String> = compose.onAllNodes(anEntryTitle, useUnmergedTree = true).fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    private fun assertListed(vararg titles: String) {
        runCatching { compose.waitUntil(PATIENCE_MS) { listed() == titles.toList() } }
        assertEquals(titles.toList(), listed())
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun click(tag: String) {
        compose.waitUntilExactlyOneExists(hasTestTag(tag), PATIENCE_MS)
        compose.onNodeWithTag(tag).scrolledTo().performClick()
        compose.waitForIdle()
    }

    private fun openTheSheet() {
        click(LibraryTags.FILTER_ADD)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.TAG_CHOOSER), PATIENCE_MS)
    }

    private fun closeTheSheet() {
        click(LibraryTags.FILTERS_SHEET_DONE)
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.FILTERS_SHEET), PATIENCE_MS)
    }

    /** Every text drawn inside the node tagged [tag], in order. */
    private fun textsIn(tag: String): List<String> = compose.onAllNodes(
        hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    private fun lineCount(tag: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().lineCount
    }

    private fun toggleState(tag: String): ToggleableState? =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

    private fun equipmentOn(view: LibraryView = LibraryView.OWN) = session.filters(view).value.equipment

    // Criterion 1: offered while the kitchen holds an item that counts; on, it hides the recipes naming an entry the
    // kitchen lacks — never for free text, a consumable or a removed entry — and shows as a chip that switches it off.
    @Test
    fun theSheetOffersEnoughEquipmentAndItHidesWhatTheKitchenLacks() {
        theBook()
        show(LibraryView.OWN)
        assertListed(*all)
        openTheSheet()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.EQUIPMENT_CHOICE), PATIENCE_MS)
        assertEquals(listOf("Equipment", "Recipes that need something My kitchen lacks are hidden.", "Enough equipment", "Edit My kitchen"),
            textsIn(LibraryTags.EQUIPMENT_CHOOSER))
        assertEquals(ToggleableState.Off, toggleState(LibraryTags.EQUIPMENT_CHOICE))
        assertEquals(Role.Checkbox, compose.onNodeWithTag(LibraryTags.EQUIPMENT_CHOICE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        compose.onNodeWithTag(LibraryTags.EQUIPMENT_CHOICE).assertHeightIsAtLeast(48.dp)
        click(LibraryTags.EQUIPMENT_CHOICE)
        compose.waitUntil(PATIENCE_MS) { toggleState(LibraryTags.EQUIPMENT_CHOICE) == ToggleableState.On }
        assertEquals(BookFilters(equipment = true), session.filters(LibraryView.OWN).value)
        closeTheSheet()
        assertListed(*cookable)
        // Shown among the filters in force, a selected chip; tapping it switches the filter off.
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.EQUIPMENT_FILTER), PATIENCE_MS)
        assertEquals(listOf("Enough equipment"), compose.textsOf(LibraryTags.EQUIPMENT_FILTER))
        assertEquals(true, compose.onNodeWithTag(LibraryTags.EQUIPMENT_FILTER).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        compose.onNodeWithTag(LibraryTags.EQUIPMENT_FILTER).assertHeightIsAtLeast(48.dp)
        click(LibraryTags.EQUIPMENT_FILTER)
        assertListed(*all)
        assertFalse(equipmentOn())
        assertFalse(exists(LibraryTags.EQUIPMENT_FILTER))
    }

    // Criterion 1: the marked view is filtered the same way.
    @Test
    fun theMarkedViewIsFilteredToo() {
        found("Found pancakes", item(FRYING_PAN), minutesAgo = 1)
        found("Found soup", item(SAUCEPAN), minutesAgo = 2)
        found("Found wok", freeText("Wok"), minutesAgo = 3)
        session.filter(LibraryView.MARKED, BookFilters(equipment = true))
        show(LibraryView.MARKED)
        assertListed("Found pancakes", "Found wok")
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.EQUIPMENT_FILTER), PATIENCE_MS)
    }

    // Criterion 1: nothing ticked, or nothing that counts — a consumable, an entry since removed — offers no such filter;
    // the section offers to set My kitchen up instead (v0.30, T-221).
    @Test
    fun aKitchenWithNothingThatCountsOffersNoSuchFilter() {
        theBook()
        store.hold(listOf(FRYING_PAN), false)
        show(LibraryView.OWN)
        openTheSheet()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.EQUIPMENT_KITCHEN), PATIENCE_MS)
        assertFalse(exists(LibraryTags.EQUIPMENT_CHOICE))
        assertEquals(
            listOf("Equipment", "Tick what your kitchen has, and recipes that need something else can be hidden.", "Set up My kitchen"),
            textsIn(LibraryTags.EQUIPMENT_CHOOSER),
        )
        compose.onNodeWithTag(LibraryTags.EQUIPMENT_KITCHEN).assertHeightIsAtLeast(48.dp)
        closeTheSheet()
        store.hold(listOf(PARCHMENT, REMOVED_POT), true)
        compose.waitForIdle()
        openTheSheet()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.EQUIPMENT_KITCHEN), PATIENCE_MS)
        assertFalse(exists(LibraryTags.EQUIPMENT_CHOICE))
        closeTheSheet()
        // Ticking an item that counts offers it.
        store.hold(listOf(SAUCEPAN), true)
        openTheSheet()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.EQUIPMENT_CHOICE), PATIENCE_MS)
    }

    // Criterion 2: the kitchen emptied, the filter is off, its chip gone, every recipe listed, and it is not offered.
    @Test
    fun emptyingTheKitchenSwitchesTheFilterOff() {
        theBook()
        session.filter(LibraryView.OWN, BookFilters(equipment = true))
        show(LibraryView.OWN)
        assertListed(*cookable)
        store.hold(listOf(FRYING_PAN), false)
        compose.waitUntil(PATIENCE_MS) { !equipmentOn() }
        assertListed(*all)
        assertFalse(exists(LibraryTags.EQUIPMENT_FILTER))
        // Ticking again does not bring it back on: it was switched off.
        store.hold(listOf(FRYING_PAN), true)
        compose.waitForIdle()
        assertListed(*all)
        assertFalse(equipmentOn())
    }

    // Criterion 2: ticking or unticking in My kitchen updates the filtered list.
    @Test
    fun tickingInMyKitchenUpdatesTheList() {
        theBook()
        session.filter(LibraryView.OWN, BookFilters(equipment = true))
        show(LibraryView.OWN)
        assertListed(*cookable)
        store.hold(listOf(SAUCEPAN), true)
        assertListed("Pancakes", "Soup", "Wok noodles", "Baked fish", "Old pot stew", "Toast")
        store.hold(listOf(FRYING_PAN), false)
        assertListed("Soup", "Wok noodles", "Baked fish", "Old pot stew", "Toast")
        store.hold(listOf(SCALE), true)
        assertListed("Soup", "Bread", "Wok noodles", "Baked fish", "Old pot stew", "Toast")
        assertTrue(equipmentOn())
    }

    // Criterion 3: the filter alone hiding every recipe says so, and its one action switches the filter off.
    @Test
    fun theFilterHidingEveryRecipeSaysSoAndOffersToSwitchItOff() {
        own("Soup", item(SAUCEPAN))
        own("Bread", item(SCALE), minutesAgo = 1)
        session.filter(LibraryView.OWN, BookFilters(equipment = true))
        show(LibraryView.OWN)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NOTHING_FOUND), PATIENCE_MS)
        assertEquals(listOf("Each recipe needs something you lack"), compose.textsOf(LibraryTags.emptyHeadline(LibraryTags.NOTHING_FOUND)))
        assertEquals(1, lineCount(LibraryTags.emptyHeadline(LibraryTags.NOTHING_FOUND)))
        assertEquals(listOf("Turn off “Enough equipment”"), compose.textsOf(LibraryTags.NOTHING_FOUND_EQUIPMENT_OFF))
        compose.onNodeWithTag(LibraryTags.NOTHING_FOUND_EQUIPMENT_OFF).assertHeightIsAtLeast(48.dp)
        assertFalse(exists(LibraryTags.NOTHING_FOUND_CLEAR_FILTERS))
        click(LibraryTags.NOTHING_FOUND_EQUIPMENT_OFF)
        assertListed("Soup", "Bread")
        assertEquals(BookFilters(), session.filters(LibraryView.OWN).value)
    }

    // Criterion 3: with another filter on as well, the result is handled as any filters' — one action clears them.
    @Test
    fun withAnotherFilterTheEmptyResultClearsTheFilters() {
        own("Soup", item(SAUCEPAN), language = RecipeLanguage.EN)
        own("Pancakes", item(FRYING_PAN), language = RecipeLanguage.RU, minutesAgo = 1)
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.EN, equipment = true))
        show(LibraryView.OWN)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NOTHING_FOUND_CLEAR_FILTERS), PATIENCE_MS)
        assertEquals(listOf("No recipe matches the filters"), compose.textsOf(LibraryTags.emptyHeadline(LibraryTags.NOTHING_FOUND)))
        click(LibraryTags.NOTHING_FOUND_CLEAR_FILTERS)
        assertListed("Soup", "Pancakes")
        assertEquals(BookFilters(), session.filters(LibraryView.OWN).value)
    }

    // Tests EN/RU: the sheet's section, the chip and the empty result in Russian.
    @Test
    @Config(qualifiers = "ru-h2000dp")
    fun inRussianTheFilterSpeaksRussian() {
        own("Суп", item(SAUCEPAN))
        show(LibraryView.OWN)
        openTheSheet()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.EQUIPMENT_CHOICE), PATIENCE_MS)
        assertEquals(
            listOf("Посуда и техника", "Скроются рецепты, для которых нужно то, чего нет в «Моей кухне».", "Хватает посуды и техники", "Изменить «Мою кухню»"),
            textsIn(LibraryTags.EQUIPMENT_CHOOSER),
        )
        click(LibraryTags.EQUIPMENT_CHOICE)
        closeTheSheet()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.EQUIPMENT_FILTER), PATIENCE_MS)
        assertEquals(listOf("Хватает посуды и техники"), compose.textsOf(LibraryTags.EQUIPMENT_FILTER))
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NOTHING_FOUND), PATIENCE_MS)
        assertEquals(listOf("Для всех рецептов чего-то не хватает"), compose.textsOf(LibraryTags.emptyHeadline(LibraryTags.NOTHING_FOUND)))
        assertEquals(1, lineCount(LibraryTags.emptyHeadline(LibraryTags.NOTHING_FOUND)))
        assertEquals(listOf("Выключить «Хватает посуды и техники»"), compose.textsOf(LibraryTags.NOTHING_FOUND_EQUIPMENT_OFF))
    }

    // Criterion 1: it combines with the ingredient, tag and language filters and the search, where the book's queries run.
    @Test
    fun itCombinesWithTheOtherFiltersAndTheSearch() = runBlocking {
        val withFlour = listOf(Ingredient(0, flour.id, null, null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200), null, null))
        own("Pancakes", item(FRYING_PAN), tags = setOf(quick.id), language = RecipeLanguage.EN, ingredients = withFlour, minutesAgo = 1)
        own("Pan bread", item(SCALE), tags = setOf(quick.id), language = RecipeLanguage.EN, ingredients = withFlour, minutesAgo = 2)
        own("Pan soup", item(SAUCEPAN), language = RecipeLanguage.RU, minutesAgo = 3)
        own("Pan toast", freeText("Toaster"), tags = setOf(quick.id), language = RecipeLanguage.RU, minutesAgo = 4)
        val held = Kitchen(setOf(FRYING_PAN), KITCHEN_DICTIONARY)
        suspend fun titles(query: String, filters: BookFilters) = recipes
            .ownListed(flowOf(ListingParams(query, BookOrder.NEWEST, Locale.ENGLISH, filters)), flowOf(held)) { listOf(flour) }
            .first().rows.map { it.title }
        val on = BookFilters(equipment = true)
        assertEquals(listOf("Pancakes", "Pan toast"), titles("", on))
        assertEquals(listOf("Pancakes", "Pan toast"), titles("pan", on))
        assertEquals(listOf("Pancakes"), titles("pancakes", on))
        assertEquals(listOf("Pancakes", "Pan toast"), titles("", on.copy(tags = setOf(quick.id))))
        assertEquals(listOf("Pancakes"), titles("", on.copy(language = BookLanguage.EN)))
        assertEquals(listOf("Pan toast"), titles("", on.copy(language = BookLanguage.RU)))
        assertEquals(listOf("Pancakes"), titles("", on.copy(ingredient = flour.id)))
        assertEquals(listOf("Pancakes", "Pan bread"), titles("", BookFilters(ingredient = flour.id)))
        // An empty kitchen lacks nothing: the filter, were it on, would hide nothing.
        val empty = recipes.ownListed(flowOf(ListingParams("", BookOrder.NEWEST, Locale.ENGLISH, on)), flowOf(Kitchen(emptySet(), KITCHEN_DICTIONARY))) {
            listOf(flour)
        }.first().rows.map { it.title }
        assertEquals(listOf("Pancakes", "Pan bread", "Pan soup", "Pan toast"), empty)
    }

    // Criterion 2: the choice is kept as the other filters are — through the end of the process, in saved state — and a
    // recipe a file brings clears it with them.
    @Test
    fun theChoiceIsKeptAsTheOtherFiltersAre() {
        val first = SavedStateHandle()
        LibrarySession(first).filter(LibraryView.OWN, BookFilters(tags = setOf(quick.id), equipment = true))
        val restored = LibrarySession(SavedStateHandle(first.keys().associateWith { first.get<Any?>(it) }))
        assertEquals(BookFilters(tags = setOf(quick.id), equipment = true), restored.filters(LibraryView.OWN).value)
        assertFalse(LibrarySession(SavedStateHandle()).filters(LibraryView.OWN).value.equipment)
        restored.filter(LibraryView.MARKED, BookFilters(equipment = true))
        restored.showImported(app.recipefork.core.recipe.RecipeId("brought"))
        assertEquals(BookFilters(), restored.filters(LibraryView.MARKED).value)
    }
}
