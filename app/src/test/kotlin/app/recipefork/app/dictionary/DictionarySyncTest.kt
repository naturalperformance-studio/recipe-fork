package app.recipefork.app.dictionary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.data.Connectivity
import app.recipefork.app.data.Network
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class DictionarySyncTest {
    private val v1 = DictionariesDto(
        version = "v1",
        ingredients = listOf(
            IngredientEntryDto(1, "курица", "chicken"),
            IngredientEntryDto(2, "крылышки", "chicken wings", parentId = 1),
            IngredientEntryDto(3, "говядина", "beef"),
        ),
        tags = listOf(TagDto(10, "итальянская", "Italian")),
    )

    // The product owner removes beef, relabels a tag and adds both an entry and a tag.
    private val v2 = DictionariesDto(
        version = "v2",
        ingredients = listOf(
            IngredientEntryDto(1, "курица", "chicken"),
            IngredientEntryDto(2, "крылышки", "chicken wings", parentId = 1),
            IngredientEntryDto(4, "бедро", "chicken thigh", parentId = 1),
        ),
        tags = listOf(TagDto(10, "итальянская", "Italian cuisine"), TagDto(11, "грузинская", "Georgian")),
    )

    /** The server as the device sees it: what it holds now, and whether the device can reach it. */
    private var served = v1
    @Volatile private var connected = true

    // Every change is delivered, as the platform's callbacks are: a StateFlow would conflate a quick loss and return.
    private val connectivityEvents = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 16).apply { tryEmit(true) }

    private fun connection(available: Boolean) {
        connected = available
        connectivityEvents.tryEmit(available)
    }
    private val heldVersionsSent: MutableList<String?> = Collections.synchronizedList(mutableListOf())

    /** How many of the next requests a captive portal answers with its login page instead of the server. */
    @Volatile private var portalAnswers = 0

    private val engine = MockEngine { request ->
        if (!connected) throw IOException("no network")
        val held = request.headers[HttpHeaders.IfNoneMatch]
        heldVersionsSent += held
        if (portalAnswers > 0) {
            portalAnswers--
            respond(
                "<html><body>Log in to the Wi-Fi</body></html>",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "text/html"),
            )
        } else if (held == "\"${served.version}\"") {
            respond("", HttpStatusCode.NotModified)
        } else {
            respond(
                Json.encodeToString(served),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    private val connectivity = object : Connectivity {
        override val online = connectivityEvents
    }

    private lateinit var db: AppDatabase
    private lateinit var dao: DictionaryDao
    private lateinit var sync: DictionarySync
    // What would crash the process on a device: an exception escaping the sync into the application scope.
    private val uncaught: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught += e })

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.dictionaryDao()
        val repository = DictionaryRepository(Network.client(engine, "http://server.test"), dao)
        sync = DictionarySync(connectivity, repository)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `FLOW-001 - given a connection returns after the product owner changed a dictionary, when the app next reaches the server, the held copy is replaced`() = runBlocking {
        sync.start(scope)
        awaitHeldVersion("v1")
        assertHeld(v1)

        connection(false)
        served = v2
        // Without a connection nothing changes on the device, and nothing is lost either.
        assertEquals(DictionaryRepository.Refresh.Unreachable, DictionaryRepository(Network.client(engine, "http://server.test"), dao).refresh())
        assertHeld(v1)

        connection(true)
        awaitHeldVersion("v2")
        assertHeld(v2)
        assertEquals("\"v1\"", heldVersionsSent.last())
    }

    @Test
    fun `FLOW-001 - when the dictionaries have not changed, reaching the server leaves the held copy as it is`() = runBlocking {
        sync.start(scope)
        awaitHeldVersion("v1")

        connection(false)
        connection(true)
        withTimeout(5_000) { while (heldVersionsSent.size < 2) delay(10) }

        assertEquals(listOf(null, "\"v1\""), heldVersionsSent.toList())
        assertHeld(v1)
    }

    @Test
    fun `FLOW-001 - an answer that is not the dictionaries leaves the held copy, and a later return of the connection still replaces it`() = runBlocking {
        sync.start(scope)
        awaitHeldVersion("v1")

        connection(false)
        served = v2
        portalAnswers = 1
        connection(true)
        // The connection is back but a captive portal answers 200 with its login page.
        withTimeout(5_000) { while (heldVersionsSent.size < 2 || portalAnswers > 0) delay(10) }
        delay(200)
        assertHeld(v1)
        assertTrue("the sync must not fail: $uncaught", uncaught.isEmpty())

        connection(false)
        connection(true)
        awaitHeldVersion("v2")
        assertHeld(v2)
        assertTrue("the sync must not fail: $uncaught", uncaught.isEmpty())
    }

    private suspend fun awaitHeldVersion(version: String) {
        withTimeout(5_000) { dao.versionUpdates().first { it == version } }
    }

    private suspend fun assertHeld(expected: DictionariesDto) {
        assertEquals(expected.version, dao.version())
        assertEquals(
            expected.ingredients.map { IngredientEntryEntity(it.id, it.labelRu, it.labelEn, it.parentId) },
            dao.ingredients(),
        )
        assertEquals(expected.tags.map { TagEntity(it.id, it.labelRu, it.labelEn) }, dao.tags())
    }
}
