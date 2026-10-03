package app.recipefork.app.media

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.media.crop.CropTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A screen holding one place's photo sources as its own content, so that it comes back whole when recreated. */
class CaptureHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val sources = rememberPhotoSources(
                    key = "recreated",
                    onPhoto = { offer ->
                        offers += offer.open().use { it.readBytes() }
                        offer.release()
                    },
                    onUnavailable = {},
                )
                Text("Take a photo", Modifier.clickable { sources.takePhoto() }.testTag(TAKE))
            }
        }
    }

    companion object {
        const val TAKE = "host-take"
        val offers = mutableListOf<ByteArray>()
    }
}

/**
 * The activity recreated while the phone's camera application is in front — the phone turned, or the activity
 * reclaimed — still takes the shot the camera comes back with: the file it was written into is remembered across the
 * recreation, the crop opens on it, and Done hands it over and deletes it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraRecreationTest {
    /** The host is the test's own, in no manifest: the package manager is told of it before it is launched. */
    @get:Rule(order = 0)
    val registered = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, CaptureHostActivity::class.java))
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<CaptureHostActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        FileProviderCache.clear()
        CaptureHostActivity.offers.clear()
    }

    @Test
    fun theShotSurvivesTheActivityBeingRecreatedWhileTheCameraIsInFront() {
        compose.waitUntilExactlyOneExists(hasTestTag(CaptureHostActivity.TAKE), PATIENCE_MS)
        compose.onNodeWithTag(CaptureHostActivity.TAKE).performClick()
        val shadow = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { TestCamera.launched(shadow) }
        val started = shadow.nextStartedActivityForResult
        val intent = started.intent
        TestCamera.write(context, intent, TestPhotos.jpeg(File(context.filesDir, "shot.jpg"), 800, 600).readBytes())

        compose.activityRule.scenario.recreate()
        compose.waitUntilExactlyOneExists(hasTestTag(CaptureHostActivity.TAKE), PATIENCE_MS)
        compose.runOnUiThread {
            compose.activity.activityResultRegistry.dispatchResult(started.requestCode, Activity.RESULT_OK, null)
        }

        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.onNodeWithTag(CropTags.DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { CaptureHostActivity.offers.size == 1 }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CropTags.SCREEN)).fetchSemanticsNodes().isEmpty() }
        assertEquals(1, CaptureHostActivity.offers.size)
        assertTrue(TestCamera.left(context).isEmpty())
    }

    private companion object {
        const val PATIENCE_MS = 60_000L
    }
}
