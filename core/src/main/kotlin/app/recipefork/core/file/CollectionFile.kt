package app.recipefork.core.file

import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.RecipeCollection
import app.recipefork.core.recipe.RecipeId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What a collection file carries (v0.36): the collection's identity, its name, the nickname of its stated author, and
 * the identities of its recipes in its order. Each of those recipes travels beside the document as its recipe file
 * holds it ([CollectionFile.recipeFolder]). Nothing of the collection's origin or time made travels: the receiving
 * device makes of it an imported collection of its own.
 */
data class CollectionFileContent(
    val id: CollectionId,
    val name: String,
    /** The stated author's nickname; null when the collection states none. */
    val author: String?,
    /** The recipes, in the collection's order; each is in the archive once, in the folder of its place. */
    val recipes: List<RecipeId>,
) {
    companion object {
        /**
         * The file's content for [collection], listing [recipes] — those of its recipes the file holds, in its order.
         * An imported collection names the author its own file stated, so that a collection shared onward keeps it; an
         * own one names [ownerNickname], as the reader's recipe export names the reader (null: no account, no author).
         * A blank nickname names none.
         */
        fun of(collection: RecipeCollection, ownerNickname: String?, recipes: List<RecipeId> = collection.recipes) = CollectionFileContent(
            id = collection.id,
            name = collection.name,
            author = when (collection.origin) {
                CollectionOrigin.IMPORTED -> collection.statedAuthor
                CollectionOrigin.OWN -> ownerNickname
            }?.trim()?.takeIf { it.isNotEmpty() },
            recipes = recipes,
        )
    }
}

/** The document is of a later collection format than this application reads: written by a later version of it. */
class LaterCollectionFile(val formatVersion: Int) :
    UnreadableRecipeFile("collection format $formatVersion is later than ${CollectionFile.FORMAT_VERSION}")

/**
 * The collection file's shape and its document, `collection.json`. The file is a ZIP, as a recipe file is
 * (decisions/20260928-recipe-file-is-a-zip.md): `collection.json` at its root, and the recipe at place `n` of the
 * document's list (the first is 1) in the folder `recipes/n/`, holding exactly what that recipe's own file holds —
 * `recipe.json` and `media/`, the document naming its media under `media/` relative to the folder. Read with the
 * folder's prefix taken off, a folder is a recipe file.
 *
 * [encode] writes what [CollectionFileContent] holds; [decode] reads it back, refusing a document of a later format
 * than [FORMAT_VERSION] or one that does not parse. It checks the document's form only.
 */
object CollectionFile {
    /** The format this application writes, and the latest it reads. */
    const val FORMAT_VERSION = 1
    const val EXTENSION = "recipefork-collection"
    const val MIME_TYPE = "application/vnd.recipefork.collection+zip"

    /** The document's entry in the archive. */
    const val DOCUMENT = "collection.json"

    /** The directory holding a folder per recipe. */
    const val RECIPES_DIRECTORY = "recipes/"

    /** The folder of the recipe at [place] of the document's list, counted from 0: `recipes/1/` for the first. */
    fun recipeFolder(place: Int): String {
        require(place >= 0) { "place $place" }
        return RECIPES_DIRECTORY + (place + 1) + "/"
    }

    private val json = Json { prettyPrint = true; explicitNulls = false; encodeDefaults = true }
    private val versionOnly = Json { ignoreUnknownKeys = true }

    fun encode(content: CollectionFileContent): String = json.encodeToString(
        CollectionDocument.serializer(),
        CollectionDocument(
            formatVersion = FORMAT_VERSION,
            collection = DocumentCollection(content.id.value, content.name, content.recipes.map { it.value }),
            author = content.author?.let(::DocumentAuthor),
        ),
    )

    fun decode(text: String): CollectionFileContent {
        val version = try {
            versionOnly.decodeFromString(CollectionDocumentVersion.serializer(), text).formatVersion
        } catch (e: SerializationException) {
            throw UnreadableRecipeFile("no format version", e)
        } catch (e: IllegalArgumentException) {
            throw UnreadableRecipeFile("no format version", e)
        }
        if (version > FORMAT_VERSION) throw LaterCollectionFile(version)
        if (version < 1) throw UnreadableRecipeFile("collection format $version does not exist")
        val document = try {
            json.decodeFromString(CollectionDocument.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is one.
            throw UnreadableRecipeFile("the document does not parse", e)
        }
        return CollectionFileContent(
            id = CollectionId(document.collection.id),
            name = document.collection.name,
            author = document.author?.nickname,
            recipes = document.collection.recipes.map(::RecipeId),
        )
    }
}

/** `collection.json`, the document of a collection file, as it is written. */
@Serializable
internal data class CollectionDocument(
    @SerialName("format_version") val formatVersion: Int,
    val collection: DocumentCollection,
    /** The author the collection states; absent for none. */
    val author: DocumentAuthor? = null,
)

@Serializable
internal data class DocumentCollection(
    val id: String,
    val name: String,
    /** The recipes' identities in the collection's order; the one at place `n` (from 1) lies in `recipes/n/`. */
    val recipes: List<String>,
)

@Serializable
private data class CollectionDocumentVersion(@SerialName("format_version") val formatVersion: Int)
