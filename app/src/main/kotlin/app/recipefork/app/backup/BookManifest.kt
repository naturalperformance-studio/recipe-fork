package app.recipefork.app.backup

import app.recipefork.app.book.BookDatabase
import app.recipefork.core.file.RecipeFileCheck
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * A saved book (decisions/20261012-a-book-file-holds-the-books-database.md): a ZIP holding, in this order, [MANIFEST],
 * [DATABASE] — a consistent copy of the book's database — every file of the book's media directory under [MEDIA], and
 * [KITCHEN], My kitchen. The manifest comes first, so that a reader judges the file before it unpacks anything.
 */
object BookFile {
    const val EXTENSION = "recipefork-book"
    const val MANIFEST = "manifest.json"
    const val DATABASE = "book.db"
    const val MEDIA = "media/"
    const val KITCHEN = "kitchen.json"

    /** The version of this layout; a file of a later one is refused as from a newer application. */
    const val FORMAT = 1

    /** Whether [name] is one a media file of a book is kept and carried under: a plain file, and not one half-written. */
    fun isMediaName(name: String): Boolean = !name.startsWith(".") && '/' !in name && RecipeFileCheck.isSafeEntryName(name)
}

/**
 * What a saved book says of itself: the file's [format], the [bookSchema] its database was written at, the [appVersion]
 * that saved it and [savedAt], and how many [recipes] and [media] files it holds — the reader checks both against what
 * it unpacks, so a file cut short is told from a whole one.
 */
data class BookManifest(
    val format: Int = BookFile.FORMAT,
    val bookSchema: Int = BookDatabase.VERSION,
    val appVersion: String,
    val savedAt: Instant,
    val recipes: Int,
    val media: Int,
) {
    fun encode(): String = buildJsonObject {
        put(FORMAT, JsonPrimitive(format))
        put(BOOK_SCHEMA, JsonPrimitive(bookSchema))
        put(APP_VERSION, JsonPrimitive(appVersion))
        put(SAVED_AT, JsonPrimitive(savedAt.toString()))
        put(RECIPES, JsonPrimitive(recipes))
        put(MEDIA, JsonPrimitive(media))
    }.toString()

    companion object {
        private const val FORMAT = "format"
        private const val BOOK_SCHEMA = "book_schema"
        private const val APP_VERSION = "app_version"
        private const val SAVED_AT = "saved_at"
        private const val RECIPES = "recipes"
        private const val MEDIA = "media"

        /**
         * The manifest [text] holds, judged before anything else of the file is read: of a later format, or naming a
         * book schema later than this application's, it is [BookFileRefusal.NEWER]; one that does not read is
         * [BookFileRefusal.DAMAGED]. The format is read first, so a later one is told whatever else it holds.
         */
        fun decode(text: String): BookManifest {
            val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw RefusedBookFile.damaged("manifest")
            fun int(key: String) = (root[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            fun text(key: String) = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val format = int(FORMAT) ?: throw RefusedBookFile.damaged("no format")
            if (format > BookFile.FORMAT) throw RefusedBookFile(BookFileRefusal.NEWER, "format $format")
            val schema = int(BOOK_SCHEMA) ?: throw RefusedBookFile.damaged("no book schema")
            if (schema > BookDatabase.VERSION) throw RefusedBookFile(BookFileRefusal.NEWER, "book schema $schema")
            val savedAt = try {
                Instant.parse(text(SAVED_AT) ?: throw RefusedBookFile.damaged("no time"))
            } catch (e: DateTimeParseException) {
                throw RefusedBookFile.damaged("time")
            }
            val manifest = BookManifest(
                format, schema, text(APP_VERSION) ?: throw RefusedBookFile.damaged("no app version"), savedAt,
                int(RECIPES) ?: throw RefusedBookFile.damaged("no recipes"), int(MEDIA) ?: throw RefusedBookFile.damaged("no media"),
            )
            if (format < 1 || schema < 1 || manifest.recipes < 0 || manifest.media < 0) throw RefusedBookFile.damaged("manifest")
            return manifest
        }
    }
}

/** Why a file was not opened as a saved book; the screen words each. */
enum class BookFileRefusal {
    /** It is no ZIP, or a ZIP that does not begin with a book's manifest — a recipe file, say. */
    NOT_A_BOOK_FILE,

    /** It is a saved book, but damaged: cut short, or something in it does not read. */
    DAMAGED,

    /** It was saved by a newer application: a later format, or a book schema later than this application's. */
    NEWER,

    /** It could not be read to its end, or there was no room on the device to unpack it. */
    CANNOT_READ,
}

internal class RefusedBookFile(val refusal: BookFileRefusal, message: String, cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        fun damaged(message: String, cause: Throwable? = null) = RefusedBookFile(BookFileRefusal.DAMAGED, message, cause)
    }
}
