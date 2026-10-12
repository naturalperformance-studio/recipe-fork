package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.QuantityUnit.TO_TASTE
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun r(n: Long, d: Long = 1) = Rational.of(n, d)
private fun factor(n: Long, d: Long = 1) = ScaleFactor.of(r(n, d))

private const val NBSP = ' '

/** The text of [authored] displayed at [factor] in [language]. */
private fun text(authored: AuthoredQuantity, factor: ScaleFactor, language: InterfaceLanguage): String =
    QuantityText.of(Scaling.display(authored, factor), language)

/** The text of an `exact` value already computed in grams. */
private fun grams(value: Rational, language: InterfaceLanguage = ENGLISH): String =
    text(AuthoredQuantity(GRAM, value), ScaleFactor.ONE, language)

class Scale010LargeCountInKilogramsTest {
    @Test
    fun `an exact ingredient above 1000 g at a very large count is shown in kilograms`() {
        val salt = AuthoredQuantity(GRAM, r(5))
        val line = Scaling.display(salt, ScaleFactor.forServings(r(1000), 4))
        assertEquals(r(1250), line.value)
        assertEquals("1.25 kg", QuantityText.of(line, ENGLISH))
        assertEquals("1,25 кг", QuantityText.of(line, RUSSIAN))
    }

    @Test
    fun `a count far larger still climbs to tonnes and keeps every digit`() {
        val salt = AuthoredQuantity(GRAM, r(5))
        val line = Scaling.display(salt, ScaleFactor.forServings(r(4_000_000), 4))
        assertEquals("5 t", QuantityText.of(line, ENGLISH))
    }
}

class Scale013ExactLadderTest {
    @Test
    fun `1234 g shows 1_234 kg`() {
        assertEquals("1.234 kg", grams(r(1234)))
    }

    @Test
    fun `1500 g shows 1_5 kg and not 1_500 kg or 2 kg`() {
        val shown = grams(r(1500))
        assertEquals("1.5 kg", shown)
        assertFalse(shown == "1.500 kg" || shown == "2 kg")
    }

    @Test
    fun `999_6 g shows 1 kg because the nearest whole gram is 1000 g`() {
        // 4998 g at a factor of 1/5 is an exact 999.6 g.
        val line = Scaling.display(AuthoredQuantity(GRAM, r(4998)), factor(1, 5))
        assertEquals("1 kg", QuantityText.of(line, ENGLISH))
        assertEquals("1 kg", grams(r(9996, 10)))
    }

    @Test
    fun `rung boundaries in mass`() {
        assertEquals("999 g", grams(r(999)))
        assertEquals("1 kg", grams(r(1000)))
        assertEquals("1.001 kg", grams(r(1001)))
        assertEquals("1.23 kg", grams(r(1230)))
        assertEquals("999.999 kg", grams(r(999_999)))
        assertEquals("1 t", grams(r(1_000_000)))
        assertEquals("1.235 t", grams(r(1_234_567)))
        assertEquals("1.2 t", grams(r(1_200_000)))
        assertEquals("1,000 t", grams(r(1_000_000_000)))
    }

    @Test
    fun `rung boundaries in volume`() {
        fun ml(v: Long) = QuantityText.of(Scaling.display(AuthoredQuantity(MILLILITRE, r(v)), ScaleFactor.ONE), ENGLISH)
        assertEquals("999 ml", ml(999))
        assertEquals("1 l", ml(1000))
        assertEquals("1.5 l", ml(1500))
        assertEquals("1.05 l", ml(1050))
        assertEquals("1 m³", ml(1_000_000))
        assertEquals("2.5 m³", ml(2_500_000))
    }

    @Test
    fun `an authored kilogram quantity is written on the ladder of its magnitude`() {
        val line = Scaling.display(AuthoredQuantity(KILOGRAM, r(3, 2)), factor(1, 3))
        assertEquals("500 g", QuantityText.of(line, ENGLISH))
        assertEquals("500 г", QuantityText.of(line, RUSSIAN))
    }
}

class Scale014MixedNumbersTest {
    @Test
    fun `1 tablespoon at a factor of 1_5 shows 1½ tablespoons`() {
        assertEquals("1½ tbsp", text(AuthoredQuantity(TABLESPOON, r(1)), factor(3, 2), ENGLISH))
        assertEquals("1½ ст. л.", text(AuthoredQuantity(TABLESPOON, r(1)), factor(3, 2), RUSSIAN))
    }

    @Test
    fun `1 teaspoon at a factor of 2_3 shows 2⅓ teaspoons`() {
        assertEquals("2⅓ tsp", text(AuthoredQuantity(TEASPOON, r(1)), factor(23, 10), ENGLISH))
        assertEquals("2⅓ ч. л.", text(AuthoredQuantity(TEASPOON, r(1)), factor(23, 10), RUSSIAN))
    }

    @Test
    fun `1 divisible onion at a factor of 2_5 shows 2½ onions`() {
        assertEquals("2½ pc", text(AuthoredQuantity(PIECE, r(1), divisible = true), factor(5, 2), ENGLISH))
        assertEquals("2½ шт", text(AuthoredQuantity(PIECE, r(1), divisible = true), factor(5, 2), RUSSIAN))
    }

