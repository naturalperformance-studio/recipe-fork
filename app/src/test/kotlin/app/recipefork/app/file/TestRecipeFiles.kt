package app.recipefork.app.file

import app.recipefork.core.file.FileParent
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.minutes

/** Recipe files for the importer's tests: well made, and made wrong in each of the ways an importer must refuse. */
object TestRecipeFiles {
    /** A recipe a friend sends: two servings, ingredients that scale, a step with a timer, an author stated. */
    fun soup(id: String = "0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10", author: String? = "anna") = RecipeFileContent(
        id = RecipeId(id),
        title = "Bean soup",
        description = "Serve hot.",
        tags = setOf(3),
        language = null,
        servings = 2,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "beans", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
            Ingredient(1, null, freeTextName = "onion", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(1), divisible = false),
        ),
        steps = listOf(
            Step(0, "Boil the beans", listOf(IngredientReference(0, 9)), Timer(10.minutes)),
            Step(1, "Add the onion"),
        ),
        author = author,
        parent = FileParent(RecipeId("p-1"), "Soup", "boris"),
    )

    fun document(content: RecipeFileContent) = RecipeFile.encode(content).toByteArray(Charsets.UTF_8)

    /** A ZIP holding [entries] in order, as the standard library writes one. */
    fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** The file of [content], with [media] as entries of its own beside the document. */
    fun file(content: RecipeFileContent, vararg media: Pair<String, ByteArray>): ByteArray =
        zip(RecipeFile.DOCUMENT to document(content), RecipeFile.MEDIA_DIRECTORY to ByteArray(0), *media)

    fun jpeg(size: Int = 1_000) = ByteArray(size).also { byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()).copyInto(it) }

    fun mp4(size: Int = 1_000) = ByteArray(size).also {
        byteArrayOf(0, 0, 0, 0x20).copyInto(it)
        "ftypisom".toByteArray().copyInto(it, 4)
    }

    /**
     * A ZIP of one deflated entry whose local header declares [declaredSize] as its size, whatever [data] inflates to:
     * an archive that lies about what it holds.
     */
    fun lying(name: String, data: ByteArray, declaredSize: Int): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true).apply { setInput(data); finish() }
        val compressed = ByteArrayOutputStream().also { out ->
            val buffer = ByteArray(64 * 1024)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        }.toByteArray()
        val crc = CRC32().apply { update(data) }.value
        val nameBytes = name.toByteArray()
        val header = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x04034b50).putShort(20).putShort(0).putShort(8).putShort(0).putShort(0)
            .putInt(crc.toInt()).putInt(compressed.size).putInt(declaredSize)
            .putShort(nameBytes.size.toShort()).putShort(0)
        return header.array() + nameBytes + compressed
    }

    /** A file of the test's own holding [bytes], which a content resolver opens by its `file:` address. */
    fun write(directory: File, name: String, bytes: ByteArray): File =
        File(directory, name).also { it.parentFile?.mkdirs(); it.writeBytes(bytes) }
}
