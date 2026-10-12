package app.recipefork.app.file

import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.CollectionFileCheck
import app.recipefork.core.file.CollectionFileContent
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.LaterCollectionFile
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.file.UnreadableRecipeFile
import app.recipefork.core.scaling.Rational
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * How much a collection file's reader inflates, walks, keeps and writes: its document up to [document]; each recipe's
 * folder as a recipe file is held to [recipe]; what the first reading keeps of the recipes' folders to [documents] —
 * each document decoded as it is read and counted at its text or at what it holds decoded, whichever is more, and each
 * media entry's name and leading bytes; an entry's name to [name] characters; the whole archive to the document and as
 * many folders as the document lists; and the media the second reading writes, together, to [media] — so a file
 * listing few recipes cannot be made to hold more, a small file to fill memory, nor a crafted one to fill the disk.
 */
data class CollectionLimits(
    val document: Long = ArchiveLimits.MIB,
    val recipe: ArchiveLimits = ArchiveLimits(),
    val recipes: Int = CollectionFileCheck.MAX_RECIPES,
    val documents: Long = 32 * ArchiveLimits.MIB,
    val name: Int = 255,
    val media: Long = 2 * 1024 * ArchiveLimits.MIB,
) {
    internal fun entries(recipes: Int): Long = 1L + recipes.toLong() * recipe.entries
    internal fun archive(recipes: Int): Long = document + recipes.toLong() * recipe.archive
}

/** A collection file read and believed: its [document], and each recipe it lists, in its order, checked. */
class ReadCollectionFile internal constructor(
    val document: CollectionFileContent,
    val recipes: List<RecipeFileContent>,
)

/**
 * Reads a collection file ([CollectionFile]) trusting nothing in it, as [RecipeArchiveReader] reads a recipe file: as
 * a stream, twice. The first reading writes nothing: `collection.json`, which must be the archive's first entry, is
 * decoded and checked ([CollectionFileCheck]) before anything else is read; then each recipe's folder is read as a
 * recipe file — its document decoded and checked as one the moment it is read ([RecipeArchiveReader.decoded]), only
 * what it states kept, each media item it names present once, within its cap and of its kind by its leading bytes. Of
 * a folder's other entries only a media entry's name and head are kept, and what is kept is counted against a cap.
 * The media the recipes name are held together to their cap once the first reading ends. Only a file believed whole
 * is read the second time, for the media of the recipes the caller takes ([media]), held to that cap again as they are
 * written. Every entry's name is checked, an absolute one or one climbing with `..`
 * refusing the file; entries outside the folders the document lists are passed over unread but counted. Inflation
 * stops at the caps of [CollectionLimits], whatever sizes the archive declares; [alive] is asked between every read.
 */
