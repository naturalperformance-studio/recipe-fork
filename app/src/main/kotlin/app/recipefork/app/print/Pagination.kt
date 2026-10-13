package app.recipefork.app.print

/** A page's size in points (1/72 inch), with its 15 mm margins on every side. */
data class PageSize(val width: Float, val height: Float) {
    val margin: Float get() = MARGIN
    val textWidth: Float get() = width - 2 * margin

    companion object {
        /** The page of a shared PDF file. */
        val A4 = PageSize(595f, 842f)
        const val MARGIN = 15f / 25.4f * 72f

        /** A page of [widthMils] by [heightMils] thousandths of an inch, as a print job's media size gives it. */
        fun ofMils(widthMils: Int, heightMils: Int) = PageSize(widthMils * 72f / 1000f, heightMils * 72f / 1000f)
    }
}

/** A width and height in points. */
data class Points(val width: Float, val height: Float)

/**
 * Lines [from] until [to] of the document's block number [block], the first of them [top] points below the top of
 * the page's text area.
 */
data class Placement(val block: Int, val from: Int, val to: Int, val top: Float)

/**
 * A block as pagination sees it: [space] above it (none at a page's top), its lines' heights top to bottom, whether
 * it is kept with the first line of the block after it, and whether its lines go to one page together where they fit
 * on one. A [newPage] slab has no lines: what follows it begins a page.
 */
internal class Slab(
    val space: Float,
    val lines: List<Float>,
    val keepWithNext: Boolean = false,
    val whole: Boolean = false,
    val newPage: Boolean = false,
)

/**
 * [slabs] put on pages whose text area is [bodyHeight] tall, top to bottom: a line goes on the page only where all
 * of it fits, else it begins the next page; a block longer than a page breaks between its lines; a [Slab.whole] block
 * and a [Slab.keepWithNext] block with the first line after it move to the next page together when they do not fit
 * where they would begin (unless they would not fit on any page); after a [Slab.newPage] slab the next line begins a
 * page, unless the page is still empty — so a break opens no blank page, at the start, the end or after another break.
 * Placements name slabs by their index. Always at least one page.
 */
internal fun paginate(slabs: List<Slab>, bodyHeight: Float): List<List<Placement>> {
    val pages = mutableListOf(mutableListOf<Placement>())
    var y = 0f
    var breaking = false
    slabs.forEachIndexed { index, slab ->
        if (slab.newPage) breaking = true
        if (slab.lines.isEmpty()) return@forEachIndexed
        if (breaking) {
            breaking = false
            if (pages.last().isNotEmpty()) {
                pages += mutableListOf<Placement>()
                y = 0f
            }
        }
        val together = when {
            slab.keepWithNext -> keptHeight(slabs, index)
            slab.whole -> slab.lines.sum()
            else -> slab.lines.first()
        }
        if (y > 0f && y + slab.space + together > bodyHeight && together <= bodyHeight) {
            pages += mutableListOf<Placement>()
            y = 0f
        }
        if (y > 0f) y += slab.space
        var from = 0
        var top = y
        slab.lines.forEachIndexed { line, height ->
            if (y > 0f && y + height > bodyHeight) {
                if (line > from) pages.last() += Placement(index, from, line, top)
                pages += mutableListOf<Placement>()
                from = line
                y = 0f
                top = 0f
            }
            y += height
        }
        pages.last() += Placement(index, from, slab.lines.size, top)
    }
    return pages
}

/**
 * The height slab [index] needs to stay with what follows it: all of it, and the first line (or more) after it — only
 * itself where a page break follows it.
 */
private fun keptHeight(slabs: List<Slab>, index: Int): Float {
    val slab = slabs[index]
    val next = (index + 1 until slabs.size).firstOrNull { slabs[it].lines.isNotEmpty() || slabs[it].newPage }
    if (next == null || slabs[next].newPage) return slab.lines.sum()
    val after = slabs[next]
    val following = when {
        after.keepWithNext -> keptHeight(slabs, next)
        after.whole -> after.lines.sum()
        else -> after.lines.first()
    }
    return slab.lines.sum() + after.space + following
}

/**
 * The size a picture of [width] by [height] pixels is drawn at: as wide as [textWidth], or less where that would make
 * it taller than [maxHeight]; its proportions kept.
 */
internal fun pictureSize(width: Int, height: Int, textWidth: Float, maxHeight: Float): Points {
    val scale = minOf(textWidth / width, maxHeight / height)
    return Points(width * scale, height * scale)
}
