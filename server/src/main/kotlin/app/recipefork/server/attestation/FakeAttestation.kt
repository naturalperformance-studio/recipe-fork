package app.recipefork.server.attestation

import java.time.Clock
import java.time.YearMonth

/**
 * A stand-in for the attestation service's per-device state, for tests and the local deployment
 * (ADR 20260927-test-strategy). Like device recall, it holds three bits per device, each with the month it
 * was last written in; a set bit is an occupied slot and its write month is the month it was occupied in.
 * The state lives here, in the "service", never in the product's store (AUTH-015).
 *
 * A token is `fake-device:<label>`, the label standing for one device; any other token gets no verdict.
 * [reachable] set to false models an outage: no verdict, and no write.
 */
class FakeAttestation(private val clock: Clock) : AttestationService {
    private val devices = HashMap<String, Array<YearMonth?>>()

    @Volatile
    var reachable: Boolean = true

    @Synchronized
    override fun verdict(token: String): VerdictOutcome {
        if (!reachable) return VerdictOutcome.Unobtainable
        val device = deviceOf(token) ?: return VerdictOutcome.Unobtainable
        return VerdictOutcome.Obtained(slotsOf(device))
    }

    @Synchronized
    override fun occupy(token: String, slot: Int): Boolean {
        if (!reachable) return false
        val device = deviceOf(token) ?: return false
        if (slot !in 0 until DeviceSlots.COUNT) return false
        write(device, slot, YearMonth.now(clock))
        return true
    }

    /** The device's slots as the service holds them. */
    @Synchronized
    fun slotsOf(device: String): DeviceSlots = DeviceSlots(bits(device).toList())

    /** Sets [slot] of [device] as written in [month], as an earlier creation on that device would have. */
    @Synchronized
    fun write(device: String, slot: Int, month: YearMonth) {
        bits(device)[slot] = month
    }

    private fun bits(device: String): Array<YearMonth?> = devices.getOrPut(device) { arrayOfNulls(DeviceSlots.COUNT) }

    companion object {
        const val TOKEN_PREFIX = "fake-device:"
        private val LABEL = Regex("^[A-Za-z0-9-]{1,64}$")

        /** The token a client on [device] would send. */
        fun tokenFor(device: String): String {
            require(LABEL.matches(device)) { "a device label is 1 to 64 letters, digits or hyphens" }
            return TOKEN_PREFIX + device
        }

        private fun deviceOf(token: String): String? =
            token.takeIf { it.startsWith(TOKEN_PREFIX) }?.removePrefix(TOKEN_PREFIX)?.takeIf { LABEL.matches(it) }
    }
}
