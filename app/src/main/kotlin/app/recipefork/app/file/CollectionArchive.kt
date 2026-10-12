package app.recipefork.app.file

import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.CollectionFileContent
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.RecipeFileContent
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a collection file ([CollectionFile]): `collection.json`, then each recipe the document lists, in its order,
 * in the folder of its place, written by [RecipeArchive] as the recipe's own file holds it.
 */
object CollectionArchive {
    /**
     * Writes [document] and [recipes] — the content of each recipe it lists, in its order — to [out]; [file] gives the
     * file each media item a recipe's document names is read from.
     */
    fun write(
        document: CollectionFileContent,
        recipes: List<RecipeFileContent>,
        out: OutputStream,
        file: (RecipeFileContent, FileMedia) -> File,
    ) {
        require(recipes.map { it.id } == document.recipes) { "the recipes are not those the document lists" }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(CollectionFile.DOCUMENT))
            zip.write(CollectionFile.encode(document).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            recipes.forEachIndexed { place, recipe ->
                RecipeArchive.writeEntries(zip, recipe, CollectionFile.recipeFolder(place)) { file(recipe, it) }
            }
        }
    }
}
