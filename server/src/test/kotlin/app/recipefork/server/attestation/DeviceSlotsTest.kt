package app.recipefork.server.attestation

import org.junit.jupiter.api.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A device's three slots and the month each frees in (AUTH-002), as read from a verdict. */
class DeviceSlotsTest {
    private val march2026 = YearMonth.of(2026, 3)

    @Test
    fun `AUTH-002 (1) - a device on which no account has ever been created reads three free slots`() {
        val fake = FakeAttestation(MonthClock(YearMonth.of(2026, 9)))
        val verdict = fake.verdict(FakeAttestation.tokenFor("never-used"))
        val slots = (verdict as VerdictOutcome.Obtained).slots

        assertEquals(listOf(0, 1, 2), slots.free(YearMonth.of(2026, 9)))
        assertEquals(DeviceSlots.NONE_OCCUPIED, slots)
    }

    @Test
    fun `AUTH-002 (2) - a slot occupied in March 2026 is free in September 2026 and later`() {
        val slots = DeviceSlots(listOf(march2026, null, null))

        for (month in listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2027, 3), YearMonth.of(2030, 1))) {
            assertTrue(slots.isFree(0, month), "$month")
        }
    }

    @Test
    fun `AUTH-002 (3) - a slot occupied in March 2026 is not free in August 2026`() {
        val slots = DeviceSlots(listOf(march2026, null, null))

        assertFalse(slots.isFree(0, YearMonth.of(2026, 8)))
        // Nor in any month before it.
        for (month in 3..7) assertFalse(slots.isFree(0, YearMonth.of(2026, month)), "2026-$month")
        assertEquals(listOf(1, 2), slots.free(YearMonth.of(2026, 8)))
    }

    @Test
    fun `AUTH-002 (4) - three slots occupied in the same month are all free at once six months later`() {
        val slots = DeviceSlots(listOf(march2026, march2026, march2026))

        assertEquals(emptyList(), slots.free(YearMonth.of(2026, 8)))
        assertEquals(YearMonth.of(2026, 9), slots.nextFreeMonth(YearMonth.of(2026, 8)))
        assertEquals(listOf(0, 1, 2), slots.free(YearMonth.of(2026, 9)))
    }

    @Test
    fun `the month named when no slot is free is the month the earliest occupied slot frees in`() {
        val slots = DeviceSlots(listOf(YearMonth.of(2026, 7), march2026, YearMonth.of(2026, 5)))

        assertEquals(YearMonth.of(2026, 9), slots.nextFreeMonth(YearMonth.of(2026, 6)))
        assertEquals(null, slots.nextFreeMonth(YearMonth.of(2026, 9)))
    }
}
