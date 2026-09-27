package app.recipefork.server.account

import app.recipefork.server.attestation.FakeAttestation
import app.recipefork.server.attestation.MonthClock
import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nothing from a device verdict, and nothing that identifies a device, is kept (AUTH-015, CON-001): after
 * accounts are created through the gate, the store is enumerated — every table, every column, every value.
 */
class VerdictNotKeptTest {
    private lateinit var db: HikariDataSource

    // Months no other stored value can carry: the service's write months for this device, and the month
    // the creations happen in.
    private val clock = MonthClock(YearMonth.of(2031, 5))
    private val fake = FakeAttestation(clock)
    private val device = "pixel-7f3a9c"
    private val token = FakeAttestation.tokenFor(device)

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
        db.admit(listOf("brave", "copper"), listOf("otter"))
        fake.write(device, 1, YearMonth.of(2029, 1))
        fake.write(device, 2, YearMonth.of(2030, 2))
    }

    @AfterEach
    fun tearDown() = db.close()

    /** Every non-binary value in every table of the store, as text, with the column it came from. */
    private fun everyStoredValue(): List<Pair<String, String>> {
        val columns = db.rows(
            """
            select table_name, column_name from information_schema.columns
            where table_schema = current_schema() and data_type <> 'bytea' and table_name <> 'flyway_schema_history'
            order by table_name, column_name
            """.trimIndent(),
        ) { it.getString(1) to it.getString(2) }
        return columns.flatMap { (table, column) ->
            db.rows("select \"$column\"::text from \"$table\" where \"$column\" is not null") { "$table.$column" to it.getString(1) }
        }
    }

    private fun createThroughTheGate() = serve(db, attestation = fake, clock = clock) {
        val response = client.createAccount("Brave Copper Otter", client.issueKeys(), attestationToken = token)
        assertEquals(HttpStatusCode.Created, response.status)
        // The verdict was received and checked: a slot on the device was occupied through it.
        assertEquals(YearMonth.of(2031, 5), fake.slotsOf(device).occupiedIn(0))
    }

    @Test
    fun `AUTH-015 (1) - the stored record of an account created through a checked verdict holds no field from that verdict`() {
        createThroughTheGate()

        // The account's record is the fields it had before any verdict was involved, and nothing more.
        val fields = db.rows(
            """
            select table_name, column_name from information_schema.columns
            where table_schema = current_schema() and table_name in ('account', 'account_key')
            order by table_name, column_name
            """.trimIndent(),
        ) { "${it.getString(1)}.${it.getString(2)}" }
        assertEquals(
            listOf(
                "account.account_id", "account.nickname_first", "account.nickname_noun", "account.nickname_second",
                "account.terms_accepted_on", "account.terms_version",
                "account_key.account_id", "account_key.digest", "account_key.kind",
            ),
            fields,
        )

        // No value anywhere in the store carries the verdict's slot state: its write months, or the month
        // the slot was occupied in.
        val values = everyStoredValue()
        assertTrue(values.isNotEmpty())
        for (month in listOf("2029", "2030", "2031")) {
            assertEquals(emptyList(), values.filter { (_, v) -> month in v }, month)
        }
    }

    @Test
    fun `AUTH-015 (2) - the stored data of any account holds nothing that identifies a device`() {
        createThroughTheGate()
        // A muted account too, from the same device, with the service unreachable.
        fake.reachable = false
        serve(db, attestation = fake, clock = clock) {
            assertEquals(HttpStatusCode.Created, client.createAccount("Copper Brave Otter", client.issueKeys(), attestationToken = token).status)
        }
        assertEquals(2, db.count("account"))

        // No table is kept for devices, slots or verdicts.
        val tables = db.rows(
            "select table_name from information_schema.tables where table_schema = current_schema() order by table_name",
        ) { it.getString(1) }
        for (word in listOf("device", "slot", "verdict", "attest", "integrity", "token")) {
            assertEquals(emptyList(), tables.filter { word in it }, word)
        }

        // No value anywhere in the store is, or contains, the device's token or the device it stands for.
        val values = everyStoredValue()
        assertEquals(emptyList(), values.filter { (_, v) -> device in v || token in v || "fake-device" in v })

        // The binary values are the keys' digests, and only those: three 32-byte digests per account.
        val binary = db.rows(
            """
            select table_name, column_name from information_schema.columns
            where table_schema = current_schema() and data_type = 'bytea' order by table_name, column_name
            """.trimIndent(),
        ) { "${it.getString(1)}.${it.getString(2)}" }
        assertEquals(listOf("account_key.digest"), binary)
        assertEquals(6, db.count("account_key"))
    }
}
