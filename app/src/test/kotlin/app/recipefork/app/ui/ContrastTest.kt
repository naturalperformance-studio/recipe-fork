package app.recipefork.app.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A11Y-002: the contrast of every pair of the theme's colours the screens draw text, icons and control boundaries in,
 * computed in both schemes AppTheme builds from `app_theme.xml` — the light one and the dark one — by the formula of
 * WCAG 2.2. A disabled control (drawn at 38 % opacity) is left out, as WCAG leaves out inactive controls.
 */
@RunWith(AndroidJUnit4::class)
class ContrastTest {
    /** A foreground on a background, where it is drawn, and the ratio it must reach. */
    private data class Pair(val where: String, val foreground: Color, val background: Color, val least: Double)

    private fun text(where: String, foreground: Color, background: Color) = Pair(where, foreground, background, TEXT)
    private fun graphic(where: String, foreground: Color, background: Color) = Pair(where, foreground, background, GRAPHIC)

    private fun pairs(c: ColorScheme) = listOf(
        // Text.
        text("body text — titles, ingredient lines, steps, fields' text", c.onSurface, c.surface),
        text("muted notes and marks — an entry's sending mark, an ingredient row's free-text mark and its \"New ingredient\", a heading's \"Unnamed group\", step notes, a step's author's note on the card, ingredient notes, labels, placeholders, the synonym beside an offered entry, a step's timer line on the card's View (its clock and \"Oven · 40 min\"), a step's row in the editor — its number, author's note, timer lines and \"New step\"", c.onSurfaceVariant, c.surface),
        text("text actions — the bar's actions, text buttons, timers' figures, the checklist's label", c.primary, c.surface),
        text("a filled button's label, a step's number in its circle on the card", c.onPrimary, c.primary),
        text("a problem said under a field — the title's and the description's marks and counters among them — an ingredient row's mark of an amount its field does not hold", c.error, c.surface),
        text("a reference in a step, on its tint", c.onSurface, c.secondaryContainer),
        text("a reference that exceeds its ingredient, on the warning tint", c.onSurface, c.errorContainer),
        text("a filter chip's label, a filter in force", c.onSecondaryContainer, c.secondaryContainer),
        text("a quick filter offered, \"All filters\"", c.onSurfaceVariant, c.surface),
        text("the filters sheet — its title, sections and choices", c.onSurface, c.surfaceContainerLow),
        text("the filters sheet — its hints and fields' labels", c.onSurfaceVariant, c.surfaceContainerLow),
        text("the filters sheet's Done", c.primary, c.surfaceContainerLow),
        text("an ingredient row's or a heading's sheet in the editor — its title, the fields' text, the offers, the divisibility question, its actions", c.onSurface, c.surfaceContainerLow),
        text("an ingredient row's or a heading's sheet — its fields' labels and placeholders, the free-text hint, a synonym beside an offer", c.onSurfaceVariant, c.surfaceContainerLow),
        text("an ingredient row's or a heading's sheet's Done", c.primary, c.surfaceContainerLow),
        text("a problem said under a field of an ingredient row's sheet; the removal among a row's or a heading's sheet's actions", c.error, c.surfaceContainerLow),
        text("a step's sheet in the editor — its title, the step's text, its note, the timers' fields, the names of its timers' and its photo or clip's parts, its actions (move, insert)", c.onSurface, c.surfaceContainerLow),
        text("a step's sheet — its fields' labels, the note's fork hint, the step's number, the empty step's placeholder, the references' offer", c.onSurfaceVariant, c.surfaceContainerLow),
        text("a step's sheet — its Done, its \"Insert ingredient\", \"Add timer\" and \"Add photo or clip\"", c.primary, c.surfaceContainerLow),
        text("a step's sheet — its removal, a part more than its whole, the text's mark and counter, a timer's removal, what went wrong with a photo or clip offered", c.error, c.surfaceContainerLow),
        text("a tag's input chip in the editor, the declared language's segment", c.onSecondaryContainer, c.secondaryContainer),
        text("an offered tag's suggestion chip, the other language's segment", c.onSurfaceVariant, c.surface),
        text("a tonal button's label and icon — a timer's controls, the card's − and +", c.onSecondaryContainer, c.secondaryContainer),
        text("a running timer's control on the card — its name, its time left and its icon on the primary fill", c.onPrimary, c.primary),
        text("the card's ingredients and equipment on their tint — headings, lines, the fixed note, \"All gathered\" once folded", c.onSurface, c.surfaceContainerLow),
        text("the card's ingredients and equipment on their tint — the status line, the arrows, an ingredient's note, the fixing field's label, an item's \"Not in your kitchen\"", c.onSurfaceVariant, c.surfaceContainerLow),
        text("the fixing control's Cancel and Release, the folded ingredients' and equipment's \"show\", on their tint", c.primary, c.surfaceContainerLow),
        text("an ingredient line or an item of equipment marked done on the card — struck through and muted — on their tint", c.onSurfaceVariant, c.surfaceContainerLow),
        text("a step marked done on the card — struck through and muted", c.onSurfaceVariant, c.surface),
        text("a line of the shopping list ticked as bought, in the basket — struck through and muted — and the notes under each line", c.onSurfaceVariant, c.background),
        text("a problem said under the fixing field, on the ingredients' tint", c.error, c.surfaceContainerLow),
        text("Delete and Remove in the card's menu and in a book entry's menu", c.error, c.surfaceContainer),
        text("the snackbar's statement and a tooltip's text", c.inverseOnSurface, c.inverseSurface),
        text("the snackbar's action", c.inversePrimary, c.inverseSurface),
        text("a dialog's title", c.onSurface, c.surfaceContainerHigh),
        text("a dialog's text", c.onSurfaceVariant, c.surfaceContainerHigh),
        text("a dialog's buttons", c.primary, c.surfaceContainerHigh),
        text("a destructive confirm in a dialog — Delete, Remove, Clear", c.error, c.surfaceContainerHigh),
        text("the restore question's ways — \"Add to the book\"", c.onSurface, c.surfaceContainerHigh),
        text("the restore question's ways — \"Replace the book\"", c.error, c.surfaceContainerHigh),
        text("a menu's items", c.onSurface, c.surfaceContainer),
        text("\"Preparing the document\" under its progress, while a document is made to print or share", c.onSurface, c.surfaceContainer),
        text("the bar of running timers — names and times left", c.onSurface, c.surfaceContainer),
        text("the book's top bar — its title and menu — with a list scrolled under it", c.onSurface, c.surfaceContainer),
        text("the chosen tab — the book's view, the card's View or Cooking", c.primary, c.surface),
        text("a tab not chosen — the book's view, the card's View or Cooking", c.onSurfaceVariant, c.surface),
        text("the card's chosen tab with the recipe scrolled under the bar, on the bar's scrolled colour", c.primary, c.surfaceContainer),
        text("the card's tab not chosen with the recipe scrolled under the bar, on the bar's scrolled colour", c.onSurfaceVariant, c.surfaceContainer),
        text("a view's floating action — its words and icon; the card's current step on Cooking, on its container", c.onPrimaryContainer, c.primaryContainer),
        text("the arrow after a rounded reference in the card's current step, and the step's author's note, on its container", c.onSurfaceVariant, c.primaryContainer),
        text("My kitchen opened from a recipe — the row of its item, its name and \"Needed for the recipe\", on the primary container", c.onPrimaryContainer, c.primaryContainer),
        text("a book's entry, a row of its contents — its title and state", c.onSurface, c.surface),
        text("a book's entry, a row of its contents — its tags, stated author and sending mark", c.onSurfaceVariant, c.surface),
        text("a book's entry chosen in the selection, on its tint — its title and state", c.onSurface, c.secondaryContainer),
        text("a book's entry chosen in the selection, on its tint — its tags, stated author and sending mark", c.onSurfaceVariant, c.secondaryContainer),
        text("a book's entry's drop cap — the title's first letter on its square", c.onSurfaceVariant, c.surfaceContainer),
        text("a collection's card in the book's row of collections — its name", c.onSurface, c.surfaceContainerHighest),
        text("a collection's card in the book's row of collections — its count of recipes", c.onSurfaceVariant, c.surfaceContainerHighest),
        text("the heading of the book's row of collections, and an imported collection's stated author", c.onSurfaceVariant, c.surface),
        text("a photo source sheet's title and items", c.onSurface, c.surfaceContainerLow),
        text("a photo source sheet's removal — the cover, a step's photo or clip", c.error, c.surfaceContainerLow),
        text("the empty cover's tile — its \"Add a cover photo\"", c.onSurface, c.surfaceContainerHigh),
        text("the empty cover's tile — its \"Optional\"", c.onSurfaceVariant, c.surfaceContainerHigh),
        text("the card's summary line under the title — counts of ingredients and steps, the timers' time", c.onSurfaceVariant, c.background),
        // Icons that carry meaning and the boundaries of controls.
        graphic("the drag column's handle, on the column's tint", c.onSurfaceVariant, c.surfaceContainerHigh),
        graphic("the empty cover's picture with a plus", c.onSurfaceVariant, c.surfaceContainerHigh),
        graphic("a photo source sheet's icons — take, choose, record, choose a clip", c.onSurfaceVariant, c.surfaceContainerLow),
        graphic("a photo source sheet's removal's bin", c.error, c.surfaceContainerLow),
        graphic("the restore question's ways — the icon of \"Add to the book\"", c.onSurface, c.surfaceContainerHigh),
        graphic("the restore question's ways — the icon of \"Replace the book\"", c.error, c.surfaceContainerHigh),
        graphic("a step's sheet — the icons of \"Insert ingredient\", \"Add timer\" and \"Add photo or clip\"", c.primary, c.surfaceContainerLow),
        graphic("an empty view's picture — the book with nothing in it, a search that found nothing, a card whose recipe is gone", c.onSurfaceVariant, c.surface),
        graphic("the engraved mark of an empty section of the book, in ink", c.onSurface, c.background),
        graphic("the engraved cart of the empty shopping list", c.onSurfaceVariant, c.background),
        graphic("an outlined field's and a fixing option's boundary", c.outline, c.surface),
        graphic("a focused field's and a chosen option's boundary, the chosen view's bar", c.primary, c.surface),
        graphic("a checkbox's and a radio button's boundary, the free-text warning's icon", c.onSurfaceVariant, c.surface),
        graphic("a filter chip's remove icon, a tag's cross in the editor", c.onSecondaryContainer, c.secondaryContainer),
        graphic("a quick filter's and \"All filters\"'s boundary", c.outline, c.surface),
        graphic("the filters sheet — a field's boundary", c.outline, c.surfaceContainerLow),
        graphic("the fixing field's and the unit menu's boundary, on the ingredients' tint", c.outline, c.surfaceContainerLow),
        graphic("the focused fixing field's boundary, on the ingredients' tint", c.primary, c.surfaceContainerLow),
        graphic("the filters sheet — a checkbox's and a radio button's boundary", c.onSurfaceVariant, c.surfaceContainerLow),
        graphic("an ingredient row's sheet — a field's boundary", c.outline, c.surfaceContainerLow),
        graphic("an ingredient row's sheet — a focused field's boundary", c.primary, c.surfaceContainerLow),
        graphic("an ingredient row's sheet — a radio button's boundary, the unit menu's arrow", c.onSurfaceVariant, c.surfaceContainerLow),
        graphic("a step's sheet — a field's boundary", c.outline, c.surfaceContainerLow),
        graphic("a step's sheet — a focused field's boundary", c.primary, c.surfaceContainerLow),
        graphic("a step's sheet — its icons: insert an ingredient, timer, photo or clip", c.onSurfaceVariant, c.surfaceContainerLow),
        graphic("a step's row — its timer line's clock", c.onSurfaceVariant, c.surface),
        graphic("a step's row — a clip's mark on its picture, before the frame is read", c.onSurfaceVariant, c.surfaceVariant),
        graphic("a suggestion chip's and the language switch's boundary", c.outline, c.surface),
        graphic("an entry's ⋮, on the page", c.onSurface, c.surface),
        graphic("an entry's mark of being chosen in the book's selection — the filled circle with its check — on the chosen row's tint", c.primary, c.secondaryContainer),
        graphic("an entry's mark of not being chosen in the book's selection — the empty circle — on the page", c.onSurfaceVariant, c.surface),
        text("the book's selection bar — its count, its ✕, its icons and its ⋮ — on its tint (v0.38)", c.onSecondaryContainer, c.secondaryContainer),
        graphic("the indicator of a list being read", c.primary, c.surface),
        graphic("a removal's icon in the editor — an ingredient row, a step, a timer", c.error, c.surface),
        graphic("the progress of a file being imported or exported, on its track", c.primary, c.surfaceContainerHighest),
        graphic("a step's number circle in the card's current step, on its container", c.primary, c.primaryContainer),
        graphic("the stepper's line joining one step's circle to the next on Cooking", c.onSurfaceVariant, c.surface),
        graphic("the stepper's line from the card's current step's circle, on its container", c.onSurfaceVariant, c.primaryContainer),
        graphic("the check in a step's circle once marked done on Cooking", c.onPrimary, c.primary),
        graphic("a running timer's primary fill, told from the card it stands on", c.primary, c.surface),
        graphic("a running timer's primary fill, told from the current step's container it stands in", c.primary, c.primaryContainer),
        graphic("a timer's control at rest and its Cancel on the current step — the primary edge that stands them off its container", c.primary, c.primaryContainer),
        text("a timer's control at rest and its Cancel on the current step — the label on the surface they are filled with", c.primary, c.surface),
        graphic("My kitchen — a ticked or partly ticked checkbox's box, an item's or a group's", c.primary, c.surface),
        graphic("My kitchen — the tick or the dash in a ticked or partly ticked checkbox's box", c.onPrimary, c.primary),
        graphic("My kitchen opened from a recipe — its item's checkbox boundary, unticked, on the primary container", c.onSurfaceVariant, c.primaryContainer),
        graphic("My kitchen opened from a recipe — its item's checkbox box, ticked, on the primary container", c.primary, c.primaryContainer),
        text("the card's nutrition — a tile's value", c.onSurface, c.surfaceContainer),
        text("the card's nutrition — a tile's word", c.onSurfaceVariant, c.surfaceContainer),
        text("the card's nutrition — the chosen segment's label and check", c.onSecondaryContainer, c.secondaryContainer),
        text("the card's nutrition — the segment not chosen", c.onSurface, c.surface),
        text("the card's nutrition — \"Whole dish\" alone, the rows not counted", c.onSurfaceVariant, c.surface),
        text("the card's nutrition — \"Details\" and its chevron", c.primary, c.surface),
        graphic("the card's nutrition — the switch's boundary", c.outline, c.surface),
        text("the nutrition sheet — its title, captions and counted rows", c.onSurface, c.surfaceContainerLow),
        text("the nutrition sheet — the rows not counted and how it is counted", c.onSurfaceVariant, c.surfaceContainerLow),
        // Beet: the tertiary colour marks provenance and nothing else (the redesign, T-296).
        text("a fork's provenance line on the card — \"Forked from …\"", c.tertiary, c.surface),
        text("a fork's provenance line on the container tint", c.tertiary, c.surfaceContainer),
        graphic("the provenance glyph heading the held-fork dialog", c.tertiary, c.surfaceContainerHigh),
        graphic("the engraved mark's bent tine in an empty section of the book", c.tertiary, c.background),
    )

    private fun failing(c: ColorScheme) = pairs(c).filter { ratio(it.foreground, it.background) < it.least }
        .map { "${it.where}: %.2f:1, less than %.1f:1".format(ratio(it.foreground, it.background), it.least) }

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun everyPairTheScreensUseMeetsItsThresholdInTheLightScheme() {
        assertEquals(emptyList<String>(), failing(appColorScheme(resources, dark = false)))
    }

    @Test
    fun everyPairTheScreensUseMeetsItsThresholdInTheDarkScheme() {
        assertEquals(emptyList<String>(), failing(appColorScheme(resources, dark = true)))
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
