package app.recipefork.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The application's colours: one light presentation, whatever the system's, so that every pair of them the screens
 * use is the pair measured against A11Y-002's thresholds.
 */
val AppColors: ColorScheme = lightColorScheme()

/** The application's theme, in [AppColors]. */
@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AppColors, content = content)