object CollectionArchiveReader {
    /** The file [open] opens, read and checked; refused with [RefusedFile] when anything of it cannot be believed. */
    fun read(
        open: () -> InputStream,
        limits: CollectionLimits = CollectionLimits(),
        alive: () -> Unit = {},
        entryOf: (String) -> Long? = { null },
    ): ReadCollectionFile {
        var document: CollectionFileContent? = null
        val folders = HashMap<String, Folder>()
        var kept = 0L
        fun keep(bytes: Long, refusal: String) {
            kept += bytes
            if (kept > limits.documents) throw RefusedFile(Refusal.DAMAGED, refusal)
        }
        RecipeArchiveReader.reading(open, alive) { zip ->
            val first = zip.nextEntry ?: throw RefusedFile(Refusal.NOT_A_RECIPE_FILE, "not a ZIP")
            if (first.name != CollectionFile.DOCUMENT) throw RefusedFile(Refusal.NOT_A_RECIPE_FILE, "no document first")
            val content = decode(text(zip, limits.document, alive), limits)
            document = content
            walk(zip, content, limits, alive, folders, done = { false }) { name, folder, budget ->
                val relative = name.removePrefix(folder.prefix)
                if (relative == RecipeFile.DOCUMENT) {
                    if (folder.content != null) throw RefusedFile(Refusal.DAMAGED, "two documents in ${folder.prefix}")
                    val left = limits.documents - kept
                    if (left <= 0) throw RefusedFile(Refusal.DAMAGED, DOCUMENTS)
                    val bytes = ByteArrayOutputStream()
                    val read = RecipeArchiveReader.copy(zip, bytes, minOf(limits.recipe.document, left, budget), alive)
                    // Decoded and checked at once: what it states is kept, never its text — counted at what it holds.
                    val recipe = RecipeArchiveReader.decoded(RecipeArchiveReader.utf8(bytes.toByteArray()), entryOf)
                    keep(maxOf(read, held(recipe)), DOCUMENTS)
                    folder.content = recipe
                    read
                } else if (RecipeFileCheck.isMediaEntry(relative)) {
                    // Only a media entry's name and head are kept; any other entry of the folder is passed over.
                    if (relative in folder.heads) throw RefusedFile(Refusal.DAMAGED, "${folder.prefix}$relative given twice")
                    keep(ENTRY + 2L * relative.length + RecipeArchiveReader.HEAD, "the recipes' media entries over their cap")
                    val head = ByteArray(RecipeArchiveReader.HEAD)
                    val cap = minOf(maxOf(limits.recipe.photo, limits.recipe.video), budget)
                    RecipeArchiveReader.copy(zip, RecipeArchiveReader.HeadKeeper(RecipeArchiveReader.Nowhere, head), cap, alive)
                        .also { folder.heads[relative] = head to it }
                } else {
                    RecipeArchiveReader.drain(zip, budget, alive)
                }
            }
        }
        val content = document ?: throw RefusedFile(Refusal.NOT_A_RECIPE_FILE, "no document")
        // The media the recipes name, together, are held to their cap before anything is written.
        var media = 0L
        val recipes = content.recipes.mapIndexed { place, id ->
            val prefix = CollectionFile.recipeFolder(place)
            val folder = folders[prefix] ?: throw RefusedFile(Refusal.DAMAGED, "no folder $prefix")
            val recipe = folder.content ?: throw RefusedFile(Refusal.DAMAGED, "no document in $prefix")
            if (recipe.id != id) throw RefusedFile(Refusal.DAMAGED, "${folder.prefix} holds another recipe")
            recipe.media.forEach { item ->
                val (head, size) = folder.heads[item.entry] ?: throw RefusedFile(Refusal.DAMAGED, "missing ${folder.prefix}${item.entry}")
                if (size > limits.recipe.of(item.kind)) throw RefusedFile(Refusal.DAMAGED, "${folder.prefix}${item.entry} over its cap")
                media += size
                if (media > limits.media) throw RefusedFile(Refusal.DAMAGED, MEDIA)
                if (!RecipeArchiveReader.isOfKind(head, size, item.kind)) {
                    throw RefusedFile(Refusal.DAMAGED, "${folder.prefix}${item.entry} is no ${item.kind}")
                }
            }
            recipe
        }
        return ReadCollectionFile(content, recipes)
    }

