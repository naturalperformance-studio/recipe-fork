package app.recipefork.app.dictionary

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

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
    fun theDemoEntriesCarryTheirDivisibilityAndFactors() {
        val byLabel = snapshot.ingredients.associateBy { it.labelRu }
        fun entry(label: String) = byLabel.getValue(label)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), snapshot.ingredients.map { it.id })
        assertEquals(1L, entry("курица").id)
        assertEquals(true, entry("курица").divisible)
        assertEquals(mapOf("g" to 1.0, "pc" to 1500.0), entry("курица").factors)
        assertEquals(2L, entry("крылышки").id)
        assertEquals(1L, entry("крылышки").parentId)
        assertEquals(false, entry("крылышки").divisible)
        assertEquals(mapOf("g" to 1.0, "pc" to 80.0), entry("крылышки").factors)
        listOf("мясо", "фарш", "говядина").forEach {
            assertEquals(it, null, entry(it).divisible)
            assertEquals(it, mapOf("g" to 1.0), entry(it).factors)
        }
        assertTrue(snapshot.ingredients.none { it.removed })
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
        val content = Json.encodeToString(DictionariesDto.serializer(), snapshot.copy(version = ""))
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        assertEquals(digest.joinToString("") { "%02x".format(it) }, snapshot.version)
    }
}
