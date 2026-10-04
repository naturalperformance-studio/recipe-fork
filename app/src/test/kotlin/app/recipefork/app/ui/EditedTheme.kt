package app.recipefork.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * The resources of [base] as if `app_theme.xml` held [colors] (by colour resource) and [sizes] (by `sp` dimen resource,
 * in sp) in place of its own values: an owner's edit of the theme file, without rebuilding it.
 */
fun editedThemeResources(base: Context, colors: Map<Int, Color> = emptyMap(), sizes: Map<Int, Int> = emptyMap()): Resources {
    @Suppress("DEPRECATION")
    return object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
        override fun getColor(id: Int, theme: Theme?): Int = colors[id]?.toArgb() ?: super.getColor(id, theme)

        override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
            super.getValue(id, outValue, resolveRefs)
            // A whole number of sp, written as a complex dimension with no fraction.
            sizes[id]?.let { outValue.data = (it shl TypedValue.COMPLEX_MANTISSA_SHIFT) or TypedValue.COMPLEX_UNIT_SP }
        }
    }
}

/**
 * [content] in the app's light [AppTheme] built from [resources] — an edited theme file — while everything else in it
 * reads the context it was given.
 */
@Composable
fun EditedAppTheme(resources: Resources, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val edited = remember(base, resources) {
        object : ContextWrapper(base) {
            override fun getResources(): Resources = resources
        }
    }
    CompositionLocalProvider(LocalContext provides edited) {
        AppTheme(dark = false) { CompositionLocalProvider(LocalContext provides base, content = content) }
    }
}
