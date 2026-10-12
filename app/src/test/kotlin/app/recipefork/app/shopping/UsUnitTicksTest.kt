package app.recipefork.app.shopping

import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.OUNCE
import app.recipefork.core.scaling.Rational
import app.recipefork.core.shopping.ShoppingAmount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.35 T-246: a ticked line in oz that now reads in grams (a metric recipe joined) is compared exactly, no factors needed. */
class UsUnitTicksTest {
    private fun tick(grams: Long) = LineTick("e:1:GRAM", ShoppingAmount(GRAM, Rational.of(grams)))

    @Test
    fun anAmountNowInGramsKeepsATickInOuncesWhileItDoesNotGrow() {
        // 12 oz = 340.19 g.
        assertTrue(tick(340).keeps(ShoppingAmount(OUNCE, Rational.of(12))))
        assertFalse(tick(341).keeps(ShoppingAmount(OUNCE, Rational.of(12))))
    }
}
