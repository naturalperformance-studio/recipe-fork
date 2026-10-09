package app.recipefork.app.editor

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.saveable.Saver
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.data.DragHandleSide
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientSource
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.ClipTransformer
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.RetrieverClipSourceDuration
import app.recipefork.app.media.cover
import app.recipefork.app.media.stepMedia
import app.recipefork.app.media.stepPhoto
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Bound
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShares
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.ReferenceSpacing
import app.recipefork.core.recipe.ServingCountInput
import app.recipefork.core.recipe.ServingCountReading
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.withTypedQuantity
import app.recipefork.core.recipe.withUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * What the editor needs beyond the recipes of the active book: the book itself, the dictionaries, the side of the drag
 * column and a clock.
 */
class EditorEnvironment(
    val book: ActiveBook,
    val tags: TagSource,
    /** The ingredient dictionary the ingredient field offers from (FLOW-057); the catalog that holds the tags holds it. */
    val ingredients: IngredientSource = tags as? IngredientSource ?: IngredientSource { emptyList() },
    /** Which side of a block its drag column stands on (the left unless set otherwise). */
    val dragHandleSide: DragHandleSide = HeldDragHandleSide(),
    /** Where the editor keeps photographs, given the application's files directory; replaced in a test to hold one back. */
    val photoStore: (File) -> PhotoStore = { PhotoStore(it) },
    /** Where the editor converts and keeps steps' clips, given the application's context; replaced in a test. */
    val clipStore: (Context) -> ClipStore = { ClipStore(it.filesDir, ClipTransformer(it), RetrieverClipSourceDuration(it)) },
    /** What the release is built as (FLOW-061): the editor says nothing of publication where nothing can be published. */
    val mode: Mode = Mode.Standard,
    val clock: () -> Instant = Instant::now,
)

/** The author's interface language (L10N-001): Russian where the interface is Russian, English otherwise. */
fun interfaceLanguage(configuration: Configuration): RecipeLanguage =
    if (configuration.locales[0].language == RecipeLanguage.RU.name.lowercase()) RecipeLanguage.RU else RecipeLanguage.EN

/**
 * A new recipe in `local`, in the active book — the pool's when no account is active (FLOW-042, FLOW-002) — whose
 * declared language is already the author's interface language (L10N-004, DOM-006).
 */
fun newRecipe(environment: EditorEnvironment, language: RecipeLanguage): Recipe {
    val owner = (environment.book.open.value.name as? BookName.OfAccount)?.let { AccountId(it.accountId) }
    return Recipe.create(environment.clock(), owner).copy(language = language)
}

/**
 * One recipe open in the editor. Every change is written to the active book as it is made, in the order it was
 * made, so that leaving the editor at any point loses nothing (FLOW-024). Only a state that `entity-states`
 * makes editable accepts a change (FLOW-002).
 */
