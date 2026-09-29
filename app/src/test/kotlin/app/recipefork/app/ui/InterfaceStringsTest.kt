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

    // L10N-001 (2): the check finds interface text however the call is laid out, and nothing else.
    @Test
    fun theCheckFindsInterfaceTextInEveryLayout() {
        val source = """
            import androidx.compose.material3.Text
            import androidx.compose.ui.semantics.semantics

            @Composable
            fun Screen(title: String, count: Int) {
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
                TextField(
                    value = title,
                    label = { Text("Label") },
                    placeholder = {
                        Text(text = "Placeholder")
                    },
                )
                Text(title.ifBlank { "Untitled" })
                Toast.makeText(context,
                    "Toasted", Toast.LENGTH_SHORT)
                Text(${'"'}""Raw""${'"'})
                // Text("In a comment")
                Text("${'$'}count")
                Text(stringResource(R.string.editor_tags), modifier = Modifier.testTag("editor-tags"))
                Log.d("Editor", "Text(\"not interface\")")
                val tag = "editor-title"
            }
        """.trimIndent()
        assertEquals(
            listOf(
                "Tags", "Named", "Close", "Described", "Label", "Placeholder", "Untitled", "Toasted", "Raw",
            ),
            LocalizationChecks.hardcodedInterfaceText(source).map { it.second.trim('"') },
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