    /**
     * Streams each media item of the recipes at [places] of [file] (counted from 0) to the file [target] gives it,
     * reading the archive [open] opens anew — checked again as it is read, for a second reading need not give what the
     * first did. What was written before a refusal is the caller's to delete. Reading stops once every item is read.
     */
    fun media(
        open: () -> InputStream,
        file: ReadCollectionFile,
        places: Set<Int>,
        target: (place: Int, FileMedia) -> File,
        limits: CollectionLimits = CollectionLimits(),
        alive: () -> Unit = {},
    ) {
        val wanted = places.flatMap { place ->
            file.recipes[place].media.map { item -> CollectionFile.recipeFolder(place) + item.entry to (place to item) }
        }.toMap()
        if (wanted.isEmpty()) return
        val read = HashSet<String>()
        var written = 0L
        RecipeArchiveReader.reading(open, alive) { zip ->
            val first = zip.nextEntry
            if (first?.name != CollectionFile.DOCUMENT) throw RefusedFile(Refusal.DAMAGED, "no document first")
            RecipeArchiveReader.drain(zip, limits.document, alive)
            walk(zip, file.document, limits, alive, HashMap(), done = { read.size == wanted.size }) { name, _, budget ->
                val (place, item) = wanted[name] ?: return@walk RecipeArchiveReader.drain(zip, budget, alive)
                if (!read.add(name)) throw RefusedFile(Refusal.DAMAGED, "$name given twice")
                // The media together are held to their cap as they are written, whatever each item's own cap.
                val left = limits.media - written
                if (left <= 0) throw RefusedFile(Refusal.DAMAGED, MEDIA)
                val head = ByteArray(RecipeArchiveReader.HEAD)
                val size = target(place, item).outputStream().buffered().use { out ->
                    RecipeArchiveReader.copy(zip, RecipeArchiveReader.HeadKeeper(out, head), minOf(limits.recipe.of(item.kind), budget, left), alive)
                }
                written += size
                if (!RecipeArchiveReader.isOfKind(head, size, item.kind)) throw RefusedFile(Refusal.DAMAGED, "$name is no ${item.kind}")
                size
            }
        }
        val missing = wanted.keys - read
        if (missing.isNotEmpty()) throw RefusedFile(Refusal.DAMAGED, "missing $missing")
    }

    /** The document [text] states, decoded and checked. */
    private fun decode(text: String, limits: CollectionLimits): CollectionFileContent {
        val content = try {
            CollectionFile.decode(text)
        } catch (e: LaterCollectionFile) {
            throw RefusedFile(Refusal.LATER_VERSION, e.message ?: "later", e)
        } catch (e: UnreadableRecipeFile) {
            throw RefusedFile(Refusal.DAMAGED, e.message ?: "does not parse", e)
        }
        val problems = CollectionFileCheck.problems(content)
        if (problems.isNotEmpty()) throw RefusedFile(Refusal.DAMAGED, problems.joinToString("; "))
        if (content.recipes.size > limits.recipes) throw RefusedFile(Refusal.DAMAGED, "more recipes than a collection is read with")
        return content
    }

    /**
     * Walks the entries after the document until [done], refusing an unsafe name, a second document, and more entries or
     * more inflated than the archive of [document] may hold, a folder held to what a recipe file may; each entry of a
     * folder the document lists goes to [entry] with what is left of its budget, every other entry passed over unread.
     */
    private fun walk(
        zip: ZipInputStream,
        document: CollectionFileContent,
        limits: CollectionLimits,
        alive: () -> Unit,
        folders: MutableMap<String, Folder>,
        done: () -> Boolean,
        entry: (name: String, folder: Folder, budget: Long) -> Long,
    ) {
        val maxEntries = limits.entries(document.recipes.size)
        val maxInflated = limits.archive(document.recipes.size)
        var entries = 1L
        var inflated = 0L
        while (!done()) {
            alive()
            val name = zip.nextEntry?.name ?: break
            if (++entries > maxEntries) throw RefusedFile(Refusal.DAMAGED, "more entries than the collection needs")
            if (name.length > limits.name) throw RefusedFile(Refusal.DAMAGED, "an entry's name over ${limits.name} characters")
            if (!RecipeFileCheck.isSafeEntryName(name)) throw RefusedFile(Refusal.DAMAGED, "unsafe entry name")
            if (name == CollectionFile.DOCUMENT) throw RefusedFile(Refusal.DAMAGED, "two documents")
            val left = maxInflated - inflated
            val folder = placeOf(name, document.recipes.size)?.let { place ->
                CollectionFile.recipeFolder(place).let { prefix -> folders.getOrPut(prefix) { Folder(prefix) } }
            }
            val read = if (folder == null) {
                RecipeArchiveReader.drain(zip, left, alive)
            } else {
                if (++folder.entries > limits.recipe.entries) throw RefusedFile(Refusal.DAMAGED, "more entries than a recipe needs")
                entry(name, folder, minOf(left, limits.recipe.archive - folder.inflated)).also { folder.inflated += it }
            }
            inflated += read
            if (inflated > maxInflated) throw RefusedFile(Refusal.DAMAGED, "archive over its cap")
        }
    }

