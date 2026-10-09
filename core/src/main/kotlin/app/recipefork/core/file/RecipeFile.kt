package app.recipefork.core.file

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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
     * (DOM-024) where formats 1 and 2 gave it at most one `timer`, which reads as a step with that one timer.
     */
    const val FORMAT_VERSION = 3

    /** The last format that wrote a step's one `timer` rather than its list of `timers`. */
    private const val LAST_ONE_TIMER_FORMAT = 2
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

    fun decode(text: String): RecipeFileContent {
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
            json.decodeFromString(RecipeDocument.serializer(), text).also { it.checkTimerShape() }.toContent()
        } catch (e: UnreadableRecipeFile) {
            throw e
        } catch (e: IllegalArgumentException) {
            // SerializationException is one, and so is every value the mapping below does not know.
            throw UnreadableRecipeFile("the document does not parse", e)
        } catch (e: ArithmeticException) {
            throw UnreadableRecipeFile("the document does not parse", e)
        }
    }

    private fun RecipeFileContent.toDocument() = RecipeDocument(
        formatVersion = FORMAT_VERSION,
        recipe = DocumentRecipe(
            id = id.value,
            title = title,
            description = description,
            language = language?.let(::languageName),
            tags = tags.sorted(),
            servings = servings,
            ingredients = ingredients.map { it.toDocument() },
            ingredientGroups = ingredientGroups.map { DocumentGroup(it.name, it.firstPosition) },
            steps = steps.map { it.toDocument() },
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

    private fun RecipeDocument.toContent() = RecipeFileContent(
        id = RecipeId(recipe.id),
        title = recipe.title,
        description = recipe.description,
        tags = recipe.tags.toSet(),
        language = recipe.language?.let(::languageOf),
        servings = recipe.servings,
        ingredients = recipe.ingredients.map { it.toIngredient() },
        ingredientGroups = recipe.ingredientGroups.map { IngredientGroup(it.name, it.firstIngredient) },
        steps = recipe.steps.map { it.toStep() },
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
