package app.recipefork.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The client every call to the product's server goes through. */
object Network {
    fun client(engine: HttpClientEngine, serverUrl: String): HttpClient = HttpClient(engine) {
        expectSuccess = false
        defaultRequest { url(serverUrl) }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }
}
