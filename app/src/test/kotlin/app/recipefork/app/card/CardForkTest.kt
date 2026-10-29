package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.fork
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

/**
 * The card's fork action and a fork's provenance area (FORK-001, FORK-003 (3), FORK-004 (1, 3), FLOW-065): the fork
 * action sits in the bar of every recipe, editable or frozen; a fork names its one direct parent by title, which
 * opens the parent's card while the book holds it, and is said to be unavailable otherwise.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CardForkTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private var forks = 0
    private val opened = mutableListOf<RecipeId>()

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

    private fun save(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun recipe(title: String, state: RecipeState = RecipeState.LOCAL) = Recipe.create(now, null)
        .copy(title = title, servings = 2, sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT)
        .inState(state)

    private var shown by mutableStateOf<RecipeId?>(null)
    private var composed = false

    private fun show(id: RecipeId) {
        if (!composed) {
            composed = true
            compose.setContent {
                MaterialTheme {
                    shown?.let {
                        key(it) {
                            CardScreen(
                                it, recipes, environment, onBack = {}, onEdit = {},
                                onFork = { forks++ }, onOpenParent = { parent -> opened += parent },
                            )
                        }
                    }
                }
            }
        }
        compose.runOnIdle { shown = id }
        compose.waitForIdle()
        compose.awaitCard()
    }

    private fun awaitProvenance() = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(hasTestTag(CardTags.PROVENANCE)).fetchSemanticsNodes().size == 1
    }

    private fun interactiveTags(): List<String?> =
        compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .sortedWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x }))
            .map { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } }

    /** The card's two tabs, under its bar. */
    private val TABS = listOf(CardTags.tab(CardMode.VIEW), CardTags.tab(CardMode.COOKING))

    // The fork action floats at the bottom end of the card of every recipe of the book, in each of the eight states,
    // never in the bar or its ⋮, and asks for a fork.
    @Test
    fun theCardOffersForkOnEveryRecipeEditableOrFrozen() {
        RecipeState.entries.forEach { state ->
            val recipe = save(recipe("Soup in $state", state))
            show(recipe.id)
            assertEquals("$state", 0, compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.BAR)) and hasTestTag(CardTags.FORK)).fetchSemanticsNodes().size)
            val expected = listOf(CardTags.BACK) + listOfNotNull(CardTags.EDIT.takeIf { state.editable }) + CardTags.MENU +
                TABS + listOf(CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE, CardTags.FORK)
            assertEquals("$state", expected, interactiveTags())
            val screen = compose.onNodeWithTag(CardTags.SCREEN).fetchSemanticsNode().boundsInRoot
            val fork = compose.onNodeWithTag(CardTags.FORK).fetchSemanticsNode().boundsInRoot
            assertTrue("$state: fork at the bottom end, $fork in $screen",
                screen.right - fork.right < screen.width * 0.1f && screen.bottom - fork.bottom < screen.height * 0.1f)
            val before = forks
            compose.forkFromTheCard()
            assertEquals("$state", before + 1, forks)
        }
    }

    // Scenario 4 of v0.41: the fork action draws the provenance glyph, once and unread; its words say what it does.
    @Test
    fun theForkActionDrawsTheProvenanceGlyph() {
        show(save(recipe("Soup")).id)
        val glyphs = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(CardTags.FORK)) and SemanticsMatcher.expectValue(DrawnIcon, R.drawable.ic_provenance),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(1, glyphs.size)
        assertNull(glyphs.single().config.getOrElseNullable(SemanticsProperties.ContentDescription) { null })
    }

    // A card of a recipe the book does not hold has nothing to fork.
    @Test
    fun aMissingRecipeOffersNoFork() {
        show(RecipeId("not-here"))
        compose.onNodeWithTag(CardTags.FORK).assertDoesNotExist()
    }

    // A11Y-003, A11Y-005: the fork action is 48 dp and announces what it does; the provenance too.
    @Test
    fun theForkActionAndTheProvenanceAreNamedAndAnnounced() = announced(
        Words("Fork", "Make an editable copy of this recipe", "after “Borscht”", "Open the recipe this one was forked from",
            "after a recipe not in this book", "Borscht", "Green borscht"),
    )

    @Test
    @Config(qualifiers = "ru")
    fun theForkActionAndTheProvenanceAreNamedAndAnnouncedInRussian() = announced(
        Words("Своя версия", "Создать редактируемую копию этого рецепта", "по рецепту «Борщ»",
            "Открыть рецепт, на котором основан этот", "по рецепту, которого нет в книге",
            "Борщ", "Зелёный борщ"),
    )

    private class Words(
        val fork: String,
        val forkAction: String,
        val provenance: String,
        val openAction: String,
        val unavailable: String,
        val parentTitle: String,
        val forkTitle: String,
    )

    private fun announced(w: Words) {
        val parent = save(recipe(w.parentTitle))
        val fork = save(parent.fork(now, owner = null).copy(title = w.forkTitle))
        show(fork.id)
        awaitProvenance()
        assertEquals(listOf(w.fork), compose.textsOf(CardTags.FORK))
        assertEquals(w.forkAction, AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(CardTags.FORK).fetchSemanticsNode()))
        compose.assertCardTarget(CardTags.FORK)
        assertEquals(listOf(w.provenance), compose.textsOf(CardTags.PROVENANCE))
        assertEquals(w.openAction, AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(CardTags.PROVENANCE).fetchSemanticsNode()))
        // Back, edit, the ⋮, the provenance, fewer, more and fork.
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 6)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 6)
        val content = setOf(w.forkTitle, "2")
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)

        val orphan = save(Recipe.create(now, null).copy(title = w.forkTitle, parent = RecipeId("gone")))
        show(orphan.id)
        awaitProvenance()
        assertEquals(listOf(w.unavailable), compose.textsOf(CardTags.PROVENANCE))
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)
    }

    // FORK-003 (3): a fork names its parent by title, above the fork's title, and tapping it opens the parent.
    @Test
    fun aForksCardNamesItsParentByTitleAboveItsOwnAndTappingItOpensTheParent() {
        val parent = save(recipe("Borscht", RecipeState.PUBLISHED))
        val fork = save(parent.fork(now, owner = null).copy(title = "Green borscht"))
        show(fork.id)
        awaitProvenance()
        assertEquals(listOf("after “Borscht”"), compose.textsOf(CardTags.PROVENANCE))
        compose.onNode(hasTestTag(CardTags.PROVENANCE) and hasAnyAncestor(hasTestTag(CardTags.CONTENT))).assertExists()
        val title = compose.onNodeWithTag(CardTags.TITLE).fetchSemanticsNode().positionInRoot.y
        assertTrue(compose.onNodeWithTag(CardTags.PROVENANCE).fetchSemanticsNode().positionInRoot.y < title)
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        assertEquals(listOf(parent.id), opened)
    }

    // The provenance follows the book: a renamed parent is named by its new title.
    @Test
    fun theProvenanceNamesTheParentAsTheBookHoldsItNow() {
        val parent = save(recipe("Borscht"))
        val fork = save(parent.fork(now, owner = null))
        show(fork.id)
        awaitProvenance()
        save(parent.copy(title = "Red borscht"))
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.PROVENANCE) == listOf("after “Red borscht”") }
    }

    // A parent with no title yet is named as an untitled recipe.
    @Test
    fun anUntitledParentIsNamedAsUntitled() {
        val parent = save(recipe(""))
        show(save(parent.fork(now, owner = null)).id)
        awaitProvenance()
        assertEquals(listOf("after “Untitled recipe”"), compose.textsOf(CardTags.PROVENANCE))
    }

    // FORK-003 (3), FLOW-065: a parent the book does not hold is said to be unavailable and opens nothing.
    @Test
    fun aParentTheBookDoesNotHoldIsNamedUnavailableAndCannotBeOpened() {
        val orphan = save(Recipe.create(now, null).copy(title = "Green borscht", parent = RecipeId("not-in-this-book")))
        show(orphan.id)
        awaitProvenance()
        assertEquals(listOf("after a recipe not in this book"), compose.textsOf(CardTags.PROVENANCE))
        val node = compose.onNodeWithTag(CardTags.PROVENANCE).fetchSemanticsNode()
        assertNull(node.config.getOrElseNullable(SemanticsActions.OnClick) { null })
        assertEquals(listOf(CardTags.BACK, CardTags.EDIT, CardTags.MENU) + TABS + CardTags.FORK, interactiveTags())
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        assertEquals(emptyList<RecipeId>(), opened)
    }

    // FORK-004 (1, 3): a fork of a fork names exactly one parent, the direct one; no card lists descendants.
    @Test
    fun aForkOfAForkNamesOnlyItsDirectParentAndNoCardListsDescendants() {
        val original = save(recipe("Borscht"))
        val fork = save(original.fork(now, owner = null).copy(title = "Green borscht"))
        val forkOfFork = save(fork.fork(now, owner = null).copy(title = "Cold green borscht"))
        show(forkOfFork.id)
        awaitProvenance()
        assertEquals(listOf("after “Green borscht”"), compose.textsOf(CardTags.PROVENANCE))
        assertEquals(1, compose.onAllNodes(hasTestTag(CardTags.PROVENANCE)).fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), shownTexts().filter { "Borscht" in it })
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        assertEquals(listOf(fork.id), opened)
        // The original has forks but no parent: its card names no recipe but its own.
        show(original.id)
        compose.onNodeWithTag(CardTags.PROVENANCE).assertDoesNotExist()
        assertEquals(emptyList<String>(), shownTexts().filter { "green" in it.lowercase() })
        // The middle one names its own parent, and nothing of the fork made from it.
        show(fork.id)
        awaitProvenance()
        assertEquals(listOf("after “Borscht”"), compose.textsOf(CardTags.PROVENANCE))
        assertEquals(emptyList<String>(), shownTexts().filter { "Cold" in it })
    }

    // A recipe with no parent has no provenance area.
    @Test
    fun aRecipeWithNoParentHasNoProvenance() {
        show(save(recipe("Toast")).id)
        compose.onNodeWithTag(CardTags.PROVENANCE).assertDoesNotExist()
    }

    private fun shownTexts(): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.SCREEN)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { t -> t.text } }
            .filter { it != compose.textsOf(CardTags.TITLE).single() }
}
