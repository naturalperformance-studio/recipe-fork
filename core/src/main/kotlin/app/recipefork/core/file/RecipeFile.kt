package app.recipefork.core.file

import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger
import kotlin.time.Duration.Companion.minutes

/** A recipe file's document cannot be read: it is not one, is damaged, or is of a later format. */
open class UnreadableRecipeFile(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The document is of a format later than this application reads: written by a later version of it (FLOW-064 (5)). */
class LaterRecipeFile(val formatVersion: Int) :
    UnreadableRecipeFile("format $formatVersion is later than ${RecipeFile.FORMAT_VERSION}")

/**
 * The recipe file's shape and its document, `recipe.json` (decisions/20260928-recipe-file-is-a-zip.md).
 *
 * [encode] writes what [RecipeFileContent] holds; [decode] reads it back, refusing a document of a later format
 * than [FORMAT_VERSION] or one that does not parse. It checks the document's form only: whether its content passes
 * the rules a sending must pass is the importer's to check.
 */
object RecipeFile {
    /**
     * The format this application writes, and the latest it reads. Format 2 added ingredient groups and notes
     * (DOM-022, DOM-023); a document of format 1 reads as one with neither. Format 3 gives a step a list of `timers`
     * (DOM-024) where formats 1 and 2 gave it at most one `timer`, which reads as a step with that one timer. Format 4
     * adds the recipe's `equipment`, each item its text; a document of formats 1 to 3 reads as one with none. Format 5
     * names each item as an ingredient is named: a dictionary entry, with its labels, and a qualifier, or free text.
     * Format 6 adds a step's `note`, the author's own; a document of formats 1 to 5 reads as one with none. Format 7
     * adds the US units `ounce`, `pound` and `fluid_ounce` (v0.35), and is written only by a document holding one, in
     * a row or a part: any other is written as format 6 ([LAST_FORMAT_WITHOUT_US_UNITS]), so that an application
     * reading at most format 6 still opens it, and refuses one with US units as later rather than damaged (FLOW-064).
     * Format 8 adds the recipe's `source_text`, the text it was made from, exactly as pasted, and is written only by a
     * document holding one; any other is written as format 7 or 6, as before. A document of formats 1 to 7 reads as one
     * with none. A source text longer than [RecipeBounds.MAX_SOURCE_TEXT] is read cut at that length. Format 9 adds the
     * recipe's `finished_weight_grams`, what the finished dish weighs in whole grams at its servings, and is written only
     * by a document holding one; any other is written as format 8, 7 or 6, as before. A document of formats 1 to 8 reads
     * as one with none.
     */
    const val FORMAT_VERSION = 9

    /** The last format that wrote no `source_text`. */
    private const val LAST_FORMAT_WITHOUT_SOURCE_TEXT = 7

    /** The last format that wrote no `finished_weight_grams`. */
    private const val LAST_FORMAT_WITHOUT_FINISHED_WEIGHT = 8

    /** The format written for a document holding no US unit: the last format before them. */
    const val LAST_FORMAT_WITHOUT_US_UNITS = 6

    /** The last format that wrote a step's one `timer` rather than its list of `timers`. */
    private const val LAST_ONE_TIMER_FORMAT = 2

    /** The last format that wrote no `equipment`. */
    private const val LAST_FORMAT_WITHOUT_EQUIPMENT = 3

    /** The last format that wrote each item of `equipment` as its text alone. */
    private const val LAST_TEXT_EQUIPMENT_FORMAT = 4

    /** The last format that wrote no step's `note`. */
    private const val LAST_FORMAT_WITHOUT_STEP_NOTES = 5
    const val EXTENSION = "recipefork"
    const val MIME_TYPE = "application/vnd.recipefork.recipe+zip"
    /** The document's entry in the archive. */
    const val DOCUMENT = "recipe.json"
    /** The directory of the archive's media entries. */
    const val MEDIA_DIRECTORY = "media/"

    /** Longer numbers are no quantity a kitchen writes, and are not parsed at all. */
    private const val MAX_DIGITS = 40

    private val json = Json { prettyPrint = true; explicitNulls = false; encodeDefaults = true }
    private val versionOnly = Json { ignoreUnknownKeys = true }

    fun encode(content: RecipeFileContent): String = json.encodeToString(RecipeDocument.serializer(), content.toDocument())

    /**
     * The content [text] holds. An item of equipment a format before 5 wrote as its text names the entry [entryOf] finds
     * for that text, trimmed, with no qualifier, and is free text where it finds none.
     */
    fun decode(text: String, entryOf: (String) -> Long? = { null }): RecipeFileContent {
        val version = try {
            versionOnly.decodeFromString(DocumentVersion.serializer(), text).formatVersion
        } catch (e: SerializationException) {
            throw UnreadableRecipeFile("no format version", e)
        } catch (e: IllegalArgumentException) {
            throw UnreadableRecipeFile("no format version", e)
        }
        if (version > FORMAT_VERSION) throw LaterRecipeFile(version)
        if (version < 1) throw UnreadableRecipeFile("format $version does not exist")
        return try {
            json.decodeFromString(RecipeDocument.serializer(), text)
                .also { it.checkTimerShape() }
                .also { it.checkEquipmentShape() }
                .also { it.checkStepNoteShape() }
                .also { it.checkSourceTextShape() }
                .also { it.checkFinishedWeightShape() }
                .toContent(entryOf)
        } catch (e: UnreadableRecipeFile) {
            throw e
        } catch (e: IllegalArgumentException) {
            // SerializationException is one, and so is every value the mapping below does not know.
            throw UnreadableRecipeFile("the document does not parse", e)
        } catch (e: ArithmeticException) {
            throw UnreadableRecipeFile("the document does not parse", e)
        }
    }

    /** Whether a row or a part of [this] is in a US unit, which only format 7 names. */
    private fun RecipeFileContent.holdsUsUnit(): Boolean =
        ingredients.any { it.unit?.system == UnitSystem.US } ||
            steps.any { step -> step.ingredientReferences.any { it.share?.unit?.system == UnitSystem.US } }

    private fun RecipeFileContent.toDocument() = RecipeDocument(
        formatVersion = when {
            finishedWeightGrams != null -> FORMAT_VERSION
            sourceText != null -> LAST_FORMAT_WITHOUT_FINISHED_WEIGHT
            holdsUsUnit() -> LAST_FORMAT_WITHOUT_SOURCE_TEXT
            else -> LAST_FORMAT_WITHOUT_US_UNITS
        },
        recipe = DocumentRecipe(
            id = id.value,
            title = title,
            description = description,
            language = language?.let(::languageName),
            tags = tags.sorted(),
            servings = servings,
            equipment = equipment.map { item ->
                val labels = item.dictionaryEntry?.let(equipmentLabels::get)
                json.encodeToJsonElement(
                    DocumentEquipment.serializer(),
                    DocumentEquipment(item.dictionaryEntry, labels?.let { DocumentLabels(it.ru, it.en) }, item.qualifier, item.freeTextName),
                )
            },
            ingredients = ingredients.map { it.toDocument() },
            ingredientGroups = ingredientGroups.map { DocumentGroup(it.name, it.firstPosition) },
            steps = steps.map { it.toDocument() },
            sourceText = sourceText,
            finishedWeightGrams = finishedWeightGrams,
        ),
        author = author?.let(::DocumentAuthor),
        parent = parent?.let { DocumentParent(it.id.value, it.title, it.nickname) },
        media = media.map {
            DocumentMedia(it.mediaId, Names.kind.nameOf(it.kind), (it.attachment as? MediaAttachment.ToStep)?.stepPosition, it.entry)
        },
    )

    private fun Ingredient.toDocument() = DocumentIngredient(
        position = position,
        dictionaryEntry = dictionaryEntry,
        freeTextName = freeTextName,
        qualifier = qualifier,
        unitType = Names.unitType.nameOf(unitType),
        unit = unit?.let(Names.unit::nameOf),
        quantity = quantity?.let(::fractionOf),
        pieceWeight = pieceWeight?.let(::fractionOf),
        divisible = divisible,
        enteredQuantity = enteredQuantity,
        note = note,
    )

    private fun Step.toDocument() = DocumentStep(
        position = position,
        text = text,
        references = ingredientReferences.map { reference ->
            DocumentReference(
                reference.ingredientPosition,
                reference.textOffset,
                reference.share?.let { DocumentShare(fractionOf(it.amount), Names.unit.nameOf(it.unit)) },
            )
        },
        timers = timers.map { DocumentTimer(it.duration.inWholeMinutes, it.label) },
        note = note,
    )

    /** A step's timers stand as its format writes them: one `timer` up to format 2, a list of `timers` since. */
    private fun RecipeDocument.checkTimerShape() {
        val oneTimer = formatVersion <= LAST_ONE_TIMER_FORMAT
        recipe.steps.forEach { step ->
            if (if (oneTimer) step.timers.isNotEmpty() else step.timer != null) {
                throw UnreadableRecipeFile("step ${step.position} holds a timer as format $formatVersion does not write one")
            }
        }
    }

    /** A format before 4 wrote no equipment: a document of it that holds some is no document of its format. */
    private fun RecipeDocument.checkEquipmentShape() {
        if (formatVersion <= LAST_FORMAT_WITHOUT_EQUIPMENT && recipe.equipment.isNotEmpty()) {
            throw UnreadableRecipeFile("format $formatVersion holds equipment, which it does not write")
        }
    }

    /** A format before 6 wrote no step's note: a document of it that holds one is no document of its format. */
    private fun RecipeDocument.checkStepNoteShape() {
        if (formatVersion <= LAST_FORMAT_WITHOUT_STEP_NOTES) {
            recipe.steps.firstOrNull { it.note != null }?.let { step ->
                throw UnreadableRecipeFile("step ${step.position} holds a note, which format $formatVersion does not write")
            }
        }
    }

    /** A format before 8 wrote no source text: a document of it that holds one is no document of its format. */
    private fun RecipeDocument.checkSourceTextShape() {
        if (formatVersion <= LAST_FORMAT_WITHOUT_SOURCE_TEXT && recipe.sourceText != null) {
            throw UnreadableRecipeFile("format $formatVersion holds a source text, which it does not write")
        }
    }

    /** A format before 9 wrote no finished weight: a document of it that holds one is no document of its format. */
    private fun RecipeDocument.checkFinishedWeightShape() {
        if (formatVersion <= LAST_FORMAT_WITHOUT_FINISHED_WEIGHT && recipe.finishedWeightGrams != null) {
            throw UnreadableRecipeFile("format $formatVersion holds a finished weight, which it does not write")
        }
    }

    /** The document's equipment as its format writes it, with the labels format 5 gives its entries. */
    private fun RecipeDocument.equipmentOf(entryOf: (String) -> Long?): Pair<List<EquipmentItem>, Map<Long, EntryLabels>> {
        if (formatVersion <= LAST_TEXT_EQUIPMENT_FORMAT) {
            // Each item is read trimmed, as a note is and as the editor holds one; an item blank or too long once trimmed
            // is the check's to refuse.
            val items = recipe.equipment.map { element ->
                val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw UnreadableRecipeFile("format $formatVersion holds an item of equipment that is no text")
                text.trim().let { trimmed -> entryOf(trimmed)?.let { EquipmentItem.entry(it) } ?: EquipmentItem.free(trimmed) }
            }
            return items to emptyMap()
        }
        val items = recipe.equipment.map { json.decodeFromJsonElement(DocumentEquipment.serializer(), it) }
        val labels = items.mapNotNull { item -> item.dictionaryEntry?.let { id -> item.labels?.let { id to EntryLabels(it.ru, it.en) } } }
            .distinctBy { it.first }.toMap()
        // The free text and the qualifier are read trimmed; a qualifier of nothing but spaces is none.
        return items.map { EquipmentItem(it.dictionaryEntry, it.qualifier?.trim()?.takeIf(String::isNotEmpty), it.freeTextName?.trim()) } to labels
    }

    private fun RecipeDocument.toContent(entryOf: (String) -> Long?): RecipeFileContent {
        val (equipment, equipmentLabels) = equipmentOf(entryOf)
        return toContent(equipment, equipmentLabels)
    }

    private fun RecipeDocument.toContent(equipment: List<EquipmentItem>, equipmentLabels: Map<Long, EntryLabels>) = RecipeFileContent(
        id = RecipeId(recipe.id),
        title = recipe.title,
        description = recipe.description,
        tags = recipe.tags.toSet(),
        language = recipe.language?.let(::languageOf),
        servings = recipe.servings,
        equipment = equipment,
        equipmentLabels = equipmentLabels,
        ingredients = recipe.ingredients.map { it.toIngredient() },
        ingredientGroups = recipe.ingredientGroups.map { IngredientGroup(it.name, it.firstIngredient) },
        steps = recipe.steps.map { it.toStep() },
        // Kept exactly as written, cut at its limit where it is longer; an empty one is none.
        sourceText = Recipe.sourceTextOf(recipe.sourceText),
        finishedWeightGrams = recipe.finishedWeightGrams,
        author = author?.nickname,
        parent = parent?.let { FileParent(RecipeId(it.id), it.title, it.nickname) },
        media = media.map {
            FileMedia(
                it.id, Names.kind.valueOf(it.kind),
                it.step?.let(MediaAttachment::ToStep) ?: MediaAttachment.Cover, it.entry,
            )
        },
    )

    private fun DocumentIngredient.toIngredient() = Ingredient(
        position = position,
        dictionaryEntry = dictionaryEntry,
        qualifier = qualifier,
        freeTextName = freeTextName,
        unitType = Names.unitType.valueOf(unitType),
        unit = unit?.let(Names.unit::valueOf),
        quantity = quantity?.let(::rationalOf),
        pieceWeight = pieceWeight?.let(::rationalOf),
        divisible = divisible,
        enteredQuantity = enteredQuantity,
        // A note is read as the editor takes one: trimmed, and none when blank (DOM-023).
        note = Ingredient.noteOf(note),
    )

    private fun DocumentStep.toStep() = Step(
        position = position,
        text = text,
        ingredientReferences = references.map { reference ->
            IngredientReference(
                reference.ingredientPosition,
                reference.textOffset,
                reference.share?.let { IngredientShare(rationalOf(it.quantity), Names.unit.valueOf(it.unit)) },
            )
        },
        // Formats 1 and 2 held one timer at most; the shape was checked before.
        timers = (timers + listOfNotNull(timer)).map { Timer(it.minutes.minutes, it.label) },
        // A note is read as the editor takes one: trimmed, and none when blank.
        note = Step.noteOf(note),
    )

    private fun languageName(language: RecipeLanguage) = Names.language.nameOf(language)

    private fun languageOf(name: String) = Names.language.valueOf(name)

    /** `n` for a whole number, `n/d` otherwise, in lowest terms: written here, never by [Rational.toString]. */
    private fun fractionOf(value: Rational): String =
        if (value.denominator == BigInteger.ONE) "${value.numerator}" else "${value.numerator}/${value.denominator}"

    /**
     * The file's names for the domain's enumerations, spelt out here so that renaming a Kotlin constant cannot change
     * the format: a new constant fails to compile until it is given a name.
     */
    internal object Names {
        val unitType = nameTable<UnitType> {
            when (it) {
                UnitType.EXACT -> "exact"
                UnitType.PIECE -> "piece"
                UnitType.HOUSEHOLD -> "household"
                UnitType.TASTE_MEASURED -> "taste_measured"
                UnitType.TASTE_UNMEASURED -> "taste_unmeasured"
            }
        }
        val unit = nameTable<QuantityUnit> {
            when (it) {
                QuantityUnit.GRAM -> "gram"
                QuantityUnit.KILOGRAM -> "kilogram"
                QuantityUnit.TONNE -> "tonne"
                QuantityUnit.MILLILITRE -> "millilitre"
                QuantityUnit.LITRE -> "litre"
                QuantityUnit.CUBIC_METRE -> "cubic_metre"
                QuantityUnit.OUNCE -> "ounce"
                QuantityUnit.POUND -> "pound"
                QuantityUnit.FLUID_OUNCE -> "fluid_ounce"
                QuantityUnit.PIECE -> "piece"
                QuantityUnit.TEASPOON -> "teaspoon"
                QuantityUnit.TABLESPOON -> "tablespoon"
                QuantityUnit.CUP -> "cup"
                QuantityUnit.PINCH -> "pinch"
                QuantityUnit.TO_TASTE -> "to_taste"
            }
        }
        val language = nameTable<RecipeLanguage> {
            when (it) {
                RecipeLanguage.RU -> "ru"
                RecipeLanguage.EN -> "en"
            }
        }
        val kind = nameTable<MediaKind> {
            when (it) {
                MediaKind.PHOTO -> "photo"
                MediaKind.VIDEO -> "video"
            }
        }
    }

    internal class NameTable<E : Enum<E>>(val values: List<E>, private val name: (E) -> String) {
        private val byName = values.associateBy(name)
        fun nameOf(value: E): String = name(value)
        fun valueOf(text: String): E = byName[text] ?: throw UnreadableRecipeFile("unknown name: $text")
    }

    private inline fun <reified E : Enum<E>> nameTable(noinline name: (E) -> String) = NameTable(enumValues<E>().toList(), name)

    /** `n` or `n/d`, both whole numbers of reasonable length, the denominator not zero. */
    private fun rationalOf(text: String): Rational {
        val parts = text.split('/')
        if (parts.size > 2 || parts.any { it.length > MAX_DIGITS }) throw UnreadableRecipeFile("not a fraction: $text")
        return try {
            Rational.of(BigInteger(parts[0]), if (parts.size == 2) BigInteger(parts[1]) else BigInteger.ONE)
        } catch (e: IllegalArgumentException) {
            throw UnreadableRecipeFile("not a fraction: $text", e)
        }
    }
}
