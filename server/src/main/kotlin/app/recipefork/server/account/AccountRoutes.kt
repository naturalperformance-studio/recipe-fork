package app.recipefork.server.account

import app.recipefork.core.api.AccountCreatedDto
import app.recipefork.core.api.AccountErrorDto
import app.recipefork.core.api.AccountErrors
import app.recipefork.core.api.CreateAccountDto
import app.recipefork.core.api.KeySetDto
import app.recipefork.core.api.NicknameOfferDto
import app.recipefork.server.attestation.AttestationService
import app.recipefork.server.attestation.NoAttestation
import app.recipefork.server.attestation.VerdictOutcome
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * The server's half of creating an account (AUTH-024). The first two calls hand out what the
 * steps show — a nickname, rerolled as often as asked, and the three keys sealed by [seal] — and
 * store nothing. Only the confirming call creates the account (AUTH-007 (7)), and only with a key
 * set this server issued.
 *
 * The confirming call asks [attestation] for the device's verdict (AUTH-001): with a free slot the account
 * is full and occupies that slot; with no verdict it is muted and occupies none (AUTH-004); with no free
 * slot nothing is created (AUTH-003). The month is [clock]'s, in UTC.
 */
fun Route.accountRoutes(
    store: AccountStore,
    nicknames: NicknameGenerator = NicknameGenerator(store),
    seal: KeySetSeal,
    keys: KeyIssuer = KeyIssuer(),
    attestation: AttestationService = NoAttestation,
    clock: Clock = Clock.systemUTC(),
) {
    post(NicknameOfferDto.PATH) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        val nickname = nicknames.generate()
            ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, AccountErrorDto(AccountErrors.NO_NICKNAME_AVAILABLE))
        call.respond(NicknameOfferDto(nickname.display))
    }

    post(KeySetDto.PATH) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        val set = keys.issueSet()
        call.respond(KeySetDto(set.account.text, set.deletion.text, set.recovery.text, seal.seal(set)))
    }

    post(CreateAccountDto.PATH) {
        val request = try {
            StrictJson.decodeFromString<CreateAccountDto>(call.receiveText())
        } catch (_: SerializationException) {
            return@post refuse(HttpStatusCode.BadRequest, AccountErrors.MALFORMED_REQUEST)
        } catch (_: IllegalArgumentException) {
            return@post refuse(HttpStatusCode.BadRequest, AccountErrors.MALFORMED_REQUEST)
        }

        val nickname = Nickname.parse(request.nickname)
            ?: return@post refuse(HttpStatusCode.BadRequest, AccountErrors.NICKNAME_NOT_ADMITTED)
        if (!TERMS_VERSION.matches(request.termsVersion)) {
            return@post refuse(HttpStatusCode.BadRequest, AccountErrors.TERMS_VERSION_INVALID)
        }
        // Only a set this server issued is accepted, and it is checked before any key is looked
        // up, so a self-made or altered set learns nothing about which keys are held.
        val offered = request.keys
        if (!seal.verifies(offered.accountKey, offered.deletionKey, offered.recoveryKey, offered.seal)) {
            return@post refuse(HttpStatusCode.BadRequest, AccountErrors.KEY_INVALID)
        }
        val account = Keys.parse(KeyKind.ACCOUNT, request.keys.accountKey)
        val deletion = Keys.parse(KeyKind.DELETION, request.keys.deletionKey)
        val recovery = Keys.parse(KeyKind.RECOVERY, request.keys.recoveryKey)
        if (account == null || deletion == null || recovery == null) {
            return@post refuse(HttpStatusCode.BadRequest, AccountErrors.KEY_INVALID)
        }
        val set = KeySet(account, deletion, recovery)
        if (set.all.map { it.bytes.toList() }.toSet().size != set.all.size) {
            return@post refuse(HttpStatusCode.BadRequest, AccountErrors.KEY_INVALID)
        }

        val token = request.attestationToken
        val verdict = token?.let { attestation.verdictOrNone(it) } ?: VerdictOutcome.Unobtainable
        var result = when (verdict) {
            // No verdict while the server is reached: a muted account, occupying no slot (AUTH-004).
            VerdictOutcome.Unobtainable -> store.create(nickname, request.termsVersion, set)
            is VerdictOutcome.Obtained -> {
                val deviceToken = checkNotNull(token)
                val now = YearMonth.now(clock.withZone(ZoneOffset.UTC))
                val slot = verdict.slots.free(now).firstOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.Conflict,
                        AccountErrorDto(AccountErrors.NO_FREE_SLOT, verdict.slots.nextFreeMonth(now).toString()),
                    )
                // A full account occupies one slot, written only once the account is certain to be created.
                store.create(nickname, request.termsVersion, set) { attestation.occupyOrNot(deviceToken, slot) }
            }
        }
        // The verdict was obtained but the slot could not be written: no verdict holds, so AUTH-004 governs.
        if (result == CreationResult.SlotNotOccupied) result = store.create(nickname, request.termsVersion, set)

        when (result) {
            is CreationResult.Created ->
                call.respond(HttpStatusCode.Created, AccountCreatedDto(result.nickname.display, muted = result.muted))
            CreationResult.NicknameNotAdmitted -> refuse(HttpStatusCode.BadRequest, AccountErrors.NICKNAME_NOT_ADMITTED)
            CreationResult.NicknameTaken -> refuse(HttpStatusCode.Conflict, AccountErrors.NICKNAME_TAKEN)
            CreationResult.KeyTaken -> refuse(HttpStatusCode.Conflict, AccountErrors.KEY_TAKEN)
            CreationResult.SlotNotOccupied -> error("a creation without a slot does not occupy one")
        }
    }
}

/** The service's verdict; an adapter's failure is an outage like any other (EXT-002). */
private fun AttestationService.verdictOrNone(token: String): VerdictOutcome =
    try {
        verdict(token)
    } catch (_: Exception) {
        VerdictOutcome.Unobtainable
    }

private fun AttestationService.occupyOrNot(token: String, slot: Int): Boolean =
    try {
        occupy(token, slot)
    } catch (_: Exception) {
        false
    }

/** A version identifier, and nothing that could be free text about a person (LEG-004, RET-010). */
internal val TERMS_VERSION = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$")

/** Refuses a body carrying any field the contract does not define, so nothing else reaches the store (CON-001). */
private val StrictJson = Json { ignoreUnknownKeys = false }

private suspend fun RoutingContext.refuse(status: HttpStatusCode, code: String) =
    call.respond(status, AccountErrorDto(code))
