package app.recipefork.app.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The application's own camera keeps its picture in memory; what the system camera of earlier versions left in its
 * directory is cleared as the application starts.
 */
@RunWith(AndroidJUnit4::class)
class CameraLeftoversTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun whatTheSystemCameraLeftIsCleared() {
        val directory = PhotoSources.cameraDirectory(context).apply { mkdirs() }
        File(directory, "old.jpg").writeText("x")
        File(directory, "young.jpg").writeText("x")

        PhotoSources.clearLeftovers(context)

        assertEquals(false, directory.exists())
    }
}
