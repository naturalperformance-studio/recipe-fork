package app.recipefork.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @Test
    fun theDeviceCommandReachesARealDevice() {
        assertTrue(Build.VERSION.SDK_INT >= 29)
    }
}
