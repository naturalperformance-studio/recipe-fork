package app.recipefork.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** L10N-001 over the whole application, and the section's name of FLOW-034 (8) in every string. */
class InterfaceStringsTest {
    // L10N-001 (1)
    @Test
    fun everyStringExistsInEnglishAndInRussian() = LocalizationChecks.assertEveryStringInBothLanguages()

    // L10N-001 (2)
    @Test
    fun noInterfaceTextIsFixedInTheCode() = LocalizationChecks.assertNoHardcodedInterfaceText()

    // L10N-001 (2): every literal with a letter in interface code is found, however it is laid out, except in the
    // named allowed contexts.
    @Test
    fun theCheckFindsInterfaceTextInEveryLayout() {
        val source = """
            import androidx.compose.material3.Text
            import androidx.compose.ui.semantics.semantics

            @Composable
            fun Screen(name: String, title: String, count: Int, x: Boolean, open: Boolean) {
                Text(
                    "Tags",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    modifier = Modifier,
                    text =
                        "Named",
                )
                Icon(Icons.Default.Close, contentDescription = "Close")
                Box(Modifier.semantics {
                    contentDescription = "Described"
                })
                TextField(value = title, label = { Text("Label") }, placeholder = {
                    Text(text = "Placeholder")
                })
                Text(title.ifBlank { "Untitled" })
                Toast.makeText(context,
                    "Toasted", Toast.LENGTH_SHORT)
                Text(${'"'}""Raw""${'"'})
                Text(name +
                    "Plus")
                Text(
                    if (x)
                        "Alpha"
                    else
                        "Beta"
                )
                Icon(
                    Icons.Default.Menu,
                    contentDescription =
                        if (open) "Open"
                        else "Closed",
                )
                val assigned = "Assigned"
                Text(assigned)
                Text("Теги")
                Text("${'$'}{if (x) "Nested" else ""}")
                Text("${'\\'}u0054${'\\'}u0061${'\\'}u0067")
                Text('T'.toString())
                Log.d(TAG, "fine"); Text("Logged")
                Box(Modifier.testTag("tag-" + "Joined"))
                // Text("In a comment")
                /* Text("In a block comment") */
                Text("${'$'}count")
                Text("${'$'}{name}/${'$'}{title}: ${'$'}count")
                Text(stringResource(R.string.editor_tags), modifier = Modifier.testTag("editor-tags"))
                Log.d("Editor", "Text(\"not interface\")")
                Text(if (x) ScreenTags.TITLE else ScreenTags.row(1))
            }

            @Dao
            interface TagDao {
                @Query("select * from tag order by id")
                fun all(): List<TagEntity>
            }

            private const val VIEW_KEY = "library.view"
            const val LIBRARY_ROUTE = "library"
            const val TITLE = "Title outside a Tags object"

            object ScreenTags {
                const val TITLE = "screen-title"
                fun row(id: Long) = "screen-row-${'$'}id"
                fun label(x: Boolean): String {
                    return "Nested in a function"
                }
            }
        """.trimIndent()
        assertEquals(
            listOf(
                "Tags", "Named", "Close", "Described", "Label", "Placeholder", "Untitled", "Toasted", "Raw", "Plus",
                "Alpha", "Beta", "Open", "Closed", "Assigned", "Теги", "Nested", "\\u0054\\u0061\\u0067", "T",
                "Logged", "tag-", "Joined", "Title outside a Tags object", "Nested in a function",
            ),
            LocalizationChecks.hardcodedInterfaceText(source).map { it.second.trim('"', '\'') },
        )
    }

    // L10N-001 (2): the rule reads the interface packages and every source that imports Compose UI or Material 3.
    @Test
    fun theCheckReadsEveryInterfaceSource() {
        val compose = "import androidx.compose.material3.Text\n"
        val plain = "import java.io.File\n"
        assertEquals(
            listOf(true, true, true, true, false, false),
            listOf(
                "src/main/kotlin/app/recipefork/app/editor/RecipeEditor.kt" to plain,
                "src/main/kotlin/app/recipefork/app/library/sub/Row.kt" to plain,
                "src/main/kotlin/app/recipefork/app/ui/Theme.kt" to plain,
                "src/main/kotlin/app/recipefork/app/book/BookPicker.kt" to compose,
                "src/main/kotlin/app/recipefork/app/dictionary/DictionaryDao.kt" to plain,
                "src/main/kotlin/app/recipefork/app/data/Network.kt" to "// import androidx.compose.ui.Modifier\n",
            ).map { (path, source) -> LocalizationChecks.isInterfaceSource(path, source) },
        )
    }

    // FLOW-034 (8): "favourites" names nothing in the interface, in either language.
    @Test
    fun noInterfaceStringSaysFavourites() {
        val words = Regex("favou?rite|избранн", RegexOption.IGNORE_CASE)
        val strings = listOf("values", "values-ru").flatMap {
            LocalizationChecks.stringsIn(java.io.File(LocalizationChecks.resDir, it)).values
        }
        assertEquals(emptyList<String>(), strings.filter { words.containsMatchIn(it) })
    }
}
