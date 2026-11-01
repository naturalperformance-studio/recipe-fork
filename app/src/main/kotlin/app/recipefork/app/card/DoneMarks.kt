package app.recipefork.app.card

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import java.util.concurrent.ConcurrentHashMap

/** The part of a card a done mark is on: an ingredient line, a step, or an item of equipment. */
enum class MarkedPart(internal val code: String) { INGREDIENT(INGREDIENT_KEY), STEP(STEP_KEY), EQUIPMENT(EQUIPMENT_KEY) }

/**
 * How each part is written in the store. A part is added under a code of its own, so that the marks stored before it
 * read as they did; a store read by a version that does not know a code passes over the marks written under it.
 */
private const val INGREDIENT_KEY = "i"
private const val STEP_KEY = "s"
private const val EQUIPMENT_KEY = "e"

/**
 * A done mark (FLOW-068) on the ingredient row, the step or the item of equipment at [position] of its part, taken of what that row held
 * when it was marked ([row]), so that a mark whose row has since changed in the editor is dropped, as a held fixing is
 * ([HeldScale]).
 */
data class DoneMark(val part: MarkedPart, val position: Int, val row: Int) {
    companion object {
        /** The mark of [ingredient] as it stands. */
        fun of(ingredient: Ingredient) = DoneMark(MarkedPart.INGREDIENT, ingredient.position, rowOf(ingredient))

        /** The mark of [step] as it stands, taken of [stepKey]. */
        fun of(step: Step) = DoneMark(MarkedPart.STEP, step.position, rowOf(stepKey(step)))

        /**
         * What a step's mark is taken of: a step with no note as it printed before a step carried the author's note, so
         * that the marks held of it keep reading; a step with a note as it prints now, its note included.
         */
        internal fun stepKey(step: Step): String =
            if (step.note == null) Before.Step(step.position, step.text, step.ingredientReferences, step.timers).toString() else step.toString()

        /**
         * The mark of the item of equipment at [index] of the recipe's equipment, [item], as it stands: free text is
         * taken of its text, as before items named entries, so that its marks stay; an entry's item of its entry's id
         * and its qualifier ([equipmentKey]), never of how the item happens to print.
         */
        fun ofEquipment(index: Int, item: EquipmentItem) = DoneMark(MarkedPart.EQUIPMENT, index, rowOf(equipmentKey(item)))

        /** What an item of equipment's mark is taken of: its free text, or "e:<entry id>:<qualifier>". */
        internal fun equipmentKey(item: EquipmentItem): String =
            item.dictionaryEntry?.let { listOf(EQUIPMENT_KEY, it, item.qualifier.orEmpty()).joinToString(":") }
                ?: item.freeTextName.orEmpty()

        /**
         * A stable mark of what a row holds, the same from one process to the next; like [HeldScale]'s, it is taken of
         * the row's text, so an update of the application may read a row as changed and drop its mark.
         */
        private fun rowOf(row: Any): Int = row.toString().hashCode()
    }
}

/** What was marked before a step carried the author's note, printing as it printed then. */
private object Before {
    /** A step as it was before its note: it prints "Step(position=…, text=…, ingredientReferences=…, timers=…)". */
    data class Step(val position: Int, val text: String, val ingredientReferences: List<IngredientReference>, val timers: List<Timer>)
}

/**
 * The reader's done marks, kept on the device per book and recipe (FLOW-068, decisions/20261004-done-marks-live-on-the-device.md):
 * never in the book's database and never in the recipe, so that no file, fork or sending carries them. They stay until
 * the reader clears them, or the recipe leaves the book. Kept as short texts in a store of keys — the device's
 * preferences, or a map for as long as the object lives.
 */
