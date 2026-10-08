package app.recipefork.app.file

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.FileParent
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.fork
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-063 and decisions/20260928-recipe-file-is-a-zip.md: export writes a ZIP named with the product's extension,
 * holding `recipe.json` and an empty `media/`, and hands it to the system's share and save sheet through the
 * application's FileProvider; the recipe exported is left exactly as it was.
 */
@RunWith(AndroidJUnit4::class)
class RecipeExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Before
    fun forgetEarlierDirectories() = FileProviderCache.clear()

    @After
    fun close() {
        book.close()
        File(context.cacheDir, "export-test-files").deleteRecursively()
    }

    private fun full(title: String) = Recipe.create(now, owner = null).copy(
        title = title,
        description = "Serve hot.",
        tags = setOf(3, 7),
        language = RecipeLanguage.EN,
        servings = 4,
        ingredients = listOf(
            Ingredient(0, 101, qualifier = "ripe", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(250)),
            Ingredient(1, 102, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3, 2), divisible = true),
            Ingredient(2, null, freeTextName = "smoked salt", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        ),
        steps = listOf(
            Step(0, "Fry the tomatoes", listOf(IngredientReference(0, 8)), Timer(12.minutes, "Tomatoes")),
            // A part of an ingredient travels in the file.
            Step(1, "Add the eggs", listOf(IngredientReference(1, 4, IngredientShare(Rational.of(1, 2), QuantityUnit.PIECE))), Timer(3.minutes)),
        ),
    )

    private fun save(recipe: Recipe) = runBlocking { recipes.save(recipe) }

    private fun export(recipe: Recipe): Uri = runBlocking { RecipeExport.write(context, recipes, recipe.id) }!!

    /** The archive's entries by name, read through the content resolver as the receiving application reads them. */
    private fun entries(uri: Uri): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun content(uri: Uri) = RecipeFile.decode(entries(uri).getValue("recipe.json").toString(Charsets.UTF_8))

    // FLOW-063: the file is a ZIP with the product's extension, holding recipe.json and the media directory.
    @Test
    fun theFileIsAZipWithTheProductsExtensionHoldingTheDocumentAndTheMediaDirectory() {
        val recipe = full("Shakshuka").also(::save)
        val uri = export(recipe)
        assertEquals(RecipeExport.authority(context), uri.authority)
        assertTrue(uri.lastPathSegment!!, uri.lastPathSegment!!.endsWith(".recipefork"))
        assertEquals("Shakshuka.recipefork", uri.lastPathSegment)
        val entries = entries(uri)
        assertEquals(listOf("recipe.json", "media/"), entries.keys.toList())
        assertEquals(0, entries.getValue("media/").size)
        val json = entries.getValue("recipe.json").toString(Charsets.UTF_8)
        assertTrue(json, json.contains("\"format_version\": 2"))
    }

    // FLOW-063 (3, 5): the document holds the recipe whole; written with no account, it names no author; with no
    // parent, it names none.
    @Test
    fun theDocumentHoldsTheRecipeAndNamesNoAuthorAndNoParent() {
        val recipe = full("Shakshuka").also(::save)
        val read = content(export(recipe))
        assertEquals(RecipeFileContent.of(recipe, null, null), read)
        assertEquals(recipe.id, read.id)
        assertEquals("Shakshuka", read.title)
        assertEquals("Serve hot.", read.description)
        assertEquals(setOf(3L, 7L), read.tags)
        assertEquals(RecipeLanguage.EN, read.language)
        assertEquals(4, read.servings)
        assertEquals(recipe.ingredients, read.ingredients)
        assertEquals(recipe.steps, read.steps)
        assertNull(read.author)
        assertNull(read.parent)
        assertTrue(read.media.isEmpty())
    }

    // FLOW-063: a fork's file names its direct parent by identity, with the title the book holds for it.
    @Test
    fun aForksFileNamesItsParentWithItsTitle() {
        val parent = full("Shakshuka").also(::save)
        val fork = parent.fork(now, owner = null).copy(title = "Green shakshuka").also(::save)
        assertEquals(FileParent(parent.id, "Shakshuka", null), content(export(fork)).parent)
    }

    @Test
    fun aParentNoLongerInTheBookIsNamedByItsIdentity() {
        val fork = full("Green shakshuka").copy(parent = RecipeId("gone")).also(::save)
        assertEquals(FileParent(RecipeId("gone"), null, null), content(export(fork)).parent)
    }

    // FLOW-063 (2, 6): a found recipe's file carries the author and the parent its own file stated, and the found
    // recipe is unchanged by it.
    @Test
    fun aFoundRecipesFileCarriesTheAuthorAndTheParentItsFileStated() {
        val id = runBlocking { importFound(TestRecipeFiles.soup()) }
        val before = runBlocking { recipes.get(id) }!!
        val read = content(export(before))
        assertEquals(id, read.id)
        assertEquals("anna", read.author)
        assertEquals(FileParent(RecipeId("p-1"), "Soup", "boris"), read.parent)
        assertEquals(before, runBlocking { recipes.get(id) })
    }

    // FLOW-065 (6): a fork of a found recipe names it as its parent — by its title and its stated author — while the
    // book holds it and once it has been removed.
    @Test
    fun aForkOfAFoundRecipeNamesItAsItsParent() {
        val id = runBlocking { importFound(TestRecipeFiles.soup()) }
        val book = ActiveBook(BookName.Pool, this.book.database)
        val forkId = requireNotNull(runBlocking { forkRecipe(recipes, book, id, now) })
        val fork = runBlocking { recipes.get(forkId) }!!
        val expected = FileParent(id, "Bean soup", "anna")
        assertEquals(expected, content(export(fork)).parent)
        assertNull(content(export(fork)).author)
        assertTrue(runBlocking { importer.remove(id) })
        assertNull(runBlocking { recipes.get(id) })
        assertEquals(expected, content(export(fork)).parent)
    }

    private val importer by lazy { RecipeImport(recipes, File(context.cacheDir, "export-test-files"), clock = { now }) }

    private suspend fun importFound(content: RecipeFileContent): RecipeId {
        val outcome = importer.import { TestRecipeFiles.file(content).inputStream() }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        return content.id
    }

    // FLOW-063 (1): exporting changes nothing in the recipe — an editable recipe stays editable.
    @Test
    fun exportingChangesNothingInTheRecipe() {
        val recipe = full("Shakshuka").also(::save)
        assertTrue(recipe.state.editable)
        export(recipe)
        val after = runBlocking { recipes.get(recipe.id) }!!
        assertEquals(recipe, after)
        assertEquals(RecipeState.LOCAL, after.state)
        assertTrue(after.state.editable)
    }

    @Test
    fun aRecipeTheBookDoesNotHoldWritesNoFile() {
        assertNull(runBlocking { RecipeExport.write(context, recipes, RecipeId("absent")) })
    }

    // FLOW-063 (7): the file is handed to the system's share and save sheet, readable by whoever the person picks;
    // the product sends it nowhere itself.
    @Test
    fun theFileIsHandedToTheSystemsShareSheet() {
        val recipe = full("Shakshuka").also(::save)
        val uri = export(recipe)
        val sheet = RecipeExport.shareSheet(context, uri, "Export “Shakshuka”")
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        assertEquals("Export “Shakshuka”", sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        @Suppress("DEPRECATION")
        val send = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/vnd.recipefork.recipe+zip", send.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        // No address, no recipient: the sheet chooses nothing for the person.
        assertNull(send.`package`)
        // The application is not offered its own file back.
        @Suppress("DEPRECATION")
        val excluded = sheet.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)!!.toList()
        assertEquals(listOf(android.content.ComponentName(context, OpenRecipeFileActivity::class.java)), excluded)
        assertNull(send.component)
        assertFalse(send.hasExtra(Intent.EXTRA_EMAIL))
    }

    // The file's name is the recipe's title made safe: in ASCII (ExportFileNameTest), with what a file system refuses
    // taken out.
    @Test
    fun theFilesNameIsTheTitleMadeSafe() {
        assertEquals("Borshch.recipefork", RecipeExport.fileName("Борщ"))
        assertEquals("a-b-c.recipefork", RecipeExport.fileName(" a/b:c? "))
        assertEquals("recipe.recipefork", RecipeExport.fileName(""))
        assertEquals("recipe.recipefork", RecipeExport.fileName("../.."))
        assertEquals(80 + ".recipefork".length, RecipeExport.fileName("x".repeat(200)).length)
    }

    // A second export of the same recipe under a new title leaves only the new file to share.
    @Test
    fun aSecondExportReplacesTheFirst() {
        val recipe = full("Shakshuka").also(::save)
        val first = export(recipe)
        save(recipe.copy(title = "Eggs"))
        val second = export(recipe)
        assertEquals("Eggs.recipefork", second.lastPathSegment)
        assertEquals("Eggs", content(second).title)
        assertTrue(runCatching { context.contentResolver.openInputStream(first)!!.close() }.isFailure)
    }

    // Before each export the earlier exports' files go, those of recipes since deleted among them.
    @Test
    fun earlierExportsAreClearedBeforeEachExport() {
        val soup = full("Soup").also(::save)
        val bread = full("Bread").copy(id = RecipeId("bread")).also(::save)
        export(soup)
        runBlocking { recipes.delete(soup.id) }
        export(bread)
        val exports = File(context.cacheDir, "exports")
        assertEquals(listOf("bread/Bread.recipefork"), exports.walkTopDown().filter { it.isFile }.map { it.relativeTo(exports).path }.toList())
    }

    // An identity that keeps no character a directory may be named with still gets a directory of its own.
    @Test
    fun anIdentityWithNoUsableCharacterIsExportedToo() {
        val odd = full("Soup").copy(id = RecipeId("€/€")).also(::save)
        val uri = export(odd)
        assertEquals("Soup", content(uri).title)
        assertTrue(File(context.cacheDir, "exports/recipe/Soup.recipefork").isFile)
    }

    // The FileProvider exposes cache/exports/ and nothing else, is not exported, and grants per file only.
    @Test
    fun theProviderExposesOnlyTheExportsDirectory() {
        val paths = mutableListOf<Triple<String, String?, String?>>()
        context.resources.getXml(R.xml.file_paths).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name != "paths") {
                    paths += Triple(xml.name, xml.getAttributeValue(null, "name"), xml.getAttributeValue(null, "path"))
                }
            }
        }
        assertEquals(listOf(Triple("cache-path", "exports", "exports/")), paths)
        val info = context.packageManager.getProviderInfo(ComponentName(context, FileProvider::class.java), 0)
        assertEquals(RecipeExport.authority(context), info.authority)
        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
        val authority = RecipeExport.authority(context)
        listOf(File(context.cacheDir, "other.txt"), File(context.filesDir, "book.db"), File(context.cacheDir, "exportsx/a")).forEach { file ->
            assertTrue("$file is shared", runCatching { FileProvider.getUriForFile(context, authority, file) }.isFailure)
        }
        assertTrue(runCatching { FileProvider.getUriForFile(context, authority, File(context.cacheDir, "exports/a/b.recipefork")) }.isSuccess)
    }

    // The grant the sheet carries is to read the one file: never to write it, never to keep it.
    @Test
    fun theGrantIsReadOnly() {
        val uri = export(full("Soup").also(::save))
        @Suppress("DEPRECATION")
        val send = RecipeExport.shareSheet(context, uri, "Export").getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    }

    // An export that writes no file reports it, so that the person can be told.
    @Test
    fun anExportThatWritesNoFileFails() {
        val soup = full("Soup").also(::save)
        assertFalse(runBlocking { RecipeExport.share(context, recipes, RecipeId("absent"), "Export") })
        val cache = context.cacheDir
        File(cache, "exports").deleteRecursively()
        assertTrue(cache.setWritable(false))
        try {
            assertFalse(runBlocking { RecipeExport.share(context, recipes, soup.id, "Export") })
        } finally {
            cache.setWritable(true)
        }
    }

    // DOM-022, DOM-023: a recipe with groups and notes, exported and imported into another book, keeps them.
    @Test
    fun groupsAndNotesSurviveExportAndImport() = runBlocking {
        val recipe = full("Shakshuka").let { full ->
            full.copy(
                ingredients = full.ingredients.map { if (it.position == 1) it else it.copy(note = "note ${it.position}") },
                ingredientGroups = listOf(IngredientGroup("Sauce", 0), IngredientGroup("Eggs", 1)),
            )
        }
        save(recipe)
        val uri = export(recipe)
        val read = content(uri)
        assertEquals(recipe.ingredientGroups, read.ingredientGroups)
        assertEquals(recipe.ingredients, read.ingredients)
        val friendsBook = TestBook(context)
        try {
            val friends = RecipeRepository(ActiveBook(BookName.OfAccount("export-test-friend"), friendsBook.database))
            val outcome = RecipeImport(friends, File(context.cacheDir, "export-test-files"), clock = { now })
                .import { context.contentResolver.openInputStream(uri)!! }
            assertEquals(ImportOutcome.Added(recipe.id, "Shakshuka"), outcome)
            val found = friends.get(recipe.id)!!
            assertEquals(listOf(IngredientGroup("Sauce", 0), IngredientGroup("Eggs", 1)), found.ingredientGroups)
            assertEquals(listOf("note 0", null, "note 2"), found.ingredients.map { it.note })
            assertEquals(recipe.ingredients, found.ingredients)
        } finally {
            friendsBook.close()
        }
    }
}
