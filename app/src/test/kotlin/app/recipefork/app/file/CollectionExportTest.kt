package app.recipefork.app.file

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.ImportedCollection
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.CollectionFileContent
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.FileParent
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.fork
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sharing a collection as a file (v0.36, T-255): `<name>.recipefork-collection` is written under the cache's exports
 * through the FileProvider — the latest export alone kept — holding `collection.json` and, in `recipes/n/`, each recipe
 * as its own recipe file holds it, own and found, with its photographs and clips; a found recipe keeps its stated
 * author and parent. It opens the share sheet; a failure says so and changes nothing.
 */
@RunWith(AndroidJUnit4::class)
class CollectionExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
    private val book = ActiveBook(BookName.Pool, database)
    private val recipes = RecipeRepository(book, context.filesDir)
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val collections = CollectionRepository(book) { now }
    private val store = PhotoStore(context.filesDir)
    private val importer by lazy { RecipeImport(recipes, context.filesDir, clock = { now }) }

    @Before
    fun forgetEarlierDirectories() = FileProviderCache.clear()

    @After
    fun close() {
        database.close()
        store.directory(BookName.Pool).deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    // The book.

    private fun media(mediaId: String, kind: MediaKind, attachment: MediaAttachment, bytes: ByteArray?): Media {
        if (bytes != null) store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(bytes)
        return Media(mediaId, null, kind, attachment, MediaVisibility.PRIVATE)
    }

    private fun own(id: String, title: String, vararg media: Media): Recipe = Recipe.create(now, null).copy(
        id = RecipeId(id),
        title = title,
        steps = listOf(Step(0, "Boil."), Step(1, "Serve.")),
        media = media.toList(),
    ).also { runBlocking { recipes.save(it) } }

    /** A recipe a friend's file brought: stating `anna` as its author and `boris`'s Soup as its parent. */
    private fun found(id: String, vararg media: Pair<String, ByteArray>): RecipeId = runBlocking {
        val content = TestRecipeFiles.soup(id = id).copy(
            media = media.map { (mediaId, _) -> FileMedia(mediaId, MediaKind.PHOTO, MediaAttachment.Cover, "media/$mediaId") },
        )
        val file = TestRecipeFiles.file(content, *media.map { (mediaId, bytes) -> "media/$mediaId" to bytes }.toTypedArray())
        val outcome = importer.import { file.inputStream() }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        content.id
    }

    private fun collection(name: String, members: List<RecipeId> = emptyList()): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it, listOf(id)) } }
    }

    private fun imported(name: String, author: String?, members: List<RecipeId>): CollectionId = runBlocking {
        CollectionId("imported-1").also {
            assertEquals(ImportedCollection.CREATED, collections.putImported(it, name, author, members))
        }
    }

    // The file.

    private fun export(id: CollectionId, progress: (Float) -> Unit = {}): Uri =
        runBlocking { CollectionExport.write(context, recipes, collections, id, progress = progress) }!!

    /** The archive's entries by name, in order, read through the content resolver as the receiving application reads them. */
    private fun entries(uri: Uri): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertFalse("${entry.name} twice", entries.containsKey(entry.name))
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun document(uri: Uri): CollectionFileContent =
        CollectionFile.decode(entries(uri).getValue(CollectionFile.DOCUMENT).toString(Charsets.UTF_8))

    /** The recipe file in [folder] of the collection's file: its entries with the folder's prefix taken off. */
    private fun folder(uri: Uri, folder: String): Map<String, ByteArray> =
        entries(uri).filterKeys { it.startsWith(folder) }.mapKeys { it.key.removePrefix(folder) }

    private fun recipeIn(uri: Uri, folder: String): RecipeFileContent =
        RecipeFile.decode(folder(uri, folder).getValue(RecipeFile.DOCUMENT).toString(Charsets.UTF_8))

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun recipeFileOf(id: RecipeId): Map<String, ByteArray> {
        val uri = runBlocking { RecipeExport.write(context, recipes, id) }!!
        return entries(uri)
    }

    private fun exportsOnDisk(): List<String> {
        val exports = File(context.cacheDir, "exports")
        return exports.walkTopDown().filter { it.isFile }.map { it.relativeTo(exports).path }.toList()
    }

    // The tests.

    @Test
    fun theFileIsTheCollectionsNameWithTheCollectionExtensionUnderTheExportsThroughTheProvider() {
        val soup = own("soup", "Soup").id
        val menu = collection("New Year's menu", listOf(soup))
        val uri = export(menu)
        assertEquals(RecipeExport.authority(context), uri.authority)
        assertEquals("New-Year-s-menu.recipefork-collection", uri.lastPathSegment)
        assertEquals(listOf(menu.value + "/New-Year-s-menu.recipefork-collection"), exportsOnDisk())
        assertEquals("Novogodnee-menyu.recipefork-collection", CollectionExport.fileName("Новогоднее меню"))
        assertEquals("collection.recipefork-collection", CollectionExport.fileName("…"))
        // Nothing is left where the file was put together.
        assertFalse(File(context.cacheDir, "exports-staging").exists())
    }

    // The document: format, identity, name, no author for an own collection, the recipes in the collection's order.
    @Test
    fun theDocumentHoldsTheCollectionsIdentityNameAndRecipesInOrderAndNoAuthorForAnOwnOne() {
        val soup = own("soup", "Soup").id
        val pie = own("pie", "Pie").id
        val salad = own("salad", "Salad").id
        val menu = collection("New Year's menu", listOf(pie, soup, salad))
        val uri = export(menu)
        assertEquals(CollectionFileContent(menu, "New Year's menu", null, listOf(pie, soup, salad)), document(uri))
        val json = entries(uri).getValue("collection.json").toString(Charsets.UTF_8)
        assertTrue(json, json.contains("\"format_version\": 1"))
        assertFalse(json, json.contains("\"author\""))
        assertEquals(CollectionFile.encode(document(uri)), json)
    }

    // An imported collection, shared onward, states the author its own file stated.
    @Test
    fun anImportedCollectionStatesItsStatedAuthor() {
        val borscht = found("borscht")
        val annas = imported("Anna's pies", "anna", listOf(borscht))
        assertEquals(CollectionFileContent(annas, "Anna's pies", "anna", listOf(borscht)), document(export(annas)))
        val json = entries(export(annas)).getValue("collection.json").toString(Charsets.UTF_8)
        assertTrue(json, json.contains("\"author\": {\n        \"nickname\": \"anna\"\n    }"))
    }

    // Each recipe lies in the folder of its place, holding byte for byte what its own recipe file holds: its document
    // and media/, with its photographs and clips; read with the folder's prefix taken off, a folder is a recipe file
    // the recipe file's reader believes.
    @Test
    fun eachRecipeLiesInItsFolderAsItsRecipeFileHoldsIt() {
        val photo = TestRecipeFiles.jpeg(3_000)
        val clip = TestRecipeFiles.mp4(5_000)
        val soup = own(
            "soup", "Soup",
            media("c-1", MediaKind.PHOTO, MediaAttachment.Cover, photo),
            media("v-1", MediaKind.VIDEO, MediaAttachment.ToStep(1), clip),
        ).id
        val pie = own("pie", "Pie").id
        val cover = TestRecipeFiles.jpeg(2_000)
        val borscht = found("borscht", "f-1" to cover)
        val menu = collection("Menu", listOf(soup, borscht, pie))

        val uri = export(menu)
        val entries = entries(uri)

        // The found recipe's cover, as the book holds it under a name of its own.
        val held = recipes(borscht).media.single().mediaId
        assertEquals(
            listOf(
                "collection.json",
                "recipes/1/recipe.json", "recipes/1/media/", "recipes/1/media/c-1", "recipes/1/media/v-1",
                "recipes/2/recipe.json", "recipes/2/media/", "recipes/2/media/$held",
                "recipes/3/recipe.json", "recipes/3/media/",
            ),
            entries.keys.toList(),
        )
        assertArrayEquals(photo, entries.getValue("recipes/1/media/c-1"))
        assertArrayEquals(clip, entries.getValue("recipes/1/media/v-1"))
        assertArrayEquals(cover, entries.getValue("recipes/2/media/$held"))
        assertEquals(0, entries.getValue("recipes/1/media/").size)
        // The document's recipes are the folders' recipes, in their order.
        assertEquals(listOf(soup, borscht, pie), document(uri).recipes)
        assertEquals(
            listOf(soup, borscht, pie),
            document(uri).recipes.indices.map { recipeIn(uri, CollectionFile.recipeFolder(it)).id },
        )
        // Each folder is what the recipe's own file holds, entry for entry and byte for byte.
        listOf(soup, borscht, pie).forEachIndexed { place, id ->
            val expected = recipeFileOf(id)
            val actual = folder(export(menu), CollectionFile.recipeFolder(place))
            assertEquals(expected.keys.toList(), actual.keys.toList())
            expected.forEach { (name, bytes) -> assertArrayEquals(name, bytes, actual.getValue(name)) }
        }
        assertEquals(
            listOf(
                FileMedia("c-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/c-1"),
                FileMedia("v-1", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/v-1"),
            ),
            recipeIn(uri, "recipes/1/").media,
        )
        // The recipe file's reader takes a folder as a recipe file, media and all.
        val first = zip(folder(uri, "recipes/1/"))
        val read = RecipeArchiveReader.document({ first.inputStream() })
        assertEquals(recipeIn(uri, "recipes/1/"), read)
        val incoming = TestRecipeFiles.incoming()
        try {
            RecipeArchiveReader.media({ first.inputStream() }, read, { File(incoming, it.mediaId) })
            assertArrayEquals(photo, File(incoming, "c-1").readBytes())
            assertArrayEquals(clip, File(incoming, "v-1").readBytes())
        } finally {
            incoming.deleteRecursively()
        }
    }

    private fun recipes(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    // FLOW-063 in a collection: a found recipe keeps the author and the parent its own file stated; an own recipe
    // names no author, and a fork of the found one names it as its parent.
    @Test
    fun foundRecipesKeepTheirStatedAuthorAndParent() {
        val borscht = found("borscht")
        val fork = recipes(borscht).fork(now, owner = null).copy(title = "My borscht").also { runBlocking { recipes.save(it) } }
        val soup = own("soup", "Soup").id
        val menu = collection("Menu", listOf(borscht, fork.id, soup))
        val uri = export(menu)

        val found = recipeIn(uri, "recipes/1/")
        assertEquals(borscht, found.id)
        assertEquals("anna", found.author)
        assertEquals(FileParent(RecipeId("p-1"), "Soup", "boris"), found.parent)
        assertEquals(RecipeFileContent.of(recipes(borscht), null, null), found)

        val forked = recipeIn(uri, "recipes/2/")
        assertEquals(fork.id, forked.id)
        assertNull(forked.author)
        assertEquals(FileParent(borscht, "Bean soup", "anna"), forked.parent)

        val own = recipeIn(uri, "recipes/3/")
        assertEquals(soup, own.id)
        assertNull(own.author)
        assertNull(own.parent)
    }

    // A photograph whose file is gone is left out of its recipe's folder and document, as a recipe export leaves it.
    @Test
    fun aMediaItemWhoseFileIsGoneIsLeftOut() {
        val soup = own(
            "soup", "Soup",
            media("c-gone", MediaKind.PHOTO, MediaAttachment.Cover, null),
            media("s-1", MediaKind.PHOTO, MediaAttachment.ToStep(0), TestRecipeFiles.jpeg()),
        ).id
        val uri = export(collection("Menu", listOf(soup)))
        assertEquals(listOf("collection.json", "recipes/1/recipe.json", "recipes/1/media/", "recipes/1/media/s-1"), entries(uri).keys.toList())
        assertEquals(listOf("s-1"), recipeIn(uri, "recipes/1/").media.map { it.mediaId })
    }

    // An empty collection is shared as one with no recipes.
    @Test
    fun anEmptyCollectionIsSharedWithNoRecipes() {
        val salads = collection("Salads")
        val uri = export(salads)
        assertEquals(listOf("collection.json"), entries(uri).keys.toList())
        assertEquals(CollectionFileContent(salads, "Salads", null, emptyList()), document(uri))
    }

    // Progress: from nothing towards whole, never back, and whole at the end.
    @Test
    fun progressGoesUpToWhole() {
        val big = own("big", "Big", media("c-1", MediaKind.PHOTO, MediaAttachment.Cover, TestRecipeFiles.jpeg(400_000))).id
        val small = own("small", "Small").id
        val told = mutableListOf<Float>()
        export(collection("Menu", listOf(big, small))) { told += it }
        assertTrue(told.toString(), told.size > 2)
        assertEquals(told.sorted(), told)
        assertTrue(told.toString(), told.any { it > 0f && it < 1f })
        assertEquals(1f, told.last())
        assertTrue(told.toString(), told.all { it in 0f..1f })
    }

    // Only the latest export is kept: a collection's file replaces a recipe's, and the next collection's replaces it.
    @Test
    fun onlyTheLatestExportIsKept() {
        val soup = own("soup", "Soup").id
        val recipeFile = runBlocking { RecipeExport.write(context, recipes, soup) }!!
        val first = export(collection("Menu", listOf(soup)))
        assertTrue(runCatching { context.contentResolver.openInputStream(recipeFile)!!.close() }.isFailure)
        val salads = collection("Salads", listOf(soup))
        export(salads)
        assertTrue(runCatching { context.contentResolver.openInputStream(first)!!.close() }.isFailure)
        assertEquals(listOf(salads.value + "/Salads.recipefork-collection"), exportsOnDisk())
    }

    // Sharing reads the book and changes nothing in it.
    @Test
    fun sharingChangesNothing() {
        val soup = own("soup", "Soup")
        val borscht = found("borscht")
        val menu = collection("Menu", listOf(soup.id, borscht))
        val before = runBlocking { collections.get(menu) }
        val foundBefore = recipes(borscht)
        export(menu)
        assertEquals(before, runBlocking { collections.get(menu) })
        assertEquals(soup, recipes(soup.id))
        assertEquals(foundBefore, recipes(borscht))
    }

    // The sheet: the system's chooser over a send of the one file, of the collection file's type, readable only, the
    // application itself not offered.
    @Test
    fun theShareSheetSendsTheFileForReadingOnly() {
        val uri = export(collection("Menu", listOf(own("soup", "Soup").id)))
        val sheet = CollectionExport.shareSheet(context, uri, "Share “Menu”")
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        assertEquals("Share “Menu”", sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        @Suppress("DEPRECATION")
        val send = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/vnd.recipefork.collection+zip", send.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        assertNull(send.`package`)
        assertNull(send.component)
        @Suppress("DEPRECATION")
        val excluded = sheet.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)!!.toList()
        assertEquals(listOf(ComponentName(context, OpenRecipeFileActivity::class.java)), excluded)
    }

    // Share writes the file and opens the sheet on it, headed with the collection's name.
    @Test
    fun shareOpensTheSheetOnTheCollectionsFile() {
        val menu = collection("New Year's menu", listOf(own("soup", "Soup").id))
        val application = context as Application
        val shared = runBlocking { CollectionExport.share(context, recipes, collections, menu, sheetTitle = { "Share “$it”" }) }
        assertTrue(shared)
        val sheet = shadowOf(application).nextStartedActivity!!
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        assertEquals("Share “New Year's menu”", sheet.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        @Suppress("DEPRECATION")
        val send = sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("New-Year-s-menu.recipefork-collection", uri.lastPathSegment)
        assertEquals(CollectionFileContent(menu, "New Year's menu", null, listOf(RecipeId("soup"))), document(uri))
        // Outside an activity, the sheet opens in a task of its own.
        assertTrue(sheet.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    // A failure says so — share reports it — opens no sheet, and changes nothing: the earlier export stays whole,
    // and nothing of the failed one is left.
    @Test
    fun aFailureOpensNoSheetAndLeavesTheEarlierExport() {
        val soup = own("soup", "Soup").id
        val earlier = export(collection("Menu", listOf(soup)))
        val earlierBytes = entries(earlier)
        val salads = collection("Salads", listOf(soup))
        val application = context as Application
        shadowOf(application).clearNextStartedActivities()

        // A collection the book does not hold.
        assertFalse(runBlocking { CollectionExport.share(context, recipes, collections, CollectionId("absent"), { it }) })
        assertNull(runBlocking { CollectionExport.write(context, recipes, collections, CollectionId("absent")) })

        // A media file that cannot be read once the file is under way.
        val photo = media("c-1", MediaKind.PHOTO, MediaAttachment.Cover, TestRecipeFiles.jpeg())
        val broken = recipes(soup).copy(media = listOf(photo)).also { runBlocking { recipes.save(it) } }
        val file = store.file(BookName.Pool, "c-1")
        assertTrue(file.setReadable(false, false))
        try {
            assertFalse(runBlocking { CollectionExport.share(context, recipes, collections, salads, { it }) })
        } finally {
            file.setReadable(true, false)
        }
        assertFalse(File(context.cacheDir, "exports-staging").exists())

        // A cache that cannot be written.
        val cache = context.cacheDir
        assertTrue(cache.setWritable(false))
        try {
            assertFalse(runBlocking { CollectionExport.share(context, recipes, collections, CollectionId(salads.value), { it }) })
        } finally {
            cache.setWritable(true)
        }
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(earlierBytes.keys, entries(earlier).keys)
        assertEquals(listOf(earlier.lastPathSegment!!), exportsOnDisk().map { it.substringAfterLast('/') })
        assertFalse(File(context.cacheDir, "exports-staging").exists())
        assertEquals(broken, recipes(soup))
        assertEquals(listOf(soup), runBlocking { collections.get(salads) }!!.recipes)
    }
}
