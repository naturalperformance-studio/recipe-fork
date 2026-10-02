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
import androidx.lifecycle.viewModelScope
import app.recipefork.app.card.LocalStepToShow
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeFileIntents
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.announce
import app.recipefork.app.file.importSafely
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
                        AppNavHost(librarySession, container.recipes, container.mode, Modifier.safeDrawingPadding())
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

    /**
     * A recipe file opened from another application is imported in serverless mode (FLOW-064) and in standard mode
     * is not; a timer's notification leads to its recipe's card, at its step (FLOW-013 (4)), and stops its ringing.
     */
    private fun follow(intent: Intent?) {
        val container = (application as RecipeForkApplication).container
        RecipeFileIntents.fileOf(intent)?.let { uri ->
            openRecipeFile(applicationContext, container.mode, RecipeImport(container.recipes, filesDir), librarySession, uri)
            return
        }
        val step = TimerIntents.stepOf(intent) ?: return
        (application as RecipeForkApplication).container.timerRinging
            .forget(TimerKey(step.book, step.recipeId, step.stepPosition))
        librarySession.showStep(step.recipeId, step.stepPosition)
        TimerIntents.followed(intent!!)
    }

    companion object {
        const val ROOT_TAG = "root"

        /**
         * Follows a recipe file at [uri] handed to the application in [mode]: in [Mode.Serverless] it is imported, the
         * person told what became of it, and the marked recipes shown where it was added (FLOW-064 (1)); in
         * [Mode.Standard] nothing is imported, nor read (FLOW-064 (7)). Returns the import's work, or null.
         */
        fun openRecipeFile(context: android.content.Context, mode: Mode, files: RecipeImport, session: LibrarySession, uri: android.net.Uri) =
            if (mode != Mode.Serverless) {
                null
            } else {
                session.viewModelScope.launch {
                    val outcome = files.importSafely(context, uri)
                    announce(context, outcome)
                    if (outcome is ImportOutcome.Added) session.showImported()
                }
            }

        /**
         * The mode this release is built in, passed to the composition root. The only reader of the build's flag
         * (decisions/20260928-serverless-mode-is-a-build-flag.md).
         */
        val MODE: Mode = modeOf(BuildConfig.SERVERLESS)

        fun modeOf(serverless: Boolean): Mode = if (serverless) Mode.Serverless else Mode.Standard
    }
}
