package app.recipefork.app.editor

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
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
import app.recipefork.app.media.SourceSheetTags
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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

/**
 * The editor's sheets, alike and quicker (T-218): an ingredient is written name → amount → unit → note, a dictionary
 * offer picked going on to the amount with the keyboard up, the kitchen's usual units first in the unit menu; a row or
 * a step just added is titled as new and offers its actions once it holds something; the three editor sheets share
 * their title's style, their Done and their removals' names, and the photo source sheets their wording and icons; a
 * step's "Insert ingredient" is a labelled button, and a new step's buttons stand within reach of the keyboard; a row
 * "to taste" never shows its quantity, even stacked.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditorSheetsAlikeTest {
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
        runBlocking { device.database.dictionaryDao().replace(TEST_INGREDIENTS) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    /** Milk from the dictionary under a "Dough" heading, salt to taste, and — where [steps] — one step written. */
    private fun draft(steps: Boolean = true) = Recipe.create(created, owner = null).copy(
        title = "Soup",
        language = RecipeLanguage.EN,
        servings = 2,
        ingredients = listOf(
            Ingredient(0, 1, null, null, UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(500)),
            Ingredient(1, null, null, "salt", UnitType.TASTE_UNMEASURED, null, null),
        ),
        ingredientGroups = listOf(IngredientGroup("Dough", 0)),
        steps = if (steps) listOf(Step(0, "Boil.")) else emptyList(),
    )

    private fun open(recipe: Recipe = draft()): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme(lightColorScheme()) { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(recipe.ingredients.lastIndex)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { !compose.ingredientLine(0).contains(string(R.string.editor_ingredient_unnamed)) }
        return recipe.id
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun isFocused(tag: String): Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }

    private fun awaitFocused(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) && isFocused(tag) }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun dp(px: Float): Float = px / compose.density.density

    private fun action(info: EditorInfo): Int = info.imeOptions and EditorInfo.IME_MASK_ACTION

    /** The text the node tagged [tag] reads. */
    private fun text(tag: String): String = compose.textsOf(tag).joinToString("")

    /** The size the text tagged [tag] is drawn at. */
    private fun fontSize(tag: String): TextUnit = layout(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config)

    private fun layout(config: androidx.compose.ui.semantics.SemanticsConfiguration): TextUnit {
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single().layoutInput.style.fontSize
    }

    /** Adds a row by "Add ingredient", which opens its sheet with its name focused; its position is given back. */
    private fun addIngredient(id: RecipeId): Int {
        val position = stored(id).ingredients.size
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        awaitFocused(EditorTags.ingredientName(position))
        return position
    }

    /** The actions a row's sheet shows, by their tags. */
    private fun rowActions(position: Int): List<String> =
        (RowAction.entries.map { EditorTags.ingredientAction(position, it) } + EditorTags.ingredientRemove(position)).filter(::exists)

    private fun stepActions(position: Int): List<String> =
        (RowAction.entries.map { EditorTags.stepAction(position, it) } + EditorTags.stepRemove(position)).filter(::exists)

    // Picking a dictionary offer is the usual way to name a row: it goes on to the amount, as Next does, the keyboard
    // up and asking for a number. Then Next walks amount → the unit's menu, and a unit picked there goes on to the note
    // with the keyboard up; the note, the last field, says Done, which closes the sheet. The sheet stands in that order.
    @Test
    fun anOfferPickedGoesOnToTheAmountThenTheUnitThenTheNote() {
        val id = open()
        val row = addIngredient(id)
        compose.onNodeWithTag(EditorTags.ingredientName(row)).performTextInput("turm")
        compose.waitUntil(PATIENCE_MS) { exists(IngredientNameTags.entry(row, 20)) }
        compose.onNodeWithTag(IngredientNameTags.entry(row, 20)).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocused(EditorTags.ingredientQuantity(row))
        compose.waitUntil(PATIENCE_MS) { compose.sheetTakesInput() }
        val amount = compose.keyboardAskedFor()
        assertEquals(InputType.TYPE_CLASS_NUMBER, amount.inputType and InputType.TYPE_MASK_CLASS)
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(amount))
        assertEquals(20L, stored(id).ingredients[row].dictionaryEntry)

        // Order on screen: name, then quantity and unit, then the note.
        assertTrue(bounds(EditorTags.ingredientName(row)).bottom <= bounds(EditorTags.ingredientQuantity(row)).top)
        assertTrue(bounds(EditorTags.ingredientUnit(row)).bottom <= bounds(IngredientNoteTags.field(row)).top)

        compose.onNodeWithTag(EditorTags.ingredientQuantity(row)).performTextInput("2")
        compose.onNodeWithTag(EditorTags.ingredientQuantity(row)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.TEASPOON)) }
        assertFalse(isFocused(EditorTags.ingredientQuantity(row)))
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.TEASPOON)).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocused(IngredientNoteTags.field(row))
        compose.waitUntil(PATIENCE_MS) { compose.sheetTakesInput() }
        assertTrue(compose.ingredientSheetOpen())
        assertEquals(EditorInfo.IME_ACTION_DONE, action(compose.keyboardAskedFor()))
        compose.onNodeWithTag(IngredientNoteTags.field(row)).performTextInput("ground")
        compose.onNodeWithTag(IngredientNoteTags.field(row)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !compose.ingredientSheetOpen() }
        compose.waitForIdle()
        val written = stored(id).ingredients[row]
        assertEquals(QuantityUnit.TEASPOON, written.unit)
        assertEquals(Rational.of(2), written.quantity)
        assertEquals("ground", written.note)
    }

    // The name's Next goes to the amount, not the note; on a row "to taste" — no amount — it opens the unit's menu.
    @Test
    fun theNamesNextGoesToTheAmount() {
        val id = open()
        val row = addIngredient(id)
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(compose.keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.ingredientName(row)).performTextInput("Flour")
        compose.onNodeWithTag(EditorTags.ingredientName(row)).performImeAction()
        awaitFocused(EditorTags.ingredientQuantity(row))
        compose.closeIngredient()

        compose.openIngredient(1)
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performClick()
        awaitFocused(EditorTags.ingredientName(1))
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.GRAM)) }
        assertFalse(exists(EditorTags.ingredientQuantity(1)))
    }

    // The unit menu lists the kitchen's usual units first — pc, g, kg, ml, l, tsp, tbsp, cup, pinch, to taste — then
    // the rest of the metric system's (v0.35: no US unit in it); the domain's own order of units is left as it is.
    @Test
    fun theUnitMenuListsTheUsualUnitsFirst() {
        open()
        compose.openIngredient(0)
        compose.onNodeWithTag(EditorTags.ingredientUnit(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.TO_TASTE)) }
        val options = QuantityUnit.entries.associateBy { EditorTags.unitOption(it) }
        val listed = compose.onAllNodes(SemanticsMatcher("a unit option") { it.config.getOrNull(SemanticsProperties.TestTag) in options })
            .fetchSemanticsNodes().map { options.getValue(it.config[SemanticsProperties.TestTag]) }
        assertEquals(
            listOf(
                QuantityUnit.PIECE, QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.MILLILITRE, QuantityUnit.LITRE,
                QuantityUnit.TEASPOON, QuantityUnit.TABLESPOON, QuantityUnit.CUP, QuantityUnit.PINCH, QuantityUnit.TO_TASTE,
                QuantityUnit.TONNE, QuantityUnit.CUBIC_METRE,
            ),
            listed,
        )
        assertEquals(listed, menuUnits(UnitSystem.METRIC, null))
        assertEquals(QuantityUnit.GRAM, QuantityUnit.entries.first())
    }

    @Test
    fun aNewRowAndStepAreTitledNewAndActOnceTheyHoldSomething() =
        newItemsAreTitledNew("New ingredient", "Edit ingredient", "New step", "Edit step", "Remove ingredient", "Remove step")

    @Test
    @Config(qualifiers = "ru")
    fun aNewRowAndStepAreTitledNewAndActOnceTheyHoldSomethingInRussian() =
        newItemsAreTitledNew("Новый ингредиент", "Изменить ингредиент", "Новый шаг", "Изменить шаг", "Убрать ингредиент", "Убрать шаг")

    // A row or a step just added is titled as new and shows no actions — its fields are all there is to it — until it
    // holds something; a row or a step opened again is titled as edited, its removal named with no number.
    private fun newItemsAreTitledNew(newRow: String, editRow: String, newStep: String, editStep: String, removeRow: String, removeStep: String) {
        val id = open()
        val row = addIngredient(id)
        assertEquals(newRow, text(IngredientSheetTags.TITLE))
        assertEquals(emptyList<String>(), rowActions(row))
        compose.onNodeWithTag(EditorTags.ingredientName(row)).performTextInput("Flour")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientRemove(row)) }
        // Still the new row's sheet, whatever it now holds.
        assertEquals(newRow, text(IngredientSheetTags.TITLE))
        assertEquals(removeRow, text(EditorTags.ingredientRemove(row)))
        compose.closeIngredient()
        compose.openIngredient(row)
        assertEquals(editRow, text(IngredientSheetTags.TITLE))
        assertTrue(rowActions(row).contains(EditorTags.ingredientRemove(row)))
        compose.closeIngredient()

        compose.addStepAt(1)
        assertEquals(newStep, text(StepSheetTags.TITLE))
        assertEquals(emptyList<String>(), stepActions(1))
        compose.onNodeWithTag(EditorTags.stepText(1)).performTextInput("Serve.")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepRemove(1)) }
        assertEquals(newStep, text(StepSheetTags.TITLE))
        assertEquals(removeStep, text(EditorTags.stepRemove(1)))
        compose.closeStep()
        compose.openStep(0)
        assertEquals(editStep, text(StepSheetTags.TITLE))
        assertEquals(removeStep, text(EditorTags.stepRemove(0)))
    }

    // The three editor sheets share one scaffold: a title in one style, a Done, and a removal named alike, with no
    // number; the photo source sheets title theirs in the same style.
    @Test
    fun theEditorSheetsShareOneScaffold() {
        open()
        compose.openIngredient(0)
        val style = fontSize(IngredientSheetTags.TITLE)
        assertTrue(exists(IngredientSheetTags.DONE))
        val removals = mutableListOf(text(EditorTags.ingredientRemove(0)))
        compose.closeIngredient()
        compose.openGroup(0)
        assertEquals(style, fontSize(GroupSheetTags.TITLE))
        assertTrue(exists(GroupSheetTags.DONE))
        assertEquals("Edit group", text(GroupSheetTags.TITLE))
        removals += text(EditorTags.groupRemove(0))
        compose.closeGroup()
        compose.openStep(0)
        assertEquals(style, fontSize(StepSheetTags.TITLE))
        assertTrue(exists(StepSheetTags.DONE))
        removals += text(EditorTags.stepRemove(0))
        assertEquals(listOf("Remove ingredient", "Remove group", "Remove step"), removals)

        compose.onNodeWithTag(EditorTags.stepPhoto(0)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(SourceSheetTags.SHEET) }
        val title = compose.onNode(hasText(string(R.string.media_step_photo_label, 1)) and hasAnyAncestor(hasTestTag(SourceSheetTags.SHEET)), useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals(style, layout(title.config))
    }

    @Test
    fun theStepsPhotoSourcesAreWordedAsTheCoversWithIcons() = stepSources("Take a photo", "Choose from gallery")

    @Test
    @Config(qualifiers = "ru")
    fun theStepsPhotoSourcesAreWordedAsTheCoversWithIconsInRussian() = stepSources("Сфотографировать", "Выбрать из галереи")

    // A step's photo sources say what the cover's do — "Take a photo", "Choose from gallery" — and every source has its
    // icon before its words.
    private fun stepSources(take: String, choose: String) {
        open()
        compose.openStep(0)
        compose.onNodeWithTag(EditorTags.stepPhoto(0)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoTake(0)) }
        assertEquals(take, text(EditorTags.stepPhotoTake(0)))
        assertEquals(choose, text(EditorTags.stepPhotoChoose(0)))
        assertEquals(string(R.string.media_source_take), take)
        assertEquals(string(R.string.media_source_choose), choose)
        listOf(EditorTags.stepPhotoTake(0), EditorTags.stepPhotoChoose(0), EditorTags.stepClipRecord(0), EditorTags.stepClipChoose(0)).forEach { item ->
            assertTrue(item, exists(SourceSheetTags.icon(item)))
            assertTrue(item, bounds(SourceSheetTags.icon(item)).right <= compose.onNode(hasText(text(item)) and hasAnyAncestor(hasTestTag(item)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left)
        }
    }

    @Test
    fun insertIngredientIsALabelledButton() = insertIngredientLabelled("Insert ingredient")

    @Test
    @Config(qualifiers = "ru")
    fun insertIngredientIsALabelledButtonInRussian() = insertIngredientLabelled("Вставить ингредиент")

    // "Insert ingredient" is a button that says so, as "Add timer" does — not a bare icon — at least 48 dp tall, its
    // click naming the step.
    private fun insertIngredientLabelled(label: String) {
        open()
        compose.openStep(0)
        val insert = compose.onNodeWithTag(EditorTags.stepInsert(0)).fetchSemanticsNode()
        assertEquals(listOf(label), insert.config[SemanticsProperties.Text].map { it.text })
        assertEquals(Role.Button, insert.config[SemanticsProperties.Role])
        assertEquals(string(R.string.editor_step_insert_ingredient_action, 1), insert.config[SemanticsActions.OnClick].label)
        assertTrue(dp(insert.size.height.toFloat()) >= 48f - 0.5f)
        val timer = compose.onNodeWithTag(EditorTags.timerAttach(0)).fetchSemanticsNode()
        assertEquals(timer.config[SemanticsProperties.Role], insert.config[SemanticsProperties.Role])
        // It still offers the recipe's rows.
        compose.onNodeWithTag(EditorTags.stepInsert(0)).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepOffer(0, 0)) }
    }

    // On a 360 × 640 dp phone with a ~280 dp keyboard up — 24 dp of status bar above the sheet — a new step's sheet
    // shows "Insert ingredient", "Add timer" and "Add photo or clip" without scrolling: they stand right under the
    // step's text, above the one-time offer of references and the note, within the 336 dp the keyboard leaves.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun aNewStepsButtonsStandWithinReachOfTheKeyboard() {
        open(draft(steps = false))
        compose.addStepAt(0)
        awaitFocused(EditorTags.stepText(0))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepReferencesOffered(0)) }
        val order = listOf(
            EditorTags.stepText(0), EditorTags.stepInsert(0), EditorTags.timerAttach(0), EditorTags.stepPhoto(0),
            EditorTags.stepReferencesOffered(0), StepNoteTags.field(0),
        )
        order.zipWithNext().forEach { (above, below) -> assertTrue("$above above $below", bounds(above).bottom <= bounds(below).top) }
        // The sheet's top is its header's: 8 dp over its Done, the header's tallest.
        val sheetTop = dp(bounds(StepSheetTags.DONE).top) - 8f
        val reach = 640f - 280f - 24f
        val seen = (listOf(StepSheetTags.TITLE) + order).joinToString { "$it ${dp(bounds(it).top)}–${dp(bounds(it).bottom)}" }
        assertTrue(seen, dp(bounds(EditorTags.stepPhoto(0)).bottom) - sheetTop <= reach)
    }

    // T-216's review: a row "to taste" never shows its quantity field (FLOW-023 (8)) — even where the unit select is
    // wider than the sheet, at twice the text size on a narrow phone, and quantity and unit would stack.
    @Test
    @Config(qualifiers = "w160dp-h640dp", fontScale = 2f)
    fun aRowToTasteShowsNoQuantityEvenStacked() {
        open()
        compose.openIngredient(1)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientUnit(1)) }
        assertFalse(exists(EditorTags.ingredientQuantity(1)))
        // Stacked: the unit takes the whole width of the fields, as the name does.
        assertEquals(bounds(EditorTags.ingredientName(1)).width, bounds(EditorTags.ingredientUnit(1)).width, 1f)
        // A measured row at the same width does stack its quantity over its unit.
        compose.closeIngredient()
        compose.openIngredient(0)
        assertTrue(bounds(EditorTags.ingredientQuantity(0)).bottom <= bounds(EditorTags.ingredientUnit(0)).top)
    }
}
