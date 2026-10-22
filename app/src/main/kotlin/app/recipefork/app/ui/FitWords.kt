package app.recipefork.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
 * larger text, and on a narrow window make it unreadable — and where a word is too wide even then, it says so. With
 * [hyphenParts], a hyphenated word counts as its parts, the hyphen kept on the first — the line may break after it.
 */
internal fun fitWords(
    text: String,
    style: TextStyle,
    width: Int,
    measurer: TextMeasurer,
    density: Density,
    hyphenParts: Boolean = false,
): FittedWords {
    val words = if (hyphenParts) wordsOf(text).flatMap(::hyphenPartsOf) else wordsOf(text)
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
 * its room even then is not broken: the text keeps to one line and ends with an ellipsis. [onTextLayout] hears each
 * layout of it, as [Text]'s does.
 */
@Composable
internal fun WordFitText(
    text: String,
    style: TextStyle,
    width: Int,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fitted = remember(text, style, width, measurer, density) { fitWords(text, style, width, measurer, density) }
    FittedText(text, fitted, modifier, onTextLayout)
}

/**
 * [text] as [WordFitText] above draws it, in the width the place it stands in gives it — a list item's line, a dialog's
 * row — and in [style], by default the one that place gives its text; [modifier] is the text's own. Asked how tall or
 * wide it is before it is measured — as a list item asks of its supporting text — it answers as its text, so laid out.
 * [onTextLayout] hears each layout of the text.
 */
@Composable
internal fun WordFitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val policy = remember(text, style, measurer, density) { FittedTextPolicy(text, style, measurer, density) }
    Layout(
        content = { BoxWithConstraints { WordFitText(text, style, constraints.maxWidth, modifier, onTextLayout) } },
        measurePolicy = policy,
    )
}

/**
 * Lays out its one child — the [text] fitted to its width — as it measures itself, and answers the questions of its size
 * before measuring from the text, fitted ([fitWords]) to the width asked about, as [FittedText] lays it out.
 */
private class FittedTextPolicy(
    private val text: String,
    private val style: TextStyle,
    private val measurer: TextMeasurer,
    private val density: Density,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val placeable = measurables.single().measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    private fun height(width: Int): Int {
        val fitted = fitWords(text, style, width, measurer, density)
        return measurer.measure(
            text,
            fitted.style,
            softWrap = !fitted.overflows,
            maxLines = if (fitted.overflows) 1 else Int.MAX_VALUE,
            constraints = Constraints(maxWidth = width),
        ).size.height
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = height(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = height(width)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        widestWord(listOf(text), style, measurer)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
}

/**
 * [text] as [fitted] says: in its style, wrapped between words; or, where a word is wider than its room even so, on one
 * line ending with an ellipsis.
 */
@Composable
internal fun FittedText(text: String, fitted: FittedWords, modifier: Modifier = Modifier, onTextLayout: (TextLayoutResult) -> Unit = {}) {
    if (fitted.overflows) {
        Text(
            text, style = fitted.style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier,
            onTextLayout = onTextLayout,
        )
    } else {
        Text(text, style = fitted.style, modifier = modifier, onTextLayout = onTextLayout)
    }
}

/** [style] as [fitLine] gives it, and on how many [lines] the text is drawn: one, or two where it must. */
internal data class FittedLine(val style: TextStyle, val lines: Int)

/**
 * [text] in [style] within [width] pixels, a top bar's title: on one line, the whole of it made smaller as far as it
 * needs to fit ([fitWords], the text taken as one word), never smaller than the style's size at a font scale of 1. Longer
 * than its room even then (T-297), it takes two lines at that size, the line height [TWO_LINE_HEIGHT] times the size —
 * unless a word of it is wider than the line by itself, when it keeps to one line.
 */
internal fun fitLine(text: String, style: TextStyle, width: Int, measurer: TextMeasurer, density: Density): FittedLine {
    val line = fitWords(text.replace(' ', NO_BREAK), style, width, measurer, density)
    if (!line.overflows || widestWord(listOf(text), line.style, measurer) > width) return FittedLine(line.style, 1)
    return FittedLine(line.style.copy(lineHeight = line.style.fontSize * TWO_LINE_HEIGHT), 2)
}

/**
 * [text] in [style] within [width] pixels as [fitLine] lays it out — a top bar's title (v0.38, T-297): on one line,
 * made smaller as far as its size at a font scale of 1; else on two lines, broken between words; ending with an
 * ellipsis where it is longer still. At a large font scale it never spills out of the bar.
 */
@Composable
internal fun LineFitText(text: String, style: TextStyle, width: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fitted = remember(text, style, width, measurer, density) { fitLine(text, style, width, measurer, density) }
    Text(
        text, style = fitted.style, maxLines = fitted.lines, softWrap = fitted.lines > 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A two-line title's line height, by its size: 28 to 22, as Material's large title at its own size. */
private const val TWO_LINE_HEIGHT = 28f / 22f

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

/** The parts of [word] a line may break between: after each hyphen, which stays on the part before it. */
internal fun hyphenPartsOf(word: String): List<String> = Regex("""[^-]*-+|[^-]+""").findAll(word).map { it.value }.toList()

/** How many times the words are made smaller at most, and how much below the exact ratio each time. */
private const val FIT_STEPS = 8
private const val FIT_MARGIN = 0.98f
