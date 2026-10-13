package app.recipefork.app.print

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import app.recipefork.app.media.Orientation
import app.recipefork.app.media.exifOrientation
import java.io.File
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Decodes a picture's pixels when the page that holds it is drawn; the platform's BitmapFactory by default. Whatever it
 * returns is recycled once that page is drawn.
 */
fun interface PictureDecoder {
    /**
     * [file] decoded at no more than [width] by [height] pixels as stored — before its EXIF orientation turns it —
     * keeping its proportions and never enlarged; null where it cannot be read.
     */
    fun decode(file: File, width: Int, height: Int): Bitmap?

    companion object {
        val Platform = PictureDecoder(::decodeAtMost)
    }
}

/** [file] decoded at most [width] by [height] pixels: sampled down by a power of two, then scaled the rest of the way. */
internal fun decodeAtMost(file: File, width: Int, height: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || width <= 0 || height <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
    // A sampled side is the stored one divided, rounded down or up by the format; taking it as rounded up never
    // leaves the scaled picture larger than asked.
    val sampledWidth = ceil(bounds.outWidth / sample.toDouble()).toInt()
    val sampledHeight = ceil(bounds.outHeight / sample.toDouble()).toInt()
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    if (sampledWidth > width || sampledHeight > height) {
        // BitmapFactory scales by inTargetDensity / inDensity while decoding, so no second full-size bitmap is made.
        options.inScaled = true
        if (width.toLong() * sampledHeight <= height.toLong() * sampledWidth) {
            options.inDensity = sampledWidth
            options.inTargetDensity = width
        } else {
            options.inDensity = sampledHeight
            options.inTargetDensity = height
        }
    }
    return BitmapFactory.decodeFile(file.path, options)
}

/** The footer of a page: the document's title cut to one line, and "n / m". */
data class Footer(val title: String, val number: String)

/**
 * A [PrintDocument] laid out on pages of [size] (FLOW-075): drawn black on white in the system's sans font whatever
 * the app's theme, each page with a muted footer. Laying out reads only each picture's size and turning; a picture's
 * pixels are decoded when the page that holds it is drawn, at the size it is printed, and let go once that page is
 * drawn — so the pages hold no pictures however many the document has. Both run off the main thread. Nothing here
 * reaches the network.
 */
class PrintPages private constructor(
    val size: PageSize,
    private val title: String,
    private val blocks: Map<Int, Laid>,
    private val pages: List<List<Placement>>,
    /** The height of the pages' text area, above the footer. */
    internal val bodyHeight: Float,
) {
    val count: Int get() = pages.size

    /** What page [index] holds: lines of the document's blocks (by their index in it), top to bottom. */
    fun placements(index: Int): List<Placement> = pages[index]

    /** The measured heights of the lines of the document's block [block]; a picture is one line. */
    internal fun lineHeights(block: Int): List<Float> = blocks.getValue(block).slab.lines

    fun footer(index: Int): Footer {
        val number = "${index + 1} / $count"
        val room = size.textWidth - Style.muted.measureText(number) - FOOTER_GAP
        val oneLine = title.replace(Regex("\\s+"), " ").trim()
        return Footer(TextUtils.ellipsize(oneLine, Style.muted, room, TextUtils.TruncateAt.END).toString(), number)
    }

    /**
     * Draws page [index] on [canvas], whose units are points and whose origin is the page's top left corner; the
     * pictures decoded for it are recycled before this returns.
     */
    fun draw(canvas: Canvas, index: Int) {
        val held = mutableListOf<Bitmap>()
        try {
            draw(canvas, index, held)
        } finally {
            held.forEach(Bitmap::recycle)
        }
    }

    /** Draws page [index] on [canvas], adding to [held] each picture decoded for it, for the caller to recycle. */
    private fun draw(canvas: Canvas, index: Int, held: MutableList<Bitmap>) {
        canvas.drawColor(Color.WHITE)
        val left = size.margin
        for (placement in pages[index]) {
            blocks.getValue(placement.block).draw(canvas, placement.from, placement.to, left, size.margin + placement.top, held)
        }
        val footer = footer(index)
        val baseline = size.height - size.margin - Style.muted.fontMetrics.descent
        canvas.drawText(footer.title, left, baseline, Style.muted)
        canvas.drawText(footer.number, size.width - size.margin - Style.muted.measureText(footer.number), baseline, Style.muted)
    }

    /**
     * Writes [which] of the pages (by index, all by default) to [out] as a PDF, one PDF page each at this page size,
     * and returns how many it wrote. [out] is left open.
     */
    fun writePdf(out: OutputStream, which: List<Int> = (0 until count).toList()): Int {
        val pdf = PdfDocument()
        try {
            writePages(which) { number, draw ->
                val info = PdfDocument.PageInfo.Builder(size.width.roundToInt(), size.height.roundToInt(), number).create()
                val page = pdf.startPage(info)
                draw(page.canvas)
                pdf.finishPage(page)
            }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
        return which.size
    }

    /**
     * Draws [which] of the pages one after another: [page] is given each one's number (from 1) and a drawing of it to
     * run on its canvas, and the pictures decoded for that page are recycled once [page] returns — after a PDF page is
     * finished. The platform's PdfDocument records a finished page as a picture holding its own reference to the pixels
     * drawn on it, so recycling them then is safe; it lets them go itself when the document is written and closed.
     */
    internal fun writePages(which: List<Int>, page: (number: Int, draw: (Canvas) -> Unit) -> Unit) {
        which.forEachIndexed { written, index ->
            val held = mutableListOf<Bitmap>()
            try {
                page(written + 1) { canvas -> draw(canvas, index, held) }
            } finally {
                held.forEach(Bitmap::recycle)
            }
        }
    }

    companion object {
        /**
         * [document] laid out on pages of [size]: each block measured, a picture that cannot be read left out. Of the
         * pictures only their sizes are read; [pictures] decodes them when their pages are drawn.
         */
        fun layOut(
            document: PrintDocument,
            size: PageSize = PageSize.A4,
            pictures: PictureDecoder = PictureDecoder.Platform,
        ): PrintPages {
            val laid = document.blocks.mapIndexedNotNull { index, block -> measure(block, size, pictures)?.let { index to it } }
            val footerHeight = Style.muted.fontMetrics.let { it.descent - it.ascent } + FOOTER_GAP
            val bodyHeight = size.height - 2 * size.margin - footerHeight
            val pages = paginate(laid.map { it.second.slab }, bodyHeight)
                .map { page -> page.map { it.copy(block = laid[it.block].first) } }
            return PrintPages(size, document.title, laid.toMap(), pages, bodyHeight)
        }

        private const val FOOTER_GAP = 12f
    }
}

/** The page's type: sizes in points, black on white, the muted grey well above 4.5:1 on white. */
private object Style {
    private val bold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private const val GREY = 0xFF595959.toInt()

    fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, struck: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            typeface = if (bold) this@Style.bold else Typeface.SANS_SERIF
            this.color = color
            isStrikeThruText = struck
        }

    val title = paint(20f, bold = true)
    val heading = paint(14f, bold = true)
    val body = paint(11f)
    val struck = paint(11f, struck = true)
    val muted = paint(9.5f, color = GREY)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = Color.BLACK
    }
}

