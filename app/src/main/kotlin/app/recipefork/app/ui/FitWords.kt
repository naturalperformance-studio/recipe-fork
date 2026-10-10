package app.recipefork.app.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle

/** [style], made smaller just as far as the widest word of [text] needs to fit in [width] pixels; unchanged if it fits. */
internal fun fitWords(text: String, style: TextStyle, width: Int, measurer: TextMeasurer): TextStyle {
    val words = wordsOf(text)
    if (words.isEmpty() || width <= 0 || !style.fontSize.isSp) return style
    var fitted = style
    repeat(FIT_STEPS) {
        val widest = words.maxOf { measurer.measure(it, fitted, softWrap = false, maxLines = 1).size.width }
        if (widest <= width) return fitted
        val ratio = width.toFloat() / widest * FIT_MARGIN
        fitted = fitted.copy(
            fontSize = fitted.fontSize * ratio,
            lineHeight = if (fitted.lineHeight.isSp) fitted.lineHeight * ratio else fitted.lineHeight,
        )
    }
    return fitted
}

/** How wide, in pixels, the widest word of any of [texts] is laid out in [style] on one line; 0 where there is none. */
internal fun widestWord(texts: List<String>, style: TextStyle, measurer: TextMeasurer): Int =
    texts.flatMap(::wordsOf).maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0

private fun wordsOf(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

/** How many times the words are made smaller at most, and how much below the exact ratio each time. */
private const val FIT_STEPS = 8
private const val FIT_MARGIN = 0.98f
