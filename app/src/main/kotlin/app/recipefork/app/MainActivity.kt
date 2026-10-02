package app.recipefork.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.recipefork.app.card.LocalStepToShow
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.timer.TimerIntents
import app.recipefork.app.timer.TimerKey
import app.recipefork.app.timer.TimerService
import app.recipefork.app.ui.AppNavHost
import kotlinx.coroutines.launch

/** The single Activity; its task is the reader's session (FLOW-058). */
class MainActivity : ComponentActivity() {
    private val librarySession: LibrarySession by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RecipeForkApplication).container
        // A recreated Activity has followed its intent already.
        if (savedInstanceState == null) follow(intent)
        setContent {
            CompositionLocalProvider(LocalStepToShow provides librarySession.stepToShow) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                        AppNavHost(librarySession, container.recipes, Modifier.safeDrawingPadding())
                    }
                }
            }
        }
        // Timers held while the service that announces them was gone are taken up again, now that it may start.
        container.timerWork.launch {
            if (container.timers.unannounced().isNotEmpty()) TimerService.start(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        follow(intent)
    }

    /** A timer's notification leads to its recipe's card, at its step (FLOW-013 (4)), and stops its ringing. */
    private fun follow(intent: Intent?) {
        val step = TimerIntents.stepOf(intent) ?: return
        (application as RecipeForkApplication).container.timerRinging
            .forget(TimerKey(step.book, step.recipeId, step.stepPosition))
        librarySession.showStep(step.recipeId, step.stepPosition)
        TimerIntents.followed(intent!!)
    }

    companion object {
        const val ROOT_TAG = "root"
    }
}