private const val BULLET_INDENT = 14f
private const val BOX_INDENT = 17f
private const val BOX_SIDE = 8.5f

/** [block] measured for pages of [size]; null for a picture that cannot be read. */
private fun measure(block: PrintBlock, size: PageSize, pictures: PictureDecoder): Laid? {
    val width = size.textWidth
    return when (block) {
        is PrintBlock.Title -> TextLaid(16f, keep = true, listOf(Part(block.text, Style.title, width, 0f)))
        is PrintBlock.Heading -> TextLaid(14f, keep = true, listOf(Part(block.text, Style.heading, width, 0f)))
        is PrintBlock.Paragraph -> TextLaid(6f, parts = listOf(Part(block.text, Style.body, width, 0f)))
        is PrintBlock.Muted -> TextLaid(3f, parts = listOf(Part(block.text, Style.muted, width, 0f)))
        is PrintBlock.Bullet -> TextLaid(3f, parts = listOf(Part(block.text, Style.body, width, BULLET_INDENT))) { canvas, x, baseline ->
            canvas.drawText("•", x + 3f, baseline, Style.body)
        }
        is PrintBlock.Numbered -> {
            val number = "${block.number}."
            val indent = maxOf(Style.body.measureText("00."), Style.body.measureText(number)) + 6f
            TextLaid(6f, parts = listOf(Part(block.text, Style.body, width, indent))) { canvas, x, baseline ->
                canvas.drawText(number, x + indent - 6f - Style.body.measureText(number), baseline, Style.body)
            }
        }
        is PrintBlock.Box -> TextLaid(
            5f,
            whole = true,
            parts = listOfNotNull(
                Part(block.text, if (block.ticked) Style.struck else Style.body, width, BOX_INDENT),
                block.note?.let { Part(it, Style.muted, width, BOX_INDENT) },
            ),
        ) { canvas, x, baseline -> drawBox(canvas, x + 1f, baseline, block.ticked) }
        is PrintBlock.Picture -> PictureLaid.read(block, size, pictures)
        PrintBlock.PageBreak -> BreakLaid
    }
}

private fun drawBox(canvas: Canvas, x: Float, baseline: Float, ticked: Boolean) {
    val box = RectF(x, baseline - BOX_SIDE, x + BOX_SIDE, baseline)
    canvas.drawRect(box, Style.line)
    if (ticked) {
        val tick = Path().apply {
            moveTo(box.left + 1.8f, box.centerY())
            lineTo(box.left + 3.6f, box.bottom - 1.8f)
            lineTo(box.right - 1.5f, box.top + 1.8f)
        }
        canvas.drawPath(tick, Style.line)
    }
}

