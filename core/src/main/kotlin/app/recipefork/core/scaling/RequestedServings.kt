package app.recipefork.core.scaling

/**
 * The serving counts a reader can request, and the walk the serving count control takes through them
 * (SCALE-010, SCALE-011): below one serving the fractions ¼, ⅓, ½, ⅔, ¾; at one and above every whole number,
 * with no ceiling. No other value can be reached.
 */
object RequestedServings {

    /** The lowest count that can be requested (SCALE-010). */
    val MIN: Rational = ScaleFactor.MIN_REQUESTED_SERVINGS

    /** The counts below one serving, in order (SCALE-011 (1)). */
    val BELOW_ONE: List<Rational> = listOf(
        Rational.of(1, 4),
        Rational.of(1, 3),
        Rational.of(1, 2),
        Rational.of(2, 3),
        Rational.of(3, 4),
    )

    /** True when [count] is one the reader can request: a fraction of [BELOW_ONE], or a whole number of 1 or more. */
    fun holds(count: Rational): Boolean = count in BELOW_ONE || (count >= Rational.ONE && count.isWhole)

    /** The next count up from [count]: the next fraction below one, then 1, then one more each time — unbounded. */
    fun increased(count: Rational): Rational {
        requireHeld(count)
        if (count >= Rational.ONE) return count + Rational.ONE
        return BELOW_ONE.firstOrNull { it > count } ?: Rational.ONE
    }

    /** The next count down from [count]; at [MIN] it stays at [MIN] (SCALE-011 (3)). */
    fun decreased(count: Rational): Rational {
        requireHeld(count)
        if (count > Rational.ONE) return count - Rational.ONE
        if (count == Rational.ONE) return BELOW_ONE.last()
        return BELOW_ONE.lastOrNull { it < count } ?: MIN
    }

    /**
     * [count] as the reader requested it — a vulgar fraction below one, a whole number otherwise, grouped as
     * L10N-009 states — and never marked approximate (SCALE-011 (4)).
     */
    fun text(count: Rational, language: InterfaceLanguage): String {
        requireHeld(count)
        return QuantityText.mixedNumber(count, language)
    }

    private fun requireHeld(count: Rational) =
        require(holds(count)) { "not a serving count a reader can request: $count" }
}
