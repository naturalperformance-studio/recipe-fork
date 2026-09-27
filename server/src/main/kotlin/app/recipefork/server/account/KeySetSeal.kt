package app.recipefork.server.account

import java.security.MessageDigest
import java.security.SecureRandom
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

        /** A seal under a fresh random secret: sets issued before a restart are not accepted after it. */
        fun random(random: SecureRandom = SecureRandom()): KeySetSeal =
            KeySetSeal(ByteArray(MIN_SECRET_BYTES).also(random::nextBytes))

        /**
         * The seal under [SECRET_ENV] when it is set (base64, at least 32 bytes), so that issued sets
         * survive a restart; otherwise under a random secret for this process only.
         */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): KeySetSeal =
            env[SECRET_ENV]?.takeIf { it.isNotBlank() }
                ?.let { KeySetSeal(Base64.getDecoder().decode(it.trim())) }
                ?: random()
    }
}