/**
 * A block measured: how pagination sees it, and how lines [from] until [to] of it are drawn at [left], [top]; a picture
 * decoded to draw it is added to [held].
 */
private sealed class Laid {
    abstract val slab: Slab

    abstract fun draw(canvas: Canvas, from: Int, to: Int, left: Float, top: Float, held: MutableList<Bitmap>)
}

/** A page break: no lines, nothing drawn; what follows begins a page. */
private object BreakLaid : Laid() {
    override val slab = Slab(0f, emptyList(), newPage = true)

    override fun draw(canvas: Canvas, from: Int, to: Int, left: Float, top: Float, held: MutableList<Bitmap>) = Unit
}

/** A stretch of text wrapped to [width] less [indent], set [indent] in from the left. */
private class Part(text: String, paint: TextPaint, width: Float, val indent: Float) {
    val layout: StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, (width - indent).toInt().coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()
}

/** Text blocks: [parts] stacked, their lines the block's lines; a [marker] drawn at the first line's baseline. */
private class TextLaid(
    space: Float,
    keep: Boolean = false,
    val parts: List<Part>,
    whole: Boolean = false,
    val marker: ((Canvas, Float, Float) -> Unit)? = null,
) : Laid() {
    /** Each of the block's lines as its part and its line within that part. */
    private val lines = parts.flatMapIndexed { part, it -> (0 until it.layout.lineCount).map { line -> part to line } }

    override val slab = Slab(
        space,
        lines.map { (part, line) -> parts[part].layout.let { (it.getLineTop(line + 1) - it.getLineTop(line)).toFloat() } },
        keepWithNext = keep,
        whole = whole,
    )

    override fun draw(canvas: Canvas, from: Int, to: Int, left: Float, top: Float, held: MutableList<Bitmap>) {
        var y = top
        var k = from
        while (k < to) {
            val (part, first) = lines[k]
            var end = k
            while (end < to && lines[end].first == part) end++
            val layout = parts[part].layout
            val lineTop = layout.getLineTop(first).toFloat()
            val lineBottom = layout.getLineTop(first + end - k).toFloat()
            canvas.save()
            canvas.translate(left + parts[part].indent, y - lineTop)
            canvas.clipRect(-1f, lineTop, layout.width + 1f, lineBottom)
            layout.draw(canvas)
            canvas.restore()
            if (k == 0) marker?.invoke(canvas, left, y + layout.getLineBaseline(0))
            y += lineBottom - lineTop
            k = end
        }
    }
}

/** How finely a picture is printed: its pixels per inch at the size it is drawn. */
internal const val PICTURE_DPI = 150

/**
 * A picture of [file], turned by [orientation] and drawn centred at [drawn] size: decoded only while its page is drawn,
 * at [PICTURE_DPI] of that size; one that cannot be decoded then leaves its space empty.
 */
private class PictureLaid(
    val file: File,
    val orientation: Orientation,
    val drawn: Points,
    textWidth: Float,
    val decoder: PictureDecoder,
) : Laid() {
    private val inset = (textWidth - drawn.width) / 2

    /** The drawn size as the picture is stored, before it is turned. */
    private val stored = if (orientation.swapsSides) Points(drawn.height, drawn.width) else drawn

    override val slab = Slab(8f, listOf(drawn.height), whole = true)

    override fun draw(canvas: Canvas, from: Int, to: Int, left: Float, top: Float, held: MutableList<Bitmap>) {
        val bitmap = try {
            decoder.decode(file, pixels(stored.width), pixels(stored.height))
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: RuntimeException) {
            null
        } ?: return
        held += bitmap
        val (w, h) = stored
        canvas.save()
        canvas.translate(left + inset + drawn.width / 2, top + drawn.height / 2)
        canvas.rotate(orientation.degrees.toFloat())
        if (orientation.mirrored) canvas.scale(-1f, 1f)
        canvas.drawBitmap(bitmap, null, RectF(-w / 2, -h / 2, w / 2, h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    companion object {
        /** The pixels [points] take at [PICTURE_DPI]. */
        fun pixels(points: Float): Int = ceil(points * PICTURE_DPI / 72f).toInt().coerceAtLeast(1)

        /** The picture of [block] measured from its size and EXIF orientation alone; null when they cannot be read. */
        fun read(block: PrintBlock.Picture, size: PageSize, decoder: PictureDecoder): PictureLaid? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(block.file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                val orientation = exifOrientation { block.file.inputStream() }
                val (w, h) = if (orientation.swapsSides) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
                PictureLaid(block.file, orientation, pictureSize(w, h, size.textWidth, size.height / 3), size.textWidth, decoder)
            }
        } catch (e: RuntimeException) {
            null
        }
    }
}
