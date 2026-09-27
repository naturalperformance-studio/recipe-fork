package app.recipefork.server.account

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The strength of the three keys: length and randomness against the attempt rates of QUAL-009
 * (account and recovery keys, ten a day per source) and QUAL-007 (deletion keys, ten a day per
 * source at the deletion page).
 */
class KeyStrengthTest {

    @Test
    fun `AUTH-014 (2) - the account, deletion and recovery keys are equally strong, differing only in the kind they name`() {
        val set = KeyIssuer().issueSet()
        val bodies = set.all.map { it.text.removePrefix(it.kind.prefix) }

        assertEquals(listOf(KeyKind.ACCOUNT, KeyKind.DELETION, KeyKind.RECOVERY), set.all.map { it.kind })
        set.all.forEach { assertEquals(Keys.KEY_BYTES, it.bytes.size, "${it.kind} key length") }
        assertEquals(1, bodies.map { it.length }.toSet().size, "every kind is written at the same length")
        bodies.forEach { body -> assertTrue(body.all { it in Keys.ALPHABET }) }
        assertEquals(1, KeyKind.entries.map { entropyBits(it) }.toSet().size, "every kind carries the same entropy")
        assertEquals(256, entropyBits(KeyKind.DELETION))
    }

    @Test
    fun `AUTH-014 (3) - an account key cannot be found at the rate QUAL-009 permits in any practical time`() {
        assertUnguessable(KeyKind.ACCOUNT)
    }

    @Test
    fun `AUTH-017 (4) - a deletion key cannot be found at the page's permitted rate (QUAL-007) in any practical time`() {
        assertUnguessable(KeyKind.DELETION)
    }

    @Test
    fun `AUTH-020 (5) - the recovery key resists guessing exactly as the account key does at the rate QUAL-009 permits`() {
        assertUnguessable(KeyKind.RECOVERY)
        assertEquals(entropyBits(KeyKind.ACCOUNT), entropyBits(KeyKind.RECOVERY))
    }

    @Test
    fun `AUTH-014 (1) - keys issued by separate issuers never repeat`() {
        val seen = HashSet<List<Byte>>()
        repeat(4) {
            val issuer = KeyIssuer()
            repeat(2_500) { KeyKind.entries.forEach { kind -> assertTrue(seen.add(issuer.issue(kind).bytes.toList())) } }
        }
    }

    @Test
    fun `a key's text reads back to its bytes, and only in its canonical form under its own kind`() {
        val key = KeyIssuer().issue(KeyKind.ACCOUNT)
        val parsed = assertNotNull(Keys.parse(KeyKind.ACCOUNT, key.text))
        assertTrue(parsed.bytes.contentEquals(key.bytes))

        assertNull(Keys.parse(KeyKind.RECOVERY, key.text), "a key is not accepted under another kind")
        assertNull(Keys.parse(KeyKind.ACCOUNT, key.text.lowercase()))
        assertNull(Keys.parse(KeyKind.ACCOUNT, key.text.dropLast(1)))
        assertNull(Keys.parse(KeyKind.ACCOUNT, key.text + "0"))
        // The last character carries four padding bits, which must be zero.
        val padded = Keys.ALPHABET[Keys.ALPHABET.indexOf(key.text.last()) or 1]
        assertNull(Keys.parse(KeyKind.ACCOUNT, key.text.dropLast(1) + padded))
    }

    /**
     * Length: at ten attempts a day from each of a million sources for a million years, against a
     * billion live keys, the chance of hitting any one stays below 10^-50. Randomness: over many
     * keys every bit is set about half the time, and no two keys repeat.
     */
    private fun assertUnguessable(kind: KeyKind) {
        val bits = entropyBits(kind)
        val attempts = BigInteger.valueOf(10L * 365 * 1_000_000) * BigInteger.valueOf(1_000_000) // per day, days, sources
        val liveKeys = BigInteger.valueOf(1_000_000_000)
        val chance = BigDecimal(attempts * liveKeys).divide(BigDecimal(BigInteger.TWO.pow(bits)), MathContext.DECIMAL128)
        assertTrue(chance < BigDecimal("1e-50"), "$kind: chance $chance with $bits bits")

        val issuer = KeyIssuer()
        val samples = List(4_000) { issuer.issue(kind).bytes }
        assertEquals(samples.size, samples.map { it.toList() }.toSet().size, "$kind keys repeat")
        val ones = samples.sumOf { bytes -> bytes.sumOf { Integer.bitCount(it.toInt() and 0xff) } }
        val total = samples.size * Keys.KEY_BYTES * 8
        val share = ones.toDouble() / total
        assertTrue(share in 0.495..0.505, "$kind: share of set bits $share")
        // Every bit position varies across keys: none is fixed.
        for (bit in 0 until Keys.KEY_BYTES * 8) {
            val set = samples.count { (it[bit / 8].toInt() shr (7 - bit % 8)) and 1 == 1 }
            assertTrue(set in 1_700..2_300, "$kind: bit $bit set in $set of ${samples.size}")
        }
    }

    /** The entropy an issued key of [kind] actually carries, measured from what the issuer produces. */
    private fun entropyBits(kind: KeyKind): Int {
        val key = KeyIssuer().issue(kind)
        val parsed = assertNotNull(Keys.parse(kind, key.text))
        return parsed.bytes.size * 8
    }
}
