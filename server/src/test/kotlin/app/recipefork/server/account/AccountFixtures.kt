package app.recipefork.server.account

import app.recipefork.core.api.CreateAccountDto
import app.recipefork.core.api.KeySetDto
import app.recipefork.core.api.NicknameOfferDto
import app.recipefork.server.attestation.AttestationService
import app.recipefork.server.attestation.NoAttestation
import app.recipefork.server.module
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.time.Clock
import javax.sql.DataSource

/** Plain SQL on the store, as the operator and an inspector of the store would use it. */
internal fun DataSource.exec(sql: String, vararg params: Any) {
    connection.use { conn ->
        conn.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            stmt.execute()
        }
    }
}

internal fun <T> DataSource.rows(sql: String, vararg params: Any, row: (java.sql.ResultSet) -> T): List<T> =
    connection.use { conn ->
        conn.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
        }
    }

internal fun DataSource.count(table: String): Long = rows("select count(*) from $table") { it.getLong(1) }.single()

/** Puts words into the two lists directly in the store (AUTH-023 (2)). */
internal fun DataSource.admit(adjectives: List<String>, nouns: List<String>) {
    adjectives.forEach { exec("insert into nickname_adjective (word) values (?)", it) }
    nouns.forEach { exec("insert into nickname_noun (word) values (?)", it) }
}

/** Loads the local deployment's placeholder lists. */
internal fun DataSource.loadPlaceholderSeed() {
    val sql = File("../deploy/local/seed/nickname-words.sql").readText()
    connection.use { conn -> conn.createStatement().use { it.execute(sql) } }
}

/** A seal under a fresh random secret, as a server configured with its own secret would have. */
internal fun randomSeal(): KeySetSeal = KeySetSeal(ByteArray(KeySetSeal.MIN_SECRET_BYTES).also(SecureRandom()::nextBytes))

internal fun serve(
    db: DataSource,
    seal: KeySetSeal = randomSeal(),
    attestation: AttestationService = NoAttestation,
    clock: Clock = Clock.systemUTC(),
    test: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application { module(db, seal, attestation, clock) }
    test()
}

internal suspend fun HttpClient.offerNickname(): String =
    Json.decodeFromString<NicknameOfferDto>(post(NicknameOfferDto.PATH).bodyAsText()).nickname

internal suspend fun HttpClient.issueKeys(): KeySetDto =
    Json.decodeFromString<KeySetDto>(post(KeySetDto.PATH).bodyAsText())

internal suspend fun HttpClient.createAccount(
    nickname: String,
    keys: KeySetDto,
    termsVersion: String = "2026-09-27",
    attestationToken: String? = null,
): HttpResponse = createAccountRaw(Json.encodeToString(CreateAccountDto(nickname, termsVersion, keys, attestationToken)))

internal suspend fun HttpClient.createAccountRaw(body: String): HttpResponse =
    post(CreateAccountDto.PATH) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
