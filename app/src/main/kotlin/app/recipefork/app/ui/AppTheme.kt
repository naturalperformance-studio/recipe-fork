package app.recipefork.app.ui

import android.content.res.Resources
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DimenRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recipefork.app.R
import app.recipefork.app.data.ThemeChoice

/**
 * The application's theme as the reader [choice] gives it: light, dark, or the device's mode, followed as it changes.
 */
@Composable
fun AppTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    AppTheme(dark = choice.dark(isSystemInDarkTheme()), content = content)
}

/**
 * The application's theme, built from `res/values/app_theme.xml` (ADR 20261004): the light scheme, or the dark one
 * when [dark], whatever the system's mode; the corners and the main type sizes from the same file. The system bars'
 * icons follow the scheme: dark on the light one, light on the dark one.
 */
@Composable
fun AppTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val resources = LocalContext.current.resources
    val colors = remember(resources, dark) { appColorScheme(resources, dark) }
    val shapes = remember(resources) { appShapes(resources) }
    val typography = remember(resources) { appTypography(resources) }
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, dark) { activity?.showSystemBarsFor(dark) }
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
}

/**
 * Draws [this] Activity edge to edge with the system bars' icons readable on the [dark] or light scheme: the status
 * bar transparent, three-button navigation over a nearly opaque scrim of the scheme's lowest surface. The window's
 * own background is the scheme's surface, so nothing of the other scheme shows before the content is drawn.
 */
fun ComponentActivity.showSystemBarsFor(dark: Boolean) {
    val scheme = appColorScheme(resources, dark)
    window.setBackgroundDrawable(ColorDrawable(scheme.surface.toArgb()))
    val scrim = scheme.surfaceContainerLowest.copy(alpha = NAVIGATION_SCRIM_ALPHA).toArgb()
    val transparent = AndroidColor.TRANSPARENT
    enableEdgeToEdge(
        statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
        navigationBarStyle = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
    )
}

private const val NAVIGATION_SCRIM_ALPHA = 0xe6 / 255f

/** The colour scheme `app_theme.xml` gives: its `theme_dark_<role>` colours when [dark], else `theme_light_<role>`. */
fun appColorScheme(resources: Resources, dark: Boolean): ColorScheme {
    fun color(light: Int, darkOne: Int) = Color(resources.getColor(if (dark) darkOne else light, null))
    return ColorScheme(
        primary = color(R.color.theme_light_primary, R.color.theme_dark_primary),
        onPrimary = color(R.color.theme_light_onPrimary, R.color.theme_dark_onPrimary),
        primaryContainer = color(R.color.theme_light_primaryContainer, R.color.theme_dark_primaryContainer),
        onPrimaryContainer = color(R.color.theme_light_onPrimaryContainer, R.color.theme_dark_onPrimaryContainer),
        inversePrimary = color(R.color.theme_light_inversePrimary, R.color.theme_dark_inversePrimary),
        secondary = color(R.color.theme_light_secondary, R.color.theme_dark_secondary),
        onSecondary = color(R.color.theme_light_onSecondary, R.color.theme_dark_onSecondary),
        secondaryContainer = color(R.color.theme_light_secondaryContainer, R.color.theme_dark_secondaryContainer),
        onSecondaryContainer = color(R.color.theme_light_onSecondaryContainer, R.color.theme_dark_onSecondaryContainer),
        tertiary = color(R.color.theme_light_tertiary, R.color.theme_dark_tertiary),
        onTertiary = color(R.color.theme_light_onTertiary, R.color.theme_dark_onTertiary),
        tertiaryContainer = color(R.color.theme_light_tertiaryContainer, R.color.theme_dark_tertiaryContainer),
        onTertiaryContainer = color(R.color.theme_light_onTertiaryContainer, R.color.theme_dark_onTertiaryContainer),
        background = color(R.color.theme_light_background, R.color.theme_dark_background),
        onBackground = color(R.color.theme_light_onBackground, R.color.theme_dark_onBackground),
        surface = color(R.color.theme_light_surface, R.color.theme_dark_surface),
        onSurface = color(R.color.theme_light_onSurface, R.color.theme_dark_onSurface),
        surfaceVariant = color(R.color.theme_light_surfaceVariant, R.color.theme_dark_surfaceVariant),
        onSurfaceVariant = color(R.color.theme_light_onSurfaceVariant, R.color.theme_dark_onSurfaceVariant),
        surfaceTint = color(R.color.theme_light_surfaceTint, R.color.theme_dark_surfaceTint),
        inverseSurface = color(R.color.theme_light_inverseSurface, R.color.theme_dark_inverseSurface),
        inverseOnSurface = color(R.color.theme_light_inverseOnSurface, R.color.theme_dark_inverseOnSurface),
        error = color(R.color.theme_light_error, R.color.theme_dark_error),
        onError = color(R.color.theme_light_onError, R.color.theme_dark_onError),
        errorContainer = color(R.color.theme_light_errorContainer, R.color.theme_dark_errorContainer),
        onErrorContainer = color(R.color.theme_light_onErrorContainer, R.color.theme_dark_onErrorContainer),
        outline = color(R.color.theme_light_outline, R.color.theme_dark_outline),
        outlineVariant = color(R.color.theme_light_outlineVariant, R.color.theme_dark_outlineVariant),
        scrim = color(R.color.theme_light_scrim, R.color.theme_dark_scrim),
        surfaceBright = color(R.color.theme_light_surfaceBright, R.color.theme_dark_surfaceBright),
        surfaceDim = color(R.color.theme_light_surfaceDim, R.color.theme_dark_surfaceDim),
        surfaceContainer = color(R.color.theme_light_surfaceContainer, R.color.theme_dark_surfaceContainer),
        surfaceContainerHigh = color(R.color.theme_light_surfaceContainerHigh, R.color.theme_dark_surfaceContainerHigh),
        surfaceContainerHighest = color(R.color.theme_light_surfaceContainerHighest, R.color.theme_dark_surfaceContainerHighest),
        surfaceContainerLow = color(R.color.theme_light_surfaceContainerLow, R.color.theme_dark_surfaceContainerLow),
        surfaceContainerLowest = color(R.color.theme_light_surfaceContainerLowest, R.color.theme_dark_surfaceContainerLowest),
        primaryFixed = color(R.color.theme_light_primaryFixed, R.color.theme_dark_primaryFixed),
        primaryFixedDim = color(R.color.theme_light_primaryFixedDim, R.color.theme_dark_primaryFixedDim),
        onPrimaryFixed = color(R.color.theme_light_onPrimaryFixed, R.color.theme_dark_onPrimaryFixed),
        onPrimaryFixedVariant = color(R.color.theme_light_onPrimaryFixedVariant, R.color.theme_dark_onPrimaryFixedVariant),
        secondaryFixed = color(R.color.theme_light_secondaryFixed, R.color.theme_dark_secondaryFixed),
        secondaryFixedDim = color(R.color.theme_light_secondaryFixedDim, R.color.theme_dark_secondaryFixedDim),
        onSecondaryFixed = color(R.color.theme_light_onSecondaryFixed, R.color.theme_dark_onSecondaryFixed),
        onSecondaryFixedVariant = color(R.color.theme_light_onSecondaryFixedVariant, R.color.theme_dark_onSecondaryFixedVariant),
        tertiaryFixed = color(R.color.theme_light_tertiaryFixed, R.color.theme_dark_tertiaryFixed),
        tertiaryFixedDim = color(R.color.theme_light_tertiaryFixedDim, R.color.theme_dark_tertiaryFixedDim),
        onTertiaryFixed = color(R.color.theme_light_onTertiaryFixed, R.color.theme_dark_onTertiaryFixed),
        onTertiaryFixedVariant = color(R.color.theme_light_onTertiaryFixedVariant, R.color.theme_dark_onTertiaryFixedVariant),
    )
}

