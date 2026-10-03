package app.recipefork.app.dictionary

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The committed `assets/dictionaries.json` against the store's rules (decisions/20260928-dictionaries-ship-with-the-
 * application.md): labels in both languages (L10N-003), at most two levels (DOM-010), and the version the server
 * gives exactly this content.
 */
class ShippedSnapshotTest {
    /** Unit tests of `:app` run with the module directory as the working directory. */
    private val snapshot = DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText())

    @Test
    fun everyEntryAndTagIsLabelledInBothLanguages() {
        assertTrue(snapshot.tags.isNotEmpty())
        assertTrue(snapshot.ingredients.isNotEmpty())
        snapshot.ingredients.forEach { assertTrue("$it", it.labelRu.isNotBlank() && it.labelEn.isNotBlank()) }
        snapshot.tags.forEach { assertTrue("$it", it.labelRu.isNotBlank() && it.labelEn.isNotBlank()) }
    }

    @Test
    fun everyEntryRemovedOrNotKeepsBothLabels() {
        // A removed entry still names the recipes that hold it (DOM-009), so it keeps both its labels too.
        snapshot.ingredients.filter { it.removed }.forEach { assertTrue("$it", it.labelRu.isNotBlank() && it.labelEn.isNotBlank()) }
        snapshot.ingredients.filterNot { it.removed }.forEach { assertTrue("$it", it.labelRu.isNotBlank() && it.labelEn.isNotBlank()) }
    }

    @Test
    fun everyFactorNamesAKnownUnitWithANumberAboveZero() {
        snapshot.ingredients.forEach { entry ->
            assertTrue("$entry", entry.factors.keys.all { it in IngredientEntryDto.FACTOR_UNITS })
            assertTrue("$entry", entry.factors.values.all { it.isFinite() && it > 0 })
            assertTrue("$entry", IngredientEntryDto.validFactors(entry.factors))
        }
    }

    @Test
    fun theLegacyEntriesAndTagsArePresentAndUnchanged() {
        // Ids 1-5 and 10, 11 were shipped before the dictionary had sources; any other entry may stand beside them.
        val byId = snapshot.ingredients.associateBy { it.id }
        assertEquals(
            listOf(
                IngredientEntryDto(1, "курица", "chicken", null, true, mapOf("g" to 1.0, "pc" to 1500.0)),
                IngredientEntryDto(
                    2, "крылышки", "chicken wings", 1, false, mapOf("g" to 1.0, "pc" to 80.0),
                    synonymsRu = listOf("куриные крылья", "куриные крылышки"),
                ),
                IngredientEntryDto(3, "мясо", "meat", factors = mapOf("g" to 1.0)),
                IngredientEntryDto(4, "фарш", "mince", factors = mapOf("g" to 1.0)),
                IngredientEntryDto(5, "говядина", "beef", factors = mapOf("g" to 1.0)),
            ),
            (1L..5L).map { byId[it] },
        )
        val tags = snapshot.tags.associateBy { it.id }
        assertEquals(listOf(TagDto(10, "итальянская", "Italian"), TagDto(11, "грузинская", "Georgian")), listOf(tags[10], tags[11]))
    }

    @Test
    fun ingredientsAreAtMostTwoLevelsDeepAndIdsAreUnique() {
        val byId = snapshot.ingredients.associateBy { it.id }
        assertEquals(snapshot.ingredients.size, byId.size)
        assertEquals(snapshot.tags.size, snapshot.tags.map { it.id }.toSet().size)
        snapshot.ingredients.filter { it.parentId != null }.forEach {
            val parent = byId[it.parentId]
            assertTrue("$it names a parent that is not held", parent != null)
            assertEquals("$it sits under an entry that has a parent", null, parent!!.parentId)
        }
    }

    @Test
    fun theVersionIsTheOneTheServerGivesThisContent() {
        // As the server computes it (DictionaryRoutes): a digest of the content with an empty version.
        assertEquals(snapshot.digest(), snapshot.version)
    }
}
