package app.recipefork.app.settings

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.data.PreferencesThemeSetting
import app.recipefork.app.data.ThemeChoice
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.appColorScheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Scenarios 3 and 4 of the increment: the settings offer the theme — light, dark or as on the device, the device's at
 * first — beneath the language; a choice applies at once, the Activity left as it is, and is kept across restarts;
 * the window's background and the system bars' icons match the scheme chosen from the window's first frame.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2000dp")
class ThemeChoiceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
    private val setting get() = app.container.themeSetting
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun close() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun activity(): Activity {
        var current: Activity? = null
        scenario!!.onActivity { current = it }
        return current!!
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun openSettings() {
        if (exists(SettingsTags.SCREEN)) return
        await(LibraryTags.SETTINGS)
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        await(SettingsTags.THEME)
    }

    private fun openThemes() {
        openSettings()
        compose.onNodeWithTag(SettingsTags.THEME).performScrollTo().performClick()
        await(SettingsTags.THEME_DIALOG)
    }

    private fun choose(choice: ThemeChoice) {
        openThemes()
        compose.onNodeWithTag(SettingsTags.theme(choice)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(SettingsTags.THEME_DIALOG) }
    }

    private fun shownChoice(): String = compose.textsOf(SettingsTags.value(SettingsTags.THEME)).single()

    private fun selected(choice: ThemeChoice): Boolean =
        compose.onNodeWithTag(SettingsTags.theme(choice)).fetchSemanticsNode().config[SemanticsProperties.Selected]

    /** True while [activity]'s window shows the dark scheme: its background and light icons on both bars. */
    private fun showsDark(activity: Activity): Boolean {
        val window = activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        val background = (window.decorView.background as ColorDrawable).color
        val dark = !bars.isAppearanceLightStatusBars && !bars.isAppearanceLightNavigationBars
        val light = bars.isAppearanceLightStatusBars && bars.isAppearanceLightNavigationBars
        val expected = appColorScheme(activity.resources, dark = dark).surface.toArgb()
        assertTrue("both bars' icons agree", dark || light)
        assertEquals("the window's background is the scheme's surface", expected, background)
        return dark
    }

    private fun showsDarkNow(): Boolean {
        var dark = false
        scenario!!.onActivity { dark = showsDark(it) }
        return dark
    }

    // The theme item stands beneath the language, showing the device's at first; its dialog offers three choices.
    @Test
    fun theSettingsOfferTheThemeBeneathTheLanguageTheDevicesAtFirst() {
        launch()
        openSettings()
        assertEquals(listOf("Theme", "As on the device"), compose.textsOf(SettingsTags.THEME))
        val tops = listOf(SettingsTags.LANGUAGE, SettingsTags.THEME, SettingsTags.USUAL)
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        openThemes()
        assertEquals(listOf("Light"), compose.textsOf(SettingsTags.theme(ThemeChoice.LIGHT)))
        assertEquals(listOf("Dark"), compose.textsOf(SettingsTags.theme(ThemeChoice.DARK)))
        assertEquals(listOf("As on the device"), compose.textsOf(SettingsTags.theme(ThemeChoice.DEVICE)))
        assertEquals(listOf(false, false, true), ThemeChoice.entries.map(::selected))
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 4)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 4)
        // Dismissed, the dialog changes nothing.
        compose.onNodeWithTag(SettingsTags.THEME_CANCEL).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(SettingsTags.THEME_DIALOG) }
        assertEquals("As on the device", shownChoice())
        assertEquals(ThemeChoice.DEVICE, setting.choice.value)
    }

    // A choice applies at once: the same Activity shows the other scheme, its bars' icons following.
    @Test
    fun aChoiceAppliesAtOnceWithoutRecreatingTheActivity() {
        launch()
        val before = activity()
        assertFalse(showsDarkNow())
        choose(ThemeChoice.DARK)
        compose.waitUntil(PATIENCE_MS) { showsDarkNow() }
        assertEquals("Dark", shownChoice())
        assertSame(before, activity())
        choose(ThemeChoice.LIGHT)
        compose.waitUntil(PATIENCE_MS) { !showsDarkNow() }
        assertEquals("Light", shownChoice())
        assertSame(before, activity())
    }

    // The choice outlives the application: started again, it shows the scheme chosen, and the settings name it.
    @Test
    fun theChoiceIsKeptAcrossRestarts() {
        launch()
        choose(ThemeChoice.DARK)
        scenario!!.close()
        assertEquals(ThemeChoice.DARK, PreferencesThemeSetting(app).choice.value)
        launch()
        assertTrue(showsDarkNow())
        openSettings()
        assertEquals("Dark", shownChoice())
    }

    @Test
    fun nothingKeptReadsAsTheDevicesAndSoDoesAnythingUnknown() {
        assertEquals(ThemeChoice.DEVICE, PreferencesThemeSetting(app).choice.value)
        app.getSharedPreferences(PreferencesThemeSetting.FILE, 0).edit().putInt(PreferencesThemeSetting.KEY, 3).commit()
        assertEquals(ThemeChoice.DEVICE, PreferencesThemeSetting(app).choice.value)
    }

    /** [MainActivity] created, before anything is composed or drawn: its window as the first frame shows it. */
    private fun firstFrameDark(): Boolean {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            return showsDark(controller.get())
        } finally {
            controller.destroy()
        }
    }

    // The first frame already shows the scheme chosen, whatever the device's mode.
    @Test
    fun theFirstFrameIsDarkWhenDarkIsChosenOnALightDevice() {
        setting.set(ThemeChoice.DARK)
        assertTrue(firstFrameDark())
    }

    @Test
    @Config(qualifiers = "+night")
    fun theFirstFrameIsLightWhenLightIsChosenOnADarkDevice() {
        setting.set(ThemeChoice.LIGHT)
        assertFalse(firstFrameDark())
    }

    @Test
    @Config(qualifiers = "+night")
    fun theFirstFrameFollowsADarkDeviceAsOnTheDevice() {
        assertTrue(firstFrameDark())
    }

    @Test
    fun theFirstFrameFollowsALightDeviceAsOnTheDevice() {
        assertFalse(firstFrameDark())
    }

    // The window the system shows before the application runs follows the device's mode, in the theme's colours.
    @Test
    @Config(qualifiers = "+night")
    fun theStartingWindowIsDarkOnADarkDevice() = assertStartingWindow(dark = true)

    @Test
    fun theStartingWindowIsLightOnALightDevice() = assertStartingWindow(dark = false)

    private fun assertStartingWindow(dark: Boolean) {
        val theme = app.resources.newTheme().apply { applyStyle(app.applicationInfo.theme, true) }
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, value, true)
        assertEquals(appColorScheme(app.resources, dark).surface.toArgb(), value.data)
        val bars = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowLightStatusBar, android.R.attr.windowLightNavigationBar))
        try {
            assertEquals(!dark, bars.getBoolean(0, dark))
            assertEquals(!dark, bars.getBoolean(1, dark))
        } finally {
            bars.recycle()
        }
        val night = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        assertEquals(dark, night == Configuration.UI_MODE_NIGHT_YES)
    }
}
