package app.recipefork.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/**
 * The font scale from which a screen is laid out for large text (v0.38): a notice puts its action on a line of its own,
 * and an extended floating action stays shrunk to its icon, so that neither squeezes nor covers the words beside it.
 */
const val LARGE_TEXT_SCALE = 1.5f

/** Whether the text is drawn at [LARGE_TEXT_SCALE] or larger. */
@Composable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
