package app.recipefork.server.account

import app.recipefork.core.api.AccountCreatedDto
import app.recipefork.core.api.AccountErrorDto
import app.recipefork.core.api.AccountErrors
import app.recipefork.core.api.NicknameOfferDto
import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Generating, offering and consuming nicknames (AUTH-018, AUTH-022, AUTH-023, DOM-001). */
class NicknameTest {
    private lateinit var db: HikariDataSource
    private lateinit var store: AccountStore

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
        store = AccountStore(db)
    }

    @AfterEach
    fun tearDown() = db.close()

    // AUTH-022

    @Test
    fun `AUTH-022 (1) - a generated nickname is two adjectives, then a noun, each capitalised, separated by one space`() = serve(db) {
        val adjectives = listOf("brave", "copper", "gentle", "swift")
        val nouns = listOf("otter", "heron", "maple")
        db.admit(adjectives, nouns)
        val form = Regex("^[A-Z][a-z]+ [A-Z][a-z]+ [A-Z][a-z]+$")

        repeat(100) {
            val nickname = client.offerNickname()
            assertTrue(form.matches(nickname), "'$nickname' is not of the form Brave Copper Otter")
            val (a, b, n) = nickname.split(" ").map { it.lowercase() }
            assertTrue(a in adjectives && b in adjectives, "'$nickname' does not open with two adjectives")
            assertTrue(n in nouns, "'$nickname' does not end with a noun")
        }
    }

    @Test
    fun `AUTH-022 (3) - nicknames are compared as the three words, so case or spacing never makes two differ`() = serve(db) {
        db.admit(listOf("brave", "copper"), listOf("otter"))
        assertEquals(HttpStatusCode.Created, client.createAccount("Brave Copper Otter", client.issueKeys()).status)

        for (variant in listOf("brave copper otter", "BRAVE COPPER OTTER", "  Brave   Copper\tOtter ", "bRaVe Copper otter")) {
            val response = client.createAccount(variant, client.issueKeys())
            assertEquals(HttpStatusCode.Conflict, response.status, "'$variant'")
            assertEquals(AccountErrors.NICKNAME_TAKEN, error(response))
        }
        // The words run together are not the three words.
        assertEquals(HttpStatusCode.BadRequest, client.createAccount("BraveCopper Otter", client.issueKeys()).status)
        assertEquals(1, db.count("account"))

        // The store itself holds only the canonical words, so no variant can be written beside them.
        assertFailsWith<PSQLException> {
            db.exec("insert into account (nickname_first, nickname_second, nickname_noun, terms_version, terms_accepted_on) values ('Brave', 'Copper', 'Otter', 'v1', current_date)")
        }
        assertFailsWith<PSQLException> {
            db.exec("insert into account (nickname_first, nickname_second, nickname_noun, terms_version, terms_accepted_on) values ('brave ', 'copper', 'otter', 'v1', current_date)")
        }
        assertEquals(1, db.count("account"))
    }

    // AUTH-018 and DOM-001

    @Test
    fun `DOM-001 (3) - rerolling a nickname before the account is created assigns and consumes none`() = serve(db) {
        db.admit(listOf("brave", "copper"), listOf("otter"))
        // Four combinations exist; offer each of them many times over, and the keys too.
        val offered = List(40) { client.offerNickname() }.toSet()
        repeat(5) { client.issueKeys() }

        assertEquals(0, db.count("account"), "no nickname is assigned")
        assertEquals(0, db.count("account_key"), "no key is held")
        // Every offered nickname can still be taken, so none was consumed by being offered.
        for (nickname in offered) {
            assertEquals(HttpStatusCode.Created, client.createAccount(nickname, client.issueKeys()).status, nickname)
        }
    }

    @Test
    fun `AUTH-018 (3) - only the accepted nickname is consumed, and the passed-over ones stay available to others`() = serve(db) {
        db.admit(listOf("brave", "copper", "calm"), listOf("otter", "heron"))
        val shown = mutableListOf<String>()
        while (shown.toSet().size < 3) shown += client.offerNickname()
        val accepted = shown.last()
        val passedOver = shown.toSet() - accepted

        assertEquals(HttpStatusCode.Created, client.createAccount(accepted, client.issueKeys()).status)
        assertEquals(listOf(accepted.lowercase()), heldNicknames())

        // The accepted one is consumed; everything passed over is still offered and can be taken by others.
        val laterOffers = List(200) { client.offerNickname() }.toSet()
        assertTrue(accepted !in laterOffers)
        assertTrue(laterOffers.containsAll(passedOver), "passed over $passedOver, later offered $laterOffers")
        for (nickname in passedOver) {
            assertEquals(HttpStatusCode.Created, client.createAccount(nickname, client.issueKeys()).status, nickname)
        }
        assertEquals(HttpStatusCode.Conflict, client.createAccount(accepted, client.issueKeys()).status)
    }

    @Test
    fun `AUTH-018 (5) - two accounts created at the same moment hold different nicknames`() {
        db.admit(listOf("brave", "copper"), listOf("otter", "heron"))
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(4) { round ->
                val nickname = listOf(
                    Nickname("brave", "copper", "otter"), Nickname("copper", "brave", "otter"),
                    Nickname("brave", "brave", "heron"), Nickname("copper", "copper", "heron"),
                )[round]
                val barrier = CyclicBarrier(2)
                val results = List(2) {
                    pool.submit<CreationResult> {
                        barrier.await(10, TimeUnit.SECONDS)
                        store.create(nickname, "v1", KeyIssuer().issueSet())
                    }
                }.map { it.get(30, TimeUnit.SECONDS) }

                assertEquals(1, results.count { it is CreationResult.Created }, "round $round: $results")
                assertEquals(1, results.count { it == CreationResult.NicknameTaken }, "round $round: $results")
            }
        } finally {
            pool.shutdownNow()
        }
        val held = heldNicknames()
        assertEquals(4, held.size)
        assertEquals(held.size, held.toSet().size)
        assertEquals(4, db.count("account"))
        assertEquals(12, db.count("account_key"), "the refused creations left no keys behind")
    }

    @Test
    fun `DOM-001 (1) - any two accounts hold different nicknames`() = serve(db) {
        db.admit(listOf("brave", "copper"), listOf("otter", "heron"))
        val created = List(8) {
            val response = client.createAccount(client.offerNickname(), client.issueKeys())
            assertEquals(HttpStatusCode.Created, response.status)
            Json.decodeFromString<AccountCreatedDto>(response.bodyAsText()).nickname
        }
        assertEquals(8, created.toSet().size)
        assertEquals(8, heldNicknames().toSet().size)

        // All eight combinations are held: nothing more is offered, and a held one cannot be taken again.
        val exhausted = client.post(NicknameOfferDto.PATH)
        assertEquals(HttpStatusCode.ServiceUnavailable, exhausted.status)
        assertEquals(AccountErrors.NO_NICKNAME_AVAILABLE, error(exhausted))
        assertEquals(HttpStatusCode.Conflict, client.createAccount(created.first(), client.issueKeys()).status)

        // The store refuses a second holder even when written to directly.
        val e = assertFailsWith<PSQLException> {
            db.exec("insert into account (nickname_first, nickname_second, nickname_noun, terms_version, terms_accepted_on) values ('brave', 'copper', 'otter', 'v1', current_date)")
        }
        assertEquals("account_nickname_unique", e.serverErrorMessage?.constraint)
        assertEquals(8, db.count("account"))
    }

    // AUTH-023

    @Test
    fun `AUTH-023 (3) - every combination of admitted words is generated and accepted, checked against nothing further`() = serve(db) {
        // Harmless words that combine into something that reads badly are still combined (AUTH-023, cost stated).
        db.admit(listOf("rotten", "sour"), listOf("egg", "apple"))
        val every = listOf("rotten", "sour").flatMap { a -> listOf("rotten", "sour").flatMap { b -> listOf("egg", "apple").map { n -> Nickname(a, b, n).display } } }.toSet()

        val offered = List(400) { client.offerNickname() }.toSet()
        assertEquals(every, offered, "some combination was never generated")
        for (nickname in every) {
            assertEquals(HttpStatusCode.Created, client.createAccount(nickname, client.issueKeys()).status, nickname)
        }
    }

    @Test
    fun `AUTH-023 (4) - a word removed from a list leaves nicknames holding it unchanged and is no longer drawn`() = serve(db) {
        db.admit(listOf("brave", "copper", "calm"), listOf("otter", "heron"))
        assertEquals(HttpStatusCode.Created, client.createAccount("Brave Copper Otter", client.issueKeys()).status)

        // The product owner removes a word directly in the store (AUTH-023 (2)).
        db.exec("delete from nickname_adjective where word = 'copper'")

        assertEquals(listOf("brave copper otter"), heldNicknames(), "the existing nickname is unchanged")
        val offered = List(200) { client.offerNickname() }
        assertTrue(offered.none { "Copper" in it.split(" ") }, "a removed word was drawn: $offered")
        assertTrue(offered.toSet().size > 1)

        val response = client.createAccount("Calm Copper Heron", client.issueKeys())
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(AccountErrors.NICKNAME_NOT_ADMITTED, error(response))
        assertEquals(1, db.count("account"))
    }

    @Test
    fun `a nickname not built from the lists in adjective, adjective, noun order is not admitted`() = serve(db) {
        db.admit(listOf("brave", "copper"), listOf("otter"))
        for (nickname in listOf("Otter Brave Copper", "Brave Copper", "Brave Copper Otter Otter", "Brave Copper Walrus", "Brave Cöpper Otter", "")) {
            val response = client.createAccount(nickname, client.issueKeys())
            assertEquals(HttpStatusCode.BadRequest, response.status, "'$nickname'")
            assertEquals(AccountErrors.NICKNAME_NOT_ADMITTED, error(response))
        }
        assertEquals(0, db.count("account"))
    }

    @Test
    fun `with no words in the lists no nickname is offered`() {
        assertNull(NicknameGenerator(store).generate())
        db.admit(listOf("brave"), listOf("otter"))
        assertNotNull(NicknameGenerator(store).generate())
    }

    private fun heldNicknames(): List<String> =
        db.rows("select nickname_first || ' ' || nickname_second || ' ' || nickname_noun from account order by account_id") { it.getString(1) }

    private suspend fun error(response: HttpResponse): String =
        Json.decodeFromString<AccountErrorDto>(response.bodyAsText()).code
}