    /** The place (from 0) of the folder [name] lies in, of a document listing [recipes]; null outside every one. */
    private fun placeOf(name: String, recipes: Int): Int? {
        if (!name.startsWith(CollectionFile.RECIPES_DIRECTORY)) return null
        val end = name.indexOf('/', CollectionFile.RECIPES_DIRECTORY.length).takeIf { it > 0 } ?: return null
        val number = name.substring(CollectionFile.RECIPES_DIRECTORY.length, end)
        if (number.isEmpty() || number.length > 9 || !number.all { it in '0'..'9' } || number.startsWith("0")) return null
        return (number.toInt() - 1).takeIf { it in 0 until recipes }
    }

    private fun text(zip: InputStream, cap: Long, alive: () -> Unit): String {
        val bytes = ByteArrayOutputStream()
        RecipeArchiveReader.copy(zip, bytes, cap, alive)
        return RecipeArchiveReader.utf8(bytes.toByteArray())
    }

    private const val DOCUMENTS = "the recipes' documents over their cap"
    private const val MEDIA = "the media over their cap"

    /** What a kept media entry costs beside its name's characters and its head: its map's node, its pair, its size. */
    private const val ENTRY = 96L

    /**
     * A measure, in bytes, of what [content] holds once decoded — on the heap, not in its text: each object counted at
     * [OBJECT], each text at two bytes a character beyond its own, each tag at [TAG] (a boxed number in an ordered set),
     * each number of a quantity at [NUMBER]. It is a bound to count against a cap, not an exact size.
     */
    internal fun held(content: RecipeFileContent): Long {
        fun text(value: String?): Long = if (value == null) 0L else OBJECT + 2L * value.length
        fun number(value: Rational?): Long =
            if (value == null) 0L else OBJECT + 2 * NUMBER + (value.numerator.bitLength() + value.denominator.bitLength()) / 8L
        var total = 4 * OBJECT + text(content.id.value) + text(content.title) + text(content.description) + text(content.author)
        content.parent?.let { total += OBJECT + text(it.id.value) + text(it.title) + text(it.nickname) }
        total += content.tags.size * TAG
        content.equipment.forEach { total += OBJECT + text(it.qualifier) + text(it.freeTextName) }
        content.equipmentLabels.values.forEach { total += TAG + OBJECT + text(it.ru) + text(it.en) }
        content.ingredients.forEach {
            total += 2 * OBJECT + text(it.qualifier) + text(it.freeTextName) + text(it.enteredQuantity) + text(it.note) +
                number(it.quantity) + number(it.pieceWeight)
        }
        content.ingredientGroups.forEach { total += OBJECT + text(it.name) }
        content.steps.forEach { step ->
            total += 3 * OBJECT + text(step.text) + text(step.note)
            step.ingredientReferences.forEach { total += OBJECT + (it.share?.let { share -> OBJECT + number(share.amount) } ?: 0L) }
            step.timers.forEach { total += OBJECT + text(it.label) }
        }
        content.media.forEach { total += 2 * OBJECT + text(it.mediaId) + text(it.entry) }
        return total
    }

    private const val OBJECT = 32L
    private const val TAG = 72L
    private const val NUMBER = 48L

    /** What the first reading found in a recipe's folder: what its document states, and each media entry's head and size. */
    private class Folder(val prefix: String) {
        var content: RecipeFileContent? = null
        val heads = HashMap<String, Pair<ByteArray, Long>>()
        var entries = 0
        var inflated = 0L
    }
}
