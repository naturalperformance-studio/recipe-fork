package app.recipefork.app.file

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.recipe.EquipmentNames
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.media.PhotoStore
import app.recipefork.core.file.EntryLabels
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.Normalizer

/**
 * Export of a recipe to a file (FLOW-063): the file is written to the application's cache and handed to the
 * operating system's share and save sheet through a [FileProvider]; where it goes is the person's choice, and the
 * product sends it nowhere itself. Export only reads the recipe: it changes nothing in it, and freezes nothing.
 */
object RecipeExport {
    /** The directory under the cache that the [FileProvider] shares (res/xml/file_paths.xml). */
    internal const val DIRECTORY = "exports"

    fun authority(context: Context) = context.packageName + ".files"

    /**
     * Writes the file of the recipe [id] of the active book, with every photograph the device holds for it, and returns
     * its shareable address, or null when the book holds no such recipe. No account exists on the device yet, so no recipe is an account's own and the file names
     * an author only where the recipe states one (FLOW-063 (3)). [progress] is told the share of the file written, of
     * the size its document and its media make. Each item of equipment an entry names carries the entry's labels from
     * the [equipment] dictionary, where it holds them.
     */
    suspend fun write(
        context: Context,
        recipes: RecipeRepository,
        id: RecipeId,
        equipment: EquipmentSource = equipmentDictionaryOf(context),
        progress: (Float) -> Unit = {},
    ): Uri? {
        val book = recipes.inOpenBook()
        val recipe = book.get(id) ?: return null
        val store = PhotoStore(context.filesDir)
        val name = book.bookName()
        val labels = equipmentLabels(listOf(recipe), equipment)
        val content = withContext(Dispatchers.IO) { fileContent(book, recipe, labels) { store.file(name, it).isFile } }
        val file = withContext(Dispatchers.IO) {
            // Only the file of this export is kept: those of earlier exports, and of recipes since deleted, go first.
            val exports = File(context.cacheDir, DIRECTORY)
            exports.deleteRecursively()
            val directory = File(exports, id.value.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { FALLBACK_NAME })
            if (!directory.mkdirs()) throw IOException("cannot make $directory")
            File(directory, fileName(recipe.title)).also { file ->
                val document = RecipeFile.encode(content).toByteArray(Charsets.UTF_8).size
                val total = document + content.media.sumOf { store.file(name, it.mediaId).length() }
                file.outputStream().buffered().use { out ->
                    RecipeArchive.write(content, counting(out, total.coerceAtLeast(1), progress)) { store.file(name, it.mediaId) }
                }
                // The archive compresses what it can, so what is written falls short of the total: written is whole.
                progress(1f)
            }
        }
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /**
     * The labels of the dictionary entries the equipment of [recipes] names, as [equipment] holds them. An entry whose
     * labels cannot be read is written without them; the file reads the same on a device that holds it.
     */
    internal suspend fun equipmentLabels(recipes: List<Recipe>, equipment: EquipmentSource): (Long) -> EntryLabels? {
        if (recipes.none { recipe -> recipe.equipment.any { it.dictionaryEntry != null } }) return { null }
        val entries = try {
            equipment.equipment().associateBy { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        return { entries[it]?.let(EquipmentNames::labelsOf) }
    }

    /**
     * The file's content for [recipe] of [book], as an export of it writes it (FLOW-063): no account exists on the
     * device yet, so the file names an author only where the recipe states one; its parent is named as [book] knows
     * it; its media are those the device holds ([held]).
     */
    internal suspend fun fileContent(
        book: RecipeRepository,
        recipe: Recipe,
        labels: (Long) -> EntryLabels?,
        held: (String) -> Boolean,
    ): RecipeFileContent {
        val parent = recipe.parent?.let { book.get(it) }
        return RecipeFileContent.of(recipe, parentInBook = parent, ownerNickname = null, equipmentLabels = labels)
            .copy(media = fileMedia(recipe.media, held))
    }

    /** [out], telling [progress] the share of [total] bytes written through it. */
    internal fun counting(out: java.io.OutputStream, total: Long, progress: (Float) -> Unit) = object : java.io.FilterOutputStream(out) {
        private var written = 0L
        override fun write(b: Int) {
            out.write(b)
            told(1)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            told(len)
        }
        private fun told(bytes: Int) {
            written += bytes
            progress((written.toFloat() / total).coerceAtMost(1f))
        }
    }

    /**
     * The document's entries for the recipe's [media] (FLOW-063): every item whose file the device holds — [held] says
     * which — each under `media/` by its identity. An item whose file is gone is left out, so the document never names
     * an entry the archive does not hold.
     */
    fun fileMedia(media: List<Media>, held: (String) -> Boolean): List<FileMedia> = media
        .map { FileMedia(it.mediaId, it.kind, it.attachment, RecipeFile.MEDIA_DIRECTORY + it.mediaId) }
        .filter { RecipeFileCheck.isMediaEntry(it.entry) && held(it.mediaId) }

    /**
     * Writes the file of the recipe [id], telling [progress] how far, and opens the share and save sheet on it, headed
     * [sheetTitle]. Returns false
     * when no file could be written or no sheet opened, so that the person can be told.
     */
    suspend fun share(
        context: Context,
        recipes: RecipeRepository,
        id: RecipeId,
        sheetTitle: CharSequence,
        progress: (Float) -> Unit = {},
    ): Boolean =
        try {
            val uri = write(context, recipes, id, progress = progress)
            if (uri != null) open(context, shareSheet(context, uri, sheetTitle))
            uri != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    /**
     * The sheet that lets the person save or send the file at [uri]; [title] heads it. The application itself is not
     * offered in it: the recipe is in its book already.
     */
    fun shareSheet(context: Context, uri: Uri, title: CharSequence): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(RecipeFile.MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, title)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, OpenRecipeFileActivity::class.java)))
    }

