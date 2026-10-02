package app.recipefork.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The application, as it starts, sweeps each book's media directory as the book is opened (MEDIA-012). */
@RunWith(AndroidJUnit4::class)
class ApplicationStartTest {
    @Test
    @Config(application = RecipeForkApplication::class)
    fun theApplicationStartsTheMediaSweep() {
        val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
        assertTrue(application.mediaSweep!!.isActive)
    }

    // The tests' own application leaves their files alone.
    @Test
    fun theTestsApplicationDoesNotSweep() {
        val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
        assertTrue(application is TestRecipeForkApplication)
        assertNull(application.mediaSweep)
    }
}
