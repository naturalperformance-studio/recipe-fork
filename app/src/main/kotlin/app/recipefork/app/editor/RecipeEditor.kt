package app.recipefork.app.editor

import android.content.res.Configuration
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.dictionary.IngredientSource
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** What the editor needs beyond the recipes of the active book: the book itself, the dictionaries and a clock. */
class EditorEnvironment(
    val book: ActiveBook,
    val tags: TagSource,
    /** The ingredient dictionary the ingredient field offers from (FLOW-057); the catalog that holds the tags holds it. */
    val ingredients: IngredientSource = tags as? IngredientSource ?: IngredientSource { emptyList() },
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
     * Deletes the step at [position], with its own timer and references; the steps after it move up and keep theirs,
     * which point at ingredients, never at steps, so each still resolves to the same ingredient (DOM-007).
     */
    fun removeStep(position: Int) = edit { recipe ->
        val steps = recipe.steps.sortedBy { it.position }
        if (position !in steps.indices) return@edit recipe
        if (position < keys.size) keys.removeAt(position)
        recipe.copy(steps = steps.filterIndexed { i, _ -> i != position }.mapIndexed { i, step -> step.copy(position = i) })
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
     * The name the author gives the step's timer, kept in full however long (DOM-012 is enforced at submission, not
     * while typing). An empty name gives none, and the name follows the step's text again (FLOW-027 (7), FLOW-016).
     */
    fun setTimerLabel(position: Int, label: String): Boolean {
        if (current.value?.steps?.sortedBy { it.position }?.getOrNull(position)?.timer == null) return false
        return editStep(position) { step -> step.copy(timer = step.timer?.copy(label = label.ifEmpty { null })) }
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

    /**
     * A key for the step at [position] that stays with that step while the steps before it are deleted, so what the
     * screen holds for a step stays with it.
     */
    fun stepKey(position: Int): Long {
        while (keys.size <= position) keys += nextKey++
        return keys[position]
    }

    private val keys = mutableListOf<Long>()
    private var nextKey = 0L

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
    fun addIngredient() = edit { recipe ->
        val row = Ingredient(
            position = recipe.ingredients.size, dictionaryEntry = null, freeTextName = null,
            unitType = QuantityUnit.GRAM.type, unit = QuantityUnit.GRAM, quantity = null,
        )
        recipe.copy(ingredients = recipe.ingredients + row)
    }

    /** The row's name, in the author's own words; an empty field holds none. */
    fun setIngredientName(position: Int, name: String) = editIngredient(position) { it.copy(freeTextName = name.ifEmpty { null }) }

    /** What the author typed into the row's quantity field, kept whether or not the field holds it (DOM-017 (6)). */
    fun setIngredientQuantity(position: Int, typed: String) = editIngredient(position) { it.withTypedQuantity(typed) }

    fun setIngredientUnit(position: Int, unit: QuantityUnit) = editIngredient(position) { it.withUnit(unit) }

    /** A `piece` row's divisibility, which the author is asked for (FLOW-022 (5)). */
    fun setIngredientDivisible(position: Int, divisible: Boolean) = editIngredient(position) { row ->
        if (row.unitType == UnitType.PIECE) row.copy(divisible = divisible) else row
    }

    /**
     * Deletes the row at [position]; the rows after it move up. A step's reference to the deleted row is dropped,
     * and references to the rows after it follow them (DOM-007, FLOW-025 (5)).
     */
    fun removeIngredient(position: Int) = edit { recipe ->
        if (position !in recipe.ingredients.indices) return@edit recipe
        val rows = recipe.ingredients.filterIndexed { i, _ -> i != position }.mapIndexed { i, row -> row.copy(position = i) }
        val steps = recipe.steps.map { step ->
            step.copy(
                ingredientReferences = step.ingredientReferences.filter { it.ingredientPosition != position }.map {
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

    /** Applies [change] and writes the result; a recipe in a frozen state is left as it is. */
    private fun edit(change: (Recipe) -> Recipe): Boolean {
        val recipe = current.value ?: return false
        if (!recipe.state.editable) return false
        noteAction()
        val next = recipe.edited(clock(), change)
        current.value = next
        // Launched in order and queued on a fair lock, so the book receives the changes in the order made.
        saving.launch { writes.withLock { recipes.save(next) } }
        return true
    }

    /** The row's ingredient as the dictionary entry [entryId], with no free-text name beside it (DOM-009). */
    fun setIngredientEntry(position: Int, entryId: Long) =
        editIngredient(position) { it.copy(dictionaryEntry = entryId, freeTextName = null) }

    /** The row's ingredient in the author's own words, replacing any entry it held (DOM-009); empty holds neither. */
    fun setIngredientFreeText(position: Int, name: String) =
        editIngredient(position) { it.copy(dictionaryEntry = null, qualifier = null, freeTextName = name.ifEmpty { null }) }

    companion object {
        /** The longest timer a recipe carries is 99 h 59 min (FLOW-027 (14)). */
        const val MAX_HOURS = 99
        const val MAX_MINUTES = 59

        /** One queue of writes for every editor, so that an editor opened again reads what the last one wrote. */
        private val writes = Mutex()
    }
}
