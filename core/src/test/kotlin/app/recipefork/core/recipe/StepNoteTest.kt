package app.recipefork.core.recipe

import app.recipefork.core.file.FileParent
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.file.UnreadableRecipeFile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * A step carries the author's own note beside the method: optional, trimmed, at most 500 characters; a fork by anyone
 * else leaves it behind, as it leaves the description (FORK-007), and the recipe file carries it since format 6.
 */
class StepNoteTest {
    private val at = Instant.parse("2026-10-10T08:00:00Z")
    private val note = "Бабушка всегда добавляла щепотку сахара"
    private val recipe = Recipe.create(at, owner = AccountId("a")).copy(
        title = "Borscht",
        description = "Serve hot.",
        steps = listOf(Step(0, "Fry the beet", timer = Timer(12.minutes)).withNote(note), Step(1, "Season")),
    )

    @Test
    fun theBoundIsFiveHundredCharacters() {
        assertEquals(500, RecipeBounds.MAX_STEP_NOTE)
        assertEquals(RecipeBounds.MAX_STEP_NOTE, RecipeFileCheck.MAX_STEP_NOTE)
    }

    // The note the author typed is trimmed; a blank one is none.
    @Test
    fun aTypedNoteIsTrimmedAndBlankIsNone() {
        assertNull(Step(0, "Fry").note)
        assertEquals("Add sugar", Step(0, "Fry").withNote("  Add sugar \n").note)
        assertNull(Step(0, "Fry").withNote("   ").note)
        assertNull(Step(0, "Fry").withNote("").note)
        assertNull(Step(0, "Fry", note = "x").withNote(null).note)
    }

    // The bridge for one-timer code keeps the note.
    @Test
    fun replacingTheOneTimerKeepsTheNote() {
        assertEquals(note, recipe.steps[0].copy(timer = null).note)
        assertEquals(note, recipe.steps[0].withTimer(Timer(3.minutes))!!.note)
    }

    // The note is bound at 500 characters, inclusive, and is marked after its step's text, before its timers.
    @Test
    fun theNoteHasItsOwnBound() {
        val within = recipe.copy(steps = listOf(recipe.steps[0].copy(note = "n".repeat(500)), recipe.steps[1]))
        assertNull(RecipeBounds.of(within, RecipePart.StepNote(0)))
        assertNull(RecipeBounds.of(within, RecipePart.StepNote(1)))
        val over = within.copy(steps = listOf(within.steps[0].copy(note = "n".repeat(501), text = "t".repeat(2_001)), within.steps[1]))
        assertEquals(Bound.OverLimit(500, 501), RecipeBounds.of(over, RecipePart.StepNote(0)))
        val parts = RecipeBounds.all(over.copy(steps = listOf(over.steps[0].copy(timers = listOf(Timer(1.minutes, "x".repeat(61)))), over.steps[1]))).keys.toList()
        assertEquals(listOf(RecipePart.StepText(0), RecipePart.StepNote(0), RecipePart.TimerName(0, 0)), parts.filter { it !is RecipePart.Title && it !is RecipePart.Language && it !is RecipePart.Servings && it !is RecipePart.Ingredients })
    }

    // FORK-007: the owner's own fork keeps every step's note.
    @Test
    fun anOwnForkKeepsTheNotes() {
        val fork = recipe.fork(at, owner = AccountId("a"), byAuthor = true)
        assertEquals(listOf(note, null), fork.steps.map { it.note })
        assertEquals("Serve hot.", fork.description)
    }

    // FORK-002, FORK-007: a fork by anyone else carries the method — text, references, timers — but no note.
    @Test
    fun aForkByAnotherAccountDropsEveryNote() {
        val fork = recipe.fork(at, owner = AccountId("b"), byAuthor = false)
        assertEquals(listOf(null, null), fork.steps.map { it.note })
        assertEquals(recipe.steps.map { it.copy(note = null) }, fork.steps)
        assertEquals("", fork.description)
    }

    // Format 6 writes a step's note and reads it back; a step with none writes no `note`.
    @Test
    fun theFileCarriesTheNote() {
        val content = RecipeFileContent.of(recipe, null, null)
        val text = RecipeFile.encode(content)
        assertTrue("\"format_version\": 6" in text)
        assertTrue("\"note\": \"$note\"" in text)
        assertEquals(1, Regex("\"note\"").findAll(text).count())
        assertEquals(content, RecipeFile.decode(text))
        assertEquals(emptyList(), RecipeFileCheck.problems(RecipeFile.decode(text)))
    }

    // A note read from a file is trimmed, and a blank one is none.
    @Test
    fun aNoteReadFromAFileIsTrimmed() {
        val text = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
        assertEquals(" padded ".trim(), RecipeFile.decode(text.replace(note, " padded ")).steps[0].note)
        assertNull(RecipeFile.decode(text.replace(note, "   ")).steps[0].note)
    }

    // Formats 1 to 5 wrote no step's note: such a document reads with none, and one that holds a note is no document of its format.
    @Test
    fun anEarlierFormatHoldsNoNote() {
        val withoutNotes = RecipeFile.encode(RecipeFileContent.of(recipe.copy(steps = recipe.steps.map { it.copy(note = null) }), null, null))
        val format5 = withoutNotes.replace("\"format_version\": 6", "\"format_version\": 5")
        assertTrue("\"format_version\": 5" in format5)
        assertEquals(listOf(null, null), RecipeFile.decode(format5).steps.map { it.note })
        val format5WithNote = RecipeFile.encode(RecipeFileContent.of(recipe, null, null))
            .replace("\"format_version\": 6", "\"format_version\": 5")
        assertTrue("\"format_version\": 5" in format5WithNote)
        assertFailsWith<UnreadableRecipeFile> { RecipeFile.decode(format5WithNote) }
    }

    // The file's check holds the note to the editor's bound: trimmed, not blank, at most 500 characters.
    @Test
    fun theCheckHoldsTheNoteToItsBound() {
        val content = RecipeFileContent.of(recipe, null, null).copy(parent = FileParent(RecipeId("p-1"), null, null))
        fun with(note: String?) = content.copy(steps = listOf(content.steps[0].copy(note = note), content.steps[1]))
        assertEquals(emptyList(), RecipeFileCheck.problems(with("n".repeat(500))))
        assertEquals(emptyList(), RecipeFileCheck.problems(with(null)))
        assertEquals(listOf("step 0's note is over 500 characters"), RecipeFileCheck.problems(with("n".repeat(501))))
        listOf(" ", "", " sugar").forEach { bad ->
            assertEquals(listOf("step 0 holds a blank or untrimmed note"), RecipeFileCheck.problems(with(bad)), bad)
        }
        assertFalse(RecipeFileCheck.problems(with(" sugar")).isEmpty())
    }
}