class DoneMarks(
    private val read: (String) -> String?,
    private val write: (String, String?) -> Unit,
) {
    /** The marks held for [recipe] of [book], as they were marked; whatever does not read back reads as none. */
    fun held(book: BookName, recipe: RecipeId): Set<DoneMark> = decoded(read(key(book, recipe)))

    /** Holds [marks] for [recipe] of [book], in place of any held before; none holds nothing. */
    fun hold(book: BookName, recipe: RecipeId, marks: Set<DoneMark>) =
        write(key(book, recipe), marks.takeIf { it.isNotEmpty() }?.let(::encoded))

    /** Nothing is held for [recipe] of [book] any more. */
    fun clear(book: BookName, recipe: RecipeId) = write(key(book, recipe), null)

    companion object {
        /** Held in memory alone, for as long as the object lives. */
        fun inMemory(): DoneMarks {
            val held = ConcurrentHashMap<String, String>()
            return DoneMarks(held::get) { key, value -> if (value == null) held.remove(key) else held[key] = value }
        }

        /** Kept in a small preferences file of the device, across restarts. */
        fun preferences(context: Context): DoneMarks {
            val preferences = context.getSharedPreferences(FILE_KEY, Context.MODE_PRIVATE)
            // One key read, never a copy of every key the file holds; a value of another type reads as none.
            return DoneMarks({ runCatching { preferences.getString(it, null) }.getOrNull() }) { key, value ->
                preferences.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
        }

        /** The device's preferences file the marks are kept in, apart from every other setting. */
        private const val FILE_KEY = "done-marks"
        private const val MARKS = ";"
        private const val FIELDS = ":"

        private fun key(book: BookName, recipe: RecipeId) = "${book.fileName}/${recipe.value}"

        private fun encoded(marks: Set<DoneMark>): String =
            marks.joinToString(MARKS) { listOf(it.part.code, it.position, it.row).joinToString(FIELDS) }

        private fun decoded(text: String?): Set<DoneMark> {
            if (text.isNullOrEmpty()) return emptySet()
            return text.split(MARKS).mapNotNull { mark ->
                val fields = mark.split(FIELDS).takeIf { it.size == 3 } ?: return@mapNotNull null
                val part = MarkedPart.entries.firstOrNull { it.code == fields[0] } ?: return@mapNotNull null
                val position = fields[1].toIntOrNull() ?: return@mapNotNull null
                val row = fields[2].toIntOrNull() ?: return@mapNotNull null
                DoneMark(part, position, row)
            }.toSet()
        }
    }
}

/**
 * The done marks of the [recipe] a card shows, as they stand: those held of rows that still hold what they held when
 * marked; a mark of a row changed since, or gone, is not shown, and [dropStale] takes it out of the store too. Marking,
 * unmarking and clearing are kept at once. Nothing here depends on the scale: a change of servings or a fixing leaves
 * the marks as they are.
 */
@Stable
internal class CardMarks(private val store: DoneMarks, private val book: BookName, recipe: Recipe) {
    private val id = recipe.id

    /** The mark each line and step would carry now, by its part and position. */
    private val current: Map<Pair<MarkedPart, Int>, DoneMark> =
        (recipe.ingredients.map(DoneMark::of) + recipe.steps.map(DoneMark::of) + recipe.equipment.mapIndexed(DoneMark::ofEquipment))
            .associateBy { it.part to it.position }

    /** What the store held when the card read it, stale marks included. */
    private val held: Set<DoneMark> = store.held(book, id)

    private var marks: Set<DoneMark> by mutableStateOf(held.filter { current[it.part to it.position] == it }.toSet())

    /**
     * Takes the marks of rows changed since, or gone, out of the store as well — from an effect, never while the card
     * is composed. The store then holds the marks as they stand, which a mark, an unmark or a clearing since has
     * already written.
     */
    fun dropStale() {
        if (marks != held) store.hold(book, id, marks)
    }

    /** Whether any line, step or item of equipment of the recipe is marked. */
    val any: Boolean get() = marks.isNotEmpty()

    /** Whether the line or step at [position] of [part] is marked done. */
    fun done(part: MarkedPart, position: Int): Boolean = current[part to position]?.let { it in marks } == true

    /** Marks the line or step at [position] of [part] done, or, if it is, not done. */
    fun toggle(part: MarkedPart, position: Int) {
        val mark = current[part to position] ?: return
        marks = if (mark in marks) marks - mark else marks + mark
        store.hold(book, id, marks)
    }

    /** Every mark of the recipe goes; what went is returned, for [restore] to put back. */
    fun clear(): Set<DoneMark> {
        val cleared = marks
        marks = emptySet()
        store.clear(book, id)
        return cleared
    }

    /**
     * Puts back the [cleared] marks a [clear] took away (its Undo), beside any made since: exactly those, each only
     * while its row still holds what it held when marked.
     */
    fun restore(cleared: Set<DoneMark>) {
        val back = cleared.filter { current[it.part to it.position] == it }
        if (back.isEmpty()) return
        marks = marks + back
        store.hold(book, id, marks)
    }
}

/**
 * The marks of [recipe] of [book], read afresh whenever its lines, steps or equipment change; the marks of rows changed since are
 * dropped from the store from an effect, once the card is composed.
 */
@Composable
internal fun rememberCardMarks(store: DoneMarks, book: BookName, recipe: Recipe): CardMarks {
    val marks = remember(store, book, recipe.id, recipe.ingredients, recipe.steps, recipe.equipment) { CardMarks(store, book, recipe) }
    LaunchedEffect(marks) { marks.dropStale() }
    return marks
}

/**
 * Makes the line or step it modifies a check box the reader ticks done with a tap, and unticks with another
 * (FLOW-068): toggleable, in the role of a check box, its state on or off, so that the screen reader reads it as
 * "Done" or "Not done" — or as [state] where the line has a state of its own to say, as the current step does — and
 * names the tap "Mark as done" or "Mark as not done".
 */
@Composable
internal fun Modifier.markedDone(done: Boolean, onToggle: () -> Unit, state: String? = null): Modifier {
    val label = stringResource(if (done) R.string.card_mark_not_done_action else R.string.card_mark_done_action)
    val said = state ?: stringResource(if (done) R.string.card_marked_done else R.string.card_marked_not_done)
    return semantics {
        stateDescription = said
        onClick(label = label, action = null)
    }.toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
}

/**
 * Shows the mark of the line or step it modifies, read-only ([CardMode.VIEW]): the screen reader reads its state in
 * words — "Done" or "Not done" — but finds no action, no check box role and no toggleable state on it, so that it is
 * neither offered nor taken as a control that changes it. While the recipe has no mark at all ([stated] false) a line
 * carries no state, as a recipe not yet cooked has nothing to say of it.
 */
@Composable
internal fun Modifier.shownDone(done: Boolean, stated: Boolean): Modifier {
    if (!stated) return this
    val said = stringResource(if (done) R.string.card_marked_done else R.string.card_marked_not_done)
    return semantics { stateDescription = said }
}

/** The colour of a line's or a step's text marked done: muted, and still of the theme's contrast (ContrastTest). */
internal val doneColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** How a line's or a step's text marked done is drawn: struck through, and muted ([doneColor]). */
@Composable
internal fun TextStyle.done(done: Boolean): TextStyle =
    if (done) copy(color = doneColor, textDecoration = TextDecoration.LineThrough) else this

/**
 * The done mark before a line, an item or a step on Cooking (decisions 2026-10-15, item 9; redesign-1 § 4.4, `.mk`): a
 * disc in the primary colour with the redesign's check in `onPrimary` when [done]; an empty circle outlined in [ring]
 * when not — the theme's muted ink on the page, the container's own ink on the current step. One of three signs, with
 * the text struck through and muted ([done]): never colour alone. Drawn, not read: the line it stands on already says
 * its state in words ([markedDone]), so it says nothing; a test reads it by [tag] and [ShowsCheck].
 */
@Composable
internal fun DoneCircle(done: Boolean, tag: String, modifier: Modifier = Modifier, ring: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val colours = MaterialTheme.colorScheme
    val drawn = if (done) Modifier.background(colours.primary, CircleShape) else Modifier.border(DONE_MARK_RING, ring, CircleShape)
    Box(
        modifier
            .size(DONE_MARK_SIZE)
            .then(drawn)
            .clearAndSetSemantics { testTag = tag; showsCheck = done },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            val check = colours.onPrimary
            Canvas(Modifier.size(DONE_MARK_CHECK)) {
                // The redesign's check, drawn on a 24-unit square: M5 12.5 l4.5 4.5 L19 7.5, stroke 2.6, round.
                val unit = size.width / 24f
                val path = Path().apply {
                    moveTo(5f * unit, 12.5f * unit)
                    lineTo(9.5f * unit, 17f * unit)
                    lineTo(19f * unit, 7.5f * unit)
                }
                drawPath(path, check, style = Stroke(width = 2.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** The side of a done mark's circle. */
internal val DONE_MARK_SIZE: Dp = 28.dp

/** The width of the ring of a done mark not yet ticked. */
internal val DONE_MARK_RING: Dp = 2.dp

/** The size of the check drawn in a ticked done mark. */
private val DONE_MARK_CHECK: Dp = 18.dp

/** The space between a done mark and what follows it on its line. */
internal val DONE_MARK_GAP: Dp = 12.dp

/** Whether a done mark shows its check: what a test reads of a mark that says nothing. */
internal val ShowsCheck = SemanticsPropertyKey<Boolean>(SHOWS_CHECK_KEY)
private const val SHOWS_CHECK_KEY = "card.shows-check"
internal var SemanticsPropertyReceiver.showsCheck by ShowsCheck
