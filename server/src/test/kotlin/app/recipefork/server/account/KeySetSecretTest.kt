package app.recipefork.server.account

import app.recipefork.core.api.AccountErrorDto
import app.recipefork.core.api.AccountErrors
import app.recipefork.core.api.CreateAccountDto
import app.recipefork.core.api.KeySetDto
import app.recipefork.server.attestation.Attestation
import app.recipefork.server.configuredServer
import app.recipefork.server.db.DatabaseConfig
import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.sql.DataSource
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * The server secret that seals issued key sets (`KEY_SET_SECRET`): the server does not start without a
 * well-formed one, and a set issued before a restart is confirmed after it (AUTH-024 (6), AUTH-007 (7)).
 */
class KeySetSecretTest {
    private lateinit var db: HikariDataSource

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
        db.admit(listOf("brave", "copper"), listOf("otter"))
    }

    @AfterEach
    fun tearDown() = db.close()

    /** The rest of the server's configuration; `ATTESTATION` is set explicitly, as it has no default. */
    private val databaseEnv = mapOf(
        "DATABASE_URL" to "jdbc:postgresql://nowhere/none", "DATABASE_USER" to "u", "DATABASE_PASSWORD" to "p",
        Attestation.ENV to "none",
    )

    /** Starting the server under [secret] (absent when null); the store is never reached if it refuses. */
    private fun refusal(secret: String?): String {
        var reached = false
        val env = databaseEnv + listOfNotNull(secret?.let { KeySetSeal.SECRET_ENV to it })
        val failure = assertFailsWith<IllegalStateException> {
            configuredServer(env, connect = { _: DatabaseConfig -> reached = true; db })
        }
        assertFalse(reached, "the server reached its store before refusing its configuration")
        val message = failure.message.orEmpty()
        assertContains(message, "KEY_SET_SECRET")
        assertContains(message, "base64")
        assertContains(message, "at least 32 bytes")
        return message
    }

    @Test
    fun `the server refuses to start without KEY_SET_SECRET, naming it as missing`() {
        assertContains(refusal(null), "KEY_SET_SECRET is missing")
    }

    @Test
    fun `the server refuses to start with a blank KEY_SET_SECRET, naming it as blank`() {
        assertContains(refusal(""), "KEY_SET_SECRET is blank")
        assertContains(refusal("   "), "KEY_SET_SECRET is blank")
    }

    @Test
    fun `the server refuses to start with a malformed KEY_SET_SECRET, naming it as malformed and saying what form is expected`() {
        assertContains(refusal("not base64 at all!"), "KEY_SET_SECRET is malformed: it is not base64")
        // Well-formed base64, but too short a secret: "short" is 5 bytes.
        assertContains(refusal("c2hvcnQ="), "KEY_SET_SECRET is malformed: it decodes to 5 bytes")
        assertContains(refusal(java.util.Base64.getEncoder().encodeToString(ByteArray(31) { 7 })), "decodes to 31 bytes")
    }

    @Test
    fun `the local deployment gives the server a KEY_SET_SECRET it accepts`() {
        val line = File("../deploy/local/compose.yml").readLines().single { it.trim().startsWith("KEY_SET_SECRET:") }
        val value = line.substringAfter(':').trim()
        KeySetSeal.fromEnvironment(mapOf(KeySetSeal.SECRET_ENV to value))
        configuredServer(databaseEnv + (KeySetSeal.SECRET_ENV to value), connect = { db })
    }

    @Test
    fun `AUTH-024 (6) - a key set issued before a restart is confirmed after it under the same KEY_SET_SECRET, and refused under another`() {
        val secret = "bSB7OUZbKry6DhribkuwCLr0UhfDgA6oz3CO4m7ne0Q="
        val otherSecret = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 42 })

        val issued = running(secret) { post(KeySetDto.PATH, "") }
            .let { Json.decodeFromString<KeySetDto>(it.body()) }
        assertEquals(0, db.count("account"))
        val confirmation = Json.encodeToString(CreateAccountDto("Brave Copper Otter", "2026-09-27", issued))

        // Restarted under another secret: the set is not this server's.
        val refused = running(otherSecret) { post(CreateAccountDto.PATH, confirmation) }
        assertEquals(400, refused.statusCode())
        assertEquals(AccountErrors.KEY_INVALID, Json.decodeFromString<AccountErrorDto>(refused.body()).code)
        assertEquals(0, db.count("account"))

        // Restarted under the same secret: the very set issued before the restart is accepted.
        val accepted = running(secret) { post(CreateAccountDto.PATH, confirmation) }
        assertEquals(201, accepted.statusCode(), accepted.body())
        assertEquals(1, db.count("account"))
        assertEquals(3, db.count("account_key"))
    }

    /** Starts the configured server — as `main` does — under [secret] on [db], runs [block] against it, stops it. */
    private fun <T> running(secret: String, block: Http.() -> T): T {
        val port = ServerSocket(0).use { it.localPort }
        val env = databaseEnv + mapOf(KeySetSeal.SECRET_ENV to secret, "PORT" to "$port")
        val server = configuredServer(env, connect = { _: DatabaseConfig -> db as DataSource })
        server.start(wait = false)
        try {
            return Http(port).block()
        } finally {
            server.stop(0, 1000)
        }
    }

    private class Http(private val port: Int) {
        private val client = HttpClient.newHttpClient()

        fun post(path: String, json: String): HttpResponse<String> = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }
}
