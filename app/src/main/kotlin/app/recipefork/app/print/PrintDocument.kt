package app.recipefork.app.print

import java.io.File

/**
 * A document to put on paper or in a PDF (FLOW-075): its [title], which every page's footer names, and its [blocks],
 * laid out top to bottom by [PrintPages.layOut].
 */
data class PrintDocument(val title: String, val blocks: List<PrintBlock>)

/** One block of a [PrintDocument]. Text blocks wrap to the page's text width. */
sealed interface PrintBlock {
    /** The document's own title in large type at the top of its first page (or under its cover picture). */
    data class Title(val text: String) : PrintBlock

    /** A section heading; it is never left alone at a page's foot, but kept with the first line after it. */
    data class Heading(val text: String) : PrintBlock

    data class Paragraph(val text: String) : PrintBlock

    /** A paragraph in smaller grey type. */
    data class Muted(val text: String) : PrintBlock

    data class Bullet(val text: String) : PrintBlock

    /** A numbered line: "[number]." hangs left of the text, which wraps under itself. */
    data class Numbered(val number: Int, val text: String) : PrintBlock

    /** A line with a box before it, empty or [ticked] (a ticked line struck through), and an optional muted [note]. */
    data class Box(val text: String, val ticked: Boolean = false, val note: String? = null) : PrintBlock

    /** A picture kept on the device; one that cannot be read is left out. */
    data class Picture(val file: File) : PrintBlock
}
