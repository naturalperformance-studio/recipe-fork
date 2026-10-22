package app.recipefork.app.card

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.core.nutrition.NutritionValues
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.ServingCount
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigInteger

/** v0.40, T-293: the words the card's nutrition says numbers with, at any size and any serving count. */
@RunWith(AndroidJUnit4::class)
class NutritionWordsTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun aPluralIsChosenByTheLastTwoDigitsOfALargeNumber() {
        assertEquals(1, pluralOf(BigInteger.ONE))
        assertEquals(99, pluralOf(BigInteger.valueOf(99)))
        assertEquals(111, pluralOf(BigInteger.valueOf(1011)))
        assertEquals(121, pluralOf(BigInteger.TEN.pow(30).add(BigInteger.valueOf(21))))
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussianNumbersTakeTheirWords() {
        val values = NutritionValues(BigInteger.valueOf(1021), BigInteger.valueOf(1), BigInteger.valueOf(112), BigInteger.valueOf(2))
        assertEquals(
            "Всё блюдо, 21 порция: примерно 1021 килокалория, белки 1 грамм, жиры 112 граммов, углеводы 2 грамма",
            spokenNutrition(resources, values, perServing = false, ServingCount(Rational.of(21), approximate = false), InterfaceLanguage.RUSSIAN),
        )
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussianAFractionOfServingsIsInTheGenitive() {
        assertEquals("½ порции", servingsText(resources, ServingCount(Rational.of(1, 2), false), InterfaceLanguage.RUSSIAN, spoken = false))
        assertEquals("примерно 3 порции", servingsText(resources, ServingCount(Rational.of(3), true), InterfaceLanguage.RUSSIAN, spoken = true))
        assertEquals("≈5 порций", servingsText(resources, ServingCount(Rational.of(5), true), InterfaceLanguage.RUSSIAN, spoken = false))
    }

    @Test
    @Config(qualifiers = "en")
    fun inEnglishOneIsSingularAndAFractionBelowOneToo() {
        assertEquals("1 serving", servingsText(resources, ServingCount(Rational.ONE, false), InterfaceLanguage.ENGLISH, spoken = false))
        assertEquals("½ serving", servingsText(resources, ServingCount(Rational.of(1, 2), false), InterfaceLanguage.ENGLISH, spoken = false))
        assertEquals(
            "Per serving: about 1 kilocalorie, protein 1 gram, fat 0 grams, carbs 101 grams",
            spokenNutrition(
                resources,
                NutritionValues(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO, BigInteger.valueOf(101)),
                perServing = true,
                null,
                InterfaceLanguage.ENGLISH,
            ),
        )
    }
}
