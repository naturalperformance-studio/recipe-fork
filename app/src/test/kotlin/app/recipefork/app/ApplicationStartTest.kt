package app.recipefork.app

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.media.Captures
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.hours

/**
 * The application, as it starts, sweeps each book's media directory as the book is opened (MEDIA-012) and starts the
 * dictionaries' sync; before it is up — before any screen can take a shot or a clip — it has cleared what a process
 * ended mid-way left: the shots a day old, the earlier camera's directory and the lasting readings of picked clips.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationStartTest {
    @Test
    @Config(application = RecipeForkApplication::class)
    fun theApplicationStartsTheMediaSweepAndItsBackgroundWork() {
        val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
        try {
            assertTrue(application.mediaSweep!!.isActive)
            assertEquals(application.container.mode == Mode.Standard, application.backgroundWork.any { it.isActive })
        } finally {
            // The sync reaches for a server no test has.
            application.scope.cancel()
        }
    }

    // The tests' own application leaves their files alone, and reaches for no server.
    @Test
    fun theTestsApplicationDoesNotSweepNorStartBackgroundWork() {
        val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
        assertTrue(application is TestRecipeForkApplication)
        assertNull(application.mediaSweep)
        assertEquals(emptyList<Any>(), application.backgroundWork)
    }

    @Test
    @Config(application = LeftoversApplication::class)
    fun whatAnEndedProcessLeftIsClearedBeforeTheApplicationIsUp() {
        val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
        // Read at once, waiting for nothing: the clearing is done by the time the application has started.
        assertEquals(listOf("young.jpg"), Captures.directory(application).list()!!.sorted())
        assertFalse(Captures.legacyDirectory(application).exists())
        assertTrue(application.contentResolver.persistedUriPermissions.isEmpty())
    }
}

/** The tests' application, started over what a process ended mid-way left: an old shot, a young one, and the rest. */
class LeftoversApplication : TestRecipeForkApplication() {
    override fun onCreate() {
        val now = System.currentTimeMillis()
        val directory = Captures.directory(this).apply { mkdirs() }
        File(directory, "old.jpg").apply { writeText("x"); setLastModified(now - 25.hours.inWholeMilliseconds) }
        File(directory, "young.jpg").writeText("x")
        File(Captures.legacyDirectory(this).apply { mkdirs() }, "shot.jpg").writeText("x")
        contentResolver.takePersistableUriPermission(Uri.parse("content://media/external/video/media/7"), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        super.onCreate()
    }
}
