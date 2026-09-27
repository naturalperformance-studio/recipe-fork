package app.recipefork.server.account

import app.recipefork.core.api.AccountErrorDto
import app.recipefork.core.api.AccountErrors
import app.recipefork.core.api.KeySetDto
import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
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

        // An issued set, once used, is not accepted again.
        assertKeyTaken(client.createAccount("Calm Calm Wren", first))
        // Each key of the first account, offered again for a new account, under its own kind...
        assertNotAcceptedAgain(fresh(accountKey = first.accountKey))
        assertNotAcceptedAgain(fresh(deletionKey = first.deletionKey))
        assertNotAcceptedAgain(fresh(recoveryKey = first.recoveryKey))
        // ...and under another kind.
        assertNotAcceptedAgain(fresh(recoveryKey = retag(first.accountKey, KeyKind.ACCOUNT, KeyKind.RECOVERY)))
        assertNotAcceptedAgain(fresh(accountKey = retag(second.deletionKey, KeyKind.DELETION, KeyKind.ACCOUNT)))

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

        assertNotAcceptedAgain(fresh(deletionKey = second.deletionKey))
        assertEquals(2, db.count("account"))
    }

    @Test
    fun `AUTH-020 (5) - recovery keys are unique across accounts exactly as account keys are`() = serve(db) {
        val first = client.issueKeys()
        client.createAccount("Brave Copper Otter", first)

        assertNotAcceptedAgain(fresh(recoveryKey = first.recoveryKey))
        assertNotAcceptedAgain(fresh(deletionKey = retag(first.recoveryKey, KeyKind.RECOVERY, KeyKind.DELETION)))
        assertEquals(1, db.count("account"))
    }

    // Only a key set this server issued is accepted (ADR 20260927-keys-are-hashed-random-secrets).

    @Test
    fun `AUTH-014 (1)(3), AUTH-017 (4), AUTH-020 (5) - a key set the caller chose itself is refused, and the refusal does not tell whether any key is held`() = serve(db) {
        val held = client.issueKeys()
        assertEquals(HttpStatusCode.Created, client.createAccount("Brave Copper Otter", held).status)

        val zero = Keys.encode(ByteArray(Keys.KEY_BYTES))
        val allZero = KeySetDto("account-$zero", "deletion-${Keys.encode(ByteArray(Keys.KEY_BYTES) { 1 })}", "recovery-${Keys.encode(ByteArray(Keys.KEY_BYTES) { 2 })}", seal = "")
        val selfMade = List(3) { fresh() } // well-formed, generated off the server, sealed with nothing it issued
        val holdingKnownKey = fresh(accountKey = held.accountKey)
        val forgedSeal = allZero.copy(seal = randomSeal().seal(allZero.accountKey, allZero.deletionKey, allZero.recoveryKey))

        val answers = (listOf(allZero, forgedSeal, holdingKnownKey) + selfMade).map { keys ->
            val response = client.createAccount("Calm Calm Wren", keys)
            response.status to response.bodyAsText()
        }
        assertEquals(HttpStatusCode.BadRequest, answers.first().first)
        assertEquals(AccountErrors.KEY_INVALID, Json.decodeFromString<AccountErrorDto>(answers.first().second).code)
        assertEquals(1, answers.toSet().size, "a set holding a held key is answered as any other self-made set: $answers")
        assertEquals(1, db.count("account"))
        assertEquals(3, db.count("account_key"))
    }

    @Test
    fun `AUTH-014 (1)(3) - an issued key set changed in any way is refused`() = serve(db) {
        val issued = client.issueKeys()
        val other = client.issueKeys()
        val flipped = issued.seal.let { s -> (if (s.first() == 'A') 'B' else 'A') + s.drop(1) }

        val altered = listOf(
            issued.copy(accountKey = other.accountKey),
            issued.copy(deletionKey = other.deletionKey),
            issued.copy(recoveryKey = other.recoveryKey),
            issued.copy(accountKey = "account-" + Keys.encode(ByteArray(Keys.KEY_BYTES))),
            issued.copy(
                accountKey = retag(issued.recoveryKey, KeyKind.RECOVERY, KeyKind.ACCOUNT),
                recoveryKey = retag(issued.accountKey, KeyKind.ACCOUNT, KeyKind.RECOVERY),
            ),
            issued.copy(seal = other.seal),
            issued.copy(seal = flipped),
            issued.copy(seal = ""),
            issued.copy(seal = "not base64!"),
        )
        for (keys in altered) {
            val response = client.createAccount("Brave Copper Otter", keys)
            assertEquals(HttpStatusCode.BadRequest, response.status, "$keys")
            assertEquals(AccountErrors.KEY_INVALID, error(response), "$keys")
        }
        assertEquals(0, db.count("account"))
        assertEquals(0, db.count("account_key"))
    }

    @Test
    fun `AUTH-007 (7), AUTH-024 (6) - an issued key set confirms after going back and forward, with nothing stored before`() {
        val secret = randomSeal()
        lateinit var issued: KeySetDto
        serve(db, secret) {
            issued = client.issueKeys()
            // Back to the first step and forward again: other nicknames, other key requests.
            repeat(5) { client.offerNickname(); client.issueKeys() }
        }
        assertEquals(0, db.count("account"))
        assertEquals(0, db.count("account_key"))

        // A server that shares only the configured secret — nothing of the attempt — still accepts the set...
        serve(db, secret) {
            assertEquals(HttpStatusCode.Created, client.createAccount("Brave Copper Otter", issued).status)
        }
        // ...while under another secret the very same set would not have been accepted.
        val other = randomSeal()
        assertFalse(other.verifies(issued.accountKey, issued.deletionKey, issued.recoveryKey, issued.seal))
        assertEquals(1, db.count("account"))
        assertEquals(3, db.count("account_key"))
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

    /**
     * A set holding a key already held is refused at the door, as any set the server did not issue
     * is, and the store itself would refuse it too.
     */
    private suspend fun ApplicationTestBuilder.assertNotAcceptedAgain(keys: KeySetDto) {
        val response = client.createAccount("Calm Calm Wren", keys)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(AccountErrors.KEY_INVALID, error(response))

        val set = KeySet(
            assertNotNull(Keys.parse(KeyKind.ACCOUNT, keys.accountKey)),
            assertNotNull(Keys.parse(KeyKind.DELETION, keys.deletionKey)),
            assertNotNull(Keys.parse(KeyKind.RECOVERY, keys.recoveryKey)),
        )
        assertEquals(CreationResult.KeyTaken, AccountStore(db).create(Nickname("calm", "calm", "wren"), "v1", set))
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
            seal = "",
        )
    }

    private fun retag(text: String, from: KeyKind, to: KeyKind): String = to.prefix + text.removePrefix(from.prefix)
}
