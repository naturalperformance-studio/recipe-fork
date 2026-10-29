package app.recipefork.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Graphite is the reader's margin notes (the redesign's pencil, decisions 2026-10-15, recommendation 8; T-315): the
 * theme's graphite colour is read only by the card's margin note, which draws its words, its pencil mark and its dashed
 * rule in it. The theme itself names the colour in `ui/AppTheme.kt` without reading it, so it is not a reader.
 */
class GraphiteIsMarginNotesTest {
    /** Unit tests of `:app` run with the module directory as the working directory. */
    private val sources = File("src/main/kotlin/app/recipefork/app")

    private val aRead = Regex("""\.graphite\b""")

    @Test
    fun onlyTheMarginNoteReadsTheGraphite() {
        val reads = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { file ->
            file.relativeTo(sources).invariantSeparatorsPath to file.readLines().map { it.trim() }
                .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
                .sumOf { line -> aRead.findAll(line).count() }
        }.filterValues { it > 0 }
        assertEquals(mapOf("card/StepsList.kt" to 1), reads)
    }

    // The one read is the margin note's own, which colours its words, tints its mark and draws its rule.
    @Test
    fun theReadIsTheMarginNotesPencil() {
        val list = File(sources, "card/StepsList.kt").readText()
        val note = list.substringAfter("internal fun MarginNote(").substringBefore("\n}\n")
        assertTrue(Regex("""val graphite = MaterialTheme\.appColors\.graphite""").containsMatchIn(note))
        assertTrue("the rule", Regex("""\.dashedRule\(graphite\)""").containsMatchIn(note))
        assertTrue("the mark", Regex("""tint = graphite""").containsMatchIn(note))
        assertTrue("the words", Regex("""color = graphite""").containsMatchIn(note))
    }
}
