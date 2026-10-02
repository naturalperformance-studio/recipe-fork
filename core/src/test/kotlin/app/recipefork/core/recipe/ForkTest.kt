package app.recipefork.core.recipe

import app.recipefork.core.scaling.Rational
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * FORK-001, FORK-006, FORK-007: what an owner's fork holds, compared field by field with its parent; FORK-002,
 * FLOW-065: what anyone else's fork — of a recipe found in a file — holds.
 */
class ForkTest {
    private val created = Instant.parse("2026-09-01T08:00:00Z")
    private val lastEdit = Instant.parse("2026-09-10T08:00:00Z")
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val grandparent = RecipeId("grandparent")

    private val ingredients = listOf(
        Ingredient(0, 101, qualifier = "ripe", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(250)),
        Ingredient(
            1, 102, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3, 2),
            pieceWeight = Rational.of(120), divisible = true,
        ),
        Ingredient(2, null, freeTextName = "smoked salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(1, 2)),
        Ingredient(3, 104, unitType = UnitType.TASTE_MEASURED, unit = QuantityUnit.PINCH, quantity = Rational.of(2)),
        Ingredient(4, 105, qualifier = "black", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
    )
    private val steps = listOf(
        Step(0, "Fry the onion until golden", listOf(IngredientReference(0, 9)), Timer(12.minutes, "Onion")),
        Step(1, "Simmer with the salt", listOf(IngredientReference(2, 12), IngredientReference(1, 0)), Timer(95.minutes)),
        Step(2, "Season and serve"),
    )
    private val media = listOf(
        Media("cover-1", AccountId("owner"), MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PUBLISHED),
        Media("step-photo", AccountId("owner"), MediaKind.PHOTO, MediaAttachment.ToStep(0), MediaVisibility.PUBLISHED),
        Media("step-video", AccountId("owner"), MediaKind.VIDEO, MediaAttachment.ToStep(1), MediaVisibility.PUBLISHED),
    )

    /** A parent unlike a new recipe in every field a fork must reset. */
    private fun parent(state: RecipeState = RecipeState.PUBLISHED, safetyFlag: Boolean = false) = Recipe(
        id = RecipeId("parent"),
        owner = AccountId("owner"),
        parent = grandparent,
        sent = SentStatus.SENT_WITH_CHANGES,
        statedAuthor = "chef",
        statedParent = StatedParent("Older soup", "elder"),
        title = "Borscht",
        description = "My grandmother's way.",
        tags = setOf(7L, 9L),
        language = RecipeLanguage.RU,
        servings = 4,
        ingredients = ingredients,
        steps = steps,
        media = media,
        state = state,
        replacesParent = true,
        wasPublished = true,
        safetyFlag = safetyFlag,
        createdAt = created,
        contentChangedAt = lastEdit,
    )

    // FORK-001 (7), FORK-006: a new recipe in `local`, never sent, in the active book, whose parent is the forked one.
    @Test
    fun aForkIsANewLocalNeverSentRecipeInTheActiveBookWhoseParentIsTheForkedRecipe() {
        val parent = parent()
        val fork = parent.fork(now, owner = null)
        assertNotEquals(parent.id, fork.id)
        java.util.UUID.fromString(fork.id.value)
        assertNotEquals(fork.id, parent.fork(now, owner = null).id)
        assertEquals(RecipeId("parent"), fork.parent)
        assertNull(fork.owner)
        assertEquals(AccountId("account"), parent.fork(now, AccountId("account")).owner)
        assertEquals(RecipeState.LOCAL, fork.state)
        assertTrue(fork.state.editable)
        assertEquals(SentStatus.NEVER, fork.sent)
        assertFalse(fork.wasPublished)
        assertFalse(fork.replacesParent)
        assertEquals(now, fork.createdAt)
        assertEquals(now, fork.contentChangedAt)
        assertEquals(now, fork.newestKey)
        assertNull(fork.statedAuthor)
        assertNull(fork.statedParent)
    }

    // FORK-001 (1–5): the content travels field by field.
    @Test
    fun aForkHoldsTheParentsTitleTagsLanguageServingsIngredientsAndStepsFieldByField() {
        val fork = parent().fork(now, owner = null)
        assertEquals("Borscht", fork.title)
        assertEquals(setOf(7L, 9L), fork.tags)
        assertEquals(RecipeLanguage.RU, fork.language)
        assertEquals(4, fork.servings)
        assertEquals(UnitType.entries.toSet(), fork.ingredients.map { it.unitType }.toSet())
        assertEquals(ingredients.size, fork.ingredients.size)
        ingredients.zip(fork.ingredients).forEach { (was, now) ->
            assertEquals(was.position, now.position)
            assertEquals(was.dictionaryEntry, now.dictionaryEntry)
            assertEquals(was.qualifier, now.qualifier)
            assertEquals(was.freeTextName, now.freeTextName)
            assertEquals(was.unitType, now.unitType)
            assertEquals(was.unit, now.unit)
            assertEquals(was.quantity, now.quantity)
            assertEquals(was.pieceWeight, now.pieceWeight)
            assertEquals(was.divisible, now.divisible)
        }
        assertEquals(listOf(0, 1, 2, 3, 4), fork.ingredients.map { it.position })
        assertEquals("smoked salt", fork.ingredients[2].freeTextName)
        assertEquals(Rational.of(120), fork.ingredients[1].pieceWeight)
        assertEquals(true, fork.ingredients[1].divisible)
        assertEquals(steps.size, fork.steps.size)
        steps.zip(fork.steps).forEach { (was, now) ->
            assertEquals(was.position, now.position)
            assertEquals(was.text, now.text)
            assertEquals(was.ingredientReferences, now.ingredientReferences)
            assertEquals(was.timer?.duration, now.timer?.duration)
            assertEquals(was.timer?.label, now.timer?.label)
        }
        // The named timer keeps its name; the unnamed one stays unnamed, so its name follows the fork's own text.
        assertEquals(Timer(12.minutes, "Onion"), fork.steps[0].timer)
        assertEquals(Timer(95.minutes, null), fork.steps[1].timer)
        assertNull(fork.steps[2].timer)
        // Every reference resolves to an ingredient of the fork itself (DOM-007).
        val positions = fork.ingredients.map { it.position }.toSet()
        assertTrue(fork.steps.flatMap { it.ingredientReferences }.all { it.ingredientPosition in positions })
        assertEquals(listOf(IngredientReference(2, 12), IngredientReference(1, 0)), fork.steps[1].ingredientReferences)
    }

    // FORK-001 (4, 5): rewriting the fork's steps keeps the named timer's name and leaves the unnamed one unnamed.
    @Test
    fun rewritingTheForksStepsLeavesTheTimerNamesAsTheyWere() {
        val fork = parent().fork(now, owner = null)
        val rewritten = fork.edited(now) { r -> r.copy(steps = r.steps.map { it.copy(text = "Something else") }) }
        assertEquals("Onion", rewritten.steps[0].timer?.label)
        assertNull(rewritten.steps[1].timer?.label)
    }

    // FORK-007 (1–3): the owner's fork holds the description and media, attached at the same places.
    @Test
    fun anOwnForkHoldsTheDescriptionAndTheMediaAtTheSamePlaces() {
        val fork = parent().fork(now, owner = null)
        assertEquals("My grandmother's way.", fork.description)
        assertEquals(media.size, fork.media.size)
        media.zip(fork.media).forEach { (was, now) ->
            assertEquals(was.kind, now.kind)
            assertEquals(was.attachment, now.attachment)
            // Its own media entity (DOM-005): a new identity, private and unowned until the fork is sent.
            assertNotEquals(was.mediaId, now.mediaId)
            assertNull(now.owner)
            assertEquals(MediaVisibility.PRIVATE, now.visibility)
        }
        assertEquals(3, fork.media.map { it.mediaId }.toSet().size)
    }

    // FORK-007 (6): every state the owner may fork from, editable or frozen, gives the same fork.
    @Test
    fun aForkOfAnEditableRecipeIsTheSameAsOfAFrozenOne() {
        val forks = RecipeState.entries.map { parent(state = it).fork(now, owner = null, id = RecipeId("fork")) }
        val first = forks.first()
        forks.forEach { assertEquals(first.copy(media = emptyList()), it.copy(media = emptyList())) }
        assertTrue(forks.all { it.state == RecipeState.LOCAL })
    }

    // FORK-007 (4): an edit of the fork leaves the parent as it was.
    @Test
    fun anEditOfTheForkLeavesTheParentUnchanged() {
        val parent = parent(state = RecipeState.LOCAL)
        val before = parent.copy()
        val fork = parent.fork(now, owner = null)
        fork.edited(now) { r ->
            r.copy(title = "Green borscht", ingredients = r.ingredients.map { it.copy(quantity = it.quantity?.let { q -> q * Rational.of(2) }) })
        }
        assertEquals(before, parent)
    }

    // FORK-004 (1): a fork of a fork names exactly one parent, the direct one.
    @Test
    fun aForkOfAForkNamesOnlyItsDirectParent() {
        val parent = parent()
        val fork = parent.fork(now, owner = null)
        val forkOfFork = fork.fork(now, owner = null)
        assertEquals(fork.id, forkOfFork.parent)
        assertEquals(parent.id, fork.parent)
    }

    // safety_flag (MOD-008): a fork of a flagged recipe is flagged too; an unflagged parent gives an unflagged fork.
    @Test
    fun aForkInheritsTheParentsSafetyFlag() {
        assertTrue(parent(safetyFlag = true).fork(now, owner = null).safetyFlag)
        assertFalse(parent(safetyFlag = false).fork(now, owner = null).safetyFlag)
    }

    // FORK-002, FLOW-065 (1, 2): a fork by someone other than the author carries the ingredients, steps, references and
    // timers, and none of the description and none of the media.
    @Test
    fun aForkBySomeoneElseCarriesNoDescriptionAndNoMedia() {
        val parent = parent()
        val own = parent.fork(now, owner = null, id = RecipeId("fork"))
        val fork = parent.fork(now, owner = null, id = RecipeId("fork"), byAuthor = false)
        assertEquals("", fork.description)
        assertTrue(fork.media.isEmpty())
        assertEquals(ingredients, fork.ingredients)
        assertEquals(steps, fork.steps)
        assertEquals(listOf(IngredientReference(2, 12), IngredientReference(1, 0)), fork.steps[1].ingredientReferences)
        assertEquals(Timer(12.minutes, "Onion"), fork.steps[0].timer)
        assertEquals(parent.id, fork.parent)
        assertEquals(RecipeState.LOCAL, fork.state)
        assertTrue(fork.state.editable)
        assertNull(fork.statedAuthor)
        // Everything else is as in the owner's fork.
        assertEquals(own.copy(description = "", media = emptyList(), statedParent = fork.statedParent), fork)
    }

    // FLOW-065 (4, 6): it keeps the parent's title and stated author, to name the parent once the book no longer holds it.
    @Test
    fun aForkBySomeoneElseKeepsTheParentsTitleAndStatedAuthor() {
        assertEquals(StatedParent("Borscht", "chef"), parent().fork(now, owner = null, byAuthor = false).statedParent)
        val unsigned = parent().copy(statedAuthor = null, title = "")
        assertEquals(StatedParent("", ""), unsigned.fork(now, owner = null, byAuthor = false).statedParent)
    }
}
