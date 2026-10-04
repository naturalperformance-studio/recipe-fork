package app.recipefork.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recipefork.app.card.LocalStepToShow
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.file.RecipeFileIntents
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.library.importFile
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.timer.TimerIntents
import app.recipefork.app.timer.TimerKey
import app.recipefork.app.timer.TimerService
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.showSystemBarsFor
import kotlinx.coroutines.launch
import java.util.Locale

/** The single Activity; its task is the reader's session (FLOW-058). */
class MainActivity : ComponentActivity() {
    private val librarySession: LibrarySession by viewModels {
        viewModelFactory {
            initializer {
                LibrarySession(createSavedStateHandle(), (application as RecipeForkApplication).container.lastBookView)
            }
        }
    }

    /** The interface language the reader chose is laid over the device's before anything reads it (L10N-002). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val override = AppLanguage.overrideFor(newBase)
        applied = override?.locales?.get(0)
        override?.let(::applyOverrideConfiguration)
    }

    /** The language laid over the device's as this Activity was created, or null where the device's stood. */
    private var applied: Locale? = null

    /**
     * The Activity handles a change of locale itself (the manifest's configChanges), so a choice made behind its back —
     * in the system's per-app language settings — is followed here: it shows itself again in the language now chosen.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (AppLanguage.chosen(this).locale != applied) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Drawn edge to edge, each screen keeping its own content clear of the system bars, whose icons follow the
        // scheme AppTheme shows — light whatever the system's mode, until the reader can choose (AppTheme follows a change).
        showSystemBarsFor(dark = false)
        val container = (application as RecipeForkApplication).container
        // A recreated Activity has followed its intent already.
        if (savedInstanceState == null) follow(intent)
        setContent {
            CompositionLocalProvider(LocalStepToShow provides librarySession.stepToShow) {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                        AppNavHost(librarySession, container.recipes, container.mode)
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
                session.viewModelScope.launch { session.importFile(context, files, uri) }
            }

        /**
         * The mode this release is built in, passed to the composition root. The only reader of the build's flag
         * (decisions/20260928-serverless-mode-is-a-build-flag.md).
         */
        val MODE: Mode = modeOf(BuildConfig.SERVERLESS)

        fun modeOf(serverless: Boolean): Mode = if (serverless) Mode.Serverless else Mode.Standard
    }
}
