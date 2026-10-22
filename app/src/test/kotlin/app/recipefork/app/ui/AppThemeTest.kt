package app.recipefork.app.ui

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
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
import androidx.compose.ui.unit.dp
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
import java.io.File

/**
 * The theme is one resource file (ADR 20261004): every colour role of both schemes, the corners and the main type sizes
 * come from `app_theme.xml`; both schemes are the redesign's paper and ink, never the wallpaper's colours; a changed
 * value reaches the screen; the system bars' icons follow the scheme.
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

    // The redesign's paper and ink (docs/brand/identity/port-1-rodoslovnaya.html): the file holds its 48 light and 48
    // dark roles, in its order, as #AARRGGBB, and each scheme the app builds is exactly those colours.
    @Test
    fun bothSchemesAreThePaperAndInkOfTheRedesign() {
        val written = Regex("""<color name="theme_(light|dark)_(\w+)">(#[0-9A-F]{8})</color>""")
            .findAll(File("src/main/res/values/app_theme.xml").readText())
            .map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
        fun expected(scheme: String, values: Map<String, Long>) =
            values.map { (role, value) -> Triple(scheme, role, "#%08X".format(value)) }
        assertEquals(expected("light", LIGHT) + expected("dark", DARK), written)
        assertEquals(LIGHT.mapValues { Color(it.value) }, roles(appColorScheme(resources, dark = false)))
        assertEquals(DARK.mapValues { Color(it.value) }, roles(appColorScheme(resources, dark = true)))
    }

    // No dynamic colour: the scheme on the screen is the file's, in light and in dark, whatever the wallpaper; nothing
    // in the app builds a scheme from it.
    @Test
    fun theSchemeOnTheScreenIsTheFilesNeverTheWallpapers() {
        val dark = mutableStateOf(false)
        var shown: ColorScheme? = null
        compose.setContent { AppTheme(dark = dark.value) { shown = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        assertEquals(LIGHT.mapValues { Color(it.value) }, roles(shown!!))
        dark.value = true
        compose.waitForIdle()
        assertEquals(DARK.mapValues { Color(it.value) }, roles(shown!!))
        val dynamic = Regex("""\bdynamic(Light|Dark)ColorScheme\b|\bDynamicColors\b""")
        val using = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { dynamic.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals(emptyList<String>(), using)
    }

    @Test
    fun theCornersAndTypeSizesComeFromTheFileAndAreMaterialsDefaultsButTheMediumCorner() {
        assertEquals(Shapes(medium = RoundedCornerShape(10.dp)), appShapes(resources))
        assertEquals(Typography(), appTypography(resources))
        var shown: Triple<ColorScheme, Shapes, Typography>? = null
        compose.setContent { AppTheme(dark = true) { shown = Triple(MaterialTheme.colorScheme, MaterialTheme.shapes, MaterialTheme.typography) } }
        compose.waitUntil { shown != null }
        assertEquals(roles(appColorScheme(resources, dark = true)), roles(shown!!.first))
        assertEquals(appShapes(resources), shown!!.second)
        assertEquals(appTypography(resources), shown!!.third)
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

        /** The design source's colour roles, light and dark, in the file's order. */
        val LIGHT = mapOf(
            "primary" to 0xFF4C5F23, "onPrimary" to 0xFFFFFFFF, "primaryContainer" to 0xFFDCE5BE,
            "onPrimaryContainer" to 0xFF1B2408, "inversePrimary" to 0xFFB7C98A, "secondary" to 0xFF6A5B48,
            "onSecondary" to 0xFFFFFFFF, "secondaryContainer" to 0xFFEDE0CC, "onSecondaryContainer" to 0xFF2A2014,
            "tertiary" to 0xFF8E3B46, "onTertiary" to 0xFFFFFFFF, "tertiaryContainer" to 0xFFF6D9DA,
            "onTertiaryContainer" to 0xFF3A0A12, "background" to 0xFFF6F0E4, "onBackground" to 0xFF2B241E,
            "surface" to 0xFFF6F0E4, "onSurface" to 0xFF2B241E, "surfaceVariant" to 0xFFE9E0CF,
            "onSurfaceVariant" to 0xFF5E564C, "surfaceTint" to 0xFF4C5F23, "inverseSurface" to 0xFF332C25,
            "inverseOnSurface" to 0xFFF4ECDF, "error" to 0xFF9F2A20, "onError" to 0xFFFFFFFF,
            "errorContainer" to 0xFFFADAD4, "onErrorContainer" to 0xFF410000, "outline" to 0xFF857B6E,
            "outlineVariant" to 0xFFD3C8B6, "scrim" to 0xFF000000, "surfaceBright" to 0xFFF6F0E4,
            "surfaceDim" to 0xFFDDD3C1, "surfaceContainer" to 0xFFEEE6D7, "surfaceContainerHigh" to 0xFFE9E0CF,
            "surfaceContainerHighest" to 0xFFE3D9C6, "surfaceContainerLow" to 0xFFF2EBDE, "surfaceContainerLowest" to 0xFFFCF8F0,
            "primaryFixed" to 0xFFDCE5BE, "primaryFixedDim" to 0xFFB7C98A, "onPrimaryFixed" to 0xFF1B2408,
            "onPrimaryFixedVariant" to 0xFF3A4818, "secondaryFixed" to 0xFFEDE0CC, "secondaryFixedDim" to 0xFFD9C3A5,
            "onSecondaryFixed" to 0xFF2A2014, "onSecondaryFixedVariant" to 0xFF524433, "tertiaryFixed" to 0xFFF6D9DA,
            "tertiaryFixedDim" to 0xFFE6A3A8, "onTertiaryFixed" to 0xFF3A0A12, "onTertiaryFixedVariant" to 0xFF72303A,
        )
        val DARK = mapOf(
            "primary" to 0xFFB7C98A, "onPrimary" to 0xFF24300C, "primaryContainer" to 0xFF3A4818,
            "onPrimaryContainer" to 0xFFD8E6AE, "inversePrimary" to 0xFF4C5F23, "secondary" to 0xFFD9C3A5,
            "onSecondary" to 0xFF3A2E1E, "secondaryContainer" to 0xFF524433, "onSecondaryContainer" to 0xFFF2E0C6,
            "tertiary" to 0xFFE6A3A8, "onTertiary" to 0xFF561D26, "tertiaryContainer" to 0xFF72303A,
            "onTertiaryContainer" to 0xFFFFD9DB, "background" to 0xFF1D1915, "onBackground" to 0xFFECE3D2,
            "surface" to 0xFF1D1915, "onSurface" to 0xFFECE3D2, "surfaceVariant" to 0xFF4A4238,
            "onSurfaceVariant" to 0xFFC2B7A6, "surfaceTint" to 0xFFB7C98A, "inverseSurface" to 0xFFECE3D2,
            "inverseOnSurface" to 0xFF332C25, "error" to 0xFFFFB4A9, "onError" to 0xFF690004,
            "errorContainer" to 0xFF8C1D14, "onErrorContainer" to 0xFFFFDAD4, "outline" to 0xFF8D8274,
            "outlineVariant" to 0xFF4A4238, "scrim" to 0xFF000000, "surfaceBright" to 0xFF443C33,
            "surfaceDim" to 0xFF1D1915, "surfaceContainer" to 0xFF28231D, "surfaceContainerHigh" to 0xFF322C25,
            "surfaceContainerHighest" to 0xFF3D362E, "surfaceContainerLow" to 0xFF24201B, "surfaceContainerLowest" to 0xFF171410,
            "primaryFixed" to 0xFFDCE5BE, "primaryFixedDim" to 0xFFB7C98A, "onPrimaryFixed" to 0xFF1B2408,
            "onPrimaryFixedVariant" to 0xFF3A4818, "secondaryFixed" to 0xFFEDE0CC, "secondaryFixedDim" to 0xFFD9C3A5,
            "onSecondaryFixed" to 0xFF2A2014, "onSecondaryFixedVariant" to 0xFF524433, "tertiaryFixed" to 0xFFF6D9DA,
            "tertiaryFixedDim" to 0xFFE6A3A8, "onTertiaryFixed" to 0xFF3A0A12, "onTertiaryFixedVariant" to 0xFF72303A,
        )
    }
}
