package app.recipefork.server.account

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Proves that a key set was issued by this server (ADR 20260927-keys-are-hashed-random-secrets:
 * the server generates the keys). Issuing a set returns an HMAC-SHA256 over its three keys under a
 * server secret; the confirming call accepts only a set whose seal matches. Nothing of the attempt
 * is stored (AUTH-007 (7)) — the secret is configuration — so the same issued set stays valid
 * however often the holder goes back and forward between the steps (AUTH-024 (6)).
 */
class KeySetSeal(secret: ByteArray) {
    init {
        require(secret.size >= MIN_SECRET_BYTES) { "the key set secret must be at least $MIN_SECRET_BYTES bytes" }
    }

    private val key = SecretKeySpec(secret.copyOf(), ALGORITHM)

    /** The seal of the set whose keys read [account], [deletion] and [recovery]. */
    fun seal(account: String, deletion: String, recovery: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(mac(account, deletion, recovery))

    fun seal(set: KeySet): String = seal(set.account.text, set.deletion.text, set.recovery.text)

    /** Whether [seal] is this server's seal over exactly these three keys, in these places. */
    fun verifies(account: String, deletion: String, recovery: String, seal: String): Boolean {
        val given = try {
            Base64.getUrlDecoder().decode(seal)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return MessageDigest.isEqual(mac(account, deletion, recovery), given)
    }

    private fun mac(account: String, deletion: String, recovery: String): ByteArray =
        Mac.getInstance(ALGORITHM).run {
            init(key)
            // Each key is written with its length, so no two different sets share an input.
            for (text in listOf(account, deletion, recovery)) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
                update(':'.code.toByte())
                update(bytes)
            }
            doFinal()
        }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        const val MIN_SECRET_BYTES = 32

        /** The environment variable carrying the secret, base64-encoded. */
        const val SECRET_ENV = "KEY_SET_SECRET"

        private const val EXPECTED_FORM =
            "expected a base64-encoded secret of at least $MIN_SECRET_BYTES bytes, the same on every start " +
                "(for example the output of `openssl rand -base64 $MIN_SECRET_BYTES`)"

        /**
         * The seal under the secret in [SECRET_ENV]. There is no fallback: a secret made up for one process
         * would void, on the next restart, every key set issued and not yet confirmed (AUTH-024 (6)). A
         * missing, blank or malformed secret is refused with a message naming [SECRET_ENV].
         */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): KeySetSeal {
            val value = env[SECRET_ENV] ?: throw IllegalStateException("$SECRET_ENV is missing: $EXPECTED_FORM")
            if (value.isBlank()) throw IllegalStateException("$SECRET_ENV is blank: $EXPECTED_FORM")
            val secret = try {
                Base64.getDecoder().decode(value.trim())
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("$SECRET_ENV is malformed: it is not base64 (${e.message}); $EXPECTED_FORM")
            }
            if (secret.size < MIN_SECRET_BYTES) {
                throw IllegalStateException("$SECRET_ENV is malformed: it decodes to ${secret.size} bytes; $EXPECTED_FORM")
            }
            return KeySetSeal(secret)
        }
    }
}
