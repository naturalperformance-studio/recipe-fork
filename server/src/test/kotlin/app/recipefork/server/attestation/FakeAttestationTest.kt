package app.recipefork.server.attestation

import org.junit.jupiter.api.Test
import java.io.File
import java.time.YearMonth
import kotlin.test.assertContains
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
    fun `the service is none unless configured, the fake when ATTESTATION is fake, and an unknown value is refused`() {
        assertSame(NoAttestation, Attestation.fromEnvironment(emptyMap(), clock))
        assertSame(NoAttestation, Attestation.fromEnvironment(mapOf(Attestation.ENV to "none"), clock))
        assertTrue(Attestation.fromEnvironment(mapOf(Attestation.ENV to "fake"), clock) is FakeAttestation)
        assertContains(
            assertFailsWith<IllegalStateException> { Attestation.fromEnvironment(mapOf(Attestation.ENV to "play"), clock) }.message.orEmpty(),
            "ATTESTATION",
        )
        assertEquals(VerdictOutcome.Unobtainable, NoAttestation.verdict(phone))
    }

    @Test
    fun `the local deployment runs the server with the fake`() {
        val line = File("../deploy/local/compose.yml").readLines().single { it.trim().startsWith("${Attestation.ENV}:") }
        val value = line.substringAfter(':').trim()
        assertTrue(Attestation.fromEnvironment(mapOf(Attestation.ENV to value), clock) is FakeAttestation)
    }
}
