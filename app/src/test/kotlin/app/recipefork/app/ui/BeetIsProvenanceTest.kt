package app.recipefork.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Beet is provenance (the redesign, T-296): the scheme's tertiary roles — tertiary, onTertiary, tertiaryContainer and
 * onTertiaryContainer — are read by the card's provenance line and by nothing else in the app. The scheme itself is
 * built in `ui/AppTheme.kt` by naming the roles, not by reading them, so it is not a reader.
 */
class BeetIsProvenanceTest {
    /** Unit tests of `:app` run with the module directory as the working directory. */
    private val sources = File("src/main/kotlin/app/recipefork/app")

    private val aRead = Regex("""\.(tertiary|onTertiary|tertiaryContainer|onTertiaryContainer)\b""")

    @Test
    fun onlyTheCardsProvenanceReadsTheTertiaryRoles() {
        val reads = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { file ->
            file.relativeTo(sources).invariantSeparatorsPath to file.readLines().map { it.trim() }
                .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
                .sumOf { line -> aRead.findAll(line).count() }
        }.filterValues { it > 0 }
        // The provenance line's two forms: a parent the book holds, and one it does not.
        assertEquals(mapOf("card/CardScreen.kt" to 2), reads)
    }

    // Both of the card's reads stand in its provenance area, and are its tertiary itself.
    @Test
    fun theCardsTwoReadsAreTheProvenanceLine() {
        val card = File(sources, "card/CardScreen.kt").readText()
        val provenance = card.substringAfter("private fun Provenance(").substringBefore("\n}\n")
        assertEquals(listOf("tertiary", "tertiary"), aRead.findAll(provenance).map { it.groupValues[1] }.toList())
    }
}
