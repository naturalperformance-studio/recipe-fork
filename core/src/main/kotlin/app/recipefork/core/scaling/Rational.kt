package app.recipefork.core.scaling

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/**
 * An exact rational number, always in lowest terms with a positive denominator.
 *
 * Scaling computes on these so that no quantity is ever derived from a rounded value
 * (SCALE-003) and no factor is too large to hold exactly (SCALE-010).
 */
class Rational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) : Comparable<Rational> {

    operator fun plus(other: Rational): Rational =
        of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

    operator fun minus(other: Rational): Rational =
        of(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator)

    operator fun times(other: Rational): Rational =
        of(numerator * other.numerator, denominator * other.denominator)

    operator fun div(other: Rational): Rational {
        require(other.signum() != 0) { "division by zero" }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    operator fun unaryMinus(): Rational = Rational(numerator.negate(), denominator)

    fun signum(): Int = numerator.signum()

    fun abs(): Rational = if (signum() < 0) -this else this

    /** The largest whole number not above this value. */
    fun floor(): BigInteger {
        val (q, r) = numerator.divideAndRemainder(denominator)
        return if (r.signum() < 0) q - BigInteger.ONE else q
    }

    val isWhole: Boolean get() = denominator == BigInteger.ONE

    /** The nearest double, for comparisons that are approximate by nature (an entry's factors); never for scaling. */
    fun toDouble(): Double = BigDecimal(numerator).divide(BigDecimal(denominator), MathContext.DECIMAL64).toDouble()

    override fun compareTo(other: Rational): Int =
        (numerator * other.denominator).compareTo(other.numerator * denominator)

    override fun equals(other: Any?): Boolean =
        other is Rational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String = if (isWhole) "$numerator" else "$numerator/$denominator"

    companion object {
        val ZERO = of(0)
        val ONE = of(1)

        fun of(numerator: BigInteger, denominator: BigInteger = BigInteger.ONE): Rational {
            require(denominator.signum() != 0) { "zero denominator" }
            val sign = denominator.signum().toBigInteger()
            val gcd = numerator.gcd(denominator).let { if (it.signum() == 0) BigInteger.ONE else it }
            return Rational(numerator * sign / gcd, denominator * sign / gcd)
        }

        fun of(numerator: Long, denominator: Long = 1): Rational =
            of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator))
    }
}
