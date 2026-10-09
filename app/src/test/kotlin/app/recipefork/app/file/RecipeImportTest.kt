package app.recipefork.app.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.TestRecipeFiles.file
import app.recipefork.app.file.TestRecipeFiles.jpeg
import app.recipefork.app.file.TestRecipeFiles.mp4
import app.recipefork.app.file.TestRecipeFiles.soup
import app.recipefork.app.file.TestRecipeFiles.zip
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.Timer
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-064 and decisions/20260928-recipe-file-is-a-zip.md: a recipe file is added to the active book as a found recipe;
 * one the book already holds adds nothing; one that cannot be believed is refused, for its reason, and adds nothing.
 */
@RunWith(AndroidJUnit4::class)
class RecipeImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val files = File(context.cacheDir, "import-test-files").also { it.deleteRecursively() }
    private val importer = RecipeImport(recipes, files, clock = { now })
    private val mediaDirectory = RecipeImport.mediaDirectory(files, BookName.Pool)

    @After
    fun close() {
        book.close()
        files.deleteRecursively()
    }

    private fun import(bytes: ByteArray, limits: ArchiveLimits = ArchiveLimits()) = runBlocking {
        RecipeImport(recipes, files, clock = { now }, limits = limits, clipDuration = TestRecipeFiles.SHORT_CLIP).import { ByteArrayInputStream(bytes) }
    }

    private fun assertNothingAdded() = runBlocking {
        assertEquals(0, recipes.count())
        assertEquals(emptyList<Any>(), recipes.marked().first())
        assertEquals(emptyList<String>(), mediaDirectory.list()?.toList().orEmpty())
    }

    private fun assertRefused(refusal: Refusal, bytes: ByteArray, limits: ArchiveLimits = ArchiveLimits()) {
        assertEquals(ImportOutcome.Refused(refusal), import(bytes, limits))
        assertNothingAdded()
    }

    // FLOW-064 (1): the recipe is added to the active book as a found recipe, listed among the marked ones and not
    // among the own; it holds the file's content whole, and the author and parent it states.
    @Test
    fun aFileIsAddedAsAFoundRecipe() = runBlocking {
        val content = soup()
        assertEquals(ImportOutcome.Added(content.id, "Bean soup"), import(file(content)))
        val found = recipes.get(content.id)!!
        assertEquals(content.title, found.title)
        assertEquals(content.description, found.description)
        assertEquals(content.tags, found.tags)
        assertEquals(content.servings, found.servings)
        assertEquals(content.ingredients, found.ingredients)
        assertEquals(content.steps, found.steps)
        assertEquals("anna", found.statedAuthor)
        assertEquals(RecipeId("p-1"), found.parent)
        assertEquals(StatedParent("Soup", "boris"), found.statedParent)
        assertNull(found.owner)
        // FLOW-064 (2): a found recipe is not editable.
        assertFalse(found.state.editable)
        assertEquals(RecipeState.LINK_SHARED, found.state)
        val marked = recipes.marked().first().single()
        assertEquals(content.id.value, marked.recipeId)
        assertEquals("Bean soup", marked.title)
        assertEquals("anna", marked.statedAuthor)
        assertTrue(marked.held)
        assertEquals(emptyList<Any>(), recipes.ownNewest(10).first())
    }

    @Test
    fun aFileNamingNoAuthorStatesNone() = runBlocking {
        val content = soup(author = null)
        import(file(content))
        assertNull(recipes.get(content.id)!!.statedAuthor)
        assertNull(recipes.marked().first().single().statedAuthor)
    }

    // FLOW-064 (4): the same file again adds nothing and says the recipe is already in the book.
    @Test
    fun theSameFileAgainAddsNothing() = runBlocking {
        val content = soup()
        import(file(content))
        val first = recipes.get(content.id)
        // Named as the book holds it.
        assertEquals(ImportOutcome.AlreadyInBook(content.id, "Bean soup"), import(file(content.copy(title = "Changed"))))
        assertEquals(first, recipes.get(content.id))
        assertEquals(1, recipes.count())
        assertEquals(1, recipes.marked().first().size)
    }

    // FLOW-064 (4): a file holding the person's own recipe adds nothing, and leaves the own recipe as it was.
    @Test
    fun aFileOfTheirOwnRecipeAddsNothing() = runBlocking {
        val own = Recipe.create(now, owner = null, id = RecipeId("0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10")).copy(title = "Mine")
        recipes.save(own)
        assertEquals(ImportOutcome.AlreadyInBook(own.id, "Mine"), import(file(soup())))
        assertEquals(own, recipes.get(own.id))
        assertEquals(emptyList<Any>(), recipes.marked().first())
    }

    // FLOW-064 (5): a file that is not a ZIP.
    @Test
    fun aFileThatIsNotAZipIsRefused() {
        assertRefused(Refusal.NOT_A_RECIPE_FILE, "just some text".toByteArray())
        assertRefused(Refusal.NOT_A_RECIPE_FILE, ByteArray(0))
    }

    // FLOW-064 (5): a ZIP with no recipe.json.
    @Test
    fun aZipWithNoDocumentIsRefused() =
        assertRefused(Refusal.NOT_A_RECIPE_FILE, zip("notes.txt" to "hello".toByteArray(), "media/" to ByteArray(0)))

    // FLOW-064 (5): a document that does not parse.
    @Test
    fun aDocumentThatDoesNotParseIsRefused() {
        assertRefused(Refusal.DAMAGED, zip(RecipeFile.DOCUMENT to "{ not json".toByteArray()))
        val unknownUnit = RecipeFile.encode(soup()).replace("\"gram\"", "\"furlong\"")
        assertRefused(Refusal.DAMAGED, zip(RecipeFile.DOCUMENT to unknownUnit.toByteArray()))
        assertRefused(Refusal.DAMAGED, zip(RecipeFile.DOCUMENT to byteArrayOf(0xC3.toByte(), 0x28)))
    }

    // FLOW-064 (5): a document of a later format, written by a later version of the application.
    @Test
    fun aLaterFormatIsRefused() {
        val later = RecipeFile.encode(soup()).replace("\"format_version\": 4", "\"format_version\": 5")
        assertTrue(later.contains("\"format_version\": 5"))
        assertRefused(Refusal.LATER_VERSION, zip(RecipeFile.DOCUMENT to later.toByteArray()))
    }

    // The ADR: an entry name that is absolute or climbs with `..` refuses the file, whichever entry it is.
    @Test
    fun anUnsafeEntryNameIsRefused() {
        listOf("/etc/passwd", "../outside", "media/../../outside", "\\windows").forEach { name ->
            assertRefused(Refusal.DAMAGED, zip(RecipeFile.DOCUMENT to TestRecipeFiles.document(soup()), name to "x".toByteArray()))
        }
    }

    // The ADR: inflation stops at the cap, whatever size the archive declares.
    @Test
    fun aDocumentInflatingPastItsCapIsRefusedWhateverItDeclares() {
        val bomb = TestRecipeFiles.lying(RecipeFile.DOCUMENT, ByteArray(3 * 1024 * 1024) { ' '.code.toByte() }, declaredSize = 10)
        val thrown = runCatching { RecipeArchiveReader.document({ ByteArrayInputStream(bomb) }) }.exceptionOrNull()
        assertTrue("$thrown", thrown is RefusedFile && thrown.message == "entry over its cap")
        assertRefused(Refusal.DAMAGED, bomb)
    }

    @Test
    fun anIgnoredEntryCountsTowardTheArchivesCap() {
        val limits = ArchiveLimits(archive = 64 * 1024)
        val ok = file(soup(), "notes/a.bin" to ByteArray(10_000))
        assertTrue(import(ok, limits) is ImportOutcome.Added)
        runBlocking { importer.remove(soup().id) }
        val big = file(soup(), "notes/a.bin" to ByteArray(40_000), "notes/b.bin" to ByteArray(40_000))
        assertRefused(Refusal.DAMAGED, big, limits)
    }

    // The ADR: the document is validated against the recipe's rules — DOM-012, DOM-016, DOM-017 — and its shape.
    @Test
    fun aDocumentBreakingTheRecipesRulesIsRefused() {
        val content = soup()
        val wrongUnit = content.copy(ingredients = content.ingredients.map { if (it.position == 0) it.copy(unit = QuantityUnit.TEASPOON) else it })
        val noValue = content.copy(ingredients = content.ingredients.map { if (it.position == 0) it.copy(quantity = Rational.ZERO) else it })
        val tooLong = content.copy(title = "t".repeat(121))
        val missingIngredient = content.copy(steps = content.steps.map { if (it.position == 0) it.copy(ingredientReferences = listOf(IngredientReference(7, 0))) else it })
        val twoAtOnePlace = content.copy(steps = content.steps.map { if (it.position == 0) it.copy(ingredientReferences = listOf(IngredientReference(0, 9), IngredientReference(1, 9))) else it })
        val longTimer = content.copy(steps = content.steps.map { if (it.position == 0) it.copy(timer = Timer((100 * 60).minutes)) else it })
        listOf(wrongUnit, noValue, tooLong, missingIngredient, twoAtOnePlace, longTimer).forEach { assertRefused(Refusal.BREAKS_RULES, file(it)) }
    }

    // A step's part of an ingredient is validated like any quantity: on its grid, above zero, of its row's unit type.
    @Test
    fun aPartOffItsGridOrNotAboveZeroIsRefused() {
        val content = soup()
        fun parted(share: IngredientShare) =
            content.copy(steps = content.steps.map { if (it.position == 1) it.copy(ingredientReferences = listOf(IngredientReference(0, 18, share))) else it })
        listOf(
            IngredientShare(Rational.of(1, 2), QuantityUnit.GRAM),
            IngredientShare(Rational.ZERO, QuantityUnit.GRAM),
            IngredientShare(Rational.of(-100), QuantityUnit.GRAM),
            IngredientShare(Rational.of(1), QuantityUnit.TEASPOON),
            // In a unit of the row's type that the row can never read: it would read as the whole.
            IngredientShare(Rational.of(100), QuantityUnit.MILLILITRE),
            IngredientShare(Rational.of(1), QuantityUnit.LITRE),
        ).forEach { assertRefused(Refusal.BREAKS_RULES, file(parted(it))) }
        // A part larger than the whole is no rule broken: the book marks it, nothing refuses it.
        assertTrue(import(file(parted(IngredientShare(Rational.of(300), QuantityUnit.GRAM)))) is ImportOutcome.Added)
    }

    // FLOW-063 (1), FLOW-024 (6): a friend's draft — a row still being written, a row off its grid — is imported, its
    // rows kept exactly as written.
    @Test
    fun aDraftIsImportedAsWritten() = runBlocking {
        val content = TestRecipeFiles.draft()
        assertTrue(import(file(content)) is ImportOutcome.Added)
        val found = recipes.get(content.id)!!
        assertEquals(content.ingredients, found.ingredients)
        assertNull(found.ingredients[1].authoredQuantity)
        assertNull(found.ingredients[2].authoredQuantity)
    }

    // The ADR: only the media entries the document names are read, written under names the importer makes, each of
    // its kind by its leading bytes.
    @Test
    fun theMediaItNamesIsWrittenUnderGeneratedNames() = runBlocking {
        val content = soup().copy(
            media = listOf(
                FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg"),
                FileMedia("m-2", MediaKind.VIDEO, MediaAttachment.ToStep(0), "media/boil.mp4"),
            ),
        )
        val photo = jpeg(5_000)
        val video = mp4(7_000)
        val bytes = file(content, "media/cover.jpg" to photo, "media/boil.mp4" to video, "media/unnamed.jpg" to jpeg())
        assertTrue(import(bytes) is ImportOutcome.Added)
        val media = recipes.get(content.id)!!.media
        assertEquals(listOf(MediaKind.PHOTO to MediaAttachment.Cover, MediaKind.VIDEO to MediaAttachment.ToStep(0)), media.map { it.kind to it.attachment })
        media.forEach { assertNotEquals("m-1", it.mediaId); assertNotEquals("m-2", it.mediaId) }
        val written = mediaDirectory.listFiles()!!.associate { it.name to it.readBytes().toList() }
        assertEquals(media.map { it.mediaId }.toSet(), written.keys)
        assertEquals(photo.toList(), written.getValue(media[0].mediaId))
        assertEquals(video.toList(), written.getValue(media[1].mediaId))
    }

    @Test
    fun mediaNotOfItsKindIsRefused() {
        val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg")))
        assertRefused(Refusal.DAMAGED, file(content, "media/cover.jpg" to mp4()))
        assertRefused(Refusal.DAMAGED, file(content, "media/cover.jpg" to "<?php".toByteArray()))
        assertRefused(Refusal.DAMAGED, file(content, "media/cover.jpg" to ByteArray(0)))
        val video = soup().copy(media = listOf(FileMedia("m-1", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/v.mp4")))
        assertRefused(Refusal.DAMAGED, file(video, "media/v.mp4" to jpeg()))
    }

    @Test
    fun mediaOverItsCapIsRefused() {
        val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg")))
        assertTrue(import(file(content, "media/cover.jpg" to jpeg(ArchiveLimits.MIB.toInt())), ArchiveLimits()) is ImportOutcome.Added)
        runBlocking { importer.remove(content.id) }
        assertRefused(Refusal.DAMAGED, file(content, "media/cover.jpg" to jpeg(ArchiveLimits.MIB.toInt() + 1)))
        val video = soup().copy(media = listOf(FileMedia("m-1", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/v.mp4")))
        assertRefused(Refusal.DAMAGED, file(video, "media/v.mp4" to mp4(6 * ArchiveLimits.MIB.toInt() + 1)))
    }

    @Test
    fun mediaNamedButMissingIsRefused() {
        val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg")))
        assertRefused(Refusal.DAMAGED, file(content))
        // The same entry twice, which the standard library's writer refuses to write and an archive can still hold.
        val document = TestRecipeFiles.document(content)
        val twice = listOf(RecipeFile.DOCUMENT to document, "media/cover.jpg" to jpeg(), "media/cover.jpg" to jpeg())
            .fold(ByteArray(0)) { archive, (name, bytes) -> archive + TestRecipeFiles.lying(name, bytes, bytes.size) }
        assertRefused(Refusal.DAMAGED, twice)
    }

    // A document naming a media entry outside media/, or climbing out of it, breaks the rules and is not read.
    @Test
    fun aMediaEntryOutsideTheDirectoryIsRefused() {
        listOf("cover.jpg", "media/../cover.jpg", "/media/cover.jpg").forEach { entry ->
            val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, entry)))
            assertRefused(Refusal.BREAKS_RULES, file(content))
        }
    }

    @Test
    fun aFileThatCannotBeOpenedIsRefused() = runBlocking {
        assertEquals(ImportOutcome.Refused(Refusal.CANNOT_OPEN), importer.import { throw IOException("gone") })
        assertNothingAdded()
    }

    // FLOW-064 (6): removed from the book, a found recipe leaves it with its content and media.
    @Test
    fun aFoundRecipeIsRemovedWithItsContent() = runBlocking {
        val content = soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg")))
        import(file(content, "media/cover.jpg" to jpeg()))
        assertEquals(1, mediaDirectory.list()!!.size)
        assertTrue(importer.remove(content.id))
        assertNull(recipes.get(content.id))
        assertNothingAdded()
        // Removed, it can be imported again.
        assertTrue(import(file(content, "media/cover.jpg" to jpeg())) is ImportOutcome.Added)
    }

    // Removal never reaches an own recipe: only what is marked is removed.
    @Test
    fun removalLeavesAnOwnRecipe() = runBlocking {
        val own = Recipe.create(now, owner = null).copy(title = "Mine")
        recipes.save(own)
        assertFalse(importer.remove(own.id))
        assertEquals(own, recipes.get(own.id))
    }

    // DOM-022, DOM-023: a found recipe keeps the file's ingredient groups and the note of every row.
    @Test
    fun aFoundRecipeKeepsItsGroupsAndNotes() = runBlocking {
        val content = soup().copy(
            ingredients = soup().ingredients.map { it.copy(note = "note of ${it.freeTextName}") },
            ingredientGroups = listOf(IngredientGroup("Base", 0), IngredientGroup("Topping", 1)),
        )
        assertEquals(ImportOutcome.Added(content.id, "Bean soup"), import(file(content)))
        val found = recipes.get(content.id)!!
        assertEquals(listOf(IngredientGroup("Base", 0), IngredientGroup("Topping", 1)), found.ingredientGroups)
        assertEquals(listOf("note of beans", "note of onion"), found.ingredients.map { it.note })
        assertEquals(content.ingredients, found.ingredients)
        assertEquals(found, RecipeImport.foundRecipe(content, emptyList(), now))
    }

    // DOM-022, DOM-023: a file of format 1, written before groups and notes, still imports, with neither.
    @Test
    fun aFormat1FileImportsWithNoGroupsAndNoNotes() = runBlocking {
        val format1 = oneTimerFormat(RecipeFile.encode(soup()), 1)
            .replace(Regex(",\\s*\"ingredient_groups\": \\[\\]"), "")
        assertTrue(format1.contains("\"format_version\": 1"))
        assertFalse(format1.contains("ingredient_groups"))
        assertEquals(ImportOutcome.Added(soup().id, "Bean soup"), import(zip(RecipeFile.DOCUMENT to format1.toByteArray())))
        val found = recipes.get(soup().id)!!
        assertEquals(emptyList<IngredientGroup>(), found.ingredientGroups)
        assertEquals(soup().ingredients, found.ingredients)
        assertTrue(found.ingredients.all { it.note == null })
        assertEquals(soup().steps, found.steps)
    }

    // DOM-024: a file of format 2, one `timer` a step, imports each as its step's only timer.
    @Test
    fun aFormat2FileImportsEachTimerAsItsStepsOnlyOne() = runBlocking {
        val format2 = oneTimerFormat(RecipeFile.encode(soup()), 2)
        assertTrue(format2.contains("\"timer\": {") && !format2.contains("\"timers\""))
        assertEquals(ImportOutcome.Added(soup().id, "Bean soup"), import(zip(RecipeFile.DOCUMENT to format2.toByteArray())))
        assertEquals(listOf(listOf(Timer(10.minutes)), emptyList()), recipes.get(soup().id)!!.steps.map { it.timers })
    }

    // DOM-024: a file whose step carries several timers imports with every one, in order; a sixth, or a timer past
    // its bounds after the first, refuses the file.
    @Test
    fun aFileWithSeveralTimersOnAStepImportsThemAllAndASixthIsRefused() {
        val timers = listOf(Timer(10.minutes), Timer(6.minutes, "Sauce"), Timer(2.minutes), Timer(1.minutes, "Garlic"), Timer(3.minutes))
        fun with(timers: List<Timer>) = soup().copy(steps = soup().steps.map { if (it.position == 0) it.copy(timers = timers) else it })
        assertRefused(Refusal.BREAKS_RULES, file(with(timers + Timer(4.minutes))))
        assertRefused(Refusal.BREAKS_RULES, file(with(listOf(Timer(10.minutes), Timer((100 * 60).minutes)))))
        assertRefused(Refusal.BREAKS_RULES, file(with(listOf(Timer(10.minutes), Timer(5.minutes, "n".repeat(61))))))
        assertEquals(ImportOutcome.Added(soup().id, "Bean soup"), import(file(with(timers))))
        assertEquals(timers, runBlocking { recipes.get(soup().id)!! }.steps[0].timers)
    }

    /**
     * [document] as format [version] — 1 or 2 — wrote it: each step's list of at most one timer as its one `timer`, and
     * no equipment.
     */
    private fun oneTimerFormat(document: String, version: Int): String = withoutEquipment(document, version)
        .replace(Regex(",\\s*\"timers\": \\[\\]"), "")
        .replace(Regex("\"timers\": \\[\\s*(\\{[^{}\\[\\]]*\\})\\s*\\]"), "\"timer\": $1")

    /** [document], holding no equipment, as format [version] — 1, 2 or 3 — wrote it: with no `equipment` at all. */
    private fun withoutEquipment(document: String, version: Int): String = document
        .replace("\"format_version\": ${RecipeFile.FORMAT_VERSION}", "\"format_version\": $version")
        .replace(Regex("\"equipment\": \\[\\],\\s*"), "")

    // A found recipe keeps the file's equipment, in order.
    @Test
    fun aFoundRecipeKeepsItsEquipment() = runBlocking {
        val content = soup().copy(equipment = listOf("Pot 5 l", "Ladle"))
        assertEquals(ImportOutcome.Added(content.id, "Bean soup"), import(file(content)))
        val found = recipes.get(content.id)!!
        assertEquals(listOf("Pot 5 l", "Ladle"), found.equipment)
        assertEquals(found, RecipeImport.foundRecipe(content, emptyList(), now))
        assertEquals(listOf("Pot 5 l", "Ladle"), RecipeImport.foundRecipe(content, emptyList(), now).equipment)
    }

    // An item a file holds with spaces around it imports trimmed, as a note does.
    @Test
    fun anUntrimmedItemImportsTrimmed() = runBlocking {
        val content = soup().copy(equipment = listOf("  Pot 5 l ", "Ladle\t"))
        assertEquals(ImportOutcome.Added(content.id, "Bean soup"), import(file(content)))
        assertEquals(listOf("Pot 5 l", "Ladle"), recipes.get(content.id)!!.equipment)
    }

    // A file of format 3, written before equipment, still imports, with none.
    @Test
    fun aFormat3FileImportsWithNoEquipment() = runBlocking {
        val format3 = withoutEquipment(RecipeFile.encode(soup()), 3)
        assertTrue(format3.contains("\"format_version\": 3"))
        assertFalse(format3.contains("equipment"))
        assertEquals(ImportOutcome.Added(soup().id, "Bean soup"), import(zip(RecipeFile.DOCUMENT to format3.toByteArray())))
        val found = recipes.get(soup().id)!!
        assertEquals(emptyList<String>(), found.equipment)
        assertEquals(soup().steps, found.steps)
        assertEquals(soup().ingredients, found.ingredients)
    }

    // A file whose equipment breaks the rules — a sixteenth item, a blank one, one over 40 characters — is refused.
    @Test
    fun aFileWhoseEquipmentBreaksTheRulesIsRefused() {
        val fifteen = (1..15).map { "Item $it" }
        assertRefused(Refusal.BREAKS_RULES, file(soup().copy(equipment = fifteen + "Item 16")))
        assertRefused(Refusal.BREAKS_RULES, file(soup().copy(equipment = listOf("Pot", " "))))
        assertRefused(Refusal.BREAKS_RULES, file(soup().copy(equipment = listOf("p".repeat(41)))))
        assertEquals(ImportOutcome.Added(soup().id, "Bean soup"), import(file(soup().copy(equipment = fifteen))))
    }

    // DOM-022, DOM-023: a file whose groups or notes break the rules is refused, and adds nothing.
    @Test
    fun aFileWhoseGroupsOrNotesBreakTheRulesIsRefused() {
        assertRefused(Refusal.BREAKS_RULES, file(soup().copy(ingredients = soup().ingredients.map { it.copy(note = "n".repeat(61)) })))
        assertRefused(Refusal.BREAKS_RULES, file(soup().copy(ingredientGroups = listOf(IngredientGroup("g".repeat(41), 0)))))
        assertRefused(Refusal.BREAKS_RULES, file(soup().copy(ingredientGroups = listOf(IngredientGroup("A", 1), IngredientGroup("B", 1)))))
        assertRefused(Refusal.BREAKS_RULES, file(soup().copy(ingredientGroups = listOf(IngredientGroup("A", 3)))))
    }
}
