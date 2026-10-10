package app.recipefork.app.file

import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.LaterRecipeFile
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.file.UnreadableRecipeFile
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.VideoLimits
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Why a file was refused (FLOW-064 (5)); the person is told which. */
enum class Refusal {
    /** It could not be opened or read at all. */
    CANNOT_OPEN,

    /** It is no ZIP, or a ZIP holding no `recipe.json`. */
    NOT_A_RECIPE_FILE,

    /** Its document is of a later format: written by a later version of the application. */
    LATER_VERSION,

    /** It is a recipe file, but damaged: it does not parse, or fails one of the importer's checks. */
    DAMAGED,

    /** Its recipe breaks the rules a sending must pass (DOM-012, DOM-016, DOM-017). */
    BREAKS_RULES,

    /** A clip it holds is longer than MEDIA-004 accepts (MEDIA-007); the person is told the limit. */
    CLIP_TOO_LONG,
}

class RefusedFile(val refusal: Refusal, message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * How much an importer inflates: per entry at the limits of MEDIA-001 to MEDIA-009, and over the whole archive at the
 * sum those limits allow for one recipe — a cover photograph and a video on each of the most steps a recipe holds.
 * How many entries it walks: as many as one recipe can need — the document, the media directory, and an item for each
 * media slot the document can name (the cover and one per step).
 */
data class ArchiveLimits(
    val document: Long = MIB,
    val photo: Long = MIB,
    val video: Long = VideoLimits.MAX_BYTES,
    val archive: Long = MIB + MIB + RecipeFileCheck.MAX_STEPS * VideoLimits.MAX_BYTES,
    val entries: Int = 2 + 1 + RecipeFileCheck.MAX_STEPS,
) {
    fun of(kind: MediaKind): Long = when (kind) {
        MediaKind.PHOTO -> photo
        MediaKind.VIDEO -> video
    }

    companion object {
        const val MIB = 1024L * 1024
    }
}

/**
 * Reads a recipe file trusting nothing in it (decisions/20260928-recipe-file-is-a-zip.md). The archive is read as a
 * stream, twice: once for `recipe.json`, which is decoded and checked before anything else is believed, and once for
 * the `media/` entries that document names, each streamed straight to a file the caller names. Every other entry is
 * passed over unread but counted. An entry whose name is absolute or climbs with `..` refuses the file, as do more
 * entries than a recipe can need; inflation stops at the caps of [ArchiveLimits], whatever sizes the archive declares;
 * a media item's type is told by its leading bytes, never by its name. [alive] is asked between every read of the
 * archive, and stops it by throwing — when the import is cancelled.
 */
object RecipeArchiveReader {
    /**
     * The archive's document, decoded and checked; refuses the file with [RefusedFile] when it cannot be believed. An item
     * of equipment a format before 5 wrote as its text names the entry [entryOf] finds for it ([RecipeFile.decode]).
     */
    fun document(
        open: () -> InputStream,
        limits: ArchiveLimits = ArchiveLimits(),
        alive: () -> Unit = {},
        entryOf: (String) -> Long? = { null },
    ): RecipeFileContent {
        var text: String? = null
        val names = mutableListOf<String>()
        // Walked to its end, which the entry cap bounds: a second document anywhere refuses the file, as does a media
        // item the document names that the archive does not hold once.
        each(open, limits, alive, done = { false }) { name, zip, budget ->
            names += name
            if (name != RecipeFile.DOCUMENT) return@each drain(zip, budget, alive)
            if (text != null) throw RefusedFile(Refusal.DAMAGED, "two documents")
            val bytes = ByteArrayOutputStream()
            val read = copy(zip, bytes, minOf(limits.document, budget), alive)
            text = utf8(bytes.toByteArray())
            read
        }
        val document = text ?: throw RefusedFile(Refusal.NOT_A_RECIPE_FILE, if (names.isEmpty()) "not a ZIP" else "no document")
        val content = try {
            RecipeFile.decode(document, entryOf)
        } catch (e: LaterRecipeFile) {
            throw RefusedFile(Refusal.LATER_VERSION, e.message ?: "later", e)
        } catch (e: UnreadableRecipeFile) {
            throw RefusedFile(Refusal.DAMAGED, e.message ?: "does not parse", e)
        }
        val problems = RecipeFileCheck.problems(content)
        if (problems.isNotEmpty()) throw RefusedFile(Refusal.BREAKS_RULES, problems.joinToString("; "))
        content.media.forEach { item ->
            when (names.count { it == item.entry }) {
                0 -> throw RefusedFile(Refusal.DAMAGED, "missing ${item.entry}")
                1 -> Unit
                else -> throw RefusedFile(Refusal.DAMAGED, "${item.entry} given twice")
            }
        }
        return content
    }

    /**
     * Streams each media item [content] names to the file [target] gives it, refusing the file when an item is
     * missing, given twice, over its cap, or not of its kind by its leading bytes. What was written before a refusal
     * is the caller's to delete. Reading stops once every item named has been read.
     */
    fun media(
        open: () -> InputStream,
        content: RecipeFileContent,
        target: (FileMedia) -> File,
        limits: ArchiveLimits = ArchiveLimits(),
        alive: () -> Unit = {},
    ) {
        if (content.media.isEmpty()) return
        val named = content.media.associateBy { it.entry }
        val read = mutableSetOf<String>()
        each(open, limits, alive, done = { read.size == named.size }) { name, zip, budget ->
            val item = named[name] ?: return@each drain(zip, budget, alive)
            if (!read.add(name)) throw RefusedFile(Refusal.DAMAGED, "$name given twice")
            val file = target(item)
            val head = ByteArray(HEAD)
            val written = file.outputStream().buffered().use { out ->
                copy(zip, HeadKeeper(out, head), minOf(limits.of(item.kind), budget), alive)
            }
            if (!isOfKind(head, written, item.kind)) throw RefusedFile(Refusal.DAMAGED, "$name is no ${item.kind}")
            written
        }
        val missing = named.keys - read
        if (missing.isNotEmpty()) throw RefusedFile(Refusal.DAMAGED, "missing $missing")
    }

    /**
     * Refuses a clip written to [file] that MEDIA-007 does not accept, its duration read by [duration]: one longer than
     * [VideoLimits.MAX_DURATION] for its length, and one whose duration cannot be read as damaged.
     */
    fun checkClip(file: File, duration: (File) -> kotlin.time.Duration?) {
        val read = duration(file)
        val problem = RecipeFileCheck.clipProblem(read) ?: return
        throw RefusedFile(if (read != null && read > VideoLimits.MAX_DURATION) Refusal.CLIP_TOO_LONG else Refusal.DAMAGED, problem)
    }

    /**
     * Walks the entries of the archive until [done], refusing an unsafe name and more entries than [limits] allow, and
     * hands each to [entry] with what is left of the archive's budget; [entry] returns how much it inflated.
     */
    private fun each(
        open: () -> InputStream,
        limits: ArchiveLimits,
        alive: () -> Unit,
        done: () -> Boolean,
        entry: (String, ZipInputStream, Long) -> Long,
    ) {
        alive()
        val stream = try {
            open()
        } catch (e: IOException) {
            throw RefusedFile(Refusal.CANNOT_OPEN, "cannot open", e)
        } catch (e: SecurityException) {
            throw RefusedFile(Refusal.CANNOT_OPEN, "cannot open", e)
        }
        try {
            ZipInputStream(stream.buffered()).use { zip ->
                var inflated = 0L
                var entries = 0
                while (!done()) {
                    alive()
                    val next = zip.nextEntry ?: break
                    if (++entries > limits.entries) throw RefusedFile(Refusal.DAMAGED, "more entries than a recipe needs")
                    if (!RecipeFileCheck.isSafeEntryName(next.name)) throw RefusedFile(Refusal.DAMAGED, "unsafe entry name")
                    inflated += entry(next.name, zip, limits.archive - inflated)
                    if (inflated > limits.archive) throw RefusedFile(Refusal.DAMAGED, "archive over its cap")
                }
            }
        } catch (e: RefusedFile) {
            throw e
        } catch (e: ZipException) {
            throw RefusedFile(Refusal.DAMAGED, "damaged archive", e)
        } catch (e: EOFException) {
            throw RefusedFile(Refusal.DAMAGED, "truncated archive", e)
        } catch (e: IllegalArgumentException) {
            // A name that is not text in the archive's charset.
            throw RefusedFile(Refusal.DAMAGED, "damaged archive", e)
        } catch (e: IOException) {
            throw RefusedFile(Refusal.CANNOT_OPEN, "cannot read", e)
        }
    }

    /** Reads the current entry to its end, discarding it, and refuses it once it inflates past [cap]. */
    private fun drain(zip: InputStream, cap: Long, alive: () -> Unit): Long = copy(zip, Nowhere, cap, alive)

    /**
     * Copies the current entry to [out] and returns its size; refuses it the moment it passes [cap], and asks [alive]
     * before every read.
     */
    private fun copy(zip: InputStream, out: OutputStream, cap: Long, alive: () -> Unit): Long {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            alive()
            val read = zip.read(buffer)
            if (read < 0) return total
            total += read
            if (total > cap) throw RefusedFile(Refusal.DAMAGED, "entry over its cap")
            out.write(buffer, 0, read)
        }
    }

    private fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        throw RefusedFile(Refusal.DAMAGED, "the document is not UTF-8", e)
    }

    /**
     * Whether [head], the first [size] bytes of an item, begin a file of [kind]: a JPEG, PNG or WebP photograph, or an
     * ISO base media (MP4) video.
     */
    fun isOfKind(head: ByteArray, size: Long, kind: MediaKind): Boolean {
        fun at(offset: Int, vararg bytes: Int) =
            size >= offset + bytes.size && bytes.indices.all { head[offset + it] == bytes[it].toByte() }
        fun ascii(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())
        return when (kind) {
            MediaKind.PHOTO -> at(0, 0xFF, 0xD8, 0xFF) ||
                at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ||
                (ascii(0, "RIFF") && ascii(8, "WEBP"))
            MediaKind.VIDEO -> ascii(4, "ftyp")
        }
    }

    /** Discards what it is given. */
    private object Nowhere : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    /** Passes everything on to [out], keeping the first bytes in [head]. */
    private class HeadKeeper(private val out: OutputStream, private val head: ByteArray) : OutputStream() {
        private var kept = 0

        override fun write(b: Int) {
            if (kept < head.size) head[kept++] = b.toByte()
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val keep = minOf(len, head.size - kept)
            if (keep > 0) {
                System.arraycopy(b, off, head, kept, keep)
                kept += keep
            }
            out.write(b, off, len)
        }

        override fun flush() = out.flush()
    }

    private const val HEAD = 12
    private const val BUFFER = 64 * 1024
}