class RecipeEditor(
    private val recipes: RecipeRepository,
    val id: RecipeId,
    private val clock: () -> Instant,
    /** Outlives the screen, so that a change made just before leaving is still written. */
    private val saving: CoroutineScope,
    /**
     * The keys of the steps and of the ingredient rows. The screen keeps them across the activity being recreated, so
     * that what it saved for a row comes back on that row and not on whichever stands at its old position.
     */
    private val stepKeys: RowKeys = RowKeys(),
    private val ingredientKeys: RowKeys = RowKeys(),
    /** As [ingredientKeys], for the headings of the ingredient groups. */
    private val groupKeys: RowKeys = RowKeys(),
) {
    private val current = MutableStateFlow<Recipe?>(null)

    /** The recipe as the author has it now; null until it is read from the book. */
    val recipe: StateFlow<Recipe?> = current.asStateFlow()

    /** Reads the recipe once every change still being written, by this editor or an earlier one, is in the book. */
    suspend fun load() {
        current.value = writes.withLock { recipes.get(id) }
    }

    val editable: Boolean get() = current.value?.state?.editable == true

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setLanguage(language: RecipeLanguage?) = edit { it.copy(language = language) }

    /** A tag is committed only as an entry the dictionary offered (DOM-008). */
    fun addTag(tag: TagEntity) = edit { it.copy(tags = it.tags + tag.id) }

    fun removeTag(tagId: Long) = edit { it.copy(tags = it.tags - tagId) }

    // The steps and the description (FLOW-024).

    /** Appends a step of no text yet; it accepts text whatever else the recipe holds (FLOW-024 (2)). */
    fun addStep() = edit { recipe ->
        recipe.copy(steps = recipe.steps.sortedBy { it.position } + Step(position = recipe.steps.size, text = ""))
    }

    /** The step's plain text; its timer and its ingredient references are kept as they stand. */
    fun setStepText(position: Int, text: String) = editStep(position) { it.copy(text = text) }

    /**
     * The step's text with the ingredient [references] in it as the author left them (FLOW-031): the text never holds
     * a quantity, and each reference sits at an offset in it. Only references to this recipe's own ingredients, within
     * the text and one to an offset, are held (DOM-007). Its timer is kept.
     */
    fun setStepText(position: Int, text: String, references: List<IngredientReference>) = edit { recipe ->
        val steps = recipe.steps.sortedBy { it.position }
        if (position !in steps.indices) return@edit recipe
        val held = references
            .filter { it.ingredientPosition in recipe.ingredients.indices && it.textOffset in 0..text.length }
            .distinctBy { it.textOffset }
            .sortedBy { it.textOffset }
        recipe.copy(steps = steps.mapIndexed { i, step -> if (i == position) step.copy(text = text, ingredientReferences = held) else step })
    }

    /**
     * Deletes the step at [position], with its own timer, references and media; the steps after it move up and keep
     * theirs, which point at ingredients, never at steps, so each still resolves to the same ingredient (DOM-007).
     * The deletion is offered for undoing as [removal]; once it is ended without being undone, [written] is given the
     * identities of the media deleted with the step, whose files are then the caller's to delete.
     */
    fun removeStep(position: Int, written: suspend (removed: List<String>) -> Unit = {}): Boolean {
        val recipe = current.value ?: return false
        if (position !in recipe.steps.indices) return false
        val removed = recipe.media.filter { it.attachment == MediaAttachment.ToStep(position) }.map { it.mediaId }
        val removing = edit { edited ->
            val steps = edited.steps.sortedBy { it.position }
            stepKeys.remove(position, steps.size)
            edited.copy(
                steps = steps.filterIndexed { i, _ -> i != position }.mapIndexed { i, step -> step.copy(position = i) },
                media = edited.media
                    .filter { (it.attachment as? MediaAttachment.ToStep)?.stepPosition != position }
                    .followingSteps { if (it > position) it - 1 else it },
            )
        }
        if (removing) offered.value = Removal(Removal.Kind.STEP, position, recipe) { if (removed.isNotEmpty()) written(removed) }
        return removing
    }

    /**
     * Puts a step of no text at [position], from 0 to the number of steps; the steps from there on move down, each
     * with its own text, timer, references and media (FLOW-025 (1)). Returns whether the step was inserted.
     */
    fun insertStep(position: Int): Boolean {
        val count = current.value?.steps?.size ?: return false
        if (position !in 0..count) return false
        return edit { recipe ->
            val steps = recipe.steps.sortedBy { it.position }.toMutableList()
            steps.add(position, Step(position = position, text = ""))
            stepKeys.insert(position, count)
            recipe.copy(
                steps = steps.mapIndexed { i, step -> step.copy(position = i) },
                media = recipe.media.followingSteps { if (it >= position) it + 1 else it },
            )
        }
    }

    /**
     * Moves the step at [from] to [to]; the steps between close up. The order is all that changes: every step keeps
     * its text, timer, references and media (FLOW-025 (2, 4)). Returns whether the step moved.
     */
    fun moveStep(from: Int, to: Int): Boolean {
        val count = current.value?.steps?.size ?: return false
        if (from !in 0 until count || to !in 0 until count || from == to) return false
        return edit { recipe ->
            val steps = recipe.steps.sortedBy { it.position }.toMutableList()
            steps.add(to, steps.removeAt(from))
            stepKeys.move(from, to, count)
            recipe.copy(
                steps = steps.mapIndexed { i, step -> step.copy(position = i) },
                media = recipe.media.followingSteps { moved(it, from, to) },
            )
        }
    }

    /**
     * The author chose the control that adds a timer to the step at [position], whose block already shows [unheld]
     * timers that hold no duration yet. A step carries up to [RecipeBounds.MAX_TIMERS] timers (DOM-024): where it
     * would show more, nothing is added and [statement] names the step until the author's next action (GUIDE-007).
     * Returns whether the timer was refused.
     */
    fun attachTimer(position: Int, unheld: Int = 0): Boolean {
        val recipe = current.value ?: return false
        if (!recipe.state.editable) return false
        val step = recipe.steps.sortedBy { it.position }.getOrNull(position) ?: return false
        val full = step.timers.size + unheld >= RecipeBounds.MAX_TIMERS
        stating.value = if (full) position else null
        return full
    }

    /**
     * Puts a timer of [hours] and whole [minutes] at [index] of the step at [position]'s timers, from 0 to their
     * number; the timers from there on keep their order after it. It takes [label], the name the author gave it before
     * its duration was cleared, if any. 0 h 0 min is no duration and adds nothing (FLOW-027 (11, 13)); nor is a sixth
     * timer added (DOM-024). Returns whether the timer was added.
     */
    fun addTimer(position: Int, index: Int, hours: Int, minutes: Int, label: String? = null): Boolean {
        if (hours !in 0..MAX_HOURS || minutes !in 0..MAX_MINUTES) return false
        val duration = hours.hours + minutes.minutes
        val step = current.value?.steps?.sortedBy { it.position }?.getOrNull(position) ?: return false
        if (!duration.isPositive()) {
            // Typed all the same: the author's next action ends a statement (GUIDE-007).
            if (editable) noteAction()
            return false
        }
        if (!step.canAddTimer || index !in 0..step.timers.size) return false
        return editStep(position) { it.copy(timers = it.timers.toMutableList().apply { add(index, Timer(duration, label?.ifEmpty { null })) }) }
    }

    /**
     * The duration of the timer at [index] of the step at [position], as [hours] and whole [minutes], from 0 to 99 and
     * 0 to 59 (FLOW-027 (12)); anything beyond is not held. The timer keeps its name. 0 h 0 min is no duration, so the
     * step then no longer carries that timer, and nothing is refused (FLOW-027 (11, 13)); its other timers stand as
     * they were. Returns whether the duration was held.
     */
    fun setTimerDuration(position: Int, index: Int, hours: Int, minutes: Int): Boolean {
        if (hours !in 0..MAX_HOURS || minutes !in 0..MAX_MINUTES) return false
        if (!editable || timerAt(position, index) == null) return false
        val duration = hours.hours + minutes.minutes
        if (!duration.isPositive()) marksWithoutTimer(position, index)
        return editStep(position) { step ->
            if (!duration.isPositive()) step.withoutTimer(index)
            else step.copy(timers = step.timers.mapIndexed { i, timer -> if (i == index) timer.copy(duration = duration) else timer })
        }
    }

    /**
     * The name the author gives the timer at [index] of the step at [position], kept in full however long (DOM-012 is
     * enforced at submission and marked on leaving the field, never while typing — FLOW-028). An empty name gives none,
     * and the name follows the step's text again (FLOW-027 (7), FLOW-016). The step's other timers stand as they were.
     */
    fun setTimerLabel(position: Int, index: Int, label: String): Boolean {
        if (timerAt(position, index) == null) return false
        return editStep(position) { step ->
            step.copy(timers = step.timers.mapIndexed { i, timer -> if (i == index) timer.copy(label = label.ifEmpty { null }) else timer })
        }
    }

    /**
     * Takes the timer at [index] off the step at [position], name and all; the step's other timers keep their order.
     * The removal is offered for undoing as [removal], which puts the timer back at its place. Returns whether the step
     * carried a timer there.
     */
    fun removeTimer(position: Int, index: Int): Boolean {
        val recipe = current.value ?: return false
        if (!recipe.state.editable || timerAt(position, index) == null) return false
        val key = stepKeys.of(position)
        // The step's timer names as they were left, for an Undo to put back.
        val marks = left.value.filterTo(mutableSetOf()) { it is Left.OfStep && it.key == key && it.timer != null }
        marksWithoutTimer(position, index)
        if (!editStep(position) { it.withoutTimer(index) }) return false
        offered.value = Removal(Removal.Kind.TIMER, position, recipe, index = index, marks = marks) {}
        return true
    }

    /**
     * The timer at [index] of the step at [position] is going: its name's mark goes with it, and a mark left on a later
     * timer's name stays with that timer, now one place up.
     */
    private fun marksWithoutTimer(position: Int, index: Int) {
        val key = stepKeys.of(position)
        left.value = left.value.mapNotNullTo(mutableSetOf()) { part ->
            val timer = (part as? Left.OfStep)?.timer
            when {
                part !is Left.OfStep || part.key != key || timer == null || timer < index -> part
                timer == index -> null
                else -> part.copy(timer = timer - 1)
            }
        }
    }

    /** The timer at [index] of the step at [position]; null where it carries none there. */
    private fun timerAt(position: Int, index: Int): Timer? =
        current.value?.steps?.sortedBy { it.position }?.getOrNull(position)?.timers?.getOrNull(index)

    private fun editStep(position: Int, change: (Step) -> Step) = edit { recipe ->
        val steps = recipe.steps.sortedBy { it.position }
        if (position !in steps.indices) return@edit recipe
        recipe.copy(steps = steps.mapIndexed { i, step -> if (i == position) change(step) else step })
    }

    private val stating = MutableStateFlow<Int?>(null)

    /** The position of the step whose GUIDE-007 statement is shown, until the author's next action; null for none. */
    val statement: StateFlow<Int?> = stating.asStateFlow()

    /**
     * The author's next action in the editor — a touch on a field or control, or a typed character — ends the
     * statement of GUIDE-007. A scroll is not one.
     */
    fun noteAction() {
        stating.value = null
    }

    /** Whether the references of GUIDE-008 were offered in this editing session. */
    private var referencesOffered = false

    /**
     * The author begins the step at [position]. Returns whether to offer references (GUIDE-008): once an editing
     * session, where it is the first step of an editable recipe holding an ingredient. No step's text is read to decide.
     */
    fun beginStep(position: Int): Boolean {
        if (position != 0 || referencesOffered) return false
        val recipe = current.value ?: return false
        if (!recipe.state.editable || recipe.ingredients.isEmpty()) return false
        referencesOffered = true
        return true
    }

    /**
     * A key for the step at [position] that stays with that step while steps are inserted, moved or deleted around
     * it, so what the screen holds for a step stays with it.
     */
    fun stepKey(position: Int): Long = stepKeys.of(position)

    /** As [stepKey], for the ingredient row at [position]. */
    fun ingredientKey(position: Int): Long = ingredientKeys.of(position)

    /** As [stepKey], for the heading at [index] of the ingredient groups. */
    fun groupKey(index: Int): Long = groupKeys.of(index)

    /** Where the heading whose [groupKey] is [key] stands now; null once it is gone. */
    fun groupIndex(key: Long): Int? = groupKeys.positionOf(key)?.takeIf { it < (current.value?.ingredientGroups?.size ?: 0) }

    /** The optional description, kept exactly as typed, line breaks included. */
    fun setDescription(description: String) = edit { it.copy(description = description) }

    /**
     * What the author typed into the serving count field. A whole number of one or more is held, and an empty field
     * holds no value; anything else is not held and changes nothing (DOM-015). Returns whether it was held.
     */
    fun setServings(typed: String): Boolean = when (val reading = ServingCountInput.read(typed)) {
        ServingCountReading.Empty -> edit { it.copy(servings = null) }
        is ServingCountReading.Count -> edit { it.copy(servings = reading.servings) }
        ServingCountReading.Refused -> false
    }

    /**
     * Appends an empty row, named in the author's own words (FLOW-022) until the dictionary offers entries. Its unit
     * starts as grams, so the row always has a unit and so a unit type (DOM-016); its quantity starts empty.
     */
    fun addIngredient() = edit { recipe -> recipe.copy(ingredients = recipe.ingredients + emptyRow(recipe.ingredients.size)) }

    /**
     * Puts an empty row, exactly like an appended one, at [position], from 0 to the number of rows; the rows from
     * there on move down, and the references to them follow (DOM-007, FLOW-025 (1, 3)). Every heading stays over the
     * rows it had (DOM-022): the new row joins the group of the row it is put above — or, [intoGroupAbove], that of the
     * row it is put below. Returns whether it was put.
     */
    fun insertIngredient(position: Int, intoGroupAbove: Boolean = false): Boolean {
        val count = current.value?.ingredients?.size ?: return false
        if (position !in 0..count) return false
        return edit { recipe ->
            val rows = recipe.ingredients.toMutableList().apply { add(position, emptyRow(position)) }
            ingredientKeys.insert(position, count)
            recipe.copy(
                ingredients = rows.mapIndexed { i, row -> row.copy(position = i) },
                steps = recipe.steps.followingIngredients { if (it >= position) it + 1 else it },
                ingredientGroups = recipe.ingredientGroups.map { group ->
                    val below = group.firstPosition > position || group.firstPosition == position && intoGroupAbove
                    if (below) group.copy(firstPosition = group.firstPosition + 1) else group
                },
            )
        }
    }

    /**
     * Moves the row at [from] to [to]; the rows between close up. Every row keeps what it holds, and every step's
     * reference still resolves to the same ingredient (DOM-007, FLOW-025 (2, 3)). The row joins the group of the row
     * whose place it takes; every other row stays in its own (DOM-022). Returns whether the row moved.
     */
    fun moveIngredient(from: Int, to: Int): Boolean {
        val recipe = current.value ?: return false
        val count = recipe.ingredients.size
        if (from !in 0 until count || to !in 0 until count || from == to) return false
        val items = recipe.listItems
        val at = items.indexOf(ListItem.Row(from))
        // Taken out, the row lands at the place of the row at [to]: after it going down, before it — under its
        // heading, if any — going up.
        return moveIngredientItem(at, items.indexOf(ListItem.Row(to)))
    }

    /**
     * Moves the item at [from] of the ingredient list as the editor shows it ([listItems]) so that it stands at [to]
     * once taken out: a row alone — past a heading, it leaves its group for the one it now stands in — or a heading with
     * every row it owns, its group as a whole, to a place a group may land at ([groupLanding]) (DOM-022, FLOW-025). No
     * move empties a group: the only row of a group stays in it, and a heading with no rows yet stays where it is.
     * Returns whether anything moved.
     */
    fun moveIngredientItem(from: Int, to: Int): Boolean {
        val recipe = current.value ?: return false
        val items = recipe.listItems
        if (from !in items.indices || from == to || !items.movable(from)) return false
        val span = blockAt(items, from)
        if (to !in 0..items.size - span) return false
        if (items[from] is ListItem.Heading && !groupLanding(items, from, span)(to)) return false
        val moved = items.movedBlock(from, span, to)
        if (recipe.arranged(moved).headings.size < recipe.ingredientGroups.size) return false
        return rearrange { moved }
    }

    /**
     * Moves the group whose heading is at [index] — heading and rows — past the next group, [down], or the one before:
     * never above the rows no group owns, nor past a heading with no rows yet. Returns whether it moved.
     */
    fun moveGroup(index: Int, down: Boolean): Boolean {
        val recipe = current.value ?: return false
        val upper = if (down) index else index - 1
        if (upper < 0 || upper + 1 >= recipe.ingredientGroups.size || recipe.groupRows(upper + 1).isEmpty()) return false
        val items = recipe.listItems
        val first = items.indexOf(ListItem.Heading(upper))
        val second = items.indexOf(ListItem.Heading(upper + 1))
        // The lower group's block is put where the upper one's began.
        return rearrange { items.movedBlock(second, blockAt(items, second), first) }
    }

    /**
     * Puts a heading with no name yet above the row at [position] — at the number of rows, after the last, where its
     * group has no rows yet — so the rows from there to the next heading are its group (DOM-022). Where a heading
     * already stands there, none is put. Returns whether it was put; the heading is not saved until it is named.
     */
    fun addGroup(position: Int): Boolean {
        val recipe = current.value ?: return false
        if (position !in 0..recipe.ingredients.size || recipe.ingredientGroups.any { it.firstPosition == position }) return false
        val index = recipe.ingredientGroups.count { it.firstPosition < position }
        return edit { edited ->
            groupKeys.insert(index, edited.ingredientGroups.size)
            edited.copy(ingredientGroups = edited.ingredientGroups.toMutableList().apply { add(index, IngredientGroup("", position)) })
        }
    }

    /**
     * The name of the group whose heading is at [index], kept as typed however long (the limit is marked on leaving the
     * field, never refused — FLOW-028).
     */
    fun renameGroup(index: Int, name: String): Boolean {
        val group = current.value?.ingredientGroups?.getOrNull(index) ?: return false
        if (group.name == name) return editable
        return edit { recipe -> recipe.copy(ingredientGroups = recipe.ingredientGroups.mapIndexed { i, it -> if (i == index) it.copy(name = name) else it }) }
    }

    /**
     * The author left the name of the heading at [index], which read [entered] when they came to it: it is held
     * trimmed. A name left blank is no name — the heading takes back [entered], or, never named, goes without a word.
     */
    fun settleGroupName(index: Int, entered: String) {
        val name = current.value?.ingredientGroups?.getOrNull(index)?.name ?: return
        val settled = name.trim().ifEmpty { entered.trim() }
        when {
            settled.isEmpty() -> edit { recipe ->
                groupKeys.remove(index, recipe.ingredientGroups.size)
                recipe.copy(ingredientGroups = recipe.ingredientGroups.filterIndexed { i, _ -> i != index })
            }
            settled != name -> renameGroup(index, settled)
        }
    }

    /**
     * Takes the heading at [index] away; its rows stay where they are and so belong to the group above, or to none
     * (DOM-022). The removal is offered for undoing as [removal].
     */
    fun removeGroup(index: Int): Boolean {
        val recipe = current.value ?: return false
        if (index !in recipe.ingredientGroups.indices) return false
        val removing = edit { edited ->
            groupKeys.remove(index, edited.ingredientGroups.size)
            edited.copy(ingredientGroups = edited.ingredientGroups.filterIndexed { i, _ -> i != index })
        }
        if (removing) offered.value = Removal(Removal.Kind.GROUP, index, recipe) {}
        return removing
    }

    /**
     * Applies the ingredient list [arranged] makes of it ([Recipe.arranged]), each row's key and each kept heading's
     * following it. Returns the headings dropped, by their former index, or null where nothing was changed.
     */
    private fun rearrange(prepare: (Recipe) -> Recipe = { it }, arranged: (Recipe) -> List<ListItem>): List<Int>? {
        var dropped: List<Int>? = null
        val changed = edit { recipe ->
            val result = prepare(recipe).arranged(arranged(recipe))
            ingredientKeys.arrange(result.rows, recipe.ingredients.size)
            groupKeys.arrange(result.headings, recipe.ingredientGroups.size)
            dropped = recipe.ingredientGroups.indices.filter { it !in result.headings }
            result.recipe
        }
        return dropped.takeIf { changed }
    }

    private fun rearrange(arranged: (Recipe) -> List<ListItem>): Boolean = rearrange({ it }, arranged) != null

    /** A new row: its unit starts as grams, so it always has a unit and so a unit type (DOM-016); no quantity yet. */
    private fun emptyRow(position: Int) = Ingredient(
        position = position, dictionaryEntry = null, freeTextName = null,
        unitType = QuantityUnit.GRAM.type, unit = QuantityUnit.GRAM, quantity = null,
    )

    /** The row's name, in the author's own words; an empty field holds none. */
    fun setIngredientName(position: Int, name: String) = editIngredient(position) { it.copy(freeTextName = name.ifEmpty { null }) }

    /** What the author typed into the row's quantity field, kept whether or not the field holds it (DOM-017 (6)). */
    fun setIngredientQuantity(position: Int, typed: String) = editIngredient(position) { it.withTypedQuantity(typed) }

    /**
     * The row's unit. A step's part of the row is kept while it still reads in the new unit — the same unit, or another
     * `exact` unit of its dimension; otherwise every part of the row becomes a reference to its whole again, which
     * [partsMadeWhole] states once. A change of the row's quantity never touches its parts.
     */
    fun setIngredientUnit(position: Int, unit: QuantityUnit): Boolean {
        var madeWhole = false
        val changed = edit { recipe ->
            if (position !in recipe.ingredients.indices) return@edit recipe
            val row = recipe.ingredients[position].withUnit(unit)
            val steps = recipe.steps.map { step ->
                step.copy(
                    ingredientReferences = step.ingredientReferences.map { reference ->
                        val share = reference.share
                        if (reference.ingredientPosition == position && share != null && !IngredientShares.fits(share, row.unit)) {
                            madeWhole = true
                            reference.copy(share = null)
                        } else {
                            reference
                        }
                    },
                )
            }
            recipe.copy(ingredients = recipe.ingredients.mapIndexed { i, it -> if (i == position) row else it }, steps = steps)
        }
        if (changed && madeWhole) wholeAgain.value = PartsMadeWhole(position, ++statements)
        return changed
    }

    private val wholeAgain = MutableStateFlow<PartsMadeWhole?>(null)
    private var statements = 0L

    /** The last change of a row's unit that made its parts whole again, until the screen has stated it; null for none. */
    val partsMadeWhole: StateFlow<PartsMadeWhole?> = wholeAgain.asStateFlow()

    /** The screen has stated [statement]. */
    fun partsMadeWholeStated(statement: PartsMadeWhole) {
        wholeAgain.compareAndSet(statement, null)
    }

    /**
     * A `piece` row's divisibility, which the author is asked for (FLOW-022 (5)): for free text, or for an entry the
     * dictionary gives no divisibility. A row naming an entry that gives one holds the entry's (FLOW-023 (11)), and is
     * not asked; returns false for it.
     */
    fun setIngredientDivisible(position: Int, divisible: Boolean): Boolean {
        val row = current.value?.ingredients?.getOrNull(position) ?: return false
        if (entryDivisible(row) != null) return false
        return editIngredient(position) { if (it.unitType == UnitType.PIECE) it.copy(divisible = divisible) else it }
    }

    /** The divisibility of each dictionary entry that gives one, by its identity; filled from what the screen reads. */
    private var entryFlags: Map<Long, Boolean> = emptyMap()

    /**
     * The dictionary's [entries] as the screen read them: from the next change on, every `piece` row naming an entry
     * that gives a divisibility holds that divisibility, so a recipe file or a fork of the recipe carries it without
     * the dictionary (FLOW-023 (11), FLOW-001 (2)). Nothing is written by being told.
     */
    fun knowEntries(entries: List<IngredientEntryEntity>) {
        entryFlags = entries.mapNotNull { entry -> entry.divisible?.let { entry.id to it } }.toMap()
    }

    /** The divisibility the entry [row] names gives; null for free text, or an entry that gives none. */
    fun entryDivisible(row: Ingredient): Boolean? = row.dictionaryEntry?.let(entryFlags::get)

    /** [recipe] with each `piece` row naming an entry that gives a divisibility holding it. */
    private fun withEntryDivisibility(recipe: Recipe): Recipe {
        if (entryFlags.isEmpty()) return recipe
        val rows = recipe.ingredients.map { row ->
            val flag = entryDivisible(row)
            if (row.unitType == UnitType.PIECE && flag != null && row.divisible != flag) row.copy(divisible = flag) else row
        }
        return if (rows == recipe.ingredients) recipe else recipe.copy(ingredients = rows)
    }

    /**
     * Deletes the row at [position]; the rows after it move up. A step's reference to the deleted row is dropped, the
     * text closed up where it stood (single spacing, [ReferenceSpacing.closeUp]), and references to the rows after it
     * follow them (DOM-007, FLOW-025 (5)). The deletion is offered for undoing as [removal].
     */
    fun removeIngredient(position: Int): Boolean {
        val recipe = current.value ?: return false
        if (position !in recipe.ingredients.indices) return false
        // Every heading stays over the rows it had; one whose only row this was goes with it (DOM-022).
        val dropped = rearrange({ edited -> edited.copy(steps = edited.steps.map { ReferenceSpacing.withoutReferencesTo(it, position) }) }) { edited ->
            edited.listItems - ListItem.Row(position)
        } ?: return false
        offered.value = Removal(Removal.Kind.INGREDIENT, position, recipe, dropped) {}
        return true
    }

    private val offered = MutableStateFlow<Removal?>(null)

    /**
     * The last row, step or timer deleted, while it can still be undone: until it is undone, ended, or the next change is
     * made — which ends it — so undoing it puts back exactly the recipe it was taken from; null for none.
     */
    val removal: StateFlow<Removal?> = offered.asStateFlow()

    /**
     * Puts back what [removal] deleted, at its place, with all it held — content, references, timer and media.
     * Returns whether it was put back: a removal already ended is not.
     */
    fun undoRemoval(removal: Removal): Boolean {
        if (!offered.compareAndSet(removal, null)) return false
        val now = current.value ?: return false
        when (removal.kind) {
            Removal.Kind.STEP -> stepKeys.insert(removal.position, now.steps.size)
            Removal.Kind.INGREDIENT -> ingredientKeys.insert(removal.position, now.ingredients.size)
            Removal.Kind.GROUP -> groupKeys.insert(removal.position, now.ingredientGroups.size)
            // A timer goes back into its step, which kept its key; the step's timer names are marked as they were.
            Removal.Kind.TIMER -> {
                val key = stepKeys.of(removal.position)
                left.value = left.value.filterNotTo(mutableSetOf()) { it is Left.OfStep && it.key == key && it.timer != null } + removal.marks
            }
        }
        removal.headings.forEachIndexed { i, index -> groupKeys.insert(index, now.ingredientGroups.size + i) }
        return edit { removal.before }
    }

    /**
     * [removal] is no longer offered for undoing — its statement went without Undo, or the editor closed: what it
     * deleted is gone for good, and the files of its media are handed over to be deleted once the deletion is in the
     * book. Nothing happens for a removal already undone or ended.
     */
    fun endRemoval(removal: Removal) {
        if (!offered.compareAndSet(removal, null)) return
        // Behind every write already queued, the deletion itself among them.
        saving.launch(start = CoroutineStart.UNDISPATCHED) { writes.withLock { removal.ended() } }
    }

    private fun editIngredient(position: Int, change: (Ingredient) -> Ingredient) = edit { recipe ->
        if (position !in recipe.ingredients.indices) return@edit recipe
        recipe.copy(ingredients = recipe.ingredients.mapIndexed { i, row -> if (i == position) change(row) else row })
    }

    // The cover (FLOW-026, MEDIA-001, MEDIA-009).

    /** The recipe's cover photograph, if it holds one. */
    val cover: Media? get() = current.value?.cover

    /**
     * Makes the photograph [mediaId] the recipe's cover. A recipe holds one cover, so it replaces any it held
     * (MEDIA-001, MEDIA-009); once the change is in the book, [written] is given the identity of the cover replaced,
     * or null, whose file is then the caller's to delete. Returns whether the cover was set: a frozen recipe takes none.
     */
    fun setCover(mediaId: String, written: suspend (replaced: String?) -> Unit = {}): Boolean {
        val replaced = cover?.mediaId
        val photo = Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)
        return edit({ recipe -> recipe.copy(media = listOf(photo) + recipe.media.filter { it.attachment != MediaAttachment.Cover }) }) {
            written(replaced)
        }
    }

    /**
     * Takes the cover off; once the change is in the book, [written] is given its identity, whose file is then the
     * caller's to delete. Returns whether the recipe held a cover and was editable.
     */
    fun removeCover(written: suspend (removed: String) -> Unit = {}): Boolean {
        val removed = cover?.mediaId ?: return false
        return edit({ recipe -> recipe.copy(media = recipe.media.filter { it.attachment != MediaAttachment.Cover }) }) {
            written(removed)
        }
    }

    // A step's item, a photograph or a clip (FLOW-026 (2), MEDIA-001, MEDIA-009).

    /** The photograph of the step at [position], if the item it carries is one. */
    fun stepPhoto(position: Int): Media? = current.value?.stepPhoto(position)

    /** The item of the step at [position], a photograph or a clip, if it carries one. */
    fun stepMedia(position: Int): Media? = current.value?.stepMedia(position)

    /**
     * Makes the photograph [mediaId] the item of the step at [position], replacing whatever item the step carried,
     * as [setStepMedia] does.
     */
    fun setStepPhoto(position: Int, mediaId: String, written: suspend (replaced: String?) -> Unit = {}): Boolean =
        setStepMedia(position, mediaId, MediaKind.PHOTO, written)

    /**
     * Makes [mediaId], of [kind], the item of the step at [position]. A step carries one item of either kind, so it
     * replaces any the step carried — a photograph a clip, a clip a photograph (MEDIA-001, MEDIA-009); once the change
     * is in the book, [written] is given the identity of the item replaced, or null, whose file is then the caller's
     * to delete (MEDIA-012). Returns whether it was set: a frozen recipe takes none, and a step that is not there
     * carries none.
     */
    fun setStepMedia(position: Int, mediaId: String, kind: MediaKind, written: suspend (replaced: String?) -> Unit = {}): Boolean {
        val recipe = current.value ?: return false
        if (position !in recipe.steps.indices) return false
        val at = MediaAttachment.ToStep(position)
        val replaced = stepMedia(position)?.mediaId
        val item = Media(mediaId, null, kind, at, MediaVisibility.PRIVATE)
        return edit({ it.copy(media = it.media.filter { m -> m.attachment != at } + item) }) {
            written(replaced)
        }
    }

    /**
     * Takes the photograph off the step at [position]; once the change is in the book, [written] is given its
     * identity, whose file is then the caller's to delete. Returns whether the step carried one and was editable.
     */
    fun removeStepPhoto(position: Int, written: suspend (removed: String) -> Unit = {}): Boolean {
        val removed = stepPhoto(position)?.mediaId ?: return false
        return edit({ it.copy(media = it.media.filter { m -> m.mediaId != removed }) }) { written(removed) }
    }

    /**
     * Takes the item, a photograph or a clip, off the step at [position]; once the change is in the book, [written] is
     * given its identity, whose file is then the caller's to delete. Returns whether the step carried one and was
     * editable.
     */
    fun removeStepMedia(position: Int, written: suspend (removed: String) -> Unit = {}): Boolean {
        val removed = stepMedia(position)?.mediaId ?: return false
        return edit({ it.copy(media = it.media.filter { m -> m.mediaId != removed }) }) { written(removed) }
    }

    // The minimum and the limits, marked on leaving a part (FLOW-028).

    /** The parts the author left outside their bound, a step's by the step's key so the mark stays with the step. */
    private val left = MutableStateFlow<Set<Left>>(emptySet())
    private val marked = MutableStateFlow<Map<RecipePart, Bound>>(emptyMap())

    /**
     * Every part the author has left below DOM-006's minimum or over a limit of DOM-012 and has not completed since, with
     * how it stands; a step's parts at the step's position now. A part never left is not here. Nothing is refused.
     */
    val marks: StateFlow<Map<RecipePart, Bound>> = marked.asStateFlow()

    /**
     * The author left [part]. It is marked if it is outside its bound, until it is completed; a step's part is held by
     * the key of the step now at its position, as [leaveStep] holds it.
     */
    fun leave(part: RecipePart) = hold(
        when (part) {
            is RecipePart.StepText -> Left.OfStep(stepKeys.of(part.position), timer = null)
            is RecipePart.TimerName -> Left.OfStep(stepKeys.of(part.position), timer = part.index)
            is RecipePart.IngredientNote -> Left.OfIngredient(ingredientKeys.of(part.position))
            is RecipePart.GroupName -> Left.OfGroup(groupKeys.of(part.index))
            else -> Left.Whole(part)
        },
    )

    /** The author left the text of the step whose [stepKey] is [key], or, given a [timer], the name of its timer at that index. */
    fun leaveStep(key: Long, timer: Int? = null) = hold(Left.OfStep(key, timer))

    private fun hold(part: Left) {
        val recipe = current.value ?: return
        if (!recipe.state.editable || bound(part, recipe) == null) return
        left.value = left.value + part
        remark(recipe)
    }

    /** What is marked once [recipe] stands as it does: a part completed loses its mark and must be left again to regain it. */
    private fun remark(recipe: Recipe) {
        left.value = left.value.filterTo(mutableSetOf()) { bound(it, recipe) != null }
        marked.value = left.value.mapNotNull { part -> part.at()?.let { at -> RecipeBounds.of(recipe, at)?.let { at to it } } }.toMap()
    }

    private fun bound(part: Left, recipe: Recipe): Bound? = part.at()?.let { RecipeBounds.of(recipe, it) }

    /** The part [this] is at now; null for a step deleted since. */
    private fun Left.at(): RecipePart? = when (this) {
        is Left.Whole -> part
        is Left.OfStep -> stepPosition(key)?.let { if (timer != null) RecipePart.TimerName(it, timer) else RecipePart.StepText(it) }
        is Left.OfIngredient -> ingredientPosition(key)?.let { RecipePart.IngredientNote(it) }
        is Left.OfGroup -> groupIndex(key)?.let { RecipePart.GroupName(it) }
    }

    internal sealed interface Left {
        data class Whole(val part: RecipePart) : Left
        /** The text of the step whose [stepKey] is [key], or, given a [timer], the name of its timer at that index. */
        data class OfStep(val key: Long, val timer: Int?) : Left
        /** The note of the ingredient row whose [ingredientKey] is [key], so the mark stays with the row. */
        data class OfIngredient(val key: Long) : Left
        /** The name of the heading whose [groupKey] is [key]. */
        data class OfGroup(val key: Long) : Left
    }

    /** Where the ingredient row whose [ingredientKey] is [key] stands now; null once it is deleted. */
    private fun ingredientPosition(key: Long): Int? =
        ingredientKeys.positionOf(key)?.takeIf { it < (current.value?.ingredients?.size ?: 0) }

    /** Where the step whose [stepKey] is [key] stands now; null once it is deleted. */
    fun stepPosition(key: Long): Int? = stepKeys.positionOf(key)?.takeIf { it < (current.value?.steps?.size ?: 0) }

    /**
     * Applies [change] and writes the result, then runs [then]; a recipe in a frozen state is left as it is, and
     * [then] is not run.
     */
    private fun edit(change: (Recipe) -> Recipe, then: suspend () -> Unit): Boolean {
        val recipe = current.value ?: return false
        if (!recipe.state.editable) return false
        noteAction()
        // Any change ends the removal still offered: undoing it would put back a recipe older than this change.
        offered.value?.let(::endRemoval)
        val next = recipe.edited(clock()) { withEntryDivisibility(change(it)) }
        current.value = next
        if (left.value.isNotEmpty() || marked.value.isNotEmpty()) remark(next)
        // Queued on a fair lock before this returns, so the book receives the changes in the order made — started
        // undispatched, since a dispatcher may run a launch made inside another coroutine of its own after a later one.
        saving.launch(start = CoroutineStart.UNDISPATCHED) {
            // A heading still being named is not saved (DOM-022).
            writes.withLock { recipes.save(next.withNamedGroups()) }
            then()
        }
        return true
    }

    /** Returns once every change made so far is in the book: queued behind the writes already asked for. */
    suspend fun written() {
        saving.async(start = CoroutineStart.UNDISPATCHED) { writes.withLock {} }.await()
    }

    /** Applies [change] and writes the result; a recipe in a frozen state is left as it is. */
    private fun edit(change: (Recipe) -> Recipe): Boolean = edit(change) {}

    /** The row's ingredient as the dictionary entry [entryId], with no free-text name beside it (DOM-009). */
    fun setIngredientEntry(position: Int, entryId: Long) =
        editIngredient(position) { it.copy(dictionaryEntry = entryId, qualifier = null, freeTextName = null) }

    /**
     * The row's ingredient in the author's own words, replacing any entry it held, and the entry's qualifier with it
     * (DOM-009); empty holds neither. A divisibility the entry gave goes with the entry, so the author is asked again.
     */
    fun setIngredientFreeText(position: Int, name: String) = editIngredient(position) { row ->
        val divisible = if (entryDivisible(row) != null) null else row.divisible
        row.copy(dictionaryEntry = null, qualifier = null, freeTextName = name.ifEmpty { null }, divisible = divisible)
    }

    /**
     * The words the author typed after the label of the row's entry, which narrow it ("крылышки острые"): kept beside
     * the entry without the spaces and commas that separate them from the label; none holds no qualifier (DOM-009).
     * A row in the author's own words takes none; returns false. The qualifier the row already holds changes nothing.
     */
    fun setIngredientQualifier(position: Int, qualifier: String?): Boolean {
        val row = current.value?.ingredients?.getOrNull(position) ?: return false
        if (row.dictionaryEntry == null) return false
        val held = qualifier?.trim { it.isWhitespace() || it == ',' }?.ifEmpty { null }
        if (held == row.qualifier) return true
        return editIngredient(position) { it.copy(qualifier = held) }
    }

    /**
     * How the row's ingredient is prepared, as the author [typed] it (DOM-023): any row, an entry or free text, takes
     * one. It is held trimmed, and blank holds none ([Ingredient.noteOf]); it is kept in full however long — the limit
     * is marked on leaving the field, never refused (FLOW-028). The note the row already holds changes nothing.
     */
    fun setIngredientNote(position: Int, typed: String): Boolean {
        val row = current.value?.ingredients?.getOrNull(position) ?: return false
        val note = Ingredient.noteOf(typed)
        if (note == row.note) return editable
        return editIngredient(position) { it.copy(note = note) }
    }

    companion object {
        /** Where the row at [position] stands once the row at [from] has moved to [to]. */
        private fun moved(position: Int, from: Int, to: Int): Int = when {
            position == from -> to
            from < to && position in from + 1..to -> position - 1
            to < from && position in to until from -> position + 1
            else -> position
        }

        /** Every reference to the ingredient at a position now at [moved] of it; the rest of each step is kept. */
        private fun List<Step>.followingIngredients(moved: (Int) -> Int): List<Step> = map { step ->
            step.copy(ingredientReferences = step.ingredientReferences.map { it.copy(ingredientPosition = moved(it.ingredientPosition)) })
        }

        /** Every medium attached to the step at a position now attached at [moved] of it. */
        private fun List<Media>.followingSteps(moved: (Int) -> Int): List<Media> = map { medium ->
            val attachment = medium.attachment
            if (attachment is MediaAttachment.ToStep) medium.copy(attachment = MediaAttachment.ToStep(moved(attachment.stepPosition))) else medium
        }

        /** The longest timer a recipe carries is 99 h 59 min (FLOW-027 (14)). */
        const val MAX_HOURS = 99
        const val MAX_MINUTES = 59

        /** One queue of writes for every editor, so that an editor opened again reads what the last one wrote. */
        private val writes = Mutex()
    }
}

