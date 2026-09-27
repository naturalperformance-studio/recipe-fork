package app.recipefork.server.account

import app.recipefork.core.api.AccountErrorDto
import app.recipefork.core.api.AccountErrors
import app.recipefork.core.api.KeySetDto
import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Issuing the three keys, holding them at the server, and never issuing one twice. */
class AccountKeysTest {
    private lateinit var db: HikariDataSource

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
        db.admit(listOf("brave", "calm", "copper"), listOf("otter", "heron", "wren"))
    }

    @AfterEach
    fun tearDown() = db.close()

    @Test
    fun `AUTH-014 (1) - two accounts' keys differ, and no key of any of the three kinds is accepted for a second account`() = serve(db) {
        val first = client.issueKeys()
        val second = client.issueKeys()
        assertEquals(HttpStatusCode.Created, client.createAccount("Brave Copper Otter", first).status)
        assertEquals(HttpStatusCode.Created, client.createAccount("Calm Brave Heron", second).status)

        val all = listOf(first, second).flatMap { listOf(it.accountKey, it.deletionKey, it.recoveryKey) }
        assertEquals(6, all.toSet().size, "the keys of two accounts differ")
        assertEquals(6, db.rows("select distinct digest from account_key") { it.getBytes(1).toList() }.size)

        // Each key of the first account, offered again for a new account, under its own kind...
        assertKeyTaken(client.createAccount("Calm Calm Wren", fresh(accountKey = first.accountKey)))
        assertKeyTaken(client.createAccount("Calm Calm Wren", fresh(deletionKey = first.deletionKey)))
        assertKeyTaken(client.createAccount("Calm Calm Wren", fresh(recoveryKey = first.recoveryKey)))
        // ...and under another kind.
        assertKeyTaken(client.createAccount("Calm Calm Wren", fresh(recoveryKey = retag(first.accountKey, KeyKind.ACCOUNT, KeyKind.RECOVERY))))
        assertKeyTaken(client.createAccount("Calm Calm Wren", fresh(accountKey = retag(second.deletionKey, KeyKind.DELETION, KeyKind.ACCOUNT))))

        assertEquals(2, db.count("account"))
        assertEquals(6, db.count("account_key"))
    }

    @Test
    fun `AUTH-014 (1) - one key given for two kinds of the same account is refused`() = serve(db) {
        val keys = client.issueKeys()
        val same = keys.copy(recoveryKey = retag(keys.accountKey, KeyKind.ACCOUNT, KeyKind.RECOVERY))

        val response = client.createAccount("Brave Copper Otter", same)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(AccountErrors.KEY_INVALID, error(response))
        assertEquals(0, db.count("account"))
    }

    @Test
    fun `AUTH-014 (1) - the keys issued at creation are never the same twice`() = serve(db) {
        val issued = List(200) { client.issueKeys() }.flatMap { listOf(it.accountKey, it.deletionKey, it.recoveryKey) }
        val bodies = issued.map { it.substringAfter('-') }
        assertEquals(issued.size, bodies.toSet().size)
    }

    @Test
    fun `AUTH-017 (3) - two accounts' deletion keys differ, and a deletion key is never accepted twice`() = serve(db) {
        val first = client.issueKeys()
        val second = client.issueKeys()
        client.createAccount("Brave Copper Otter", first)
        client.createAccount("Calm Brave Heron", second)

        val deletionDigests = db.rows("select digest from account_key where kind = 'deletion'") { it.getBytes(1).toList() }
        assertEquals(2, deletionDigests.toSet().size)
        assertTrue(first.deletionKey != second.deletionKey)

        assertKeyTaken(client.createAccount("Calm Calm Wren", fresh(deletionKey = second.deletionKey)))
        assertEquals(2, db.count("account"))
    }

    @Test
    fun `AUTH-020 (5) - recovery keys are unique across accounts exactly as account keys are`() = serve(db) {
        val first = client.issueKeys()
        client.createAccount("Brave Copper Otter", first)

        assertKeyTaken(client.createAccount("Calm Calm Wren", fresh(recoveryKey = first.recoveryKey)))
        assertKeyTaken(client.createAccount("Calm Calm Wren", fresh(deletionKey = retag(first.recoveryKey, KeyKind.RECOVERY, KeyKind.DELETION))))
        assertEquals(1, db.count("account"))
    }

    @Test
    fun `AUTH-014 (4) - nothing the server stores reconstructs any key of an account`() = serve(db) {
        val keys = client.issueKeys()
        assertEquals(HttpStatusCode.Created, client.createAccount("Brave Copper Otter", keys).status)

        val stored = everythingStored()
        val texts = listOf(keys.accountKey, keys.deletionKey, keys.recoveryKey)
        val parsed = texts.zip(KeyKind.entries).map { (text, kind) -> assertNotNull(Keys.parse(kind, text)) }
        for ((text, key) in texts.zip(parsed)) {
            val forms = listOf(
                text, text.substringAfter('-'), text.lowercase(),
                key.bytes.hex(), Base64.getEncoder().encodeToString(key.bytes),
            )
            forms.forEach { form -> assertFalse(stored.contains(form, ignoreCase = true), "the store holds the key as '$form'") }
        }
        // What is held in their place is a one-way digest of each key, and only that.
        val digests = db.rows("select kind, digest from account_key order by kind") { it.getString(1) to it.getBytes(2).hex() }.toMap()
        assertEquals(
            mapOf("account" to parsed[0].digest.hex(), "deletion" to parsed[1].digest.hex(), "recovery" to parsed[2].digest.hex()),
            digests,
        )
        assertEquals(
            listOf("account_id", "digest", "kind"),
            db.rows("select column_name from information_schema.columns where table_schema = current_schema() and table_name = 'account_key' order by column_name") { it.getString(1) },
        )
    }

    /** Every value of every table in the store, as text: what someone holding all of it would have. */
    private fun everythingStored(): String {
        val tables = db.rows("select table_name from information_schema.tables where table_schema = current_schema()") { it.getString(1) }
        return tables.joinToString("\n") { table ->
            db.rows("select t::text from \"$table\" t") { it.getString(1) }.joinToString("\n")
        }
    }

    private suspend fun assertKeyTaken(response: HttpResponse) {
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(AccountErrors.KEY_TAKEN, error(response))
    }

    private suspend fun error(response: HttpResponse): String =
        Json.decodeFromString<AccountErrorDto>(response.bodyAsText()).code

    private fun fresh(accountKey: String? = null, deletionKey: String? = null, recoveryKey: String? = null): KeySetDto {
        val set = KeyIssuer().issueSet()
        return KeySetDto(
            accountKey ?: set.account.text,
            deletionKey ?: set.deletion.text,
            recoveryKey ?: set.recovery.text,
        )
    }

    private fun retag(text: String, from: KeyKind, to: KeyKind): String = to.prefix + text.removePrefix(from.prefix)
}
