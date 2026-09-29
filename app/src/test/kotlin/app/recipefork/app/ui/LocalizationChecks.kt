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

    /**
     * L10N-001 (2): no interface text is written into the code in one language; it comes from resources.
     *
     * Each Kotlin source is read whole — comments blanked, string literals (plain, raw, with templates) lexed —
     * so a call reads the same however it is laid out across lines. Interface text is the value of:
     *  - the text argument of `Text`/`BasicText` (first positional or `text =`), of `Toast.makeText` (second
     *    positional or `text =`) and of `showSnackbar` (first positional, `message =` or `actionLabel =`);
     *  - a named argument or assignment [uiOnlyNames] anywhere — `contentDescription = "…"`, `label = { … }`,
     *    `placeholder = { … }`, `semantics { contentDescription = "…" }` and so on;
     *  - a named argument or assignment [uiFileNames] (`text`, `title`, `message`) in a file that imports
     *    Compose or Android widgets, where these names are the interface's; in the data layer they name the
     *    reader's content.
     * A value is the whole argument expression, lambdas included (`text = x.ifBlank { "…" }` is caught). It is
     * fixed in the code when a string literal in it holds a letter outside its `$` templates and escapes;
     * test tags, log messages, SQL and other non-interface strings are not in these places and are not read.
     */
    fun assertNoHardcodedInterfaceText(sources: File = sourceDir) {
        val found = sources.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            hardcodedInterfaceText(file.readText()).map { (line, literal) -> "${file.path}:$line: $literal" }
        }.toList()
        assertEquals("interface text fixed in the code", emptyList<String>(), found)
    }

    private val uiOnlyNames = listOf(
        "contentDescription", "label", "onClickLabel", "placeholder", "stateDescription", "actionLabel",
        "supportingText", "paneTitle",
    )
    private val uiFileNames = listOf("text", "title", "message")
    private val uiImport = Regex("""^import (androidx\.compose|android\.widget|android\.app)\.""", RegexOption.MULTILINE)
    private val textCall = Regex("""\b(Text|BasicText|makeText|showSnackbar)\s*\(""")

    /** The position of the text among positional arguments, and the names it may be passed by, per call. */
    private val textArgument = mapOf(
        "Text" to (0 to setOf("text")),
        "BasicText" to (0 to setOf("text")),
        "makeText" to (1 to setOf("text")),
        "showSnackbar" to (0 to setOf("message", "actionLabel")),
    )

    private class Literal(val start: Int, val end: Int, val hasLetter: Boolean)

    /** Every string literal in [source] passed as interface text, as (line, literal) pairs. */
    fun hardcodedInterfaceText(source: String): List<Pair<Int, String>> {
        val (code, literals) = lex(source)
        val literalAt = literals.associateBy { it.start }
        fun inLiteral(i: Int) = literals.any { i >= it.start && i < it.end }
        val values = mutableListOf<IntRange>()

        textCall.findAll(code).filter { !inLiteral(it.range.first) }.forEach { call ->
            val (position, names) = textArgument.getValue(call.groupValues[1])
            val args = arguments(code, call.range.last + 1, literalAt)
            args.filter { it.first == null }.getOrNull(position)?.let { values += it.second }
            args.filter { it.first in names }.forEach { values += it.second }
        }
        val names = uiOnlyNames + if (uiImport.containsMatchIn(code)) uiFileNames else emptyList()
        Regex("""\b(${names.joinToString("|")})\s*=(?!=)""").findAll(code)
            .filter { !inLiteral(it.range.first) }
            .forEach { values += expression(code, it.range.last + 1, literalAt) }

        return literals.filter { lit -> lit.hasLetter && values.any { lit.start in it } }
            .map { lit -> (source.substring(0, lit.start).count { it == '\n' } + 1) to source.substring(lit.start, lit.end) }
    }

    /** The arguments of the call whose `(` ends before [from]: (name or null, range of the value). */
    private fun arguments(code: String, from: Int, literalAt: Map<Int, Literal>): List<Pair<String?, IntRange>> {
        val args = mutableListOf<Pair<String?, IntRange>>()
        var i = from
        while (i < code.length) {
            val value = expression(code, i, literalAt)
            val text = code.substring(value.first, value.last + 1)
            val named = Regex("""^\s*(\w+)\s*=(?!=)""").find(text)
            if (text.isNotBlank()) args += (named?.groupValues?.get(1) to value)
            i = value.last + 1
            if (i >= code.length || code[i] != ',') break
            i++
        }
        return args
    }

    /**
     * The expression that starts at [from] (leading blanks and line breaks skipped): up to a `,`, `;`, closing
     * bracket, or line break not followed by a continuing operator, outside brackets and string literals.
     */
    private fun expression(code: String, from: Int, literalAt: Map<Int, Literal>): IntRange {
        var i = from
        while (i < code.length && code[i].isWhitespace()) i++
        val start = i
        var depth = 0
        while (i < code.length) {
            literalAt[i]?.let { i = it.end; continue }
            val c = code[i]
            if (depth == 0 && (c == ',' || c == ';' || c == ')' || c == '}' || c == ']')) break
            if (depth == 0 && c == '\n') {
                val next = code.substring(i).trimStart().firstOrNull()
                if (next == null || next !in ".?:+-*/&|") break
            }
            if (c == '(' || c == '{' || c == '[') depth++
            if (c == ')' || c == '}' || c == ']') depth--
            i++
        }
        return start until i
    }

    /** [source] with its comments blanked (line breaks kept), and its string literals. */
    private fun lex(source: String): Pair<String, List<Literal>> {
        val code = StringBuilder(source)
        val literals = mutableListOf<Literal>()
        fun blank(from: Int, to: Int) { for (k in from until to) if (code[k] != '\n') code[k] = ' ' }
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) -> {
                    val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                    blank(i, end); i = end
                }
                source.startsWith("/*", i) -> {
                    var depth = 0
                    var j = i
                    while (j < source.length) {
                        if (source.startsWith("/*", j)) { depth++; j += 2 }
                        else if (source.startsWith("*/", j)) { depth--; j += 2; if (depth == 0) break }
                        else j++
                    }
                    blank(i, j); i = j
                }
                source[i] == '\'' -> {
                    var j = i + 1
                    if (j < source.length && source[j] == '\\') j++
                    j++
                    i = if (j < source.length && source[j] == '\'') j + 1 else i + 1
                }
                source[i] == '"' -> {
                    val (end, hasLetter) = stringLiteral(source, i)
                    literals += Literal(i, end, hasLetter); i = end
                }
                else -> i++
            }
        }
        return code.toString() to literals
    }

    /** The end of the string literal opening at [start], and whether it holds a letter outside templates. */
    private fun stringLiteral(source: String, start: Int): Pair<Int, Boolean> {
        val raw = source.startsWith("\"\"\"", start)
        var i = start + if (raw) 3 else 1
        var hasLetter = false
        while (i < source.length) {
            val c = source[i]
            when {
                raw && source.startsWith("\"\"\"", i) -> {
                    i += 3
                    while (i < source.length && source[i] == '"') i++
                    return i to hasLetter
                }
                !raw && c == '"' -> return (i + 1) to hasLetter
                !raw && c == '\\' -> i += 2
                c == '$' && source.startsWith("\${", i) -> {
                    var depth = 1
                    i += 2
                    while (i < source.length && depth > 0) {
                        when (source[i]) {
                            '"' -> { i = stringLiteral(source, i).first; continue }
                            '{' -> depth++
                            '}' -> depth--
                        }
                        i++
                    }
                }
                c == '$' && i + 1 < source.length && (source[i + 1].isLetter() || source[i + 1] == '_') -> {
                    i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                }
                else -> { if (c.isLetter()) hasLetter = true; i++ }
            }
        }
        return i to hasLetter
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
        val shown = shownText(screen)
        assertTrue("the screen shows no text", shown.isNotEmpty())
        val foreign = shown.filter { it !in content && patterns.none { p -> p.matches(it) } }
        assertEquals("text not taken from the resources of this language", emptyList<String>(), foreign)
    }

    /**
     * L10N-001 (1) as the reader reads it: every text, description and action label shown, except the reader's
     * own content listed in [content], is written in the script of the language in force — Cyrillic in Russian,
     * Latin in English — whatever directory it came from. A Russian resource that holds English text, or the
     * reverse, fails here although it exists in both directories.
     */
    fun assertScreenTextWrittenInItsLanguage(
        screen: SemanticsNodeInteractionsProvider,
        context: Context,
        content: Set<String> = emptySet(),
    ) {
        val language = context.resources.configuration.locales[0].language
        val script = when (language) {
            "ru" -> Character.UnicodeScript.CYRILLIC
            "en" -> Character.UnicodeScript.LATIN
            else -> error("no script known for the language $language")
        }
        val shown = shownText(screen).filter { it !in content }
        assertTrue("the screen shows no interface text", shown.isNotEmpty())
        val foreign = shown.filter { text ->
            val letters = text.filter(Char::isLetter)
            letters.isEmpty() || letters.any { Character.UnicodeScript.of(it.code) != script }
        }
        assertEquals("text not written in $language", emptyList<String>(), foreign)
    }

    /** Every text, description and click-action label on [screen], as the screen reader can reach them. */
    private fun shownText(screen: SemanticsNodeInteractionsProvider): List<String> =
        screen.onAllNodes(isRoot().not() or isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    listOfNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.label)
            }
}
