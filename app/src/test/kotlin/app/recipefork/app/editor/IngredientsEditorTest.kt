package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.backThroughTheCard
import app.recipefork.app.data.HeldUnitSystemSetting
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.UnitSystem
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
 * The serving count and the ingredient rows of the editor (FLOW-022, FLOW-023, FLOW-024, DOM-015, DOM-016, DOM-017,
 * DOM-019), on a new recipe written through the screen, and on seeded recipes opened directly.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class IngredientsEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-09-29T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)
    private val units = HeldUnitSystemSetting()
    private var shown = false

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
        val environment = EditorEnvironment(active, device.catalog(context), unitSystem = units) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    /** From an empty book: takes the offer to write, and returns the new recipe the editor opened on. */
    private fun writeANewRecipe(): RecipeId {
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.onNodeWithTag(LibraryTags.FROM_SCRATCH).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    /** Opens the editor directly on the recipe [id], as a test fixture: no screen of the application routes there. */
    private fun openDirectly(id: RecipeId) {
        show()
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Where the node tagged [tag] is laid out in the scrolling form, whether or not it is scrolled into view. */
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    /** What the field tagged [tag] holds. */
    private fun fieldText(tag: String): String = node(tag).config[SemanticsProperties.EditableText].text

    private fun isMarkedAsError(tag: String): Boolean = SemanticsProperties.Error in node(tag).config

    private fun servingsAccepts(): String =
        compose.onNodeWithTag(EditorTags.SERVINGS_ACCEPTS, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Adds a row by "Add ingredient", which opens its sheet: any sheet open before is closed first. */
    private fun addIngredient(): Int {
        compose.closeIngredient()
        val position = compose.onAllNodes(hasTestTagStartingWith("editor-ingredient-") and hasTestTagEndingWith("-line"))
            .fetchSemanticsNodes().size
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(position)), PATIENCE_MS)
        return position
    }

    private fun typeQuantity(position: Int, text: String) {
        compose.openIngredient(position)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(position)).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    /** Types [text] into the quantity field and leaves the field for the note, above it in the row's sheet. */
    private fun enterQuantity(position: Int, text: String) {
        typeQuantity(position, text)
        leaveQuantity(position)
    }

    /**
     * Leaves the quantity field of the open sheet for its note. (Its keyboard action, Next, opens the unit's menu: the
     * keyboard test follows that.)
     */
    private fun leaveQuantity(position: Int) {
        compose.onNodeWithTag(IngredientNoteTags.field(position)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }

    private fun openUnits(position: Int) {
        compose.openIngredient(position)
        compose.onNodeWithTag(EditorTags.ingredientUnit(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(QuantityUnit.PIECE)), PATIENCE_MS)
    }

    private fun chooseUnit(position: Int, unit: QuantityUnit) {
        openUnits(position)
        compose.onNodeWithTag(EditorTags.unitOption(unit)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(QuantityUnit.PIECE)), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun unitShown(position: Int): String {
        compose.openIngredient(position)
        return compose.textsOf(EditorTags.ingredientUnit(position)).last()
    }

    /** The units the open menu offers, by their tags in the order listed, each with its name as shown. */
    private fun offeredUnits(): Map<String, String> = compose.onAllNodes(hasTestTagStartingWith("editor-unit-")).fetchSemanticsNodes()
        .associate { node -> node.config[SemanticsProperties.TestTag] to node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }

    private fun hasTestTagEndingWith(suffix: String) = SemanticsMatcher("tag ends with $suffix") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.endsWith(suffix) == true
    }

    private fun hasSetTextAction() = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    // FLOW-024 (1): after the tags, the serving count, then the ingredients.
    @Test
    fun theServingCountAndThenTheIngredientsFollowTheTags() {
        writeANewRecipe()
        val tags = bounds(EditorTags.TAGS)
        val servings = bounds(EditorTags.SERVINGS)
        val ingredients = bounds(EditorTags.INGREDIENTS)
        assertTrue("the serving count is not after the tags", servings.top >= tags.bottom)
        assertTrue("the ingredients are not after the serving count", ingredients.top >= servings.bottom)
        // A row stays inside the ingredient list.
        addIngredient()
        assertTrue(bounds(EditorTags.ingredient(0)).top >= bounds(EditorTags.SERVINGS).bottom)
        assertTrue(bounds(EditorTags.INGREDIENT_ADD).top >= bounds(EditorTags.ingredient(0)).bottom)
    }

    // DOM-015 (1, 2, 3): a whole number of one or more, or nothing; anything else is not held, and the field says
    // what it accepts.
    @Test
    fun theServingCountHoldsAWholeNumberOfOneOrMoreOrNothing() {
        val id = writeANewRecipe()
        assertNull(stored(id).servings)
        assertEquals(string(R.string.editor_servings_accepts), servingsAccepts())
        assertFalse(isMarkedAsError(EditorTags.SERVINGS))

        listOf("1" to 1, "4" to 4, "1000" to 1000).forEach { (typed, count) ->
            compose.onNodeWithTag(EditorTags.SERVINGS).performTextReplacement(typed)
            compose.waitForIdle()
            assertEquals(typed, count, stored(id).servings)
            assertEquals(typed, fieldText(EditorTags.SERVINGS))
            assertFalse(typed, isMarkedAsError(EditorTags.SERVINGS))
        }
        listOf("0", "-1", "2.5", "2,5", "½", "four").forEach { typed ->
            compose.onNodeWithTag(EditorTags.SERVINGS).performTextReplacement(typed)
            compose.waitForIdle()
            // Not held: the field and the book keep the count they held, and the field is marked.
            assertEquals(typed, 1000, stored(id).servings)
            assertEquals(typed, "1000", fieldText(EditorTags.SERVINGS))
            assertTrue(typed, isMarkedAsError(EditorTags.SERVINGS))
            assertEquals(string(R.string.editor_servings_accepts), servingsAccepts())
        }
        // Left empty: no value, and no zero stands in for it.
        compose.onNodeWithTag(EditorTags.SERVINGS).performTextReplacement("")
        compose.waitForIdle()
        assertNull(stored(id).servings)
        assertEquals("", fieldText(EditorTags.SERVINGS))
        assertFalse(isMarkedAsError(EditorTags.SERVINGS))
    }

    // FLOW-023 (1, 7): a row's sheet holds exactly three fields and the note, and no unit type is ever shown. The row
    // itself holds no field and no action: its line opens the sheet, which holds the row's actions.
    @Test
    fun addIngredientAppendsARowOfExactlyThreeFieldsAndNoUnitType() {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.ingredient(0)).assertDoesNotExist()
        addIngredient()
        assertEquals(1, stored(id).ingredients.size)
        val inSheet = hasAnyAncestor(hasTestTag(IngredientSheetTags.SHEET))
        // Two fields take text — the ingredient and the quantity — and the third is the unit, chosen from a list; after
        // them, the optional note on how the ingredient is prepared (DOM-023; T-218: the amount before the note).
        val textFields = compose.onAllNodes(inSheet and hasSetTextAction()).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }
        assertEquals(listOf(EditorTags.ingredientName(0), EditorTags.ingredientQuantity(0), IngredientNoteTags.field(0)), textFields)
        // Every control of the sheet, each tagged but Material's drag handle (T-272), in order: the handle, Done, then the
        // fields; a row just added offers no actions while it holds nothing (T-218).
        fun controls() = compose.onAllNodes(inSheet and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .map(::controlName)
        val fields = SHEET_HANDLE + listOf(
            IngredientSheetTags.DONE, EditorTags.ingredientName(0), EditorTags.ingredientQuantity(0), EditorTags.ingredientUnit(0),
            IngredientNoteTags.field(0),
        )
        assertEquals(fields, controls())
        // Once it holds something, the row's actions follow the fields; the only row moves nowhere.
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performTextInput("sifted")
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(EditorTags.ingredientRemove(0))).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(
            fields + listOf(
                EditorTags.ingredientAction(0, RowAction.INSERT_ABOVE),
                EditorTags.ingredientAction(0, RowAction.INSERT_BELOW), EditorTags.ingredientAction(0, RowAction.GROUP_ABOVE),
                EditorTags.ingredientRemove(0),
            ),
            controls(),
        )
        val inRow = hasAnyAncestor(hasTestTag(EditorTags.ingredient(0)))
        assertEquals(0, compose.onAllNodes(inRow and hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals(
            listOf(IngredientSheetTags.line(0)),
            compose.onAllNodes(inRow and AccessibilityChecks.isInteractive).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] },
        )
        assertEquals(string(R.string.editor_ingredient_unit), compose.textsOf(EditorTags.ingredientUnit(0)).first())

        // Whatever unit is chosen — in its own system — no unit type is named anywhere on the screen.
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextInput("wild garlic")
        QuantityUnit.entries.forEach { unit ->
            units.set(unit.system ?: UnitSystem.METRIC)
            chooseUnit(0, unit)
            assertNoUnitTypeShown()
            assertEquals("$unit", unit.type, stored(id).ingredients.single().unitType)
        }
        openUnits(0)
        assertNoUnitTypeShown()
    }

    private fun assertNoUnitTypeShown() {
        val shown = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().map { it.text } +
                    node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty()
            }
            .map { it.lowercase() }
        val typeNames = UnitType.entries.flatMap { listOf(it.name.lowercase(), it.name.lowercase().replace('_', ' ')) } +
            listOf("unit type", "тип единицы")
        val named = shown.filter { text -> typeNames.any { name -> text == name || "unit type" in text || "тип единиц" in text } }
        assertEquals("a unit type is shown", emptyList<String>(), named)
    }

    // DOM-016 (1), FLOW-023 (10): exactly the twelve units and the three US ones (v0.35), named in the interface
    // language; DOM-016 (2). Each is offered in its own system, the shared ones in both (v0.35 T-249).
    @Test
    fun theUnitFieldOffersExactlyTheTwelveUnitsInEnglish() = theTwelveUnits(
        listOf("g", "kg", "t", "ml", "l", "m³", "oz", "lb", "fl oz", "pc", "tsp", "tbsp", "cup", "pinch", "to taste"),
    )

    @Test
    @Config(qualifiers = "ru")
    fun theUnitFieldOffersExactlyTheTwelveUnitsInRussian() = theTwelveUnits(
        listOf("г", "кг", "т", "мл", "л", "м³", "унц.", "фн", "жидк. унц.", "шт", "ч. л.", "ст. л.", "стакан", "щепотка", "по вкусу"),
    )

    private fun theTwelveUnits(names: List<String>) {
        val id = writeANewRecipe()
        addIngredient()
        openUnits(0)
        val metric = offeredUnits()
        // Listed with the kitchen's usual units first (T-218); each still named as DOM-016 names it.
        assertEquals(menuUnits(UnitSystem.METRIC, null).map { EditorTags.unitOption(it) }, metric.keys.toList())
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.GRAM)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(QuantityUnit.GRAM)), PATIENCE_MS)
        // The US system's menu, the row now holding a unit of mass of its own system.
        units.set(UnitSystem.US)
        chooseUnit(0, QuantityUnit.OUNCE)
        openUnits(0)
        val us = offeredUnits()
        assertEquals(menuUnits(UnitSystem.US, null).map { EditorTags.unitOption(it) }, us.keys.toList())
        assertEquals(names, QuantityUnit.entries.map { (metric + us)[EditorTags.unitOption(it)] })
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.OUNCE)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(QuantityUnit.OUNCE)), PATIENCE_MS)
        // Each unit chosen — in its own system — stores its own unit and the unit type the table gives it.
        QuantityUnit.entries.zip(names).forEach { (unit, name) ->
            units.set(unit.system ?: UnitSystem.METRIC)
            chooseUnit(0, unit)
            val row = stored(id).ingredients.single()
            assertEquals("$unit", unit.type, row.unitType)
            assertEquals("$unit", unit.takeIf { it != QuantityUnit.TO_TASTE }, row.unit)
            assertEquals("$unit", name, unitShown(0))
        }
        assertEquals(UnitType.HOUSEHOLD, QuantityUnit.CUP.type)
        assertEquals(UnitType.TASTE_MEASURED, QuantityUnit.PINCH.type)
        assertEquals(UnitType.PIECE, QuantityUnit.PIECE.type)
    }

    // FLOW-023 (8, 9), DOM-002 (1).
    @Test
    fun toTasteWithdrawsTheQuantityAndAnotherUnitBringsItBackEmpty() {
        val id = writeANewRecipe()
        addIngredient()
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextInput("salt")
        enterQuantity(0, "5")
        assertEquals(Rational.of(5), stored(id).ingredients.single().quantity)

        chooseUnit(0, QuantityUnit.TO_TASTE)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.ingredientUnit(0)).assertExists()
        assertEquals("to taste", unitShown(0))
        val toTaste = stored(id).ingredients.single()
        assertEquals(UnitType.TASTE_UNMEASURED, toTaste.unitType)
        assertNull(toTaste.quantity)
        assertNull(toTaste.enteredQuantity)
        assertNull(toTaste.unit)

        chooseUnit(0, QuantityUnit.PINCH)
        assertEquals("", fieldText(EditorTags.ingredientQuantity(0)))
        assertEquals("pinch", unitShown(0))
        val back = stored(id).ingredients.single()
        assertEquals(UnitType.TASTE_MEASURED, back.unitType)
        assertNull(back.quantity)
        assertEquals("salt", back.freeTextName)
    }

    // FLOW-022 (2): the name typed as free text shows the warning, which carries both statements, once the field is
    // left — not while it is typed — as a hint in the field's supporting text, not as an error.
    @Test
    fun aNameInTheAuthorsOwnWordsShowsTheFreeTextWarning() = freeTextWarning()

    @Test
    @Config(qualifiers = "ru")
    fun aNameInTheAuthorsOwnWordsShowsTheFreeTextWarningInRussian() = freeTextWarning()

    private fun freeTextWarning() {
        val id = writeANewRecipe()
        addIngredient()
        compose.onNodeWithTag(EditorTags.ingredientWarning(0), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextInput("wild garlic")
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTags.ingredientWarning(0), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientWarning(0)) }
        val warning = compose.textsOf(EditorTags.ingredientWarning(0)).single()
        assertFalse(compose.onNodeWithTag(EditorTags.ingredientName(0)).fetchSemanticsNode().config.contains(SemanticsProperties.Error))
        assertEquals(string(R.string.editor_ingredient_free_text_warning), warning)
        val english = context.resources.configuration.locales[0].language == "en"
        // Filters will not find the recipe by it, and publication makes it permanent.
        if (english) {
            assertTrue(warning, "filters will not find this recipe by this ingredient" in warning)
            assertTrue(warning, "once the recipe is published this cannot be changed" in warning)
        } else {
            assertTrue(warning, "фильтры не найдут рецепт по нему" in warning)
            assertTrue(warning, "после публикации это уже нельзя будет изменить" in warning)
        }
        val row = stored(id).ingredients.single()
        assertEquals("wild garlic", row.freeTextName)
        assertNull(row.dictionaryEntry)
        // Cleared, the row names nothing and warns of nothing.
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextReplacement("")
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTags.ingredientWarning(0), useUnmergedTree = true).assertDoesNotExist()
        assertNull(stored(id).ingredients.single().freeTextName)
    }

    // FLOW-022 (5): a piece row in the author's own words asks for its divisibility.
    @Test
    fun aPieceRowAsksForItsDivisibility() {
        val id = writeANewRecipe()
        addIngredient()
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextInput("eggs")
        compose.onNodeWithTag(EditorTags.divisibility(0)).assertDoesNotExist()
        chooseUnit(0, QuantityUnit.PIECE)
        val question = compose.onNodeWithTag(EditorTags.divisibility(0)).performScrollTo()
        question.assertExists()
        compose.onNode(hasText(string(R.string.editor_ingredient_divisible_question)) and hasAnyAncestor(hasTestTag(EditorTags.divisibility(0))))
            .assertExists()
        // Not answered for the author.
        listOf(true, false).forEach { assertEquals(false, node(EditorTags.divisible(0, it)).config[SemanticsProperties.Selected]) }
        assertNull(stored(id).ingredients.single().divisible)

        compose.onNodeWithTag(EditorTags.divisible(0, false)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, stored(id).ingredients.single().divisible)
        assertEquals(true, node(EditorTags.divisible(0, false)).config[SemanticsProperties.Selected])
        compose.onNodeWithTag(EditorTags.divisible(0, true)).performClick()
        compose.waitForIdle()
        assertEquals(true, stored(id).ingredients.single().divisible)

        // Another unit asks nothing, and divisibility belongs to a piece only.
        chooseUnit(0, QuantityUnit.GRAM)
        compose.onNodeWithTag(EditorTags.divisibility(0)).assertDoesNotExist()
        assertNull(stored(id).ingredients.single().divisible)
    }

    // DOM-019 (1): in Russian, a comma or a point; the field then displays the Russian form.
    @Test
    @Config(qualifiers = "ru")
    fun inRussianACommaOrAPointIsTheDecimalMark() {
        val id = writeANewRecipe()
        addIngredient()
        chooseUnit(0, QuantityUnit.KILOGRAM)
        listOf("1,5", "1.5").forEach { typed ->
            enterQuantity(0, typed)
            assertEquals(typed, Rational.of(3, 2), stored(id).ingredients.single().quantity)
            assertEquals(typed, "1,5", fieldText(EditorTags.ingredientQuantity(0)))
            compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).assertDoesNotExist()
        }
    }

    // DOM-019 (2, 3, 4, 5) in English.
    @Test
    fun inEnglishACommaOrAPointIsTheDecimalMarkAndSeparatorsAreRefused() {
        val id = writeANewRecipe()
        addIngredient()
        chooseUnit(0, QuantityUnit.KILOGRAM)
        listOf("1.5", "1,5", "1,500").forEach { typed ->
            enterQuantity(0, typed)
            assertEquals(typed, Rational.of(3, 2), stored(id).ingredients.single().quantity)
            assertEquals(typed, "1.5", fieldText(EditorTags.ingredientQuantity(0)))
            compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).assertDoesNotExist()
        }
        // Read, and not held: no whole number of grams.
        listOf("1,2345", "1.2345").forEach { typed ->
            enterQuantity(0, typed)
            assertEquals(typed, Rational.of(12345, 10000), stored(id).ingredients.single().quantity)
            assertEquals(typed, string(R.string.editor_quantity_accepts_grams), compose.textsOf(EditorTags.ingredientError(0)).single())
        }
        // Separators: not held, kept in the field, and the field says a number is written without them.
        chooseUnit(0, QuantityUnit.GRAM)
        listOf("1 500", "1.500,5", "1,5,0").forEach { typed ->
            enterQuantity(0, typed)
            assertEquals(typed, typed, fieldText(EditorTags.ingredientQuantity(0)))
            assertNull(typed, stored(id).ingredients.single().quantity)
            assertEquals(typed, typed, stored(id).ingredients.single().enteredQuantity)
            assertTrue(typed, isMarkedAsError(EditorTags.ingredientQuantity(0)))
            assertEquals(typed, string(R.string.editor_quantity_separators), compose.textsOf(EditorTags.ingredientError(0)).single())
        }
    }

    // A field's error is said in its own supporting text, the field marked as an error — not drawn beside it.
    @Test
    fun aQuantityErrorIsInTheFieldsSupportingText() {
        writeANewRecipe()
        addIngredient()
        chooseUnit(0, QuantityUnit.GRAM)
        enterQuantity(0, "1 500")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientError(0)) }
        assertTrue(isMarkedAsError(EditorTags.ingredientQuantity(0)))
        // Inside the quantity field, under its text, and read with it.
        val error = compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).fetchSemanticsNode()
        val field = node(EditorTags.ingredientQuantity(0))
        val e = Rect(error.positionInRoot, error.size.toSize())
        val f = Rect(field.positionInRoot, field.size.toSize())
        assertTrue("the error is not inside its field ($e in $f)", e.left >= f.left && e.right <= f.right + 0.5f && e.bottom <= f.bottom + 0.5f)
        val read = compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertTrue(read.toString(), string(R.string.editor_quantity_separators) in read)
        // Corrected, the error and the mark go.
        enterQuantity(0, "1500")
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.ingredientError(0)) }
        assertFalse(isMarkedAsError(EditorTags.ingredientQuantity(0)))
    }

    // DOM-017 (1–3, 6) through the field: held values are written as the grid writes them; others stay and are
    // marked with the values the field accepts.
    @Test
    @Config(qualifiers = "ru")
    fun theQuantityFieldHoldsOnlyValuesOfItsGrid() {
        val id = writeANewRecipe()
        addIngredient()
        chooseUnit(0, QuantityUnit.TEASPOON)
        // 0,5 ч. л. is ½, 0,25 ч. л. is ¼, and 1/3 is ⅓.
        listOf("0,5" to "½", "0,25" to "¼", "1/3" to "⅓", "1⅓" to "1⅓").forEach { (typed, written) ->
            typeQuantity(0, typed)
            // While typing, the field keeps what was typed.
            assertEquals(typed, fieldText(EditorTags.ingredientQuantity(0)))
            leaveQuantity(0)
            assertEquals(typed, written, fieldText(EditorTags.ingredientQuantity(0)))
            compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).assertDoesNotExist()
            assertFalse(isMarkedAsError(EditorTags.ingredientQuantity(0)))
        }
        // 0,33 and 0,3 ч. л. are not held: the value stays, the row is marked naming the fractions accepted.
        listOf("0,33", "0,3", "0", "-1").forEach { typed ->
            enterQuantity(0, typed)
            assertEquals(typed, typed, fieldText(EditorTags.ingredientQuantity(0)))
            assertTrue(typed, isMarkedAsError(EditorTags.ingredientQuantity(0)))
            assertEquals(typed, string(R.string.editor_quantity_accepts_fractions), compose.textsOf(EditorTags.ingredientError(0)).single())
        }
        // 500 г and 7,5 г.
        chooseUnit(0, QuantityUnit.GRAM)
        enterQuantity(0, "500")
        compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).assertDoesNotExist()
        assertEquals(Rational.of(500), stored(id).ingredients.single().quantity)
        enterQuantity(0, "7,5")
        assertEquals("7,5", fieldText(EditorTags.ingredientQuantity(0)))
        assertEquals(Rational.of(15, 2), stored(id).ingredients.single().quantity)
        assertEquals(string(R.string.editor_quantity_accepts_grams), compose.textsOf(EditorTags.ingredientError(0)).single())
    }

    // DOM-017 (1–4) for a piece, divisible and indivisible.
    @Test
    @Config(qualifiers = "ru")
    fun aPieceHoldsHalvesOnlyWhenDivisible() {
        val id = writeANewRecipe()
        addIngredient()
        chooseUnit(0, QuantityUnit.PIECE)
        compose.onNodeWithTag(EditorTags.divisible(0, true)).performScrollTo().performClick()
        listOf("1½" to "1½", "2,5" to "2½", "½" to "½", "3" to "3").forEach { (typed, written) ->
            enterQuantity(0, typed)
            assertEquals(typed, written, fieldText(EditorTags.ingredientQuantity(0)))
            compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).assertDoesNotExist()
        }
        compose.onNodeWithTag(EditorTags.divisible(0, false)).performScrollTo().performClick()
        listOf("3", "1").forEach { typed ->
            enterQuantity(0, typed)
            compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).assertDoesNotExist()
        }
        listOf("2,5", "½").forEach { typed ->
            enterQuantity(0, typed)
            assertEquals(typed, typed, fieldText(EditorTags.ingredientQuantity(0)))
            assertEquals(typed, string(R.string.editor_quantity_accepts_whole_pieces), compose.textsOf(EditorTags.ingredientError(0)).single())
        }
        assertEquals(Rational.of(1, 2), stored(id).ingredients.single().quantity)
    }

    // DOM-017 (6): a unit or divisibility change that takes the value off its grid keeps it and marks the row.
    @Test
    @Config(qualifiers = "ru")
    fun changingTheUnitOrTheDivisibilityOffTheGridKeepsTheValueAndMarksTheRow() {
        val id = writeANewRecipe()
        addIngredient()
        chooseUnit(0, QuantityUnit.TABLESPOON)
        enterQuantity(0, "½")
        compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).assertDoesNotExist()
        chooseUnit(0, QuantityUnit.GRAM)
        assertEquals("½", fieldText(EditorTags.ingredientQuantity(0)))
        assertEquals(Rational.of(1, 2), stored(id).ingredients.single().quantity)
        assertTrue(isMarkedAsError(EditorTags.ingredientQuantity(0)))
        assertEquals(string(R.string.editor_quantity_accepts_grams), compose.textsOf(EditorTags.ingredientError(0)).single())
        // A unit whose grid holds it clears the mark.
        chooseUnit(0, QuantityUnit.CUP)
        compose.onNodeWithTag(EditorTags.ingredientError(0), useUnmergedTree = true).assertDoesNotExist()

        addIngredient()
        chooseUnit(1, QuantityUnit.PIECE)
        compose.onNodeWithTag(EditorTags.divisible(1, true)).performScrollTo().performClick()
        enterQuantity(1, "1½")
        compose.onNodeWithTag(EditorTags.ingredientError(1), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.divisible(1, false)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("1½", fieldText(EditorTags.ingredientQuantity(1)))
        assertEquals(Rational.of(3, 2), stored(id).ingredients[1].quantity)
        assertTrue(isMarkedAsError(EditorTags.ingredientQuantity(1)))
        assertEquals(string(R.string.editor_quantity_accepts_whole_pieces), compose.textsOf(EditorTags.ingredientError(1)).single())
    }

    // A row can be deleted; the rows after it move up and keep what they hold.
    @Test
    fun aRowCanBeDeleted() {
        val id = writeANewRecipe()
        listOf("salt" to "5", "flour" to "500", "water" to "300").forEachIndexed { position, (name, quantity) ->
            addIngredient()
            compose.onNodeWithTag(EditorTags.ingredientName(position)).performTextInput(name)
            typeQuantity(position, quantity)
        }
        compose.closeIngredient()
        // The removal is the last action of the row's sheet, and says what it removes.
        compose.openIngredient(0)
        assertEquals(listOf(string(R.string.editor_ingredient_remove)), compose.textsOf(EditorTags.ingredientRemove(0)))
        compose.removeIngredient(0)
        val rows = stored(id).ingredients
        assertEquals(listOf("flour", "water"), rows.map { it.freeTextName })
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf(Rational.of(500), Rational.of(300)), rows.map { it.quantity })
        assertEquals(listOf("500 g flour", "300 g water"), (0..1).map { compose.ingredientLine(it) })
        compose.openIngredient(0)
        assertEquals("flour", fieldText(EditorTags.ingredientName(0)))
        assertEquals("500", fieldText(EditorTags.ingredientQuantity(0)))
        compose.openIngredient(1)
        assertEquals("300", fieldText(EditorTags.ingredientQuantity(1)))
        compose.closeIngredient()
        compose.onNodeWithTag(EditorTags.ingredient(2)).assertDoesNotExist()
        compose.removeIngredient(1)
        compose.removeIngredient(0)
        assertEquals(emptyList<Ingredient>(), stored(id).ingredients)
        compose.onNodeWithTag(EditorTags.ingredient(0)).assertDoesNotExist()
    }

    // FLOW-024 (6): everything entered, incomplete and off-grid rows included, is there after leaving the editor.
    @Test
    @Config(qualifiers = "ru")
    fun everythingEnteredIsThereAfterLeavingTheEditor() {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.SERVINGS).performTextInput("4")
        // A complete row, a row with no quantity, one off its grid, one that reads as no number, one to taste and a
        // piece whose divisibility is not given; and a row with nothing in it — one no longer new, which closing its
        // sheet does not take away.
        val rows = listOf(
            Triple("мука", QuantityUnit.KILOGRAM, "0.5"),
            Triple("сахар", QuantityUnit.GRAM, ""),
            Triple("дрожжи", QuantityUnit.GRAM, "7,5"),
            Triple("вода", QuantityUnit.MILLILITRE, "1 500"),
            Triple("соль", QuantityUnit.TO_TASTE, ""),
            Triple("яйца", QuantityUnit.PIECE, "2"),
            Triple("", QuantityUnit.GRAM, ""),
        )
        rows.forEachIndexed { position, (name, unit, quantity) ->
            addIngredient()
            if (name.isNotEmpty()) compose.onNodeWithTag(EditorTags.ingredientName(position)).performTextInput(name)
            if (unit != QuantityUnit.GRAM) chooseUnit(position, unit)
            if (quantity.isNotEmpty()) typeQuantity(position, quantity)
        }
        // Named, closed, and emptied again once no longer new: closing its sheet then leaves it.
        compose.onNodeWithTag(EditorTags.ingredientName(rows.lastIndex)).performTextInput("лук")
        compose.closeIngredient()
        compose.openIngredient(rows.lastIndex)
        compose.onNodeWithTag(EditorTags.ingredientName(rows.lastIndex)).performTextReplacement("")
        compose.closeIngredient()
        compose.waitUntil(PATIENCE_MS) { stored(id).ingredients.size == rows.size && stored(id).ingredients.last().freeTextName == null }
        val before = stored(id)
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)

        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(rows.lastIndex)), PATIENCE_MS)
        assertEquals("4", fieldText(EditorTags.SERVINGS))
        val shownQuantities = listOf("0,5", "", "7,5", "1 500", null, "2", "")
        // Each row's line says what it holds, the rows whose quantity its field does not hold marked.
        assertEquals(
            listOf("500 г мука", "сахар", "7,5 г дрожжи", "1 500 мл вода", "соль — по вкусу", "2 шт яйца", "Новый ингредиент"),
            rows.indices.map { compose.ingredientLine(it) },
        )
        assertEquals(listOf(2, 3), rows.indices.filter { exists(IngredientSheetTags.problem(it)) })
        rows.forEachIndexed { position, (name, unit, _) ->
            compose.openIngredient(position)
            assertEquals("$position", name, fieldText(EditorTags.ingredientName(position)))
            assertEquals("$position", context.getString(unitName(unit)), unitShown(position))
            val quantity = shownQuantities[position]
            if (quantity == null) compose.onNodeWithTag(EditorTags.ingredientQuantity(position)).assertDoesNotExist()
            else assertEquals("$position", quantity, fieldText(EditorTags.ingredientQuantity(position)))
            if (position in listOf(2, 3)) assertTrue("$position", exists(EditorTags.ingredientError(position)))
            assertEquals("$position", position == 5, exists(EditorTags.divisibility(position)))
        }
        compose.closeIngredient()
        val after = stored(id)
        assertEquals(before, after)
        assertEquals(4, after.servings)
        assertEquals(
            listOf(Rational.of(1, 2), null, Rational.of(15, 2), null, null, Rational.of(2), null),
            after.ingredients.map { it.quantity },
        )
        assertEquals("1 500", after.ingredients[3].enteredQuantity)
        assertEquals(listOf(true, false, false, false, true, false, false), after.ingredients.map { it.isComplete })
    }

    // A frozen recipe shows the serving count and the rows, and nothing changes them.
    @Test
    @Config(qualifiers = "ru")
    fun aFrozenRecipeShowsTheServingCountAndTheRowsReadOnly() {
        listOf(RecipeState.PENDING, RecipeState.LINK_SHARED, RecipeState.PUBLISHED, RecipeState.REPORTED_HIDDEN, RecipeState.REMOVED)
            .forEach { state ->
                val recipe = Recipe.create(created, null).copy(
                    title = "Блины",
                    servings = 4,
                    ingredients = listOf(
                        Ingredient(0, null, null, "мука", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)),
                        Ingredient(1, null, null, "сахар", UnitType.HOUSEHOLD, QuantityUnit.TABLESPOON, Rational.of(1, 2)),
                        Ingredient(2, null, null, "соль", UnitType.TASTE_UNMEASURED, null, null),
                        Ingredient(3, null, null, "яйца", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2), divisible = false),
                    ),
                    sent = SentStatus.SENT,
                ).inState(state)
                runBlocking { recipes.save(recipe) }
                openDirectly(recipe.id)
                compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(3)), PATIENCE_MS)
                assertEquals("$state", listOf("Порций", "4"), compose.textsOf(EditorTags.SERVINGS))
                // Each row reads as its line, as on the card.
                assertEquals("$state", listOf("500 г мука"), compose.textsOf(EditorTags.ingredient(0)))
                assertEquals("$state", listOf("½ ст. л. сахар"), compose.textsOf(EditorTags.ingredient(1)))
                assertEquals("$state", listOf("соль — по вкусу"), compose.textsOf(EditorTags.ingredient(2)))
                assertEquals("$state", listOf("2 шт яйца"), compose.textsOf(EditorTags.ingredient(3)))
                assertEquals("$state", 0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
                val actions = compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
                assertEquals("$state", listOf(EditorTags.BACK, EditorTags.FORK_NOTE, ChecklistTags.CONTROL), actions.map { it.config[SemanticsProperties.TestTag] })
                compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).assertDoesNotExist()
                compose.onNodeWithTag(EditorTags.ingredientRemove(0)).assertDoesNotExist()
                compose.onNodeWithTag(EditorTags.divisibility(3)).assertDoesNotExist()
                assertEquals("$state", recipe, stored(recipe.id))
                compose.onNodeWithTag(EditorTags.BACK).performClick()
                compose.waitForIdle()
            }
    }

    // A frozen recipe with no serving count says so.
    @Test
    fun aFrozenRecipeWithNoServingCountSaysItIsNotGiven() {
        val recipe = Recipe.create(created, null).copy(title = "Plum cake").inState(RecipeState.PUBLISHED)
        runBlocking { recipes.save(recipe) }
        openDirectly(recipe.id)
        assertEquals(listOf("Servings", "Not given"), compose.textsOf(EditorTags.SERVINGS))
    }

    // FLOW-002 (4) at the editor's boundary: no change to the new parts reaches a frozen recipe.
    @Test
    fun theEditorRefusesEveryChangeOfTheNewPartsToAFrozenRecipe() {
        val recipe = Recipe.create(created, null).copy(
            servings = 2,
            ingredients = listOf(Ingredient(0, null, null, "salt", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(5))),
        ).inState(RecipeState.PUBLISHED)
        runBlocking { recipes.save(recipe) }
        val editor = RecipeEditor(recipes, recipe.id, { created }, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        runBlocking { editor.load() }
        assertFalse(editor.setServings("3"))
        assertFalse(editor.addIngredient())
        assertFalse(editor.setIngredientName(0, "sugar"))
        assertFalse(editor.setIngredientQuantity(0, "6"))
        assertFalse(editor.setIngredientUnit(0, QuantityUnit.TO_TASTE))
        assertFalse(editor.setIngredientDivisible(0, true))
        assertFalse(editor.removeIngredient(0))
        assertEquals(recipe, editor.recipe.value)
        assertEquals(recipe, stored(recipe.id))
    }
}
