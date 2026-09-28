package app.recipefork.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.ui.AppNavHost

/** The single Activity; its task is the reader's session (FLOW-058). */
class MainActivity : ComponentActivity() {
    private val librarySession: LibrarySession by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RecipeForkApplication).container
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                    AppNavHost(librarySession, container.recipes, Modifier.safeDrawingPadding())
                }
            }
        }
    }

    companion object {
        const val ROOT_TAG = "root"
    }
}
