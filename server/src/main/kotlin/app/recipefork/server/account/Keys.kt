package app.recipefork.server.account

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The three kinds of key an account carries. They differ in what they do and in where they are
 * kept, never in how strong they are (AUTH-014 (2)): every kind is [Keys.KEY_BYTES] random bytes.
 */
enum class KeyKind(val prefix: String, val storedName: String) {
    ACCOUNT("account-", "account"),
    DELETION("deletion-", "deletion"),
    RECOVERY("recovery-", "recovery"),
}

/** A key as issued: its kind and its random bytes. [text] is what the holder keeps. */
class IssuedKey(val kind: KeyKind, bytes: ByteArray) {
    private val raw = bytes.copyOf()

    val bytes: ByteArray get() = raw.copyOf()
    val text: String get() = kind.prefix + Keys.encode(raw)
    val digest: ByteArray get() = Keys.digest(raw)
}

/** One key of each kind, as issued together at creation. */
data class KeySet(val account: IssuedKey, val deletion: IssuedKey, val recovery: IssuedKey) {
    val all: List<IssuedKey> get() = listOf(account, deletion, recovery)
}

/**
 * Issues keys: 256 bits from a cryptographically secure generator, written as Crockford base32
 * behind a prefix naming the kind (ADR 20260927-keys-are-hashed-random-secrets). Issuing stores
 * nothing; a key is held — as its digest only — once an account is created with it.
 */
class KeyIssuer(private val random: SecureRandom = SecureRandom()) {
    fun issue(kind: KeyKind): IssuedKey =
        IssuedKey(kind, ByteArray(Keys.KEY_BYTES).also(random::nextBytes))

    fun issueSet(): KeySet = KeySet(issue(KeyKind.ACCOUNT), issue(KeyKind.DELETION), issue(KeyKind.RECOVERY))
}

object Keys {
    /** 256 bits for every kind of key. */
    const val KEY_BYTES = 32

    /** Crockford's base32 alphabet: no I, L, O or U. */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Length of a key's body: 256 bits in 5-bit characters, the last padded with zeros. */
    val BODY_LENGTH = (KEY_BYTES * 8 + 4) / 5

    /** The digest the store holds in place of a key. */
    fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    /** The key's bytes, if [text] is a well-formed key of [kind] in its canonical form; else null. */
    fun parse(kind: KeyKind, text: String): IssuedKey? {
        if (!text.startsWith(kind.prefix)) return null
        return decode(text.substring(kind.prefix.length))?.let { IssuedKey(kind, it) }
    }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    /** Decodes a body of exactly [BODY_LENGTH] canonical characters whose padding bits are zero. */
    fun decode(body: String): ByteArray? {
        if (body.length != BODY_LENGTH) return null
        val out = ByteArray(KEY_BYTES)
        var buffer = 0
        var bits = 0
        var i = 0
        for (c in body) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out[i++] = (buffer shr (bits - 8)).toByte()
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return if (buffer == 0 && i == KEY_BYTES) out else null
    }
}
