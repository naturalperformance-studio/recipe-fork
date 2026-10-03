package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.UnitType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** A seeded test dictionary: a parent with two leaves, an entry beside them, milk and coconut milk. */
val TEST_INGREDIENTS = DictionariesDto(
    version = "test-ingredients-1",
    ingredients = listOf(
        IngredientEntryDto(1, "молоко", "milk"),
        IngredientEntryDto(2, "кокосовое молоко", "coconut milk"),
        IngredientEntryDto(10, "курица", "chicken"),
        IngredientEntryDto(11, "крылышки", "chicken wings", 10),
        IngredientEntryDto(12, "филе", "fillet", 10),
        IngredientEntryDto(20, "куркума", "turmeric"),
    ),
    tags = SAMPLE_DICTIONARIES.tags,
)

/**
 * The ingredient field of the editor as it offers the dictionary (FLOW-057, FLOW-022 (1, 3), DOM-009, L10N-003):
 * what it offers, what picking an entry or free text stores, replacing free text by an entry, and that the choice
 * outlives the editor.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class IngredientNameFieldTest {
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
    private var shown = false

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(TEST_INGREDIENTS) }
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
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun writeANewRecipe(): RecipeId {
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    private fun openDirectly(id: RecipeId) {
        show()
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun addIngredient(position: Int) {
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(position)), PATIENCE_MS)
    }

    /** Types [text] as the whole content of the row's ingredient field and waits until it is held. */
    private fun type(position: Int, text: String) {
        compose.onNodeWithTag(EditorTags.ingredientName(position)).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }

    /** The choices the row's field offers, by tag, in the order shown. */
    private fun choices(position: Int): List<String> {
        if (!exists(IngredientNameTags.offer(position))) return emptyList()
        return compose.onAllNodes(
            hasAnyAncestor(hasTestTag(IngredientNameTags.offer(position))) and hasTestTagStartingWith("editor-ingredient-$position-offer-"),
        ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
    }

    private fun entryChoices(position: Int, vararg ids: Long) = ids.map { IngredientNameTags.entry(position, it) }

    private fun shownText(tag: String): String = compose.textsOf(tag).joinToString("")

    private fun pick(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(tag), PATIENCE_MS)
        compose.waitForIdle()
    }

    // FLOW-057 (1, 4, 6, 10) as the screen shows them: the leaves first, the two groups, then free text beside them.
    @Test
    @Config(qualifiers = "ru")
    fun typingOffersEntriesInOrderWithFreeTextBeside() {
        writeANewRecipe()
        addIngredient(0)
        assertEquals(emptyList<String>(), choices(0))
        type(0, "кур")
        assertEquals(entryChoices(0, 11, 12, 10, 20) + IngredientNameTags.freeText(0), choices(0))
        assertEquals(listOf("крылышки", "филе", "курица", "куркума"), entryChoices(0, 11, 12, 10, 20).map(::shownText))
        assertEquals(string(R.string.editor_ingredient_free_text_offer, "кур"), shownText(IngredientNameTags.freeText(0)))
        // FLOW-057 (2, 9): a word that begins no word keeps an entry out; what begins with the text comes first.
        type(0, "мол")
        assertEquals(entryChoices(0, 1, 2) + IngredientNameTags.freeText(0), choices(0))
        type(0, "кокосовое мол")
        assertEquals(entryChoices(0, 2) + IngredientNameTags.freeText(0), choices(0))
        type(0, "свежее молоко")
        assertEquals(listOf(IngredientNameTags.freeText(0)), choices(0))
        // FLOW-057 (3): case and `ё` do not change the offer.
        type(0, "МОЛ")
        assertEquals(entryChoices(0, 1, 2) + IngredientNameTags.freeText(0), choices(0))
        // Nothing typed offers nothing.
        type(0, "")
        assertEquals(emptyList<String>(), choices(0))
    }

    // FLOW-057 (5), L10N-003: matched in either language, shown in the interface language.
    @Test
    @Config(qualifiers = "ru")
    fun anEntryMatchedInEnglishIsShownInRussian() = shownInTheInterfaceLanguage("chick", "курица", "кури")

    @Test
    fun anEntryMatchedInRussianIsShownInEnglish() = shownInTheInterfaceLanguage("кури", "chicken", "chick")

    private fun shownInTheInterfaceLanguage(typed: String, label: String, sameEntry: String) {
        val id = writeANewRecipe()
        addIngredient(0)
        type(0, typed)
        assertTrue(choices(0).contains(IngredientNameTags.entry(0, 10)))
        assertEquals(label, shownText(IngredientNameTags.entry(0, 10)))
        val entries = choices(0) - IngredientNameTags.freeText(0)
        // The same entry is offered by its label in the other language, and shown the same.
        type(0, sameEntry)
        assertEquals(entries, choices(0) - IngredientNameTags.freeText(0))
        assertEquals(label, shownText(IngredientNameTags.entry(0, 10)))
        pick(IngredientNameTags.entry(0, 10))
        assertEquals(label, fieldText(EditorTags.ingredientName(0)))
        assertEquals(10L, stored(id).ingredients.single().dictionaryEntry)
    }

    // FLOW-057 (7, 8), FLOW-022 (1): free text is offered unless the text is exactly an offered entry's label.
    @Test
    @Config(qualifiers = "ru")
    fun exactlyALabelOffersNoFreeText() {
        writeANewRecipe()
        addIngredient(0)
        listOf("молоко", "Молоко", " молоко ", "milk", "MILK").forEach { typed ->
            type(0, typed)
            val offered = choices(0)
            assertTrue(typed, offered.contains(IngredientNameTags.entry(0, 1)))
            assertTrue(typed, IngredientNameTags.freeText(0) !in offered)
        }
        listOf("молок", "молоко без лактозы").forEach { typed ->
            type(0, typed)
            assertTrue(typed, IngredientNameTags.freeText(0) in choices(0))
        }
    }

    // DOM-009: an entry picked stores the entry and no free-text name; free text stores the reverse.
    @Test
    fun pickingAnEntryStoresTheEntryAndFreeTextStoresTheWords() {
        val id = writeANewRecipe()
        addIngredient(0)
        type(0, "chick")
        assertEquals(string(R.string.editor_ingredient_pick_action, "chicken"), AccessibilityChecks.clickActionLabel(
            compose.onNodeWithTag(IngredientNameTags.entry(0, 10)).fetchSemanticsNode(),
        ))
        pick(IngredientNameTags.entry(0, 10))
        val entry = stored(id).ingredients.single()
        assertEquals(10L, entry.dictionaryEntry)
        assertNull(entry.freeTextName)
        assertNull(entry.qualifier)
        assertEquals("chicken", fieldText(EditorTags.ingredientName(0)))
        assertTrue(!exists(EditorTags.ingredientWarning(0)))
        assertEquals(emptyList<String>(), choices(0))

        addIngredient(1)
        type(1, " wild garlic ")
        assertEquals(string(R.string.editor_ingredient_free_text_offer, "wild garlic"), shownText(IngredientNameTags.freeText(1)))
        pick(IngredientNameTags.freeText(1))
        val words = stored(id).ingredients[1]
        assertNull(words.dictionaryEntry)
        assertEquals("wild garlic", words.freeTextName)
        assertEquals(string(R.string.editor_ingredient_free_text_warning), compose.descriptionOf(EditorTags.ingredientWarning(1)).single())
        assertEquals(emptyList<String>(), choices(1))
    }

    // FLOW-057 (7): left with exactly an entry's label typed, the row is that entry and warns of nothing.
    @Test
    fun leavingTheFieldWithExactlyALabelNamesTheRowByTheEntry() {
        val id = writeANewRecipe()
        addIngredient(0)
        type(0, "Milk ")
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        compose.waitForIdle()
        val row = stored(id).ingredients.single()
        assertEquals(1L, row.dictionaryEntry)
        assertNull(row.freeTextName)
        assertEquals("milk", fieldText(EditorTags.ingredientName(0)))
        assertTrue(!exists(EditorTags.ingredientWarning(0)))
        // Text that is not a label stays the author's own words.
        addIngredient(1)
        type(1, "mil")
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performImeAction()
        compose.waitForIdle()
        assertEquals("mil", stored(id).ingredients[1].freeTextName)
        assertNull(stored(id).ingredients[1].dictionaryEntry)
    }

    // FLOW-022 (3): a free-text row of an editable recipe is replaced by an entry, and its warning is gone.
    @Test
    fun aFreeTextRowIsReplacedByAnEntryAndTheWarningIsGone() {
        val recipe = Recipe.create(created, null).copy(
            ingredients = listOf(Ingredient(0, null, null, "chiken", UnitType.EXACT, QuantityUnit.GRAM, null)),
        )
        runBlocking { recipes.save(recipe) }
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientWarning(0)), PATIENCE_MS)
        assertEquals("chiken", fieldText(EditorTags.ingredientName(0)))
        type(0, "chicken w")
        pick(IngredientNameTags.entry(0, 11))
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.ingredientWarning(0)), PATIENCE_MS)
        val row = stored(recipe.id).ingredients.single()
        assertEquals(11L, row.dictionaryEntry)
        assertNull(row.freeTextName)
        assertEquals("chicken wings", fieldText(EditorTags.ingredientName(0)))
        // Typed over again, the row is the author's words once more and warns again.
        type(0, "smoked chicken wings")
        assertNull(stored(recipe.id).ingredients.single().dictionaryEntry)
        assertEquals("smoked chicken wings", stored(recipe.id).ingredients.single().freeTextName)
        assertTrue(exists(EditorTags.ingredientWarning(0)))
    }

    // FLOW-023 (11), FLOW-022 (5): these entries give no divisibility, so a picked `piece` row still asks.
    @Test
    fun aPieceRowPickedFromAnEntryStillAsksForDivisibility() {
        val id = writeANewRecipe()
        addIngredient(0)
        type(0, "wing")
        pick(IngredientNameTags.entry(0, 11))
        compose.onNodeWithTag(EditorTags.ingredientUnit(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(QuantityUnit.PIECE)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.PIECE)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(QuantityUnit.PIECE)), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.divisibility(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.divisible(0, false)).performScrollTo().performClick()
        compose.waitForIdle()
        val row = stored(id).ingredients.single()
        assertEquals(11L, row.dictionaryEntry)
        assertEquals(UnitType.PIECE, row.unitType)
        assertEquals(false, row.divisible)
    }

    // The chosen name outlives the editor: back to the book through the card, and opened again.
    @Test
    @Config(qualifiers = "ru")
    fun theChosenNameSurvivesLeavingTheEditor() {
        val id = writeANewRecipe()
        addIngredient(0)
        type(0, "chick")
        pick(IngredientNameTags.entry(0, 10))
        addIngredient(1)
        type(1, "черемша")
        pick(IngredientNameTags.freeText(1))
        val before = stored(id)
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        // The card names the row by the entry's label too.
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.CONTENT), PATIENCE_MS)
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)

        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(1)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(0)) == "курица" }
        assertEquals("черемша", fieldText(EditorTags.ingredientName(1)))
        assertTrue(!exists(EditorTags.ingredientWarning(0)))
        assertTrue(exists(EditorTags.ingredientWarning(1)))
        assertEquals(before, stored(id))
        assertEquals(listOf(10L, null), before.ingredients.map { it.dictionaryEntry })
        assertEquals(listOf(null, "черемша"), before.ingredients.map { it.freeTextName })
    }

    // L10N-001, A11Y-003, A11Y-005: the open offer reads in the interface language, its choices labelled and 48 dp.
    @Test
    fun theOpenOfferInEnglish() = theOpenOffer("chi", setOf("chi", "chicken", "chicken wings", "fillet"))

    @Test
    @Config(qualifiers = "ru")
    fun theOpenOfferInRussian() = theOpenOffer("кур", setOf("кур", "курица", "крылышки", "филе", "куркума"))

    private fun theOpenOffer(typed: String, content: Set<String>) {
        writeANewRecipe()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.offered(10)), PATIENCE_MS)
        addIngredient(0)
        type(0, typed)
        assertTrue(choices(0).size >= 3)
        val all = content + tagLabels(TEST_INGREDIENTS)
        LocalizationChecks.assertScreenTextFromResources(compose, context, all)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, all)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 10)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 10)
    }
}
