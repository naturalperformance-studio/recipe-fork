package app.recipefork.app

import android.app.Application
import app.recipefork.app.timer.TimerChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RecipeForkApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Work that lives as long as the process does. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The channels exist before any timer is started, so that the timers' one can be read and turned off alone.
        TimerChannels.create(this)
        container.dictionarySync.start(scope)
    }
}
