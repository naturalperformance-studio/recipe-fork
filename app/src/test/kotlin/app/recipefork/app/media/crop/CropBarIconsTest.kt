package app.recipefork.app.media.crop

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.ui.AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.ByteArrayInputStream

/**
 * Over the application's own theme, whose bars carry dark icons for its light surface, the crop's window carries light
 * ones on its black ground, so that they stay visible there — while the application's window keeps its dark ones.
 */
@RunWith(AndroidJUnit4::class)
class CropBarIconsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun assertLightIconsOnTheCrop() {
        val photo = PhotoToCrop(open = { ByteArrayInputStream(ByteArray(0)) }, orientation = null, retake = false)
        compose.runOnUiThread {
            compose.activity.setContent { AppTheme { CropScreen(photo, onDone = {}, onRetake = {}, onCancel = {}, onUnreadable = {}) } }
        }
        compose.onNodeWithTag(CropTags.SCREEN).assertExists()
        compose.runOnUiThread {
            val crop = ShadowDialog.getLatestDialog().window!!
            WindowCompat.getInsetsController(crop, crop.decorView).run {
                assertFalse("crop status bar icons light", isAppearanceLightStatusBars)
                assertFalse("crop navigation bar icons light", isAppearanceLightNavigationBars)
            }
            val app = compose.activity.window
            WindowCompat.getInsetsController(app, app.decorView).run {
                assertTrue("application status bar icons dark", isAppearanceLightStatusBars)
                assertTrue("application navigation bar icons dark", isAppearanceLightNavigationBars)
            }
        }
    }

    @Test
    @Config(sdk = [29])
    fun theCropsBarIconsAreLightOnTheOldestRelease() = assertLightIconsOnTheCrop()

    @Test
    @Config(sdk = [36])
    fun theCropsBarIconsAreLightWhereEdgeToEdgeIsEnforced() = assertLightIconsOnTheCrop()
}
