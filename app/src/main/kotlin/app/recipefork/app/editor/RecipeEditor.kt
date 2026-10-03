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
     * Once the change is in the book, [written] is given the identities of the media deleted with the step, whose
     * files are then the caller's to delete.
     */
    fun removeStep(position: Int, written: suspend (removed: List<String>) -> Unit = {}): Boolean {
        val recipe = current.value ?: return false
        val removed = recipe.media.filter { it.attachment == MediaAttachment.ToStep(position) }.map { it.mediaId }
        return edit({ edited ->
            val steps = edited.steps.sortedBy { it.position }
            if (position !in steps.indices) return@edit edited
            stepKeys.remove(position, steps.size)
            edited.copy(
                steps = steps.filterIndexed { i, _ -> i != position }.mapIndexed { i, step -> step.copy(position = i) },
                media = edited.media
                    .filter { (it.attachment as? MediaAttachment.ToStep)?.stepPosition != position }
                    .followingSteps { if (it > position) it - 1 else it },
            )
        }) { if (removed.isNotEmpty()) written(removed) }
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
     * The author chose the control that attaches a timer to the step at [position]. A step carries one timer
     * (DOM-013): where it already carries one, nothing is created and [statement] names the step until the author's
     * next action (GUIDE-007). Returns whether the step already carried a timer.
     */
    fun attachTimer(position: Int): Boolean {
        val recipe = current.value ?: return false
        if (!recipe.state.editable) return false
        val carries = recipe.steps.sortedBy { it.position }.getOrNull(position)?.timer != null
        stating.value = if (carries) position else null
        return carries
    }

    /**
     * The step's timer as [hours] and whole [minutes], from 0 to 99 and 0 to 59 (FLOW-027 (12)); anything beyond is
     * not held. 0 h 0 min is no duration, so the step then carries no timer, and nothing is refused (FLOW-027 (11, 13)).
     * A timer that stays keeps its name; one newly attached takes [label], the name the author gave it before its
     * duration was cleared, if any. Returns whether the duration was held.
     */
    fun setTimerDuration(position: Int, hours: Int, minutes: Int, label: String? = null): Boolean {
        if (hours !in 0..MAX_HOURS || minutes !in 0..MAX_MINUTES) return false
        val duration = hours.hours + minutes.minutes
        return editStep(position) { step ->
            step.copy(timer = if (duration.isPositive()) Timer(duration, step.timer?.label ?: label?.ifEmpty { null }) else null)
        }
    }

    /**
     * The name the author gives the step's timer, kept in full however long (DOM-012 is enforced at submission and
     * marked on leaving the field, never while typing — FLOW-028). An empty name gives none, and the name follows the step's text again (FLOW-027 (7), FLOW-016).
     */
    fun setTimerLabel(position: Int, label: String): Boolean {
        if (current.value?.steps?.sortedBy { it.position }?.getOrNull(position)?.timer == null) return false
        return editStep(position) { step -> step.copy(timer = step.timer?.copy(label = label.ifEmpty { null })) }
    }

    /**
     * Takes the step's timer off, name and all: attached again, it starts from the name derived from the step.
     * Returns whether the step carried a timer.
     */
    fun removeTimer(position: Int): Boolean {
        if (current.value?.steps?.sortedBy { it.position }?.getOrNull(position)?.timer == null) return false
        return editStep(position) { it.copy(timer = null) }
    }

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
     * there on move down, and the references to them follow (DOM-007, FLOW-025 (1, 3)). Returns whether it was put.
     */
    fun insertIngredient(position: Int): Boolean {
        val count = current.value?.ingredients?.size ?: return false
        if (position !in 0..count) return false
        return edit { recipe ->
            val rows = recipe.ingredients.toMutableList().apply { add(position, emptyRow(position)) }
            ingredientKeys.insert(position, count)
            recipe.copy(
                ingredients = rows.mapIndexed { i, row -> row.copy(position = i) },
                steps = recipe.steps.followingIngredients { if (it >= position) it + 1 else it },
            )
        }
    }

    /**
     * Moves the row at [from] to [to]; the rows between close up. Every row keeps what it holds, and every step's
     * reference still resolves to the same ingredient (DOM-007, FLOW-025 (2, 3)). Returns whether the row moved.
     */
    fun moveIngredient(from: Int, to: Int): Boolean {
        val count = current.value?.ingredients?.size ?: return false
        if (from !in 0 until count || to !in 0 until count || from == to) return false
        return edit { recipe ->
            val rows = recipe.ingredients.toMutableList().apply { add(to, removeAt(from)) }
            ingredientKeys.move(from, to, count)
            recipe.copy(
                ingredients = rows.mapIndexed { i, row -> row.copy(position = i) },
                steps = recipe.steps.followingIngredients { moved(it, from, to) },
            )
        }
    }

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
     * follow them (DOM-007, FLOW-025 (5)).
     */
    fun removeIngredient(position: Int) = edit { recipe ->
        if (position !in recipe.ingredients.indices) return@edit recipe
        ingredientKeys.remove(position, recipe.ingredients.size)
        val rows = recipe.ingredients.filterIndexed { i, _ -> i != position }.mapIndexed { i, row -> row.copy(position = i) }
        val steps = recipe.steps.map { step ->
            val left = ReferenceSpacing.withoutReferencesTo(step, position)
            left.copy(
                ingredientReferences = left.ingredientReferences.map {
                    if (it.ingredientPosition > position) it.copy(ingredientPosition = it.ingredientPosition - 1) else it
                },
            )
        }
        recipe.copy(ingredients = rows, steps = steps)
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
            is RecipePart.StepText -> Left.OfStep(stepKeys.of(part.position), timerName = false)
            is RecipePart.TimerName -> Left.OfStep(stepKeys.of(part.position), timerName = true)
            else -> Left.Whole(part)
        },
    )

    /** The author left the text of the step whose [stepKey] is [key], or, [timerName], the name of its timer. */
    fun leaveStep(key: Long, timerName: Boolean = false) = hold(Left.OfStep(key, timerName))

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
        is Left.OfStep -> stepPosition(key)?.let { if (timerName) RecipePart.TimerName(it) else RecipePart.StepText(it) }
    }

    private sealed interface Left {
        data class Whole(val part: RecipePart) : Left
        data class OfStep(val key: Long, val timerName: Boolean) : Left
    }

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
        val next = recipe.edited(clock()) { withEntryDivisibility(change(it)) }
        current.value = next
        if (left.value.isNotEmpty()) remark(next)
        // Launched in order and queued on a fair lock, so the book receives the changes in the order made.
        saving.launch {
            writes.withLock { recipes.save(next) }
            then()
        }
        return true
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
