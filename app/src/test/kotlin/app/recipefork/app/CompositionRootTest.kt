package app.recipefork.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.data.Network
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FLOW-061 (2), decisions/20260928-serverless-mode-is-a-build-flag.md: the serverless composition root constructs no
 * HTTP client and starts no dictionary sync, so it makes no request to the product's server; the standard one does both.
 */
@RunWith(AndroidJUnit4::class)
class CompositionRootTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var clientsMade = 0
    private val requests = mutableListOf<String>()

    private fun container(mode: Mode) = AppContainer(context, mode) {
        clientsMade++
        Network.client(MockEngine { request -> requests += request.url.toString(); respondError(HttpStatusCode.NotFound) }, "http://server.test")
    }

    @After
    fun close() = scope.cancel()

    @Test
    fun theServerlessCompositionRootConstructsNoHttpClientAndStartsNoSync() {
        val container = container(Mode.Serverless)
        assertEquals(0, clientsMade)
        assertNull(container.httpClient)
        assertNull(container.dictionarySync)
        assertEquals(emptyList<Any>(), container.start(scope))
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun theStandardCompositionRootConstructsTheClientAndStartsTheSync() {
        val container = container(Mode.Standard)
        assertEquals(1, clientsMade)
        assertNotNull(container.httpClient)
        val started = container.start(scope)
        assertEquals(1, started.size)
        assertTrue(started.single().isActive)
    }
}
