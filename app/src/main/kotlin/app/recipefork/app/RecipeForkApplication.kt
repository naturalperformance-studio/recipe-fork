package app.recipefork.app

import android.app.Application
import android.content.res.Configuration
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.data.UnitSystemSetting
import app.recipefork.app.media.ClipSources
import app.recipefork.app.media.PhotoSources
import app.recipefork.app.timer.TimerChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

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

    /**
     * Whether the container's background work — the dictionaries' sync with the product's server — is started; a
     * test's application, which has no server to reach, starts none, so that no test waits on a connection that never
     * comes while every test after it runs.
     */
    protected open val startsBackgroundWork: Boolean = true

    /** The mode the composition root is built in: the build's; a test's application may be built in the other. */
    protected open val mode: Mode get() = MainActivity.MODE

    /**
     * The region the system of units follows until the reader chooses one: the device's; a test's application pins
     * its own, so that no test reads its amounts by the region the test machine happens to have.
     */
    protected open val unitRegion: () -> String = UnitSystemSetting::deviceRegion

    /** The sweep of the books' media directories, once started. */
    var mediaSweep: Job? = null
        private set

    /** The container's background work, as started. */
    var backgroundWork: List<Job> = emptyList()
        private set

    override fun onCreate() {
        super.onCreate()
        startLanguage()
        // Before any screen exists: a shot or a reading of a clip taken as the process starts — a camera's or a
        // picker's answer delivered to a process started anew for it — is never cleared or let go of as it is taken.
        PhotoSources.clearLeftovers(this)
        ClipSources.releaseLeftoverReadings(this)
        container = AppContainer(this, mode, unitRegion = unitRegion)
        if (startsBackgroundWork) backgroundWork = container.start(scope)
        if (sweepsMedia) mediaSweep = container.sweepMedia(scope)
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
