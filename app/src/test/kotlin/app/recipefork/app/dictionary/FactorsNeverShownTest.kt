package app.recipefork.app.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * DOM-011: no screen shows a comparison factor. A factor reaches the application only through the dictionary entry, and
 * no code outside the device's holding of the dictionary reads it — so no composable can put one on a screen. Code that
 * orders by factors without showing them joins [readers] when it comes.
 */
class FactorsNeverShownTest {
    /** Unit tests of `:app` run with the module directory as the working directory. */
    private val sources = File("src/main/kotlin/app/recipefork/app")

    /** The dictionary holding factors, and the filter by an ingredient ordering by them (CAT-009). */
    private val readers = setOf("dictionary/DictionaryEntities.kt", "recipe/BookSearch.kt")

    private val aRead = Regex("""\bfactors\b|\bcomparisonFactor\b|\bFactorsConverter\b""")

    @Test
    fun noCodeOutsideTheDictionaryReadsAFactor() {
        val reading = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { file ->
            file.readLines().map { it.trim() }
                .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
                .any { aRead.containsMatchIn(it) }
        }.map { it.relativeTo(sources).invariantSeparatorsPath }.toSet()
        assertEquals(setOf("data/AppDatabase.kt") + readers, reading)
    }

    @Test
    fun noInterfaceStringNamesAFactor() {
        val strings = File("src/main/res").walkTopDown().filter { it.isFile && it.name == "strings.xml" }.toList()
        assert(strings.isNotEmpty())
        strings.forEach { file ->
            val text = file.readText().lowercase()
            listOf("factor", "коэффициент").forEach { assertEquals("${file.path} names a $it", false, it in text) }
        }
    }
}
