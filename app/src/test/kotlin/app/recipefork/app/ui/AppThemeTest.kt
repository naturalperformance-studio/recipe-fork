package app.recipefork.app.ui

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.data.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The theme is one resource file (ADR 20261004): every colour role of both schemes, the corners and the main type sizes
 * come from `app_theme.xml`; the light scheme is the one the app showed before; a changed value reaches the screen; the
 * system bars' icons follow the scheme; each type role is set in its bundled family (T-297).
 */
@RunWith(AndroidJUnit4::class)
class AppThemeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val resources: Resources get() = compose.activity.resources

    /** Every colour role of a [ColorScheme], by name, read through its getters (each role is a packed colour). */
    private fun roles(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
            .associate { m ->
                val name = m.name.removePrefix("get").substringBefore('-').replaceFirstChar(Char::lowercaseChar)
                name to Color((m.invoke(scheme) as Long).toULong())
            }

    @Test
    fun everyColourRoleOfBothSchemesIsANamedResource() {
        for (dark in listOf(false, true)) {
            val scheme = roles(appColorScheme(resources, dark))
            assertTrue("Material 3's roles: ${scheme.keys}", scheme.size >= 48)
            for ((role, colour) in scheme) {
                val name = "theme_${if (dark) "dark" else "light"}_$role"
                val id = resources.getIdentifier(name, "color", compose.activity.packageName)
                assertNotEquals("$name is missing from app_theme.xml", 0, id)
                assertEquals(name, Color(resources.getColor(id, null)), colour)
            }
        }
    }

    @Test
    fun theLightSchemeIsTheColoursTheAppShowedBefore() {
        assertEquals(roles(lightColorScheme()), roles(appColorScheme(resources, dark = false)))
    }

    @Test
    fun theCornersAndTypeSizesComeFromTheFileAndAreMaterialsDefaults() {
        assertEquals(Shapes(), appShapes(resources))
        assertEquals(sizes(Typography()), sizes(appTypography(resources)))
        var shown: Triple<ColorScheme, Shapes, Typography>? = null
        compose.setContent { AppTheme(dark = true) { shown = Triple(MaterialTheme.colorScheme, MaterialTheme.shapes, MaterialTheme.typography) } }
        compose.waitUntil { shown != null }
        assertEquals(roles(appColorScheme(resources, dark = true)), roles(shown!!.first))
        assertEquals(appShapes(resources), shown!!.second)
        assertEquals(appTypography(resources), shown!!.third)
    }

    /** Every text style of a [Typography], by role. */
    private fun styles(typography: Typography): Map<String, TextStyle> = with(typography) {
        mapOf(
            "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
            "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
        )
    }

    /** The size and line height of every role: what the fonts may not change. */
    private fun sizes(typography: Typography) = styles(typography).mapValues { (_, style) -> style.fontSize to style.lineHeight }

    // T-297, the design's role table: what is read in Literata, what is pressed and counted in Golos Text, the labels
    // with tabular figures; the roles the app does not set keep Material's own.
    @Test
    fun eachRoleIsSetInItsFamilyAndWeight() {
        val expected = mapOf(
            "headlineSmall" to Triple(ReadingFamily, FontWeight.Normal, null),
            "titleLarge" to Triple(ReadingFamily, FontWeight.Medium, null),
            "titleMedium" to Triple(ReadingFamily, FontWeight.Medium, null),
            "titleSmall" to Triple(WorkingFamily, FontWeight.Medium, null),
            "bodyLarge" to Triple(ReadingFamily, FontWeight.Normal, null),
            "bodyMedium" to Triple(ReadingFamily, FontWeight.Normal, null),
            "bodySmall" to Triple(WorkingFamily, FontWeight.Normal, null),
            "labelLarge" to Triple(WorkingFamily, FontWeight.Medium, "tnum"),
            "labelMedium" to Triple(WorkingFamily, FontWeight.Medium, "tnum"),
            "labelSmall" to Triple(WorkingFamily, FontWeight.Medium, "tnum"),
        )
        val material = styles(Typography())
        styles(appTypography(resources)).forEach { (role, style) ->
            val (family, weight, features) = expected[role] ?: material.getValue(role).let { Triple(it.fontFamily, it.fontWeight, it.fontFeatureSettings) }
            assertEquals("$role's family", family, style.fontFamily)
            assertEquals("$role's weight", weight, style.fontWeight)
            assertEquals("$role's font features", features, style.fontFeatureSettings)
        }
        // The families are the bundled files, Regular and Medium each: never a downloadable font.
        fun files(family: FontFamily) = (family as FontListFontFamily).fonts.map { (it as ResourceFont).resId to it.weight }
        assertEquals(listOf(R.font.literata_regular to FontWeight.Normal, R.font.literata_medium to FontWeight.Medium), files(ReadingFamily))
        assertEquals(listOf(R.font.golos_text_regular to FontWeight.Normal, R.font.golos_text_medium to FontWeight.Medium), files(WorkingFamily))
    }

    @Test
    fun aChangedPrimaryInTheFileReachesAPrimaryButton() {
        val changed = Color(0xFF00796B)
        val base = compose.activity
        @Suppress("DEPRECATION")
        val edited = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun getColor(id: Int, theme: Theme?): Int =
                if (id == R.color.theme_light_primary) changed.toArgb() else super.getColor(id, theme)
        }
        val context = object : ContextWrapper(base) {
            override fun getResources(): Resources = edited
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                AppTheme { TextButton(onClick = {}, modifier = Modifier.testTag(BUTTON)) { Text("Go") } }
            }
        }
        compose.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasAnyAncestor(hasTestTag(BUTTON)) and hasText("Go"), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        assertEquals(changed, layouts.single().layoutInput.style.color)
    }

    @Test
    fun theSystemBarsIconsAreDarkOnTheLightSchemeAndLightOnTheDarkOne() {
        val dark = mutableStateOf(true)
        compose.setContent { AppTheme(dark = dark.value) {} }
        val window = compose.activity.window
        val bars = { WindowCompat.getInsetsController(window, window.decorView) }
        compose.waitForIdle()
        assertFalse("the status bar's icons light, on the dark scheme", bars().isAppearanceLightStatusBars)
        assertFalse("the navigation bar's icons light, on the dark scheme", bars().isAppearanceLightNavigationBars)
        dark.value = false
        compose.waitForIdle()
        assertTrue("the status bar's icons dark, on the light scheme", bars().isAppearanceLightStatusBars)
        assertTrue("the navigation bar's icons dark, on the light scheme", bars().isAppearanceLightNavigationBars)
        dark.value = true
        compose.waitForIdle()
        assertFalse("the status bar's icons light again, on the dark scheme", bars().isAppearanceLightStatusBars)
        assertFalse("the navigation bar's icons light again, on the dark scheme", bars().isAppearanceLightNavigationBars)
    }

    // "As on the device" follows the device's dark mode as it changes, its bars with it; light and dark stay put.
    @Test
    fun asOnTheDeviceFollowsTheSystemsDarkModeAsItChanges() {
        val night = mutableStateOf(false)
        val choice = mutableStateOf(ThemeChoice.DEVICE)
        var shown: Color? = null
        compose.setContent {
            val base = LocalConfiguration.current
            val configuration = Configuration(base).apply {
                uiMode = (base.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                AppTheme(choice.value) { shown = MaterialTheme.colorScheme.surface }
            }
        }
        val light = appColorScheme(resources, dark = false).surface
        val dark = appColorScheme(resources, dark = true).surface
        val window = compose.activity.window
        val bars = { WindowCompat.getInsetsController(window, window.decorView) }
        fun assertShown(expectDark: Boolean) {
            compose.waitForIdle()
            assertEquals(if (expectDark) dark else light, shown)
            assertEquals("the bars' icons follow", !expectDark, bars().isAppearanceLightStatusBars)
        }
        assertShown(expectDark = false)
        night.value = true
        assertShown(expectDark = true)
        night.value = false
        assertShown(expectDark = false)
        choice.value = ThemeChoice.DARK
        assertShown(expectDark = true)
        night.value = true
        night.value = false
        assertShown(expectDark = true)
        choice.value = ThemeChoice.LIGHT
        night.value = true
        assertShown(expectDark = false)
    }

    private companion object {
        const val BUTTON = "button"
    }
}