/**
 * A step, an ingredient row, a group's heading or a step's timer ([kind]) deleted from [position] — a timer from the
 * step at [position], at [index] of its timers — offered for undoing; [before] is the recipe it was deleted from,
 * [headings] the indices of the headings that went with a row — its group left empty — and [ended] what is done once it
 * is gone for good.
 */
class Removal internal constructor(
    val kind: Kind,
    val position: Int,
    internal val before: Recipe,
    internal val headings: List<Int> = emptyList(),
    val index: Int = 0,
    /** For a timer, the marks its step's timer names held when it was removed. */
    internal val marks: Set<RecipeEditor.Left> = emptySet(),
    internal val ended: suspend () -> Unit,
) {
    enum class Kind { STEP, INGREDIENT, GROUP, TIMER }

    /** Whether the timer removed was one of several on its step, so that which one is said. */
    val ofSeveral: Boolean
        get() = kind == Kind.TIMER && (before.steps.sortedBy { it.position }.getOrNull(position)?.timers?.size ?: 0) > 1
}

/** A change of the unit of the row at [ingredientPosition] made its parts whole again; [serial] tells two apart. */
data class PartsMadeWhole(val ingredientPosition: Int, val serial: Long)

/**
 * Keys for the rows of one list, each staying with its row while rows are inserted, moved or deleted around it. A
 * row is given its key when it is first asked for; every change first gives one to each of the [count] rows.
 */
