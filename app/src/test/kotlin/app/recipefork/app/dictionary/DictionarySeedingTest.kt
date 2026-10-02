package app.recipefork.app.dictionary

import android.content.Context
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
}