    /** Opens [sheet] from [context]; outside an activity, in a task of its own. */
    fun open(context: Context, sheet: Intent) {
        if (context.findActivity() == null) sheet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(sheet)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * The file's name, in ASCII so that a messenger keeps its extension (Telegram drops it from a Cyrillic name): the
     * recipe's title, composed (NFC, so that и with a combining breve is й), with Cyrillic letters spelt in Latin ones
     * and accents taken off, every run of anything but a letter or a digit made one hyphen, at most [MAX_NAME]
     * characters; a title whose letters are all upper case is spelt all in upper case; `recipe` when nothing of the
     * title is left; then the product's extension.
     */
    fun fileName(title: String): String = safeFileName(title, RecipeFile.EXTENSION, FALLBACK_NAME)

    /** [title] made a file's name as [fileName] makes it, with [extension]; [fallback] when nothing of it is left. */
    internal fun safeFileName(title: String, extension: String, fallback: String): String {
        val composed = Normalizer.normalize(title, Normalizer.Form.NFC)
        val spelt = composed.map { CYRILLIC_IN_LATIN[it] ?: it.toString() }.joinToString("")
        val letters = composed.filter { it.isLetter() }
        val cased = if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) spelt.uppercase() else spelt
        val latin = Normalizer.normalize(cased, Normalizer.Form.NFD)
        val cleaned = latin.replace(Regex("\\p{M}+"), "").replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(MAX_NAME).trimEnd('-')
        return (cleaned.ifEmpty { fallback }) + "." + extension
    }

    /**
     * Russian letters in Latin ones, as passports and road signs commonly spell them, and the Ukrainian and Belarusian
     * letters Russian lacks.
     */
    private val CYRILLIC_IN_LATIN: Map<Char, String> = run {
        val lower = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "zh",
            'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
            'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
            'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu",
            'я' to "ya", 'і' to "i", 'ї' to "yi", 'є' to "ye", 'ґ' to "g", 'ў' to "w",
        )
        lower + lower.map { (letter, latin) -> letter.uppercaseChar() to latin.replaceFirstChar { it.uppercaseChar() } }
    }

    private const val MAX_NAME = 80
    private const val FALLBACK_NAME = "recipe"
}

/**
 * The device's equipment dictionary as [context]'s application holds it, seeded before it is read, which a recipe file's
 * equipment is named against on export and import; none where the application holds no dictionaries.
 */
fun equipmentDictionaryOf(context: Context): EquipmentSource {
    val application = context.applicationContext as? RecipeForkApplication ?: return EquipmentSource { emptyList() }
    return EquipmentSource { application.container.dictionaries.equipment() }
}
