package app.recipefork.app.data

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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

        val seen = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { AndroidConnectivity(context).online.take(3).toList() }
        }
        withTimeout(5_000) { while (shadow.networkCallbacks.isEmpty()) delay(10) }
        val network = ShadowNetwork.newInstance(1)
        shadow.networkCallbacks.forEach { it.onLost(network) }
        shadow.networkCallbacks.forEach { it.onAvailable(network) }

        assertEquals(listOf(true, false, true), seen.await())
    }
}
