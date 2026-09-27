package app.recipefork.app.dictionary

import app.recipefork.core.api.DictionariesDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

/** Keeps the device's copy of both dictionaries (FLOW-001) in step with the server. */
class DictionaryRepository(
    private val client: HttpClient,
    private val dao: DictionaryDao,
) {
    enum class Refresh { Replaced, Unchanged, Unreachable }

    /**
     * Asks the server for both dictionaries, naming the version held. A changed version replaces the
     * held copy; an unchanged one, or a server that cannot be reached, leaves it as it is.
     */
    suspend fun refresh(): Refresh {
        val held = dao.version()
        val response = try {
            client.get(DictionariesDto.PATH) {
                if (held != null) header(HttpHeaders.IfNoneMatch, "\"$held\"")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Refresh.Unreachable
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                dao.replace(response.body<DictionariesDto>())
                Refresh.Replaced
            }
            HttpStatusCode.NotModified -> Refresh.Unchanged
            else -> Refresh.Unreachable
        }
    }
}
