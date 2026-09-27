package app.recipefork.server.attestation

/** What asking the attestation service for a device verdict came to. */
sealed interface VerdictOutcome {
    /** The service vouched for the device and reported its slots. */
    data class Obtained(val slots: DeviceSlots) : VerdictOutcome

    /**
     * No verdict: the service could not be reached, did not answer, or did not vouch for the device
     * (a sideloaded build, an emulator, a device without the store's services). AUTH-004 governs.
     */
    data object Unobtainable : VerdictOutcome
}

/**
 * The device attestation service (EXT-002), behind an interface so that tests and the local deployment
 * use [FakeAttestation] (ADR 20260927-test-strategy); the Play Integrity adapter is a later task.
 *
 * The device is reached only through the [token] the client obtained from the service for this request.
 * The product never learns a device identifier, and keeps nothing a verdict carried (AUTH-015): the slots
 * are read from the service each time they are needed, and a slot is occupied by writing to the service,
 * which records the month itself.
 */
interface AttestationService {
    /** The verdict for the device [token] was issued on. Never throws for an outage: that is [VerdictOutcome.Unobtainable]. */
    fun verdict(token: String): VerdictOutcome

    /**
     * Occupies [slot] on the device [token] was issued on, in the current month as the service records it.
     * False when the write could not be made.
     */
    fun occupy(token: String, slot: Int): Boolean
}

/** No attestation service configured: no verdict can ever be obtained, so every account created is muted (AUTH-004). */
object NoAttestation : AttestationService {
    override fun verdict(token: String): VerdictOutcome = VerdictOutcome.Unobtainable
    override fun occupy(token: String, slot: Int): Boolean = false
}

object Attestation {
    /** The environment variable naming the attestation service: `none` or `fake`. It has no default. */
    const val ENV = "ATTESTATION"

    private const val ACCEPTED = "expected one of 'none' (no attestation service: every account created is muted) or 'fake' (the in-memory fake, local deployment only)"

    /**
     * The service [env] names: `none` is [NoAttestation]; `fake` is a [FakeAttestation] holding its devices
     * in memory, for the local deployment only. Missing, blank or anything else is refused, naming [ENV]
     * and the accepted values — a server that silently ran without a verdict would create only muted
     * accounts (AUTH-001, AUTH-004).
     */
    fun fromEnvironment(env: Map<String, String>, clock: java.time.Clock): AttestationService {
        val raw = env[ENV] ?: throw IllegalStateException("$ENV is missing: $ACCEPTED")
        return when (val value = raw.trim()) {
            "" -> throw IllegalStateException("$ENV is blank: $ACCEPTED")
            "none" -> NoAttestation
            "fake" -> FakeAttestation(clock)
            else -> throw IllegalStateException("$ENV is '$value': $ACCEPTED")
        }
    }
}
