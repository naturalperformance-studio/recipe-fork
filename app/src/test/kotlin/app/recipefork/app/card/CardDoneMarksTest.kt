package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeExport
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.library.deleteRecipe
import app.recipefork.app.library.removeFound
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.Instant
import java.util.zip.ZipFile

/**
 * FLOW-068, decisions/20261004-done-marks-live-on-the-device.md: a tap on an ingredient line or a step marks it done —
 * struck through, muted and read as done — and a tap again unmarks it; the marks are kept on the device per book and
 * recipe, through leaving the card and a restart, whatever the servings or a fixing do; a mark of a row changed in the
 * editor is dropped; "Clear marks" in the card's ⋮ takes them all away; a file, a fork, or the recipe itself never
 * carries them, and a recipe leaving the book takes its marks with it. A line that offers fixing opens it from its
 * amount, a target of its own, the line's second action for the screen reader.
 */
@RunWith(AndroidJUnit4::class)
class CardDoneMarksTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    /** The device's marks, as the application keeps them. */
    private var marks = DoneMarks.preferences(context)
    private var environment by mutableStateOf(environmentOf(marks))
    private var shown by mutableStateOf<RecipeId?>(null)
    private var composed = false

    private val timers = TestTimers(context)

    private fun environmentOf(marks: DoneMarks) = CardEnvironment(marks = marks, ingredients = IngredientEntries { emptyList() })

    @Before
    fun setUp() {
        listOf(book.idling, timers.idling).forEach(compose::registerIdlingResource)
    }

    @After
    fun close() {
        listOf(book.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        book.close()
        timers.close()
    }

    /** For 4: chicken and rice by weight, pepper to taste; three steps. */
    private fun pilaf(state: RecipeState = RecipeState.LOCAL) = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Rice", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(350)),
            Ingredient(2, null, freeTextName = "Pepper", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        ),
        steps = listOf(Step(0, "Fry the chicken."), Step(1, "Add the rice."), Step(2, "Season with pepper.")),
    ).inState(state)

    private fun save(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    /** Opens the card on [id]; a later call opens it anew, as coming back from the book does. */
    private fun show(id: RecipeId?) {
        if (!composed) {
            composed = true
            compose.setContent {
                MaterialTheme(colorScheme = scheme) {
                    shown?.let { CardScreen(it, recipes, environment, onBack = {}, onEdit = {}) }
                }
            }
        }
        compose.runOnIdle { shown = id }
        if (id != null) {
            compose.awaitCard()
            compose.waitUntil(PATIENCE_MS) { exists(CardTags.ingredient(0)) }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String, unmerged: Boolean = false) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Whether the line or step tagged [tag] is read as done by the screen reader. */
    private fun readAsDone(tag: String) = node(tag).config.getOrNull(SemanticsProperties.StateDescription) == "Done"

    /** The texts within [tag] drawn struck through in the muted colour. */
    private fun struck(tag: String): List<String> = compose.textLayoutsWithin(tag)
        .filter { it.layoutInput.style.textDecoration == TextDecoration.LineThrough && it.layoutInput.style.color == scheme.onSurfaceVariant }
        .map { it.layoutInput.text.text }

    private fun done(tag: String): Boolean {
        val read = readAsDone(tag)
        assertEquals("$tag is read as it is drawn", read, struck(tag).isNotEmpty())
        return read
    }

    private fun clickLabel(tag: String) = AccessibilityChecks.clickActionLabel(node(tag))

    private fun ingredient(position: Int) = CardTags.ingredient(position)
    private fun step(position: Int) = CardTags.step(position)

    /** Which lines and steps of the pilaf are done, as the card shows them. */
    private fun doneParts(): List<String> = ((0..2).map(::ingredient) + (0..2).map(::step)).filter(::done)

    // FLOW-068 (1): a tap marks a line or a step done — struck through, muted, read as done, its tap now named as
    // unmarking — and a tap again unmarks it.
    @Test
    fun aTapMarksALineOrAStepDoneAndAnotherUnmarksIt() {
        show(save(pilaf()).id)
        assertEquals(emptyList<String>(), doneParts())
        assertEquals("Mark as done", clickLabel(ingredient(1)))
        assertEquals("Mark as done", clickLabel(step(0)))

        tap(ingredient(1))
        tap(step(0))
        assertEquals(listOf(ingredient(1), step(0)), doneParts())
        assertEquals(listOf("Rice", "350 g"), struck(ingredient(1)))
        assertEquals(listOf("Fry the chicken."), struck(step(0)))
        assertEquals("Mark as not done", clickLabel(ingredient(1)))
        assertEquals("Mark as not done", clickLabel(step(0)))
        // What a line says is unchanged: the mark is its state, not its words.
        assertEquals(listOf("Rice", "350 g"), compose.textsOf(ingredient(1)))

        tap(ingredient(1))
        tap(step(0))
        assertEquals(emptyList<String>(), doneParts())
        assertEquals("Mark as done", clickLabel(ingredient(1)))
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 8)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 8)
    }

    @Test
    @Config(qualifiers = "ru")
    fun theMarksAreNamedInRussian() {
        show(save(pilaf()).id)
        assertEquals("Отметить как готовое", clickLabel(ingredient(0)))
        tap(ingredient(0))
        assertEquals("Готово", node(ingredient(0)).config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals("Снять отметку «готово»", clickLabel(ingredient(0)))
        compose.onNodeWithTag(CardTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.CLEAR_MARKS) }
        assertEquals(listOf("Снять отметки"), compose.textsOf(CardTags.CLEAR_MARKS))
    }

    // FLOW-068 (2): the marks stay after leaving the card for the book and coming back, and after a restart: they are
    // on the device, written to its preferences file, not held by the screen.
    @Test
    fun theMarksStayAfterLeavingTheCardAndARestart() {
        val recipe = save(pilaf())
        show(recipe.id)
        tap(ingredient(0))
        tap(step(2))

        show(null)
        show(recipe.id)
        assertEquals(listOf(ingredient(0), step(2)), doneParts())

        // A restart: the marks read afresh, from a new store over the device's file, by a card composed anew.
        show(null)
        val file = File(context.dataDir, "shared_prefs/done-marks.xml")
        compose.waitUntil(PATIENCE_MS) { file.isFile && recipe.id.value in file.readText() }
        marks = DoneMarks.preferences(context)
        compose.runOnIdle { environment = environmentOf(marks) }
        show(recipe.id)
        assertEquals(listOf(ingredient(0), step(2)), doneParts())
        // Kept per book and recipe, and nowhere in the book's own database.
        assertEquals(2, marks.held(BookName.Pool, recipe.id).size)
        assertEquals(emptySet<DoneMark>(), marks.held(BookName.OfAccount("another"), recipe.id))
        assertEquals(recipe, runBlocking { recipes.get(recipe.id) })
    }

    // FLOW-068 (5): a change of servings and a fixed amount leave the marks where they were.
    @Test
    fun servingsAndAFixedAmountLeaveTheMarks() {
        show(save(pilaf()).id)
        tap(ingredient(0))
        tap(step(1))
        tap(CardTags.SERVINGS_MORE)
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SERVINGS).single() == "Servings: 5" }
        assertEquals(listOf(ingredient(0), step(1)), doneParts())

        compose.onNodeWithTag(CardTags.fixAmount(1), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX) }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("700")
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.FIX) }
        assertEquals("Servings: ≈8", compose.textsOf(CardTags.SERVINGS).single())
        assertEquals(listOf(ingredient(0), step(1)), doneParts())
    }

    // A mark is of a row as it stood: one whose row changed in the editor is dropped, for good; the others stay.
    @Test
    fun aMarkOfARowChangedInTheEditorIsDropped() {
        val recipe = save(pilaf())
        show(recipe.id)
        listOf(ingredient(0), ingredient(1), step(0), step(1)).forEach(::tap)

        val changed = save(
            recipe.copy(
                ingredients = recipe.ingredients.map { if (it.position == 0) it.copy(quantity = Rational.of(600)) else it },
                steps = recipe.steps.map { if (it.position == 1) it.copy(text = "Add the rice and stir.") else it },
            ),
        )
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(ingredient(0)) == listOf("Chicken", "600 g") }
        compose.waitForIdle()
        assertEquals(listOf(ingredient(1), step(0)), doneParts())

        // Changed back, the row does not bring its old mark back.
        save(changed.copy(ingredients = recipe.ingredients))
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(ingredient(0)) == listOf("Chicken", "500 g") }
        compose.waitForIdle()
        assertEquals(listOf(ingredient(1), step(0)), doneParts())
        assertEquals(2, marks.held(BookName.Pool, recipe.id).size)
    }

    // FLOW-068 (3): "Clear marks" is in the card's ⋮ while the recipe has marks, and takes every one of them away.
    @Test
    fun clearMarksIsOfferedWhileTheRecipeHasMarksAndClearsThemAll() {
        // An own recipe in a deletable state: the menu holds Delete alone until something is marked.
        val recipe = save(pilaf())
        show(recipe.id)
        compose.onNodeWithTag(CardTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.DELETE) }
        assertFalse(exists(CardTags.CLEAR_MARKS))
        compose.onNodeWithTag(CardTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.DELETE) }

        listOf(ingredient(0), ingredient(2), step(1)).forEach(::tap)
        compose.onNodeWithTag(CardTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.CLEAR_MARKS) }
        assertEquals(listOf("Clear marks"), compose.textsOf(CardTags.CLEAR_MARKS))
        compose.assertCardTarget(CardTags.CLEAR_MARKS)
        assertTrue(exists(CardTags.DELETE))
        compose.onNodeWithTag(CardTags.CLEAR_MARKS).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.CLEAR_MARKS) }
        assertEquals(emptyList<String>(), doneParts())
        assertEquals(emptySet<DoneMark>(), marks.held(BookName.Pool, recipe.id))
        // The recipe itself is still in the book.
        assertNotNull(runBlocking { recipes.get(recipe.id) })
    }

    // A recipe the card offers no removal for shows its ⋮ only while it has marks to clear; fork floats beside it all
    // the while, never in the ⋮.
    @Test
    fun aFrozenRecipeShowsItsMenuOnlyWhileItHasMarks() {
        show(save(pilaf(RecipeState.PUBLISHED)).id)
        assertFalse(exists(CardTags.MENU))
        assertTrue(exists(CardTags.FORK))
        tap(step(2))
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.MENU) }
        compose.onNodeWithTag(CardTags.MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.CLEAR_MARKS) }
        assertFalse(exists(CardTags.DELETE) || exists(CardTags.REMOVE))
        compose.onNodeWithTag(CardTags.CLEAR_MARKS).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.MENU) }
        assertEquals(emptyList<String>(), doneParts())
        assertTrue(exists(CardTags.FORK))
    }

    // FLOW-068 (4): the file of a recipe carries no marks — it is the same file marked or not — and a fork of it
    // starts with none, its parent's kept.
    @Test
    fun theFileAndAForkCarryNoMarks() {
        val recipe = save(pilaf())
        val before = exportedDocument(recipe.id)
        show(recipe.id)
        listOf(ingredient(0), ingredient(1), step(0)).forEach(::tap)
        assertEquals(before, exportedDocument(recipe.id))
        assertEquals(recipe, runBlocking { recipes.get(recipe.id) })

        val fork = requireNotNull(runBlocking { forkRecipe(recipes, active, recipe.id, now.plusSeconds(60)) })
        show(fork)
        assertEquals(emptyList<String>(), doneParts())
        assertEquals(emptySet<DoneMark>(), marks.held(BookName.Pool, fork))
        assertEquals(3, marks.held(BookName.Pool, recipe.id).size)
    }

    /** The document of the file the recipe [id] exports to. */
    private fun exportedDocument(id: RecipeId): String {
        assertNotNull(runBlocking { RecipeExport.write(context, recipes, id) })
        val file = File(context.cacheDir, "exports").walkTopDown().single { it.isFile }
        return ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(RecipeFile.DOCUMENT)).readBytes().toString(Charsets.UTF_8) }
    }

    // Deleting an own recipe or removing a found one drops its marks, and only its own.
    @Test
    fun aRecipeLeavingTheBookTakesItsMarks() {
        val own = save(pilaf())
        val found = pilaf().copy(title = "Found pilaf").inState(RecipeImport.FOUND_STATE)
        assertTrue(runBlocking { recipes.addFound(found, now) })
        val kept = save(pilaf().copy(title = "Kept pilaf"))
        listOf(own, found, kept).forEach { marks.hold(BookName.Pool, it.id, setOf(DoneMark.of(it.steps.first()))) }
        deleteRecipe(recipes, timers.card, marks, own.id)
        removeFound(RecipeImport(recipes, context.filesDir), timers.card, marks, found.id)
        // The removals run on the timers' scope, on the main thread: its looper is let run until they are done.
        compose.waitUntil(PATIENCE_MS) {
            ShadowLooper.idleMainLooper()
            marks.held(BookName.Pool, own.id).isEmpty() && marks.held(BookName.Pool, found.id).isEmpty()
        }
        assertEquals(null, runBlocking { recipes.get(own.id) })
        assertEquals(null, runBlocking { recipes.get(found.id) })
        assertEquals(1, marks.held(BookName.Pool, kept.id).size)
    }

    // A line that offers fixing keeps it: the tap on the line marks it, the tap on its amount — a card target of its
    // own, named for what it does — opens the fixing; for the screen reader the line carries both, two named actions.
    @Test
    fun aLineThatOffersFixingOpensItFromItsAmount() {
        show(save(pilaf()).id)
        val fixLabel = "Enter the amount you have and recalculate the recipe for it"
        compose.assertCardTarget(CardTags.fixAmount(0))
        val amount = compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(fixLabel, amount.config.getOrNull(SemanticsActions.OnClick)?.label)
        val line = node(ingredient(0))
        assertEquals("Mark as done", line.config.getOrNull(SemanticsActions.OnClick)?.label)
        assertEquals(listOf(fixLabel), line.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label })
        // The amount lies over the quantity, within the line.
        val bounds = amount.boundsInRoot
        assertTrue(bounds.left >= line.boundsInRoot.left - 0.5f && bounds.bottom <= line.boundsInRoot.bottom + 0.5f)

        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX) }
        assertFalse(done(ingredient(0)))
        compose.onNodeWithTag(CardTags.FIX_CANCEL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.FIX) }

        tap(ingredient(0))
        assertTrue(done(ingredient(0)))
        assertFalse(exists(CardTags.FIX))

        // The screen reader's second action opens the fixing as the amount does.
        val fixing = node(ingredient(0)).config[SemanticsActions.CustomActions].single { it.label == fixLabel }
        compose.runOnUiThread { fixing.action() }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX) }
        assertTrue(done(ingredient(0)))
        // A line to taste offers no fixing.
        assertFalse(exists(CardTags.fixAmount(2), unmerged = true))
    }

    private companion object {
        val scheme = lightColorScheme()
    }
}
