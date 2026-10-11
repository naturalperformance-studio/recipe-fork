package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * T-203: an item of equipment is typed as an ingredient's name is — the field offers the equipment dictionary's entries
 * for what is typed, in either language and by synonym, with keeping the text as free text beside them; a pick binds
 * the item, the words after the entry's label are its qualifier, text matching nothing stays free text and says it is not
 * from the dictionary. An entry's item is measured by its label and qualifier, and never shows blank while the
 * dictionary is read. Against the shipped equipment dictionary: 10001 is the frying pan («Сковорода», "skillet"), 10202
 * the oven.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EquipmentOfferScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
    }

    @After
    fun close() {
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun recipe(vararg equipment: EquipmentItem) = Recipe.create(now, null).copy(
        title = "Pancakes",
        servings = 4,
        equipment = equipment.toList(),
        ingredients = listOf(Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))),
        steps = listOf(Step(0, "Fry.")),
    )

    private fun open(recipe: Recipe, equipment: EquipmentSource? = null): Recipe {
        runBlocking { recipes.save(recipe) }
        val catalog = device.catalog(context)
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), catalog, equipment = equipment ?: catalog) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        if (equipment == null) compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe
    }

    private fun stored(recipe: Recipe): Recipe = runBlocking { recipes.get(recipe.id) }!!

    private fun awaitStored(recipe: Recipe, vararg equipment: EquipmentItem) =
        compose.waitUntil(PATIENCE_MS) { stored(recipe).equipment == equipment.toList() }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun field(index: Int): String = EditorTags.equipmentName(index)

    private fun text(tag: String): String = node(tag).config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun addItem(index: Int) {
        compose.onNodeWithTag(EditorTags.EQUIPMENT_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(field(index)) && node(field(index)).config.getOrElse(SemanticsProperties.Focused) { false } }
    }

    /** Types [typed] as the whole content of the item's field. */
    private fun type(index: Int, typed: String) {
        compose.onNodeWithTag(field(index)).performScrollTo().performTextReplacement(typed)
        compose.waitForIdle()
    }

    /** Takes the focus away from where it is, to the title. */
    private fun leaveField() {
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun pick(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(tag), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }

    /** The choices the item's field offers, by tag, in the order shown. */
    private fun choices(index: Int): List<String> {
        if (!exists(EquipmentLineTags.offer(index))) return emptyList()
        return compose.onAllNodes(
            hasAnyAncestor(hasTestTag(EquipmentLineTags.offer(index))) and hasTestTagStartingWith("editor-equipment-$index-offer-"),
        ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
    }

    private fun shownText(tag: String): String = compose.textsOf(tag).joinToString("")

    // Typing offers the entries a word of either language begins, shown in the interface language, with the text to
    // keep as free text beside them; a synonym finds its entry and is shown muted beside its label.
    @Test
    fun typingOffersEntriesInEitherLanguageWithFreeTextBeside() {
        open(recipe())
        addItem(0)
        assertEquals(emptyList<String>(), choices(0))
        type(0, "сков")
        val pan = EquipmentLineTags.entry(0, PAN)
        assertTrue(choices(0).toString(), pan in choices(0))
        assertEquals("Frying pan", shownText(pan))
        assertEquals(EquipmentLineTags.freeText(0), choices(0).last())
        assertEquals(string(R.string.editor_equipment_free_text_offer, "сков"), shownText(EquipmentLineTags.freeText(0)))
        assertEquals("Add “сков” as free text", string(R.string.editor_equipment_free_text_offer, "сков"))
        type(0, "skillet")
        assertEquals(pan, choices(0).first())
        assertFalse(EquipmentLineTags.freeText(0) in choices(0))
        assertEquals("Frying pan · skillet", shownText(pan))
        compose.onNodeWithTag(EquipmentLineTags.synonym(0, PAN), useUnmergedTree = true).assertTextEquals(" · skillet")
        // Each choice is a line at least 48 dp tall whose click says what it does.
        compose.onNodeWithTag(pan).assertHeightIsAtLeast(48.dp)
        assertEquals("Use the dictionary item Frying pan", AccessibilityChecks.clickActionLabel(node(pan)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun typingOffersEntriesInRussian() {
        open(recipe())
        addItem(0)
        type(0, "fry")
        val pan = EquipmentLineTags.entry(0, PAN)
        assertTrue(choices(0).toString(), pan in choices(0))
        assertEquals("Сковорода", shownText(pan))
        assertEquals("Добавить «fry» своими словами", shownText(EquipmentLineTags.freeText(0)))
        // An English synonym finds the entry, shown by its Russian label with the synonym beside it.
        type(0, "frypan")
        assertEquals(pan, choices(0).first())
        assertEquals("Сковорода · frypan", shownText(pan))
        assertEquals("Выбрать предмет словаря Сковорода", AccessibilityChecks.clickActionLabel(node(pan)))
    }

    // A pick binds the item; the words typed after the entry's label are its qualifier, kept as the author leaves; an
    // edit inside the label makes the item the author's own words.
    @Test
    fun pickingAnEntryBindsTheItemAndWordsAfterItAreItsQualifier() {
        val recipe = open(recipe())
        addItem(0)
        type(0, "fry")
        pick(EquipmentLineTags.entry(0, PAN))
        awaitStored(recipe, EquipmentItem.entry(PAN))
        assertEquals("Frying pan", text(field(0)))
        assertFalse(exists(EquipmentLineTags.offer(0)))
        assertFalse(exists(EquipmentLineTags.warning(0)))
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        type(0, "Frying pan ")
        // The space typed stays in the field while the next word comes.
        assertEquals("Frying pan ", text(field(0)))
        type(0, "Frying pan 26 cm")
        awaitStored(recipe, EquipmentItem.entry(PAN, "26 cm"))
        assertFalse(exists(EquipmentLineTags.offer(0)))
        leaveField()
        assertEquals("Frying pan 26 cm", text(field(0)))
        assertFalse(exists(EquipmentLineTags.warning(0)))
        awaitStored(recipe, EquipmentItem.entry(PAN, "26 cm"))
        // A comma separates the qualifier as a space does.
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        type(0, "Frying pan, 28 cm")
        awaitStored(recipe, EquipmentItem.entry(PAN, "28 cm"))
        type(0, "Frying 28 cm")
        awaitStored(recipe, EquipmentItem.free("Frying 28 cm"))
        compose.waitUntil(PATIENCE_MS) { exists(EquipmentLineTags.offer(0)) }
    }

    @Test
    @Config(qualifiers = "ru")
    fun theQualifierInRussian() {
        val recipe = open(recipe())
        addItem(0)
        type(0, "сков")
        pick(EquipmentLineTags.entry(0, PAN))
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        type(0, "Сковорода 26 см")
        awaitStored(recipe, EquipmentItem.entry(PAN, "26 см"))
        leaveField()
        assertEquals("Сковорода 26 см", text(field(0)))
    }

    // Text that matches nothing is kept as free text — by its choice, or as typed — and once left says, as a hint and
    // not as an error, that it is not from the dictionary; a pick in its place takes the hint away.
    @Test
    fun freeTextStaysAndIsMarkedAsNotFromTheDictionary() {
        val recipe = open(recipe())
        addItem(0)
        type(0, "Moonshine still")
        assertEquals(listOf(EquipmentLineTags.freeText(0)), choices(0))
        assertFalse(exists(EquipmentLineTags.warning(0)))
        pick(EquipmentLineTags.freeText(0))
        awaitStored(recipe, EquipmentItem.free("Moonshine still"))
        compose.waitUntil(PATIENCE_MS) { exists(EquipmentLineTags.warning(0)) }
        val hint = "Not from the equipment dictionary: My kitchen does not check this item."
        assertEquals(hint, string(R.string.editor_equipment_free_text_warning))
        assertEquals(hint, shownText(EquipmentLineTags.warning(0)))
        assertFalse(node(field(0)).config.contains(SemanticsProperties.Error))
        // While the author types, the hint is gone; a pick names the item and takes it away for good.
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EquipmentLineTags.warning(0)) }
        type(0, "ove")
        pick(EquipmentLineTags.entry(0, OVEN))
        awaitStored(recipe, EquipmentItem.entry(OVEN))
        assertEquals("Oven", text(field(0)))
        assertFalse(exists(EquipmentLineTags.warning(0)))
        // Typed and left without a pick, it is free text all the same.
        addItem(1)
        type(1, "Moonshine still")
        leaveField()
        awaitStored(recipe, EquipmentItem.entry(OVEN), EquipmentItem.free("Moonshine still"))
        compose.waitUntil(PATIENCE_MS) { exists(EquipmentLineTags.warning(1)) }
    }

    @Test
    @Config(qualifiers = "ru")
    fun freeTextIsMarkedInRussian() {
        val recipe = open(recipe(EquipmentItem.free("Самогонный аппарат")))
        compose.waitUntil(PATIENCE_MS) { exists(EquipmentLineTags.warning(0)) }
        assertEquals("Этого предмета нет в словаре: «Моя кухня» его не проверяет.", shownText(EquipmentLineTags.warning(0)))
        assertEquals(listOf(EquipmentItem.free("Самогонный аппарат")), stored(recipe).equipment)
    }

    // Left with exactly an entry's name typed — a label or a synonym, in either language and any letter case — the item
    // is that entry, as an ingredient's row is.
    @Test
    fun leavingWithAnEntrysNameTypedBindsIt() {
        val recipe = open(recipe())
        addItem(0)
        type(0, "oven")
        assertFalse(EquipmentLineTags.freeText(0) in choices(0))
        leaveField()
        awaitStored(recipe, EquipmentItem.entry(OVEN))
        assertEquals("Oven", text(field(0)))
        assertFalse(exists(EquipmentLineTags.warning(0)))
        addItem(1)
        type(1, "сковородка")
        leaveField()
        awaitStored(recipe, EquipmentItem.entry(OVEN), EquipmentItem.entry(PAN))
        assertEquals("Frying pan", text(field(1)))
    }

    // An entry's item is held to its 40 characters as it reads: the label with the qualifier.
    @Test
    fun anEntrysItemIsMeasuredByItsLabelAndQualifier() {
        // Within the limit alone, over it after "Frying pan ".
        val qualifier = "with a lid, cast iron, 28 cm wide"
        assertEquals(33, qualifier.length)
        val recipe = open(recipe(EquipmentItem.entry(PAN, qualifier)))
        compose.waitUntil(PATIENCE_MS) { text(field(0)) == "Frying pan $qualifier" }
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        leaveField()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.equipmentMark(0)) }
        val said = string(R.string.editor_mark_characters_over, "40", "44")
        compose.onNode(hasTestTag(EditorTags.equipmentMark(0)) and hasText(said)).assertExists()
        assertEquals(listOf(EquipmentItem.entry(PAN, qualifier)), stored(recipe).equipment)
        // Shortened to 40 as it reads, the mark goes.
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        type(0, "Frying pan with a lid, cast iron, 28 cm")
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.equipmentMark(0)) }
        awaitStored(recipe, EquipmentItem.entry(PAN, "with a lid, cast iron, 28 cm"))
    }

    // While the dictionary is read, the form waits for it with the recipe, so an entry's item never shows blank — nor as
    // its qualifier alone; once it is read, the item reads as its label and qualifier.
    @Test
    fun anEntrysItemNeverShowsBlankWhileTheDictionaryIsRead() {
        val read = CompletableDeferred<Unit>()
        val catalog = device.catalog(context)
        val recipe = open(recipe(EquipmentItem.entry(PAN, "26 cm"), EquipmentItem.free("Whisk")), EquipmentSource {
            read.await()
            catalog.equipment()
        })
        // The recipe is read: its title is in the bar.
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasText("Pancakes")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertFalse(exists(field(0)))
        assertFalse(exists(EditorTags.FORM))
        read.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { exists(field(0)) }
        assertEquals("Frying pan 26 cm", text(field(0)))
        assertEquals("Whisk", text(field(1)))
        assertEquals(listOf(EquipmentItem.entry(PAN, "26 cm"), EquipmentItem.free("Whisk")), stored(recipe).equipment)
    }

    private companion object {
        const val PAN = 10001L
        const val OVEN = 10202L
    }
}
