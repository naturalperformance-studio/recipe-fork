package app.recipefork.core.scaling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun q(n: Long, d: Long = 1) = Rational.of(n, d)

/** The serving count control's walk (SCALE-010, SCALE-011). */
class RequestedServingsTest {

    @Test
    fun `the plus action walks from a quarter through the fractions to one and then by whole numbers`() {
        val walk = generateSequence(q(1, 4)) { RequestedServings.increased(it) }.take(9).toList()
        assertEquals(listOf(q(1, 4), q(1, 3), q(1, 2), q(2, 3), q(3, 4), q(1), q(2), q(3), q(4)), walk)
    }

    @Test
    fun `the minus action walks back the same way and stays at a quarter`() {
        val walk = generateSequence(q(4)) { RequestedServings.decreased(it) }.take(10).toList()
        assertEquals(listOf(q(4), q(3), q(2), q(1), q(3, 4), q(2, 3), q(1, 2), q(1, 3), q(1, 4), q(1, 4)), walk)
        assertEquals(q(1, 4), RequestedServings.decreased(q(1, 4)))
    }

    @Test
    fun `there is no ceiling`() {
        assertEquals(q(1001), RequestedServings.increased(q(1000)))
        val huge = Rational.of(java.math.BigInteger.TEN.pow(30))
        assertEquals(huge + q(1), RequestedServings.increased(huge))
        assertEquals(huge, RequestedServings.decreased(huge + q(1)))
    }

    @Test
    fun `every count the walk reaches is a fraction of the set below one or a whole number`() {
        val below = listOf(q(1, 4), q(1, 3), q(1, 2), q(2, 3), q(3, 4))
        assertEquals(below, RequestedServings.BELOW_ONE)
        var count = q(1, 4)
        val reached = mutableSetOf(count)
        repeat(30) { count = RequestedServings.increased(count); reached += count }
        repeat(40) { count = RequestedServings.decreased(count); reached += count }
        assertEquals(below.toSet() + (1L..26L).map { q(it) }, reached)
    }

    @Test
    fun `no other value is held or walked from`() {
        listOf(q(1, 5), q(1, 8), q(5, 6), q(3, 2), q(7, 3), q(0), q(-1)).forEach {
            assertFalse(RequestedServings.holds(it), "$it")
            assertFailsWith<IllegalArgumentException> { RequestedServings.increased(it) }
            assertFailsWith<IllegalArgumentException> { RequestedServings.decreased(it) }
            assertFailsWith<IllegalArgumentException> { RequestedServings.text(it, InterfaceLanguage.ENGLISH) }
        }
        listOf(q(1, 4), q(3, 4), q(1), q(12)).forEach { assertTrue(RequestedServings.holds(it), "$it") }
    }

    @Test
    fun `the count is written as requested - a fraction below one, a whole number otherwise`() {
        val cases = mapOf(
            q(1, 4) to "¼", q(1, 3) to "⅓", q(1, 2) to "½", q(2, 3) to "⅔", q(3, 4) to "¾",
            q(1) to "1", q(6) to "6", q(1000) to "1,000",
        )
        cases.forEach { (count, text) -> assertEquals(text, RequestedServings.text(count, InterfaceLanguage.ENGLISH)) }
        assertEquals("1\u00A0000", RequestedServings.text(q(1000), InterfaceLanguage.RUSSIAN))
        assertEquals("⅔", RequestedServings.text(q(2, 3), InterfaceLanguage.RUSSIAN))
    }
}
