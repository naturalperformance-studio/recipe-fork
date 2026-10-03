package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.library.LibraryScreen
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
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
 * A row naming a dictionary entry in the editor: an entry's divisibility asks nothing and decides the quantity's grid
 * (FLOW-023 (11), FLOW-022 (5), DOM-017 (9)); words typed past its label are its qualifier (FLOW-023 (4), DOM-009),
 * shown on the card; the row at 360 dp and font scale 2.0, in both languages.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EntryRowEditorTest {
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

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        val dictionaries = DictionariesDto("flagged-1", FLAGGED_ENTRIES.map { it.toDto() }, SAMPLE_DICTIONARIES.tags)
        runBlocking { device.database.dictionaryDao().replace(dictionaries) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun show(fontScale: Float = 1f) {
        if (shown) return
        shown = true
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        val card = CardEnvironment(ingredients = { FLAGGED_ENTRIES })
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    // The card reads the same dictionary as the editor.
                    val id = opened.value
                    if (id != null) EditorScreen(id, recipes, environment, session.viewModelScope, onClose = { opened.value = null })
                    else LibraryScreen(session, recipes, editor = environment, card = card)
                }
            }
        }
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

    private fun openSeeded(vararg rows: Ingredient, fontScale: Float = 1f, state: RecipeState? = null): RecipeId {
        var recipe = Recipe.create(created, null).copy(title = "Крылья", servings = 2, ingredients = rows.toList())
        if (state != null) recipe = recipe.inState(state)
        runBlocking { recipes.save(recipe) }
        show(fontScale)
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(rows.lastIndex)), PATIENCE_MS)
        return recipe.id
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun row(id: RecipeId, position: Int = 0): Ingredient = stored(id).ingredients[position]

    private fun string(id: Int): String = context.getString(id)

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun addIngredient(position: Int) {
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(position)), PATIENCE_MS)
    }

    private fun type(position: Int, text: String) {
        compose.onNodeWithTag(EditorTags.ingredientName(position)).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun pick(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(tag), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun name(position: Int, typed: String, entry: Long) {
        addIngredient(position)
        type(position, typed)
        pick(IngredientNameTags.entry(position, entry))
    }

    private fun pieces(position: Int) {
        compose.onNodeWithTag(EditorTags.ingredientUnit(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(QuantityUnit.PIECE)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.PIECE)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(QuantityUnit.PIECE)), PATIENCE_MS)
    }

    private fun quantity(position: Int, typed: String) {
        compose.onNodeWithTag(EditorTags.ingredientQuantity(position)).performScrollTo().performTextReplacement(typed)
        compose.waitForIdle()
    }

    private fun errorText(position: Int): String? =
        if (exists(EditorTags.ingredientError(position))) compose.textsOf(EditorTags.ingredientError(position)).joinToString("") else null

    // Scenario 1; FLOW-001 (2), FLOW-023 (11), DOM-017: an entry's flag asks nothing, is held by the row and decides the grid.
    @Test
    @Config(qualifiers = "ru")
    fun anEntrysDivisibilityAsksNothingAndDecidesTheGrid() {
        val id = writeANewRecipe()
        name(0, "крыл", 11)
        pieces(0)
        quantity(0, "1,5")
        compose.waitUntil(PATIENCE_MS) { errorText(0) == string(R.string.editor_quantity_accepts_whole_pieces) }
        assertFalse(exists(EditorTags.divisibility(0)))
        assertEquals(false, row(id).divisible)
        quantity(0, "2")
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.ingredientError(0)), PATIENCE_MS)

        name(1, "кури", 10)
        pieces(1)
        quantity(1, "1,5")
        compose.waitForIdle()
        assertNull(errorText(1))
        assertFalse(exists(EditorTags.divisibility(1)))
        val chicken = row(id, 1)
        assertEquals(true, chicken.divisible)
        assertEquals(Rational.of(3, 2), chicken.quantity)
        assertTrue(chicken.isComplete)
    }

    // Scenario 1; FLOW-022 (5): free text, or an entry giving no divisibility, is still asked.
    @Test
    @Config(qualifiers = "ru")
    fun freeTextOrAnEntryWithoutAFlagIsStillAsked() {
        val id = writeANewRecipe()
        addIngredient(0)
        type(0, "яйцо")
        pick(IngredientNameTags.freeText(0))
        pieces(0)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.divisibility(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.divisible(0, false)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, row(id).divisible)

        name(1, "мяс", 30)
        pieces(1)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.divisibility(1)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.divisible(1, true)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(true, row(id, 1).divisible)
        assertEquals(30L, row(id, 1).dictionaryEntry)
    }

    // DOM-017 (9), DOM-018 (3): a draft written before, holding 1½ of an indivisible entry, is marked on opening.
    @Test
    @Config(qualifiers = "en")
    fun aDraftOffItsEntrysGridIsMarked() {
        val id = openSeeded(Ingredient(0, 11, null, null, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(3, 2), divisible = true))
        val seeded = stored(id)
        compose.waitUntil(PATIENCE_MS) { errorText(0) == string(R.string.editor_quantity_accepts_whole_pieces) }
        assertFalse(exists(EditorTags.divisibility(0)))
        // Opening writes nothing: the row keeps what it held until the author changes something.
        compose.waitForIdle()
        assertEquals(seeded, stored(id))
        assertEquals(true, row(id).divisible)
        // The next change has the row hold its entry's flag.
        quantity(0, "1.5")
        compose.waitUntil(PATIENCE_MS) { row(id).divisible == false }
        assertTrue(exists(EditorTags.ingredientError(0)))
    }

    // Scenario 2; FLOW-023 (4), DOM-009: typed past the label, the words are the entry's qualifier; shown on the card.
    @Test
    @Config(qualifiers = "ru")
    fun wordsTypedPastTheLabelAreTheEntrysQualifier() {
        val id = writeANewRecipe()
        name(0, "крыл", 11)
        assertEquals("крылышки", fieldText(EditorTags.ingredientName(0)))
        // The space before the next word stays in the field while it is typed.
        type(0, "крылышки ")
        assertEquals("крылышки ", fieldText(EditorTags.ingredientName(0)))
        assertEquals(11L, row(id).dictionaryEntry)
        assertNull(row(id).qualifier)
        type(0, "крылышки острые")
        assertEquals("крылышки острые", fieldText(EditorTags.ingredientName(0)))
        assertEquals(11L, row(id).dictionaryEntry)
        assertEquals("острые", row(id).qualifier)
        assertNull(row(id).freeTextName)
        assertFalse(exists(EditorTags.ingredientWarning(0)))
        assertFalse(exists(IngredientNameTags.offer(0)))

        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.ingredient(0)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { "крылышки, острые" in compose.textsOf(CardTags.ingredient(0)) }
    }

    // FLOW-023 (3, 11), FLOW-057 (7): typed past an entry's label, a text that is exactly another entry's label names
    // that entry — no qualifier — and takes its grid: "chicken" made "chicken wings" holds whole pieces only.
    @Test
    @Config(qualifiers = "en")
    fun anotherEntrysWholeLabelNamesThatEntry() {
        val id = openSeeded(Ingredient(0, 10, null, null, UnitType.PIECE, QuantityUnit.PIECE, null, divisible = true))
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(0)) == "chicken" }
        type(0, "chicken wings")
        quantity(0, "1.5")
        val held = row(id)
        assertEquals(11L, held.dictionaryEntry)
        assertNull(held.qualifier)
        assertEquals(false, held.divisible)
        compose.waitUntil(PATIENCE_MS) { errorText(0) == string(R.string.editor_quantity_accepts_whole_pieces) }
        assertEquals("chicken wings", fieldText(EditorTags.ingredientName(0)))
    }

    // The same in Russian, the label matched as FLOW-057 folds it (`ё` as `е`).
    @Test
    @Config(qualifiers = "ru")
    fun anotherEntrysWholeLabelNamesThatEntryInRussian() {
        val id = openSeeded(Ingredient(0, 10, null, null, UnitType.PIECE, QuantityUnit.PIECE, null, divisible = true))
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(0)) == "курица" }
        type(0, "курица копченая")
        quantity(0, "1,5")
        val held = row(id)
        assertEquals(12L, held.dictionaryEntry)
        assertNull(held.qualifier)
        assertEquals(false, held.divisible)
        compose.waitUntil(PATIENCE_MS) { errorText(0) == string(R.string.editor_quantity_accepts_whole_pieces) }
        // Left, the field reads the entry's own label.
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(0)) == "курица копчёная" }
    }

    // DOM-009: a comma or spaces after the label separate the qualifier and are not kept in it.
    @Test
    @Config(qualifiers = "ru")
    fun separatorsAfterTheLabelAreNotKept() {
        val id = openSeeded(Ingredient(0, 11, null, null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)))
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(0)) == "крылышки" }
        type(0, "крылышки , острые")
        assertEquals(11L to "острые", row(id).let { it.dictionaryEntry to it.qualifier })
        type(0, "крылышки, острые")
        assertEquals(11L to "острые", row(id).let { it.dictionaryEntry to it.qualifier })
        assertEquals("крылышки, острые", fieldText(EditorTags.ingredientName(0)))
        assertFalse(exists(EditorTags.ingredientWarning(0)))
    }

    // DOM-009: a frozen recipe's row reads as the field does, the entry's label then its qualifier.
    @Test
    @Config(qualifiers = "ru")
    fun aFrozenRowShowsTheLabelAndTheQualifier() {
        openSeeded(Ingredient(0, 11, "острые", null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)), state = RecipeState.PUBLISHED)
        compose.waitUntil(PATIENCE_MS) { "крылышки острые" in compose.textsOf(EditorTags.ingredient(0)) }
    }

    // FLOW-023 (4), DOM-009: an edit inside the label makes the row the author's own words, the qualifier dropped.
    @Test
    @Config(qualifiers = "en")
    fun anEditInsideTheLabelMakesFreeText() {
        val id = openSeeded(Ingredient(0, 11, "spicy", null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)))
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(0)) == "chicken wings spicy" }
        type(0, "chicken wing spicy")
        val held = row(id)
        assertNull(held.dictionaryEntry)
        assertNull(held.qualifier)
        assertEquals("chicken wing spicy", held.freeTextName)
        assertEquals("chicken wing spicy", fieldText(EditorTags.ingredientName(0)))
        assertTrue(exists(EditorTags.ingredientWarning(0)))
    }

    // The row's layout, both languages: a qualified piece row at 360 dp and font scale 2.0 keeps every input inside its block.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aQualifiedRowLaysOutAtDoubleFontScale() = qualifiedRowLaysOut("крылышки острые")

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun aQualifiedRowLaysOutAtDoubleFontScaleInEnglish() = qualifiedRowLaysOut("chicken wings острые")

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun qualifiedRowLaysOut(shownName: String) {
        openSeeded(
            Ingredient(0, 11, "острые", null, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(6), divisible = false),
            Ingredient(1, null, null, "яйцо", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2)),
            fontScale = 2f,
        )
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(0)) == shownName }
        assertFalse(exists(EditorTags.divisibility(0)))
        assertTrue(exists(EditorTags.divisibility(1)))
        val block = bounds(EditorTags.ingredient(0))
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(block.right <= screen.right + 0.5f && block.left >= screen.left - 0.5f)
        listOf(EditorTags.ingredientName(0), EditorTags.ingredientQuantity(0), EditorTags.ingredientUnit(0)).forEach { tag ->
            val part = bounds(tag)
            assertTrue("$tag inside its block", part.left >= block.left - 0.5f && part.right <= block.right + 0.5f)
            assertTrue("$tag inside its block", part.top >= block.top - 0.5f && part.bottom <= block.bottom + 0.5f)
            assertTrue("$tag has width", part.width > 0f)
        }
        assertTrue(bounds(EditorTags.ingredientName(0)).bottom <= bounds(EditorTags.ingredientQuantity(0)).top + 0.5f)
    }
}
