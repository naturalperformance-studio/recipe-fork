package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * One rule says a step holds nothing (v0.30, T-217): no text but blanks, no reference, note, timer, photograph or clip.
 * The step's sheet offers a fresh step no actions by it, and [RecipeEditor.dropStep] takes a step away by it — the same
 * function, so the two agree by construction; here, each case of the rule, and the editor dropping exactly the steps the
 * rule calls empty.
 */
@RunWith(AndroidJUnit4::class)
class StepHoldsNothingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @After
    fun close() = book.close()

    private val photo = Media("step.jpg", null, MediaKind.PHOTO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE)

    /** Each step that may follow a first one, with whether it carries [photo], and whether it holds nothing. */
    private val cases = listOf(
        Triple(Step(1, ""), false, true),
        Triple(Step(1, "   "), false, true),
        Triple(Step(1, "Bake."), false, false),
        Triple(Step(1, "", listOf(IngredientReference(0, 0))), false, false),
        Triple(Step(1, "", note = "As grandma did."), false, false),
        Triple(Step(1, "", timer = Timer(5.minutes, "Rest")), false, false),
        Triple(Step(1, ""), true, false),
    )

    @Test
    fun aStepHoldsNothingOnlyWithNoTextReferenceNoteTimerOrMedia() {
        cases.forEach { (step, media, empty) ->
            assertEquals("$step, media $media", empty, holdsNothing(step, if (media) photo else null))
        }
    }

    @Test
    fun theEditorDropsExactlyTheStepsTheRuleCallsEmpty() {
        cases.forEachIndexed { i, (step, media, empty) ->
            val recipe = Recipe.create(now.plusSeconds(i.toLong()), null).copy(
                title = "Case $i",
                ingredients = listOf(Ingredient(0, null, null, "Flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(100))),
                steps = listOf(Step(0, "Mix."), step),
                media = if (media) listOf(photo) else emptyList(),
            )
            runBlocking { recipes.save(recipe) }
            val editor = RecipeEditor(recipes, recipe.id, { now.plusSeconds(60) }, CoroutineScope(Dispatchers.Unconfined))
            runBlocking { editor.load() }
            val shown = editor.recipe.value!!
            val rule = holdsNothing(shown.steps.sortedBy { it.position }[1], shown.stepMedia(1))
            assertEquals("$step, media $media: the rule", empty, rule)
            assertEquals("$step, media $media: the editor", rule, editor.dropStep(1))
            assertEquals("$step, media $media", if (rule) 1 else 2, editor.recipe.value!!.steps.size)
        }
    }
}
