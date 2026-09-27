package app.recipefork.server.attestation

import app.recipefork.server.account.KeySetSeal
import app.recipefork.server.configuredServer
import org.junit.jupiter.api.Test
import java.io.File
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The fake stands for the service's three per-device bits and their write months, and for its outages. */
class FakeAttestationTest {
    private val clock = MonthClock(YearMonth.of(2026, 4))
    private val fake = FakeAttestation(clock)
    private val phone = FakeAttestation.tokenFor("phone")

    @Test
    fun `a write occupies the slot in the month the service records, on that device only`() {
        assertTrue(fake.occupy(phone, 1))

        assertEquals(DeviceSlots(listOf(null, YearMonth.of(2026, 4), null)), fake.slotsOf("phone"))
        assertEquals(DeviceSlots.NONE_OCCUPIED, fake.slotsOf("tablet"))
        assertEquals(VerdictOutcome.Obtained(fake.slotsOf("phone")), fake.verdict(phone))

        // A slot written again is occupied from the month of the new write.
        clock.month = YearMonth.of(2026, 11)
        assertTrue(fake.occupy(phone, 1))
        assertEquals(YearMonth.of(2026, 11), fake.slotsOf("phone").occupiedIn(1))
    }

    @Test
    fun `an unreachable service gives no verdict and takes no write, and a token it did not issue gets no verdict`() {
        fake.reachable = false
        assertEquals(VerdictOutcome.Unobtainable, fake.verdict(phone))
        assertFalse(fake.occupy(phone, 0))
        assertEquals(DeviceSlots.NONE_OCCUPIED, fake.slotsOf("phone"))

        fake.reachable = true
        for (token in listOf("", "phone", "fake-device:", "fake-device:a b", "sideloaded-build")) {
            assertEquals(VerdictOutcome.Unobtainable, fake.verdict(token), "'$token'")
        }
    }

    @Test
    fun `the service is none when ATTESTATION is none, the fake when it is fake, and an unknown value is refused`() {
        assertSame(NoAttestation, Attestation.fromEnvironment(mapOf(Attestation.ENV to "none"), clock))
        assertTrue(Attestation.fromEnvironment(mapOf(Attestation.ENV to "fake"), clock) is FakeAttestation)
        assertAcceptedValuesListed(assertContains(refusal("play"), "ATTESTATION is 'play'"))
        assertEquals(VerdictOutcome.Unobtainable, NoAttestation.verdict(phone))
    }

    @Test
    fun `a missing ATTESTATION is refused, naming it as missing and listing the accepted values`() {
        assertAcceptedValuesListed(assertContains(refusal(null), "ATTESTATION is missing"))
    }

    @Test
    fun `a blank ATTESTATION is refused, naming it as blank and listing the accepted values`() {
        assertAcceptedValuesListed(assertContains(refusal(""), "ATTESTATION is blank"))
        assertAcceptedValuesListed(assertContains(refusal("   "), "ATTESTATION is blank"))
    }

    @Test
    fun `the server refuses to start with a missing or blank ATTESTATION, before it reaches its store`() {
        val secret = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val env = mapOf(
            "DATABASE_URL" to "jdbc:postgresql://nowhere/none", "DATABASE_USER" to "u", "DATABASE_PASSWORD" to "p",
            KeySetSeal.SECRET_ENV to secret,
        )
        for ((attestation, expected) in listOf(null to "ATTESTATION is missing", "" to "ATTESTATION is blank", " " to "ATTESTATION is blank")) {
            val failure = assertFailsWith<IllegalStateException>("ATTESTATION '$attestation'") {
                configuredServer(env + listOfNotNull(attestation?.let { Attestation.ENV to it })) {
                    throw AssertionError("the server reached its store before refusing its configuration")
                }
            }
            assertAcceptedValuesListed(assertContains(failure.message.orEmpty(), expected))
        }
    }

    /** The message [Attestation.fromEnvironment] refuses [value] with (absent when null). */
    private fun refusal(value: String?): String =
        assertFailsWith<IllegalStateException>("ATTESTATION '$value'") {
            Attestation.fromEnvironment(listOfNotNull(value?.let { Attestation.ENV to it }).toMap(), clock)
        }.message.orEmpty()

    private fun assertContains(message: String, expected: String): String {
        kotlin.test.assertContains(message, expected)
        return message
    }

    private fun assertAcceptedValuesListed(message: String) {
        kotlin.test.assertContains(message, "'none'")
        kotlin.test.assertContains(message, "'fake'")
    }

    @Test
    fun `the local deployment runs the server with the fake`() {
        val line = File("../deploy/local/compose.yml").readLines().single { it.trim().startsWith("${Attestation.ENV}:") }
        val value = line.substringAfter(':').trim()
        assertTrue(Attestation.fromEnvironment(mapOf(Attestation.ENV to value), clock) is FakeAttestation)
    }
}
