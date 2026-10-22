package app.recipefork.app.file

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.media.ClipDuration
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.CollectionFileContent
import app.recipefork.core.file.EntryLabels
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Importing a collection file (v0.36, T-256, scenario 5): each recipe arrives as a found recipe, skipped when the book
 * holds it; the collection arrives as an imported one, or — held as one already — takes the file's name and order and
 * the recipes it lacks; the reader's own collection of its identity is never touched. A file that cannot be believed,
 * in any part, is refused with its reason and changes nothing: no recipe, no collection, no media file.
 */
@RunWith(AndroidJUnit4::class)
class CollectionImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
    private val book = ActiveBook(BookName.Pool, database)
    private val files = File(context.cacheDir, "collection-import-files").also { it.deleteRecursively() }
    private val recipes = RecipeRepository(book, files)
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val collections = CollectionRepository(book) { now }
    private val media = RecipeImport.mediaDirectory(files, BookName.Pool)

    private fun importer(limits: CollectionLimits = CollectionLimits(), clips: ClipDuration = TestRecipeFiles.SHORT_CLIP) =
        CollectionImport(book, files, clock = { now }, limits = limits, clipDuration = clips)

    @After
    fun close() {
        database.close()
        files.deleteRecursively()
    }

    // The files.

    private val menu = CollectionId("3c1f9a52-7d0e-4b8a-9f61-2e5d8c4b7a10")

    private fun recipe(id: String, title: String = "Recipe $id", vararg media: FileMedia) =
        TestRecipeFiles.soup(id = id).copy(title = title, media = media.toList())

    private fun cover(mediaId: String) = FileMedia(mediaId, MediaKind.PHOTO, MediaAttachment.Cover, "media/$mediaId")

    private fun clip(mediaId: String) = FileMedia(mediaId, MediaKind.VIDEO, MediaAttachment.ToStep(0), "media/$mediaId")

    /**
     * A collection file as the application writes one: `collection.json` first, then each recipe in `recipes/n/` as
     * its own file holds it, with [media] bytes of each entry (by place, from 0), and [extra] entries after.
     */
    private fun collectionFile(
        recipes: List<RecipeFileContent>,
        name: String = "New Year's menu",
        id: CollectionId = menu,
        media: Map<Int, List<Pair<String, ByteArray>>> = emptyMap(),
        extra: List<Pair<String, ByteArray>> = emptyList(),
        document: ByteArray = CollectionFile.encode(CollectionFileContent(id, name, "anna", recipes.map { it.id })).toByteArray(),
    ): ByteArray {
        val entries = mutableListOf(CollectionFile.DOCUMENT to document)
        recipes.forEachIndexed { place, recipe ->
            val folder = CollectionFile.recipeFolder(place)
            entries += folder + RecipeFile.DOCUMENT to TestRecipeFiles.document(recipe)
            entries += folder + RecipeFile.MEDIA_DIRECTORY to ByteArray(0)
            media[place].orEmpty().forEach { (entry, bytes) -> entries += folder + entry to bytes }
        }
        entries += extra
        return TestRecipeFiles.zip(*entries.toTypedArray())
    }

    private fun import(bytes: ByteArray, importer: CollectionImport = importer()) =
        runBlocking { importer.import { bytes.inputStream() } }

    /** Adds the recipe [content] to the book as a recipe file brings it. */
    private fun held(content: RecipeFileContent) = runBlocking {
        val outcome = RecipeImport(recipes, files, clock = { now }).import { TestRecipeFiles.file(content).inputStream() }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
    }

    private fun mediaFiles(): List<File> = media.listFiles()?.toList().orEmpty()

    /** Nothing of a refused file is in the book: [recipes] recipes and [mediaCount] media files as before, no collection. */
    private fun assertUnchanged(recipesBefore: Int = 0, mediaCount: Int = 0) = runBlocking {
        assertEquals(recipesBefore, recipes.count())
        assertEquals(emptyList<Any>(), collections.listed(CollectionOrigin.IMPORTED).first())
        assertEquals(mediaCount, mediaFiles().size)
    }

    // Scenario 5: a first import — each recipe as a found recipe, frozen, with its media; one the book holds is skipped;
    // the collection is imported, in the file's order, with its stated author.
    @Test
    fun aFirstImportAddsTheRecipesAndTheCollection() = runBlocking {
        val salad = recipe("r-salad", "Olivier")
        val duck = recipe("r-duck", "Duck with apples", cover("m-duck"))
        val cake = recipe("r-cake", "Napoleon", cover("m-cake"), clip("m-clip"))
        held(salad)
        val file = collectionFile(
            listOf(duck, salad, cake),
            media = mapOf(0 to listOf("media/m-duck" to TestRecipeFiles.jpeg()), 2 to listOf("media/m-cake" to TestRecipeFiles.jpeg(), "media/m-clip" to TestRecipeFiles.mp4())),
        )
        val outcome = import(file)
        assertEquals(CollectionImportOutcome.Imported(menu, "New Year's menu", recipes = 3, held = 1, updated = false), outcome)
        assertEquals(3, recipes.count())
        val imported = collections.get(menu)!!
        assertEquals(CollectionOrigin.IMPORTED, imported.origin)
        assertEquals("New Year's menu", imported.name)
        assertEquals("anna", imported.statedAuthor)
        assertEquals(listOf(duck.id, salad.id, cake.id), imported.recipes)
        assertEquals(listOf("New Year's menu"), collections.listed(CollectionOrigin.IMPORTED).first().map { it.name })
        for (content in listOf(duck, cake)) {
            val found = recipes.get(content.id)!!
            assertEquals(RecipeImport.FOUND_STATE, found.state)
            assertEquals(content.title, found.title)
            assertEquals("anna", found.statedAuthor)
            assertTrue(recipes.isMarked(content.id))
            // Media under names of the device's own, never the file's.
            found.media.forEach { item ->
                assertFalse(item.mediaId.startsWith("m-"))
                assertTrue(File(media, item.mediaId).isFile)
            }
        }
        assertArrayEquals(TestRecipeFiles.jpeg(), File(media, recipes.get(duck.id)!!.media.single().mediaId).readBytes())
        assertEquals(3, mediaFiles().size)
        assertEquals(
            "“New Year's menu” added: 3 recipes (1 was already in the book)",
            outcome.message().text(context.resources),
        )
    }

    // v0.44, T-318: a recipe of a collection file arrives with what its finished dish weighs, as its own file brings it;
    // one with none arrives with none.
    @Test
    fun aRecipeOfACollectionKeepsItsFinishedWeight() = runBlocking {
        val weighed = recipe("r-pilaf", "Pilaf").copy(finishedWeightGrams = 560)
        val plain = recipe("r-salad", "Olivier")
        val outcome = import(collectionFile(listOf(weighed, plain)))
        assertEquals(CollectionImportOutcome.Imported(menu, "New Year's menu", recipes = 2, held = 0, updated = false), outcome)
        assertEquals(560, recipes.get(weighed.id)!!.finishedWeightGrams)
        assertNull(recipes.get(plain.id)!!.finishedWeightGrams)
    }

    // The notice as the criterion states it, and with nothing held.
    @Test
    fun theNoticeSaysWhatCameAndWhatWasHeld() {
        fun said(kind: FileMessage.Kind, recipes: Int, held: Int) =
            FileMessage(kind, "New Year's menu", collectionId = menu.value, recipes = recipes, held = held).text(context.resources)
        assertEquals("“New Year's menu” added: 5 recipes (2 were already in the book)", said(FileMessage.Kind.COLLECTION_ADDED, 5, 2))
        assertEquals("“New Year's menu” added: 1 recipe", said(FileMessage.Kind.COLLECTION_ADDED, 1, 0))
        assertEquals("“New Year's menu” updated: 7 recipes (5 were already in the book)", said(FileMessage.Kind.COLLECTION_UPDATED, 7, 5))
        assertEquals("You already have this collection", said(FileMessage.Kind.COLLECTION_OWN, 0, 0))
        // A notice of a collection survives saved state whole.
        val message = FileMessage(FileMessage.Kind.COLLECTION_ADDED, "New Year's menu", collectionId = menu.value, recipes = 5, held = 2)
        assertEquals(message, FileMessage.decoded(message.encoded()))
        assertTrue(message.isNotice)
    }

    @Test
    @Config(qualifiers = "ru")
    fun theNoticeIsSaidInRussianWithItsPlurals() {
        fun said(recipes: Int, held: Int) =
            FileMessage(FileMessage.Kind.COLLECTION_ADDED, "Новогоднее меню", collectionId = menu.value, recipes = recipes, held = held)
                .text(context.resources)
        assertEquals("Коллекция «Новогоднее меню» добавлена: 5 рецептов (2 уже были в книге)", said(5, 2))
        assertEquals("Коллекция «Новогоднее меню» добавлена: 1 рецепт (1 уже был в книге)", said(1, 1))
        assertEquals("Коллекция «Новогоднее меню» добавлена: 3 рецепта", said(3, 0))
        assertEquals("Коллекция «Новогоднее меню» добавлена: 21 рецепт (11 уже были в книге)", said(21, 11))
        assertEquals("У вас уже есть эта коллекция", FileMessage(FileMessage.Kind.COLLECTION_OWN, "x").text(context.resources))
        assertEquals("Файл коллекции повреждён, ничего не добавлено", FileMessage(FileMessage.Kind.COLLECTION_DAMAGED).text(context.resources))
    }

    // Scenario 5: the author sends an updated file — the new recipes join the book and the collection, the others are
    // kept, and the collection takes the file's name and order; a recipe the book's copy held that the file leaves out
    // stays, after the file's.
    @Test
    fun aSecondImportAddsTheNewRecipesAndTakesTheFilesNameAndOrder() = runBlocking {
        val salad = recipe("r-salad", "Olivier")
        val duck = recipe("r-duck", "Duck with apples")
        val cake = recipe("r-cake", "Napoleon", cover("m-cake"))
        assertTrue(import(collectionFile(listOf(salad, duck))) is CollectionImportOutcome.Imported)
        val updated = collectionFile(
            listOf(cake, duck), name = "New Year's menu 2027",
            media = mapOf(0 to listOf("media/m-cake" to TestRecipeFiles.jpeg())),
        )
        val outcome = import(updated)
        assertEquals(CollectionImportOutcome.Imported(menu, "New Year's menu 2027", recipes = 2, held = 1, updated = true), outcome)
        assertEquals(3, recipes.count())
        val collection = collections.get(menu)!!
        assertEquals("New Year's menu 2027", collection.name)
        assertEquals(CollectionOrigin.IMPORTED, collection.origin)
        assertEquals(listOf(cake.id, duck.id, salad.id), collection.recipes)
        assertEquals(1, collections.listed(CollectionOrigin.IMPORTED).first().size)
        assertEquals(1, mediaFiles().size)
        assertEquals(
            "“New Year's menu 2027” updated: 2 recipes (1 was already in the book)",
            (outcome as CollectionImportOutcome).message().text(context.resources),
        )
        // The same file again adds nothing new.
        assertEquals(CollectionImportOutcome.Imported(menu, "New Year's menu 2027", recipes = 2, held = 2, updated = true), import(updated))
        assertEquals(3, recipes.count())
        assertEquals(1, mediaFiles().size)
    }

    // A recipe of the file that is one of the reader's own — shared in a collection and come back — is held: counted so,
    // never overwritten by the file's copy, and listed in the imported collection as the book holds it.
    @Test
    fun aRecipeThatIsTheReadersOwnIsHeldAndKept() = runBlocking {
        val own = Recipe.create(now, null, id = RecipeId("r-own")).copy(title = "My own pie")
        recipes.save(own)
        val ownBefore = recipes.get(own.id)!!
        val sent = recipe("r-own", "The file's pie", cover("m-own"))
        val duck = recipe("r-duck", "Duck with apples")
        val outcome = import(collectionFile(listOf(sent, duck), media = mapOf(0 to listOf("media/m-own" to TestRecipeFiles.jpeg()))))
        assertEquals(CollectionImportOutcome.Imported(menu, "New Year's menu", recipes = 2, held = 1, updated = false), outcome)
        assertEquals(ownBefore, recipes.get(own.id))
        assertFalse(recipes.isMarked(own.id))
        assertEquals(listOf(own.id, duck.id), collections.get(menu)!!.recipes)
        assertEquals(2, recipes.count())
        // The file's copy of the own recipe's photograph is not written.
        assertEquals(0, mediaFiles().size)
    }

    // The import's progress is told whole once it is done, though no second reading was needed.
    @Test
    fun progressReachesWhole() {
        val bytes = collectionFile(listOf(recipe("r-1"), recipe("r-2")))
        val told = mutableListOf<Float>()
        val outcome = runBlocking { importer().import(bytes.size.toLong(), { told += it }) { bytes.inputStream() } }
        assertTrue("$outcome", outcome is CollectionImportOutcome.Imported)
        assertEquals(1f, told.last())
        assertTrue("$told", told.zipWithNext().all { (a, b) -> a <= b })
        // Refused, too.
        told.clear()
        assertTrue(runBlocking { importer().import(8, { told += it }) { "garbage!".toByteArray().inputStream() } } is CollectionImportOutcome.Refused)
        assertEquals(1f, told.last())
    }

    // A small file cannot make the reader hold more than the recipes' documents' cap: each recipe's document is decoded
    // as it is read, its text let go, and the whole of them refused once past the cap — long before the archive's end.
    @Test
    fun manyPaddedDocumentsAreRefusedAsTooLarge() {
        val listed = (1..2000).map { recipe("r-pad-$it") }
        val padded = { content: RecipeFileContent ->
            TestRecipeFiles.document(content).let { it + ByteArray(ArchiveLimits.MIB.toInt() - it.size) { ' '.code.toByte() } }
        }
        val entries = mutableListOf(
            CollectionFile.DOCUMENT to CollectionFile.encode(CollectionFileContent(menu, "Menu", null, listed.map { it.id })).toByteArray(),
        )
        // As many as the default cap takes, and a few more; the file goes on listing 2000.
        val past = (CollectionLimits().documents / ArchiveLimits.MIB).toInt() + 3
        listed.take(past).forEachIndexed { place, content -> entries += CollectionFile.recipeFolder(place) + RecipeFile.DOCUMENT to padded(content) }
        val bytes = TestRecipeFiles.zip(*entries.toTypedArray())
        assertTrue("a small file: ${bytes.size}", bytes.size < 2 * ArchiveLimits.MIB)
        val refusal = runCatching { CollectionArchiveReader.read({ bytes.inputStream() }) }.exceptionOrNull() as RefusedFile
        assertEquals(Refusal.DAMAGED, refusal.refusal)
        assertEquals("the recipes' documents over their cap", refusal.message)
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), import(bytes))
        assertUnchanged()
        // Under small limits, the same.
        val small = CollectionLimits(documents = 3 * ArchiveLimits.MIB)
        val few = TestRecipeFiles.zip(*entries.take(6).toTypedArray())
        val refused = runCatching { CollectionArchiveReader.read({ few.inputStream() }, small) }.exceptionOrNull() as RefusedFile
        assertEquals("the recipes' documents over their cap", refused.message)
    }

    // An entry's name is held to a length: a long one is refused before anything of it is kept.
    @Test
    fun aLongEntryNameIsRefused() {
        val first = recipe("r-1")
        val long = "recipes/1/media/" + "x".repeat(300)
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), import(collectionFile(listOf(first), extra = listOf(long to ByteArray(1)))))
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), import(collectionFile(listOf(first), extra = listOf("x".repeat(300) to ByteArray(1)))))
        assertUnchanged()
        assertTrue(import(collectionFile(listOf(first), extra = listOf("recipes/1/media/" + "x".repeat(200) to ByteArray(1)))) is CollectionImportOutcome.Imported)
    }

    // The reader's own collection, shared and come back: refused with its notice; it, and the book, are left as they are.
    @Test
    fun anOwnCollectionsIdentityIsRefused() = runBlocking {
        val salad = recipe("r-salad", "Olivier")
        held(salad)
        val own = collections.create("Our menu")!!
        collections.addTo(salad.id, listOf(own))
        val duck = recipe("r-duck", "Duck with apples", cover("m-duck"))
        val outcome = import(
            collectionFile(listOf(duck, salad), id = own, name = "Renamed", media = mapOf(0 to listOf("media/m-duck" to TestRecipeFiles.jpeg()))),
        )
        assertEquals(CollectionImportOutcome.OwnHeld(own, "Our menu"), outcome)
        assertEquals("You already have this collection", (outcome as CollectionImportOutcome).message().text(context.resources))
        assertTrue(outcome.message().isNotice)
        val kept = collections.get(own)!!
        assertEquals("Our menu", kept.name)
        assertEquals(CollectionOrigin.OWN, kept.origin)
        assertEquals(listOf(salad.id), kept.recipes)
        assertNull(recipes.get(duck.id))
        assertUnchanged(recipesBefore = 1)
    }

    // A damaged file, or a newer one, is refused with its reason and changes nothing — whichever part of it is wrong,
    // the last recipe's as much as the first's.
    @Test
    fun aDamagedOrNewerFileIsRefusedAndChangesNothing() {
        val first = recipe("r-1", "First", cover("m-1"))
        val second = recipe("r-2", "Second", cover("m-2"))
        val good = mapOf(0 to listOf("media/m-1" to TestRecipeFiles.jpeg()), 1 to listOf("media/m-2" to TestRecipeFiles.jpeg()))
        val document = { recipes: List<RecipeFileContent>, name: String ->
            CollectionFile.encode(CollectionFileContent(menu, name, null, recipes.map { it.id })).toByteArray()
        }
        val refused = listOf(
            "not a ZIP" to ("garbage".toByteArray() to Refusal.NOT_A_RECIPE_FILE),
            "a recipe file" to (TestRecipeFiles.file(first) to Refusal.NOT_A_RECIPE_FILE),
            "a document that does not parse" to (collectionFile(listOf(first, second), media = good, document = "{".toByteArray()) to Refusal.DAMAGED),
            "a newer format" to (
                collectionFile(listOf(first), media = good, document = """{"format_version": 2, "collection": {"id": "x", "name": "y", "recipes": []}}""".toByteArray()) to
                    Refusal.LATER_VERSION
                ),
            "a blank name" to (collectionFile(listOf(first, second), media = good, document = document(listOf(first, second), "  ")) to Refusal.DAMAGED),
            "a name too long" to (collectionFile(listOf(first, second), media = good, document = document(listOf(first, second), "n".repeat(61))) to Refusal.DAMAGED),
            "a recipe listed twice" to (collectionFile(listOf(first, first), media = mapOf(0 to good.getValue(0), 1 to good.getValue(0))) to Refusal.DAMAGED),
            "a folder missing" to (collectionFile(listOf(first), media = good, document = document(listOf(first, second), "Menu")) to Refusal.DAMAGED),
            "a folder holding another recipe" to (
                collectionFile(listOf(first, second), media = good, document = document(listOf(first, recipe("r-3")), "Menu")) to Refusal.DAMAGED
                ),
            "the last recipe's media missing" to (collectionFile(listOf(first, second), media = mapOf(0 to good.getValue(0))) to Refusal.DAMAGED),
            "the last recipe's photograph no photograph" to (
                collectionFile(listOf(first, second), media = mapOf(0 to good.getValue(0), 1 to listOf("media/m-2" to ByteArray(100)))) to Refusal.DAMAGED
                ),
            "a second document in a folder" to (
                withEntry(collectionFile(listOf(first, second), media = good), "recipes/2/recipe.json", TestRecipeFiles.document(second)) to Refusal.DAMAGED
                ),
            "the last recipe's document damaged" to (
                TestRecipeFiles.zip(
                    CollectionFile.DOCUMENT to document(listOf(first, second), "Menu"),
                    "recipes/1/recipe.json" to TestRecipeFiles.document(first),
                    "recipes/1/media/m-1" to TestRecipeFiles.jpeg(),
                    "recipes/2/recipe.json" to "{".toByteArray(),
                ) to Refusal.DAMAGED
                ),
            "the last recipe's document no UTF-8" to (
                TestRecipeFiles.zip(
                    CollectionFile.DOCUMENT to document(listOf(first, second), "Menu"),
                    "recipes/1/recipe.json" to TestRecipeFiles.document(first),
                    "recipes/1/media/m-1" to TestRecipeFiles.jpeg(),
                    "recipes/2/recipe.json" to byteArrayOf(0xC3.toByte(), 0x28),
                ) to Refusal.DAMAGED
                ),
            "the last recipe breaking the rules" to (
                collectionFile(listOf(first, second.copy(title = "t".repeat(RecipeFileCheck.MAX_TITLE + 1))), media = good) to Refusal.BREAKS_RULES
                ),
            "a second document" to (
                withEntry(collectionFile(listOf(first, second), media = good), CollectionFile.DOCUMENT, document(listOf(first, second), "Menu")) to Refusal.DAMAGED
                ),
            "a media entry given twice" to (withEntry(collectionFile(listOf(first, second), media = good), "recipes/2/media/m-2", TestRecipeFiles.jpeg()) to Refusal.DAMAGED),
            "the document not first" to (
                TestRecipeFiles.zip("recipes/1/recipe.json" to TestRecipeFiles.document(first), CollectionFile.DOCUMENT to document(listOf(first), "Menu")) to
                    Refusal.NOT_A_RECIPE_FILE
                ),
        )
        for ((why, case) in refused) {
            val (bytes, refusal) = case
            assertEquals(why, CollectionImportOutcome.Refused(refusal), import(bytes))
            assertUnchanged()
        }
        // A newer file says so.
        assertEquals(
            "This collection file was made by a newer version of the app. Update the app to open it; nothing was added",
            CollectionImportOutcome.Refused(Refusal.LATER_VERSION).message().text(context.resources),
        )
        assertEquals(
            "This collection file is damaged, so nothing was added",
            CollectionImportOutcome.Refused(Refusal.DAMAGED).message().text(context.resources),
        )
        assertEquals(
            "This file is not a collection file, so nothing was added",
            CollectionImportOutcome.Refused(Refusal.NOT_A_RECIPE_FILE).message().text(context.resources),
        )
        assertFalse(CollectionImportOutcome.Refused(Refusal.DAMAGED).message().isNotice)
    }

    // A clip found too long once written refuses the whole file: the media already written go, and nothing is added.
    @Test
    fun aClipTooLongInTheLastRecipeLeavesNoMediaBehind() {
        val first = recipe("r-1", "First", cover("m-1"))
        val second = recipe("r-2", "Second", clip("m-2"))
        val file = collectionFile(
            listOf(first, second),
            media = mapOf(0 to listOf("media/m-1" to TestRecipeFiles.jpeg()), 1 to listOf("media/m-2" to TestRecipeFiles.mp4())),
        )
        val outcome = import(file, importer(clips = ClipDuration { 5.minutes }))
        assertEquals(CollectionImportOutcome.Refused(Refusal.CLIP_TOO_LONG), outcome)
        assertEquals(
            "A clip in this collection file is longer than ${VideoLimits.MAX_DURATION.inWholeSeconds} seconds, so nothing was added",
            (outcome as CollectionImportOutcome).message().text(context.resources),
        )
        assertUnchanged()
    }

    // A second reading that breaks off as the media are written undoes the whole import: no recipe, no collection, and
    // no media file — not even the one being written.
    @Test
    fun aReadingBrokenOffWhileWritingLeavesNothing() {
        val first = recipe("r-1", "First", cover("m-1"))
        val file = collectionFile(listOf(first), media = mapOf(0 to listOf("media/m-1" to TestRecipeFiles.jpeg(200_000))))
        val cut = indexOf(file, "recipes/1/media/m-1".toByteArray()) + 40
        var reads = 0
        val failing = {
            reads++
            if (reads == 1) file.inputStream() else file.copyOf(cut).inputStream()
        }
        val outcome = runBlocking { importer().import(open = failing) }
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), outcome)
        assertUnchanged()
    }

    // Untrusted entry names, as the book file's are checked (v0.34): an absolute one, one climbing out with `..`, with a
    // backslash or a drive — anywhere in the archive — refuses the file, and nothing is written anywhere.
    @Test
    fun pathTraversalIsRefused() {
        val first = recipe("r-1", "First", cover("m-1"))
        val good = mapOf(0 to listOf("media/m-1" to TestRecipeFiles.jpeg()))
        val outside = File(files.parentFile, "evil.jpg").also { it.delete() }
        for (name in listOf(
            "../evil.jpg", "../../evil.jpg", "/evil.jpg", "recipes/1/media/../../../evil.jpg", "recipes/1/../../evil.jpg",
            "recipes\\1\\..\\..\\evil.jpg", "C:/evil.jpg", "recipes/1/media/..",
        )) {
            assertEquals(name, CollectionImportOutcome.Refused(Refusal.DAMAGED), import(collectionFile(listOf(first), media = good, extra = listOf(name to TestRecipeFiles.jpeg()))))
            assertUnchanged()
            assertFalse(outside.exists())
        }
        // A recipe's document naming its media outside `media/` breaks the rules of a recipe file.
        for (entry in listOf("media/../../evil.jpg", "../evil.jpg", "/evil.jpg", "media/a/b.jpg")) {
            val climbing = recipe("r-2", "Climbing", FileMedia("m-2", MediaKind.PHOTO, MediaAttachment.Cover, entry))
            val outcome = import(collectionFile(listOf(first, climbing), media = good))
            assertEquals(entry, CollectionImportOutcome.Refused(Refusal.BREAKS_RULES), outcome)
            assertUnchanged()
        }
        // A media identity the file states is never a name on the device.
        val named = recipe("r-3", "Named", FileMedia("../../evil", MediaKind.PHOTO, MediaAttachment.Cover, "media/x.jpg"))
        val outcome = import(collectionFile(listOf(named), media = mapOf(0 to listOf("media/x.jpg" to TestRecipeFiles.jpeg()))))
        assertTrue("$outcome", outcome is CollectionImportOutcome.Imported)
        val written = runBlocking { recipes.get(named.id)!!.media.single().mediaId }
        assertFalse(written.contains('/'))
        assertTrue(File(media, written).isFile)
        assertFalse(outside.exists())
    }

    // The archive is held to the caps its document allows: per recipe as a recipe file is, and over the whole file
    // as many folders as it lists — whatever sizes it declares.
    @Test
    fun theArchiveIsHeldToItsCaps() {
        val first = recipe("r-1", "First", cover("m-1"))
        val small = CollectionLimits(recipe = ArchiveLimits(photo = 2_000, entries = 4))
        // A photograph over its cap.
        assertEquals(
            CollectionImportOutcome.Refused(Refusal.DAMAGED),
            import(collectionFile(listOf(first), media = mapOf(0 to listOf("media/m-1" to TestRecipeFiles.jpeg(3_000)))), importer(small)),
        )
        // An archive lying about its size.
        val lying = TestRecipeFiles.lying("recipes/1/media/m-1", TestRecipeFiles.jpeg(5_000_000), 10)
        val document = TestRecipeFiles.zip(
            CollectionFile.DOCUMENT to CollectionFile.encode(CollectionFileContent(menu, "Menu", null, listOf(first.id))).toByteArray(),
            "recipes/1/recipe.json" to TestRecipeFiles.document(first),
        )
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), import(splice(document, lying), importer(small)))
        // More entries in a folder than a recipe needs.
        val crowded = (1..4).map { "recipes/1/media/x$it" to ByteArray(1) }
        assertEquals(
            CollectionImportOutcome.Refused(Refusal.DAMAGED),
            import(collectionFile(listOf(first), media = mapOf(0 to listOf("media/m-1" to TestRecipeFiles.jpeg())), extra = crowded), importer(small)),
        )
        // More entries than the folders it lists may hold, put outside them.
        val elsewhere = (1..5).map { "other/x$it" to ByteArray(1) }
        assertEquals(
            CollectionImportOutcome.Refused(Refusal.DAMAGED),
            import(collectionFile(listOf(first), media = mapOf(0 to listOf("media/m-1" to TestRecipeFiles.jpeg())), extra = elsewhere), importer(small)),
        )
        // More recipes than a collection is read with.
        val many = (1..3).map { recipe("r-many-$it") }
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), import(collectionFile(many), importer(CollectionLimits(recipes = 2))))
        assertUnchanged()
        // Within its caps, it is read.
        assertTrue(import(collectionFile(listOf(first), media = mapOf(0 to listOf("media/m-1" to TestRecipeFiles.jpeg()))), importer(small)) is CollectionImportOutcome.Imported)
    }

    // Tags are numbers, held many times larger than the text listing them (T-260): a file of recipes each carrying the
    // most tags a recipe may — distinct, no padding — is counted at what its documents hold decoded, and refused once
    // that passes the cap though their text is far under it; a recipe of more tags, or of labels on more equipment than
    // a recipe lists, is refused as breaking the rules.
    @Test
    fun manyDistinctTagsAreCountedAtWhatTheyHoldDecoded() {
        val tags = (0L until RecipeFileCheck.MAX_TAGS).toSet()
        val listed = (1..700).map { recipe("r-tags-$it").copy(tags = tags) }
        val text = listed.sumOf { TestRecipeFiles.document(it).size.toLong() }
        assertTrue("text far under the cap: $text", text < CollectionLimits().documents / 2)
        val bytes = collectionFile(listed)
        assertTrue("a small file: ${bytes.size}", bytes.size < 8 * ArchiveLimits.MIB)
        val refusal = runCatching { CollectionArchiveReader.read({ bytes.inputStream() }) }.exceptionOrNull() as RefusedFile
        assertEquals(Refusal.DAMAGED, refusal.refusal)
        assertEquals("the recipes' documents over their cap", refusal.message)
        // Under small limits, through the import: nothing is added; within the cap, the same recipes are.
        val few = listed.take(8)
        val small = CollectionLimits(documents = 256 * 1024)
        assertTrue(few.sumOf { TestRecipeFiles.document(it).size.toLong() } < small.documents)
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), import(collectionFile(few), importer(small)))
        assertUnchanged()
        // More tags than a recipe carries; and many labels, which a file can give only inside its items (each label stands
        // in the item naming its entry), so the equipment's cap of fifteen is what bounds them.
        val tagged = recipe("r-more").copy(tags = (0L until 40_000L).toSet())
        assertEquals(CollectionImportOutcome.Refused(Refusal.BREAKS_RULES), import(collectionFile(listOf(recipe("r-1"), tagged))))
        val items = (1L..2_000L).map { EquipmentItem.entry(it) }
        val labelled = recipe("r-labels").copy(equipment = items, equipmentLabels = items.associate { it.dictionaryEntry!! to EntryLabels("Нож $it", "Knife $it") })
        assertEquals(CollectionImportOutcome.Refused(Refusal.BREAKS_RULES), import(collectionFile(listOf(recipe("r-1"), labelled))))
        assertUnchanged()
        assertTrue(import(collectionFile(few)) is CollectionImportOutcome.Imported)
    }

    // Of a recipe's folder, only its document and its media entries are kept by the first reading, and counted: other
    // entries, however long their names, are passed over; media entries past the cap refuse the file.
    @Test
    fun onlyTheEntriesThatMatterAreKept() {
        val first = recipe("r-1")
        val small = CollectionLimits(documents = 16 * 1024)
        val long = "y".repeat(200)
        val media = collectionFile(listOf(first), extra = (1..45).map { "recipes/1/media/$long-$it" to ByteArray(1) })
        val refusal = runCatching { CollectionArchiveReader.read({ media.inputStream() }, small) }.exceptionOrNull() as RefusedFile
        assertEquals("the recipes' media entries over their cap", refusal.message)
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), import(media, importer(small)))
        assertUnchanged()
        val others = collectionFile(listOf(first), extra = (1..45).map { "recipes/1/notes/$long-$it" to ByteArray(1) })
        assertTrue(import(others, importer(small)) is CollectionImportOutcome.Imported)
    }

    // The media a collection brings are held together to their cap (2 GiB): the first reading refuses a file whose
    // named media pass it before anything is written — the archive is never opened a second time.
    @Test
    fun theMediaTogetherAreHeldToTheirCapBeforeAnythingIsWritten() {
        assertEquals(2L * 1024 * 1024 * 1024, CollectionLimits().media)
        val file = threeMedia()
        val capped = CollectionLimits(media = 2_500)
        val refusal = runCatching { CollectionArchiveReader.read({ file.inputStream() }, capped) }.exceptionOrNull() as RefusedFile
        assertEquals(Refusal.DAMAGED, refusal.refusal)
        assertEquals("the media over their cap", refusal.message)
        val opens = java.util.concurrent.atomic.AtomicInteger()
        val outcome = runBlocking { importer(capped).import { opens.incrementAndGet(); file.inputStream() } }
        assertEquals(CollectionImportOutcome.Refused(Refusal.DAMAGED), outcome)
        assertEquals("read once, nothing written", 1, opens.get())
        assertUnchanged()
        assertTrue(import(file, importer(CollectionLimits(media = 3_000))) is CollectionImportOutcome.Imported)
    }

    // The second reading holds them to the same cap again as they are written, for it need not give what the first did:
    // it is refused never having written more than the cap.
    @Test
    fun theMediaTogetherAreHeldToTheirCapAsTheyAreWritten() {
        val file = threeMedia()
        val capped = CollectionLimits(media = 2_500)
        val read = CollectionArchiveReader.read({ file.inputStream() })
        val out = TestRecipeFiles.incoming()
        try {
            val written = mutableListOf<File>()
            val refusal = runCatching {
                CollectionArchiveReader.media({ file.inputStream() }, read, setOf(0, 1), target = { _, item -> File(out, item.mediaId).also { written += it } }, capped)
            }.exceptionOrNull() as RefusedFile
            assertEquals(Refusal.DAMAGED, refusal.refusal)
            assertTrue("written ${written.sumOf { it.length() }}", written.sumOf { it.length() } <= 2_500)
        } finally {
            out.deleteRecursively()
        }
    }

    /** Two recipes holding three media items of 1 000 bytes each. */
    private fun threeMedia(): ByteArray = collectionFile(
        listOf(recipe("r-duck", "Duck", cover("m-duck")), recipe("r-cake", "Cake", cover("m-cake"), clip("m-clip"))),
        media = mapOf(0 to listOf("media/m-duck" to TestRecipeFiles.jpeg()), 1 to listOf("media/m-cake" to TestRecipeFiles.jpeg(), "media/m-clip" to TestRecipeFiles.mp4())),
    )

    // Scenario 5 end to end: the file the application writes for a collection (T-255) imports into another book whole.
    @Test
    fun aCollectionsOwnFileImportsWhole() = runBlocking {
        val duck = recipe("r-duck", "Duck with apples", cover("m-duck"))
        val salad = recipe("r-salad", "Olivier")
        val photo = File(files, "sent-photo").also { it.parentFile!!.mkdirs(); it.writeBytes(TestRecipeFiles.jpeg(2_345)) }
        val out = ByteArrayOutputStream()
        CollectionArchive.write(CollectionFileContent(menu, "New Year's menu", "anna", listOf(duck.id, salad.id)), listOf(duck, salad), out) { _, _ -> photo }
        val outcome = import(out.toByteArray())
        assertEquals(CollectionImportOutcome.Imported(menu, "New Year's menu", recipes = 2, held = 0, updated = false), outcome)
        assertEquals(listOf(duck.id, salad.id), collections.get(menu)!!.recipes)
        val cover = recipes.get(duck.id)!!.media.single()
        assertArrayEquals(TestRecipeFiles.jpeg(2_345), File(media, cover.mediaId).readBytes())
        assertNotNull(recipes.get(salad.id))
    }

    /** [file] with one more entry, [name] holding [data], after its own — whatever names they hold already. */
    private fun withEntry(file: ByteArray, name: String, data: ByteArray) = splice(file, TestRecipeFiles.lying(name, data, data.size))

    /** [document], a ZIP of the document alone, followed by the local entry [entry] — a ZIP read as a stream reads both. */
    private fun splice(document: ByteArray, entry: ByteArray): ByteArray {
        // A stream reader stops at the central directory: the document's own entry is kept, its directory left out.
        val end = indexOf(document, byteArrayOf(0x50, 0x4b, 0x01, 0x02))
        return document.copyOf(end) + entry
    }

    private fun indexOf(bytes: ByteArray, pattern: ByteArray): Int =
        (0..bytes.size - pattern.size).first { at -> pattern.indices.all { bytes[at + it] == pattern[it] } }
}
