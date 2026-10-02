package app.recipefork.app.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** What a camera left behind is cleared as the application starts; a photograph still on its way back is kept. */
@RunWith(AndroidJUnit4::class)
class CameraLeftoversTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun anOldCameraFileIsClearedAndAYoungOneKept() {
        val directory = PhotoSources.cameraDirectory(context).apply { mkdirs() }
        val now = 10L * PhotoSources.LEFTOVER_AGE_MS
        val old = File(directory, "old.jpg").apply { writeText("x"); setLastModified(now - PhotoSources.LEFTOVER_AGE_MS - 1) }
        val young = File(directory, "young.jpg").apply { writeText("x"); setLastModified(now - 1_000) }

        PhotoSources.clearLeftovers(context, now)

        assertEquals(listOf(young.name), directory.list()!!.toList())
        assertEquals(false, old.exists())
    }
}
