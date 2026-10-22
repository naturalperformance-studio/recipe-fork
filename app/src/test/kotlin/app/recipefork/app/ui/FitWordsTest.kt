package app.recipefork.app.ui

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * [fitWordsTogether] (T-223): texts given the same room are drawn at one size, the one the text with the widest word
 * needs, and never below the size at a font scale of 1.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FitWordsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = Density(1f, fontScale = 2f)
    private val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
    private val style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    private val narrow = "Go on"
    private val wide = "Considerably longer"

    private fun widestAtFull(text: String) = widestWord(listOf(text), style, measurer)

    private fun px(style: TextStyle) = with(density) { style.fontSize.toPx() }

    @Test
    fun theTextsAreDrawnAtTheSizeTheWidestWordNeeds() {
        val width = (widestAtFull(wide) * 0.8f).toInt()
        // The narrow text alone fits as it is; the wide one must be made smaller.
        assertEquals(px(style), px(fitWords(narrow, style, width, measurer, density).style), 0.01f)
        val needed = fitWords(wide, style, width, measurer, density)
        assertTrue(px(needed.style) < px(style))
        val together = fitWordsTogether(listOf(narrow, wide), style, width, measurer, density)
        assertEquals(px(needed.style), px(together), 0.01f)
        assertEquals(px(together), px(fitWordsTogether(listOf(wide, narrow), style, width, measurer, density)), 0.01f)
        // Every word of both fits at that size, and no smaller size than the margin of fitWords is taken.
        assertTrue(widestWord(listOf(narrow, wide), together, measurer) <= width)
        val fits = px(together) * width / widestWord(listOf(wide), together, measurer)
        assertTrue("${px(together)} px where $fits px fit", px(together) >= fits * 0.95f)
    }

    // A hyphenated word may break after its hyphen: with hyphenParts its parts are measured, not the whole (T-301).
    @Test
    fun aHyphenatedWordCountsAsItsParts() {
        assertEquals(listOf("Картофельно-", "грибная"), hyphenPartsOf("Картофельно-грибная"))
        assertEquals(listOf("a-", "b-", "c"), hyphenPartsOf("a-b-c"))
        assertEquals(listOf("plain"), hyphenPartsOf("plain"))
        val title = "Strawberry-rhubarb crumble"
        val partsWidth = maxOf(widestAtFull("Strawberry-"), widestAtFull("rhubarb"), widestAtFull("crumble"))
        val width = partsWidth + 2
        assertTrue("the whole word is wider than the room", widestAtFull("Strawberry-rhubarb") > width)
        val parts = fitWords(title, style, width, measurer, density, hyphenParts = true)
        assertEquals("its parts fit as they are", px(style), px(parts.style), 0.01f)
        assertTrue(!parts.overflows)
        assertTrue("taken whole, it would be made smaller", px(fitWords(title, style, width, measurer, density).style) < px(style))
    }

    @Test
    fun theTextsAreNeverDrawnBelowTheSizeAtAFontScaleOfOne() {
        val width = (widestAtFull(wide) * 0.3f).toInt()
        val together = fitWordsTogether(listOf(narrow, wide), style, width, measurer, density)
        val least = with(density) { style.fontSize.value.dp.toPx() }
        assertEquals(least, px(together), 0.01f)
    }

    @Test
    fun textsThatFitKeepTheirStyle() {
        val width = widestAtFull(wide) + 1
        assertEquals(style, fitWordsTogether(listOf(narrow, wide), style, width, measurer, density))
    }

    // T-297, a bar's title ([fitLine]): one line where it fits drawn smaller; else two lines at the least size, the line
    // height 28/22 of it; one line again where a word by itself is wider than the line.
    @Test
    fun aTitleTakesTwoLinesOnlyWhereOneDoesNotFitEvenAtTheLeastSize() {
        val least = with(density) { style.fontSize.value.dp.toSp() }
        val atLeast = style.copy(fontSize = least)
        fun lineAtLeast(text: String) = measurer.measure(text, atLeast, softWrap = false, maxLines = 1).size.width

        val fitsSmaller = fitLine(wide, style, lineAtLeast(wide) + 2, measurer, density)
        assertEquals(1, fitsSmaller.lines)
        assertTrue(px(fitsSmaller.style) < px(style))

        val title = "$narrow $wide"
        val two = fitLine(title, style, widestWord(listOf(title), atLeast, measurer) + 2, measurer, density)
        assertEquals(2, two.lines)
        assertEquals(px(atLeast), px(two.style), 0.01f)
        assertEquals(two.style.fontSize.value * 28f / 22f, two.style.lineHeight.value, 0.01f)

        val word = fitLine(title, style, widestWord(listOf(title), atLeast, measurer) / 2, measurer, density)
        assertEquals(1, word.lines)
        assertEquals(px(atLeast), px(word.style), 0.01f)
    }
}
