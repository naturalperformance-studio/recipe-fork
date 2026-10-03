package app.recipefork.app

import android.app.Application
import android.content.res.Configuration
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.media.ClipSources
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
        startLanguage()
        container = AppContainer(this, MainActivity.MODE)
        container.start(scope)
        if (sweepsMedia) mediaSweep = container.sweepMedia(scope)
        scope.launch {
            PhotoSources.clearLeftovers(this@RecipeForkApplication)
            ClipSources.releaseLeftoverReadings(this@RecipeForkApplication)
        }
    }

    /**
     * What a starting process does for the interface language (L10N-002): a choice an earlier Android kept moves to the
     * platform, the application's resources take the choice, and the timers' channels are named in it. The channels
     * exist before any timer is started, so that the timers' one can be read and turned off alone.
     */
    internal fun startLanguage() {
        AppLanguage.moveIntoPlatform(this)
        AppLanguage.applyToApplication(this)
        TimerChannels.create(this)
    }

    /**
     * A change of the configuration — the device's language, or the application's own in the system's settings —
     * resets the application's resources; the reader's language is kept and the timers' channels renamed in it.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.applyToApplication(this)
        TimerChannels.create(this)
    }
}
