package app.recipefork.app

import android.app.Application
import app.recipefork.app.media.PhotoSources
import app.recipefork.app.timer.TimerChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecipeForkApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Work that lives as long as the process does. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, MainActivity.MODE)
        // The channels exist before any timer is started, so that the timers' one can be read and turned off alone.
        TimerChannels.create(this)
        container.start(scope)
        container.sweepMedia(scope)
        scope.launch { PhotoSources.clearLeftovers(this@RecipeForkApplication) }
    }
}