class RowKeys private constructor(private val keys: MutableList<Long>, private var next: Long) {
    constructor() : this(mutableListOf(), 0L)

    fun of(position: Int): Long {
        fill(position + 1)
        return keys[position]
    }

    /** Where the row whose key is [key] stands; null for a key no row holds. */
    fun positionOf(key: Long): Int? = keys.indexOf(key).takeIf { it >= 0 }

    fun insert(position: Int, count: Int) {
        fill(count)
        keys.add(position, next++)
    }

    fun remove(position: Int, count: Int) {
        fill(count)
        keys.removeAt(position)
    }

    fun move(from: Int, to: Int, count: Int) {
        fill(count)
        keys.add(to, keys.removeAt(from))
    }

    /** The [count] rows now stand in the order [order] gives by their former positions; a row left out of it is gone. */
    fun arrange(order: List<Int>, count: Int) {
        fill(count)
        val was = keys.toList()
        keys.clear()
        order.mapTo(keys) { was[it] }
    }

    private fun fill(count: Int) {
        while (keys.size < count) keys += next++
    }

    companion object {
        /** Saved as the next key to give, then the keys row by row. */
        val Saver: Saver<RowKeys, LongArray> = Saver(
            save = { longArrayOf(it.next, *it.keys.toLongArray()) },
            restore = { RowKeys(it.drop(1).toMutableList(), it.first()) },
        )
    }
}
