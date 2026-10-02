package app.recipefork.server.account

import app.recipefork.core.api.AccountCreatedDto
import app.recipefork.core.api.AccountErrorDto
import app.recipefork.core.api.AccountErrors
import app.recipefork.server.attestation.AttestationService
import app.recipefork.server.attestation.DeviceSlots
import app.recipefork.server.attestation.FakeAttestation
import app.recipefork.server.attestation.MonthClock
import app.recipefork.server.attestation.VerdictOutcome
import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.YearMonth
import kotlin.test.assertEquals

/**
 * Creating an account through the gate: a full account occupies a slot on the device with its month
 * (AUTH-001), slots free six months later (AUTH-002), and with no verdict the account is muted and
 * occupies none (AUTH-004).
 */
class DeviceSlotTest {
    private lateinit var db: HikariDataSource
    private val clock = MonthClock(YearMonth.of(2026, 3))
    private val fake = FakeAttestation(clock)
    private val phone = FakeAttestation.tokenFor("kitchen-phone")

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
        db.admit(listOf("brave", "copper", "quiet", "amber"), listOf("otter", "heron"))
    }

    @AfterEach
    fun tearDown() = db.close()

    private suspend fun HttpClient.create(token: String?): HttpResponse =
        createAccount(offerNickname(), issueKeys(), attestationToken = token)

    private suspend fun HttpResponse.created(): AccountCreatedDto {
        assertEquals(HttpStatusCode.Created, status, bodyAsText())
        return Json.decodeFromString(bodyAsText())
    }

    private suspend fun HttpResponse.refusal(): AccountErrorDto = Json.decodeFromString(bodyAsText())

    @Test
    fun `AUTH-001 (3) - a full account occupies one slot on the device, with the month it was occupied in`() =
        serve(db, attestation = fake, clock = clock) {
            val created = client.create(phone).created()

            assertEquals(false, created.muted)
            assertEquals(DeviceSlots(listOf(YearMonth.of(2026, 3), null, null)), fake.slotsOf("kitchen-phone"))
            assertEquals(1, db.count("account"))

            // The next full account on the device occupies the next slot, in its own month.
            clock.month = YearMonth.of(2026, 5)
            assertEquals(false, client.create(phone).created().muted)
            assertEquals(DeviceSlots(listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 5), null)), fake.slotsOf("kitchen-phone"))
        }

    @Test
    fun `AUTH-001 (3), AUTH-004 (6) - a muted account occupies no slot on the device`() =
        serve(db, attestation = fake, clock = clock) {
            fake.reachable = false
            val created = client.create(phone).created()

            assertEquals(true, created.muted)
            fake.reachable = true
            assertEquals(DeviceSlots.NONE_OCCUPIED, fake.slotsOf("kitchen-phone"))
            assertEquals(1, db.count("account"))

            // A device with every slot occupied still gets no further slot occupied by a muted account.
            listOf(0, 1, 2).forEach { fake.write("kitchen-phone", it, YearMonth.of(2026, 1)) }
            val before = fake.slotsOf("kitchen-phone")
            fake.reachable = false
            assertEquals(true, client.create(phone).created().muted)
            assertEquals(before, fake.slotsOf("kitchen-phone"))
        }

    @Test
    fun `AUTH-004 (6) - a muted account is created without any slot being written, even where a write would land`() {
        // A service that gives no verdict for the device, but would take a write to it if one were made.
        val writes = mutableListOf<Int>()
        val noVerdict = object : AttestationService {
            override fun verdict(token: String): VerdictOutcome = VerdictOutcome.Unobtainable
            override fun occupy(token: String, slot: Int): Boolean {
                writes += slot
                return fake.occupy(token, slot)
            }
        }
        serve(db, attestation = noVerdict, clock = clock) {
            assertEquals(true, client.create(phone).created().muted)
            assertEquals(emptyList(), writes)
            assertEquals(DeviceSlots.NONE_OCCUPIED, fake.slotsOf("kitchen-phone"))
            assertEquals(1, db.count("account"))
        }
    }

    @Test
    fun `AUTH-001 (5) - with the server reached and no verdict obtainable, the account created is a muted one (AUTH-004)`() =
        serve(db, attestation = fake, clock = clock) {
            // The service is unreachable.
            fake.reachable = false
            assertEquals(true, client.create(phone).created().muted)

            // The device obtained no token at all, as a sideloaded build does not.
            fake.reachable = true
            assertEquals(true, client.create(null).created().muted)

            // The service does not vouch for what the device sent.
            assertEquals(true, client.create("not-a-token-the-service-issued").created().muted)

            // In every case an account exists, and no slot anywhere was occupied.
            assertEquals(3, db.count("account"))
            assertEquals(9, db.count("account_key"))
            assertEquals(DeviceSlots.NONE_OCCUPIED, fake.slotsOf("kitchen-phone"))
        }

    @Test
    fun `AUTH-001 (5) - a verdict obtained but a slot that cannot be written leaves no verdict, so the account is muted`() {
        val failingWrites = object : AttestationService {
            var writes = 0
            override fun verdict(token: String): VerdictOutcome = fake.verdict(token)
            override fun occupy(token: String, slot: Int): Boolean {
                writes++
                throw IllegalStateException("the service went away")
            }
        }
        serve(db, attestation = failingWrites, clock = clock) {
            assertEquals(true, client.create(phone).created().muted)
            assertEquals(1, failingWrites.writes)
            assertEquals(1, db.count("account"))
            assertEquals(DeviceSlots.NONE_OCCUPIED, fake.slotsOf("kitchen-phone"))
        }
    }

    @Test
    fun `AUTH-002 (1) - a device on which no account was ever created creates three full accounts, each into a free slot`() =
        serve(db, attestation = fake, clock = clock) {
            repeat(3) { assertEquals(false, client.create(phone).created().muted) }
            assertEquals(DeviceSlots(List(3) { YearMonth.of(2026, 3) }), fake.slotsOf("kitchen-phone"))
            assertEquals(3, db.count("account"))
        }

    @Test
    fun `AUTH-001, AUTH-003 - with no free slot nothing is created, muted or otherwise, and the month a slot frees is named`() =
        serve(db, attestation = fake, clock = clock) {
            repeat(3) { client.create(phone).created() }

            val refused = client.create(phone)
            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals(AccountErrorDto(AccountErrors.NO_FREE_SLOT, "2026-09"), refused.refusal())
            assertEquals(3, db.count("account"))
            assertEquals(9, db.count("account_key"))
        }

    @Test
    fun `AUTH-002 (2, 3) - a slot occupied in March 2026 is not free in August 2026 and is free in September 2026`() =
        serve(db, attestation = fake, clock = clock) {
            // Slot 0 is occupied in March; the other two later, so that only slot 0 can free by September.
            assertEquals(false, client.create(phone).created().muted)
            clock.month = YearMonth.of(2026, 6)
            repeat(2) { client.create(phone).created() }

            clock.month = YearMonth.of(2026, 8)
            val refused = client.create(phone)
            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals(AccountErrorDto(AccountErrors.NO_FREE_SLOT, "2026-09"), refused.refusal())

            clock.month = YearMonth.of(2026, 9)
            assertEquals(false, client.create(phone).created().muted)
            assertEquals(
                DeviceSlots(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 6), YearMonth.of(2026, 6))),
                fake.slotsOf("kitchen-phone"),
            )
            assertEquals(4, db.count("account"))
        }

    @Test
    fun `AUTH-002 (4) - three slots occupied in the same month are all free at once six months later`() =
        serve(db, attestation = fake, clock = clock) {
            repeat(3) { client.create(phone).created() }

            clock.month = YearMonth.of(2026, 8)
            assertEquals(HttpStatusCode.Conflict, client.create(phone).status)

            clock.month = YearMonth.of(2026, 9)
            repeat(3) { assertEquals(false, client.create(phone).created().muted) }
            assertEquals(DeviceSlots(List(3) { YearMonth.of(2026, 9) }), fake.slotsOf("kitchen-phone"))
            assertEquals(6, db.count("account"))
        }

    @Test
    fun `AUTH-002 (5) - creating an account into a freed slot leaves the account that occupied it before untouched`() =
        serve(db, attestation = fake, clock = clock) {
            val earlier = List(3) { client.create(phone).created().nickname }
            val stored = snapshot()
            assertEquals(3, stored.accounts.size)

            clock.month = YearMonth.of(2026, 9)
            val later = client.create(phone).created().nickname

            val after = snapshot()
            // Every account created before is still there, byte for byte, with the same keys.
            assertEquals(stored.accounts, after.accounts.filter { it.id in stored.accounts.map { a -> a.id } })
            assertEquals(stored.keys, after.keys.filter { it.accountId in stored.accounts.map { a -> a.id } })
            assertEquals(4, after.accounts.size)
            assertEquals(12, after.keys.size)
            assertEquals(false, later in earlier)

            // And each can still be told apart from the new one by its keys and nickname, so none was replaced.
            assertEquals(3, after.accounts.count { it.nickname in earlier })
        }

    private data class StoredAccount(val id: Long, val nickname: String, val termsVersion: String, val termsAcceptedOn: String)
    private data class StoredKey(val accountId: Long, val digest: String, val kind: String)
    private data class Snapshot(val accounts: List<StoredAccount>, val keys: List<StoredKey>)

    private fun snapshot() = Snapshot(
        accounts = db.rows(
            "select account_id, nickname_first, nickname_second, nickname_noun, terms_version, terms_accepted_on::text from account order by account_id",
        ) {
            StoredAccount(
                it.getLong(1),
                Nickname(it.getString(2), it.getString(3), it.getString(4)).display,
                it.getString(5),
                it.getString(6),
            )
        },
        keys = db.rows("select account_id, digest, kind from account_key order by account_id, kind") {
            StoredKey(it.getLong(1), it.getBytes(2).hex(), it.getString(3))
        },
    )
}
