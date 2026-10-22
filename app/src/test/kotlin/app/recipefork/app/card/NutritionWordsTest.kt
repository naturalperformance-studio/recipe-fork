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

/**
 * v0.40, T-293; v0.44, T-320: the words the card's nutrition says numbers with, at any size and any serving count, and
 * the finished weight with the servings it is for.
 */
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
            spokenNutrition(resources, values, NutritionShown.WHOLE, ServingCount(Rational.of(21), approximate = false), InterfaceLanguage.RUSSIAN),
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
                NutritionShown.SERVING,
                null,
                InterfaceLanguage.ENGLISH,
            ),
        )
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussianTheWeightIsForTheServingsInTheAccusative() {
        fun weight(grams: Long, count: ServingCount?) =
            weightText(resources, BigInteger.valueOf(grams), count, InterfaceLanguage.RUSSIAN).replace('\u00A0', ' ')
        assertEquals("560 г на 1 порцию", weight(560, ServingCount(Rational.ONE, false)))
        assertEquals("560 г на 21 порцию", weight(560, ServingCount(Rational.of(21), false)))
        assertEquals("1 120 г на 4 порции", weight(1120, ServingCount(Rational.of(4), false)))
        assertEquals("1 400 г на 5 порций", weight(1400, ServingCount(Rational.of(5), false)))
        assertEquals("840 г на ≈3 порции", weight(840, ServingCount(Rational.of(3), true)))
        assertEquals("140 г на ½ порции", weight(140, ServingCount(Rational.of(1, 2), false)))
        assertEquals("560 г", weight(560, null))
    }

    @Test
    @Config(qualifiers = "en")
    fun inEnglishTheWeightIsForTheServings() {
        fun weight(grams: Long, count: ServingCount?) =
            weightText(resources, BigInteger.valueOf(grams), count, InterfaceLanguage.ENGLISH).replace('\u00A0', ' ')
        assertEquals("280 g for 1 serving", weight(280, ServingCount(Rational.ONE, false)))
        assertEquals("1,120 g for 4 servings", weight(1120, ServingCount(Rational.of(4), false)))
        assertEquals("840 g for ≈3 servings", weight(840, ServingCount(Rational.of(3), true)))
        assertEquals("140 g for ½ serving", weight(140, ServingCount(Rational.of(1, 2), false)))
        assertEquals("560 g", weight(560, null))
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussianPer100GramsIsSpelledOut() {
        val values = NutritionValues(BigInteger.valueOf(1210), BigInteger.valueOf(15), BigInteger.valueOf(11), BigInteger.valueOf(1))
        assertEquals(
            "На 100 граммов готового блюда: примерно 1210 килокалорий, белки 15 граммов, жиры 11 граммов, углеводы 1 грамм",
            spokenNutrition(resources, values, NutritionShown.PER_100_G, ServingCount(Rational.of(2), false), InterfaceLanguage.RUSSIAN),
        )
    }
}
