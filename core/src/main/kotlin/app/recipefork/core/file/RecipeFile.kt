package app.recipefork.core.file

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
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
class UnreadableRecipeFile(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The recipe file's shape and its document, `recipe.json` (decisions/20260928-recipe-file-is-a-zip.md).
 *
 * [encode] writes what [RecipeFileContent] holds; [decode] reads it back, refusing a document of a later format
 * than [FORMAT_VERSION] or one that does not parse. It checks the document's form only: whether its content passes
 * the rules a sending must pass is the importer's to check.
 */
object RecipeFile {
    /** The format this application writes, and the latest it reads. */
    const val FORMAT_VERSION = 1
    const val EXTENSION = "recipefork"
    const val MIME_TYPE = "application/vnd.recipefork.recipe+zip"
    /** The document's entry in the archive. */
    const val DOCUMENT = "recipe.json"
    /** The directory of the archive's media entries. */
    const val MEDIA_DIRECTORY = "media/"

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
        if (version > FORMAT_VERSION) throw UnreadableRecipeFile("format $version is later than $FORMAT_VERSION")
        if (version < 1) throw UnreadableRecipeFile("format $version does not exist")
        return try {
            json.decodeFromString(RecipeDocument.serializer(), text).toContent()
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
            steps = steps.map { it.toDocument() },
        ),
        author = author?.let(::DocumentAuthor),
        parent = parent?.let { DocumentParent(it.id.value, it.title, it.nickname) },
        media = media.map {
            DocumentMedia(it.mediaId, it.kind.name.lowercase(), (it.attachment as? MediaAttachment.ToStep)?.stepPosition, it.entry)
        },
    )

    private fun Ingredient.toDocument() = DocumentIngredient(
        position = position,
        dictionaryEntry = dictionaryEntry,
        freeTextName = freeTextName,
        qualifier = qualifier,
        unitType = unitType.name.lowercase(),
        unit = unit?.name?.lowercase(),
        quantity = quantity?.toString(),
        pieceWeight = pieceWeight?.toString(),
        divisible = divisible,
        enteredQuantity = enteredQuantity,
    )

    private fun Step.toDocument() = DocumentStep(
        position = position,
        text = text,
        references = ingredientReferences.map { DocumentReference(it.ingredientPosition, it.textOffset) },
        timer = timer?.let { DocumentTimer(it.duration.inWholeMinutes, it.label) },
    )

    private fun RecipeDocument.toContent() = RecipeFileContent(
        id = RecipeId(recipe.id),
        title = recipe.title,
        description = recipe.description,
        tags = recipe.tags.toSet(),
        language = recipe.language?.let(::languageOf),
        servings = recipe.servings,
        ingredients = recipe.ingredients.map { it.toIngredient() },
        steps = recipe.steps.map { it.toStep() },
        author = author?.nickname,
        parent = parent?.let { FileParent(RecipeId(it.id), it.title, it.nickname) },
        media = media.map {
            FileMedia(
                it.id, enumOf<MediaKind>(it.kind),
                it.step?.let(MediaAttachment::ToStep) ?: MediaAttachment.Cover, it.entry,
            )
        },
    )

    private fun DocumentIngredient.toIngredient() = Ingredient(
        position = position,
        dictionaryEntry = dictionaryEntry,
        qualifier = qualifier,
        freeTextName = freeTextName,
        unitType = enumOf<UnitType>(unitType),
        unit = unit?.let { enumOf<QuantityUnit>(it) },
        quantity = quantity?.let(::rationalOf),
        pieceWeight = pieceWeight?.let(::rationalOf),
        divisible = divisible,
        enteredQuantity = enteredQuantity,
    )

    private fun DocumentStep.toStep() = Step(
        position = position,
        text = text,
        ingredientReferences = references.map { IngredientReference(it.ingredientPosition, it.textOffset) },
        timer = timer?.let { Timer(it.minutes.minutes, it.label) },
    )

    private fun languageName(language: RecipeLanguage) = language.name.lowercase()

    private fun languageOf(name: String) = enumOf<RecipeLanguage>(name)

    /** The constant named [name] in the domain model's lower-case spelling; anything else is refused. */
    private inline fun <reified E : Enum<E>> enumOf(name: String): E =
        enumValues<E>().firstOrNull { it.name.lowercase() == name }
            ?: throw UnreadableRecipeFile("unknown ${E::class.simpleName}: $name")

    /** `n` or `n/d`, both whole numbers, the denominator not zero. */
    private fun rationalOf(text: String): Rational {
        val parts = text.split('/')
        if (parts.size > 2) throw UnreadableRecipeFile("not a fraction: $text")
        return try {
            Rational.of(BigInteger(parts[0]), if (parts.size == 2) BigInteger(parts[1]) else BigInteger.ONE)
        } catch (e: IllegalArgumentException) {
            throw UnreadableRecipeFile("not a fraction: $text", e)
        }
    }
}
