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
     * The rule reads no expressions, so no layout of a call can hide text from it. In every interface source
     * ([isInterfaceSource]), with its comments blanked, every string or character literal that holds a letter
     * (Latin, Cyrillic or any other; a `\u` escape that stands for a letter counts as one) fails — wherever it
     * stands — unless it is in one of the [allowed] contexts. Letters inside `$name` and `${…}` templates are
     * not the literal's own text, so `"$count"` and `"${a}/${b}"` pass; a literal written inside a template is a
     * literal of its own and is read like any other.
     *
     * The allowed contexts are recognised on the literal's own line, the literal standing exactly there:
     *  - the direct argument of `.testTag(…)`: `Modifier.testTag("editor-title")`;
     *  - the tag or message of `Log.v/d/i/w/e(…)`: `Log.d("Editor", "…")`, `Log.d(TAG, "…")`;
     *  - the SQL of `@Query(…)`, a Room `@Entity(tableName = …)` or `@ColumnInfo(name = …)`;
     *  - the whole value of a `const val` whose name ends in `_KEY`, `_ROUTE` or `_TAG` (saved-state keys,
     *    navigation routes, test tags): `const val VIEW_KEY = "library.view"`;
     *  - the whole value of a `const val` or single-expression `fun` declared directly in an `object` whose name
     *    ends in `Tags` — the screens' test-tag holders (`EditorTags`, `LibraryTags`).
     * Anything else with a letter fails; text that is not the interface's takes a name in one of these forms.
     */
    fun assertNoHardcodedInterfaceText(sources: File = sourceDir) {
        val files = sources.walkTopDown().filter { it.extension == "kt" }
            .map { it to it.readText() }
            .filter { (file, text) -> isInterfaceSource(file.path, text) }
            .toList()
        val read = files.map { it.first.name }.toSet()
        assertTrue("interface sources not found under $sources: $read", read.containsAll(listOf("EditorScreen.kt", "LibraryScreen.kt", "AppNavHost.kt")))
        val found = files.flatMap { (file, text) ->
            hardcodedInterfaceText(text).map { (line, literal) -> "${file.path}:$line: $literal" }
        }
        assertEquals("interface text fixed in the code", emptyList<String>(), found)
    }

    /** The interface packages, as directories of the source tree; their subpackages are included. */
    private val interfacePackages = listOf("editor", "library", "ui").map { "app/recipefork/app/$it/" }
    private val composeUiImport = Regex("""^\s*import\s+androidx\.compose\.(ui|material3)\b""", RegexOption.MULTILINE)

    /**
     * Whether the source at [path] is interface code: it lies in the `editor`, `library` or `ui` package, or —
     * in any package — it imports `androidx.compose.ui` or `androidx.compose.material3`.
     */
    fun isInterfaceSource(path: String, source: String): Boolean {
        val (code, literals) = lex(source)
        return interfacePackages.any { path.replace('\\', '/').contains(it) } ||
            composeUiImport.containsMatchIn(blankLiterals(code, literals))
    }

    /** A context where a literal is not interface text: the code before and after it on its line. */
    private class AllowedContext(val before: Regex, val after: Regex, val directlyInTagsObject: Boolean = false)

    private val lineEnd = Regex("""^\s*$""")
    private val literalText = """"(?:[^"\\\n]|\\.)*""""
    private val allowed = mapOf(
        "test tag" to AllowedContext(Regex("""\.testTag\(\s*$"""), Regex("""^\s*\)""")),
        "log tag or message" to AllowedContext(
            Regex("""\bLog\.[vdiwe]\(\s*(?:(?:\w+|$literalText)\s*,\s*)?$"""),
            Regex("""^\s*[,)]"""),
        ),
        "Room query" to AllowedContext(Regex("""@Query\(\s*$"""), Regex("""^\s*\)""")),
        "Room table or column" to AllowedContext(
            Regex("""@(?:Entity\(\s*tableName|ColumnInfo\(\s*name)\s*=\s*$"""),
            Regex("""^\s*[,)]"""),
        ),
        "key, route or tag constant" to AllowedContext(
            Regex("""^\s*(?:(?:private|internal|public)\s+)?const val [A-Z][A-Z0-9_]*_(?:KEY|ROUTE|TAG)\s*=\s*$"""),
            lineEnd,
        ),
        "member of a Tags object" to AllowedContext(
            Regex("""^\s*(?:const val [A-Z][A-Z0-9_]*|fun \w+\([^()]*\))\s*=\s*$"""),
            lineEnd,
            directlyInTagsObject = true,
        ),
    )
    private val tagsObject = Regex("""\bobject\s+\w*Tags\s*\{""")

    private class Literal(val start: Int, val end: Int, val hasLetter: Boolean)

    /** Every literal in [source] that holds a letter and stands in no [allowed] context, as (line, literal). */
    fun hardcodedInterfaceText(source: String): List<Pair<Int, String>> {
        val (code, literals) = lex(source)
        val blanked = blankLiterals(code, literals)
        val tagsBodies = tagsObject.findAll(blanked).map { it.range.last until closingBrace(blanked, it.range.last) }.toList()
        fun directlyIn(body: IntRange, at: Int) =
            at in body && blanked.substring(body.first, at).let { s -> s.count { it == '{' } - s.count { it == '}' } } == 1

        return literals.filter { it.hasLetter }.filter { lit ->
            val before = code.substring(code.lastIndexOf('\n', lit.start - 1) + 1, lit.start)
            val after = code.substring(lit.end, code.indexOf('\n', lit.end).let { if (it < 0) code.length else it })
            allowed.values.none { context ->
                context.before.containsMatchIn(before) && context.after.containsMatchIn(after) &&
                    (!context.directlyInTagsObject || tagsBodies.any { directlyIn(it, lit.start) })
            }
        }.map { lit -> (source.substring(0, lit.start).count { it == '\n' } + 1) to source.substring(lit.start, lit.end) }
    }

    /** [code] with every literal's characters blanked (line breaks kept), so brackets and imports read as code. */
    private fun blankLiterals(code: String, literals: List<Literal>): String {
        val out = StringBuilder(code)
        literals.forEach { lit -> for (k in lit.start until lit.end) if (out[k] != '\n') out[k] = ' ' }
        return out.toString()
    }

    /** The index of the `}` that closes the `{` at [open] in [code] (literals and comments already blanked). */
    private fun closingBrace(code: String, open: Int): Int {
        var depth = 0
        for (i in open until code.length) {
            if (code[i] == '{') depth++
            if (code[i] == '}' && --depth == 0) return i
        }
        return code.length
    }

    /**
     * [source] with its comments blanked (line breaks kept), and its string and character literals — those written
     * inside another literal's templates included, each on its own.
     */
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
                source[i] == '\'' -> i = charLiteral(source, i, literals)
                source[i] == '"' -> {
                    val end = stringLiteral(source, i, literals)
                    i = end
                }
                else -> i++
            }
        }
        return code.toString() to literals.sortedBy { it.start }
    }

    /** Reads the character literal opening at [start] into [literals] and returns its end; a lone `'` is skipped. */
    private fun charLiteral(source: String, start: Int, literals: MutableList<Literal>): Int {
        val j = start + 1
        if (j >= source.length) return j
        val (end, letter) = if (source[j] == '\\') escape(source, j) else (j + 1) to source[j].isLetter()
        if (end < source.length && source[end] == '\'') {
            literals += Literal(start, end + 1, letter)
            return end + 1
        }
        return j
    }

    /** The end of the escape at [at] (a `\`), and whether it stands for a letter (`A`; not `\n`, `\t`…). */
    private fun escape(source: String, at: Int): Pair<Int, Boolean> {
        if (source.startsWith("\\u", at) && at + 6 <= source.length) {
            val code = source.substring(at + 2, at + 6).toIntOrNull(16)
            if (code != null) return (at + 6) to code.toChar().isLetter()
        }
        return (at + 2) to false
    }

    /**
     * Reads the string literal opening at [start], adds it — and every literal written inside its templates — to
     * [literals], and returns its end. A literal holds a letter when one stands outside its `$` templates.
     */
    private fun stringLiteral(source: String, start: Int, literals: MutableList<Literal>): Int {
        val raw = source.startsWith("\"\"\"", start)
        var i = start + if (raw) 3 else 1
        var hasLetter = false
        fun done(end: Int): Int { literals += Literal(start, end, hasLetter); return end }
        while (i < source.length) {
            val c = source[i]
            when {
                raw && source.startsWith("\"\"\"", i) -> {
                    i += 3
                    while (i < source.length && source[i] == '"') i++
                    return done(i)
                }
                !raw && c == '"' -> return done(i + 1)
                !raw && c == '\\' -> {
                    val (next, letter) = escape(source, i)
                    if (letter) hasLetter = true
                    i = next
                }
                c == '$' && source.startsWith("\${", i) -> {
                    var depth = 1
                    i += 2
                    while (i < source.length && depth > 0) {
                        when (source[i]) {
                            '"' -> { i = stringLiteral(source, i, literals); continue }
                            '\'' -> { i = charLiteral(source, i, literals); continue }
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
        return done(i)
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
     * accepted as this language's text. A plural resource gives each of its forms (as 0, 1, 2 and 5 select them).
     */
    fun ownResolvedStrings(context: Context, res: File = resDir): List<String> {
        val own = stringsIn(ownValuesDir(context, res)).keys
        val strings = R.string::class.java.fields.filter { it.name in own }.map { context.getString(it.getInt(null)) }
        val plurals = R.plurals::class.java.fields.filter { it.name in own }.flatMap { field ->
            PLURAL_SAMPLES.map { context.resources.getQuantityText(field.getInt(null), it).toString() }
        }.distinct()
        return strings + plurals
    }

    /** Counts that between them select every plural form of English and Russian: other, one, few and many. */
    private val PLURAL_SAMPLES = listOf(0, 1, 2, 5)

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
    fun shownText(screen: SemanticsNodeInteractionsProvider): List<String> =
        screen.onAllNodes(isRoot().not() or isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    listOfNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.label)
            }
}
