package app.recipefork.app.editor

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.backThroughTheCard
import app.recipefork.app.data.Network
import app.recipefork.app.dictionary.DictionaryRepository
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagOffer
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.InterfaceLanguage
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FLOW-001 (1) on a clean installation: a device that has never reached the server, with no connection, offers
 * in the tag editor exactly the entries a device offers once it has fetched the same dictionaries from the server.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CleanInstallTagsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val connected = TestDevice(context)
    private val clean = TestDevice(context)
    private val opened = mutableStateOf<RecipeId?>(null)

    /** What the production server serves: the dictionaries the application ships, as the release step took them. */
    private val served: DictionariesDto =
        DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText())

    @Before
    fun setUp() = listOf(book.idling, connected.idling, clean.idling).forEach(compose::registerIdlingResource)

    @After
    fun close() {
        listOf(book.idling, connected.idling, clean.idling).forEach(compose::unregisterIdlingResource)
        book.close()
        connected.close()
        clean.close()
    }

    private fun offeredTags(): List<String> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(EditorTags.OFFERED)) and hasClickAction()).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    /** What the tag search offers for [typed], once the dictionary is read. */
    private fun offeredFor(typed: String): List<String> {
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.TAG_SEARCH), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.TAG_SEARCH).performScrollTo().performTextReplacement(typed)
        compose.waitUntil(PATIENCE_MS) { offeredTags().isNotEmpty() }
        return offeredTags()
    }

    @Test
    fun aCleanInstallationWithNoConnectionOffersTheTagsAsTheyAreWithAConnection() {
        // The connected device has fetched the dictionaries from the server.
        runBlocking {
            val engine = MockEngine {
                respond(Json.encodeToString(DictionariesDto.serializer(), served), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val refresh = DictionaryRepository(Network.client(engine, "http://server.test"), connected.database.dictionaryDao()).refresh()
            assertEquals(DictionaryRepository.Refresh.Replaced, refresh)
        }
        assertFalse(Connection.isOffline(context))
        var device by mutableStateOf(connected)
        compose.setContent {
            MaterialTheme {
                key(device) { LibraryOrEditor(session, recipes, EditorEnvironment(active, device.catalog(context)), opened) }
            }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.onNodeWithTag(LibraryTags.FROM_SCRATCH).performClick()
        val withAConnection = offeredFor(QUERY)
        val id = requireNotNull(session.editing.value)
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id)), PATIENCE_MS)

        // A clean installation — no dictionaries held, never seeded — with no connection.
        assertNull(runBlocking { clean.database.dictionaryDao().version() })
        Connection.offline(context)
        assertTrue(Connection.isOffline(context))
        device = clean
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id)), PATIENCE_MS)
        // The same recipe, opened on it directly in the editor.
        compose.runOnIdle { opened.value = RecipeId(id) }
        assertEquals(withAConnection, offeredFor(QUERY))
        // What is offered is the shipped dictionary's, searched as the editor searches it.
        val shipped = served.tags.map { TagEntity(it.id, it.labelRu, it.labelEn) }
        assertEquals(TagOffer.of(QUERY, shipped, InterfaceLanguage.ENGLISH).map { it.labelEn }, withAConnection)
        assertEquals(served.version, runBlocking { clean.database.dictionaryDao().version() })
    }

    private companion object {
        /** A beginning many shipped tags share. */
        const val QUERY = "s"
    }
}
