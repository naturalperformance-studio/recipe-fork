package app.recipefork.app.ui

import android.content.ContextWrapper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
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
 * system bars' icons follow the scheme.
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

    private companion object {
        const val BUTTON = "button"
    }
}