/** Material 3's shape scale, its corners from `app_theme.xml`. */
fun appShapes(resources: Resources): Shapes {
    fun corner(@DimenRes id: Int) = RoundedCornerShape(resources.number(id, TypedValue.COMPLEX_UNIT_DIP).dp)
    return Shapes(
        extraSmall = corner(R.dimen.theme_shape_extraSmall),
        small = corner(R.dimen.theme_shape_small),
        medium = corner(R.dimen.theme_shape_medium),
        large = corner(R.dimen.theme_shape_large),
        extraLarge = corner(R.dimen.theme_shape_extraLarge),
    )
}

/** Material 3's type scale, its title and body sizes from `app_theme.xml`, each line height kept in proportion. */
fun appTypography(resources: Resources): Typography {
    fun TextStyle.sized(@DimenRes id: Int): TextStyle {
        val size = resources.number(id, TypedValue.COMPLEX_UNIT_SP)
        return copy(fontSize = size.sp, lineHeight = (lineHeight.value * size / fontSize.value).sp)
    }
    val base = Typography()
    return base.copy(
        titleLarge = base.titleLarge.sized(R.dimen.theme_type_titleLarge),
        titleMedium = base.titleMedium.sized(R.dimen.theme_type_titleMedium),
        titleSmall = base.titleSmall.sized(R.dimen.theme_type_titleSmall),
        bodyLarge = base.bodyLarge.sized(R.dimen.theme_type_bodyLarge),
        bodyMedium = base.bodyMedium.sized(R.dimen.theme_type_bodyMedium),
        bodySmall = base.bodySmall.sized(R.dimen.theme_type_bodySmall),
    )
}

/** The number a `dimen` resource is written with, which must be in [unit] (dp or sp). */
private fun Resources.number(@DimenRes id: Int, unit: Int): Float {
    val value = TypedValue()
    getValue(id, value, true)
    check(value.type == TypedValue.TYPE_DIMENSION && value.getComplexUnit() == unit)
    return TypedValue.complexToFloat(value.data)
}
