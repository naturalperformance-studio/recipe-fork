package app.recipefork.core.api

import kotlinx.serialization.Serializable

/**
 * A generated nickname offered on the first step of creation (AUTH-018, AUTH-024). Asking again
 * is a reroll. Nothing is stored by an offer: the nickname is consumed only when an account is
 * created with it.
 */
@Serializable
data class NicknameOfferDto(
    val nickname: String,
) {
    companion object {
        const val PATH = "/api/v1/accounts/nickname"
    }
}

/**
 * The three keys shown during creation (AUTH-007, AUTH-017, AUTH-020). Issuing them stores
 * nothing: an account holding them exists only once [CreateAccountDto] is sent with them.
 * [seal] is the server's proof that it issued exactly these three keys; the client keeps it with
 * the keys and sends it back unchanged. A set the server did not issue, or any change to one it
 * did, is refused.
 */
@Serializable
data class KeySetDto(
    val accountKey: String,
    val deletionKey: String,
    val recoveryKey: String,
    val seal: String,
) {
    companion object {
        const val PATH = "/api/v1/accounts/keys"
    }
}

/**
 * The confirming call of creation (AUTH-024 (4)): the accepted nickname, the version of the terms
 * of use accepted on the first step (LEG-004), and the keys shown on the later steps.
 */
@Serializable
data class CreateAccountDto(
    val nickname: String,
    val termsVersion: String,
    val keys: KeySetDto,
) {
    companion object {
        const val PATH = "/api/v1/accounts"
    }
}

/** The answer to a confirmed creation: the nickname the account now holds for good (DOM-001). */
@Serializable
data class AccountCreatedDto(
    val nickname: String,
)

/** Why an account request was refused; [code] is one of [AccountErrors]. */
@Serializable
data class AccountErrorDto(
    val code: String,
)

object AccountErrors {
    /** The body is not a [CreateAccountDto], or carries a field it does not define. */
    const val MALFORMED_REQUEST = "malformed_request"

    /** The nickname is not two adjectives and a noun from the current word lists. */
    const val NICKNAME_NOT_ADMITTED = "nickname_not_admitted"

    /** Another account already holds this nickname. */
    const val NICKNAME_TAKEN = "nickname_taken"

    /** The terms version is not a version identifier. */
    const val TERMS_VERSION_INVALID = "terms_version_invalid"

    /**
     * The key set is not one this server issued, or was changed after issue: a key is not a
     * well-formed key of its kind, one key is given for two kinds, or the seal does not match.
     */
    const val KEY_INVALID = "key_invalid"

    /** The issued key set has already been used to create an account. */
    const val KEY_TAKEN = "key_taken"

    /** No nickname could be generated that no account holds. */
    const val NO_NICKNAME_AVAILABLE = "no_nickname_available"
}
