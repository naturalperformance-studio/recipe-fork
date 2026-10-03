package app.recipefork.app.media

import java.io.File
import java.nio.ByteBuffer

/**
 * The boxes of an MP4 file outside its media data, read as ISO/IEC 14496-12 lays them out: what a test reads to see
 * what a file says of itself beyond its picture (MEDIA-003).
 */
class Mp4Boxes(file: File) {
    /** One box: its four-character type, where it sits (its parents' types, outermost first) and its contents. */
    class Box(val type: String, val path: List<String>, val contents: ByteBuffer)

    /** Every box of the file, depth first, the media data itself not entered. */
    val all: List<Box>

    init {
        val bytes = ByteBuffer.wrap(file.readBytes())
        all = mutableListOf<Box>().also { read(bytes, emptyList(), it) }
    }

    fun types(): Set<String> = all.map { it.type }.toSet()

    fun first(type: String): Box = all.first { it.type == type }

    /** The contents of the first box of [type], read as Latin-1 text. */
    fun text(type: String): String {
        val contents = first(type).contents.duplicate()
        return String(ByteArray(contents.remaining()).also { contents.get(it) }, Charsets.ISO_8859_1)
    }

    /** The creation time a movie header states, in seconds since 1904. */
    fun creationTime(): Long {
        val mvhd = first("mvhd").contents.duplicate()
        val version = mvhd.get().toInt()
        mvhd.position(4)
        return if (version == 1) mvhd.long else mvhd.int.toLong() and 0xffffffffL
    }

    private fun read(buffer: ByteBuffer, path: List<String>, into: MutableList<Box>) {
        while (buffer.remaining() >= 8) {
            val start = buffer.position()
            var size = buffer.int.toLong() and 0xffffffffL
            val type = String(ByteArray(4).also { buffer.get(it) }, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                size = buffer.long
                header = 16
            } else if (size == 0L) {
                size = (buffer.limit() - start).toLong()
            }
            if (size < header || start + size > buffer.limit()) return
            val contents = buffer.duplicate().apply {
                position(start + header)
                limit((start + size).toInt())
            }.slice()
            into += Box(type, path, contents)
            when (type) {
                in CONTAINERS -> read(contents.duplicate(), path + type, into)
                // A full box: version and flags come before its children.
                "meta" -> read(contents.duplicate().apply { position(4) }.slice(), path + type, into)
            }
            buffer.position((start + size).toInt())
        }
    }

    companion object {
        private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl", "udta", "edts", "dinf", "ilst", "mvex", "moof", "traf")

        /** The boxes that carry what a file says of where, when and on what it was shot. */
        val IDENTIFYING = setOf("udta", "meta", "keys", "ilst", "©xyz", "©mak", "©mod", "©day", "loci", "uuid", "XMP_")
    }
}
