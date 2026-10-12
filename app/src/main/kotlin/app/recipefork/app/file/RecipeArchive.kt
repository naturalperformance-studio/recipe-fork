package app.recipefork.app.file

import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a recipe file: a ZIP holding `recipe.json` and the `media/` directory (decisions/20260928-recipe-file-is-a-zip.md).
 * Each media item the document names is streamed into the directory as an entry of its own, under the name the
 * document gives it, its bytes as the device holds them: nothing is re-encoded on the way out.
 */
object RecipeArchive {
    /** Writes [content] to [out]; [file] gives the file each media item the document names is read from. */
    fun write(content: RecipeFileContent, out: OutputStream, file: (FileMedia) -> File = { error("no media expected") }) {
        ZipOutputStream(out).use { zip -> writeEntries(zip, content, folder = "", file) }
    }

    /**
     * Writes the entries of [content]'s recipe file into [zip], each under [folder] (empty, or a path ending in `/`):
     * the folder then holds exactly what the recipe's own file holds, its document naming its media relative to it.
     */
    fun writeEntries(zip: ZipOutputStream, content: RecipeFileContent, folder: String, file: (FileMedia) -> File) {
        require(folder.isEmpty() || folder.endsWith("/")) { "folder $folder" }
        zip.putNextEntry(ZipEntry(folder + RecipeFile.DOCUMENT))
        zip.write(RecipeFile.encode(content).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(folder + RecipeFile.MEDIA_DIRECTORY))
        zip.closeEntry()
        content.media.forEach { item ->
            zip.putNextEntry(ZipEntry(folder + item.entry))
            file(item).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
