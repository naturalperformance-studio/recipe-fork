package app.recipefork.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.data.Network
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shipped snapshot seeds the device (decisions/20260928-dictionaries-ship-with-the-application.md): when it
 * holds no dictionaries, or holds an earlier snapshot; never over a copy fetched from the server.
 */
@RunWith(AndroidJUnit4::class)
class DictionarySeedingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.dictionaryDao()

    private val seeded = object : SeededVersion {
        var version: String? = null
        override fun get() = version
        override fun set(version: String) {
            this.version = version
        }
    }

    private var shipped = snapshot("s1", "Italian")
    private val seeding = DictionarySeeding(dao, seeded) { shipped }

    private fun snapshot(version: String, tag: String) = DictionariesDto(
        version = version,
        ingredients = listOf(IngredientEntryDto(1, "курица", "chicken"), IngredientEntryDto(2, "крылышки", "chicken wings", 1)),
        tags = listOf(TagDto(10, "итальянская", tag)),
    )

    private fun refreshFromServer(served: DictionariesDto) = runBlocking {
        val engine = MockEngine {
            respond(Json.encodeToString(DictionariesDto.serializer(), served), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        assertEquals(DictionaryRepository.Refresh.Replaced, DictionaryRepository(Network.client(engine, "http://server.test"), dao).refresh())
    }

    private fun held(): Pair<String?, List<String>> = runBlocking { dao.version() to dao.tags().map { it.labelEn } }

    @After
    fun close() = db.close()

    @Test
    fun aDeviceHoldingNoDictionariesIsSeededFromTheSnapshot() = runBlocking {
        assertNull(dao.version())
        assertTrue(seeding.seedIfNeeded())
        assertEquals("s1" to listOf("Italian"), held())
        assertEquals(listOf(IngredientEntryEntity(1, "курица", "chicken", null), IngredientEntryEntity(2, "крылышки", "chicken wings", 1)), dao.ingredients())
        assertEquals("s1", seeded.version)
        // Seeded once: the same snapshot leaves it as it is.
        assertFalse(seeding.seedIfNeeded())
    }

    @Test
    fun aCopyFromTheServerIsNeverReplacedBySeeding() = runBlocking {
        seeding.seedIfNeeded()
        refreshFromServer(snapshot("server-2", "Italian cuisine"))
        shipped = snapshot("s2", "Italian food")
        assertFalse(seeding.seedIfNeeded())
        assertEquals("server-2" to listOf("Italian cuisine"), held())
    }

    @Test
    fun aCopyFromTheServerOnADeviceNeverSeededStays() = runBlocking {
        refreshFromServer(snapshot("server-1", "Italian cuisine"))
        assertFalse(seeding.seedIfNeeded())
        assertEquals("server-1" to listOf("Italian cuisine"), held())
    }

    @Test
    fun anEarlierSnapshotIsReplacedByTheSnapshotOfANewerApplication() = runBlocking {
        seeding.seedIfNeeded()
        shipped = snapshot("s2", "Italian food")
        assertTrue(seeding.seedIfNeeded())
        assertEquals("s2" to listOf("Italian food"), held())
        assertEquals("s2", seeded.version)
    }

    @Test
    fun theShippedSnapshotIsReadFromTheApplicationsAssets() {
        val read = DictionarySnapshot.read(context)
        assertTrue(read.tags.isNotEmpty())
        assertTrue(read.version.isNotBlank())
    }

    // DOM-011, CAT-009: the divisibility, the factors and the removal of an entry reach the device as the server gives them.
    @Test
    fun anEntryKeepsItsDivisibilityFactorsAndRemovalOnTheDevice() = runBlocking {
        val chicken = IngredientEntryDto(1, "курица", "chicken", divisible = true, factors = mapOf("g" to 1.0, "pc" to 1500.0))
        val wings = IngredientEntryDto(2, "крылышки", "chicken wings", 1, divisible = false, factors = mapOf("g" to 1.0, "pc" to 80.0))
        val goose = IngredientEntryDto(3, "гусь", "goose", removed = true)
        shipped = DictionariesDto("s1", listOf(chicken, wings, goose), emptyList())
        assertTrue(seeding.seedIfNeeded())
        assertEquals(listOf(chicken, wings, goose), dao.ingredients().map { it.toDto() })
        assertEquals(
            IngredientEntryEntity(2, "крылышки", "chicken wings", 1, false, mapOf("g" to 1.0, "pc" to 80.0), false),
            dao.ingredients()[1],
        )
    }

    // FLOW-001 (7), DOM-009 (5): replacing the held copy keeps a removed entry, so every recipe naming it keeps its name —
    // whether the new copy carries it marked removed or no longer carries it at all.
    @Test
    fun replacingTheDictionaryKeepsRemovedEntries() = runBlocking {
        seeding.seedIfNeeded()
        val marked = DictionariesDto(
            "server-1",
            listOf(IngredientEntryDto(1, "курица", "chicken", removed = true), IngredientEntryDto(2, "крылышки", "chicken wings", 1)),
            emptyList(),
        )
        refreshFromServer(marked)
        assertEquals(listOf(1L to true, 2L to false), dao.ingredients().map { it.id to it.removed })

        refreshFromServer(DictionariesDto("server-2", listOf(IngredientEntryDto(1, "курица", "chicken", removed = true)), emptyList()))
        assertEquals(
            listOf(IngredientEntryEntity(1, "курица", "chicken", null, removed = true), IngredientEntryEntity(2, "крылышки", "chicken wings", 1, removed = true)),
            dao.ingredients(),
        )
        // An entry back upstream is offered again.
        refreshFromServer(DictionariesDto("server-3", listOf(IngredientEntryDto(2, "крылышки", "chicken wings", 1)), emptyList()))
        assertEquals(listOf(1L to true, 2L to false), dao.ingredients().map { it.id to it.removed })
    }

    // AppDatabase version 2: a device holding the version-1 store has it wiped, not migrated, and the dictionaries are
    // seeded again on their next read — even though the snapshot it was seeded from has not changed.
    @Test
    fun aVersionOneStoreIsWipedAndTheDictionariesAreSeededAgain() = runBlocking {
        val name = "app-database-v1-test.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            v1.execSQL("create table ingredient_entry (id integer not null primary key, labelRu text not null, labelEn text not null, parentId integer)")
            v1.execSQL("create table tag (id integer not null primary key, labelRu text not null, labelEn text not null)")
            v1.execSQL("create table dictionary_version (id integer not null primary key, version text not null)")
            v1.execSQL("insert into ingredient_entry values (1, 'курица', 'chicken', null)")
            v1.execSQL("insert into dictionary_version values (0, 's1')")
            v1.version = 1
        }
        seeded.version = "s1"
        shipped = DictionariesDto("s1", listOf(IngredientEntryDto(1, "курица", "chicken", divisible = true, factors = mapOf("pc" to 1500.0))), emptyList())

        val opened = AppDatabase.open(context, name)
        try {
            val dao = opened.dictionaryDao()
            assertNull(dao.version())
            val catalog = DictionaryCatalog(dao, DictionarySeeding(dao, seeded) { shipped })
            assertEquals(listOf(IngredientEntryEntity(1, "курица", "chicken", null, true, mapOf("pc" to 1500.0))), catalog.ingredients())
            assertEquals("s1", dao.version())
            assertEquals(AppDatabase.VERSION, opened.openHelper.readableDatabase.version)
        } finally {
            opened.close()
            context.deleteDatabase(name)
        }
    }
}
