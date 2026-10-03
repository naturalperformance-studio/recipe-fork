package app.recipefork.app.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.hours

/**
 * As the application starts, the shots a process ended between the camera and the crop left a day ago or more are
 * cleared — a younger one may still be on its way back from the camera — and so is what the camera of earlier versions
 * left in its own directory.
 */
@RunWith(AndroidJUnit4::class)
class CaptureLeftoversTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = System.currentTimeMillis()

    @Test
    fun shotsADayOldAreClearedAndYoungerOnesKept() {
        val directory = Captures.directory(context).apply { mkdirs() }
        File(directory, "old.jpg").apply { writeText("x"); setLastModified(now - 25.hours.inWholeMilliseconds) }
        File(directory, "young.jpg").apply { writeText("x"); setLastModified(now - 23.hours.inWholeMilliseconds) }

        Captures.clearLeftovers(context, now)

        assertEquals(listOf("young.jpg"), directory.list()!!.sorted())
    }

    @Test
    fun whatTheEarlierCameraLeftIsCleared() {
        val legacy = Captures.legacyDirectory(context).apply { mkdirs() }
        File(legacy, "young.jpg").writeText("x")

        PhotoSources.clearLeftovers(context)

        assertFalse(legacy.exists())
    }

    @Test
    fun aMissingDirectoryIsNoTrouble() {
        Captures.directory(context).deleteRecursively()

        PhotoSources.clearLeftovers(context)

        assertFalse(Captures.directory(context).exists())
    }
}
