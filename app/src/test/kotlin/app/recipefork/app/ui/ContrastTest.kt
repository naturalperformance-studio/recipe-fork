package app.recipefork.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A11Y-002: the contrast of every pair of the theme's colours the screens draw text, icons and control boundaries in,
 * computed from [AppColors] — the colours MainActivity's theme gives — by the formula of WCAG 2.2. The application has
 * one light presentation, so there is no dark one to measure. A disabled control (drawn at 38 % opacity) is left out,
 * as WCAG leaves out inactive controls.
 */
class ContrastTest {
    private val c: ColorScheme = AppColors

    /** A foreground on a background, where it is drawn, and the ratio it must reach. */
    private data class Pair(val where: String, val foreground: Color, val background: Color, val least: Double)

    private fun text(where: String, foreground: Color, background: Color) = Pair(where, foreground, background, TEXT)
    private fun graphic(where: String, foreground: Color, background: Color) = Pair(where, foreground, background, GRAPHIC)

    private val pairs = listOf(
        // Text.
        text("body text — titles, ingredient lines, steps, fields' text", c.onSurface, c.surface),
        text("muted notes and marks — an entry's sending mark, step notes, ingredient notes, labels, placeholders, the synonym beside an offered entry", c.onSurfaceVariant, c.surface),
        text("text actions — the bar's actions, text buttons, timers' figures, the checklist's label", c.primary, c.surface),
        text("a filled button's label, a step's number in its circle on the card", c.onPrimary, c.primary),
        text("a problem said under a field", c.error, c.surface),
        text("a reference in a step, on its tint", c.onSurface, c.secondaryContainer),
        text("a reference that exceeds its ingredient, on the warning tint", c.onSurface, c.errorContainer),
        text("a filter chip's label, a filter in force", c.onSecondaryContainer, c.secondaryContainer),
        text("a quick filter offered, \"All filters\"", c.onSurfaceVariant, c.surface),
        text("the filters sheet — its title, sections and choices", c.onSurface, c.surfaceContainerLow),
        text("the filters sheet — its hints and fields' labels", c.onSurfaceVariant, c.surfaceContainerLow),
        text("the filters sheet's Done", c.primary, c.surfaceContainerLow),
        text("a tag's input chip in the editor, the declared language's segment", c.onSecondaryContainer, c.secondaryContainer),
        text("an offered tag's suggestion chip, the other language's segment", c.onSurfaceVariant, c.surface),
        text("a tonal button's label and icon — a timer's controls", c.onSecondaryContainer, c.secondaryContainer),
        text("the snackbar's statement and a tooltip's text", c.inverseOnSurface, c.inverseSurface),
        text("the snackbar's action", c.inversePrimary, c.inverseSurface),
        text("a dialog's title", c.onSurface, c.surfaceContainerHigh),
        text("a dialog's text", c.onSurfaceVariant, c.surfaceContainerHigh),
        text("a dialog's buttons", c.primary, c.surfaceContainerHigh),
        text("a menu's items", c.onSurface, c.surfaceContainer),
        text("the bar of running timers — names and times left", c.onSurface, c.surfaceContainer),
        text("the book's top bar — its title and menu — with a list scrolled under it", c.onSurface, c.surfaceContainer),
        text("the chosen view's tab", c.primary, c.surface),
        text("a view's tab not chosen", c.onSurfaceVariant, c.surface),
        text("a view's floating action — its words and icon", c.onPrimaryContainer, c.primaryContainer),
        text("a book's entry, a card — its title and state", c.onSurface, c.surfaceContainerHighest),
        text("a book's entry, a card — its tags and sending mark", c.onSurfaceVariant, c.surfaceContainerHighest),
        text("a photo source sheet's title and items", c.onSurface, c.surfaceContainerLow),
        text("the empty cover's label", c.onSurfaceVariant, c.surfaceContainerHigh),
        // Icons that carry meaning and the boundaries of controls.
        graphic("the drag column's handle, on the column's tint", c.onSurfaceVariant, c.surfaceContainerHigh),
        graphic("the empty cover's camera", c.onSurfaceVariant, c.surfaceContainerHigh),
        graphic("an outlined field's and a fixing option's boundary", c.outline, c.surface),
        graphic("a focused field's and a chosen option's boundary, the chosen view's bar", c.primary, c.surface),
        graphic("a checkbox's and a radio button's boundary, the free-text warning's icon", c.onSurfaceVariant, c.surface),
        graphic("a filter chip's remove icon", c.onSecondaryContainer, c.secondaryContainer),
        graphic("a quick filter's and \"All filters\"'s boundary", c.outline, c.surface),
        graphic("the filters sheet — a field's boundary", c.outline, c.surfaceContainerLow),
        graphic("the filters sheet — a checkbox's and a radio button's boundary", c.onSurfaceVariant, c.surfaceContainerLow),
        graphic("a suggestion chip's and the language switch's boundary", c.outline, c.surface),
        graphic("an entry's ⋮, on its card", c.onSurface, c.surfaceContainerHighest),
        graphic("the indicator of a list being read", c.primary, c.surface),
        graphic("the progress of a file being imported or exported, on its track", c.primary, c.surfaceContainerHighest),
    )

    @Test
    fun everyPairTheScreensUseMeetsItsThreshold() {
        val failing = pairs.filter { ratio(it.foreground, it.background) < it.least }
            .map { "${it.where}: %.2f:1, less than %.1f:1".format(ratio(it.foreground, it.background), it.least) }
        assertEquals(emptyList<String>(), failing)
    }

    // The formula itself, on the pairs whose ratios are known.
    @Test
    fun theRatioIsWcags() {
        assertEquals(21.0, ratio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, ratio(Color.White, Color.White), 0.001)
        assertEquals(4.54, ratio(Color(0xFF767676), Color.White), 0.01)
    }

    private companion object {
        const val TEXT = 4.5
        const val GRAPHIC = 3.0

        fun ratio(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        /** The relative luminance of an opaque sRGB colour (WCAG 2.2). */
        fun luminance(color: Color): Double {
            require(color.alpha == 1f) { "$color is not opaque" }
            fun channel(v: Float): Double = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }
    }
}
