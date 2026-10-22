package app.recipefork.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Beet is provenance (the redesign, T-296): the scheme's tertiary roles — tertiary, onTertiary, tertiaryContainer and
 * onTertiaryContainer — are read only by provenance marks: the card's provenance line, the provenance glyph heading
 * the held-fork dialog (T-299), the bent tine of the mark — the version branching off — in the engraved empty
 * states of the book (T-306), and the line under a version's title in the book that names its parent, glyph and words
 * (T-302). The scheme itself is
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
        // The provenance line's two forms (a parent the book holds, and one it does not) and the held-fork dialog's glyph;
        // the engraved mark's bent tine; the book entry's provenance line.
        assertEquals(mapOf("card/CardScreen.kt" to 3, "library/EmptyState.kt" to 1, "library/BookEntryRow.kt" to 1), reads)
    }

    // Both of the card's reads stand in its provenance area, and are its tertiary itself.
    @Test
    fun theCardsTwoReadsAreTheProvenanceLine() {
        val card = File(sources, "card/CardScreen.kt").readText()
        val provenance = card.substringAfter("private fun Provenance(").substringBefore("\n}\n")
        assertEquals(listOf("tertiary", "tertiary"), aRead.findAll(provenance).map { it.groupValues[1] }.toList())
    }

    // The third read is the held-fork dialog's icon colour, that is its provenance glyph.
    @Test
    fun theHeldForkDialogsGlyphIsBeet() {
        val card = File(sources, "card/CardScreen.kt").readText()
        val dialog = card.substringAfter("fun HeldForkDialog(").substringBefore("\n}\n")
        assertEquals(listOf("tertiary"), aRead.findAll(dialog).map { it.groupValues[1] }.toList())
        assertTrue(Regex("""iconContentColor\s*=\s*MaterialTheme\.colorScheme\.tertiary""").containsMatchIn(dialog))
    }

    // The empty states' read is the engraved mark's bent tine, drawn from its own layer.
    @Test
    fun theEngravedMarksBentTineIsBeet() {
        val engraved = File(sources, "library/EmptyState.kt").readText().substringAfter("private fun Engraved(").substringBefore("\n}\n")
        assertEquals(listOf("tertiary"), aRead.findAll(engraved).map { it.groupValues[1] }.toList())
        assertTrue(Regex("""R\.drawable\.engraving_mark_beet,\s*tint = colours\.tertiary""").containsMatchIn(engraved))
    }

    // The book entry's read is its provenance line's beet, which tints the glyph and colours the words, and nothing else.
    @Test
    fun theBookEntrysReadIsItsProvenanceLine() {
        val row = File(sources, "library/BookEntryRow.kt").readText()
        val line = row.substringAfter("internal fun EntryProvenance(").substringBefore("\n}\n")
        assertEquals(listOf("tertiary"), aRead.findAll(line).map { it.groupValues[1] }.toList())
        assertTrue(Regex("""val beet = MaterialTheme\.colorScheme\.tertiary""").containsMatchIn(line))
        assertTrue(Regex("""R\.drawable\.ic_provenance""").containsMatchIn(line))
        assertTrue(Regex("""tint = beet""").containsMatchIn(line))
        assertTrue(Regex("""color = beet""").containsMatchIn(line))
    }
}
