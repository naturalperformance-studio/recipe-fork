package app.recipefork.app.file

import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a recipe file: a ZIP holding `recipe.json` and the `media/` directory (decisions/20260928-recipe-file-is-a-zip.md).
 * The device holds no media yet, so the directory is written empty; a media item will be streamed into it as an
 * entry of its own, named as the document names it.
 */
object RecipeArchive {
    fun write(content: RecipeFileContent, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(RecipeFile.DOCUMENT))
            zip.write(RecipeFile.encode(content).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(RecipeFile.MEDIA_DIRECTORY))
            zip.closeEntry()
        }
    }
}
