package app.recipefork.core.search

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-275: the dictionary matching of the editor's field lives in `:core`, over the dictionaries' own entries, and binds
 * a name read from a recipe's text to the entry it names — against the shipped snapshot.
 */
class DictionaryMatchTest {
    private val ingredients = DictionaryMatch(shipped.ingredients)
    private val equipment = DictionaryMatch(shipped.equipment)

    private fun ingredient(labelEn: String) = shipped.ingredients.single { it.labelEn == labelEn }
    private fun item(labelEn: String) = shipped.equipment.single { it.labelEn == labelEn }

    @Test
    fun anInflectedNameIsBoundToItsEntry() {
        assertEquals(DictionaryMatch.Bound(ingredient("Greek yogurt"), emptyList()), ingredients.bind("греческого йогурта"))
        assertEquals(DictionaryMatch.Bound(ingredient("egg"), emptyList()), ingredients.bind("яйца"))
        assertEquals(DictionaryMatch.Bound(ingredient("Greek yogurt"), emptyList()), ingredients.bind("Greek yogurt"))
        assertEquals(DictionaryMatch.Bound(ingredient("egg"), emptyList()), ingredients.bind("eggs"))
    }

    @Test
    fun theWordsANameDoesNotUseAreGivenBackAsWritten() {
        assertEquals(DictionaryMatch.Bound(ingredient("flour"), listOf("cups", "of")), ingredients.bind("cups of flour"))
        assertEquals(DictionaryMatch.Bound(ingredient("flour"), listOf("2", "cups")), ingredients.bind("2 cups flour"))
        // No leaf for it: the parent, the rest left for a qualifier.
        assertEquals(DictionaryMatch.Bound(ingredient("yogurt"), listOf("с", "Травами")), ingredients.bind("йогурта с Травами"))
        // The most of the name's words win, in any order: a leaf over its parent.
        assertEquals(ingredient("rye flour"), ingredients.bind("муки ржаной")?.entry)
        assertEquals(DictionaryMatch.Bound(ingredient("wheat flour"), listOf("sifted")), ingredients.bind("sifted wheat flour"))
    }

    @Test
    fun equipmentIsBoundAsIngredientsAre() {
        assertEquals(DictionaryMatch.Bound(item("Frying pan"), emptyList()), equipment.bind("сковороде"))
        assertEquals(DictionaryMatch.Bound(item("Frying pan"), listOf("large")), equipment.bind("large skillet"))
        assertEquals(DictionaryMatch.Bound(item("Oven"), listOf("в")), equipment.bind("в духовку"))
    }

    @Test
    fun nothingKnownIsBoundToNothing() {
        assertNull(ingredients.bind("щепотка любви"))
        assertNull(ingredients.bind(" , "))
    }

    @Test
    fun aRemovedEntryIsNeverBound() {
        val dictionary = listOf(
            IngredientEntryDto(1, "соль", "salt", removed = true),
            IngredientEntryDto(2, "соль морская", "sea salt"),
        )
        assertNull(DictionaryMatch(dictionary).bind("соли"))
        assertEquals(dictionary[1], DictionaryMatch(dictionary).bind("морской соли")?.entry)
    }

    @Test
    fun theCascadeTakesALabelBeforeASynonymAndTheSameFormBeforeAnother() {
        val dictionary = listOf(
            IngredientEntryDto(1, "томаты", "canned tomatoes"),
            IngredientEntryDto(2, "помидор", "tomato", synonymsRu = listOf("томат", "томаты")),
            IngredientEntryDto(3, "томат", "tomato paste"),
        )
        val match = DictionaryMatch(dictionary)
        // The same words as a label, then as a synonym, then a label's in another form.
        assertEquals(dictionary[0], match.offer("томаты", RUSSIAN, emptySet()).exact)
        assertEquals(dictionary[0], match.bind("томаты")?.entry)
        assertEquals(dictionary[2], match.offer("томат", RUSSIAN, emptySet()).exact)
        assertEquals(dictionary[2], match.bind("томат")?.entry)
        assertEquals(dictionary[1], match.offer("помидоры", RUSSIAN, emptySet()).exact)
    }

    @Test
    fun theOfferRunsOverEitherDictionary() {
        val eggs = ingredients.offer("яйца", RUSSIAN, emptySet())
        assertEquals(ingredient("egg"), eggs.exact)
        assertEquals(ingredient("egg"), eggs.entries.first())
        assertNull(eggs.freeText)
        assertEquals(ingredient("rye flour"), ingredients.offer("rye flour", ENGLISH, emptySet()).entries.first())
        val skillet = equipment.offer("skillet", ENGLISH, emptySet())
        assertEquals(item("Frying pan"), skillet.exact)
        assertEquals("skillet", skillet.synonyms[item("Frying pan").id])
        val unknown = ingredients.offer("щепотка любви", RUSSIAN, emptySet())
        assertNull(unknown.exact)
        assertEquals("щепотка любви", unknown.freeText)
    }

    private companion object {
        /** Unit tests of `:core` run with the module directory as the working directory. */
        val shipped: DictionariesDto = Json { ignoreUnknownKeys = true }
            .decodeFromString(DictionariesDto.serializer(), File("../app/src/main/assets/dictionaries.json").readText())
    }
}
