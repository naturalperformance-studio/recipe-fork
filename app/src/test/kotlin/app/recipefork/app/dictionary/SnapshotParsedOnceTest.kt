package app.recipefork.app.dictionary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.AppContainer
import app.recipefork.app.Mode
import app.recipefork.app.card.cardEnvironmentOf
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.editor.editorEnvironmentOf
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shipped snapshot is parsed at most once per installation: the installation it was checked for is remembered
 * in the device's preferences, outside the database, so a later screen or a later launch does not parse it; a new
 * installation (a new snapshot) is parsed and seeds; a database rebuilt empty is seeded again all the same.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotParsedOnceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<AppDatabase>()
    private var parses = 0
    private var shipped = snapshot("s1", "Italian")

    private fun snapshot(version: String, tag: String) = DictionariesDto(
        version = version,
        ingredients = listOf(IngredientEntryDto(1, "курица", "chicken"), IngredientEntryDto(2, "крылышки", "chicken wings", 1)),
        tags = listOf(TagDto(10, "итальянская", tag)),
    )

    private fun database(): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().also { databases += it }

    /** The catalog as a launch of the installation [installed] builds it, over [db], with the device's preferences. */
    private fun launch(db: AppDatabase, installed: String): DictionaryCatalog = db.dictionaryDao().let { dao ->
        DictionaryCatalog(
            dao,
            DictionarySeeding(
                dao,
                PreferencesSeededVersion(context),
                installed = installed,
                checked = PreferencesSeededVersion(context, PreferencesSeededVersion.CHECKED),
            ) { parses++; shipped },
        )
    }

    /** What the editor and the card read as they open: the tags and the ingredients, several at once. */
    private fun openScreens(catalog: DictionaryCatalog) = runBlocking {
        (1..3).flatMap { listOf(async { catalog.tags() }, async { catalog.ingredients() }) }.awaitAll()
        Unit
    }

    private fun held(db: AppDatabase) = runBlocking { db.dictionaryDao().version() to db.dictionaryDao().tags().map { it.labelEn } }

    @After
    fun close() = databases.forEach { it.close() }

    @Test
    fun aLaunchParsesTheSnapshotOnceHoweverManyScreensRead() {
        val db = database()
        val catalog = launch(db, "install-1")
        openScreens(catalog)
        openScreens(catalog)
        assertEquals(1, parses)
        assertEquals("s1" to listOf("Italian"), held(db))
    }

    @Test
    fun aLaterLaunchOfTheSameInstallationParsesNothing() {
        val db = database()
        openScreens(launch(db, "install-1"))
        openScreens(launch(db, "install-1"))
        openScreens(launch(db, "install-1"))
        assertEquals(1, parses)
        assertEquals("s1" to listOf("Italian"), held(db))
    }

    @Test
    fun aNewInstallationParsesItsSnapshotOnceAndAChangedOneSeeds() {
        val db = database()
        openScreens(launch(db, "install-1"))
        shipped = snapshot("s2", "Italian food")
        val updated = launch(db, "install-2")
        openScreens(updated)
        openScreens(updated)
        assertEquals(2, parses)
        assertEquals("s2" to listOf("Italian food"), held(db))
        openScreens(launch(db, "install-2"))
        assertEquals(2, parses)
    }

    @Test
    fun aDatabaseRebuiltEmptyIsSeededAgainThoughTheInstallationIsRemembered() {
        openScreens(launch(database(), "install-1"))
        // The same installation, its database dropped and rebuilt empty by a destructive migration.
        val rebuilt = database()
        openScreens(launch(rebuilt, "install-1"))
        assertEquals(2, parses)
        assertEquals("s1" to listOf("Italian"), held(rebuilt))
        openScreens(launch(rebuilt, "install-1"))
        assertEquals(2, parses)
    }

    @Test
    fun aCopyFromTheServerIsKeptAndTheSnapshotNotParsedAgain() {
        val db = database()
        runBlocking { db.dictionaryDao().replace(snapshot("server-1", "Italian cuisine")) }
        openScreens(launch(db, "install-1"))
        openScreens(launch(db, "install-1"))
        assertEquals(1, parses)
        assertEquals("server-1" to listOf("Italian cuisine"), held(db))
    }

    // One catalog for the application: the editor and the card read the container's, so opening both, again and
    // again and in a later launch, parses the shipped asset once.
    @Test
    fun theEditorAndTheCardShareTheContainersCatalog() {
        var read = 0
        val counting: (Context) -> DictionariesDto = { read++; DictionarySnapshot.read(it) }
        val first = AppContainer(context, Mode.Serverless, readSnapshot = counting)
        val later = AppContainer(context, Mode.Serverless, readSnapshot = counting)
        try {
            val editor = editorEnvironmentOf(first)
            assertSame(first.dictionaries, editor.tags)
            assertSame(first.dictionaries, editor.ingredients)
            runBlocking {
                repeat(3) {
                    editor.tags.tags()
                    editor.ingredients.ingredients()
                    assertTrue(cardEnvironmentOf(first).ingredients.entries().isNotEmpty())
                }
                assertEquals(1, read)
                editorEnvironmentOf(later).tags.tags()
                cardEnvironmentOf(later).ingredients.entries()
            }
            assertEquals(1, read)
        } finally {
            first.database.close()
            later.database.close()
        }
    }
}
