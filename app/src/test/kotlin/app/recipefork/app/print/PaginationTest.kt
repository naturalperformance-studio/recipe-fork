package app.recipefork.app.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page breaks as a pure step over measured heights (FLOW-075 (3)). */
class PaginationTest {
    @Test
    fun aBlockLongerThanAPageBreaksBetweenItsLinesAndNoLineIsCut() {
        // 25 lines of 14 points in a 100-point text area: 7 lines a page (98 points), the 8th would be cut.
        val pages = paginate(listOf(Slab(6f, List(25) { 14f })), 100f)

        assertEquals(4, pages.size)
        assertEquals(listOf(Placement(0, 0, 7, 0f)), pages[0])
        assertEquals(listOf(Placement(0, 7, 14, 0f)), pages[1])
        assertEquals(listOf(Placement(0, 21, 25, 0f)), pages[3])
    }

    @Test
    fun blocksStackWithTheirSpaceWhichIsDroppedAtAPagesTop() {
        val pages = paginate(listOf(Slab(6f, listOf(14f, 14f)), Slab(6f, listOf(14f)), Slab(6f, listOf(50f))), 100f)

        assertEquals(listOf(Placement(0, 0, 2, 0f), Placement(1, 0, 1, 34f)), pages[0])
        // The third would end at 34 + 14 + 6 + 50 = 104 > 100: it begins the next page, without its space.
        assertEquals(listOf(Placement(2, 0, 1, 0f)), pages[1])
    }

    @Test
    fun aHeadingIsKeptWithTheFirstLineOfTheBlockAfterIt() {
        // Lines down to 70; the heading (20) would fit at 76..96, but the paragraph's first line would not.
        val slabs = listOf(Slab(0f, List(5) { 14f }), Slab(6f, listOf(20f), keepWithNext = true), Slab(4f, listOf(14f, 14f)))
        val pages = paginate(slabs, 100f)

        assertEquals(2, pages.size)
        assertEquals(listOf(Placement(0, 0, 5, 0f)), pages[0])
        assertEquals(listOf(Placement(1, 0, 1, 0f), Placement(2, 0, 2, 24f)), pages[1])
    }

    @Test
    fun aHeadingThatHasRoomForTheFirstLineAfterItStaysAndTheRestBreaks() {
        val slabs = listOf(Slab(0f, List(4) { 14f }), Slab(6f, listOf(20f), keepWithNext = true), Slab(4f, listOf(14f, 14f)))
        val pages = paginate(slabs, 100f)

        // 56 + 6 + 20 = 82, + 4 + 14 = 100: the heading and one line stay; the second line goes over.
        assertEquals(listOf(Placement(0, 0, 4, 0f), Placement(1, 0, 1, 62f), Placement(2, 0, 1, 86f)), pages[0])
        assertEquals(listOf(Placement(2, 1, 2, 0f)), pages[1])
    }

    @Test
    fun headingsInARowAreKeptTogetherWithTheLineAfterThem() {
        val slabs = listOf(
            Slab(0f, List(3) { 14f }),
            Slab(6f, listOf(20f), keepWithNext = true),
            Slab(6f, listOf(16f), keepWithNext = true),
            Slab(4f, listOf(14f)),
        )
        // 42 + 6 + 20 + 6 + 16 + 4 + 14 = 108 > 100: both headings move with the line.
        val pages = paginate(slabs, 100f)

        assertEquals(listOf(Placement(0, 0, 3, 0f)), pages[0])
        assertEquals(listOf(1, 2, 3), pages[1].map { it.block })
    }

    @Test
    fun aPictureThatDoesNotFitMovesWholeToTheNextPage() {
        val pages = paginate(listOf(Slab(0f, List(4) { 14f }), Slab(8f, listOf(60f), whole = true)), 100f)

        assertEquals(listOf(Placement(0, 0, 4, 0f)), pages[0])
        assertEquals(listOf(Placement(1, 0, 1, 0f)), pages[1])
    }

    @Test
    fun aWholeBlockTallerThanAPageStillBreaksBetweenItsLines() {
        val pages = paginate(listOf(Slab(0f, listOf(14f)), Slab(5f, List(10) { 14f }, whole = true)), 100f)

        assertEquals(listOf(Placement(0, 0, 1, 0f), Placement(1, 0, 5, 19f)), pages[0])
        assertEquals(listOf(Placement(1, 5, 10, 0f)), pages[1])
    }

