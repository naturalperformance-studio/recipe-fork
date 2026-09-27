package app.recipefork.server.account

import app.recipefork.core.api.AccountErrorDto
import app.recipefork.core.api.AccountErrors
import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What the store holds for an account: the acceptance of the terms (LEG-004) and no personal data (CON-001). */
class AccountRecordTest {
    private lateinit var db: HikariDataSource

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
        db.admit(listOf("brave", "copper"), listOf("otter"))
    }

    @AfterEach
    fun tearDown() = db.close()

    @Test
    fun `LEG-004 (4) - the recorded acceptance is a version identifier and a date, and nothing about a person`() = serve(db) {
        assertEquals(HttpStatusCode.Created, client.createAccount("Brave Copper Otter", client.issueKeys(), termsVersion = "terms-2026.09").status)

        val today = db.rows("select current_date") { it.getObject(1, LocalDate::class.java) }.single()
        val recorded = db.rows("select terms_version, terms_accepted_on from account") {
            it.getString(1) to it.getObject(2, LocalDate::class.java)
        }.single()
        assertEquals("terms-2026.09" to today, recorded)

        // The acceptance is those two columns, typed as an identifier and a date.
        assertEquals(
            listOf("terms_accepted_on" to "date", "terms_version" to "text"),
            db.rows(
                "select column_name, data_type from information_schema.columns where table_schema = current_schema() and table_name = 'account' and column_name like 'terms%' order by column_name",
            ) { it.getString(1) to it.getString(2) },
        )

        // A "version" that is free text — a name, an address — is not a version identifier and is not recorded.
        for (version in listOf("Ivan Karasev", "ivan@example.com", "+7 912 345-67-89", "", "v".repeat(33))) {
            val response = client.createAccount("Copper Brave Otter", client.issueKeys(), termsVersion = version)
            assertEquals(HttpStatusCode.BadRequest, response.status, "'$version'")
            assertEquals(AccountErrors.TERMS_VERSION_INVALID, Json.decodeFromString<AccountErrorDto>(response.bodyAsText()).code)
        }
        assertFailsWith<PSQLException> {
            db.exec("insert into account (nickname_first, nickname_second, nickname_noun, terms_version, terms_accepted_on) values ('copper', 'brave', 'otter', 'ivan@example.com', current_date)")
        }
        assertEquals(1, db.count("account"))
    }

    @Test
    fun `CON-001 (2) - the stored data of an account holds no name, email address, telephone number or image`() = serve(db) {
        assertEquals(HttpStatusCode.Created, client.createAccount("Brave Copper Otter", client.issueKeys()).status)

        // The fields an account is stored with, enumerated: each is accounted for, and none is of the four kinds.
        val fields = db.rows(
            """
            select table_name, column_name, data_type from information_schema.columns
            where table_schema = current_schema() and table_name in ('account', 'account_key')
            order by table_name, column_name
            """.trimIndent(),
        ) { "${it.getString(1)}.${it.getString(2)}:${it.getString(3)}" }
        assertEquals(
            listOf(
                "account.account_id:bigint",
                "account.nickname_first:text",
                "account.nickname_noun:text",
                "account.nickname_second:text",
                "account.terms_accepted_on:date",
                "account.terms_version:text",
                "account_key.account_id:bigint",
                "account_key.digest:bytea",
                "account_key.kind:text",
            ),
            fields,
        )

        // The live account's values: three list words, a version, a date, and three 32-byte digests.
        val account = db.rows("select nickname_first, nickname_second, nickname_noun, terms_version from account") {
            listOf(it.getString(1), it.getString(2), it.getString(3), it.getString(4))
        }.single()
        assertEquals(listOf("brave", "copper", "otter", "2026-09-27"), account)
        assertTrue(db.rows("select octet_length(digest) from account_key") { it.getInt(1) }.all { it == 32 })

        // A creation that tries to hand over any of the four kinds is refused, and nothing is stored.
        for ((field, value) in listOf("name" to "Ivan Karasev", "email" to "ivan@example.com", "phone" to "+79123456789", "avatar" to "iVBORw0KGgo=")) {
            val keys = client.issueKeys()
            val body = buildJsonObject {
                put("nickname", "Copper Brave Otter")
                put("termsVersion", "2026-09-27")
                putJsonObject("keys") {
                    put("accountKey", keys.accountKey)
                    put("deletionKey", keys.deletionKey)
                    put("recoveryKey", keys.recoveryKey)
                }
                put(field, JsonPrimitive(value))
            }
            val response = client.createAccountRaw(body.toString())
            assertEquals(HttpStatusCode.BadRequest, response.status, field)
            assertEquals(AccountErrors.MALFORMED_REQUEST, Json.decodeFromString<AccountErrorDto>(response.bodyAsText()).code)
        }
        assertEquals(1, db.count("account"))
    }
}
