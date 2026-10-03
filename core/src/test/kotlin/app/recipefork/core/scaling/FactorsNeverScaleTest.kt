package app.recipefork.core.scaling

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DOM-011: a comparison factor orders a filter and nothing else — no scaling reads one. In `:core` a factor is read only
 * where the dictionary carries it, where it is looked up by unit, and where the filter's comparable quantity is taken.
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
        assertEquals(setOf("api/DictionaryContract.kt", "recipe/EntryDivisibility.kt", "scaling/QuantityPerServing.kt"), reading)
    }
}
