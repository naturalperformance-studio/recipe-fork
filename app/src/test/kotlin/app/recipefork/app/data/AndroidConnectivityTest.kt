package app.recipefork.app.data

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

@RunWith(AndroidJUnit4::class)
class AndroidConnectivityTest {
    @Test
    fun `a connection that is lost and returns is observed as offline, then online`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadow = shadowOf(context.getSystemService(ConnectivityManager::class.java))

        // Only the callback this flow registers is told: one registered by anything else is no sign that it is. The
        // flow is collected on this thread, so the shadow's callbacks are never read while another thread adds one.
        val others = shadow.networkCallbacks.toSet()
        val seen = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { AndroidConnectivity(context).online.take(3).toList() }
        }
        val own = withTimeout(5_000) {
            var registered = shadow.networkCallbacks - others
            while (registered.isEmpty()) {
                delay(10)
                registered = shadow.networkCallbacks - others
            }
            registered.single()
        }
        val network = ShadowNetwork.newInstance(1)
        own.onLost(network)
        own.onAvailable(network)

        assertEquals(listOf(true, false, true), seen.await())
    }
}
