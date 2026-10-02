package app.recipefork.server.attestation

import java.time.YearMonth

/**
 * A device's three account slots as a verdict reports them (AUTH-002): for each slot, the month it was
 * last occupied in, or null if no account was ever created into it. A slot is free again once six or
 * more calendar months have passed since the month it was occupied in, so a slot occupied in March 2026
 * is free from September 2026 and not before. Freeing is read from the month alone: nothing is written
 * when a slot frees, and nothing about the account that occupied it is involved.
 *
 * This is read from the attestation service for each creation and never kept by the product (AUTH-015).
 */
class DeviceSlots(occupiedIn: List<YearMonth?>) {
    private val months: List<YearMonth?> = occupiedIn.toList()

    init {
        require(months.size == COUNT) { "a device has exactly $COUNT slots" }
    }

    /** The month [slot] was occupied in, or null if it never was. */
    fun occupiedIn(slot: Int): YearMonth? = months[slot]

    /** Whether [slot] is free in the month [now]. */
    fun isFree(slot: Int, now: YearMonth): Boolean {
        val occupied = months[slot] ?: return true
        return !occupied.plusMonths(FREE_AFTER_MONTHS).isAfter(now)
    }

    /** The slots free in the month [now], in slot order. */
    fun free(now: YearMonth): List<Int> = (0 until COUNT).filter { isFree(it, now) }

    /** The month the earliest occupied slot frees in, when none is free in [now]; null when one is free. */
    fun nextFreeMonth(now: YearMonth): YearMonth? =
        if (free(now).isNotEmpty()) null else months.filterNotNull().minOf { it.plusMonths(FREE_AFTER_MONTHS) }

    override fun equals(other: Any?): Boolean = other is DeviceSlots && other.months == months
    override fun hashCode(): Int = months.hashCode()
    override fun toString(): String = "DeviceSlots$months"

    companion object {
        /** A device holds three slots (AUTH-002). */
        const val COUNT = 3

        /** A slot frees six calendar months after the month it was occupied in (AUTH-002). */
        const val FREE_AFTER_MONTHS = 6L

        /** A device on which no account was ever created: every slot free. */
        val NONE_OCCUPIED = DeviceSlots(List(COUNT) { null })
    }
}
