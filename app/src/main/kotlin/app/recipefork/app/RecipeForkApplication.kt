package app.recipefork.app

import android.app.Application
import android.content.res.Configuration
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.media.PhotoSources
import app.recipefork.app.timer.TimerChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class RecipeForkApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Work that lives as long as the process does. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Whether the books' media directories are swept as they are opened; a test's application, which shares its
     * files with the tests it runs, does not sweep them from under those tests.
     */
    protected open val sweepsMedia: Boolean = true

    /** The sweep of the books' media directories, once started. */
    var mediaSweep: Job? = null
        private set

    override fun onCreate() {
        super.onCreate()
        AppLanguage.applyToApplication(this)
        container = AppContainer(this, MainActivity.MODE)
        // The channels exist before any timer is started, so that the timers' one can be read and turned off alone.
        TimerChannels.create(this)
        container.start(scope)
        if (sweepsMedia) mediaSweep = container.sweepMedia(scope)
        scope.launch { PhotoSources.clearLeftovers(this@RecipeForkApplication) }
    }

    /** A change of the device's configuration resets the application's resources to it; the reader's language is kept. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.applyToApplication(this)
    }
}
