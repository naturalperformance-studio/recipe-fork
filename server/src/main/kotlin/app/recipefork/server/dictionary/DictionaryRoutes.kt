package app.recipefork.server.dictionary

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Serves both dictionaries to devices (FLOW-001). The version is a digest of the content, so it
 * changes exactly when the operator changes either dictionary in the store (DOM-014); a device
 * sending the version it holds as `If-None-Match` is answered 304 with no body.
 */
fun Route.dictionaryRoutes(store: DictionaryStore) {
    get(DictionariesDto.PATH) {
        val dictionaries = store.snapshot().toDto()
        val etag = "\"${dictionaries.version}\""
        call.response.header(HttpHeaders.ETag, etag)
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        val held = call.request.headers[HttpHeaders.IfNoneMatch]
        if (held != null && held.split(',').any { it.trim() == etag }) {
            call.respond(HttpStatusCode.NotModified)
        } else {
            call.respond(dictionaries)
        }
    }
}

internal fun DictionarySnapshot.toDto(): DictionariesDto {
    val ingredients = ingredients.map { IngredientEntryDto(it.id, it.labelRu, it.labelEn, it.parentId) }
    val tags = tags.map { TagDto(it.id, it.labelRu, it.labelEn) }
    val content = Json.encodeToString(DictionariesDto(version = "", ingredients = ingredients, tags = tags))
    val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
    return DictionariesDto(
        version = digest.joinToString("") { "%02x".format(it) },
        ingredients = ingredients,
        tags = tags,
    )
}