    @Test
    fun nothingToLayOutIsOneEmptyPage() {
        assertEquals(listOf(emptyList<Placement>()), paginate(emptyList(), 100f))
    }

    @Test
    fun aPictureFitsTheTextWidthAndAtMostAThirdOfThePageKeepingItsProportions() {
        val a4 = PageSize.A4
        val wide = pictureSize(1600, 800, a4.textWidth, a4.height / 3)
        assertEquals(a4.textWidth, wide.width, 0.01f)
        assertEquals(a4.textWidth / 2, wide.height, 0.01f)
        // At 16:9 the full width would be 287 points tall, more than a third of A4: the third decides.
        assertEquals(a4.height / 3, pictureSize(1600, 900, a4.textWidth, a4.height / 3).height, 0.01f)

        val tall = pictureSize(900, 1600, a4.textWidth, a4.height / 3)
        assertEquals(a4.height / 3, tall.height, 0.01f)
        assertEquals(a4.height / 3 * 900 / 1600, tall.width, 0.01f)
        assertTrue(tall.width < a4.textWidth)

        // A small picture is brought up to the width too.
        assertEquals(a4.textWidth, pictureSize(160, 80, a4.textWidth, a4.height / 3).width, 0.01f)
    }

    // v0.37, T-262: what follows a break begins a page, its space dropped there.
    @Test
    fun aPageBreakStartsTheNextPage() {
        val slabs = listOf(Slab(0f, listOf(14f)), Slab(0f, emptyList(), newPage = true), Slab(6f, listOf(14f, 14f)))
        val pages = paginate(slabs, 100f)

        assertEquals(listOf(listOf(Placement(0, 0, 1, 0f)), listOf(Placement(2, 0, 2, 0f))), pages)
    }

    // A break opens no blank page: not at the start, not after another break, not at a page already begun by an
    // overflow, not at the end.
    @Test
    fun aPageBreakNeverLeavesABlankPage() {
        val brk = Slab(0f, emptyList(), newPage = true)
        assertEquals(listOf(listOf(Placement(1, 0, 1, 0f))), paginate(listOf(brk, Slab(6f, listOf(14f))), 100f))
        assertEquals(
            listOf(listOf(Placement(0, 0, 1, 0f)), listOf(Placement(3, 0, 1, 0f))),
            paginate(listOf(Slab(0f, listOf(14f)), brk, brk, Slab(6f, listOf(14f))), 100f),
        )
        assertEquals(listOf(listOf(Placement(0, 0, 1, 0f))), paginate(listOf(Slab(0f, listOf(14f)), brk), 100f))
        // Seven lines fill the page exactly; the eighth begins the next, and the break after it moves nothing more.
        val full = paginate(listOf(Slab(0f, List(8) { 14f }), brk, Slab(6f, listOf(14f))), 100f)
        assertEquals(3, full.size)
        assertEquals(listOf(Placement(0, 7, 8, 0f)), full[1])
        assertEquals(listOf(Placement(2, 0, 1, 0f)), full[2])
        val exactly = paginate(listOf(Slab(0f, List(7) { 14f }), brk, Slab(6f, listOf(14f))), 100f)
        assertEquals(listOf(listOf(Placement(0, 0, 7, 0f)), listOf(Placement(2, 0, 1, 0f))), exactly)
    }

    // A heading just before a break is not held to what comes after it.
    @Test
    fun aHeadingBeforeABreakIsKeptWithNothingAfterIt() {
        val slabs = listOf(
            Slab(0f, List(4) { 14f }),
            Slab(6f, listOf(20f), keepWithNext = true),
            Slab(0f, emptyList(), newPage = true),
            Slab(6f, listOf(60f)),
        )
        val pages = paginate(slabs, 100f)

        assertEquals(listOf(Placement(0, 0, 4, 0f), Placement(1, 0, 1, 62f)), pages[0])
        assertEquals(listOf(Placement(3, 0, 1, 0f)), pages[1])
    }

    @Test
    fun theMarginsAreFifteenMillimetres() {
        assertEquals(42.52f, PageSize.A4.margin, 0.01f)
        assertEquals(595f - 2 * 42.52f, PageSize.A4.textWidth, 0.02f)
        assertEquals(PageSize(595.44f, 841.68f), PageSize.ofMils(8270, 11690))
    }
}
