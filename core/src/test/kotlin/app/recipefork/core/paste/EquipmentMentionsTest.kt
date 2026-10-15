package app.recipefork.core.paste

import app.recipefork.core.search.DictionaryMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** T-278: the equipment a pasted step or equipment line names, bound to the shipped equipment dictionary. */
class EquipmentMentionsTest {
    private val shipped = IngredientLineReaderTest.shipped
    private val mentions = EquipmentMentions(shipped.equipment, DictionaryMatch(shipped.ingredients))

    private fun named(text: String, inStep: Boolean = true) = mentions.of(text, inStep).map { it.labelEn }

    @Test
    fun anInflectedNameInAStepIsItsEntry() {
        assertEquals(listOf("Frying pan"), named("Разогреть масло на сковороде."))
        assertEquals(listOf("Oven"), named("Запекать в духовке 20 минут."))
        assertEquals(listOf("Frying pan"), named("Melt the butter in a large skillet."))
        assertEquals(listOf("Oven"), named("Preheat the oven to 200 °C."))
        assertEquals(listOf("Grater"), named("Морковь натереть на крупной тёрке."))
    }

    @Test
    fun theLongestNameWins() {
        assertEquals(listOf("Grill pan"), named("Обжарить на сковороде-гриль."))
        assertEquals(listOf("Dutch oven"), named("Braise in a Dutch oven."))
    }

    @Test
    fun eachEntryOnceInOrderOfFirstMention() =
        assertEquals(listOf("Oven", "Frying pan"), named("Разогреть духовку. На сковороде обжарить лук, потом в духовку."))

    @Test
    fun anEnglishVerbIsNoEquipmentUnlessMarkedAsANoun() {
        assertEquals(emptyList(), named("Whisk the eggs, then grill the chicken and ladle the soup."))
        assertEquals(listOf("Whisk", "Grill"), named("Beat with a whisk; preheat the hot grill."))
    }

    @Test
    fun aWordOfAnIngredientsNameIsNoEquipment() {
        assertEquals(emptyList(), named("Нарезать свиную лопатку крупными кусками."))
        assertEquals(emptyList(), named("Полить соусом барбекю."))
        assertEquals(emptyList(), named("Brush with barbecue sauce."))
        assertEquals(listOf("Spatula"), named("Соскабливать лопаткой пригар со дна."))
    }

    // Negatives met in the owner's files: weight, a dictionary heading, and the app's own timers.
    @Test
    fun wordsThatReadAsSomethingElseAreNoEquipment() {
        assertEquals(emptyList(), named("Жидкости по весу, кусок весом 200 г."))
        assertEquals(emptyList(), named("Соскоблить пригар — в нём весь вкус. Вес готового блюда 500 г."))
        assertEquals(listOf("Refrigerator"), named("Хранение: холодильник, 2 дня."))
        assertEquals(emptyList(), named("Техника простая: поставить таймер на 10 минут."))
        assertEquals(emptyList(), named("Форму поставить по центру корзины."))
    }

    @Test
    fun oneWordOfALongerNameIsNoEquipment() {
        listOf(
            "Cook in the Instant Pot for 10 minutes.", "Put everything in the Crock-Pot.", "Put everything in the crock-pot.",
            "Cook the rice in a rice cooker.", "Serve the pot roast.", "Bake a chicken pot pie.",
        ).forEach { assertEquals(emptyList(), named(it), it) }
        assertEquals(listOf("Oven mitt"), named("Take it out with oven mitts."))
        assertEquals(listOf("Saucepan"), named("Bring a large pot to a boil."))
        // An equipment word before any other word is still the equipment.
        assertEquals(listOf("Oven"), named("Bake in the preheated oven 25 to 30 minutes."))
        assertEquals(listOf("Frying pan"), named("Toast in a dry skillet 2 minutes."))
        assertEquals(listOf("Frying pan"), named("Set the skillet aside."))
        assertEquals(listOf("Saucepan"), named("Cover the pot partially and simmer."))
        assertEquals(listOf("Barbecue"), named("Grill on the barbecue 5 minutes per side."))
    }

    @Test
    fun aContainerOfAnIngredientIsNoEquipment() {
        listOf("Add a jar of salsa.", "Pour in a bottle of wine.", "Добавить банка фасоли.")
            .forEach { assertEquals(emptyList(), named(it), it) }
        assertEquals(listOf("Jar"), named("Переложить в банку и закрыть."))
        assertEquals(listOf("Jar"), named("Уложить в банку огурцы, залить рассолом."))
    }

    @Test
    fun anEquipmentLineIsReadWithoutTheStepGuards() {
        assertEquals(listOf("Whisk"), named("Whisk", inStep = false))
        assertEquals(listOf("Air fryer", "Parchment paper"), named("аэрогриль, пергамент", inStep = false))
        assertEquals(emptyList(), named("пиала диаметром 12–13 см — для сборки", inStep = false))
    }

    @Test
    fun aLongTextIsReadAtOnce() {
        listOf("сковорода ".repeat(5_000), "a".repeat(20_000), "форма для ".repeat(5_000), "сковорода-".repeat(5_000))
            .forEach { text ->
                val took = measureTime { mentions.of(text, inStep = true) }
                assertTrue(took.inWholeMilliseconds < 2_000, "${text.take(10)}…: $took")
            }
    }
}
