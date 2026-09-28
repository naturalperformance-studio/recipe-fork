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