    @Test
    fun `every value of each set above one is written as a whole number and a vulgar fraction`() {
        val expected = mapOf(
            r(5, 4) to "1¼", r(4, 3) to "1⅓", r(3, 2) to "1½", r(5, 3) to "1⅔", r(7, 4) to "1¾",
            r(2) to "2", r(1, 4) to "¼", r(1, 2) to "½",
        )
        for ((value, number) in expected) {
            assertEquals("$number cup", QuantityText.of(DisplayedQuantity(CUP, value, true, Rounding.NONE), ENGLISH))
            assertEquals("$number щепотка", QuantityText.of(DisplayedQuantity(PINCH, value, true, Rounding.NONE), RUSSIAN))
        }
    }

    @Test
    fun `no quantity of these unit types ever carries a decimal mark`() {
        val lines = listOf(
            AuthoredQuantity(TABLESPOON, r(1)),
            AuthoredQuantity(TEASPOON, r(1, 3)),
            AuthoredQuantity(CUP, r(3, 4)),
            AuthoredQuantity(PINCH, r(1)),
            AuthoredQuantity(PIECE, r(3, 2), divisible = true),
            AuthoredQuantity(PIECE, r(3), divisible = false),
        )
        // Factors from 1/100 to 1000 in steps that land on and between every value of each set.
        val factors = (1L..400L).map { factor(it, 40) } + listOf(factor(1, 100), factor(1000), factor(1_000_001, 7))
        for (line in lines) for (f in factors) for (language in InterfaceLanguage.entries) {
            val shown = text(line, f, language)
            val number = shown.substringBefore(' ')
            assertFalse(language.decimalMark in number, "$line at $f in $language: $shown")
            assertTrue(number.all { it.isDigit() || it in "¼⅓½⅔¾" || it == language.groupSeparator }, shown)
        }
    }
}

class L10n009NumberFormsTest {
    private val tenMillionPieces = DisplayedQuantity(PIECE, r(10_000_000), true, Rounding.NONE)

    @Test
    fun `in Russian 10 000 000 pieces read with non-breaking group spaces`() {
        val shown = QuantityText.of(tenMillionPieces, RUSSIAN)
        assertEquals("10${NBSP}000${NBSP}000 шт", shown)
        val number = shown.substringBefore(' ')
        assertEquals("10${NBSP}000${NBSP}000", number)
        assertTrue(number.none { it == ' ' }, "no breaking space inside the number")
    }

    @Test
    fun `in English the same quantity reads 10,000,000`() {
        assertEquals("10,000,000 pc", QuantityText.of(tenMillionPieces, ENGLISH))
    }

    @Test
    fun `1500 g reads 1,5 кг in Russian and 1_5 kg in English`() {
        assertEquals("1,5 кг", grams(r(1500), RUSSIAN))
        assertEquals("1.5 kg", grams(r(1500), ENGLISH))
    }

    @Test
    fun `a four-digit whole number reads 1 234 in Russian and 1,234 in English`() {
        val servingsLike = DisplayedQuantity(PIECE, r(1234), true, Rounding.NONE)
        assertEquals("1${NBSP}234 шт", QuantityText.of(servingsLike, RUSSIAN))
        assertEquals("1,234 pc", QuantityText.of(servingsLike, ENGLISH))
        assertEquals("1${NBSP}234,5 т", grams(r(1_234_500_000), RUSSIAN))
        assertEquals("1,234.5 t", grams(r(1_234_500_000), ENGLISH))
    }

    @Test
    fun `a vulgar fraction carries no decimal mark in either language`() {
        for (value in listOf(r(1, 4), r(1, 3), r(1, 2), r(2, 3), r(3, 4), r(2_000_001, 2))) {
            val quantity = DisplayedQuantity(TABLESPOON, value, true, Rounding.NONE)
            assertFalse(',' in QuantityText.of(quantity, RUSSIAN))
            assertFalse('.' in QuantityText.of(quantity, ENGLISH).substringBefore(' '))
        }
        assertEquals("1${NBSP}000${NBSP}000½ ст. л.", QuantityText.of(DisplayedQuantity(TABLESPOON, r(2_000_001, 2), true, Rounding.NONE), RUSSIAN))
    }
}

class UnitLabelsTest {
    @Test
    fun `the twelve units carry the DOM-016 names in both languages, and the US units theirs`() {
        val russian = QuantityUnit.entries.map { UnitLabels.of(it, RUSSIAN) }
        val english = QuantityUnit.entries.map { UnitLabels.of(it, ENGLISH) }
        assertEquals(
            listOf("г", "кг", "т", "мл", "л", "м³", "унц.", "фн", "жидк. унц.", "шт", "ч. л.", "ст. л.", "стакан", "щепотка", "по вкусу"),
            russian,
        )
        assertEquals(
            listOf("g", "kg", "t", "ml", "l", "m³", "oz", "lb", "fl oz", "pc", "tsp", "tbsp", "cup", "pinch", "to taste"),
            english,
        )
    }

    @Test
    fun `a taste_unmeasured line is its label alone`() {
        assertEquals("по вкусу", text(AuthoredQuantity(TO_TASTE, null), factor(3), RUSSIAN))
        assertEquals("to taste", text(AuthoredQuantity(TO_TASTE, null), factor(3), ENGLISH))
    }
}

/** L10N-009 for a whole count or limit, as the editor's marks print them (FLOW-028). */
class WholeNumberGroupingTest {
    @Test
    fun `a whole number is grouped the interface language's way`() {
        assertEquals("2,000", QuantityText.whole(2_000, ENGLISH))
        assertEquals("2 000", QuantityText.whole(2_000, RUSSIAN))
        assertEquals("120", QuantityText.whole(120, ENGLISH))
        assertEquals("1,234,567", QuantityText.whole(1_234_567, ENGLISH))
    }
}
