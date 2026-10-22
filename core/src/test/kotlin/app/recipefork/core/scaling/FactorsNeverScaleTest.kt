package app.recipefork.core.scaling

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DOM-011: a comparison factor orders a filter and nothing else — no scaling reads one. In `:core` a factor is read only
 * where the dictionary carries it, where it is looked up by unit, and where the filter's comparable quantity is taken.
 *
 * One departure, approved by the owner for the shopping list alone (v0.32): the list counts one entry's parts in other
 * units by its factors, as an approximate line (`shopping/`). A second, planned in v0.35: the conversion into the
 * reader's unit system reads an entry's measures for an approximate ("≈") amount (`scaling/UnitConversion.kt`).
 * A third, planned in v0.40: the nutrition of a dish weighs a row by its entry's measures, for a rough count
 * (`nutrition/`). Scaling itself still reads none.
 */
class FactorsNeverScaleTest {
    /** Unit tests of `:core` run with the module directory as the working directory. */
    private val sources = File("src/main/kotlin/app/recipefork/core")

    private val aRead = Regex("""\bfactors\b|\bcomparisonFactor\b""")

    @Test
    fun onlyTheComparableQuantityReadsAFactor() {
        val reading = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { file ->
            file.readLines().map { it.trim() }
                .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
                .any { aRead.containsMatchIn(it) }
        }.map { it.relativeTo(sources).invariantSeparatorsPath }.toSet()
        assertEquals(
            setOf(
                "api/DictionaryContract.kt",
                "recipe/EntryDivisibility.kt",
                "nutrition/Nutrition.kt",
                "nutrition/NutritionEntries.kt",
                "scaling/QuantityPerServing.kt",
                "scaling/UnitConversion.kt",
                "shopping/ShoppingInput.kt",
                "shopping/ShoppingTotals.kt",
            ),
            reading,
        )
    }
}
