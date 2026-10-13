package app.recipefork.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * [style] as [fitWords] gives it, and whether a word of the text is still wider than its room in it: drawn at the least
 * size allowed, it may not break inside, so the text keeps to one line and ends with an ellipsis instead.
 */
internal data class FittedWords(val style: TextStyle, val overflows: Boolean)

/**
 * [style], made smaller just as far as the widest word of [text] needs to fit in [width] pixels; unchanged if it fits.
 * It is never made smaller than the style's own size at a font scale of 1 in [density] — smaller would undo the reader's
 * larger text, and on a narrow window make it unreadable — and where a word is too wide even then, it says so.
 */
internal fun fitWords(text: String, style: TextStyle, width: Int, measurer: TextMeasurer, density: Density): FittedWords {
    val words = wordsOf(text)
    if (words.isEmpty() || width <= 0 || !style.fontSize.isSp) return FittedWords(style, false)
    val least = leastSize(style.fontSize, density).value
    var fitted = style
    fun widest() = words.maxOf { measurer.measure(it, fitted, softWrap = false, maxLines = 1).size.width }
    repeat(FIT_STEPS) {
        val widest = widest()
        if (widest <= width) return FittedWords(fitted, false)
        if (fitted.fontSize.value <= least) return FittedWords(fitted, true)
        val size = maxOf(fitted.fontSize.value * width / widest * FIT_MARGIN, least)
        val ratio = size / fitted.fontSize.value
        fitted = fitted.copy(
            fontSize = fitted.fontSize * ratio,
            lineHeight = if (fitted.lineHeight.isSp) fitted.lineHeight * ratio else fitted.lineHeight,
        )
    }
    return FittedWords(fitted, widest() > width)
}

/** In sp at the font scale in force, the size [size] is drawn at with a font scale of 1: as many pixels as so many dp. */
private fun leastSize(size: TextUnit, density: Density): TextUnit = with(density) { size.value.dp.toSp() }

/**
 * [text] in [style] within [width] pixels, each word made smaller as far as it needs to fit whole on a line ([fitWords]),
 * so that the text wraps only between words; never smaller than the style's size at a font scale of 1. A word wider than
 * its room even then is not broken: the text keeps to one line and ends with an ellipsis.
 */
@Composable
internal fun WordFitText(text: String, style: TextStyle, width: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fitted = remember(text, style, width, measurer, density) { fitWords(text, style, width, measurer, density) }
    FittedText(text, fitted, modifier)
}

/**
 * [text] as [fitted] says: in its style, wrapped between words; or, where a word is wider than its room even so, on one
 * line ending with an ellipsis.
 */
@Composable
internal fun FittedText(text: String, fitted: FittedWords, modifier: Modifier = Modifier) {
    if (fitted.overflows) {
        Text(text, style = fitted.style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier)
    } else {
        Text(text, style = fitted.style, modifier = modifier)
    }
}

/**
 * [text] in [style] on one line within [width] pixels — a top bar's title (v0.38) — the whole of it made smaller as far
 * as it needs to fit ([fitWords], the text taken as one word); never smaller than the style's size at a font scale of 1.
 * Longer than its room even then, it ends with an ellipsis: at a large font scale it neither wraps nor spills out of
 * the bar.
 */
@Composable
internal fun LineFitText(text: String, style: TextStyle, width: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fitted = remember(text, style, width, measurer, density) { fitWords(text.replace(' ', NO_BREAK), style, width, measurer, density) }
    Text(text, style = fitted.style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** The space that keeps the words beside it together, so that [fitWords] measures a whole line as one word. */
private const val NO_BREAK = '\u00A0'

/**
 * The one [style] for all of [texts], each given [width] pixels: made smaller, as [fitWords] does, just as far as the
 * widest word of any of them needs to fit, so that they are all drawn at one size; never smaller than the style's own
 * size at a font scale of 1.
 */
internal fun fitWordsTogether(texts: List<String>, style: TextStyle, width: Int, measurer: TextMeasurer, density: Density): TextStyle =
    fitWords(texts.joinToString(" "), style, width, measurer, density).style

/** How wide, in pixels, the widest word of any of [texts] is laid out in [style] on one line; 0 where there is none. */
internal fun widestWord(texts: List<String>, style: TextStyle, measurer: TextMeasurer): Int =
    texts.flatMap(::wordsOf).maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0

private fun wordsOf(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

/** How many times the words are made smaller at most, and how much below the exact ratio each time. */
private const val FIT_STEPS = 8
private const val FIT_MARGIN = 0.98f
