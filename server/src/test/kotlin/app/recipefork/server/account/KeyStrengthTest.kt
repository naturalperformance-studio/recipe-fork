package app.recipefork.server.account

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.security.SecureRandom
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

        // Strength is what the secure generator supplies: each kind's key is exactly the bytes drawn
        // from it for that key, the same amount for every kind, with nothing derived, stretched or fixed.
        val drawn = KeyKind.entries.associateWith { kind -> draws(kind) }
        drawn.forEach { (kind, draw) ->
            assertEquals(1, draw.calls.size, "$kind: one draw from the secure generator per key, got ${draw.calls.map { it.size }}")
            assertTrue(draw.calls.single().contentEquals(draw.key.bytes), "$kind: the key is not the bytes drawn for it")
        }
        assertEquals(1, drawn.values.map { it.bits }.toSet().size, "every kind carries the same entropy: ${drawn.mapValues { it.value.bits }}")
        assertEquals(256, drawn.getValue(KeyKind.DELETION).bits)

        // Issued as a set, the three kinds come from the same generator in the same way.
        val recorder = RecordingRandom()
        val issuedSet = KeyIssuer(recorder).issueSet()
        assertEquals(3, recorder.calls.size)
        issuedSet.all.zip(recorder.calls).forEach { (key, bytes) -> assertTrue(bytes.contentEquals(key.bytes), "${key.kind} in a set") }
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

    /**
     * The entropy an issued key of [kind] actually carries: the bits drawn from the secure generator
     * for it, provided the key's text reads back to exactly those bytes.
     */
    private fun entropyBits(kind: KeyKind): Int {
        val draw = draws(kind)
        val parsed = assertNotNull(Keys.parse(kind, draw.key.text))
        assertTrue(parsed.bytes.contentEquals(draw.calls.fold(ByteArray(0)) { acc, b -> acc + b }), "$kind: key is not what was drawn")
        return draw.bits
    }

    private class Draw(val key: IssuedKey, val calls: List<ByteArray>) {
        val bits: Int get() = calls.sumOf { it.size } * 8
    }

    /** Issues one key of [kind] from a generator that records every byte it hands out. */
    private fun draws(kind: KeyKind): Draw {
        val recorder = RecordingRandom()
        val key = KeyIssuer(recorder).issue(kind)
        return Draw(key, recorder.calls.toList())
    }

    /**
     * A secure generator that records what it supplies. Every draw from a [SecureRandom] — bytes,
     * ints, longs — passes through [nextBytes], so nothing drawn escapes the record.
     */
    private class RecordingRandom : SecureRandom() {
        val calls = mutableListOf<ByteArray>()

        override fun nextBytes(bytes: ByteArray) {
            super.nextBytes(bytes)
            calls += bytes.copyOf()
        }
    }
}
