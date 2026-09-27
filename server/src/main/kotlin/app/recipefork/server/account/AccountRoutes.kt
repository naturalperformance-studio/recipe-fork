package app.recipefork.server.account

import app.recipefork.core.api.AccountCreatedDto
import app.recipefork.core.api.AccountErrorDto
import app.recipefork.core.api.AccountErrors
import app.recipefork.core.api.CreateAccountDto
import app.recipefork.core.api.KeySetDto
import app.recipefork.core.api.NicknameOfferDto
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

/**
 * The server's half of creating an account (AUTH-024). The first two calls hand out what the
 * steps show — a nickname, rerolled as often as asked, and the three keys — and store nothing.
 * Only the confirming call creates the account (AUTH-007 (7)).
 */
fun Route.accountRoutes(
    store: AccountStore,
    nicknames: NicknameGenerator = NicknameGenerator(store),
    keys: KeyIssuer = KeyIssuer(),
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
        call.respond(KeySetDto(set.account.text, set.deletion.text, set.recovery.text))
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

        when (val result = store.create(nickname, request.termsVersion, set)) {
            is CreationResult.Created -> call.respond(HttpStatusCode.Created, AccountCreatedDto(result.nickname.display))
            CreationResult.NicknameNotAdmitted -> refuse(HttpStatusCode.BadRequest, AccountErrors.NICKNAME_NOT_ADMITTED)
            CreationResult.NicknameTaken -> refuse(HttpStatusCode.Conflict, AccountErrors.NICKNAME_TAKEN)
            CreationResult.KeyTaken -> refuse(HttpStatusCode.Conflict, AccountErrors.KEY_TAKEN)
        }
    }
}

/** A version identifier, and nothing that could be free text about a person (LEG-004, RET-010). */
internal val TERMS_VERSION = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$")

/** Refuses a body carrying any field the contract does not define, so nothing else reaches the store (CON-001). */
private val StrictJson = Json { ignoreUnknownKeys = false }

private suspend fun RoutingContext.refuse(status: HttpStatusCode, code: String) =
    call.respond(status, AccountErrorDto(code))
