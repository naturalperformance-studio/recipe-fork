package app.recipefork.app.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import app.recipefork.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Checks for L10N-001, reusable by every screen and run over the whole application. */
object LocalizationChecks {
    /** Unit tests of `:app` run with the module directory as the working directory. */
    val resDir = File("src/main/res")
    val sourceDir = File("src/main/kotlin")

    /** Every string resource, by name, in one `values` directory (strings, plurals and arrays). */
    fun stringsIn(valuesDir: File): Map<String, String> {
        val strings = mutableMapOf<String, String>()
        valuesDir.listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            listOf("string", "plurals", "string-array").forEach { tag ->
                val nodes = document.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.getAttribute("translatable") == "false") continue
                    strings[element.getAttribute("name")] = element.textContent.trim()
                }
            }
        }
        return strings
    }

    /** L10N-001 (1): every string exists, non-empty, in English (`values`) and in Russian (`values-ru`). */
    fun assertEveryStringInBothLanguages(res: File = resDir) {
        val english = stringsIn(File(res, "values"))
        val russian = stringsIn(File(res, "values-ru"))
        assertTrue("no strings found under $res", english.isNotEmpty())
        assertEquals("strings in English without Russian", emptySet<String>(), english.keys - russian.keys)
        assertEquals("strings in Russian without English", emptySet<String>(), russian.keys - english.keys)
        (english + russian.mapKeys { "ru:${it.key}" }).forEach { (name, value) ->
            assertTrue("string $name is empty", value.isNotBlank())
        }
    }

    private val hardcoded = listOf(
        Regex("""\bText\(\s*"[^"]*\p{L}"""),
        Regex("""\b(text|contentDescription|label|onClickLabel|placeholder|title)\s*=\s*"[^"]*\p{L}"""),
        Regex("""makeText\([^,]+,\s*"[^"]*\p{L}"""),
    )

    /** L10N-001 (2): no interface text is written into the code in one language; it comes from resources. */
    fun assertNoHardcodedInterfaceText(sources: File = sourceDir) {
        val found = sources.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (hardcoded.any { it.containsMatchIn(line) }) "${file.path}:${i + 1}: ${line.trim()}" else null
            }
        }.toList()
        assertEquals("interface text fixed in the code", emptyList<String>(), found)
    }

    /**
     * The `values` directory that holds [context]'s current language itself: `values-<language>` where it
     * exists, `values` for English. Android resolves a string missing there from `values`, which is the
     * fallback L10N-001 (1) forbids, so this directory — not what `getString` returns — says what the
     * language holds.
     */
    fun ownValuesDir(context: Context, res: File = resDir): File {
        val language = context.resources.configuration.locales[0].language
        val own = File(res, "values-$language")
        if (own.isDirectory) return own
        assertEquals("no values directory of its own for the language in force", "en", language)
        return File(res, "values")
    }

    /**
     * The string resources of [context]'s current language that the language holds itself, resolved in it:
     * a string that exists only in another language's directory is left out, so its fallback value is not
     * accepted as this language's text.
     */
    fun ownResolvedStrings(context: Context, res: File = resDir): List<String> {
        val own = stringsIn(ownValuesDir(context, res)).keys
        return R.string::class.java.fields.filter { it.name in own }.map { context.getString(it.getInt(null)) }
    }

    /**
     * L10N-001 (1) on a rendered screen: every text, description and action label shown is a string resource
     * that the language in force holds in its own `values` directory — so none falls back to the other
     * language and none is fixed in the code — except the reader's own content listed in [content].
     */
    fun assertScreenTextFromResources(
        screen: SemanticsNodeInteractionsProvider,
        context: Context,
        content: Set<String> = emptySet(),
        res: File = resDir,
    ) {
        val patterns = ownResolvedStrings(context, res).map { value ->
            Regex(Regex.escape(value).replace(Regex("""%(\d+\$)?[sd]"""), """\\E.+\\Q"""))
        }
        val shown = screen.onAllNodes(isRoot().not() or isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    listOfNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.label)
            }
        assertTrue("the screen shows no text", shown.isNotEmpty())
        val foreign = shown.filter { it !in content && patterns.none { p -> p.matches(it) } }
        assertEquals("text not taken from the resources of this language", emptyList<String>(), foreign)
    }
}
